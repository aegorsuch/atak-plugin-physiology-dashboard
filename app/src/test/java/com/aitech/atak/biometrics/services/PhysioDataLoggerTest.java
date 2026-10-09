package com.aitech.atak.biometrics.services;

import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;

public class PhysioDataLoggerTest {
    @Test
    public void formatRowEscapesCallsignAndLeavesMissingValuesEmpty() {
        String row = PhysioDataLogger.formatRow(
                new Date(0), "Team, \"One\"", "72", "N/A", "0.25");

        assertEquals("1970-01-01T00:00:00.000Z,\"Team, \"\"One\"\"\",72,,0.25", row);
    }
}
