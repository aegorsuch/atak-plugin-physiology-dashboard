#!/usr/bin/env python3
"""Publish the signed CIV APK from the current protected TAK Forge pipeline."""

from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener


GITHUB_REPOSITORY = "Ascent-Integrated-Tech/biometrics-atak-plugin"
CI_PROJECT_PATH = "aegorsuch/atak-plugin-physiology-dashboard"
RELEASE_TAG = "takgov-pipeline"
RELEASE_TITLE = "TAK Forge Automated Pipeline"
STABLE_ASSET_NAME = "ATAK-Plugin-PhysiologyDashboard-latest.apk"
OWNERSHIP_MARKER = "<!-- physiology-dashboard-ci-publisher -->"
GITHUB_API = "https://api.github.com"


class PublishError(RuntimeError):
    pass


class SafeRedirectHandler(HTTPRedirectHandler):
    def redirect_request(self, request, file_pointer, code, message, headers, new_url):
        redirected = super().redirect_request(
            request, file_pointer, code, message, headers, new_url
        )
        if redirected is not None:
            old_host = urlsplit(request.full_url).hostname
            new_host = urlsplit(new_url).hostname
            if old_host != new_host:
                redirected.remove_header("Authorization")
        return redirected


class GitHubApi:
    def __init__(self, token):
        self.token = token
        self.opener = build_opener(SafeRedirectHandler())

    def _request(self, method, url, payload=None, data=None, accept=None):
        headers = {
            "Accept": accept or "application/vnd.github+json",
            "Authorization": "Bearer " + self.token,
            "User-Agent": "tak-forge-dashboard-release-publisher",
            "X-GitHub-Api-Version": "2022-11-28",
        }
        if payload is not None:
            data = json.dumps(payload).encode("utf-8")
            headers["Content-Type"] = "application/json"
        elif data is not None:
            headers["Content-Type"] = "application/vnd.android.package-archive"

        request = Request(url, data=data, headers=headers, method=method)
        try:
            with self.opener.open(request, timeout=120) as response:
                return response.status, response.read()
        except HTTPError as error:
            return error.code, error.read()
        except (OSError, URLError) as error:
            raise PublishError(
                f"GitHub {method} request failed for {urlsplit(url).path}."
            ) from error

    def request_json(self, method, path, payload=None, allow_not_found=False):
        status, body = self._request(method, GITHUB_API + path, payload=payload)
        if allow_not_found and status == 404:
            return None
        if status < 200 or status >= 300:
            raise PublishError(f"GitHub API {method} returned HTTP {status}.")
        try:
            return json.loads(body.decode("utf-8")) if body else {}
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            raise PublishError("GitHub API returned invalid JSON.") from error

    def list_assets(self, release_id):
        assets = []
        page = 1
        while True:
            batch = self.request_json(
                "GET",
                f"/repos/{GITHUB_REPOSITORY}/releases/{release_id}/assets"
                f"?per_page=100&page={page}",
            )
            if not isinstance(batch, list) or any(
                not isinstance(asset, dict) for asset in batch
            ):
                raise PublishError("GitHub returned an invalid release-asset list.")
            assets.extend(batch)
            if len(batch) < 100:
                return assets
            page += 1

    def upload_asset(self, release, name, asset_path):
        upload_url = release.get("upload_url")
        if not isinstance(upload_url, str):
            raise PublishError("GitHub returned an invalid APK upload URL.")
        upload_url = upload_url.split("{", 1)[0]
        upload_parts = urlsplit(upload_url)
        if upload_parts.scheme != "https" or upload_parts.netloc != "uploads.github.com":
            raise PublishError("GitHub returned an unexpected APK upload URL.")
        url = upload_url + "?" + urlencode({"name": name})
        status, body = self._request("POST", url, data=asset_path.read_bytes())
        if status < 200 or status >= 300:
            raise PublishError(f"GitHub APK upload returned HTTP {status}.")
        try:
            result = json.loads(body.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            raise PublishError("GitHub returned invalid upload metadata.") from error
        if not isinstance(result, dict):
            raise PublishError("GitHub returned unexpected upload metadata.")
        return result

    def download_asset(self, asset_id):
        status, body = self._request(
            "GET",
            f"{GITHUB_API}/repos/{GITHUB_REPOSITORY}/releases/assets/{asset_id}",
            accept="application/octet-stream",
        )
        if status < 200 or status >= 300:
            raise PublishError(f"GitHub asset verification returned HTTP {status}.")
        return body

    def delete_asset(self, asset_id):
        status, _ = self._request(
            "DELETE",
            f"{GITHUB_API}/repos/{GITHUB_REPOSITORY}/releases/assets/{asset_id}",
        )
        if status != 204:
            raise PublishError(f"GitHub asset deletion returned HTTP {status}.")


def latest_develop_sha(repository_url):
    try:
        result = subprocess.run(
            ["git", "ls-remote", repository_url, "refs/heads/develop"],
            check=False,
            capture_output=True,
            text=True,
        )
    except OSError as error:
        raise PublishError("Could not verify the canonical develop branch head.") from error
    if result.returncode != 0:
        raise PublishError("Could not verify the canonical develop branch head.")
    refs = []
    for line in result.stdout.splitlines():
        fields = line.split()
        if len(fields) == 2 and fields[1] == "refs/heads/develop":
            refs.append(fields[0].lower())
    if len(refs) != 1:
        raise PublishError("Could not read exactly one canonical develop branch head.")
    return refs[0]


def verify_signed_apk(apk_path):
    if not apk_path.is_file() or apk_path.is_symlink() or apk_path.stat().st_size == 0:
        raise PublishError("The release APK is missing, empty, or not a regular file.")
    sdk_root = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    if not sdk_root:
        raise PublishError("Android SDK path is unavailable for APK signature verification.")
    build_tools = Path(sdk_root) / "build-tools"
    signer_name = "apksigner.bat" if os.name == "nt" else "apksigner"
    candidates = [
        path / signer_name
        for path in build_tools.iterdir()
        if path.is_dir() and (path / signer_name).is_file()
    ] if build_tools.is_dir() else []
    if not candidates:
        raise PublishError("Android apksigner is unavailable for APK signature verification.")
    apksigner = max(
        candidates,
        key=lambda path: tuple(int(part) for part in re.findall(r"\d+", path.parent.name)),
    )
    try:
        result = subprocess.run(
            [str(apksigner), "verify", "--print-certs", str(apk_path)],
            check=False,
            capture_output=True,
            text=True,
        )
    except OSError as error:
        raise PublishError("Android apksigner could not be executed.") from error
    output = result.stdout + result.stderr
    if result.returncode != 0 or not re.search(
        r"Signer #\d+ certificate SHA-256 digest:", output
    ):
        raise PublishError("The release APK failed Android signature verification.")


def _validate_asset(api, asset, name, size, digest):
    if (
        not isinstance(asset, dict)
        or asset.get("name") != name
        or asset.get("state") != "uploaded"
        or asset.get("size") != size
        or not isinstance(asset.get("id"), int)
    ):
        raise PublishError("GitHub did not confirm the uploaded APK name and size.")
    uploaded_digest = asset.get("digest")
    if uploaded_digest is None:
        uploaded_digest = "sha256:" + hashlib.sha256(
            api.download_asset(asset["id"])
        ).hexdigest()
    if uploaded_digest != "sha256:" + digest:
        raise PublishError("GitHub did not confirm the uploaded APK SHA-256 digest.")


def _update_rolling_tag(api, commit_sha):
    tag_path = f"/repos/{GITHUB_REPOSITORY}/git/ref/tags/{RELEASE_TAG}"
    ref = api.request_json("GET", tag_path, allow_not_found=True)
    if ref is None:
        api.request_json(
            "POST",
            f"/repos/{GITHUB_REPOSITORY}/git/refs",
            {"ref": f"refs/tags/{RELEASE_TAG}", "sha": commit_sha},
        )
    elif not isinstance(ref, dict) or not isinstance(ref.get("object"), dict):
        raise PublishError("GitHub returned an invalid rolling tag reference.")
    elif not isinstance(ref["object"].get("sha"), str):
        raise PublishError("GitHub returned an invalid rolling tag reference.")
    elif ref["object"]["sha"].lower() != commit_sha:
        api.request_json(
            "PATCH",
            tag_path.replace("/git/ref/", "/git/refs/"),
            {"sha": commit_sha, "force": True},
        )


class ReleasePublisher:
    def __init__(
        self,
        env,
        api=None,
        head_reader=latest_develop_sha,
        apk_verifier=verify_signed_apk,
    ):
        self.env = env
        self.api = api
        self.head_reader = head_reader
        self.apk_verifier = apk_verifier

    def _assert_current_head(self, commit_sha):
        if self.head_reader(self.env["CI_REPOSITORY_URL"]) != commit_sha:
            raise PublishError("This pipeline is no longer the canonical develop head.")
        mirror = self.api.request_json(
            "GET", f"/repos/{GITHUB_REPOSITORY}/commits/develop"
        )
        if not isinstance(mirror, dict) or not isinstance(mirror.get("sha"), str):
            raise PublishError("GitHub returned an invalid develop branch reference.")
        if mirror["sha"].lower() != commit_sha:
            raise PublishError("The GitHub develop mirror does not match this pipeline.")

    def run(self, cwd=None):
        required = (
            "CI_SERVER_HOST",
            "CI_PROJECT_PATH",
            "CI_COMMIT_BRANCH",
            "CI_PIPELINE_SOURCE",
            "CI_COMMIT_REF_PROTECTED",
            "CI_COMMIT_SHA",
            "CI_REPOSITORY_URL",
            "CI_JOB_ID",
            "GITHUB_RELEASE_TOKEN",
        )
        missing = [name for name in required if not self.env.get(name)]
        if missing:
            raise PublishError("Required CI configuration is missing: " + ", ".join(missing))
        if self.env.get("CI_DEBUG_TRACE", "").lower() in {"1", "true", "yes"}:
            raise PublishError("CI_DEBUG_TRACE must be disabled for release publication.")
        if (
            self.env["CI_SERVER_HOST"] != "git.tak.gov"
            or self.env["CI_PROJECT_PATH"] != CI_PROJECT_PATH
            or self.env["CI_COMMIT_BRANCH"] != "develop"
            or self.env["CI_PIPELINE_SOURCE"] != "push"
            or self.env["CI_COMMIT_REF_PROTECTED"].lower() != "true"
        ):
            raise PublishError("Only protected canonical develop push pipelines may publish.")

        commit_sha = self.env["CI_COMMIT_SHA"].lower()
        if not re.fullmatch(r"[0-9a-f]{40}", commit_sha):
            raise PublishError("The pipeline commit SHA is invalid.")

        directory = cwd or Path.cwd()
        artifacts = sorted(
            path
            for path in directory.glob("ATAK-Plugin-*-civ-release.apk")
            if path.is_file() and not path.is_symlink()
        )
        if len(artifacts) != 1:
            raise PublishError(
                "Expected exactly one CIV release APK from release_build_and_sign; "
                f"found {len(artifacts)}."
            )
        apk_path = artifacts[0]
        if apk_path.stat().st_size == 0:
            raise PublishError("The CIV release APK is empty.")
        self.apk_verifier(apk_path)
        digest = hashlib.sha256(apk_path.read_bytes()).hexdigest()
        size = apk_path.stat().st_size

        if self.api is None:
            self.api = GitHubApi(self.env["GITHUB_RELEASE_TOKEN"])
        self._assert_current_head(commit_sha)

        release_path = f"/repos/{GITHUB_REPOSITORY}/releases/tags/{RELEASE_TAG}"
        release = self.api.request_json("GET", release_path, allow_not_found=True)
        if release is None:
            release = self.api.request_json(
                "POST",
                f"/repos/{GITHUB_REPOSITORY}/releases",
                {
                    "tag_name": RELEASE_TAG,
                    "name": RELEASE_TITLE,
                    "body": (
                        "Rolling signed CIV APK from the canonical TAK Forge "
                        f"develop pipeline.\n\n{OWNERSHIP_MARKER}"
                    ),
                    "draft": True,
                    "prerelease": False,
                    "target_commitish": commit_sha,
                },
            )
        if not isinstance(release, dict):
            raise PublishError("GitHub returned an invalid rolling release.")
        release_body = release.get("body")
        if not isinstance(release_body, str) or OWNERSHIP_MARKER not in release_body:
            raise PublishError("The rolling release is not marked as publisher-owned.")
        release_id = release.get("id")
        if not isinstance(release_id, int):
            raise PublishError("GitHub returned an invalid rolling release.")

        assets = self.api.list_assets(release_id)
        matching_assets = [
            asset for asset in assets if asset.get("name") == STABLE_ASSET_NAME
        ]
        if len(matching_assets) > 1:
            raise PublishError(f"The rolling release has duplicate {STABLE_ASSET_NAME} assets.")
        stable = matching_assets[0] if matching_assets else None
        if stable is not None and not isinstance(stable.get("id"), int):
            raise PublishError("GitHub returned invalid metadata for the rolling APK.")

        candidate_name = f"dashboard-candidate-{self.env['CI_JOB_ID']}-{commit_sha[:8]}.apk"
        candidates = [asset for asset in assets if asset.get("name") == candidate_name]
        if len(candidates) > 1:
            raise PublishError(f"The rolling release has duplicate {candidate_name} assets.")
        if candidates:
            candidate = candidates[0]
            _validate_asset(self.api, candidate, candidate_name, size, digest)
        else:
            candidate = self.api.upload_asset(release, candidate_name, apk_path)
            _validate_asset(self.api, candidate, candidate_name, size, digest)

        self._assert_current_head(commit_sha)
        backup_name = f"dashboard-previous-{self.env['CI_JOB_ID']}-{commit_sha[:8]}.apk"
        old_asset_backed_up = False
        if stable is not None:
            self.api.request_json(
                "PATCH",
                f"/repos/{GITHUB_REPOSITORY}/releases/assets/{stable['id']}",
                {"name": backup_name},
            )
            old_asset_backed_up = True

        try:
            promoted = self.api.request_json(
                "PATCH",
                f"/repos/{GITHUB_REPOSITORY}/releases/assets/{candidate['id']}",
                {"name": STABLE_ASSET_NAME},
            )
            _validate_asset(self.api, promoted, STABLE_ASSET_NAME, size, digest)
        except PublishError:
            if old_asset_backed_up:
                self.api.request_json(
                    "PATCH",
                    f"/repos/{GITHUB_REPOSITORY}/releases/assets/{stable['id']}",
                    {"name": STABLE_ASSET_NAME},
                )
            raise

        self.api.request_json(
            "PATCH",
            f"/repos/{GITHUB_REPOSITORY}/releases/{release_id}",
            {"name": RELEASE_TITLE, "draft": False, "prerelease": False},
        )
        _update_rolling_tag(self.api, commit_sha)

        if old_asset_backed_up:
            self.api.delete_asset(stable["id"])
        return STABLE_ASSET_NAME


def main():
    try:
        asset_name = ReleasePublisher(os.environ).run()
    except PublishError as error:
        print("ERROR: " + str(error), file=sys.stderr)
        return 1
    print("Published verified signed CIV APK: " + asset_name)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
