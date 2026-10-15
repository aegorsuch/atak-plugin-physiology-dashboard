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
