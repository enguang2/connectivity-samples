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

import android.content.Context;
import android.content.Intent;
import android.net.wifi.ScanResult;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.WifiRttManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays ranging information about a particular access point chosen by the user. Uses {@link
 * Handler} to trigger new requests based on a configurable delay.
 */
public class AccessPointRangingResultsActivity extends AppCompatActivity {
    private static final String TAG = "APRRActivity";

    public static final String SCAN_RESULT_EXTRA =
            "com.example.android.wifirttscan.extra.SCAN_RESULT";
    public static final String TOP_RANGING_SCAN_RESULTS_EXTRA =
            "com.example.android.wifirttscan.extra.TOP_RANGING_SCAN_RESULTS";

    private static final int SAMPLE_SIZE_DEFAULT = 50;
    private static final int MILLISECONDS_DELAY_BEFORE_NEW_RANGING_REQUEST_DEFAULT = 300;

    // UI Elements.
    private TextView mSsidTextView;
    private TextView mBssidTextView;

    private TextView mRangeTextView;
    private TextView mRangeMeanTextView;
    private TextView mRangeSDTextView;
    private TextView mRangeSDMeanTextView;
    private TextView mRssiTextView;
    private TextView mSuccessesInBurstTextView;
    private TextView mSuccessRatioTextView;
    private TextView mNumberOfRequestsTextView;

    private EditText mSampleSizeEditText;
    private EditText mMillisecondsDelayBeforeNewRangingRequestEditText;

    // Non UI variables.
    private ScanResult mScanResult;
    private ArrayList<ScanResult> mTopRangingScanResults;
    private String mMAC;

    private int mNumberOfRangeRequests;
    private int mNumberOfSuccessfulRangeRequests;

    private int mMillisecondsDelayBeforeNewRangingRequest;

    // Flag to track if the ranging loop should be running.
    private boolean mIsActive = false;

    // SystemClock.elapsedRealtime() of the most recent startRanging() call. Used to log the
    // round-trip duration when the RangingResultCallback fires.
    private long mLastStartRangingElapsedMs = 0L;

    // Max sample size to calculate average for
    // 1. Distance to device (getDistanceMm) over time
    // 2. Standard deviation of the measured distance to the device (getDistanceStdDevMm) over time
    // Note: A RangeRequest result already consists of the average of 7 readings from a burst,
    // so the average in (1) is the average of these averages.
    private int mSampleSize;

    // Used to loop over a list of distances to calculate averages (ensures data structure never
    // get larger than sample size).
    private int mStatisticRangeHistoryEndIndex;
    private ArrayList<Integer> mStatisticRangeHistory;

    // Used to loop over a list of standard deviations to calculate averages.
    private int mStatisticRangeSDHistoryEndIndex;
    private ArrayList<Integer> mStatisticRangeSDHistory;

    private WifiRttManager mWifiRttManager;
    private RttRangingResultCallback mRttRangingResultCallback;

    // Triggers additional RangingRequests with delay.
    final Handler mRangeRequestDelayHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_access_point_ranging_results);

        // Initializes UI elements.
        mSsidTextView = findViewById(R.id.ssid);
        mBssidTextView = findViewById(R.id.bssid);

        mRangeTextView = findViewById(R.id.range_value);
        mRangeMeanTextView = findViewById(R.id.range_mean_value);
        mRangeSDTextView = findViewById(R.id.range_sd_value);
        mRangeSDMeanTextView = findViewById(R.id.range_sd_mean_value);
        mRssiTextView = findViewById(R.id.rssi_value);
        mSuccessesInBurstTextView = findViewById(R.id.successes_in_burst_value);
        mSuccessRatioTextView = findViewById(R.id.success_ratio_value);
        mNumberOfRequestsTextView = findViewById(R.id.number_of_requests_value);

        mSampleSizeEditText = findViewById(R.id.stats_window_size_edit_value);
        mSampleSizeEditText.setText(String.valueOf(SAMPLE_SIZE_DEFAULT));

        mMillisecondsDelayBeforeNewRangingRequestEditText =
                findViewById(R.id.ranging_period_edit_value);
        mMillisecondsDelayBeforeNewRangingRequestEditText.setText(
                String.valueOf(MILLISECONDS_DELAY_BEFORE_NEW_RANGING_REQUEST_DEFAULT));

        // Retrieve ScanResult from Intent.
        Intent intent = getIntent();
        mScanResult = intent.getParcelableExtra(SCAN_RESULT_EXTRA);
        mTopRangingScanResults = intent.getParcelableArrayListExtra(TOP_RANGING_SCAN_RESULTS_EXTRA);

        if (mScanResult == null) {
            finish();
            return;
        }

        if (mTopRangingScanResults == null || mTopRangingScanResults.isEmpty()) {
            mTopRangingScanResults = new ArrayList<>();
            mTopRangingScanResults.add(mScanResult);
        }

        mMAC = mScanResult.BSSID;

        mSsidTextView.setText(mScanResult.SSID);
        mBssidTextView.setText(mScanResult.BSSID);
        
        // TODO: @Enguang use Application context instead of Activity context.
        mWifiRttManager = (WifiRttManager) getSystemService(Context.WIFI_RTT_RANGING_SERVICE);
        mRttRangingResultCallback = new RttRangingResultCallback();

        // Used to store range (distance) and rangeSd (standard deviation of the measured distance)
        // history to calculate averages.
        mStatisticRangeHistory = new ArrayList<>();
        mStatisticRangeSDHistory = new ArrayList<>();

        resetData();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume()");
        super.onResume();
        mIsActive = true;
        startRangingRequest();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause(): stop ranging loop.");
        super.onPause();
        mIsActive = false;
        mRangeRequestDelayHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop()");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy()");
        super.onDestroy();
        mIsActive = false;
        mRangeRequestDelayHandler.removeCallbacksAndMessages(null);
    }

    private void resetData() {
        try {
            mSampleSize = Integer.parseInt(mSampleSizeEditText.getText().toString());
            mMillisecondsDelayBeforeNewRangingRequest =
                    Integer.parseInt(
                            mMillisecondsDelayBeforeNewRangingRequestEditText.getText().toString());
        } catch (NumberFormatException e) {
            mSampleSize = SAMPLE_SIZE_DEFAULT;
            mMillisecondsDelayBeforeNewRangingRequest = MILLISECONDS_DELAY_BEFORE_NEW_RANGING_REQUEST_DEFAULT;
        }

        mNumberOfSuccessfulRangeRequests = 0;
        mNumberOfRangeRequests = 0;

        mStatisticRangeHistoryEndIndex = 0;
        mStatisticRangeHistory.clear();

        mStatisticRangeSDHistoryEndIndex = 0;
        mStatisticRangeSDHistory.clear();
    }

    private void startRangingRequest() {
        if (!mIsActive) {
            return;
        }

        if (!WifiRttUtils.hasRangingPermission(this)) {
            Log.w(TAG, "Permissions not granted for ranging.");
            return;
        }

        mNumberOfRangeRequests++;
        RangingRequest rangingRequest = WifiRttUtils.buildSingleAccessPointRequest(mScanResult);

        mLastStartRangingElapsedMs = SystemClock.elapsedRealtime();
        Log.d(TAG, "startRanging() called for MAC " + mMAC
                + " at " + mLastStartRangingElapsedMs + " ms");
        mWifiRttManager.startRanging(
                rangingRequest, getApplication().getMainExecutor(), mRttRangingResultCallback);
    }

    // Calculates average distance based on stored history.
    private float getDistanceMean() {
        if (mStatisticRangeHistory.isEmpty()) return 0;
        float distanceSum = 0;
        for (int distance : mStatisticRangeHistory) {
            distanceSum += distance;
        }
        return distanceSum / mStatisticRangeHistory.size();
    }

    // Adds distance to history. If larger than sample size value, loops back over and replaces the
    // oldest distance record in the list.
    private void addDistanceToHistory(int distance) {
        if (mStatisticRangeHistory.size() >= mSampleSize) {
            if (mStatisticRangeHistoryEndIndex >= mSampleSize) {
                mStatisticRangeHistoryEndIndex = 0;
            }
            mStatisticRangeHistory.set(mStatisticRangeHistoryEndIndex, distance);
            mStatisticRangeHistoryEndIndex++;
        } else {
            mStatisticRangeHistory.add(distance);
        }
    }

    // Calculates standard deviation of the measured distance based on stored history.
    private float getStandardDeviationOfDistanceMean() {
        if (mStatisticRangeSDHistory.isEmpty()) return 0;
        float distanceSdSum = 0;
        for (int distanceSd : mStatisticRangeSDHistory) {
            distanceSdSum += distanceSd;
        }
        return distanceSdSum / mStatisticRangeSDHistory.size();
    }

    // Adds standard deviation of the measured distance to history. If larger than sample size
    // value, loops back over and replaces the oldest distance record in the list.
    private void addStandardDeviationOfDistanceToHistory(int distanceSd) {
        if (mStatisticRangeSDHistory.size() >= mSampleSize) {
            if (mStatisticRangeSDHistoryEndIndex >= mSampleSize) {
                mStatisticRangeSDHistoryEndIndex = 0;
            }
            mStatisticRangeSDHistory.set(mStatisticRangeSDHistoryEndIndex, distanceSd);
            mStatisticRangeSDHistoryEndIndex++;
        } else {
            mStatisticRangeSDHistory.add(distanceSd);
        }
    }

    public void onResetButtonClick(View view) {
        resetData();
    }

    public void onLoggingButtonClick(View view) {
        Intent intent = new Intent(this, LoggingActivity.class);
        intent.putExtra(SCAN_RESULT_EXTRA, mScanResult);
        intent.putParcelableArrayListExtra(TOP_RANGING_SCAN_RESULTS_EXTRA, mTopRangingScanResults);
        startActivity(intent);
    }

    // Class that handles callbacks for all RangingRequests and issues new RangingRequests.
    private class RttRangingResultCallback extends RangingResultCallback {

        private void queueNextRangingRequest() {
            if (!mIsActive) {
                return;
            }
            mRangeRequestDelayHandler.postDelayed(
                    () -> AccessPointRangingResultsActivity.this.startRangingRequest(),
                    mMillisecondsDelayBeforeNewRangingRequest);
        }

        @Override
        public void onRangingFailure(int code) {
            if (!mIsActive) {
                return;
            }
            Log.d(TAG, "onRangingFailure() code: " + code);
            queueNextRangingRequest();
        }

        @Override
        public void onRangingResults(@NonNull List<RangingResult> list) {
            long callbackElapsedMs = SystemClock.elapsedRealtime();
            long durationMs = callbackElapsedMs - mLastStartRangingElapsedMs;
            Log.d(TAG, "RangingResultCallback triggered at " + callbackElapsedMs
                    + " ms, duration=" + durationMs + " ms (results=" + list.size() + ")");
            if (!mIsActive) {
                return;
            }
            Log.d(TAG, "onRangingResults(): " + list);

            // Because we are only requesting RangingResult for one access point (not multiple
            // access points), this will only ever be one. (Use loops when requesting RangingResults
            // for multiple access points.)
            if (list.size() == 1) {
                RangingResult rangingResult = list.get(0);
                if (mMAC.equals(rangingResult.getMacAddress().toString())) {
                    if (rangingResult.getStatus() == RangingResult.STATUS_SUCCESS) {
                        mNumberOfSuccessfulRangeRequests++;
                        mRangeTextView.setText(String.valueOf(rangingResult.getDistanceMm() / 1000f));
                        addDistanceToHistory(rangingResult.getDistanceMm());
                        mRangeMeanTextView.setText(String.valueOf(getDistanceMean() / 1000f));
                        mRangeSDTextView.setText(String.valueOf(rangingResult.getDistanceStdDevMm() / 1000f));
                        addStandardDeviationOfDistanceToHistory(rangingResult.getDistanceStdDevMm());
                        mRangeSDMeanTextView.setText(String.valueOf(getStandardDeviationOfDistanceMean() / 1000f));
                        mRssiTextView.setText(String.valueOf(rangingResult.getRssi()));
                        mSuccessesInBurstTextView.setText(
                                rangingResult.getNumSuccessfulMeasurements()
                                        + "/"
                                        + rangingResult.getNumAttemptedMeasurements());

                        float successRatio = ((float) mNumberOfSuccessfulRangeRequests / (float) mNumberOfRangeRequests) * 100;
                        mSuccessRatioTextView.setText(String.format("%.2f%%", successRatio));
                        mNumberOfRequestsTextView.setText(String.valueOf(mNumberOfRangeRequests));
                    } else {
                        Log.d(TAG, "RangingResult failed with status: " + rangingResult.getStatus());
                    }
                }
            }
            queueNextRangingRequest();
        }
    }
}
