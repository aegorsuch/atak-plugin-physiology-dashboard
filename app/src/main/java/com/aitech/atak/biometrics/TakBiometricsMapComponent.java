/*
 * Copyright (C) 2024, Ascent Integrated Tech. All rights reserved.
 *
 * The copyright to the computer software herein is the property of Ascent Integrated Tech.
 * This software may not be used and/or copied without the explicit written permission
 * of Ascent Integrated Tech, and only in accordance with the terms and conditions stipulated
 * in the license agreement and/or contract under which the software has been supplied.
 */

package com.aitech.atak.biometrics;

import static android.content.Context.RECEIVER_EXPORTED;
import static com.aitech.atak.biometrics.events.Events.HR_RECEIVED;
import static com.aitech.atak.biometrics.preferences.BiometricsPreferences.PREFERENCES_KEYS;
import static com.aitech.atak.biometrics.ui.TakBiometricsDropDown.SHOW_DROP_DOWN;

import com.aitech.atak.biometrics.preferences.BiometricsPreferenceFragment;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;

import com.atakmap.android.contact.ContactLocationView;
import com.atakmap.android.cot.detail.CotDetailManager;
import com.atakmap.android.dropdown.DropDownMapComponent;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.preferences.ToolsPreferenceFragment;
import com.aitech.atak.biometrics.events.TakBiometricsEventController;
import com.aitech.atak.biometrics.heartrate.ExtendedUserDetails;
import com.aitech.atak.biometrics.heartrate.HeartRateCotHandler;
import com.aitech.atak.biometrics.plugin.R;
import com.aitech.atak.biometrics.ui.TakBiometricsDropDown;
import com.aitech.atak.biometrics.preferences.BiometricsPreferencesResolver;

public class TakBiometricsMapComponent extends DropDownMapComponent {

    private Context context;
    private TakBiometricsEventController takBiometricsEventController;
    private static TakBiometricsDropDown takBiometricsDropDown;        // will have to make this public so we can access the updateHeartRate functionality
    private final String TAG = "TakBiometricsMapComponent";
    private HeartRateCotHandler heartRateCotHandler;
    private ExtendedUserDetails extendedUserDetails;
    // TODO test only: remove line below
    public static int testCounter = 0;

    @Override
    public void onCreate(final Context context, Intent intent, final MapView view) {
        this.context = context;
        context.setTheme(R.style.ATAKPluginTheme);

        registerTakBiometricsDropDown(context, view);         // register the Drop Down menu for the plugin, where preferences button, heart rate, etc are displayed to the user.
        registerHeartRateCotHandler();
        registerUserDetailsExtension(context, view);
        registerEventController();
        registerPreferences();

        // this is run once once bc onCreate(). Check the license, check if Biometrics is enabled in prefs.
        if (licenseKeyCheck()) {
            Log.d(TAG, "License Key is valid.");
            TakBiometricsMapComponent.getTakBiometricsDropDown().displayInfoOnUI("License Key valid");
        } else {
            Log.d(TAG, "License Key is invalid.");
            TakBiometricsMapComponent.getTakBiometricsDropDown().displayInfoOnUI("License Key invalid");
        }
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        super.onDestroyImpl(context, view);
        takBiometricsDropDown.closeDropDown();
        takBiometricsDropDown.disposeImpl();
        context.unregisterReceiver(takBiometricsEventController);
        CotDetailManager.getInstance().unregisterHandler(heartRateCotHandler);
        ContactLocationView.unregister(extendedUserDetails);
        ToolsPreferenceFragment.unregister(PREFERENCES_KEYS);
    }

    public static TakBiometricsDropDown getTakBiometricsDropDown() {
        return takBiometricsDropDown;
    }

    private void registerPreferences() {
        ToolsPreferenceFragment.register(
                new ToolsPreferenceFragment.ToolPreference(
                        context.getResources().getString(R.string.preferences_name),
                        context.getResources().getString(R.string.preferences_description),
                        PREFERENCES_KEYS,
                        context.getResources().getDrawable(R.drawable.aitbiometricslogo, null),
                        new BiometricsPreferenceFragment(context))
        );
    }

    private static boolean licenseKeyCheck() {
        return BiometricsPreferencesResolver.resolveLicenseKeyCheck();
        // check to see if the license is valid so we know whether to initialize biometrics services or not.
    }

    // need this for Biometrics Prefs Fragment listener that restarts biometrics services. We need to check the license here as well.
    public static boolean getLicenseKeyCheck() {
        return licenseKeyCheck();
    }

    private void registerUserDetailsExtension(Context context, MapView view) {
        extendedUserDetails = new ExtendedUserDetails(context, view.getContext());
        ContactLocationView.register(extendedUserDetails);
    }

    private void registerHeartRateCotHandler() {
        heartRateCotHandler = new HeartRateCotHandler();
        CotDetailManager.getInstance().registerHandler(heartRateCotHandler);
    }

    private void registerTakBiometricsDropDown(Context context, MapView view) {
        takBiometricsDropDown = new TakBiometricsDropDown(view, context);
        AtakBroadcast.DocumentedIntentFilter ddFilter = new AtakBroadcast.DocumentedIntentFilter();
        ddFilter.addAction(SHOW_DROP_DOWN, "Show plugin's drop-down menu");
        this.registerDropDownReceiver(takBiometricsDropDown, ddFilter);
        Log.d(TAG, "Registered TakBiometricsDropDown");
    }


    private void registerEventController() {
        takBiometricsEventController = new TakBiometricsEventController(heartRateCotHandler, takBiometricsDropDown);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(HR_RECEIVED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.registerReceiver(takBiometricsEventController, intentFilter, RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(takBiometricsEventController, intentFilter);
        }
    }

}
