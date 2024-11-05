package com.aitech.atak.ssband.preferences;

import static com.atakmap.android.maps.MapView.getMapView;
import static com.aitech.atak.ssband.preferences.SSbandPreferences.DEVICE_SERIAL_NUMBER;
import static com.aitech.atak.ssband.preferences.SSbandPreferences.ENABLE_BT_CONNECTIONS;       // import the enable bluetooth connections from the preferences, so we can listen to it change state.
import static com.aitech.atak.ssband.preferences.SSbandPreferences.ENABLE_ALERTS;               // enable alerts pref
import static com.aitech.atak.ssband.preferences.SSbandPreferences.LICENSE_KEY_AIT;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.preference.PluginPreferenceFragment;
import com.aitech.atak.ssband.TakSSbandMapComponent;
import com.aitech.atak.ssband.plugin.R;

import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.Log;

import com.atakmap.android.gui.PanSwitchPreference;
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

        Preference enableSSbandPreference = findPreference(ENABLE_BT_CONNECTIONS);
        enableSSbandPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                boolean oldValue = SSbandPreferencesResolver.resolveSSbandConnectionEnabled();
                boolean newValueBoolean = (Boolean) newValue;
                Log.d(TAG, "Old ENABLE_BT_CONNECTIONS: " + oldValue);
                Log.d(TAG, "New ENABLE_BT_CONNECTIONS: " + newValueBoolean);

                if (oldValue == newValueBoolean) {
                    Log.d(TAG, "ENABLE_BT_CONNECTIONS has not changed.");
                    return true;
                }

                if (!LicenseManager.isLicenseValid()) {
                    Log.d(TAG, "License Key Invalid!");
                    TakSSbandMapComponent.getTakSSbandDropDown().displayInfoOnUI("License Key invalid");
                    TakSSbandMapComponent.getTakSSbandDropDown().stopScheduler();
                    TakSSbandMapComponent.getTakSSbandDropDown().removeDevices();
                    TakSSbandMapComponent.getTakSSbandDropDown().unregisterCotStreamListener(); // Unregister the listener
                } else {
                    TakSSbandMapComponent.getTakSSbandDropDown().displayInfoOnUI("License Key valid");
                }
                return true;
            }
        });


//        enableSSbandPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
//            @Override
//            public boolean onPreferenceClick(Preference preference) {
//                boolean checked = ((PanSwitchPreference) preference).isChecked();
//                Log.d(TAG, "onPreferenceClick ENABLE_BT_CONNECTIONS is " + checked);
//                Log.d(TAG, "SharedPreference for ENABLE_BT_CONNECTIONS is " +
//                        PreferenceManager.getDefaultSharedPreferences(getMapView().getContext()).getBoolean(ENABLE_BT_CONNECTIONS, false));
//                if (checked) {
//                    Log.d(TAG, "SSband Services are enabled in preferences.");
//                    // check the license
//                    boolean licenseKeyCheck = SSbandPreferencesResolver.resolveLicenseKeyCheck();
//                    Log.d(TAG, "License Key Check is: " + licenseKeyCheck);
//                    if (licenseKeyCheck) {
//                        Log.d(TAG, "License Key is valied. Initializing SSband Services...");
//                        TakSSbandMapComponent.getSSbandService().initializeSSband();
//                        TakSSbandMapComponent.getTakSSbandDropDown().displayInfoOnUI("Scanning...");
//                    }
//                    else {
//                        Log.d(TAG, "License Key is invalid. Will not initialize SSband Services");
//                        TakSSbandMapComponent.getSSbandService().disposeSSband();
//                        TakSSbandMapComponent.getTakSSbandDropDown().displayInfoOnUI("License Key Invalid.");
//                    }
//                }
//                else {
//                    Log.d(TAG, "SSband Services are disabled in preferences.");
//                    TakSSbandMapComponent.getSSbandService().disposeSSband();
//                    TakSSbandMapComponent.getTakSSbandDropDown().displayInfoOnUI("SSband Connections Disabled.");
//                    TakSSbandMapComponent.getTakSSbandDropDown().removeLocalDevice();
//                }
//                return true;
//            }
//        });


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


        Preference enableAlertPreference = findPreference(ENABLE_ALERTS);
        enableAlertPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                boolean checked = ((PanSwitchPreference) preference).isChecked();
                Log.d(TAG, "onPreferenceClick ENABLE_ALERTS is " + checked);
                Log.d(TAG, "SharedPreference for ENABLE_ALERTS is " +
                        PreferenceManager.getDefaultSharedPreferences(getMapView().getContext()).getBoolean(ENABLE_ALERTS, false));
                return true;
            }
        });

    }

}

