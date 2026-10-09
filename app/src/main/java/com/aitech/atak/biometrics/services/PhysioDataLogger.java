/*
 * Copyright (C) 2024, Ascent Integrated Tech. All rights reserved.
 *
 * The copyright to the computer software herein is the property of Ascent Integrated Tech.
 * This software may not be used and/or copied without the explicit written permission
 * of Ascent Integrated Tech, and only in accordance with the terms and conditions stipulated
 * in the license agreement and/or contract under which the software has been supplied.
 */

package com.aitech.atak.biometrics.services;

import android.content.Context;
import android.util.Log;

import com.atakmap.coremap.filesystem.FileSystemUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PhysioDataLogger {
    private static final String TAG = "PhysioDataLogger";
    private static final String HEADER =
            "collected_at_utc,callsign,heart_rate_bpm,skin_temp_f,exertion_percent";

    public interface CompletionListener {
        void onComplete(File file, IOException error);
    }

    public interface FailureListener {
        void onFailure(IOException error);
    }

    private final ExecutorService writerExecutor = Executors.newSingleThreadExecutor();
    private BufferedWriter writer;
    private File logFile;
    private volatile boolean logging;
    private IOException writeFailure;
    private volatile FailureListener failureListener;

    public synchronized File start(Context context) throws IOException {
        if (writerExecutor.isShutdown()) {
            throw new IOException("The physiological data logger has been shut down");
        }
        if (logging || writer != null) {
            throw new IOException("A physiological data log is already open");
        }

        File atakRoot = FileSystemUtils.getRoot();
        if (atakRoot == null) {
            throw new IOException("ATAK storage is unavailable");
        }
        File directory = new File(atakRoot, "physio-logs");
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Unable to create log directory: " + directory);
        }

        String timestamp = fileTimestamp(new Date());
        File candidate = new File(directory, "physio-log-" + timestamp + ".csv");
        int suffix = 1;
        while (!candidate.createNewFile()) {
            candidate = new File(directory, "physio-log-" + timestamp + "-" + suffix++ + ".csv");
        }

        BufferedWriter openedWriter = null;
        try {
            openedWriter = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(candidate), StandardCharsets.UTF_8));
            openedWriter.write(HEADER);
            openedWriter.newLine();
            openedWriter.flush();
        } catch (IOException e) {
            if (openedWriter != null) {
                try {
                    openedWriter.close();
                } catch (IOException closeError) {
                    e.addSuppressed(closeError);
                }
            }
            if (!candidate.delete()) {
                Log.w(TAG, "Unable to remove incomplete log file: " + candidate);
            }
            throw e;
        }

        writer = openedWriter;
        logFile = candidate;
        writeFailure = null;
        logging = true;
        Log.i(TAG, "Started physiological data log: " + candidate);
        return candidate;
    }

    public synchronized boolean isLogging() {
        return logging;
    }

    public void setFailureListener(FailureListener listener) {
        failureListener = listener;
    }

    public synchronized void recordSample(Date collectedAt, String callsign, String heartRate,
                                          String skinTemp, String exertion) {
        if (!logging || writeFailure != null) {
            return;
        }

        final String row = formatRow(collectedAt, callsign, heartRate, skinTemp, exertion);
        writerExecutor.execute(new Runnable() {
            @Override
            public void run() {
                writeRow(row);
            }
        });
    }

    public synchronized void stop(final CompletionListener listener) {
        if (writer == null) {
            listener.onComplete(null, new IOException("There is no physiological data log to finish"));
            return;
        }

        logging = false;
        final File completedFile = logFile;
        writerExecutor.execute(new Runnable() {
            @Override
            public void run() {
                IOException error;
                synchronized (PhysioDataLogger.this) {
                    error = writeFailure;
                    try {
                        writer.flush();
                    } catch (IOException e) {
                        error = appendError(error, e);
                    }
                    try {
                        writer.close();
                    } catch (IOException e) {
                        error = appendError(error, e);
                    }
                    writer = null;
                    logFile = null;
                    writeFailure = null;
                }
                listener.onComplete(completedFile, error);
            }
        });
    }

    public synchronized void shutdown() {
        if (writerExecutor.isShutdown()) {
            return;
        }
        logging = false;
        if (writer == null) {
            writerExecutor.shutdown();
            return;
        }

        writerExecutor.execute(new Runnable() {
            @Override
            public void run() {
                synchronized (PhysioDataLogger.this) {
                    if (writer != null) {
                        IOException error = null;
                        try {
                            writer.flush();
                        } catch (IOException e) {
                            error = e;
                        }
                        try {
                            writer.close();
                        } catch (IOException e) {
                            error = appendError(error, e);
                        }
                        writer = null;
                        logFile = null;
                        if (error != null) {
                            Log.e(TAG, "Unable to finish physiological data log", error);
                        }
                    }
                }
            }
        });
        writerExecutor.shutdown();
    }

    private void writeRow(String row) {
        IOException error = null;
        synchronized (this) {
            if (writer == null || writeFailure != null) {
                return;
            }
            try {
                writer.write(row);
                writer.newLine();
                writer.flush();
            } catch (IOException e) {
                writeFailure = e;
                error = e;
            }
        }

        if (error != null) {
            Log.e(TAG, "Unable to write physiological data sample", error);
            FailureListener listener = failureListener;
            if (listener != null) {
                listener.onFailure(error);
            }
        }
    }

    static String formatRow(Date collectedAt, String callsign, String heartRate,
                            String skinTemp, String exertion) {
        SimpleDateFormat formatter = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        return csvField(formatter.format(collectedAt)) + ","
                + csvField(callsign) + ","
                + csvNumber(heartRate) + ","
                + csvNumber(skinTemp) + ","
                + csvNumber(exertion);
    }

    private static String csvNumber(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "N/A".equalsIgnoreCase(trimmed)
                || "NONE".equalsIgnoreCase(trimmed)) {
            return "";
        }
        try {
            Double.parseDouble(trimmed);
            if ("NaN".equalsIgnoreCase(trimmed) || "Infinity".equalsIgnoreCase(trimmed)
                    || "-Infinity".equalsIgnoreCase(trimmed)) {
                return "";
            }
            return trimmed;
        } catch (NumberFormatException e) {
            return "";
        }
    }

    private static String csvField(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0
                && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String fileTimestamp(Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US);
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        return formatter.format(date);
    }

    private static IOException appendError(IOException first, IOException next) {
        if (first == null) {
            return next;
        }
        first.addSuppressed(next);
        return first;
    }
}
