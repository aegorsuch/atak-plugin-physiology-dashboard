package com.aitech.atak.biometrics.services;

import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;

/**
 * Selects the local device's outbound self-marker CoT events that carry
 * biometrics (as attached by the WearTAK Companion plugin) and dedupes the
 * same sample when ATAK sends one event to several destinations.
 */
public class SelfBiometrics {
    private String lastSampleKey;
    private String watchUid;

    /** Returns true when the outbound event is a new self biometrics sample. */
    public synchronized boolean accept(CotEvent event, String selfUid) {
        if (event == null || isBlank(selfUid) || !selfUid.equals(event.getUID())) {
            return false;
        }
        CotDetail device = biometricDevice(event);
        if (device == null) {
            return false;
        }
        String key = sampleKey(event, device);
        if (key.equals(lastSampleKey)) {
            return false;
        }
        lastSampleKey = key;
        String uid = childTextOrAttribute(device, "uid");
        if (!isBlank(uid)) {
            watchUid = uid;
        }
        return true;
    }

    /** UID of the watch last seen on the self marker, or null. */
    public synchronized String getWatchUid() {
        return watchUid;
    }

    /**
     * Chooses the dashboard card key for an event so the local user's watch
     * only produces a single self card, whether it arrives on the self marker
     * or as a separate WEAROS_ CoT.
     */
    public static String cardKey(String eventUid, String biometricDeviceUid,
            String selfUid, String selfWatchUid) {
        if (isBlank(selfUid) || selfUid.equals(eventUid)) {
            return eventUid;
        }
        if (!isBlank(selfWatchUid) && (selfWatchUid.equals(eventUid)
                || selfWatchUid.equals(biometricDeviceUid))) {
            return selfUid;
        }
        return eventUid;
    }

    static CotDetail biometricDevice(CotEvent event) {
        CotDetail detail = event.getDetail();
        if (detail == null) {
            return null;
        }
        CotDetail biometrics = detail.getFirstChildByName(0, "biometrics");
        if (biometrics == null || biometrics.getAttribute("alertState") != null) {
            return null;
        }
        return biometrics.getFirstChildByName(0, "device");
    }

    private static String sampleKey(CotEvent event, CotDetail device) {
        String sampleTime = device.getAttribute("sampleTime");
        if (isBlank(sampleTime)) {
            CotDetail hr = device.getChild("hr");
            sampleTime = hr == null ? null : hr.getAttribute("sampleTime");
        }
        if (!isBlank(sampleTime)) {
            return "t:" + sampleTime;
        }
        long time = event.getTime() == null ? 0 : event.getTime().getMilliseconds();
        return "e:" + time + ":" + childTextOrAttribute(device, "hr");
    }

    private static String childTextOrAttribute(CotDetail detail, String name) {
        CotDetail child = detail.getChild(name);
        return child != null ? child.getInnerText() : detail.getAttribute(name);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
