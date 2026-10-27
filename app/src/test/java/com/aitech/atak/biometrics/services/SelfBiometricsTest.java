package com.aitech.atak.biometrics.services;

import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SelfBiometricsTest {
    private static final String SELF = "ANDROID-8c3a66b30c4e7e8c";
    private static final String WATCH = "WEAROS_ffd863ef0fd282b4";

    private CotEvent event(String uid, String sampleTime, boolean alert) {
        CotDetail root = new CotDetail("detail");
        CotDetail biometrics = new CotDetail("biometrics");
        CotDetail device = new CotDetail("device");
        device.setAttribute("sampleTime", sampleTime);
        CotDetail model = new CotDetail("model");
        model.setInnerText("WEAROS");
        CotDetail deviceUid = new CotDetail("uid");
        deviceUid.setInnerText(WATCH);
        CotDetail hr = new CotDetail("hr");
        hr.setAttribute("sampleTime", sampleTime);
        hr.setInnerText("79");
        device.addChild(model);
        device.addChild(deviceUid);
        device.addChild(hr);
        biometrics.addChild(device);
        if (alert) {
            biometrics.setAttribute("alertState", "ALERT");
        }
        root.addChild(biometrics);
        CotEvent event = new CotEvent();
        event.setUID(uid);
        event.setDetail(root);
        return event;
    }

    @Test
    public void acceptsSelfMarkerBiometricsAndLearnsWatch() {
        SelfBiometrics self = new SelfBiometrics();
        assertTrue(self.accept(event(SELF, "2026-10-27T01:52:40Z", false), SELF));
        assertEquals(WATCH, self.getWatchUid());
    }

    @Test
    public void ignoresOtherDevicesAndMissingSelfUid() {
        SelfBiometrics self = new SelfBiometrics();
        assertFalse(self.accept(event("ANDROID-other", "t1", false), SELF));
        assertFalse(self.accept(event(SELF, "t1", false), null));
        assertNull(self.getWatchUid());
    }

    @Test
    public void ignoresAlertsAndEventsWithoutBiometrics() {
        SelfBiometrics self = new SelfBiometrics();
        assertFalse(self.accept(event(SELF, "t1", true), SELF));
        CotEvent plain = new CotEvent();
        plain.setUID(SELF);
        plain.setDetail(new CotDetail("detail"));
        assertFalse(self.accept(plain, SELF));
    }

    @Test
    public void sameSampleSentToSeveralDestinationsIsProcessedOnce() {
        SelfBiometrics self = new SelfBiometrics();
        assertTrue(self.accept(event(SELF, "t1", false), SELF));
        assertFalse(self.accept(event(SELF, "t1", false), SELF));
        assertTrue(self.accept(event(SELF, "t2", false), SELF));
    }

    @Test
    public void selfWatchCotSharesTheSelfCard() {
        assertEquals(SELF, SelfBiometrics.cardKey(SELF, WATCH, SELF, null));
        assertEquals(SELF, SelfBiometrics.cardKey(WATCH, WATCH, SELF, WATCH));
        assertEquals(SELF, SelfBiometrics.cardKey("WEAROS_alias", WATCH, SELF, WATCH));
    }

    @Test
    public void otherDevicesKeepTheirOwnCards() {
        assertEquals("WEAROS_f97a246b4d2e5cee", SelfBiometrics.cardKey(
                "WEAROS_f97a246b4d2e5cee", "WEAROS_f97a246b4d2e5cee", SELF, WATCH));
        assertEquals(WATCH, SelfBiometrics.cardKey(WATCH, WATCH, SELF, null));
    }
}
