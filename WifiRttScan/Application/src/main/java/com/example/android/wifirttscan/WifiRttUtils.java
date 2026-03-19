package com.example.android.wifirttscan;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.ResponderConfig;
import android.os.Build;

import androidx.core.content.ContextCompat;

final class WifiRttUtils {

    private WifiRttUtils() {
    }

    static boolean hasRangingPermission(Context context) {
        String permission =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        ? Manifest.permission.NEARBY_WIFI_DEVICES
                        : Manifest.permission.ACCESS_FINE_LOCATION;
        return ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    static RangingRequest buildSingleAccessPointRequest(ScanResult scanResult) {
        ResponderConfig original = ResponderConfig.fromScanResult(scanResult);
        ResponderConfig modified = new ResponderConfig.Builder()
                .setMacAddress(original.getMacAddress())
                .set80211mcSupported(true)
                .setChannelWidth(original.getChannelWidth())
                .setFrequencyMhz(original.getFrequencyMhz())
                .setCenterFreq0Mhz(original.getCenterFreq0Mhz())
                .setCenterFreq1Mhz(original.getCenterFreq1Mhz())
                .setPreamble(original.getPreamble())
                .build();

        return new RangingRequest.Builder().addResponder(modified).build();
    }
}
