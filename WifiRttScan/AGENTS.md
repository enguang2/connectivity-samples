# AGENTS.md

## Scope
- Project is a single Android app module: `Application` (see `settings.gradle`), package `com.example.android.wifirttscan`.
- Purpose: scan Wi-Fi APs and run Wi-Fi RTT ranging against a selected AP (`README.md`, `MainActivity`, `AccessPointRangingResultsActivity`).

## Architecture and Data Flow
- Entry point is `MainActivity` (`Application/src/main/AndroidManifest.xml`).
- Scan flow: `onClickScanForAccessPoints()` -> `WifiManager.startScan()` -> `WifiScanResultsCallback.onScanResultsAvailable()` -> `MyAdapter.swapData()`.
- Selection flow: tap item in `MyAdapter` -> `onScanResultItemClick()` -> intent extra `SCAN_RESULT_EXTRA` (`ScanResult` parcelable).
- Ranging flow: `AccessPointRangingResultsActivity.startRangingRequest()` -> `WifiRttManager.startRanging()` -> `RttRangingResultCallback.onRangingResults()`.
- Continuous ranging uses `Handler.postDelayed(...)` with configurable period (`mMillisecondsDelayBeforeNewRangingRequest`).

## Project-Specific Conventions (Important)
- `MainActivity` currently assigns `mAccessPointsSupporting80211mc = scanResults;` (shows all APs). The `find80211mcSupportedAccessPoints(...)` path is present but commented out.
- Ranging intentionally rebuilds `ResponderConfig` and forces `.set80211mcSupported(true)` before request build; do not simplify to `addAccessPoint(...)` unless behavior change is intended.
- Permission gates are API-split:
  - API 33+: `NEARBY_WIFI_DEVICES`
  - API 28-32: `ACCESS_FINE_LOCATION`
  Keep checks aligned across `MainActivity`, `LocationPermissionRequestActivity`, and `AccessPointRangingResultsActivity`.
- Stats are rolling-window averages via circular overwrite indices (`mStatisticRangeHistoryEndIndex`, `mStatisticRangeSDHistoryEndIndex`), not unbounded accumulation.

## Build / Run Workflows
- Build from repo root with Gradle wrapper:
  - `./gradlew build`
  - `./gradlew :Application:assembleDebug`
  - `./gradlew :Application:installDebug`
- Module is named `Application` (not `app`), so use module-qualified tasks when targeting one module.
- `Application/build.gradle` defines `sourceSets` for `src/main`, `src/common`, `src/template`; this sample currently uses `src/main` only.

## Runtime Constraints and Integrations
- Required manifest feature: `android.hardware.wifi.rtt`.
- `WifiManager.ScanResultsCallback` path is Android 11+ (API 30+); pre-30 devices show a log/UI message and do not register scan callback.
- Core Android APIs: `WifiManager`, `WifiRttManager`, `RangingRequest`, `ResponderConfig`, `RangingResultCallback`.
- Dependencies are minimal AndroidX + Material in `Application/build.gradle`; no DI framework, no networking layer, no database.

## Validation Notes for Agents
- There are no committed tests in this sample; verify behavior on-device by running scan -> select AP -> observe ranging metrics update.
- If adding logic around scan/ranging cadence, watch for platform scan throttling (already hinted by `"WiFi scan failed to start (throttled?)"`).

