import hashlib
import tempfile
import unittest
from pathlib import Path

from scripts.publish_github_release import (
    GITHUB_REPOSITORY,
    OWNERSHIP_MARKER,
    RELEASE_TAG,
    RELEASE_TITLE,
    STABLE_ASSET_NAME,
    PublishError,
    ReleasePublisher,
)


COMMIT_SHA = "a" * 40
APK_BYTES = b"verified signed CIV APK"
APK_NAME = (
    "ATAK-Plugin-PhysiologyDashboard-0.35-aaaaaaaa-5.3.0-civ-release.apk"
)


def environment(**updates):
    values = {
        "CI_SERVER_HOST": "git.tak.gov",
        "CI_PROJECT_PATH": "aegorsuch/atak-plugin-physiology-dashboard",
        "CI_COMMIT_BRANCH": "develop",
        "CI_PIPELINE_SOURCE": "push",
        "CI_COMMIT_REF_PROTECTED": "true",
        "CI_COMMIT_SHA": COMMIT_SHA,
        "CI_REPOSITORY_URL": (
            "https://git.tak.gov/aegorsuch/atak-plugin-physiology-dashboard.git"
        ),
        "CI_JOB_ID": "77",
        "GITHUB_RELEASE_TOKEN": "test-token-not-a-credential",
    }
    values.update(updates)
    return values


class FakeGitHubApi:
    def __init__(
        self,
        *,
        branch_sha=COMMIT_SHA,
        release_body=None,
        assets=None,
        corrupt_upload=False,
        fail_promotion=False,
    ):
        self.branch_sha = branch_sha
        self.release = None
        self.assets = {}
        self.next_asset_id = 1
        self.corrupt_upload = corrupt_upload
        self.fail_promotion = fail_promotion
        self.calls = []
        self.writes = []
        self.events = []
        self.tag_ref = None
        if release_body is not None:
            self.release = self._release(release_body)
        for name, content in (assets or {}).items():
            self.add_asset(name, content)

    @staticmethod
    def _release(body):
        return {
            "id": 17,
            "upload_url": (
                f"https://uploads.github.com/repos/{GITHUB_REPOSITORY}/"
                "releases/17/assets{?name,label}"
            ),
            "name": "Existing release title",
            "body": body,
            "draft": False,
            "prerelease": True,
        }

    def add_asset(self, name, content):
        asset_id = self.next_asset_id
        self.next_asset_id += 1
        asset = {
            "id": asset_id,
            "name": name,
            "state": "uploaded",
            "size": len(content),
            "digest": "sha256:" + hashlib.sha256(content).hexdigest(),
        }
        self.assets[asset_id] = [asset, content]
        return asset

    def request_json(self, method, path, payload=None, allow_not_found=False):
        self.calls.append((method, path, payload))
        if path.endswith("/commits/develop"):
            return {"sha": self.branch_sha}
        if path.endswith(f"/releases/tags/{RELEASE_TAG}"):
            return self.release
        if path.endswith("/releases") and method == "POST":
            self.writes.append(("create-release", payload))
            self.release = dict(payload, **self._release(payload["body"]))
            return self.release
        if path.endswith("/releases/17/assets?per_page=100&page=1"):
            return [entry[0] for entry in self.assets.values()]
        if path.endswith("/releases/17") and method == "PATCH":
            self.writes.append(("update-release", payload))
            self.release.update(payload)
            return self.release
        if "/releases/assets/" in path and method == "PATCH":
            asset_id = int(path.rsplit("/", 1)[1])
            if self.fail_promotion and payload["name"] == STABLE_ASSET_NAME:
                self.fail_promotion = False
                raise PublishError("Simulated asset promotion failure.")
            self.writes.append(("rename-asset", asset_id, payload["name"]))
            self.assets[asset_id][0]["name"] = payload["name"]
            return self.assets[asset_id][0]
        if path.endswith(f"/git/ref/tags/{RELEASE_TAG}"):
            if method == "GET":
                return self.tag_ref
            self.writes.append(("move-tag", payload))
            self.tag_ref = {"object": {"sha": payload["sha"]}}
            return self.tag_ref
        if path.endswith("/git/refs") and method == "POST":
            self.writes.append(("create-tag", payload))
            self.tag_ref = {"object": {"sha": payload["sha"]}}
            return self.tag_ref
        raise AssertionError(f"Unexpected GitHub request: {method} {path}")

    def list_assets(self, release_id):
        return [entry[0] for entry in self.assets.values()]

    def upload_asset(self, release, name, asset_path):
        self.writes.append(("upload", name))
        self.events.append("upload")
        content = asset_path.read_bytes()
        if self.corrupt_upload:
            content = b"x" + content[1:]
        return self.add_asset(name, content)

    def download_asset(self, asset_id):
        return self.assets[asset_id][1]

    def delete_asset(self, asset_id):
        self.writes.append(("delete-asset", asset_id))
        del self.assets[asset_id]


class ReleasePublisherTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)
        self.apk_path = Path(self.temp_dir.name, APK_NAME)
        self.apk_path.write_bytes(APK_BYTES)
        self.env = environment()

    def publisher(self, api, **kwargs):
        return ReleasePublisher(
            self.env,
            api=api,
            head_reader=kwargs.get("head_reader", lambda _: COMMIT_SHA),
            apk_verifier=kwargs.get(
                "apk_verifier", lambda _: api.events.append("signature")
            ),
        )

    def test_creates_stable_release_with_signed_apk_and_rolling_tag(self):
        api = FakeGitHubApi()

        result = self.publisher(api).run(Path(self.temp_dir.name))

        self.assertEqual(result, STABLE_ASSET_NAME)
        self.assertEqual(api.release["name"], RELEASE_TITLE)
        self.assertFalse(api.release["draft"])
        self.assertFalse(api.release["prerelease"])
        self.assertEqual(api.release["tag_name"], RELEASE_TAG)
        self.assertIn(OWNERSHIP_MARKER, api.release["body"])
        self.assertEqual(api.tag_ref["object"]["sha"], COMMIT_SHA)
        published = list(api.assets.values())
        self.assertEqual([item[0]["name"] for item in published], [STABLE_ASSET_NAME])
        self.assertEqual(published[0][1], APK_BYTES)
        self.assertLess(api.events.index("signature"), api.events.index("upload"))

    def test_replaces_only_rolling_asset_and_preserves_release_notes(self):
        notes = f"Existing release notes.\n\n{OWNERSHIP_MARKER}"
        api = FakeGitHubApi(
            release_body=notes,
            assets={
                STABLE_ASSET_NAME: b"old signed APK",
                "release-notes.txt": b"keep this asset",
            },
        )
        old_id = next(
            asset["id"]
            for asset, _ in api.assets.values()
            if asset["name"] == STABLE_ASSET_NAME
        )

        self.publisher(api).run(Path(self.temp_dir.name))

        self.assertEqual(api.release["body"], notes)
        self.assertNotIn(old_id, api.assets)
        self.assertIn(
            "release-notes.txt",
            [asset["name"] for asset, _ in api.assets.values()],
        )
        self.assertIn(
            STABLE_ASSET_NAME,
            [asset["name"] for asset, _ in api.assets.values()],
        )
        self.assertIn(("delete-asset", old_id), api.writes)

    def test_missing_token_fails_before_network_access(self):
        api = FakeGitHubApi()
        self.env["GITHUB_RELEASE_TOKEN"] = ""
        publisher = self.publisher(api, head_reader=lambda _: self.fail("unexpected"))

        with self.assertRaisesRegex(PublishError, "GITHUB_RELEASE_TOKEN"):
            publisher.run(Path(self.temp_dir.name))

        self.assertEqual(api.calls, [])
        self.assertEqual(api.writes, [])

    def test_refuses_unowned_release_without_modifying_it(self):
        api = FakeGitHubApi(release_body="Release notes not owned by this publisher.")

        with self.assertRaisesRegex(PublishError, "not marked as publisher-owned"):
            self.publisher(api).run(Path(self.temp_dir.name))

        self.assertFalse(api.writes)

    def test_refuses_stale_github_mirror_without_writing(self):
        api = FakeGitHubApi(branch_sha="0" * 40)

        with self.assertRaisesRegex(PublishError, "mirror does not match"):
            self.publisher(api).run(Path(self.temp_dir.name))

        self.assertFalse(api.writes)

    def test_refuses_when_canonical_branch_has_advanced(self):
        api = FakeGitHubApi()
        publisher = self.publisher(api, head_reader=lambda _: "0" * 40)

        with self.assertRaisesRegex(PublishError, "no longer the canonical develop head"):
            publisher.run(Path(self.temp_dir.name))

        self.assertEqual(api.calls, [])

    def test_refuses_unsigned_apk_before_contacting_github(self):
        api = FakeGitHubApi()

        def reject_unsigned(_):
            raise PublishError("The release APK failed Android signature verification.")

        with self.assertRaisesRegex(PublishError, "signature verification"):
            self.publisher(api, apk_verifier=reject_unsigned).run(
                Path(self.temp_dir.name)
            )

        self.assertEqual(api.calls, [])
        self.assertFalse(api.writes)

    def test_corrupt_upload_does_not_replace_existing_asset(self):
        api = FakeGitHubApi(
            release_body=OWNERSHIP_MARKER,
            assets={STABLE_ASSET_NAME: b"old signed APK"},
            corrupt_upload=True,
        )
        old_id = next(iter(api.assets))

        with self.assertRaisesRegex(PublishError, "SHA-256 digest"):
            self.publisher(api).run(Path(self.temp_dir.name))

        self.assertIn(old_id, api.assets)
        self.assertEqual(api.assets[old_id][0]["name"], STABLE_ASSET_NAME)
        self.assertFalse(any(write[0] == "rename-asset" for write in api.writes))
        self.assertFalse(any(write[0] == "delete-asset" for write in api.writes))

    def test_failed_promotion_restores_previous_asset(self):
        api = FakeGitHubApi(
            release_body=OWNERSHIP_MARKER,
            assets={STABLE_ASSET_NAME: b"old signed APK"},
            fail_promotion=True,
        )
        old_id = next(iter(api.assets))

        with self.assertRaisesRegex(PublishError, "promotion failure"):
            self.publisher(api).run(Path(self.temp_dir.name))

        self.assertEqual(api.assets[old_id][0]["name"], STABLE_ASSET_NAME)
        self.assertFalse(any(write[0] == "delete-asset" for write in api.writes))
        self.assertEqual(
            [write[2] for write in api.writes if write[0] == "rename-asset"],
            [
                f"dashboard-previous-77-{COMMIT_SHA[:8]}.apk",
                STABLE_ASSET_NAME,
            ],
        )

    def test_non_protected_pipeline_is_rejected_before_network_access(self):
        api = FakeGitHubApi()
        self.env["CI_COMMIT_REF_PROTECTED"] = "false"

        with self.assertRaisesRegex(PublishError, "Only protected canonical"):
            self.publisher(api, head_reader=lambda _: self.fail("unexpected")).run(
                Path(self.temp_dir.name)
            )

        self.assertEqual(api.calls, [])


if __name__ == "__main__":
    unittest.main()
