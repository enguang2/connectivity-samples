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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import java.util.List;

/**
 * Keeps continuous Wi-Fi scan loop running while app is in the background.
 */
public class ContinuousWifiScanService extends Service {
    public static final String ACTION_START =
            "com.example.android.wifirttscan.action.START_CONTINUOUS_SCAN";
    public static final String ACTION_STOP =
            "com.example.android.wifirttscan.action.STOP_CONTINUOUS_SCAN";

    private static final String TAG = "BgWifiScanService";
    private static final String CHANNEL_ID = "wifi_scan_background";
    private static final int NOTIFICATION_ID = 6001;
    private static final long NEXT_SCAN_DELAY_MS = 30000;
    private static final long RETRY_DELAY_MS = 5000;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mRetryScanRunnable =
            new Runnable() {
                @Override
                public void run() {
                    triggerNextScan();
                }
            };

    private WifiManager mWifiManager;
    private WifiScanResultsCallback mWifiScanResultsCallback;
    private boolean mScanLoopEnabled = false;
    private long mLastScanStartMs = 0;

    public static Intent createStartIntent(Context context) {
        Intent intent = new Intent(context, ContinuousWifiScanService.class);
        intent.setAction(ACTION_START);
        return intent;
    }

    public static Intent createStopIntent(Context context) {
        Intent intent = new Intent(context, ContinuousWifiScanService.class);
        intent.setAction(ACTION_STOP);
        return intent;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mWifiScanResultsCallback = new WifiScanResultsCallback();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            stopScanLoop();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            android.util.Log.w(TAG, "Continuous scan service requires Android 11+ (API 30).");
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification("Starting continuous Wi-Fi scans"));
        startScanLoop();
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopScanLoop();
        super.onDestroy();
    }

    private void startScanLoop() {
        if (mScanLoopEnabled) {
            return;
        }
        mScanLoopEnabled = true;
        mWifiManager.registerScanResultsCallback(getMainExecutor(), mWifiScanResultsCallback);
        triggerNextScan();
    }

    private void stopScanLoop() {
        if (!mScanLoopEnabled) {
            return;
        }
        mScanLoopEnabled = false;
        mHandler.removeCallbacks(mRetryScanRunnable);
        try {
            mWifiManager.unregisterScanResultsCallback(mWifiScanResultsCallback);
        } catch (IllegalArgumentException e) {
            android.util.Log.w(TAG, "Scan callback was not registered.", e);
        }
    }

    private void triggerNextScan() {
        if (!mScanLoopEnabled) {
            return;
        }
        mHandler.removeCallbacks(mRetryScanRunnable);
        mLastScanStartMs = SystemClock.elapsedRealtime();
        try {
            boolean scanStarted = mWifiManager.startScan();
            if (!scanStarted) {
                android.util.Log.w(
                        TAG, "startScan() returned false; retrying in " + RETRY_DELAY_MS + "ms");
                mHandler.postDelayed(mRetryScanRunnable, RETRY_DELAY_MS);
            }
        } catch (SecurityException e) {
            android.util.Log.e(TAG, "Missing permission for startScan(); stopping service.", e);
            updateNotification("Missing required location/Wi-Fi permissions");
            stopScanLoop();
            stopSelf();
        }
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
                .setContentTitle("Wi-Fi scan running")
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
                        CHANNEL_ID, "Background Wi-Fi scanning", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Shows status of continuous Wi-Fi scans");
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
            if (!mScanLoopEnabled) {
                return;
            }
            long scanDurationMs = SystemClock.elapsedRealtime() - mLastScanStartMs;
            List<ScanResult> scanResults;
            try {
                scanResults = mWifiManager.getScanResults();
            } catch (SecurityException e) {
                android.util.Log.e(TAG, "Missing permission for getScanResults(); stopping service.", e);
                updateNotification("Missing required location/Wi-Fi permissions");
                stopScanLoop();
                stopSelf();
                return;
            }
            int totalCount = scanResults == null ? 0 : scanResults.size();
            int rttCount = 0;
            if (scanResults != null) {
                for (ScanResult scanResult : scanResults) {
                    if (scanResult.is80211mcResponder()) {
                        rttCount++;
                    }
                }
            }

            android.util.Log.i(
                    TAG,
                    "SCAN_LOOP durationMs="
                            + scanDurationMs
                            + " totalAps="
                            + totalCount
                            + " rttAps="
                            + rttCount);
            updateNotification(
                    "Last scan "
                            + scanDurationMs
                            + " ms, APs "
                            + totalCount
                            + ", RTT "
                            + rttCount);
            mHandler.postDelayed(mRetryScanRunnable, NEXT_SCAN_DELAY_MS);
        }
    }
}
