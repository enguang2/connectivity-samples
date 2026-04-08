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
    private boolean mHasLoggedResults;

    private LoggingSession(Context context, ScanResult scanResult)
            throws IOException {
        mBssid = scanResult.BSSID;
        mLogFile = new RttLoggingFile(context, scanResult);
    }

    static LoggingSession createNewLoggingSession(Context context, ScanResult scanResult)
            throws IOException {
        endCurrentLoggingSession();
        sCurrentLoggingSession = new LoggingSession(context.getApplicationContext(), scanResult);
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

    static void addRangingResult(
            ScanResult scanResult, RangingResult rangingResult, @androidx.annotation.Nullable Double trueRangeMeters)
            throws IOException {
        if (sCurrentLoggingSession == null) {
            return;
        }
        sCurrentLoggingSession.mLogFile.addRangingResult(scanResult, rangingResult, trueRangeMeters);
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
