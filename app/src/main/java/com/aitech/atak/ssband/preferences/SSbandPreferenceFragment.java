/*
 * Copyright (C) 2024, Ascent Integrated Tech. All rights reserved.
 *
 * The copyright to the computer software herein is the property of Ascent Integrated Tech.
 * This software may not be used and/or copied without the explicit written permission
 * of Ascent Integrated Tech, and only in accordance with the terms and conditions stipulated
 * in the license agreement and/or contract under which the software has been supplied.
 */

package com.aitech.atak.ssband.preferences;

import static com.atakmap.android.maps.MapView.getMapView;
import static com.aitech.atak.ssband.preferences.SSbandPreferences.LICENSE_KEY_AIT;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.preference.PluginPreferenceFragment;
import com.aitech.atak.ssband.TakSSbandMapComponent;
import com.aitech.atak.ssband.plugin.R;

import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.Log;

import com.aitech.atak.ssband.services.LicenseManager;

public class SSbandPreferenceFragment extends PluginPreferenceFragment {

    public static final String TAG = "SSbandPreferenceFr";
    private static Context staticPluginContext;

    /**
     * Only will be called after this has been instantiated with the 1-arg constructor.
     * Fragments must has a zero arg constructor.
     */
    public SSbandPreferenceFragment() {
        super(staticPluginContext, R.xml.preferences);
    }

    @SuppressLint("ValidFragment")
    public SSbandPreferenceFragment(final Context pluginContext) {
        super(pluginContext, R.xml.preferences);
        staticPluginContext = pluginContext;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Preference LicenseKeyPreference = findPreference(LICENSE_KEY_AIT);
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
                    Log.d(TAG, "License Key is valid. Checking ssband enable prefs");
                    TakSSbandMapComponent.getTakSSbandDropDown().displayInfoOnUI("License key valid");
                } else {
                    Log.d(TAG, "License Key is invalid. Clearing CoT messages & UI");
                    TakSSbandMapComponent.getTakSSbandDropDown().displayInfoOnUI("License Key invalid");
                    TakSSbandMapComponent.getTakSSbandDropDown().stopScheduler();
                    TakSSbandMapComponent.getTakSSbandDropDown().removeDevices();
                    TakSSbandMapComponent.getTakSSbandDropDown().unregisterCotStreamListener(); // Unregister the listener
                }

                // Refresh the dropdown
                TakSSbandMapComponent.getTakSSbandDropDown().refreshDropDown();

                return true;
            }
        });
    }
}

