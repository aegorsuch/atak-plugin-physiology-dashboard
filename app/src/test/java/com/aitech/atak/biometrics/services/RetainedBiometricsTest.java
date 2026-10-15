package com.aitech.atak.biometrics.services;

import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class RetainedBiometricsTest {
    private CotDetail details() {
        CotDetail root = new CotDetail("detail");
        CotDetail biometrics = new CotDetail("biometrics");
        CotDetail device = new CotDetail("device");
        device.setAttribute("uid", "watch-1");
        device.setAttribute("hr", "72");
        device.setAttribute("model", "WearTAK");
        biometrics.addChild(device);
        root.addChild(biometrics);
        return root;
    }

    @Test
    public void snapshotKeepsRetainedReadingAndDoesNotRefreshItsAge() {
        CotDetail details = details();
        CotEvent snapshot = RetainedBiometrics.snapshot("watch-1", "Team One", details, 1000);

        assertEquals("watch-1", snapshot.getUID());
        assertSame(details, snapshot.getDetail());
        assertEquals("Team One", snapshot.getDetail().getFirstChildByName(0, "contact")
                .getAttribute("callsign"));
        assertEquals(1000L, snapshot.getTime().getMilliseconds());
        assertEquals(1000L, snapshot.getStart().getMilliseconds());
        assertEquals(1000L, snapshot.getStale().getMilliseconds());
        assertEquals("72", snapshot.getDetail().getFirstChildByName(0, "biometrics")
                .getFirstChildByName(0, "device").getAttribute("hr"));
    }

    @Test
    public void missingTimestampCannotBecomeAFreshReading() {
        assertNull(RetainedBiometrics.snapshot("watch-1", "Team One", details(), -1));
    }

    @Test
    public void retainedAlertIsNotReplayedAsTelemetry() {
        CotDetail details = details();
        details.getFirstChildByName(0, "biometrics").setAttribute("alertState", "ALERT");
        assertNull(RetainedBiometrics.snapshot("watch-1", "Team One", details, 1000));
    }

    @Test
    public void unrelatedMapDetailsAreIgnored() {
        assertNull(RetainedBiometrics.snapshot("watch-1", "Team One",
                new CotDetail("detail"), 1000));
    }

    @Test
    public void missingIdentityOrContactCannotBeRestored() {
        assertNull(RetainedBiometrics.snapshot(null, "Team One", details(), 1000));
        assertNull(RetainedBiometrics.snapshot("watch-1", null, details(), 1000));
    }
}
