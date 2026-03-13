/*
 * Copyright (C) 2018 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.wifirttscan;

import static com.example.android.wifirttscan.AccessPointRangingResultsActivity.SCAN_RESULT_EXTRA;

import android.Manifest;
import android.Manifest.permission;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.rtt.RangingRequest;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.LayoutManager;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.example.android.wifirttscan.MyAdapter.ScanResultClickListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays list of Access Points enabled with WifiRTT (to check distance). Requests permissions
 * if they are not approved via secondary splash screen explaining why they are needed.
 */
public class MainActivity extends AppCompatActivity implements ScanResultClickListener {

    private static final String TAG = "MainActivity";

    private boolean mPermissionApproved = false;

    List<ScanResult> mAccessPointsSupporting80211mc;

    private WifiManager mWifiManager;
    private WifiScanResultsCallback mWifiScanResultsCallback;

    private TextView mOutputTextView;
    private RecyclerView mRecyclerView;

    private MyAdapter mAdapter;

    long startTime;
    long endTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mOutputTextView = findViewById(R.id.access_point_summary_text_view);
        mRecyclerView = findViewById(R.id.recycler_view);

        // Improve performance if you know that changes in content do not change the layout size
        // of the RecyclerView
        mRecyclerView.setHasFixedSize(true);

        // use a linear layout manager
        LayoutManager layoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(layoutManager);

        mAccessPointsSupporting80211mc = new ArrayList<>();

        mAdapter = new MyAdapter(mAccessPointsSupporting80211mc, this);
        mRecyclerView.setAdapter(mAdapter);

        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mWifiScanResultsCallback = new WifiScanResultsCallback();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume()");
        super.onResume();

        mPermissionApproved = isPermissionGranted();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mWifiManager.registerScanResultsCallback(getMainExecutor(), mWifiScanResultsCallback);
            Log.d(TAG, "Registered WifiManager.ScanResultsCallback");
        } else {
            logToUi("Scan results callback requires Android 11+ (API 30).");
        }
    }

    private boolean isPermissionGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause()");
        super.onPause();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                mWifiManager.unregisterScanResultsCallback(mWifiScanResultsCallback);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Wifi scan callback was not registered.", e);
            }
        }
    }

    private void logToUi(final String message) {
        if (!message.isEmpty()) {
            Log.d(TAG, message);
            mOutputTextView.setText(message);
        }
    }

    @Override
    public void onScanResultItemClick(ScanResult scanResult) {
        Log.d(TAG, "onScanResultItemClick(): ssid: " + scanResult.SSID);

        Intent intent = new Intent(this, AccessPointRangingResultsActivity.class);
        intent.putExtra(SCAN_RESULT_EXTRA, scanResult);
        startActivity(intent);
    }

    public void onClickScanForAccessPoints(View view) {
        if (mPermissionApproved) {
            logToUi(getString(R.string.retrieving_access_points));

            // Start WiFi scan, log time. Results will be received in WifiScanResultsCallback.
            startTime = System.currentTimeMillis();
            Log.d(TAG, "WiFi scan started at: " + startTime);
            boolean success = mWifiManager.startScan();
            if (!success) {
                logToUi("WiFi scan failed to start (throttled?).");
            }

        } else {
            // Permission not granted. Request permission.
            Intent startIntent = new Intent(this, LocationPermissionRequestActivity.class);
            startActivity(startIntent);
        }
    }

    private class WifiScanResultsCallback extends WifiManager.ScanResultsCallback {

        private List<ScanResult> find80211mcSupportedAccessPoints(
                @NonNull List<ScanResult> originalList) {
            List<ScanResult> newList = new ArrayList<>();

            for (ScanResult scanResult : originalList) {
                if (scanResult.is80211mcResponder()) {
                    newList.add(scanResult);
                }

                // Show all RTT enabled APs.
                // if (newList.size() >= RangingRequest.getMaxPeers()) {
                //     break;
                // }
            }
            return newList;
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onScanResultsAvailable() {
            // Log the time when the scan finishes
            endTime = System.currentTimeMillis();
            Log.d(TAG, "WiFi scan finished at: " + endTime);
            Log.d(TAG, "WiFi scan duration: " + (endTime - startTime) + " ms");

            List<ScanResult> scanResults = mWifiManager.getScanResults();

            if (scanResults != null) {
                if (mPermissionApproved) {
                    mAccessPointsSupporting80211mc = scanResults;

                    // Deprecated, show all APs.
                    // mAccessPointsSupporting80211mc = find80211mcSupportedAccessPoints(scanResults);

                    mAdapter.swapData(mAccessPointsSupporting80211mc);

                    logToUi(
                            scanResults.size()
                                    + " APs discovered, "
                                    + mAccessPointsSupporting80211mc.size()
                                    + " RTT capable.");

                } else {
                    // TODO (jewalker): Add Snackbar regarding permissions
                    Log.d(TAG, "Permissions not allowed.");
                }
            }
        }
    }
}
