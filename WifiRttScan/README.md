
Android WifiRttScan Sample
===================================

Sample demonstrates best practices for using WifiRTT APIs in Android. Also, this is a a useful
application for testing Wifi-RTT enabled phones and access points and validating the estimated
distance is close to the actual distance between them.

Introduction
------------

Steps for trying out the sample:
* Compile and install the mobile app onto your mobile device (for mobile scenario).

This sample demonstrates best practices for using the WifiRtt APIs in Android. The main activity
triggers a WiFi scan using [WifiManager.startScan()][1] and receives results via the
[WifiManager.ScanResultsCallback][4] (introduced in Android R / API 30), replacing the legacy
`SCAN_RESULTS_AVAILABLE_ACTION` broadcast intent approach. The scan results list all access points
that are WifiRtt (802.11mc) enabled. By clicking on one of the access points, another activity will
launch and initiate [RangingRequest][2] via the [WifiRttManager][3]. The activity will display many
of the details returned from the access point including the distance between the access point and
the phone.

[1]: https://developer.android.com/reference/android/net/wifi/WifiManager#startScan()
[2]: https://developer.android.com/reference/android/net/wifi/rtt/RangingRequest
[3]: https://developer.android.com/reference/android/net/wifi/rtt/WifiRttManager
[4]: https://developer.android.com/reference/android/net/wifi/WifiManager.ScanResultsCallback

Pre-requisites
--------------

- Android SDK 30
- Android Build Tools v28.0.3
- Android Support Repository

Screenshots
-------------

<img src="screenshots/main1.png" height="400" alt="Screenshot"/> <img src="screenshots/main2.png" height="400" alt="Screenshot"/> <img src="screenshots/main3.png" height="400" alt="Screenshot"/> 

Getting Started
---------------

This sample uses the Gradle build system. To build this project, use the
"gradlew build" command or use "Import Project" in Android Studio.

Support
-------

- Stack Overflow: http://stackoverflow.com/questions/tagged/android

If you've found an error in this sample, please file an issue:
https://github.com/android/connectivity

Patches are encouraged, and may be submitted by forking this project and
submitting a pull request through GitHub. Please see CONTRIBUTING.md for more details.
