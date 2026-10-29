package com.aitech.atak.biometrics.services;

import com.atakmap.coremap.cot.event.CotDetail;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PhysiologyDetailsTest {
    @Test
    public void physiologyIsSelectedWithoutLegacyDetails() {
        CotDetail root = new CotDetail("detail");
        CotDetail physiology = new CotDetail("physiology");
        root.addChild(physiology);

        assertSame(physiology, PhysiologyDetails.find(root));
        assertTrue(PhysiologyDetails.isSelected(root, physiology));
    }

    @Test
    public void legacyTelemetryAndAlertsAreStillSelected() {
        CotDetail root = new CotDetail("detail");
        CotDetail legacy = new CotDetail("biometrics");
        root.addChild(legacy);

        assertSame(legacy, PhysiologyDetails.find(root));
        assertTrue(PhysiologyDetails.isSelected(root, legacy));
        legacy.setAttribute("alertState", "ALERT");
        assertTrue(PhysiologyDetails.isSelected(root, legacy));
        legacy.setAttribute("alertState", "CANCEL");
        assertTrue(PhysiologyDetails.isSelected(root, legacy));
    }

    @Test
    public void physiologyAlertsAndCancellationsPreserveMetadataAndDeviceChildren() {
        for (String state : new String[]{"ALERT", "CANCEL"}) {
            CotDetail root = new CotDetail("detail");
            CotDetail physiology = new CotDetail("physiology");
            physiology.setAttribute("alertState", state);
            physiology.setAttribute("alertUid", "alert-1");
            physiology.setAttribute("alertCategory", "HEAT");
            physiology.setAttribute("alertPriority", "1");
            physiology.setAttribute("alertDescription", "Heat alert");
            CotDetail device = new CotDetail("device");
            CotDetail uid = new CotDetail("uid");
            uid.setInnerText("watch-1");
            device.addChild(uid);
            physiology.addChild(device);
            root.addChild(physiology);

            CotDetail selected = PhysiologyDetails.find(root);
            assertTrue(PhysiologyDetails.isSelected(root, physiology));
            assertSame(physiology, selected);
            assertEquals(state, selected.getAttribute("alertState"));
            assertEquals("alert-1", selected.getAttribute("alertUid"));
            assertEquals("HEAT", selected.getAttribute("alertCategory"));
            assertEquals("1", selected.getAttribute("alertPriority"));
            assertEquals("Heat alert", selected.getAttribute("alertDescription"));
            assertEquals("watch-1", selected.getFirstChildByName(0, "device")
                    .getFirstChildByName(0, "uid").getInnerText());
        }
    }

    @Test
    public void physiologyWinsRegardlessOfTagOrder() {
        for (boolean legacyFirst : new boolean[]{true, false}) {
            CotDetail root = new CotDetail("detail");
            CotDetail physiology = new CotDetail("physiology");
            CotDetail legacy = new CotDetail("biometrics");
            root.addChild(legacyFirst ? legacy : physiology);
            root.addChild(legacyFirst ? physiology : legacy);

            assertSame(physiology, PhysiologyDetails.find(root));
            assertTrue(PhysiologyDetails.isSelected(root, physiology));
            assertFalse(PhysiologyDetails.isSelected(root, legacy));
        }
    }

    @Test
    public void legacyAlertsAndCancellationsCoexistWithPhysiologyTelemetry() {
        CotDetail root = new CotDetail("detail");
        CotDetail physiology = new CotDetail("physiology");
        CotDetail legacy = new CotDetail("biometrics");
        root.addChild(physiology);
        root.addChild(legacy);

        for (String state : new String[]{"ALERT", "CANCEL"}) {
            legacy.setAttribute("alertState", state);
            assertSame(physiology, PhysiologyDetails.find(root));
            assertTrue(PhysiologyDetails.isSelected(root, physiology));
            assertTrue(PhysiologyDetails.isSelected(root, legacy));
        }
    }

    @Test
    public void dualTaggedAlertsAndCancellationsSelectOnlyPhysiology() {
        CotDetail root = new CotDetail("detail");
        CotDetail physiology = new CotDetail("physiology");
        CotDetail legacy = new CotDetail("biometrics");
        root.addChild(legacy);
        root.addChild(physiology);

        for (String state : new String[]{"ALERT", "CANCEL"}) {
            physiology.setAttribute("alertState", state);
            legacy.setAttribute("alertState", state);
            assertTrue(PhysiologyDetails.isSelected(root, physiology));
            assertFalse(PhysiologyDetails.isSelected(root, legacy));
        }
    }

    @Test
    public void physiologyAlertDoesNotFallBackToLegacyTelemetry() {
        CotDetail root = new CotDetail("detail");
        CotDetail physiology = new CotDetail("physiology");
        physiology.setAttribute("alertState", "ALERT");
        CotDetail legacy = new CotDetail("biometrics");
        root.addChild(physiology);
        root.addChild(legacy);

        assertTrue(PhysiologyDetails.isSelected(root, physiology));
        assertFalse(PhysiologyDetails.isSelected(root, legacy));
    }

    @Test
    public void missingAndUnrelatedDetailsAreIgnored() {
        CotDetail root = new CotDetail("detail");
        CotDetail contact = new CotDetail("contact");
        root.addChild(contact);

        assertNull(PhysiologyDetails.find(null));
        assertNull(PhysiologyDetails.find(root));
        assertFalse(PhysiologyDetails.isSelected(null, null));
        assertFalse(PhysiologyDetails.isSelected(root, contact));
        root.addChild(new CotDetail("physiology"));
        assertFalse(PhysiologyDetails.isSelected(root, null));
        assertFalse(PhysiologyDetails.isSelected(root, contact));
    }
}
