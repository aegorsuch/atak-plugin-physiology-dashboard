package com.aitech.atak.biometrics.services;

import com.atakmap.coremap.cot.event.CotDetail;

public final class PhysiologyDetails {
    public static final String PRIMARY_NAME = "physiology";
    public static final String LEGACY_NAME = "biometrics";

    private PhysiologyDetails() {
    }

    public static CotDetail find(CotDetail details) {
        if (details == null) {
            return null;
        }
        CotDetail physiology = details.getFirstChildByName(0, PRIMARY_NAME);
        return physiology != null ? physiology : details.getFirstChildByName(0, LEGACY_NAME);
    }

    public static boolean isSelected(CotDetail details, CotDetail detail) {
        CotDetail preferred = find(details);
        if (preferred == null || detail == null) {
            return false;
        }
        if (detail == preferred) {
            return true;
        }
        // Legacy alert metadata remains separate from physiology PLI telemetry.
        return preferred.getAttribute("alertState") == null
                && detail.getAttribute("alertState") != null
                && detail == details.getFirstChildByName(0, LEGACY_NAME);
    }
}
