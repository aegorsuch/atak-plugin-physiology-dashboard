package com.aitech.atak.ssband.heartrate;

import static com.atakmap.coremap.filesystem.FileSystemUtils.isEmpty;
import static com.aitech.atak.ssband.heartrate.Constants.DETAILS_META_KEY_HEART_RATE;

import com.atakmap.android.cot.detail.CotDetailHandler;
import com.atakmap.android.maps.MapItem;
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;

public class HeartRateCodHandler extends CotDetailHandler {

    public static final String HEART_RATE_COD_KEY = "HEART_RATE_COD_KEY";

    public HeartRateCodHandler() {
        super(HEART_RATE_COD_KEY);
    }

    @Override
    public CommsMapComponent.ImportResult toItemMetadata(
            MapItem item, CotEvent event, CotDetail detail) {
        String heartRateString = detail.getAttribute(DETAILS_META_KEY_HEART_RATE);

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
