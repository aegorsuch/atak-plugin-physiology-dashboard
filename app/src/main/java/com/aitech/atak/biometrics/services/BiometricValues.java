package com.aitech.atak.biometrics.services;

public final class BiometricValues {
    private BiometricValues() {
    }

    public static Double parseExertionPercent(String value) {
        Double parsed = parseNumber(value);
        if (parsed != null && parsed >= 0.0 && parsed <= 1.0) {
            return parsed * 100.0;
        }
        return parsed;
    }

    public static Double parseSkinTemperatureCelsius(String value, String model) {
        Double parsed = parseNumber(value);
        if (parsed != null && "WEAROS".equalsIgnoreCase(model)) {
            return (parsed - 32.0) * 5.0 / 9.0;
        }
        return parsed;
    }

    private static Double parseNumber(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || "N/A".equalsIgnoreCase(normalized)
                || "NONE".equalsIgnoreCase(normalized)) {
            return null;
        }
        try {
            return Double.valueOf(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
