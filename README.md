# Physiology Dashboard ATAK Plugin

## Summary
Physiology Dashboard is an ATAK plugin that displays biometric information received from a variety of wearables, including WearTAK. It shows team members' physiological status, such as heart rate, skin temperature, exertion, and battery level, along with alert and clear events, on a phone or tablet running ATAK.

Live and retained CoT readings use `detail/physiology/device`, with legacy `detail/biometrics/device` accepted only when `physiology` is absent. If both tags appear, physiology wins without duplicate telemetry processing. Alerts and cancellations also use `<physiology alertState="...">`, with legacy `<biometrics alertState="...">` still supported. Existing alert attributes, device children/attributes, and vital-value parsing are unchanged. Alert handling remains independent of telemetry selection: separate legacy alert metadata can accompany physiology telemetry, but when both tags carry alert metadata, only physiology is processed.

Primary readings appear in Exertion, Heart Rate, then Skin Temperature order. Unavailable values (including `-1`) are hidden. Tap the sort button to cycle sort modes or hold it to select a named sort, including ascending/descending sorts for all three readings. Unavailable values sort last in either direction.

Fractional exertion values from WearTAK (for example `0.22`) are normalized to percentages (`22%`). WEAROS skin-temperature CoT values are Fahrenheit and are converted to the dashboard's Celsius storage before display conversion to Fahrenheit.

Units with no available physiological readings are hidden, even if battery data is present. Units with active alerts or retained alert history remain visible so alerts are not concealed.

Repeated deliveries of an alert through multiple TAK servers are counted and displayed once per device, alert UID, and alert/clear state. Cancellations are recorded separately from the original alert. Each device retains the latest 256 event identities in memory, independently of its ten visible history entries; events without an alert UID are not deduplicated.

Biometric collection starts when the plugin loads and continues while the dashboard is closed. At startup, the plugin also reads biometric events retained on ATAK map items. Original expiry times are preserved when available; older opaque biometric details without an original expiry are conservatively marked stale using ATAK's retained update time. Cached readings do not create new CSV samples or replay test alerts, and existing stale-device removal still applies. This is local catch-up only, not a query of server history.

## Rights
Unlimited Rights granted to TAK Product Center.

## Point of Contact
Alex Gorsuch on chat.tak.gov or Signal.

## Repositories
The TAK Forge repository is canonical. GitHub is a secondary repository.

## TPP Build and Signing
The submission ZIP contains source for TPP to build and sign, not an installable or signed APK. It targets ATAK 5.3.0 CIV and includes alert deduplication, dashboard header cleanup, and local biometric catch-up.

Use the matching ATAK 5.3.0 SDK, Android SDK platform 33, and build tools 30.0.3. Copy `template.local.properties` to `local.properties` and configure the local Android SDK path and authenticated TAK Maven repository. Alternatively, supply `takdev.plugin` and the matching SDK libraries through the normal local TAK SDK setup. SDK binaries, local configuration, credentials, generated outputs, and signing keys are excluded from the ZIP.

Run `gradlew.bat :app:testCivDebugUnitTest :app:assembleCivDebug` for local checks. TPP must configure its release signing keystore before running `gradlew.bat :app:assembleCivRelease`; the existing release configuration expects `app/build/android_keystore`. Release signing and device compatibility require the TPP environment and matching ATAK version.

## TAK Forge CI/CD
`.gitlab-ci.yml` uses the same shared `devsecops/gitlab-ci-common` build-and-sign
template as the WearTAK Companion plugin. Push pipelines on protected canonical
`main` run `release_build_and_sign` with JDK 17 for the `civ` variant. Other
branches, unprotected refs, non-push pipelines, and other Forge projects do not
run this build job. The dashboard continues to target ATAK 5.3.0.

The shared launcher handles authenticated TAK dependency access, building,
signing, and build/report artifacts. This requires access to the shared template,
TAK runners, artifact repositories, and signing service. Credentials and signing
keys must stay in the Forge CI services/configuration, not in the repository.
Both the normal `takrepo.url`, `takrepo.user`, and `takrepo.password` Gradle/local
properties and the launcher's underscore-form `takrepo_url`, `takrepo_user`, and
`takrepo_password` properties are supported; dotted properties take precedence.

The build accepts a root-level, untracked `signing.properties` containing
`storeFile`, `storePassword`, `keyAlias`, and `keyPassword`, as used by the shared
launcher. A relative `storeFile` is resolved from `app`. Without this file,
existing local debug signing and the TPP release-keystore defaults are preserved.

The shared template exposes manual `release_internal` and
`distribute_resources` jobs on protected branches. As with Companion, building
does not automatically distribute the plugin to TAK production repositories. Production publication
uses the shared template's protected maintenance-branch and plugin-metadata
requirements; a successful main build is not production approval.

## Stable rolling GitHub pipeline release

`publish_github_pipeline_release` publishes only the signed CIV release APK from
the successful `release_build_and_sign` job in the same pipeline. It runs in
`.post`, after all earlier stages succeed, only for push pipelines on protected
canonical `git.tak.gov/aegorsuch/atak-plugin-physiology-dashboard` `main`
(project **6557**). The independent `test`-stage publisher tests must also pass.
`main` is treated as stable for this distribution: the release title is
**TAK.gov Forge CI/CD Pipeline Release (<APK version>-p<project pipeline IID>)**,
for example **TAK.gov Forge CI/CD Pipeline Release (0.35-p2)**, with
`draft=false` and `prerelease=false`. The version comes from the signed APK,
not the ATAK SDK version. The visible body follows the WearOS template exactly:
a stable main-channel introduction, five bullets (APK version/code, global
pipeline ID/project IID, bare pipeline URL, full source SHA, SHA256), and the
fixed-anchor installation/source disclaimer. It contains no signing-job bullet,
extra download link or production-approval line. Manual notes outside managed
boundaries remain untouched. The native APK download label is readable; ownership metadata stays in
hidden comments; new filenames are readable and legacy filenames remain unchanged. Generated notes use
managed boundaries so manual notes outside them survive updates.
This does not change the Forge production-release approval process.

The only destination is `aegorsuch/atak-plugin-physiology-dashboard` (public),
using the fixed `tak-gov-pipeline` anchor. Company publishing is disabled:
`Ascent-Integrated-Tech/biometrics-atak-plugin` is rejected by the publisher,
including repository overrides. Historical company releases and tags are not
modified. The canonical Forge project is **internal**. Do not change repository
visibility as part of publisher setup.

`publish_github_pipeline_release` uses only `GITHUB_PERSONAL_RELEASE_TOKEN`
in the `github-personal-rolling-release` environment. The personal token must
be protected, masked, and fine-grained to Contents read/write on only
`aegorsuch/atak-plugin-physiology-dashboard`. The job pins its repository target
to that personal repository; the same restriction is enforced in code.
Publication is automatic on eligible main pushes; `PUBLISH_PERSONAL_GITHUB`
is no longer consulted. A missing personal token fails closed; the legacy
company `GITHUB_RELEASE_TOKEN` is never used as a fallback.

Before each personal publication, the job checks that the pipeline SHA is still
the canonical protected `main` tip, fetches the personal mirror's `main`
branch, and permits only a Git fast-forward to that SHA. It pushes only
`refs/heads/main`, without force or tags, then verifies the canonical and
personal branch SHAs still match before publishing. A divergent, stale,
changed-during-sync, or failed push stops the job rather than rewriting the
mirror. One GitLab resource group serializes sync and release updates.
The personal repository must be active, public, and default to `main`.
The personal sync requires a full-depth canonical checkout to verify ancestry.

### One-time setup (administrator approval required)

1. Review distribution authorization for the signed APK and the expected TAK
   signing certificate fingerprint. Successful signing alone does not establish
   authorization to distribute through GitHub.
2. Precreate the dedicated `tak-gov-pipeline` tag at an existing GitHub commit.
   The publisher never creates or moves Git refs; source pipeline identity is
   recorded in the release body, not inferred from this fixed tag. GitHub
   generated source archives therefore describe that tag, not the APK source.
   Do not repurpose an existing numbered release/tag. If the rolling release
   already exists without this publisher's ownership marker, setup fails closed.
   Existing fixed tag targets, including historical company tags, stay unchanged.
3. Provision a fine-grained CI-only GitHub token owned by `aegorsuch`, restricted
   to **aegorsuch/atak-plugin-physiology-dashboard**, with **Contents: read and
   write** (Metadata read is implicit), no other permissions. Store it as
   `GITHUB_PERSONAL_RELEASE_TOKEN`, protected/masked and scoped exactly to
   `github-personal-rolling-release`, with Contents read/write only on
   that repository. Branch rules must permit its non-force fast-forward updates
   to personal `main`. No Workflows, Administration, or organization permissions
   are requested; if repository rules reject the sync, fail closed rather than
   expanding privileges automatically. Set an expiry and rotation owner.
   No enable flag or company token is required. Never copy a workstation
   `gh`/`glab` credential into CI. This code change does not provision tokens or
   modify Forge variables; administrators must verify their configuration.
4. Set protected, environment-scoped `TAK_SIGNER_SHA256` to the independently
   approved lowercase SHA-256 certificate fingerprint. The observed signer for
   the latest inspected build is
   `f24a38057275fcecf67be975ab803d12f75dc23581bef69cba9eb03a15bb8c17`.
   Review signing rotations explicitly rather than automatically trusting the
   certificate bundled with an APK.
5. Keep canonical `main` protected and reserve the rolling release for this publisher.
   The runner needs the Node 22 image, Debian package access for `apksigner` and
   `aapt`, GitHub API/upload/asset download access, and CI job-token read access to
   the canonical `repository/commits/main` endpoint. This Commits API accepts
   job-token authentication; the Branches API does not. No extra GitLab PAT is used.
   Run `node --test ci/*.test.mjs` before enabling.

The job downloads the signing job's artifacts through GitLab's `dependencies`,
but uploads only one allowlisted root-level dashboard `*-civ-release.apk`.
`apksigner` must verify v2/v3 signing and the pinned single certificate; `aapt`
must identify `com.aitech.atak.biometrics.plugin` without the debug flag. The APK
version must contain the pipeline's short SHA. AABs, keys, certificates, reports,
logs, mappings, SBOMs, source/debug archives and unrelated APKs are never uploaded.
GitHub's automatically generated source links cannot be disabled by this job.

The GitLab resource group `dashboard-github-rolling-release` serializes updates.
It is not a lock against out-of-band manual edits. The
personal sync requires a full-depth checkout, fetches only personal `main`,
checks that its tip is an ancestor of the current canonical pipeline SHA, and
pushes only `main` without force or tags. A pre-sync race check, post-sync
canonical and personal SHA checks, and the publisher's additional freshness
checks stop stale or divergent updates. The publisher-owned release marker also
records the project and pipeline IID so an older pipeline cannot overwrite a
newer release, including reruns of the same commit.

New APKs and native download labels use
`Physiology-Dashboard-<version>-p<IID>.apk`, without duplicating an existing
matching pipeline suffix. Hashes stay in the hidden ownership marker and the
separate visible SHA-256 field, not the download label. Cleanup verifies old
owned asset bytes against their recorded hash before deletion. The publisher
recognizes legacy filenames and ownership labels as well as the new format.
Retries reuse existing assets without renaming them, so existing asset IDs,
names and URLs are unchanged.
The publisher downloads the new GitHub asset, verifies its size and SHA-256,
updates stable release metadata, and reads it back before deleting **only**
older APKs matching both its strict filename and ownership label on that
release. Numbered releases/tags and unrelated assets are untouched. It never
deletes the rolling release or tag. Failed uploads/verification/metadata updates
leave the usable old APK intact. An initial release remains draft until the APK
is verified. A failed or interrupted upload can leave an orphaned candidate;
an incomplete same-name candidate fails closed and requires approved manual
inspection, not preemptive deletion. Cleanup failures fail the job explicitly
while leaving the newly verified APK usable. Retries reuse and reverify an
already uploaded candidate.

### Rolling release policy

The personal stable rolling release is published at its `tak-gov-pipeline`
tag and kept marked as GitHub's **Latest** release. Successful pushes to the
protected canonical TAK.gov `main` branch publish the newest signed CIV APK
after the signing job and publisher tests succeed. The release title includes
the APK version and project pipeline IID. It remains a stable release
(`prerelease=false`), not an approval for TAK production distribution.

Each publication verifies the signed APK, uploads only that installable APK, and
records its canonical source commit, pipeline, and SHA-256 in the release notes.
The `tak-gov-pipeline` tag is a fixed release anchor; GitHub's generated source
archives refer to that tag, not necessarily to the source commit for the APK.
Use the source commit recorded in the release notes as the authoritative build
source. The publisher preserves numbered releases, unrelated assets, and
user-authored release notes outside its managed section. Only the personal
destination receives new releases; historical company releases and tags remain
untouched.
