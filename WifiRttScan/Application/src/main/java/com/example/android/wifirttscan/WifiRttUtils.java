package com.example.android.wifirttscan;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.ResponderConfig;
import android.os.Build;

import androidx.core.content.ContextCompat;

import java.util.Collections;
import java.util.List;

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
        return buildAccessPointRequest(Collections.singletonList(scanResult));
    }

    static RangingRequest buildAccessPointRequest(List<ScanResult> scanResults) {
        RangingRequest.Builder builder = new RangingRequest.Builder();
        for (ScanResult scanResult : scanResults) {
            builder.addResponder(buildResponderConfig(scanResult));
        }
        return builder.build();
    }

    private static ResponderConfig buildResponderConfig(ScanResult scanResult) {
        ResponderConfig original = ResponderConfig.fromScanResult(scanResult);
        return new ResponderConfig.Builder()
                .setMacAddress(original.getMacAddress())
                .set80211mcSupported(true)
                .setChannelWidth(original.getChannelWidth())
                .setFrequencyMhz(original.getFrequencyMhz())
                .setCenterFreq0Mhz(original.getCenterFreq0Mhz())
                .setCenterFreq1Mhz(original.getCenterFreq1Mhz())
                .setPreamble(original.getPreamble())
                .build();
    }
}
