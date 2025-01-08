/*
 * Copyright (C) 2024, Ascent Integrated Tech. All rights reserved.
 *
 * The copyright to the computer software herein is the property of Ascent Integrated Tech.
 * This software may not be used and/or copied without the explicit written permission
 * of Ascent Integrated Tech, and only in accordance with the terms and conditions stipulated
 * in the license agreement and/or contract under which the software has been supplied.
 */

package com.aitech.atak.biometrics.ui;

import static com.aitech.atak.biometrics.preferences.BiometricsPreferences.ENABLE_TEST_ALERTS;
import static com.aitech.atak.biometrics.preferences.BiometricsPreferences.PREFERENCES_KEYS;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.preference.PreferenceManager;
import android.view.View;
import android.util.Log;
import android.widget.ExpandableListView;
import android.widget.ListView;
import android.widget.TextView; // for displaying text in the dropdown
import android.widget.Button; //add button widget android
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.aitech.atak.biometrics.AlertLevel;
import com.aitech.atak.biometrics.TakBiometricsMapComponent;
import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.SettingsActivity;
import com.atakmap.comms.CommsLogger;
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.comms.CotStreamListener;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.comms.CotServiceRemote;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.aitech.atak.biometrics.plugin.R;
import com.aitech.atak.biometrics.DeviceData;
import com.aitech.atak.biometrics.services.LicenseManager;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TakBiometricsDropDown extends DropDownReceiver implements com.atakmap.android.dropdown.DropDown.OnStateListener, CommsLogger {

    public static final String SHOW_DROP_DOWN = "com.aitech.atak.biometrics.SHOW_DROP_DOWN";
    public static final String TAG = "TakBiometricsDropDown";
    private final View dropDownView;
    private CotStreamListener cotStreamListener;
    private ScheduledExecutorService scheduler;
    private String localDeviceSerialNumber;

    // hashmap to store heart rates with biometrics devices
    private final Map<String, Integer> heartRateData = new ConcurrentHashMap<>();
    private final Map<String, DeviceData> deviceDataMap = new ConcurrentHashMap<>();
    private DeviceData localDeviceData;

    private ArrayList<DeviceData> arrayList;
    private ListView listView;
    private String selfTitle;
    private CustomExpandableAdapter customExpandableAdapter;
    private ExpandableListView expandableListView;

    public TakBiometricsDropDown(final MapView mapView, final Context context) {
        super(mapView);
        //dropDownView = PluginLayoutInflater.inflate(context, R.layout.drop_down_view);
        dropDownView = PluginLayoutInflater.inflate(context, R.layout.drop_down_expandable_list_view);

        // Initialize the arrayList and customAdapter
        arrayList = new ArrayList<>();
        /*
        customAdapter = new CustomAdapter(context, arrayList);

        // Find the ListView and set the adapter
        listView = dropDownView.findViewById(R.id.listView);
        listView.setAdapter(customAdapter);
         */

        customExpandableAdapter = new CustomExpandableAdapter(context, arrayList);
        expandableListView = (ExpandableListView) dropDownView.findViewById(R.id.expandableListView);
        expandableListView.setAdapter(customExpandableAdapter);

        //expand all the Groups
        expandAll();

        // setOnChildClickListener listener for child row click
        expandableListView.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
                //get the group header
                DeviceData deviceData = arrayList.get(groupPosition);
                //get the child info
                DeviceData.AlertOrClearEvent alertOrClearEvent =  ((DeviceData.AlertOrClearEvent) deviceData.alertOrClearEventsEQ.toArray()[childPosition]);
                //display it or do something with it
                Log.d(TAG, "Clicked on position (" + childPosition + "): " + (alertOrClearEvent.isAlert?"ALERT Event":"CLEAR Event") + " with AlertLevel " + alertOrClearEvent.alertLevel + " and type " + alertOrClearEvent.type);
                return false;
            }
        });
        // setOnGroupClickListener listener for group heading click
        expandableListView.setOnGroupClickListener(new ExpandableListView.OnGroupClickListener() {

            @Override
            public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id) {
                //get the group header
                DeviceData deviceData = arrayList.get(groupPosition);
                //display it or do something with it
                Log.d(TAG, "Clicked on group postion (" + groupPosition + ") device =" + deviceData.callsign);
                return false;
            }

        });

        startScheduler();
    }

    //method to expand all groups
    private void expandAll() {
        int count = customExpandableAdapter.getGroupCount();
        for (int i = 0; i < count; i++){
            expandableListView.expandGroup(i);
        }
    }

    //method to collapse all groups
    private void collapseAll() {
        int count = customExpandableAdapter.getGroupCount();
        for (int i = 0; i < count; i++){
            expandableListView.collapseGroup(i);
        }
    }

    private void loadBiometricsPreferencesButton(Context context) {
        if (context == null) {
            Log.e(TAG, "Context is null, can't load the Biometrics Status Information");
            return;
        }

        // initialize the preferences button
        Button preferencesButton = dropDownView.findViewById(R.id.preferencesButton);
        if (preferencesButton != null) {
            // Listen for the button to be clicked
            preferencesButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // try launching the preferences page activity
                    try {
                        Log.d(TAG, "Preferences button clicked, launching Preferences");
                        SettingsActivity.start(PREFERENCES_KEYS, null);
                    } catch (NumberFormatException e) {
                        Log.d(TAG, "Error executing onClick action");
                    }
                }
            });
        } else {
            Log.e("ALARM_log", "Button not found!");
        }
    }

    //Where device data is populated to display - NOT devices directly connected to the viewable device
    public void displayCotDeviceData() {
        // Clear the arrayList to avoid duplicates
        arrayList.clear();

        // Iterate over the map and add local device first, followed by remote devices
        for (Map.Entry<String, DeviceData> entry : deviceDataMap.entrySet()) {
            DeviceData data = entry.getValue();
//            arrayList.sort(data.alertLevelComparator);

            if ((data.highestAlertLevel == AlertLevel.HIGH)) {
                // Add local device at the top
                arrayList.add(0, data);
            } else if (data.highestAlertLevel == AlertLevel.LOW) {
                arrayList.add(0, data);
            } else {
                // Add remote devices afterward
                arrayList.add(data);
            }

        }

//        Log.d(TAG, "Updating DropDown with device data");


        // Update the adapter to reflect the new data
        //customAdapter.notifyDataSetChanged();
        customExpandableAdapter.notifyDataSetChanged();
    }

    public void removeDeviceFromHash(String deviceId) {
        if (deviceId != null) {
            heartRateData.remove(deviceId);
            deviceDataMap.remove(deviceId);
            Log.d(TAG, "Removed hashmap heart rate data for device: " + deviceId);
        } else {
            Log.e(TAG, "Attempted to remove heart rate data for null deviceId");
        }
    }

    public void displayInfoOnUI(String info) {
        getMapView().post(() -> {
            TextView textView = dropDownView.findViewById(R.id.infoString);
            Log.d(TAG, "Displaying info string to the UI: " + info);
            textView.setText(info);
            if (info.toUpperCase(Locale.getDefault()).contains("INVALID")) {
                textView.setTextColor(Color.RED);
            } else {
                textView.setTextColor(Color.WHITE);
            }
        });
    }


    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        Log.d(TAG, "onReceive action: " + action);
        if (action == null) return;
        selfTitle = getMapView().getDeviceCallsign();

        if (action.equals(SHOW_DROP_DOWN)) {
            if (!isClosed()) {
                //hideDropDown();
                closeDropDown();
                return;
            }
            showDropDown(dropDownView, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH, HALF_HEIGHT, false, this);
            if (LicenseManager.isLicenseValid()) {
                if (cotStreamListener == null) {
                    cotStreamListener = new CotStreamListener(context, "BiometricsListener", new CotServiceRemote.CotEventListener() {
                        @Override
                        public void onCotEvent(CotEvent cotEvent, Bundle bundle) {
                            //                        Log.d(TAG, "onCotEvent called");
                            //cotReceiver(cotEvent);
                        }
                    });
                }
                // register the cot logger once the license is validated and the dropdown is open.
                registerCotLogger();
                displayInfoOnUI("License Key valid");
            } else {
                Log.d(TAG, "License Key is invalid");
                displayInfoOnUI("License Key invalid");
                stopScheduler();
                unregisterCotStreamListener(); // Ensure listener is unregistered
                unregisterCotLogger();         // Ensure the logger is unregistered
            }

            // create prefs button, listen for click. if clicked, launch biometrics preferences screen.
            loadBiometricsPreferencesButton(context);
            setAssociationKey(PREFERENCES_KEYS);
        }
    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void logSend(CotEvent cotEvent, String s) {

    }

    @Override
    public void logSend(CotEvent cotEvent, String[] strings) {

    }

    @Override
    public void logReceive(CotEvent cotEvent, String s, String s1) {
        String cotFilter = "biometrics";
        if (!LicenseManager.isLicenseValid()) {
            Log.d(TAG, "License is invalid, ignoring the CoT event.");
            return;
        }
            // If the message contains our key, pass it through our cot receiver, which will process the cot message further.
        if (cotEvent.toString().contains(cotFilter)) {
            //Log.d(TAG, cotEvent.toString());
            cotReceiver(cotEvent);
        }
    }

    // puts cot messages (biometric devices & their data) into hashmaps for the dropdown UI cards.
    private void cotReceiver(CotEvent cotEvent) {
        if (!LicenseManager.isLicenseValid()) {
            Log.d(TAG, "License is invalid, ignoring the CoT event.");
            return;
        }
        if (cotEvent.getDetail() != null) {
            // biometrics -- heart rate
            CotDetail biometrics = cotEvent.getDetail().getFirstChildByName(0, "biometrics");
            String heartRateStr = biometrics.getAttribute("hr");
            String modelStr = biometrics.getAttribute("model");
            String serialNumber = biometrics.getAttribute("serialNumber");

            if (modelStr==null) {
                // force the model to WEAROS so the logos populate in the dropdown.
                modelStr = "WEAROS";
            }

            Boolean isFalsePositive = Boolean.valueOf(biometrics.getAttribute("isFalsePositive"));
            String coreTempStr = biometrics.getAttribute("coreTemp");
            String exertionStr = biometrics.getAttribute("exertion");
            String hssiStr = biometrics.getAttribute("hssi");
            String cadenceStr = biometrics.getAttribute("cadence");
            String caloricBurnRateStr = biometrics.getAttribute("caloricBurnRate");
            String hrlvStr = biometrics.getAttribute("hrlv");
            String vo2Str = biometrics.getAttribute("vo2");
            String hrvStr = biometrics.getAttribute("hrv");

            // device id
            String deviceId = cotEvent.getUID(); // Using UID as deviceId
            // callsign
            CotDetail contact = cotEvent.getDetail().getFirstChildByName(0, "contact");
            String callsign = contact.getAttribute("callsign");
            String phone = contact.getAttribute("phone");

            // biometric device battery level (ie WearOS, Biostrap)
            CotDetail status = cotEvent.getDetail().getFirstChildByName(0, "status");
            String batteryLevelStr = status.getAttribute("battery");

            CoordinatedTime staleTime = cotEvent.getStale();
            if (heartRateStr != null && deviceId != null) {
                try {
                    boolean enableTestAlerts = PreferenceManager.getDefaultSharedPreferences(getMapView().getContext()).getBoolean(ENABLE_TEST_ALERTS, false);
                    int heartRate = Integer.parseInt(heartRateStr);
                    int batteryLevel = batteryLevelStr != null ? Integer.parseInt(batteryLevelStr) : -1;
                    if (enableTestAlerts) {
                        TakBiometricsMapComponent.testCounter = TakBiometricsMapComponent.testCounter + 1;
                    } else {
                        TakBiometricsMapComponent.testCounter = 0;
                    }
                    //Log.d(TAG, "selfTitle = (" + selfTitle + ")");
                    if ((null != selfTitle) && selfTitle.equals(callsign)) {
//                            Log.d(TAG, "Received heart rate data from local device; storing in deviceDataMap.");
                        callsign = callsign + " (You)";
                        Log.d(TAG, "Recieved Cot - LOCAL  - Model #: [ " + modelStr + " ]   Heart Rate: [ " + heartRateStr + " bpm ]   SerialNumber: [ " + serialNumber + " ] " + "    UID: [ " + deviceId +  " ]   Callsign: [ " + callsign + " ]" );
                        if (deviceDataMap.containsKey(deviceId)) {
                            // Update the data. No need to create a new DeviceData
                            DeviceData localDeviceData = deviceDataMap.get(deviceId);
                            if (null != localDeviceData) {
                                localDeviceData.updateBiometrics(callsign, true, serialNumber, staleTime, isFalsePositive,
                                        ((null == coreTempStr) || (coreTempStr.equals("")) || (coreTempStr.equals("None"))) ? null : Double.valueOf(coreTempStr),
                                        (double) heartRate,
                                        ((null == exertionStr) || (exertionStr.equals("")) || (exertionStr.equals("None"))) ? null : Double.valueOf(exertionStr),
                                        ((null == hssiStr) || (hssiStr.equals("")) || (hssiStr.equals("None"))) ? null : Double.valueOf(hssiStr),
                                        ((null == cadenceStr) || (cadenceStr.equals("")) || (cadenceStr.equals("None"))) ? null : Double.valueOf(cadenceStr),
                                        ((null == caloricBurnRateStr) || (caloricBurnRateStr.equals("")) || (caloricBurnRateStr.equals("None"))) ? null : Double.valueOf(caloricBurnRateStr),
                                        ((null == hrlvStr) || (hrlvStr.equals("")) || (hrlvStr.equals("None"))) ? null : Double.valueOf(hrlvStr),
                                        ((null == vo2Str) || (vo2Str.equals("")) || (vo2Str.equals("None"))) ? null : Double.valueOf(vo2Str),
                                        ((null == hrvStr) || (hrvStr.equals("")) || (hrvStr.equals("None"))) ? null : Double.valueOf(hrvStr),
                                        (double) batteryLevel);
                                if (enableTestAlerts) {
                                    if ((TakBiometricsMapComponent.testCounter % 5) == 0) {
                                        localDeviceData.addAlertOrClearEvent(new Date(), false, AlertLevel.NONE, "HEART_RATE", 150, 1, null);
                                    } else if ((TakBiometricsMapComponent.testCounter % 3) == 0) {
                                        localDeviceData.addAlertOrClearEvent(new Date(), true, AlertLevel.HIGH, "HEART_RATE", 150, 1, null);
                                    } else if ((TakBiometricsMapComponent.testCounter % 2) == 0) {
                                        localDeviceData.addAlertOrClearEvent(new Date(), true, AlertLevel.LOW, "HEART_RATE", 120, 1, null);
                                    }
                                }
                            }
                        } else {
                            // isLocal = true means that it's local.
                            DeviceData localDeviceData = new DeviceData(modelStr, callsign, true, serialNumber, staleTime, isFalsePositive,
                                    ((null == coreTempStr) || (coreTempStr.equals("")) || (coreTempStr.equals("None"))) ? null : Double.valueOf(coreTempStr),
                                    (double) heartRate,
                                    ((null == exertionStr) || (exertionStr.equals("")) || (exertionStr.equals("None"))) ? null : Double.valueOf(exertionStr),
                                    ((null == hssiStr) || (hssiStr.equals("")) || (hssiStr.equals("None"))) ? null : Double.valueOf(hssiStr),
                                    ((null == cadenceStr) || (cadenceStr.equals("")) || (cadenceStr.equals("None"))) ? null : Double.valueOf(cadenceStr),
                                    ((null == caloricBurnRateStr) || (caloricBurnRateStr.equals("")) || (caloricBurnRateStr.equals("None"))) ? null : Double.valueOf(caloricBurnRateStr),
                                    ((null == hrlvStr) || (hrlvStr.equals("")) || (hrlvStr.equals("None"))) ? null : Double.valueOf(hrlvStr),
                                    ((null == vo2Str) || (vo2Str.equals("")) || (vo2Str.equals("None"))) ? null : Double.valueOf(vo2Str),
                                    ((null == hrvStr) || (hrvStr.equals("")) || (hrvStr.equals("None"))) ? null : Double.valueOf(hrvStr),
                                    (double) batteryLevel);
                            deviceDataMap.put(deviceId, localDeviceData);// Store in the map
                            if (enableTestAlerts) {
                                if ((TakBiometricsMapComponent.testCounter % 5) == 0) {
                                    localDeviceData.addAlertOrClearEvent(new Date(), false, AlertLevel.NONE, "HEART_RATE", 150, 1, null);
                                } else if ((TakBiometricsMapComponent.testCounter % 3) == 0) {
                                    localDeviceData.addAlertOrClearEvent(new Date(), true, AlertLevel.HIGH, "HEART_RATE", 150, 1, null);
                                } else if ((TakBiometricsMapComponent.testCounter % 2) == 0) {
                                    localDeviceData.addAlertOrClearEvent(new Date(), true, AlertLevel.LOW, "HEART_RATE", 120, 1, null);
                                }
                            }
                        }
                        getMapView().post(this::displayCotDeviceData);
                    } else {
                        Log.d(TAG, "Recieved Cot - REMOTE - Model #: [ " + modelStr + " ]   Heart Rate: [ " + heartRateStr + " bpm ]   SerialNumber: [ " + serialNumber + " ] " + "    UID: [ " + deviceId +  " ]   Callsign: [ " + callsign + " ]" );

//                            Log.d(TAG, "Received heart rate data from remote device; storing in deviceDataMap.");
                        // isLocal = false means that it's remote.
                        if (deviceDataMap.containsKey(deviceId)) {
                            // Update the data. No need to create a new DeviceData
                            DeviceData remoteDeviceData = deviceDataMap.get(deviceId);
                            remoteDeviceData.updateBiometrics(callsign, true, serialNumber, staleTime, isFalsePositive,
                                    ((null == coreTempStr) || (coreTempStr.equals("")) || (coreTempStr.equals("None"))) ? null : Double.valueOf(coreTempStr),
                                    (double) heartRate,
                                    ((null == exertionStr) || (exertionStr.equals("")) || (exertionStr.equals("None"))) ? null : Double.valueOf(exertionStr),
                                    ((null == hssiStr) || (hssiStr.equals("")) || (hssiStr.equals("None"))) ? null : Double.valueOf(hssiStr),
                                    ((null == cadenceStr) || (cadenceStr.equals("")) || (cadenceStr.equals("None"))) ? null : Double.valueOf(cadenceStr),
                                    ((null == caloricBurnRateStr) || (caloricBurnRateStr.equals("")) || (caloricBurnRateStr.equals("None"))) ? null : Double.valueOf(caloricBurnRateStr),
                                    ((null == hrlvStr) || (hrlvStr.equals("")) || (hrlvStr.equals("None"))) ? null : Double.valueOf(hrlvStr),
                                    ((null == vo2Str) || (vo2Str.equals("")) || (vo2Str.equals("None"))) ? null : Double.valueOf(vo2Str),
                                    ((null == hrvStr) || (hrvStr.equals("")) || (hrvStr.equals("None"))) ? null : Double.valueOf(hrvStr),
                                    (double) batteryLevel);
                            if (enableTestAlerts) {
                                Log.d(TAG, "Adding testcounter: " + TakBiometricsMapComponent.testCounter);
                                if ((TakBiometricsMapComponent.testCounter % 5) == 0) {
                                    Log.d(TAG, deviceId + ": Adding clear event");
                                    remoteDeviceData.addAlertOrClearEvent(new Date(), false, AlertLevel.NONE, "HEART_RATE", 110, 1, null);
                                } else if ((TakBiometricsMapComponent.testCounter % 3) == 0) {
                                    Log.d(TAG, deviceId + ": Adding HIGH alert event");
                                    remoteDeviceData.addAlertOrClearEvent(new Date(), true, AlertLevel.HIGH, "CORE_TEMP", 150, 1, null);
                                } else if ((TakBiometricsMapComponent.testCounter % 2) == 0) {
                                    Log.d(TAG, deviceId + ": Adding LOW alert event");
                                    remoteDeviceData.addAlertOrClearEvent(new Date(), true, AlertLevel.LOW, "EXERTION", 120, 1, null);
                                }
                            }
                        } else {
                            DeviceData remoteDeviceData = new DeviceData(modelStr, callsign, false, serialNumber, staleTime, isFalsePositive,
                                    ((null == coreTempStr) || (coreTempStr.equals("")) || (coreTempStr.equals("None"))) ? null : Double.valueOf(coreTempStr),
                                    (double) heartRate,
                                    ((null == exertionStr) || (exertionStr.equals("")) || (exertionStr.equals("None"))) ? null : Double.valueOf(exertionStr),
                                    ((null == hssiStr) || (hssiStr.equals("")) || (hssiStr.equals("None"))) ? null : Double.valueOf(hssiStr),
                                    ((null == cadenceStr) || (cadenceStr.equals("")) || (cadenceStr.equals("None"))) ? null : Double.valueOf(cadenceStr),
                                    ((null == caloricBurnRateStr) || (caloricBurnRateStr.equals("")) || (caloricBurnRateStr.equals("None"))) ? null : Double.valueOf(caloricBurnRateStr),
                                    ((null == hrlvStr) || (hrlvStr.equals("")) || (hrlvStr.equals("None"))) ? null : Double.valueOf(hrlvStr),
                                    ((null == vo2Str) || (vo2Str.equals("")) || (vo2Str.equals("None"))) ? null : Double.valueOf(vo2Str),
                                    ((null == hrvStr) || (hrvStr.equals("")) || (hrvStr.equals("None"))) ? null : Double.valueOf(hrvStr),
                                    (double) batteryLevel);
                            arrayList.add(remoteDeviceData);

                            deviceDataMap.put(deviceId, remoteDeviceData); // Store in the map
                            if (enableTestAlerts) {
                                Log.d(TAG, "Adding testcounter: " + TakBiometricsMapComponent.testCounter);
                                if ((TakBiometricsMapComponent.testCounter % 5) == 0) {
                                    Log.d(TAG, deviceId + ": Adding clear event");
                                    remoteDeviceData.addAlertOrClearEvent(new Date(), false, AlertLevel.NONE, "HEART_RATE", 150, 1, null);
                                } else if ((TakBiometricsMapComponent.testCounter % 3) == 0) {
                                    Log.d(TAG, deviceId + ": Adding HIGH alert event");
                                    remoteDeviceData.addAlertOrClearEvent(new Date(), true, AlertLevel.HIGH, "CORE_TEMP", 150, 1, null);
                                } else if ((TakBiometricsMapComponent.testCounter % 2) == 0) {
                                    Log.d(TAG, deviceId + ": Adding LOW alert event");
                                    remoteDeviceData.addAlertOrClearEvent(new Date(), true, AlertLevel.LOW, "HEART_RATE", 120, 1, null);
                                }
                            }

                        }
                        getMapView().post(this::displayCotDeviceData);

                    }

                } catch (NumberFormatException e) {
                    Log.e(TAG, "Invalid heart rate value: " + heartRateStr, e);
                }
            }
        } else {
            Log.e(TAG, "Received CoT event with null detail or License is Invalid");
        }

    }

    public boolean isDeviceStale(DeviceData deviceData) {
        long currentTime = System.currentTimeMillis(); // Get the current system time
        if (deviceData != null && deviceData.staleTime != null) {
            long staleTime = deviceData.staleTime.getMilliseconds();
            //Log.d("Stale", "Current sys time ms: " + currentTime + " Stale ms: " + staleTime);
            return currentTime > staleTime; // Returns true if the device is stale
        }
        return false; // Device is not stale
    }

    public void removeLocalDevice() {
        for (Map.Entry<String, DeviceData> entry : deviceDataMap.entrySet()) {
            DeviceData deviceData = entry.getValue();
            if (deviceData.isLocal()) {
                Log.d(TAG, "Removing local device from map.");
                deviceDataMap.remove(entry.getKey()); // Remove the local device
                getMapView().post(this::displayCotDeviceData);
                break;
            }
        }
    }

    public void unregisterCotStreamListener() {
        if (cotStreamListener != null) {
            cotStreamListener.dispose();
            cotStreamListener = null;
            Log.d(TAG, "Unregistered cotStreamListener due to invalid license");
        }
    }

    public void removeStaleDevices() {
        //Log.d(TAG, "Checking for stale CoT messages.");
        for (Map.Entry<String, DeviceData> entry : deviceDataMap.entrySet()) {
            String deviceId = entry.getKey();
            DeviceData deviceData = entry.getValue();

            long currentTime2 = System.currentTimeMillis(); // Get the current system time
            if (isDeviceStale(deviceData)) {
                deviceData.setStale(true);
                long staleTime = deviceData.staleTime.getMilliseconds();
                Log.d("Stale", "Device " + deviceData.callsign + " is Stale |" + " Current ms: "+ currentTime2 + " Stale ms: " + staleTime);

                if (currentTime2 > staleTime + 30000) {
                    Log.d("Stale", "Device " + deviceId + " has been stale for too long (30 secs), removing it from the map.");
                    removeDeviceFromHash(deviceId); // Using the existing method to remove the device
                    getMapView().post(this::displayCotDeviceData);
                } else {
                    Log.d("Stale", "Device is stale, but not removable yet");
                }
//                removeDeviceFromHash(deviceId); // Using the existing method to remove the device
//
//                // Update the UI to remove the device from the dropdown
                getMapView().post(this::displayCotDeviceData);
            } else {
                deviceData.setStale(false);
            }
        }
    }

    public void removeDevices() {
        Log.d(TAG, "Removing Devices from Hash Map");
        for (Map.Entry<String, DeviceData> entry : deviceDataMap.entrySet()) {
            String deviceId = entry.getKey();
            DeviceData deviceData = entry.getValue();
            removeDeviceFromHash(deviceId); // Using the existing method to remove the device
            // Update the UI to remove the device from the dropdown
            getMapView().post(this::displayCotDeviceData);
        }
    }

        //scheduler is for check when to dispose of stale CoT messages
    private void startScheduler() {
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newScheduledThreadPool(1);
            scheduler.scheduleWithFixedDelay(this::removeStaleDevices, 0, 10, TimeUnit.SECONDS);
//            scheduler.scheduleWithFixedDelay(this::refreshDropDown, 0, 10, TimeUnit.SECONDS);
            Log.d(TAG, "Scheduler started.");
        }
    }

    public void stopScheduler() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            Log.d(TAG, "Scheduler stopped.");
        }
    }

    private void registerCotLogger() {
        CommsMapComponent.getInstance().registerCommsLogger(this);
        Log.d(TAG, "Registering Cot Logger");
    }

    private void unregisterCotLogger() {
        CommsMapComponent.getInstance().unregisterCommsLogger(this);
        Log.d(TAG, "Unregistering Cot Logger");
    }

    @Override
    public void onDropDownClose() {
        // Dispose of the CoT listener when dropdown is closed. TODO: Deprecate cot listener methods.
        if (cotStreamListener != null) {
            cotStreamListener.dispose();
            cotStreamListener = null;
        }
        // TODO: simplify and use concise methods for starting, stopping scheduler
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow(); // Shut down the scheduler
            Log.d(TAG, "Scheduler shut down.");
        }

        // stop listening to cot messages when the drop down closes
        unregisterCotLogger();
        // clear the hashmap filled with heart rate & biometric device data. The hashmap is used to fill data in the dropdown UI cards.
        heartRateData.clear();
    }

    @Override
    public void onDropDownSizeChanged(double v, double v1) {
    }

    @Override
    public void onDropDownVisible(boolean b) {
    }

    @Override
    public void disposeImpl() {
        stopScheduler();
    }

    public void refreshDropDown() {
        if (isClosed()) {
            // Dropdown is not open, no need to refresh
            return;
        }

        if (LicenseManager.isLicenseValid()) {
            // Re-register the cotStreamListener if necessary TODO: deprecate cotstream listener
            if (cotStreamListener == null) {
                cotStreamListener = new CotStreamListener(getMapView().getContext(), "BiometricsListener", new CotServiceRemote.CotEventListener() {
                    @Override
                    public void onCotEvent(CotEvent cotEvent, Bundle bundle) {
                        //cotReceiver(cotEvent);
                    }
                });
            }
            // Start scheduler if not running
            startScheduler();
            // register the cot logger that listens for cots with biometric data
            registerCotLogger();

        } else {
            // License invalid, unregister listener and stop scheduler TODO: deprecate cotstream listener
            unregisterCotStreamListener();
            // if the license is invalid, we want to unregister the cot logger & stop the scheduler that checks for stale data.
            unregisterCotLogger();
            stopScheduler();
            displayInfoOnUI("License Key invalid");
        }

        // Update UI elements as needed
        displayCotDeviceData();
    }


}
