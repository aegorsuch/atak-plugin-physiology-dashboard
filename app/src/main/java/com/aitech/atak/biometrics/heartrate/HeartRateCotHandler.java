/*
 * Copyright (C) 2024, Ascent Integrated Tech. All rights reserved.
 *
 * The copyright to the computer software herein is the property of Ascent Integrated Tech.
 * This software may not be used and/or copied without the explicit written permission
 * of Ascent Integrated Tech, and only in accordance with the terms and conditions stipulated
 * in the license agreement and/or contract under which the software has been supplied.
 */

package com.aitech.atak.biometrics.heartrate;

import static com.atakmap.coremap.filesystem.FileSystemUtils.isEmpty;
import static com.aitech.atak.biometrics.heartrate.Constants.DETAILS_META_KEY_HEART_RATE;

import android.util.Log;

import com.atakmap.android.cot.detail.CotDetailHandler;
import com.atakmap.android.maps.MapItem;
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;

public class HeartRateCotHandler extends CotDetailHandler {

    public static final String HEART_RATE_COT_KEY = "HEART_RATE_COT_KEY";
    private static final String TAG = "TakBiometricsHRCH";

    public HeartRateCotHandler() {
        super(HEART_RATE_COT_KEY);
        Log.d("WEARTAK_STARTUP", "HeartRateCotHandler registered");
    }

    @Override
    public CommsMapComponent.ImportResult toItemMetadata(
            MapItem item, CotEvent event, CotDetail detail) {

        String heartRateString = detail.getAttribute(DETAILS_META_KEY_HEART_RATE);
        //Log.d(TAG, "received mapItem: " + item.toString());
        //Log.d(TAG, "received event: " + event.toString());
        //Log.d(TAG, "received COT w/ detail: " + detail.toString());
        //Log.d(TAG, "heartRateString: " + heartRateString);

        if (isEmpty(heartRateString)) {
            return CommsMapComponent.ImportResult.FAILURE;
        }

        int heartRate = Integer.parseInt(heartRateString);

        item.setMetaInteger(DETAILS_META_KEY_HEART_RATE, heartRate);

        return CommsMapComponent.ImportResult.SUCCESS;
    }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event,
                               CotDetail root) {
        return true;
    }
}
