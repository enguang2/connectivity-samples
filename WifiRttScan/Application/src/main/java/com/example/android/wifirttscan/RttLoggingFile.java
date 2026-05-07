package com.example.android.wifirttscan;

import android.content.Context;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.rtt.RangingResult;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class RttLoggingFile {
    private static final String HEADER =
            "timestamp_ms,true_range_m,estimated_range_m,std_dev_m,"
                    + "successful_measurements,attempted_measurements,rssi_dbm,"
                    + "frequency_mhz,ssid,bssid,is_80211mc_responder,status\n";
    private static final String LOG_DIRECTORY_NAME = "ranging_logs";
    private static final int COPY_BUFFER_SIZE_BYTES = 8 * 1024;
    private static final SimpleDateFormat DATE_FORMATTER =
            new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US);

    private final File mFile;
    private final BufferedWriter mWriter;

    RttLoggingFile(Context context, ScanResult scanResult, String fileNamePrefix)
            throws IOException {
        File directory = new File(context.getCacheDir(), LOG_DIRECTORY_NAME);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Failed to create log directory: " + directory.getAbsolutePath());
        }

        mFile = new File(directory, createSuggestedFileName(scanResult, fileNamePrefix));
        mWriter =
                new BufferedWriter(
                        new OutputStreamWriter(
                                new FileOutputStream(mFile), StandardCharsets.UTF_8));
        mWriter.write(HEADER);
        mWriter.flush();
    }

    String getFilePath() {
        return mFile.getName();
    }

    String getSuggestedExportFileName() {
        return mFile.getName();
    }

    void addRangingResult(
            ScanResult scanResult,
            RangingResult rangingResult,
            @Nullable Double trueRangeMeters)
            throws IOException {
        mWriter.write(createEntry(scanResult, rangingResult, trueRangeMeters));
        mWriter.flush();
    }

    void flush() throws IOException {
        mWriter.flush();
    }

    void close() throws IOException {
        mWriter.flush();
        mWriter.close();
    }

    void exportTo(Context context, Uri destinationUri) throws IOException {
        flush();

        try (BufferedInputStream inputStream =
                        new BufferedInputStream(new FileInputStream(mFile));
                OutputStream rawOutputStream =
                        context.getContentResolver().openOutputStream(destinationUri, "wt");
                BufferedOutputStream outputStream =
                        rawOutputStream == null
                                ? null
                                : new BufferedOutputStream(rawOutputStream)) {
            if (outputStream == null) {
                throw new IOException("Failed to open output stream for uri: " + destinationUri);
            }

            byte[] buffer = new byte[COPY_BUFFER_SIZE_BYTES];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
        }
    }

    static String createSuggestedFileName(ScanResult scanResult, String fileNamePrefix) {
        String ssid = sanitizeFileComponent(scanResult.SSID);
        String bssid = sanitizeFileComponent(scanResult.BSSID);
        String prefix = fileNamePrefix == null ? "" : fileNamePrefix;
        return prefix + "rtt-log-" + ssid + "-" + bssid + "-" + DATE_FORMATTER.format(new Date()) + ".csv";
    }

    private static String sanitizeFileComponent(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "unknown";
        }

        String sanitized = value.replaceAll("[^A-Za-z0-9._-]+", "_");
        return sanitized.isEmpty() ? "unknown" : sanitized;
    }

    private static String createEntry(
            ScanResult scanResult, RangingResult rangingResult, @Nullable Double trueRangeMeters) {
        boolean success = rangingResult.getStatus() == RangingResult.STATUS_SUCCESS;

        String trueRange = trueRangeMeters == null ? "" : formatFloat(trueRangeMeters);
        String estimatedRange = success ? formatMeters(rangingResult.getDistanceMm()) : "";
        String standardDeviation = success ? formatMeters(rangingResult.getDistanceStdDevMm()) : "";
        String successfulMeasurements =
                success ? String.valueOf(rangingResult.getNumSuccessfulMeasurements()) : "";
        String attemptedMeasurements =
                success ? String.valueOf(rangingResult.getNumAttemptedMeasurements()) : "";
        String rssi = success ? String.valueOf(rangingResult.getRssi()) : "";
        String timestampMillis =
                success ? String.valueOf(rangingResult.getRangingTimestampMillis()) : "";

        return new StringBuilder()
                .append(timestampMillis).append(',')
                .append(trueRange).append(',')
                .append(estimatedRange).append(',')
                .append(standardDeviation).append(',')
                .append(successfulMeasurements).append(',')
                .append(attemptedMeasurements).append(',')
                .append(rssi).append(',')
                .append(scanResult.frequency).append(',')
                .append(csvEscape(scanResult.SSID)).append(',')
                .append(csvEscape(scanResult.BSSID)).append(',')
                .append(scanResult.is80211mcResponder() ? "1" : "0").append(',')
                .append(statusToString(rangingResult.getStatus()))
                .append('\n')
                .toString();
    }

    private static String formatMeters(int millimeters) {
        return formatFloat(millimeters / 1000f);
    }

    private static String formatFloat(double value) {
        return String.format(Locale.US, "%.3f", value);
    }

    private static String csvEscape(String value) {
        String safeValue = value == null ? "" : value;
        String escapedValue = safeValue.replace("\"", "\"\"");
        return "\"" + escapedValue + "\"";
    }

    @NonNull
    private static String statusToString(int status) {
        switch (status) {
            case RangingResult.STATUS_SUCCESS:
                return "SUCCESS";
            case RangingResult.STATUS_FAIL:
                return "FAIL";
            case RangingResult.STATUS_RESPONDER_DOES_NOT_SUPPORT_IEEE80211MC:
                return "RESPONDER_NOT_80211MC";
            default:
                return "STATUS_" + status;
        }
    }
}
