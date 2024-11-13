/*
 * Copyright (C) 2024, Ascent Integrated Tech. All rights reserved.
 *
 * The copyright to the computer software herein is the property of Ascent Integrated Tech.
 * This software may not be used and/or copied without the explicit written permission
 * of Ascent Integrated Tech, and only in accordance with the terms and conditions stipulated
 * in the license agreement and/or contract under which the software has been supplied.
 */

package com.aitech.atak.ssband.services;

import static com.atakmap.android.maps.MapView.getMapView;
import static com.aitech.atak.ssband.heartrate.Constants.DETAILS_META_KEY_HEART_RATE;
import static com.aitech.atak.ssband.heartrate.HeartRateCodHandler.HEART_RATE_COD_KEY;

import android.content.Intent;
import android.util.Log;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.comms.ReportingRate;
import com.atakmap.coremap.cot.event.CotDetail;

import com.atakmap.android.cot.CotUtils;
import com.aitech.atak.ssband.heartrate.HeartRateCodHandler;

import com.atakmap.android.maps.MapView;
import com.atakmap.comms.CotDispatcher;
import com.atakmap.comms.DispatchFlags;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotDetail;
import java.util.Date;
import java.util.Map;

import com.atakmap.coremap.maps.time.CoordinatedTime;

public class CotDetailsUpdaterService {

    public static final String TAG = "CotService";
    private final HeartRateCodHandler heartRateCodHandler;

    public CotDetailsUpdaterService(HeartRateCodHandler heartRateCodHandler) {

        this.heartRateCodHandler = heartRateCodHandler;
    }

    public void updateDetails(int hr) {
        Log.d(TAG, "Sending CoT with enhanced details...");
        CotDetail cd = new CotDetail(HEART_RATE_COD_KEY);
        cd.setAttribute(DETAILS_META_KEY_HEART_RATE, Integer.toString(hr));
        if (heartRateCodHandler != null) {
            heartRateCodHandler.toItemMetadata(getMapView().getSelfMarker(), null, cd);
        }
        CotMapComponent.getInstance().addAdditionalDetail(cd.getElementName(), cd);
        Intent updateIntent = new Intent(ReportingRate.REPORT_LOCATION).putExtra("reason", "detail update for heart rate");
        AtakBroadcast.getInstance().sendBroadcast(updateIntent);
    }

    public void dispatchCot(String deviceId, int hr, String deviceSerial, int batteryLevel) {
        Log.d(TAG, "Sending CoT with heart rate details...");

        // Create a new CoT event without location data
        CotEvent cotEvent = new CotEvent();
        cotEvent.setUID(deviceId);
        cotEvent.setType("t-x-c-heartRate"); // Custom type for heart rate events
        cotEvent.setHow("m-g");

        // Set the current time using CoordinatedTime
        CoordinatedTime now = new CoordinatedTime();
        cotEvent.setTime(now);
        cotEvent.setStart(now);
        cotEvent.setStale(now.addSeconds(15)); // Valid for 15 seconds

        // Do not set lat/lon if not needed
        String callsign = MapView.getMapView().getDeviceCallsign();
        Log.d(TAG, "Callsign is: " + callsign);

        // Add heart rate, serial number, battery, callsign detail
        CotDetail detail = new CotDetail();
        CotDetail statusDetail = new CotDetail("status");
        statusDetail.setAttribute("heartRate", Integer.toString(hr));
        statusDetail.setAttribute("serialNumber", deviceSerial);
        statusDetail.setAttribute("batteryLevel", Integer.toString(batteryLevel));
        statusDetail.setAttribute("callsign", callsign);

        detail.addChild(statusDetail);
        cotEvent.setDetail(detail);

        // Log the event for debugging
        Log.d("CotDispatcher", "Dispatching CoT Event: " + cotEvent.toString());

        // Create a CotDispatcher and dispatch the event
        CotDispatcher cotDispatcher = new CotDispatcher();
        cotDispatcher.setDispatchFlags(DispatchFlags.EXTERNAL | DispatchFlags.INTERNAL); // Use INTERNAL to keep it within the app
        cotDispatcher.dispatch(cotEvent);
        Log.d(TAG, "CoT event dispatched.");
    }
}
