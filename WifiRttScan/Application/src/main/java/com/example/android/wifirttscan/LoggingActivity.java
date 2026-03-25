package com.example.android.wifirttscan;

import static com.example.android.wifirttscan.AccessPointRangingResultsActivity.SCAN_RESULT_EXTRA;
import static com.example.android.wifirttscan.AccessPointRangingResultsActivity.TOP_RANGING_SCAN_RESULTS_EXTRA;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.WifiRttManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LoggingActivity extends AppCompatActivity {
    private static final String TAG = "LoggingActivity";
    private static final int MAX_LOGGING_AP_COUNT = 8;
    private static final float TRUE_RANGE_METERS_DEFAULT = 0f;
    private static final int TIMER_INTERVAL_SECONDS_DEFAULT = 300;
    private static final int RANGING_INTERVAL_MS_DEFAULT = 1000;
    private static final String DISTANCE_FORMAT = "%.2f";

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private final Runnable mStopLoggingRunnable =
            new Runnable() {
                @Override
                public void run() {
                    stopLogging(true);
                }
            };

    private TextView mSsidTextView;
    private TextView mBssidTextView;
    private TextView mEstimatedRangeTextView;
    private TextView mLoggingStateTextView;
    private TextView mLoggingFilePathTextView;

    private EditText mTrueRangeEditText;
    private EditText mTimerIntervalEditText;
    private EditText mRangingIntervalEditText;
    private SwitchCompat mUseTimerSwitch;

    private Button mStartButton;
    private Button mStopButton;
    private Button mShareButton;

    private ActivityResultLauncher<String> mShareFileLauncher;

    private ScanResult mScanResult;
    private ArrayList<ScanResult> mLoggingScanResults;
    private Map<String, ScanResult> mScanResultsByBssid;
    private String mMac;
    private WifiRttManager mWifiRttManager;
    private LoggingRangingResultCallback mRangingResultCallback;

    private boolean mIsLogging;
    private int mRangingIntervalMillis = RANGING_INTERVAL_MS_DEFAULT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate()");
        setContentView(R.layout.activity_logging);

        initializeViews();
        initializeDefaultValues();
        initializeListeners();
        initializeActivityResultLaunchers();

        Intent intent = getIntent();
        mScanResult = intent.getParcelableExtra(SCAN_RESULT_EXTRA);
        if (mScanResult == null) {
            Toast.makeText(this, R.string.logging_missing_scan_result, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mMac = mScanResult.BSSID;
        mLoggingScanResults = getLoggingScanResults(intent, mScanResult);
        mScanResultsByBssid = new HashMap<>();
        for (ScanResult scanResult : mLoggingScanResults) {
            mScanResultsByBssid.put(scanResult.BSSID, scanResult);
        }

        mWifiRttManager = (WifiRttManager) getSystemService(Context.WIFI_RTT_RANGING_SERVICE);
        mRangingResultCallback = new LoggingRangingResultCallback();

        mSsidTextView.setText(mScanResult.SSID);
        mBssidTextView.setText(mScanResult.BSSID);
        refreshSessionUi();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause()");
        super.onPause();
        stopLogging(false);
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy()");
        mMainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    public void onNewSessionClick(View view) {
        createNewSession();
    }

    public void onStartLoggingClick(View view) {
        startLogging();
    }

    public void onStopLoggingClick(View view) {
        stopLogging(false);
    }

    public void onShareFileClick(View view) {
        shareFile();
    }

    private void initializeViews() {
        mSsidTextView = findViewById(R.id.logging_ssid);
        mBssidTextView = findViewById(R.id.logging_bssid);
        mEstimatedRangeTextView = findViewById(R.id.estimated_range_value);
        mLoggingStateTextView = findViewById(R.id.logging_state_value);
        mLoggingFilePathTextView = findViewById(R.id.logging_file_path_value);
        mTrueRangeEditText = findViewById(R.id.true_range_edit_value);
        mTimerIntervalEditText = findViewById(R.id.timer_interval_edit_value);
        mRangingIntervalEditText = findViewById(R.id.logging_ranging_interval_edit_value);
        mUseTimerSwitch = findViewById(R.id.use_timer_switch);
        mStartButton = findViewById(R.id.start_logging_button);
        mStopButton = findViewById(R.id.stop_logging_button);
        mShareButton = findViewById(R.id.share_file_button);
    }

    private void initializeDefaultValues() {
        mTrueRangeEditText.setText(formatDistance(TRUE_RANGE_METERS_DEFAULT));
        mTimerIntervalEditText.setText(String.valueOf(TIMER_INTERVAL_SECONDS_DEFAULT));
        mRangingIntervalEditText.setText(String.valueOf(RANGING_INTERVAL_MS_DEFAULT));
        updateTimerInputState(mUseTimerSwitch.isChecked());
    }

    private void initializeListeners() {
        mUseTimerSwitch.setOnCheckedChangeListener(
                (buttonView, isChecked) -> updateTimerInputState(isChecked));

        mTrueRangeEditText.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(
                            CharSequence s, int start, int before, int count) {
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        Float trueRangeMeters = tryParseNonNegativeFloat(s.toString());
                        if (trueRangeMeters != null && LoggingSession.hasCurrentLoggingSession(mMac)) {
                            LoggingSession.setGroundTruthDistanceMeters(trueRangeMeters);
                        }
                    }
                });
    }

    private void createNewSession() {
        Float trueRangeMeters =
                parseNonNegativeFloat(mTrueRangeEditText, R.string.logging_true_range_error);
        if (trueRangeMeters == null) {
            return;
        }

        stopLogging(false);

        try {
            LoggingSession.createNewLoggingSession(this, mScanResult, trueRangeMeters);
            Toast.makeText(this, R.string.logging_session_created, Toast.LENGTH_SHORT).show();
            refreshSessionUi();
        } catch (IOException e) {
            Log.e(TAG, "Failed to create logging session.", e);
            Toast.makeText(this, R.string.logging_session_create_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void initializeActivityResultLaunchers() {
        mShareFileLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.CreateDocument("text/csv"),
                        this::handleShareDestinationSelected);
    }

    private void handleShareDestinationSelected(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }

        try {
            LoggingSession.exportTo(this, uri);
            Toast.makeText(this, R.string.logging_share_file_success, Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e(TAG, "Failed to export log file.", e);
            Toast.makeText(this, R.string.logging_share_file_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void startLogging() {
        if (mIsLogging) {
            return;
        }

        if (!LoggingSession.hasCurrentLoggingSession(mMac)) {
            Toast.makeText(this, R.string.logging_create_session_first, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!WifiRttUtils.hasRangingPermission(this)) {
            Toast.makeText(this, R.string.logging_permission_required, Toast.LENGTH_SHORT).show();
            return;
        }

        if (mWifiRttManager == null) {
            Toast.makeText(this, R.string.logging_rtt_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

        Float trueRangeMeters =
                parseNonNegativeFloat(mTrueRangeEditText, R.string.logging_true_range_error);
        Integer rangingIntervalMillis =
                parsePositiveInt(
                        mRangingIntervalEditText, R.string.logging_ranging_interval_error);

        Integer timerIntervalSeconds = null;
        if (mUseTimerSwitch.isChecked()) {
            timerIntervalSeconds =
                    parsePositiveInt(
                            mTimerIntervalEditText, R.string.logging_timer_interval_error);
        }

        if (trueRangeMeters == null || rangingIntervalMillis == null
                || (mUseTimerSwitch.isChecked() && timerIntervalSeconds == null)) {
            return;
        }

        LoggingSession.setGroundTruthDistanceMeters(trueRangeMeters);
        mRangingIntervalMillis = rangingIntervalMillis;
        mIsLogging = true;
        mLoggingStateTextView.setText(R.string.logging_state_logging);
        updateButtonStates();

        if (mUseTimerSwitch.isChecked() && timerIntervalSeconds != null) {
            mMainHandler.postDelayed(mStopLoggingRunnable, timerIntervalSeconds * 1000L);
        }

        startRangingRequest();
    }

    private void stopLogging(boolean timedOut) {
        mMainHandler.removeCallbacksAndMessages(null);

        if (!mIsLogging) {
            refreshSessionUi();
            return;
        }

        mIsLogging = false;
        mEstimatedRangeTextView.setText(R.string.logging_not_available);

        try {
            LoggingSession.flush();
        } catch (IOException e) {
            Log.e(TAG, "Failed to flush logging session.", e);
        }

        refreshSessionUi();

        if (timedOut) {
            Toast.makeText(this, R.string.logging_timer_elapsed, Toast.LENGTH_SHORT).show();
        }
    }

    private void shareFile() {
        if (!LoggingSession.hasCurrentLoggingSession(mMac)) {
            Toast.makeText(this, R.string.logging_create_session_first, Toast.LENGTH_SHORT).show();
            return;
        }

        if (mIsLogging) {
            stopLogging(false);
        }

        if (!LoggingSession.hasLoggedResults(mMac)) {
            Toast.makeText(this, R.string.logging_no_results_to_share, Toast.LENGTH_SHORT).show();
            return;
        }

        mShareFileLauncher.launch(LoggingSession.getSuggestedExportFileName());
    }

    private void refreshSessionUi() {
        boolean hasSession = LoggingSession.hasCurrentLoggingSession(mMac);
        if (hasSession) {
            mTrueRangeEditText.setText(formatDistance(LoggingSession.getGroundTruthDistanceMeters()));
            mLoggingFilePathTextView.setText(LoggingSession.getCurrentLoggingPath());
            if (!mIsLogging) {
                mLoggingStateTextView.setText(R.string.logging_state_session_ready);
            }
        } else {
            mLoggingFilePathTextView.setText(R.string.logging_no_active_session);
            if (!mIsLogging) {
                mLoggingStateTextView.setText(R.string.logging_state_no_session);
            }
        }
        updateButtonStates();
    }

    private void updateButtonStates() {
        boolean hasSession = LoggingSession.hasCurrentLoggingSession(mMac);
        mStartButton.setEnabled(hasSession && !mIsLogging);
        mStopButton.setEnabled(hasSession && mIsLogging);
        mShareButton.setEnabled(hasSession && !mIsLogging && LoggingSession.hasLoggedResults(mMac));
    }

    private void updateTimerInputState(boolean enabled) {
        mTimerIntervalEditText.setEnabled(enabled);
        mTimerIntervalEditText.setAlpha(enabled ? 1f : 0.5f);
    }

    private ArrayList<ScanResult> getLoggingScanResults(Intent intent, ScanResult fallbackScanResult) {
        ArrayList<ScanResult> passedScanResults =
                intent.getParcelableArrayListExtra(TOP_RANGING_SCAN_RESULTS_EXTRA);
        ArrayList<ScanResult> loggingScanResults = new ArrayList<>();
        HashSet<String> seenBssids = new HashSet<>();

        if (passedScanResults != null) {
            for (ScanResult scanResult : passedScanResults) {
                if (scanResult == null || scanResult.BSSID == null
                        || seenBssids.contains(scanResult.BSSID)) {
                    continue;
                }

                loggingScanResults.add(scanResult);
                seenBssids.add(scanResult.BSSID);
                if (loggingScanResults.size() >= MAX_LOGGING_AP_COUNT) {
                    break;
                }
            }
        }

        if (loggingScanResults.isEmpty()) {
            loggingScanResults.add(fallbackScanResult);
        }

        return loggingScanResults;
    }

    private void startRangingRequest() {
        if (!mIsLogging) {
            return;
        }

        if (!WifiRttUtils.hasRangingPermission(this)) {
            Log.w(TAG, "Permissions not granted for logging.");
            stopLogging(false);
            Toast.makeText(this, R.string.logging_permission_required, Toast.LENGTH_SHORT).show();
            return;
        }

        RangingRequest rangingRequest = WifiRttUtils.buildAccessPointRequest(mLoggingScanResults);
        mWifiRttManager.startRanging(
                rangingRequest, getApplication().getMainExecutor(), mRangingResultCallback);
    }

    private void queueNextRangingRequest() {
        if (!mIsLogging) {
            return;
        }
        mMainHandler.postDelayed(
                new Runnable() {
                    @Override
                    public void run() {
                        startRangingRequest();
                    }
                },
                mRangingIntervalMillis);
    }

    private void handleRangingResult(ScanResult scanResult, RangingResult rangingResult) {
        Log.d(TAG, "handleRangingResult(): " + rangingResult);

        if (rangingResult.getStatus() == RangingResult.STATUS_SUCCESS) {
            if (mMac.equals(scanResult.BSSID)) {
                mEstimatedRangeTextView.setText(
                        formatDistance(rangingResult.getDistanceMm() / 1000f));
            }
        } else {
            Log.d(TAG, "RangingResult failed with status: " + rangingResult.getStatus());
        }

        try {
            LoggingSession.addRangingResult(scanResult, rangingResult);
        } catch (IOException e) {
            Log.e(TAG, "Failed to write ranging result to file.", e);
            Toast.makeText(this, R.string.logging_write_failed, Toast.LENGTH_SHORT).show();
            stopLogging(false);
        }
    }

    private String formatDistance(float distanceMeters) {
        return String.format(Locale.US, DISTANCE_FORMAT, distanceMeters);
    }

    @Nullable
    private Float parseNonNegativeFloat(EditText editText, @StringRes int errorResId) {
        String value = editText.getText().toString().trim();
        try {
            float parsedValue = Float.parseFloat(value);
            if (parsedValue < 0f) {
                editText.setError(getString(errorResId));
                return null;
            }
            editText.setError(null);
            return parsedValue;
        } catch (NumberFormatException e) {
            editText.setError(getString(errorResId));
            return null;
        }
    }

    @Nullable
    private Integer parsePositiveInt(EditText editText, @StringRes int errorResId) {
        String value = editText.getText().toString().trim();
        try {
            int parsedValue = Integer.parseInt(value);
            if (parsedValue <= 0) {
                editText.setError(getString(errorResId));
                return null;
            }
            editText.setError(null);
            return parsedValue;
        } catch (NumberFormatException e) {
            editText.setError(getString(errorResId));
            return null;
        }
    }

    @Nullable
    private Float tryParseNonNegativeFloat(String value) {
        try {
            float parsedValue = Float.parseFloat(value);
            return parsedValue < 0f ? null : parsedValue;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private class LoggingRangingResultCallback extends RangingResultCallback {

        @Override
        public void onRangingFailure(int code) {
            if (!mIsLogging) {
                return;
            }

            Log.d(TAG, "onRangingFailure() code: " + code);
            queueNextRangingRequest();
        }

        @Override
        public void onRangingResults(@NonNull List<RangingResult> list) {
            if (!mIsLogging) {
                return;
            }

            for (RangingResult rangingResult : list) {
                if (rangingResult.getMacAddress() == null) {
                    Log.w(TAG, "Ignoring malformed ranging result without MAC address.");
                    continue;
                }

                ScanResult matchingScanResult =
                        mScanResultsByBssid.get(rangingResult.getMacAddress().toString());
                if (matchingScanResult == null) {
                    Log.w(TAG, "Ignoring unexpected MAC address in logging callback.");
                    continue;
                }

                handleRangingResult(matchingScanResult, rangingResult);
            }

            queueNextRangingRequest();
        }
    }
}
