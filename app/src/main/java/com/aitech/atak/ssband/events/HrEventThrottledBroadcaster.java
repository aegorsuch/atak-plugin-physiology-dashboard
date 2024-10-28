package com.aitech.atak.ssband.events;

import static com.atakmap.android.maps.MapView.getMapView;
import static com.aitech.atak.ssband.events.Events.HR_RECEIVED;
import static com.aitech.atak.ssband.events.Events.HR_RECEIVED_PARAM_VALUE;
import static com.aitech.atak.ssband.preferences.SSbandPreferencesResolver.*;
import static java.lang.System.currentTimeMillis;

import android.content.Intent;
import android.util.Log;

import com.aitech.atak.ssband.preferences.SSbandPreferencesResolver;

public class HrEventThrottledBroadcaster {

    private static final String TAG = "SSbandBroadcaster";
    private long lastSentTime = currentTimeMillis();

    public void broadcastHrEvent(String deviceId, Integer hr, String serialNumber, int batteryLevel, boolean sendImmediately) {
        if (sendImmediately) {
            Log.d(TAG, "Broadcasting Hear rate - " + hr + " (immediate)");
            broadcast(deviceId, hr, serialNumber, batteryLevel);
            return;
        }
        Log.d(TAG, deviceId);
        boolean intervalPassed = currentTimeMillis() - lastSentTime > resolveUpdateHrInterval() * 1000L;

        if (intervalPassed) {
            Log.d(TAG, "Broadcasting Hear rate - " + hr + " (throttled).");
            broadcast(deviceId, hr, serialNumber, batteryLevel);
            lastSentTime = currentTimeMillis();
        }
    }

    private static void broadcast(String deviceId, Integer hr, String serialNumber, int batteryLevel) {
        Intent intent = new Intent();
        intent.setAction(HR_RECEIVED);
        intent.putExtra(HR_RECEIVED_PARAM_VALUE, hr);
        intent.putExtra("DEVICE_ID", deviceId);     // add ssband device ID (e.g. MAC) into the intent, so if we have multiple ssbands, we can differentiate CoTs.
        intent.putExtra("SERIAL_NUMBER", serialNumber);
        intent.putExtra("BATTERY_LEVEL", batteryLevel); // add battery level
        getMapView().getContext().sendBroadcast(intent);
    }
}
