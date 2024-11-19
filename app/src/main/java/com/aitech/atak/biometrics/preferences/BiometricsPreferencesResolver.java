/*
 * Copyright (C) 2024, Ascent Integrated Tech. All rights reserved.
 *
 * The copyright to the computer software herein is the property of Ascent Integrated Tech.
 * This software may not be used and/or copied without the explicit written permission
 * of Ascent Integrated Tech, and only in accordance with the terms and conditions stipulated
 * in the license agreement and/or contract under which the software has been supplied.
 */

package com.aitech.atak.biometrics.preferences;

import static com.atakmap.android.maps.MapView.getMapView;
import static com.aitech.atak.biometrics.preferences.BiometricsPreferences.*;

import android.preference.PreferenceManager;

import com.aitech.atak.biometrics.services.LicenseManager;


//import android.preference.PreferenceManager;

public class BiometricsPreferencesResolver {

    public static boolean resolveLicenseKeyCheck() {
        // return true if the license key in prefs equals "AscentFreeLicense"
        String licenseKeyValue = PreferenceManager.getDefaultSharedPreferences(getMapView().getContext()).getString(LICENSE_KEY_AIT, "Enter License Key");
        LicenseManager.validateLicense(licenseKeyValue);
        return LicenseManager.isLicenseValid();
    }

    public static boolean resolveTestAlertsEnabled() {
        return PreferenceManager
                .getDefaultSharedPreferences(getMapView().getContext())
                .getBoolean(ENABLE_TEST_ALERTS, false);

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
