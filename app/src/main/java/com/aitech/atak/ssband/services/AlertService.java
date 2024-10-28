package com.aitech.atak.ssband.services;

import static com.aitech.atak.ssband.preferences.SSbandPreferencesResolver.resolveAlertsEnabled;

import android.util.Log;

import com.atakmap.android.emergency.tool.EmergencyManager;
import com.atakmap.android.emergency.tool.EmergencyType;


public class AlertService {
    private static final String TAG = "SSbandAlertService";

    public static void sendTroopInContactAlert() {
        sendAlert(EmergencyType.TroopsInContact);
    }

    public static void send991Alert() {
        sendAlert(EmergencyType.NineOneOne);
    }

    private static void sendAlert(EmergencyType type) {
        boolean alertsEnabled = resolveAlertsEnabled();
        if (!alertsEnabled) {
            Log.d(TAG, "User triggered ALERT but NOT sending!");
            return;
        }

        Log.d(TAG, "User triggered ALERT!");
        EmergencyManager.getInstance().setEmergencyType(type);
        EmergencyManager.getInstance().initiateRepeat(type, false);
        EmergencyManager.getInstance().setEmergencyOn(true);
    }
}
