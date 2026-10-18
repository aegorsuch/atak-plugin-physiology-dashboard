/*
 * Copyright (C) 2024, Ascent Integrated Tech. All rights reserved.
 *
 * The copyright to the computer software herein is the property of Ascent Integrated Tech.
 * This software may not be used and/or copied without the explicit written permission
 * of Ascent Integrated Tech, and only in accordance with the terms and conditions stipulated
 * in the license agreement and/or contract under which the software has been supplied.
 */

package com.aitech.atak.biometrics.ui;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.aitech.atak.biometrics.AlertLevel;
import com.aitech.atak.biometrics.DeviceData;

import com.aitech.atak.biometrics.plugin.R;

import java.util.ArrayList;
import java.util.Locale;

public class CustomAdapter extends ArrayAdapter<DeviceData> {
    public static final String TAG = "CustomAdapter";
    private final Context context;
    private final ArrayList<DeviceData> deviceDataList;
    private TakBiometricsDropDown TakBiometricsDropDown;

    public CustomAdapter(Context context, ArrayList<DeviceData> deviceDataList) {
        super(context, 0, deviceDataList);
        this.context = context;
        this.deviceDataList = deviceDataList;
    }

    @SuppressLint("ResourceAsColor")
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Inflate the layout if it's not already created
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.custom_list_view, parent, false);
        }

        // Get the current DeviceData object
        DeviceData currentDeviceData = deviceDataList.get(position);


        // Find the TextViews in the layout and populate them with the current device data
        TextView textCallsign = convertView.findViewById(R.id.textCallsign);
        TextView textLastConnected = convertView.findViewById(R.id.textLastConnected);

        RelativeLayout expandedHealthData = convertView.findViewById(R.id.expandedHealthData);
        ImageView toggle = convertView.findViewById(R.id.toggle);


        // Populate the views with data from DeviceData
        textCallsign.setText(currentDeviceData.callsign != null ? currentDeviceData.callsign : "- - -");

        long staleTimeSeconds = (currentDeviceData.staleTime.getMilliseconds() /1000) % 60;
        Log.d("connected", String.valueOf(staleTimeSeconds));

        textLastConnected.setText(currentDeviceData.staleTime != null ? "Last connected " + staleTimeSeconds + " seconds ago" : "- - -");

        TextView textisFalsePositive = convertView.findViewById(R.id.textisFalsePositive);
        ImageView iconWearableLogo = convertView.findViewById(R.id.iconWearableLogo);

//        if (String.valueOf(currentDeviceData.model).equals("SS_BAND_V2")) {
//            iconWearableLogo.setImageResource(R.drawable.slatesafety_shield);
//            textExertion.setVisibility(VISIBLE);
//            textCoreBodyTemp.setVisibility(VISIBLE);
//            toggle.setVisibility(VISIBLE);
//        } else {
//            iconWearableLogo.setImageResource(R.drawable.biostrap);
//            toggle.setVisibility(INVISIBLE);
//            textExertion.setVisibility(GONE);
//            textCoreBodyTemp.setVisibility(GONE);
//            expandedHealthData.setVisibility(GONE);
//
//        }

        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // try launching the preferences page activity
//                if (String.valueOf(currentDeviceData.model).equals("SS_BAND_V2")) {
//                    if (expandedHealthData.getVisibility() == GONE) {
//                        try {
//                            expandedHealthData.setVisibility(VISIBLE);
//                            toggle.setImageResource(R.drawable.arrow_down);
//                        } catch (NumberFormatException e) {
//                            Log.d(TAG, "Error executing onClick action");
//                        }
//                    } else {
//                        expandedHealthData.setVisibility(GONE);
//                        toggle.setImageResource(R.drawable.arrow);
//                    }
//                }
            }
        });

        ImageView alertIcon = convertView.findViewById(R.id.alertIcon);

        if (!currentDeviceData.isStale()) {
            textLastConnected.setVisibility(GONE);
            convertView.setBackgroundResource(R.color.greenbg);
            alertIcon.setVisibility(INVISIBLE);

            //set isFalsePositive UI change
            if (currentDeviceData.isFalsePositive) {
                textisFalsePositive.setVisibility(VISIBLE);
            } else {
                textisFalsePositive.setVisibility(View.GONE);
            }
            //Set alert UI changes
            if (currentDeviceData.highestAlertLevel == AlertLevel.LOW) {
                convertView.setBackgroundResource(R.color.yellowbg);
                alertIcon.setVisibility(VISIBLE);
                alertIcon.setImageResource(R.drawable.danger);
                Log.d(TAG, "low alert UI run on " + currentDeviceData.callsign);
            } else if (currentDeviceData.highestAlertLevel == AlertLevel.HIGH) {
                convertView.setBackgroundResource(R.color.redbg);
                alertIcon.setVisibility(VISIBLE);
                alertIcon.setImageResource(R.drawable.emergency);
                Log.d(TAG, "high alert UI run " + currentDeviceData.callsign);
            }
        //Set disconnect UI changes
        } else if (currentDeviceData.isStale()){
            convertView.setBackgroundResource(R.color.graybg);
            textLastConnected.setVisibility(VISIBLE);
            alertIcon.setVisibility(VISIBLE);
            alertIcon.setImageResource(R.drawable.notconnected);
            Log.d(TAG, "disconnect UI run " + currentDeviceData.callsign);
        }

        MetricViews.bind(convertView, currentDeviceData);
        return convertView;
    }
}
