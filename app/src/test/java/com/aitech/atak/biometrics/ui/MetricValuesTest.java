package com.aitech.atak.biometrics.ui;

import org.junit.Test;
import com.aitech.atak.biometrics.DeviceData;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MetricValuesTest {
    @Test
    public void batteryOnlyAndUnavailableUnitsHaveNoPhysiologicalData() {
        DeviceData data = new DeviceData("WearTAK", "Team One", false, "watch-1");
        data.heartRate = -1.0;
        data.exertion = -1.0;
        data.coreTemp = -1.0;
        data.batteryLevel = 90.0;
        assertFalse(MetricValues.hasPhysiologicalData(data));
        data.exertion = 0.0;
        assertTrue(MetricValues.hasPhysiologicalData(data));
        data.exertion = null;
        data.heartRate = 72.0;
        assertTrue(MetricValues.hasPhysiologicalData(data));
    }
    @Test
    public void unavailableReadingsHaveNoDisplayText() {
        for (Double value : new Double[]{null, -1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertFalse(MetricValues.isAvailable(value));
            assertEquals("", MetricValues.format(value, "%.0f bpm"));
            assertEquals("", MetricValues.skinTemperature(value));
        }
    }

    @Test
    public void validReadingsKeepUnitsAndZeroExertion() {
        assertEquals("0 %", MetricValues.format(0.0, "%.0f %%"));
        assertEquals("72 bpm", MetricValues.format(72.0, "%.0f bpm"));
        assertEquals("98.60 \u00b0F", MetricValues.skinTemperature(37.0));
    }

    @Test
    public void missingReadingsSortLastInBothDirections() {
        for (boolean descending : new boolean[]{false, true}) {
            assertTrue(MetricValues.compare(-1.0, 72.0, descending) > 0);
            assertTrue(MetricValues.compare(72.0, null, descending) < 0);
            assertEquals(0, MetricValues.compare(null, -1.0, descending));
        }
        assertTrue(MetricValues.compare(30.0, 35.0, false) < 0);
        assertTrue(MetricValues.compare(30.0, 35.0, true) > 0);
    }
}
