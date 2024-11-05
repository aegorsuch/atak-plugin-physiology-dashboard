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

    public static boolean resolveLicenseKeyCheck() {
        // return true if the license key in prefs equals "AscentFreeLicense"
        String licenseKeyValue = PreferenceManager.getDefaultSharedPreferences(getMapView().getContext()).getString(LICENSE_KEY_AIT, "Enter License Key");
        LicenseManager.validateLicense(licenseKeyValue);
        return LicenseManager.isLicenseValid();
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
