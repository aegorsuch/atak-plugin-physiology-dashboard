package com.aitech.atak.biometrics.services;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class BiometricValuesTest {
    @Test
    public void fractionalExertionIsNormalizedToPercent() {
        assertEquals(Double.valueOf(22.0), BiometricValues.parseExertionPercent("0.22"));
        assertEquals(Double.valueOf(0.0), BiometricValues.parseExertionPercent("0"));
        assertEquals(Double.valueOf(100.0), BiometricValues.parseExertionPercent("1.0"));
    }

    @Test
    public void wholePercentageExertionRemainsUnchanged() {
        assertEquals(Double.valueOf(24.0), BiometricValues.parseExertionPercent("24"));
        assertEquals(Double.valueOf(-1.0), BiometricValues.parseExertionPercent("-1"));
    }

    @Test
    public void wearableOsTemperatureFahrenheitIsNormalizedToCelsius() {
        assertEquals(37.3888888889,
                BiometricValues.parseSkinTemperatureCelsius("99.3", "WEAROS"), 0.000000001);
    }

    @Test
    public void otherTemperatureSourcesKeepExistingCelsiusConvention() {
        assertEquals(Double.valueOf(37.0),
                BiometricValues.parseSkinTemperatureCelsius("37", "BIOSTRAP"));
    }

    @Test
    public void unavailableAndMalformedValuesRemainMissing() {
        assertNull(BiometricValues.parseExertionPercent("N/A"));
        assertNull(BiometricValues.parseExertionPercent("not-a-number"));
        assertNull(BiometricValues.parseSkinTemperatureCelsius(null, "WEAROS"));
    }
}
