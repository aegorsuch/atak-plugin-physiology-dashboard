package com.aitech.atak.ssband.services;

import static com.atakmap.android.maps.MapView.getMapView;

import com.atakmap.android.maps.Marker;
import com.atakmap.android.user.PlacePointTool;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class MarkerService {
    private static final String TAG = "TakSSbandMarker";

    public static void dropPoint() {
        Marker selfMarker = getMapView().getSelfMarker();
        GeoPoint point = selfMarker.getPoint();

        PlacePointTool.MarkerCreator marker = new PlacePointTool.MarkerCreator(point);
        //Log.d(TAG, "title: (" + selfMarker.getMetaString("callsign", "nocallsign") + "), uid: (" + selfMarker.getUID() + ")");
//        String prefix = selfMarker.getMetaString("callsign","");
//        if ("".equals(prefix)) {
//            prefix = "SSband";
//        } else {
//            prefix = prefix + " SSband";
//        }
        // Get the current date and time
        // LocalDateTime currentTime = LocalDateTime.now();
        // Get the current time in GMT
        ZonedDateTime gmtTime = ZonedDateTime.now(ZoneId.of("GMT"));
        // Define the format for the output string
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMdd_HHmmss");
        // Format the time as a string
        String formattedTime = gmtTime.format(formatter);
        marker.setCallsign(getMapView().getDeviceCallsign() + " SSband_" + formattedTime);
        marker.setType("a-u-G");
        marker.placePoint();
    }
}
