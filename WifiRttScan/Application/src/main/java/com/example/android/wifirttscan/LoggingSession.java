package com.example.android.wifirttscan;

import android.content.Context;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.rtt.RangingResult;

import java.io.IOException;

final class LoggingSession {
    private static LoggingSession sCurrentLoggingSession;

    private final String mBssid;
    private final RttLoggingFile mLogFile;
    private float mGroundTruthDistanceMeters;
    private boolean mHasLoggedResults;

    private LoggingSession(Context context, ScanResult scanResult, float trueRangeMeters)
            throws IOException {
        mBssid = scanResult.BSSID;
        mGroundTruthDistanceMeters = trueRangeMeters;
        mLogFile = new RttLoggingFile(context, scanResult);
    }

    static LoggingSession createNewLoggingSession(
            Context context, ScanResult scanResult, float trueRangeMeters) throws IOException {
        endCurrentLoggingSession();
        sCurrentLoggingSession =
                new LoggingSession(context.getApplicationContext(), scanResult, trueRangeMeters);
        return sCurrentLoggingSession;
    }

    static void endCurrentLoggingSession() throws IOException {
        if (sCurrentLoggingSession == null) {
            return;
        }
        sCurrentLoggingSession.mLogFile.close();
        sCurrentLoggingSession = null;
    }

    static boolean hasCurrentLoggingSession(String bssid) {
        return sCurrentLoggingSession != null && sCurrentLoggingSession.mBssid.equals(bssid);
    }

    static String getCurrentLoggingPath() {
        if (sCurrentLoggingSession == null) {
            return "";
        }
        return sCurrentLoggingSession.mLogFile.getFilePath();
    }

    static float getGroundTruthDistanceMeters() {
        if (sCurrentLoggingSession == null) {
            return 0f;
        }
        return sCurrentLoggingSession.mGroundTruthDistanceMeters;
    }

    static void setGroundTruthDistanceMeters(float trueRangeMeters) {
        if (sCurrentLoggingSession == null) {
            return;
        }
        sCurrentLoggingSession.mGroundTruthDistanceMeters = trueRangeMeters;
    }

    static void addRangingResult(ScanResult scanResult, RangingResult rangingResult)
            throws IOException {
        if (sCurrentLoggingSession == null) {
            return;
        }
        sCurrentLoggingSession.mLogFile.addRangingResult(
                scanResult, rangingResult, sCurrentLoggingSession.mGroundTruthDistanceMeters);
        sCurrentLoggingSession.mHasLoggedResults = true;
    }

    static void flush() throws IOException {
        if (sCurrentLoggingSession == null) {
            return;
        }
        sCurrentLoggingSession.mLogFile.flush();
    }

    static boolean hasLoggedResults(String bssid) {
        return hasCurrentLoggingSession(bssid) && sCurrentLoggingSession.mHasLoggedResults;
    }

    static String getSuggestedExportFileName() {
        if (sCurrentLoggingSession == null) {
            return "";
        }
        return sCurrentLoggingSession.mLogFile.getSuggestedExportFileName();
    }

    static void exportTo(Context context, Uri destinationUri) throws IOException {
        if (sCurrentLoggingSession == null) {
            return;
        }
        sCurrentLoggingSession.mLogFile.exportTo(context, destinationUri);
    }
}
