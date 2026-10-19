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

The protected `develop` push pipeline also runs
`publish_github_pipeline_release` after the signed build succeeds. It publishes
only the signed CIV release APK to the GitHub mirror's `takgov-pipeline` rolling
release, titled **TAK Forge Automated Pipeline**, using the stable asset name
`ATAK-Plugin-PhysiologyDashboard-latest.apk`. The publisher verifies the local
APK signature and uploaded bytes, and requires both canonical Forge `develop`
and the GitHub `develop` mirror to match the pipeline commit. It does not publish
debug APKs, AABs, source archives, or CI reports, and it leaves unrelated release
assets, release notes, numbered releases, and other tags untouched.

To enable publishing, create a protected, masked `GITHUB_RELEASE_TOKEN` CI/CD
variable in the canonical TAK Forge project. Use a fine-grained GitHub token
scoped only to `Ascent-Integrated-Tech/biometrics-atak-plugin` with **Contents:
read and write** permission. The publisher fails with a clear error if this
variable is missing. Never put the token in repository files or logs. Run the
offline mocked-API tests with
`python3 -m unittest discover -s tests -p 'test_publish_github_release.py'`.
