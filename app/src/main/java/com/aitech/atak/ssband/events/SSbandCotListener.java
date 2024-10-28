package com.aitech.atak.ssband.events;

import android.util.Log;
import com.atakmap.comms.CotStreamListener;
import com.atakmap.comms.CotServiceRemote;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.aitech.atak.ssband.ui.TakSSbandDropDown;

//public class SSbandCotEventListener implements CotEventListener {
//    private static final String TAG = "SSbandCotEventListener";
//    private TakSSbandDropDown dropDown;
//
//    public SSbandCotEventListener(TakSSbandDropDown dropDown) {
//        this.dropDown = dropDown;
//    }
//
//    @Override
//    public void onCotEvent(CotEvent cotEvent) {
//        // Extract heart rate and device ID from the CoT event
//        CotDetail detail = cotEvent.getDetail();
//        if (detail != null) {
//            // Assuming heart rate is stored under the attribute "heartRate"
//            String heartRateStr = detail.getAttribute("heartRate");
//            String deviceId = cotEvent.getUID();
//
//            if (heartRateStr != null && deviceId != null) {
//                Log.d(TAG, "Received heart rate: " + heartRateStr + " from device: " + deviceId);
//
//                // Update the dropdown
//                int hr = Integer.parseInt(heartRateStr);
//                if (dropDown != null) {
//                    dropDown.displayHeartRate(hr, deviceId);
//                }
//            }
//        }
//    }
//}
