package com.example.android.wifirttscan;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.provider.OpenableColumns;

import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class ApCoordinateDataHelper {

    private ApCoordinateDataHelper() {
    }

    static LoadedApCoordinateData load(Context context, Uri uri) throws IOException {
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                throw new IOException("Failed to open coordinate CSV: " + uri);
            }

            try (BufferedReader reader =
                            new BufferedReader(
                                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String headerLine = reader.readLine();
                if (headerLine == null) {
                    throw new IOException("Coordinate CSV is empty.");
                }

                String[] headers = headerLine.split(",", -1);
                int colIndex = findHeaderIndex(headers, "col");
                int rowIndex = findHeaderIndex(headers, "row");
                int macIndex = findHeaderIndex(headers, "mac", "bssid");
                int heightCmIndex = findHeaderIndex(headers, "height(cm)", "height_cm");

                if (colIndex < 0 || rowIndex < 0 || macIndex < 0 || heightCmIndex < 0) {
                    throw new IOException("Coordinate CSV header is missing required columns.");
                }

                Map<String, ApCoordinate> apCoordinatesByBssid = new HashMap<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) {
                        continue;
                    }

                    String[] values = line.split(",", -1);
                    if (values.length <= Math.max(Math.max(colIndex, rowIndex), Math.max(macIndex, heightCmIndex))) {
                        continue;
                    }

                    String bssid = normalizeBssid(values[macIndex]);
                    if (bssid == null) {
                        continue;
                    }

                    try {
                        double column = Double.parseDouble(values[colIndex].trim());
                        double row = Double.parseDouble(values[rowIndex].trim());
                        double heightMeters = Double.parseDouble(values[heightCmIndex].trim()) / 100.0;
                        apCoordinatesByBssid.put(
                                bssid, new ApCoordinate(column, row, heightMeters));
                    } catch (NumberFormatException ignored) {
                        // Skip malformed coordinate rows and continue parsing the file.
                    }
                }

                return new LoadedApCoordinateData(
                        resolveDisplayName(context, uri), apCoordinatesByBssid);
            }
        }
    }

    @Nullable
    static Double calculateTrueRangeMeters(
            ScanResult scanResult,
            @Nullable LoadedApCoordinateData loadedApCoordinateData,
            double phonePixelColumn,
            double phonePixelRow,
            double phoneHeightMeters,
            double pixelsPerMeter) {
        if (loadedApCoordinateData == null || pixelsPerMeter <= 0) {
            return null;
        }

        String normalizedBssid = normalizeBssid(scanResult.BSSID);
        if (normalizedBssid == null) {
            return null;
        }

        ApCoordinate apCoordinate = loadedApCoordinateData.apCoordinatesByBssid.get(normalizedBssid);
        if (apCoordinate == null) {
            return null;
        }

        double columnDeltaPixels = apCoordinate.pixelColumn - phonePixelColumn;
        double rowDeltaPixels = apCoordinate.pixelRow - phonePixelRow;
        double horizontalDistanceMeters =
                Math.hypot(columnDeltaPixels, rowDeltaPixels) / pixelsPerMeter;
        double verticalDistanceMeters = apCoordinate.heightMeters - phoneHeightMeters;
        return Math.hypot(horizontalDistanceMeters, verticalDistanceMeters);
    }

    @Nullable
    private static String normalizeBssid(@Nullable String bssid) {
        if (bssid == null) {
            return null;
        }

        String trimmed = bssid.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.US);
    }

    private static int findHeaderIndex(String[] headers, String... candidates) {
        for (int i = 0; i < headers.length; i++) {
            String normalizedHeader = headers[i].trim().toLowerCase(Locale.US);
            for (String candidate : candidates) {
                if (normalizedHeader.equals(candidate.toLowerCase(Locale.US))) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String resolveDisplayName(Context context, Uri uri) {
        Cursor cursor = null;
        try {
            cursor =
                    context.getContentResolver()
                            .query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (displayNameIndex >= 0) {
                    return cursor.getString(displayNameIndex);
                }
            }
        } catch (RuntimeException ignored) {
            // Fall back to the URI string below when provider metadata is unavailable.
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return uri.toString();
    }

    static final class LoadedApCoordinateData {
        final String displayName;
        final Map<String, ApCoordinate> apCoordinatesByBssid;

        LoadedApCoordinateData(String displayName, Map<String, ApCoordinate> apCoordinatesByBssid) {
            this.displayName = displayName;
            this.apCoordinatesByBssid = apCoordinatesByBssid;
        }
    }

    static final class ApCoordinate {
        final double pixelColumn;
        final double pixelRow;
        final double heightMeters;

        ApCoordinate(double pixelColumn, double pixelRow, double heightMeters) {
            this.pixelColumn = pixelColumn;
            this.pixelRow = pixelRow;
            this.heightMeters = heightMeters;
        }
    }
}
