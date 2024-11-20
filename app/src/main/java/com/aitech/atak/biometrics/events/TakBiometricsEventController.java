
/*
 * Copyright (C) 2024, Ascent Integrated Tech. All rights reserved.
 *
 * The copyright to the computer software herein is the property of Ascent Integrated Tech.
 * This software may not be used and/or copied without the explicit written permission
 * of Ascent Integrated Tech, and only in accordance with the terms and conditions stipulated
 * in the license agreement and/or contract under which the software has been supplied.
 */

package com.aitech.atak.biometrics.events;

import static com.aitech.atak.biometrics.events.Events.HR_RECEIVED;
import static com.aitech.atak.biometrics.events.Events.HR_RECEIVED_PARAM_VALUE;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.aitech.atak.biometrics.heartrate.HeartRateCotHandler;
import com.aitech.atak.biometrics.preferences.BiometricsPreferenceFragment;
import com.aitech.atak.biometrics.services.CotDetailsUpdaterService;
import com.aitech.atak.biometrics.ui.TakBiometricsDropDown;


public class TakBiometricsEventController extends BroadcastReceiver {
    private static final String TAG = "TakBiometricsEventCtrl";
    private final CotDetailsUpdaterService cotDetailsUpdaterService;
    private TakBiometricsDropDown takBiometricsDropDown;
    private BiometricsPreferenceFragment biometricsPreferenceFragment;

    public TakBiometricsEventController(HeartRateCotHandler heartRateCotHandler, TakBiometricsDropDown dropDown) {
        this.cotDetailsUpdaterService = new CotDetailsUpdaterService(heartRateCotHandler);
        this.takBiometricsDropDown = dropDown;  // Initialize the dropdown
    }


    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        String deviceId = intent.getStringExtra("DEVICE_ID");
        String deviceSerial = intent.getStringExtra("SERIAL_NUMBER");
        int deviceBatteryLevel = intent.getIntExtra("BATTERY_LEVEL",0);

        if (action == null) return;

        switch (action) {
            case HR_RECEIVED:
                handleHrReceived(intent, deviceId, deviceSerial, deviceBatteryLevel);
                break;
        }
    }

    private void handleHrReceived(Intent intent, String deviceId, String serialNumber, int batteryLevel) {
        try {
            int hr = intent.getIntExtra(HR_RECEIVED_PARAM_VALUE, -1);
            Log.d(TAG, "Handling HR_RECEIVED event for " + deviceId + "with HR: " + hr + " bpm.");
            // Update the dropdown with the new heart rate
            if (takBiometricsDropDown != null) {
                // display the heart rate in UI of own self device
                // takBiometricsDropDown.displayHeartRate(hr, deviceId);
            } else {
                Log.e(TAG, "takBiometricsDropDown is null, cannot display heart rate");
            }
            //takBiometricsDropDown.displayHeartRate(hr);

            cotDetailsUpdaterService.dispatchCot(deviceId, hr, serialNumber, batteryLevel);
            cotDetailsUpdaterService.updateDetails(hr);
        } catch (Exception e) {
            Log.e(TAG, "Cannot create CoT details " + e.getMessage());
        }
    }
}
