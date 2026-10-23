# Physiology Dashboard ATAK Plugin

## Summary
Physiology Dashboard is an ATAK plugin that displays biometric information received from a variety of wearables, including WearTAK. It shows team members' physiological status, such as heart rate, skin temperature, exertion, and battery level, along with alert and clear events, on a phone or tablet running ATAK.

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
template as the WearTAK Companion plugin. Pushes to `develop` run
`release_build_and_sign` with JDK 17 for the `civ` variant. Other branches do not
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
does not automatically publish or distribute the plugin. Production publication
uses the shared template's protected maintenance-branch and plugin-metadata
requirements; a successful develop build is not production approval.

## Stable rolling GitHub pipeline release

`publish_github_pipeline_release` publishes only the signed CIV release APK from
the successful `release_build_and_sign` job in the same pipeline. It runs in
`.post`, after all earlier stages succeed, only for push pipelines on protected
canonical `git.tak.gov/aegorsuch/atak-plugin-physiology-dashboard` `develop`
(project **6557**). The independent `.pre` publisher tests must also pass.
`develop` is treated as stable for this distribution: the release title is
**TAK.gov Forge CI/CD Pipeline Release (<APK version>-p<project pipeline IID>)**,
for example **TAK.gov Forge CI/CD Pipeline Release (0.35-p2)**, with
`draft=false` and `prerelease=false`. The version comes from the signed APK,
not the ATAK SDK version. Notes include the full APK version and version code,
global pipeline ID and project IID, canonical links, full source SHA and separate
SHA-256. The native APK download label is readable; ownership metadata stays in
hidden comments and the unchanged hash-based filename. Generated notes use
managed boundaries so manual notes outside them survive updates.
This does not change the Forge production-release approval process.

Destination: `Ascent-Integrated-Tech/biometrics-atak-plugin` on GitHub, currently
**private**. Downloaders need repository access. The canonical Forge project is
**internal**. Do not change either visibility as part of publisher setup.

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
3. Provision a fine-grained CI-only GitHub token restricted to **this repository**,
   with **Contents: read and write** (Metadata read is implicit), no other
   permissions. Obtain organization approval/SSO authorization if required.
   Store it as `GITHUB_RELEASE_TOKEN`, **protected**, **masked**, hidden where
   supported, environment-scoped to `github-rolling-release`. Set an expiry and
   rotation owner. Never copy a workstation `gh`/`glab` credential into CI.
4. Set protected, environment-scoped `TAK_SIGNER_SHA256` to the independently
   approved lowercase SHA-256 certificate fingerprint. The observed signer for
   the latest inspected build is
   `f24a38057275fcecf67be975ab803d12f75dc23581bef69cba9eb03a15bb8c17`.
   Review signing rotations explicitly rather than automatically trusting the
   certificate bundled with an APK.
5. Keep `develop` protected and reserve the rolling release for this publisher.
   The runner needs the Node 22 image, Debian package access for `apksigner` and
   `aapt`, GitHub API/upload/asset download access, and CI job-token read access to
   the canonical `repository/commits/develop` endpoint. This Commits API accepts
   job-token authentication; the Branches API does not. No extra GitLab PAT is used.
   Run `node --test ci/publish-github-release.test.mjs` before enabling.

The job downloads the signing job's artifacts through GitLab's `dependencies`,
but uploads only one allowlisted root-level dashboard `*-civ-release.apk`.
`apksigner` must verify v2/v3 signing and the pinned single certificate; `aapt`
must identify `com.aitech.atak.biometrics.plugin` without the debug flag. The APK
version must contain the pipeline's short SHA. AABs, keys, certificates, reports,
logs, mappings, SBOMs, source/debug archives and unrelated APKs are never uploaded.
GitHub's automatically generated source links cannot be disabled by this job.

The GitLab resource group `dashboard-github-rolling-release` serializes updates.
A live canonical branch-SHA check rejects obsolete commits; the publisher-owned
release marker records the project and pipeline IID so an older pipeline cannot
overwrite a newer release, including reruns of the same commit. All publishers
must use this resource group; it is not a lock against out-of-band manual edits.

New APKs and native download labels use
`Physiology-Dashboard-<version>-p<IID>.apk`, without duplicating an existing
matching pipeline suffix. Hashes stay in the hidden ownership marker and in
the separate visible SHA-256 field, not the download label. Cleanup verifies
old owned asset bytes against their recorded hash before deletion.
The publisher recognizes legacy filenames and ownership labels as well as the
new format. Retries reuse existing assets without renaming them, so existing
asset IDs, names and URLs are unchanged.
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

### Verified existing release provenance

As of 2026-10-23, the newest successful canonical develop pipeline is
[1784305](https://git.tak.gov/aegorsuch/atak-plugin-physiology-dashboard/-/pipelines/1784305)
(project IID 2), signing job
[7548996](https://git.tak.gov/aegorsuch/atak-plugin-physiology-dashboard/-/jobs/7548996),
commit `e07395d455adbb46816cc008ec2c28057b1dc335`.
Its `ATAK-Plugin-atak-plugin-physiology-dashboard-0.35-e07395d4-5.3.0-civ-release.apk`
is **30,513,260 bytes**, version **0.35 (e07395d4) - [5.3.0]**, version code
`1792185138`, min SDK 24, target SDK 33. Local Android tooling verified v2/v3
signatures, the certificate fingerprint above, expected package and no debug
flag. The existing GitHub asset was downloaded and independently verified to
match this size, version, version code, signer and APK SHA-256:
`aa53efa38d976a5aca5a2d9f8de88826546a082c5f7b99b210dc1ea02229a7b4`.
The signing artifact currently expires **2027-02-23 21:35 UTC**.
The fixed GitHub tag points to `7d40d79b17211f755ccad7afa02ab086a8fa916e`,
not the APK's canonical source commit above; this is why the release notes
explicitly distinguish generated source archives from the APK source.

**Published with explicit user approval on 2026-10-18:**
[TAK.gov CI/CD Pipeline Release](https://github.com/Ascent-Integrated-Tech/biometrics-atak-plugin/releases/tag/tak-gov-pipeline).
The one-time publication rechecked the newest successful canonical pipeline,
signing job and branch SHA, reverified the APK signature/package/digest, uploaded
only the APK, downloaded and hash-verified the GitHub asset, and read back stable
release metadata (`draft=false`, `prerelease=false`). The existing numbered tags
were preserved and repository visibility remains private.

The dedicated fixed `tak-gov-pipeline` tag was created at the existing GitHub
develop commit `7d40d79b17211f755ccad7afa02ab086a8fa916e`, not the newer canonical
APK source. The release body records the authoritative Forge commit above.
The publisher-owned asset is
`takgov-6557-2-aa53efa38d976a5aca5a2d9f8de88826546a082c5f7b99b210dc1ea02229a7b4.apk`.

**Automatic future publication is not enabled yet.** CI credential/signing-pin
setup and approval/merge of this automation remain outstanding. No CI variables,
access settings, pipeline triggers or source commits were changed by the
one-time release. This existing pipeline predates the publisher and did not run
it retroactively. Do not fake CI variables to bypass normal provenance checks.
