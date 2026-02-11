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

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import java.util.List;

/**
 * Foreground service that keeps FLP location requests running in the background.
 * Scan callbacks are also logged so FLP-vs-scan timing can be monitored with logcat.
 */
public class ContinuousFlpScanService extends Service {
    public static final String ACTION_START =
            "com.example.android.wifirttscan.action.START_CONTINUOUS_FLP_SCAN";
    public static final String ACTION_STOP =
            "com.example.android.wifirttscan.action.STOP_CONTINUOUS_FLP_SCAN";

    private static final String TAG = "ContinuousFlpScanSvc";
    private static final String CHANNEL_ID = "flp_scan_foreground_channel";
    private static final int NOTIFICATION_ID = 7001;

    private WifiManager mWifiManager;
    private FusedLocationProviderClient mFusedLocationProviderClient;
    private WifiScanResultsCallback mWifiScanResultsCallback;
    private LocationCallback mFlpLocationCallback;
    private boolean mRunning = false;

    private long mServiceStartElapsedMs;
    private long mLastFlpElapsedMs;
    private long mLastScanElapsedMs;

    public static Intent createStartIntent(Context context) {
        Intent intent = new Intent(context, ContinuousFlpScanService.class);
        intent.setAction(ACTION_START);
        return intent;
    }

    public static Intent createStopIntent(Context context) {
        Intent intent = new Intent(context, ContinuousFlpScanService.class);
        intent.setAction(ACTION_STOP);
        return intent;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        mWifiScanResultsCallback = new WifiScanResultsCallback();
        mFlpLocationCallback =
                new LocationCallback() {
                    @Override
                    public void onLocationResult(LocationResult locationResult) {
                        long now = SystemClock.elapsedRealtime();
                        long sinceStart = now - mServiceStartElapsedMs;
                        long sinceLast = mLastFlpElapsedMs == 0 ? -1 : (now - mLastFlpElapsedMs);
                        mLastFlpElapsedMs = now;

                        Log.i(
                                TAG,
                                "FLP_UPDATE sinceStartMs="
                                        + sinceStart
                                        + " sinceLastMs="
                                        + sinceLast
                                        + " locations="
                                        + locationResult.getLocations().size());
                        updateNotification(
                                "FLP running, last update "
                                        + (sinceLast < 0 ? "n/a" : sinceLast + " ms ago"));
                    }
                };
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopTracking();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification("Starting FLP + scan monitoring"));
        startTracking();
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopTracking();
        super.onDestroy();
    }

    @SuppressLint("MissingPermission")
    private void startTracking() {
        if (mRunning) {
            return;
        }
        mRunning = true;
        mServiceStartElapsedMs = SystemClock.elapsedRealtime();
        mLastFlpElapsedMs = 0;
        mLastScanElapsedMs = 0;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mWifiManager.registerScanResultsCallback(getMainExecutor(), mWifiScanResultsCallback);
        }

        LocationRequest locationRequest =
                new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 0)
                        .setMinUpdateIntervalMillis(0)
                        .setMaxUpdateDelayMillis(0)
                        .setWaitForAccurateLocation(false)
                        .setMinUpdateDistanceMeters(0)
                        .build();

        try {
            mFusedLocationProviderClient.requestLocationUpdates(
                    locationRequest, mFlpLocationCallback, getMainLooper());
            Log.i(TAG, "FLP request started.");
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission missing for FLP request.", e);
            updateNotification("Missing location permission");
            stopTracking();
            stopSelf();
        }
    }

    private void stopTracking() {
        if (!mRunning) {
            return;
        }
        mRunning = false;
        mFusedLocationProviderClient.removeLocationUpdates(mFlpLocationCallback);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                mWifiManager.unregisterScanResultsCallback(mWifiScanResultsCallback);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Scan callback was not registered.", e);
            }
        }
        Log.i(TAG, "FLP request stopped.");
    }

    private void updateNotification(String contentText) {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(contentText));
        }
    }

    private Notification buildNotification(String contentText) {
        Intent openIntent = new Intent(this, MainActivity.class);
        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntentFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent contentIntent =
                PendingIntent.getActivity(this, 0, openIntent, pendingIntentFlags);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("FLP background request active")
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_round_network_wifi_24px)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel =
                new NotificationChannel(
                        CHANNEL_ID, "FLP background scan", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Foreground service status for FLP and Wi-Fi scan monitoring");
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }

    private class WifiScanResultsCallback extends WifiManager.ScanResultsCallback {
        @Override
        @SuppressLint("MissingPermission")
        public void onScanResultsAvailable() {
            if (!mRunning) {
                return;
            }
            long now = SystemClock.elapsedRealtime();
            long sinceStart = now - mServiceStartElapsedMs;
            long sinceLast = mLastScanElapsedMs == 0 ? -1 : (now - mLastScanElapsedMs);
            mLastScanElapsedMs = now;

            List<ScanResult> scanResults;
            try {
                scanResults = mWifiManager.getScanResults();
            } catch (SecurityException e) {
                Log.e(TAG, "Missing permission for getScanResults().", e);
                return;
            }
            int totalAps = scanResults == null ? 0 : scanResults.size();
            int rttAps = 0;
            if (scanResults != null) {
                for (ScanResult scanResult : scanResults) {
                    if (scanResult.is80211mcResponder()) {
                        rttAps++;
                    }
                }
            }

            Log.i(
                    TAG,
                    "SCAN_CALLBACK sinceStartMs="
                            + sinceStart
                            + " sinceLastMs="
                            + sinceLast
                            + " totalAps="
                            + totalAps
                            + " rttAps="
                            + rttAps);
        }
    }
}
