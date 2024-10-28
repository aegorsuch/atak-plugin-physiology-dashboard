package com.aitech.atak.ssband.preferences;

import static com.atakmap.android.maps.MapView.getMapView;
import static com.aitech.atak.ssband.preferences.SSbandPreferences.*;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.preference.Preference;

import com.aitech.atak.ssband.services.LicenseManager;


//import android.preference.PreferenceManager;

public class SSbandPreferencesResolver {
    public static int resolveUpdateHrInterval() {
        return getIntegerPref(5, UPDATE_HR_INTERVAL);
    }

    public static boolean resolveWipeEnabled() {
        return PreferenceManager
                .getDefaultSharedPreferences(getMapView().getContext())
                .getBoolean(ENABLE_WIPE, false);

    }

    public static boolean resolveAlertsEnabled() {
        return PreferenceManager
                .getDefaultSharedPreferences(getMapView().getContext())
                .getBoolean(ENABLE_ALERTS, false);

    }

    public static boolean resolveSSbandConnectionEnabled() {
        return PreferenceManager
                .getDefaultSharedPreferences(getMapView().getContext())
                .getBoolean(ENABLE_BT_CONNECTIONS, false);
    }

    public static int resolveAge() {
        return getIntegerPref(25, AGE);
    }

    public static boolean resolveLicenseKeyCheck() {
        // return true if the license key in prefs equals TOMMY
        String licenseKeyValue = PreferenceManager.getDefaultSharedPreferences(getMapView().getContext()).getString(LICENSE_KEY_AIT, "Enter License Key");
        LicenseManager.validateLicense(licenseKeyValue);
        return LicenseManager.isLicenseValid();
    }

    public static int resolveHighHeartRatePeriod() {
        return getIntegerPref(60, HIGH_HEART_RATE_PERIOD);
    }

    public static int resolveLowHeartRatePeriod() {
        return getIntegerPref(60, LOW_HEART_RATE_PERIOD);
    }
    public static int resolveLowHeartRate() {
        return getIntegerPref(50, LOW_HEART_RATE);
    }

    public static String resolveDeviceSerialNumber() {
        return PreferenceManager
                .getDefaultSharedPreferences(getMapView().getContext())
                .getString(DEVICE_SERIAL_NUMBER, "None");
    }

    private static int getIntegerPref(int defaultValue, String updateHrInterval) {
        try {
            String intervalString = PreferenceManager
                    .getDefaultSharedPreferences(getMapView().getContext())
                    .getString(updateHrInterval, Integer.toString(defaultValue));
            return Integer.parseInt(intervalString);
        } catch (Exception e) {
            return defaultValue;
        }
    }

}
