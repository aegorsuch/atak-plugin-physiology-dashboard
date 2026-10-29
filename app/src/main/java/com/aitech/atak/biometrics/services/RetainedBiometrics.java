package com.aitech.atak.biometrics.services;

import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.maps.time.CoordinatedTime;

public final class RetainedBiometrics {
    private RetainedBiometrics() {
    }

    public static CotEvent snapshot(String uid, String callsign, CotDetail details,
                                    long lastUpdateTime) {
        CotDetail biometrics = PhysiologyDetails.find(details);
        if (uid == null || callsign == null || lastUpdateTime < 0 || biometrics == null
                || biometrics.getAttribute("alertState") != null
                || biometrics.getFirstChildByName(0, "device") == null) {
            return null;
        }

        CotDetail contact = new CotDetail("contact");
        contact.setAttribute("callsign", callsign);
        details.addChild(contact);
        CotEvent event = new CotEvent();
        event.setUID(uid);
        event.setTime(new CoordinatedTime(lastUpdateTime));
        event.setStart(new CoordinatedTime(lastUpdateTime));
        // Opaque details do not retain the sender's expiry; never renew cached data.
        event.setStale(new CoordinatedTime(lastUpdateTime));
        event.setDetail(details);
        return event;
    }
}
