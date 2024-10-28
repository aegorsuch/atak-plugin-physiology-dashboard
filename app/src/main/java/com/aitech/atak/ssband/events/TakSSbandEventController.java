package com.aitech.atak.ssband.events;

import static com.aitech.atak.ssband.events.Events.HR_RECEIVED;
import static com.aitech.atak.ssband.events.Events.HR_RECEIVED_PARAM_VALUE;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.aitech.atak.ssband.heartrate.HeartRateCodHandler;
import com.aitech.atak.ssband.preferences.SSbandPreferenceFragment;
import com.aitech.atak.ssband.services.AlertMonitorService;
import com.aitech.atak.ssband.services.CotDetailsUpdaterService;
import com.aitech.atak.ssband.ui.TakSSbandDropDown;


public class TakSSbandEventController extends BroadcastReceiver {
    private static final String TAG = "TakSSbandEventCtrl";
    private final CotDetailsUpdaterService cotDetailsUpdaterService;
    private final AlertMonitorService alertMonitorService;
    private TakSSbandDropDown takSSbandDropDown;
    private SSbandPreferenceFragment ssbandPreferenceFragment;

    public TakSSbandEventController(HeartRateCodHandler heartRateCodHandler, TakSSbandDropDown dropDown) {
        this.cotDetailsUpdaterService = new CotDetailsUpdaterService(heartRateCodHandler);
        this.alertMonitorService = new AlertMonitorService();
        this.takSSbandDropDown = dropDown;  // Initialize the dropdown
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
            if (takSSbandDropDown != null) {
                // display the heart rate in UI of own self device
                // takSSbandDropDown.displayHeartRate(hr, deviceId);
            } else {
                Log.e(TAG, "takSSbandDropDown is null, cannot display heart rate");
            }
            //takSSbandDropDown.displayHeartRate(hr);

            cotDetailsUpdaterService.dispatchCot(deviceId, hr, serialNumber, batteryLevel);
            cotDetailsUpdaterService.updateDetails(hr);
            alertMonitorService.monitorHearRate(hr);
        } catch (Exception e) {
            Log.e(TAG, "Cannot create CoT details " + e.getMessage());
        }
    }
}
