/*
 * Copyright (C) 2024, Ascent Integrated Tech. All rights reserved.
 *
 * The copyright to the computer software herein is the property of Ascent Integrated Tech.
 * This software may not be used and/or copied without the explicit written permission
 * of Ascent Integrated Tech, and only in accordance with the terms and conditions stipulated
 * in the license agreement and/or contract under which the software has been supplied.
 */

package com.aitech.atak.biometrics.preferences;

import static com.aitech.atak.biometrics.preferences.BiometricsPreferences.ENABLE_TEST_ALERTS;
import static com.atakmap.android.maps.MapView.getMapView;
import static com.aitech.atak.biometrics.preferences.BiometricsPreferences.LICENSE_KEY_AIT;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.gui.PanSwitchPreference;
import com.atakmap.android.preference.PluginPreferenceFragment;
import com.aitech.atak.biometrics.TakBiometricsMapComponent;
import com.aitech.atak.biometrics.plugin.R;

import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.Log;

import com.aitech.atak.biometrics.services.LicenseManager;

public class BiometricsPreferenceFragment extends PluginPreferenceFragment {

    public static final String TAG = "BiometricsPreferenceFr";
    private static Context staticPluginContext;

    /**
     * Only will be called after this has been instantiated with the 1-arg constructor.
     * Fragments must has a zero arg constructor.
     */
    public BiometricsPreferenceFragment() {
        super(staticPluginContext, R.xml.preferences);
    }

    @SuppressLint("ValidFragment")
    public BiometricsPreferenceFragment(final Context pluginContext) {
        super(pluginContext, R.xml.preferences);
        staticPluginContext = pluginContext;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Preference LicenseKeyPreference = findPreference(LICENSE_KEY_AIT);
        if (null != LicenseKeyPreference) {
            LicenseKeyPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    String oldValue = PreferenceManager.getDefaultSharedPreferences(getMapView().getContext()).getString(LICENSE_KEY_AIT, "");
                    String newPreferenceValue = newValue.toString();
                    Log.d(TAG, "Old License Key: " + oldValue);
                    Log.d(TAG, "New License Key: " + newPreferenceValue);

                    if (oldValue.equals(newPreferenceValue)) {
                        Log.d(TAG, "License Key has not changed.");
                        return true;
                    }

                    // Validate the new license
                    LicenseManager.validateLicense(newPreferenceValue);

                    if (LicenseManager.isLicenseValid()) {
                        Log.d(TAG, "License Key is valid. Checking biometrics enable prefs");
                        TakBiometricsMapComponent.getTakBiometricsDropDown().displayInfoOnUI("License key valid");
                    } else {
                        Log.d(TAG, "License Key is invalid. Clearing CoT messages & UI");
                        TakBiometricsMapComponent.getTakBiometricsDropDown().displayInfoOnUI("License Key invalid");
                        TakBiometricsMapComponent.getTakBiometricsDropDown().stopScheduler();
                        TakBiometricsMapComponent.getTakBiometricsDropDown().removeDevices();
                        TakBiometricsMapComponent.getTakBiometricsDropDown().unregisterCotHandler(); // Unregister the cot handler

                    }

                    // Refresh the dropdown
                    TakBiometricsMapComponent.getTakBiometricsDropDown().refreshDropDown();

                    return true;
                }
            });
        } else {
            Log.e(TAG, "ERROR: LicenseKeyPreference is null. Cannot set preference change listener...");
        }
        Preference enableTestAlertsPreference = findPreference(ENABLE_TEST_ALERTS);
        if (null != enableTestAlertsPreference) {
            enableTestAlertsPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    boolean checked = ((PanSwitchPreference) preference).isChecked();
                    Log.d(TAG, "onPreferenceClick ENABLE_TEST_ALERTS is " + checked);
                    Log.d(TAG, "SharedPreference for ENABLE_TEST_ALERTS is " +
                            PreferenceManager.getDefaultSharedPreferences(getMapView().getContext()).getBoolean(ENABLE_TEST_ALERTS, false));
                    return true;
                }
            });
        } else {
            Log.e(TAG, "ERROR: EnableTestAlertsPreference is null. Cannot set on preference click listener...");
        }
    }
}

