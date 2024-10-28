package com.aitech.atak.ssband.services;

import static com.aitech.atak.ssband.preferences.SSbandPreferencesResolver.resolveAge;
import static com.aitech.atak.ssband.preferences.SSbandPreferencesResolver.resolveHighHeartRatePeriod;
import static com.aitech.atak.ssband.preferences.SSbandPreferencesResolver.resolveLowHeartRate;
import static java.lang.System.*;

import android.util.Log;

public class AlertMonitorService {

    private static final String TAG = "SSbandAlertTriggererService";
    private long lastNormalValueHighRate = currentTimeMillis();
    private long lastNormalValueLowRate = currentTimeMillis();

    private boolean highRateAlertTriggered;
    private boolean lowRateAlertTriggered;

    public void monitorHearRate(int hr) {
        boolean isToHigh = hr > 211 - (0.64 * resolveAge());
        boolean isToLow = hr < resolveLowHeartRate();

        Log.d(TAG, String.format("hr: %d | isToHigh: %s | isToLow: %s", hr, isToHigh, isToLow));

        handleHighValueCheck(isToHigh);
        handleLowValueCheck(isToLow);
    }

    private void handleHighValueCheck(boolean isToHigh) {
        if (isToHigh) {
            Log.d(TAG, "HR not ok (too high)");
            if (!highRateAlertTriggered && periodFromLastNormalValue(lastNormalValueHighRate) > resolveHighHeartRatePeriod()) {
                Log.d(TAG, "Triggering alert (too high hr)");
                AlertService.send991Alert();
                highRateAlertTriggered = true;
            }
        } else {
            Log.d(TAG, "HR ok (high)");
            lastNormalValueHighRate = currentTimeMillis();
            highRateAlertTriggered = false;
        }
    }

    private void handleLowValueCheck(boolean isToLow) {
        if (isToLow) {
            Log.d(TAG, "HR not ok (too low)");
            if (!lowRateAlertTriggered && periodFromLastNormalValue(lastNormalValueLowRate) > resolveHighHeartRatePeriod()) {
                Log.d(TAG, "Triggering alert (too low hr)");
                AlertService.send991Alert();
                lowRateAlertTriggered = true;
            }
        } else {
            Log.d(TAG, "HR ok (low)");
            lastNormalValueLowRate = currentTimeMillis();
            lowRateAlertTriggered = false;
        }
    }

    private long periodFromLastNormalValue(long lastNormalValue) {
        return (currentTimeMillis() - lastNormalValue) / 1000;
    }
}
