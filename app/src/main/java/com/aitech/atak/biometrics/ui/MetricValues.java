package com.aitech.atak.biometrics.ui;

import com.aitech.atak.biometrics.DeviceData;

import java.util.Locale;

public final class MetricValues {
    private MetricValues() {
    }

    public static boolean isAvailable(Double value) {
        return value != null && !value.isNaN() && !value.isInfinite() && value != -1.0;
    }

    public static boolean hasPhysiologicalData(DeviceData data) {
        return isAvailable(data.exertion) || isAvailable(data.heartRate)
                || isAvailable(data.coreTemp) || isAvailable(data.spo2)
                || isAvailable(data.hssi) || isAvailable(data.cadence)
                || isAvailable(data.caloricBurnRate) || isAvailable(data.hrlv)
                || isAvailable(data.vo2) || isAvailable(data.hrv);
    }

    public static String format(Double value, String format) {
        return isAvailable(value) ? String.format(Locale.ENGLISH, format, value) : "";
    }

    public static String skinTemperature(Double celsius) {
        return isAvailable(celsius)
                ? String.format(Locale.ENGLISH, "%.2f \u00b0F", celsius * 9 / 5 + 32) : "";
    }

    public static int compare(Double a, Double b, boolean descending) {
        if (!isAvailable(a)) return isAvailable(b) ? 1 : 0;
        if (!isAvailable(b)) return -1;
        return descending ? Double.compare(b, a) : Double.compare(a, b);
    }
}
