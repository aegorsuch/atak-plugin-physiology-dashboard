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
import android.widget.Toast;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import android.widget.ImageButton;

import com.aitech.atak.biometrics.AlertLevel;
import com.aitech.atak.biometrics.TakBiometricsMapComponent;
import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.cot.detail.CotDetailHandler;
import com.atakmap.android.cot.detail.CotDetailManager;
import com.atakmap.android.cot.OpaqueHandler;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.SettingsActivity;
import com.atakmap.comms.CommsLogger;
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.aitech.atak.biometrics.plugin.R;
import com.aitech.atak.biometrics.DeviceData;
import com.aitech.atak.biometrics.services.LicenseManager;
import com.aitech.atak.biometrics.services.PhysioDataLogger;
import com.aitech.atak.biometrics.services.RetainedBiometrics;
import com.aitech.atak.biometrics.services.SelfBiometrics;
import com.aitech.atak.biometrics.services.BiometricValues;
import com.atakmap.android.missionpackage.api.MissionPackageApi;
import com.atakmap.android.missionpackage.api.ToastSaveCallback;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import android.view.MenuItem;
import android.widget.PopupMenu;

public class TakBiometricsDropDown extends DropDownReceiver implements com.atakmap.android.dropdown.DropDown.OnStateListener {

    public static final String SHOW_DROP_DOWN = "com.aitech.atak.biometrics.SHOW_DROP_DOWN";
    public static final String TAG = "TakBiometricsDropDown";
    private final View dropDownView;
    private ScheduledExecutorService scheduler;

    // hashmap to store heart rates with biometrics devices
    private final Map<String, Integer> heartRateData = new ConcurrentHashMap<>();
    private final Map<String, DeviceData> deviceDataMap = new ConcurrentHashMap<>();

    private ArrayList<DeviceData> arrayList;
    private String selfTitle;
    private CustomExpandableAdapter customExpandableAdapter;
    private ExpandableListView expandableListView;
    private CotDetailHandler biometricsDetailHandler = null;
    // Companion attaches the local watch's vitals to the outbound self marker,
    // which never reaches the inbound detail handler.
    private final SelfBiometrics selfBiometrics = new SelfBiometrics();
    private CommsLogger selfBiometricsLogger = null;
    private static final int SORT_COLOR_DEFAULT = -1;
    private final PhysioDataLogger physioDataLogger = new PhysioDataLogger();
    private Button loggingButton;
    private volatile boolean disposed;


    public TakBiometricsDropDown(final MapView mapView, final Context context) {
        super(mapView);
        //dropDownView = PluginLayoutInflater.inflate(context, R.layout.drop_down_view);
        dropDownView = PluginLayoutInflater.inflate(context, R.layout.drop_down_expandable_list_view);

        // Initialize the arrayList and customAdapter
        arrayList = new ArrayList<>();

        customExpandableAdapter = new CustomExpandableAdapter(context, arrayList);
        expandableListView = (ExpandableListView) dropDownView.findViewById(R.id.expandableListView);
        expandableListView.setAdapter(customExpandableAdapter);

        //expand all the Groups
        expandAll();
        loadSortButton();
        loadLoggingButton(context);
        physioDataLogger.setFailureListener(new PhysioDataLogger.FailureListener() {
            @Override
            public void onFailure(final java.io.IOException error) {
                getMapView().post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getMapView().getContext(),
                                "Physiology log write failed: " + error.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        });


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
    }
//    private void loadSortButton() {
//        Button sortButton = dropDownView.findViewById(R.id.sortButton);
//        if (sortButton == null) {
//            Log.e(TAG, "sortButton not found");
//            return;
//        }
//
//        sortButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                PopupMenu popupMenu = new PopupMenu(getMapView().getContext(), v);
//
//                MenuItem exertionItem = popupMenu.getMenu().add(0, SORT_EXERTION, 0, "Exertion");
//                exertionItem.setIcon(R.drawable.property_1_exertion);
//
//                MenuItem hrItem = popupMenu.getMenu().add(0, SORT_HEART_RATE, 1, "Heart Rate");
//                hrItem.setIcon(R.drawable.property_1_hr);
//
//                MenuItem batteryItem = popupMenu.getMenu().add(0, SORT_BATTERY_LEVEL, 2, "Battery Level");
//                batteryItem.setIcon(R.drawable.property_1_battery);
//
//                MenuItem azItem = popupMenu.getMenu().add(0, SORT_CALLSIGN_A_TO_Z, 3, "Alphabetical A-Z");
//                azItem.setIcon(R.drawable.property_1_a_z);
//
//                popupMenu.getMenu().add(0, SORT_COLOR_DEFAULT, 0, "Alert Color");
//
//                popupMenu.getMenu().add(0, SORT_CALLSIGN_Z_TO_A, 2, "Alphabetical Z-A");
//
//
//                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
//                    @Override
//                    public boolean onMenuItemClick(MenuItem item) {
//                        setSortMode(item.getItemId());
//                        sortButton.setText(item.getTitle());
//                        return true;
//                    }
//                });
//
//                popupMenu.show();
//            }
//        });
//    }


    private int currentSortIndex = 0;


    private ImageButton sortButton;

    private final int[] sortModes = {
            SORT_CALLSIGN_A_TO_Z,
            SORT_CALLSIGN_Z_TO_A,
            SORT_HEART_RATE_ASC,
            SORT_HEART_RATE_DESC,
            SORT_BATTERY_LEVEL_ASC,
            SORT_BATTERY_LEVEL_DESC,
            SORT_EXERTION_ASC,
            SORT_EXERTION_DESC,
            SORT_SKIN_TEMP_ASC,
            SORT_SKIN_TEMP_DESC
    };

    private final int[] sortIcons = {
            R.drawable.property_a_z_ascend,
            R.drawable.property_1_a_z,
            R.drawable.property_hr_ascend,
            R.drawable.property_1_hr,
            R.drawable.property_battery_ascend,
            R.drawable.property_1_battery,
            R.drawable.property_exertion_ascend,
            R.drawable.property_1_exertion,
            R.drawable.sort_skin_temp_asc,
            R.drawable.sort_skin_temp_desc
    };

    private final int[] sortLabels = {
            R.string.sort_callsign_asc, R.string.sort_callsign_desc,
            R.string.sort_hr_asc, R.string.sort_hr_desc,
            R.string.sort_battery_asc, R.string.sort_battery_desc,
            R.string.sort_exertion_asc, R.string.sort_exertion_desc,
            R.string.sort_skin_temp_asc, R.string.sort_skin_temp_desc
    };


    private void loadSortButton() {
        sortButton = dropDownView.findViewById(R.id.sortButton);

        if (sortButton == null) {
            Log.e(TAG, "sortButton not found");
            return;
        }

        sortButton.setImageResource(sortIcons[currentSortIndex]);
        sortButton.setContentDescription(dropDownView.getContext().getString(sortLabels[currentSortIndex]));
        setSortMode(sortModes[currentSortIndex]);

        sortButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentSortIndex = (currentSortIndex + 1) % sortModes.length;
                sortButton.setImageResource(sortIcons[currentSortIndex]);
                sortButton.setContentDescription(dropDownView.getContext().getString(sortLabels[currentSortIndex]));
                setSortMode(sortModes[currentSortIndex]);
            }
        });
        sortButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                PopupMenu menu = new PopupMenu(dropDownView.getContext(), v);
                for (int i = 0; i < sortModes.length; i++) {
                    menu.getMenu().add(0, i, i, sortLabels[i]);
                }
                menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        currentSortIndex = item.getItemId();
                        sortButton.setImageResource(sortIcons[currentSortIndex]);
                        sortButton.setContentDescription(dropDownView.getContext().getString(sortLabels[currentSortIndex]));
                        setSortMode(sortModes[currentSortIndex]);
                        return true;
                    }
                });
                menu.show();
                return true;
            }
        });
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

    private void loadLoggingButton(final Context context) {
        loggingButton = dropDownView.findViewById(R.id.loggingButton);
        if (loggingButton == null) {
            Log.e(TAG, "loggingButton not found");
            return;
        }
        updateLoggingButton();
        loggingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (physioDataLogger.isLogging()) {
                    loggingButton.setEnabled(false);
                    loggingButton.setText(R.string.finalizing_log);
                    physioDataLogger.stop(new PhysioDataLogger.CompletionListener() {
                        @Override
                        public void onComplete(final java.io.File file, final java.io.IOException error) {
                            getMapView().post(new Runnable() {
                                @Override
                                public void run() {
                                    loggingButton.setEnabled(true);
                                    updateLoggingButton();
                                    if (error != null) {
                                        Toast.makeText(getMapView().getContext(),
                                                "Unable to finish physiological log: " + error.getMessage(),
                                                Toast.LENGTH_LONG).show();
                                        return;
                                    }
                                    sendLogAsMissionPackage(file);
                                }
                            });
                        }
                    });
                } else {
                    try {
                        java.io.File file = physioDataLogger.start(context);
                        updateLoggingButton();
                        Toast.makeText(getMapView().getContext(),
                                "Physiology logging started", Toast.LENGTH_SHORT).show();
                        Log.i(TAG, "Logging physiological data to " + file);
                    } catch (java.io.IOException e) {
                        Toast.makeText(getMapView().getContext(),
                                "Unable to start physiological log: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                        Log.e(TAG, "Unable to start physiological log", e);
                    }
                }
            }
        });
    }

    private void updateLoggingButton() {
        if (loggingButton != null) {
            loggingButton.setText(physioDataLogger.isLogging()
                    ? R.string.stop_and_send_log : R.string.start_logging);
        }
    }

    private void sendLogAsMissionPackage(java.io.File file) {
        if (file == null || !file.isFile()) {
            Toast.makeText(getMapView().getContext(),
                    "Physiology log file is unavailable", Toast.LENGTH_LONG).show();
            return;
        }

        MissionPackageManifest manifest = MissionPackageApi.CreateTempManifest(
                "Physiology Log " + file.getName());
        if (manifest == null || !manifest.addFile(file, "text/csv", null)) {
            Toast.makeText(getMapView().getContext(),
                    "Unable to add physiological log to ATAK data package",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (!MissionPackageApi.Send(getMapView().getContext(), manifest,
                ToastSaveCallback.class, null)) {
            Toast.makeText(getMapView().getContext(),
                    "Unable to start ATAK data package send", Toast.LENGTH_LONG).show();
        }
    }


    // * 6/24 current status
    private static final int SORT_CALLSIGN_A_TO_Z = 0;
    private static final int SORT_CALLSIGN_Z_TO_A = 1;
    private static final int SORT_HEART_RATE_ASC = 2;
    private static final int SORT_HEART_RATE_DESC = 3;
    private static final int SORT_BATTERY_LEVEL_ASC = 4;
    private static final int SORT_BATTERY_LEVEL_DESC = 5;
    private static final int SORT_EXERTION_ASC = 6;
    private static final int SORT_EXERTION_DESC = 7;
    private static final int SORT_SKIN_TEMP_ASC = 8;
    private static final int SORT_SKIN_TEMP_DESC = 9;

    private int currentSortMode = SORT_COLOR_DEFAULT;


    public void setSortMode(int mode) {
        this.currentSortMode = mode;
        getMapView().post(new Runnable() {
            @Override
            public void run() {
                displayCotDeviceData();
            }
        });
    }

    public int getSortMode() {
        return currentSortMode;
    }

    private final Comparator<DeviceData> deviceSortComparator = new Comparator<DeviceData>() {
        @Override
        public int compare(DeviceData a, DeviceData b) {
            switch (currentSortMode) {
                case SORT_SKIN_TEMP_ASC:
                    return compareDoubleAsc(a.coreTemp, b.coreTemp);
                case SORT_SKIN_TEMP_DESC:
                    return compareDoubleDesc(a.coreTemp, b.coreTemp);
                case SORT_HEART_RATE_ASC:
                    return compareDoubleAsc(a.heartRate, b.heartRate);

                case SORT_HEART_RATE_DESC:
                    return compareDoubleDesc(a.heartRate, b.heartRate);

                case SORT_BATTERY_LEVEL_ASC:
                    return compareDoubleAsc(a.batteryLevel, b.batteryLevel);

                case SORT_BATTERY_LEVEL_DESC:
                    return compareDoubleDesc(a.batteryLevel, b.batteryLevel);

                case SORT_EXERTION_ASC:
                    return compareDoubleAsc(a.exertion, b.exertion);

                case SORT_EXERTION_DESC:
                    return compareDoubleDesc(a.exertion, b.exertion);

                case SORT_CALLSIGN_Z_TO_A:
                    return compareStringsDesc(a.callsign, b.callsign);

                case SORT_CALLSIGN_A_TO_Z:
                default:
                    return compareStringsAsc(a.callsign, b.callsign);
            }
        }

    };

    private static int compareStringsAsc(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return String.CASE_INSENSITIVE_ORDER.compare(a, b);
    }

    private static int compareStringsDesc(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return String.CASE_INSENSITIVE_ORDER.compare(b, a);
    }

    private static int compareDoubleDesc(Double a, Double b) {
        return MetricValues.compare(a, b, true);
    }

    private static int compareDoubleAsc(Double a, Double b) {
        return MetricValues.compare(a, b, false);
    }

    private static int getAlertPriority(AlertLevel level) {
        if (level == AlertLevel.HIGH) return 0;
        if (level == AlertLevel.LOW) return 1;
        return 2;
    }

    //Where device data is populated to display - NOT devices directly connected to the viewable device
    public void displayCotDeviceData() {
        arrayList.clear();

        for (Map.Entry<String, DeviceData> entry : deviceDataMap.entrySet()) {
            DeviceData data = entry.getValue();
            if (MetricValues.hasPhysiologicalData(data)
                    || data.numberOfActiveAlerts > 0
                    || !data.alertOrClearEventsEQ.isEmpty()) {
                arrayList.add(data);
            }
        }

        if (currentSortMode == SORT_COLOR_DEFAULT) {
            arrayList.sort(new Comparator<DeviceData>() {
                @Override
                public int compare(DeviceData a, DeviceData b) {
                    return Integer.compare(
                            getAlertPriority(a.highestAlertLevel),
                            getAlertPriority(b.highestAlertLevel)
                    );
                }
            });
        } else {
            arrayList.sort(deviceSortComparator);
        }

        customExpandableAdapter.notifyDataSetChanged();
        expandAll();
    }


//    public void displayCotDeviceData() {
//        arrayList.clear();
//
//        ArrayList<DeviceData> redAlerts = new ArrayList<>();
//        ArrayList<DeviceData> yellowAlerts = new ArrayList<>();
//        ArrayList<DeviceData> normalDevices = new ArrayList<>();
//
//        for (Map.Entry<String, DeviceData> entry : deviceDataMap.entrySet()) {
//            DeviceData data = entry.getValue();
//            if (data.highestAlertLevel == AlertLevel.HIGH) {
//                redAlerts.add(data);
//            } else if (data.highestAlertLevel == AlertLevel.LOW) {
//                yellowAlerts.add(data);
//            } else {
//                normalDevices.add(data);
//            }
//        }
//
//
//        redAlerts.sort(deviceSortComparator);
//        yellowAlerts.sort(deviceSortComparator);
//        normalDevices.sort(deviceSortComparator);
//
//        arrayList.addAll(redAlerts);
//        arrayList.addAll(yellowAlerts);
//        arrayList.addAll(normalDevices);
//
//        customExpandableAdapter.notifyDataSetChanged();
//        Log.d(TAG, "displayCotDeviceData called");
//        Log.d(TAG, "deviceDataMap size = " + deviceDataMap.size());
//    }

    public void removeDeviceFromHash(String deviceId) {
        if (deviceId != null) {
            heartRateData.remove(deviceId);
            deviceDataMap.remove(deviceId);
            Log.d(TAG, "Removed hashmap heart rate data for device: " + deviceId);
        } else {
            Log.e(TAG, "Attempted to remove heart rate data for null deviceId");
        }
    }

//    public void displayInfoOnUI(String info) {
//        getMapView().post(() -> {
//            TextView textView = dropDownView.findViewById(R.id.infoString);
//            Log.d(TAG, "Displaying info string to the UI: " + info);
//            textView.setText(info);
//            if (info.toUpperCase(Locale.getDefault()).contains("INVALID")) {
//                textView.setTextColor(Color.RED);
//            } else {
//                textView.setTextColor(Color.WHITE);
//            }
//        });
//    }

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
            displayCotDeviceData();
//            if (LicenseManager.isLicenseValid()) {
                registerCotHandler();  // start listening to cot messages
//                displayInfoOnUI("License Key valid");  // tell the user License is VALID
                startScheduler();  // start checking for stale devices
//            } else {
//                Log.d(TAG, "License Key is invalid");
//                unregisterCotHandler();  // stop listening to cot messages
//                displayInfoOnUI("License Key invalid");  // tell the user License is INVALID
//                stopScheduler();  // start checking for stale devices
//            }
            loadBiometricsPreferencesButton(context);  // if clicked, launch biometrics plugin preferences screen.
            setAssociationKey(PREFERENCES_KEYS);
        }
    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    // puts cot messages (biometric devices & their data) into hashmaps for the dropdown UI cards.
    private void cotReceiver(CotEvent cotEvent, boolean retained) {
        Log.d("WEARTAK_ENTER", "cotReceiver entered");
        Log.d("WEARTAK_COT", cotEvent.toString());

        if (cotEvent.getDetail() != null) {
            Log.d("WEARTAK_DETAIL", cotEvent.getDetail().toString());
        }

//        if (!LicenseManager.isLicenseValid()) {
//            Log.d(TAG, "License is invalid, ignoring the CoT event.");
//            return;
//        }
        CotDetail detail = cotEvent.getDetail();

        if (detail != null) {
            CotDetail biometrics = detail.getFirstChildByName(0,"biometrics");
            // Ensure the biometrics child exists
            if (biometrics == null) {
                Log.d("WEARTAK_COT", "No biometrics detail found");
                Log.d("WEARTAK_COT", cotEvent.toString());
                return;
            }
            CotDetail biometricDevice = biometrics.getFirstChildByName(0,"device");
            // Ensure the device child exists
            if (biometricDevice == null) {
                Log.d("WEARTAK_COT", "No device child found");
                Log.d("WEARTAK_COT", biometrics.toString());
                return; // Exit early if no device section is present
            }

            // Now we know that the cot message contains biometrics, we can extract the data from it.
            CotDetail contact = cotEvent.getDetail().getFirstChildByName(0, "contact");
            if (contact == null) {
                Log.w(TAG, "Ignoring biometrics without contact: " + cotEvent.getUID());
                return;
            }
            CotDetail status = cotEvent.getDetail().getFirstChildByName(0, "status");
            String callsign = contact.getAttribute("callsign");
            String phone = contact.getAttribute("phone");

            // biometrics -- heart rate
            String biometricDeviceUid = childTextOrAttribute(biometricDevice, "uid");
            String selfUid = MapView.getDeviceUid();
            String deviceId = cardKey(cotEvent, biometricDeviceUid);
            boolean isSelf = selfUid != null && selfUid.equals(deviceId);
            if (isSelf && !isBlank(selfTitle)) {
                callsign = selfTitle;
            }
            String heartRateStr = childTextOrAttribute(biometricDevice, "hr");
            String modelStr = childTextOrAttribute(biometricDevice, "model");
            if (isBlank(biometricDeviceUid) || isBlank(heartRateStr) || isBlank(modelStr)) {
                Log.w(TAG, "Ignoring biometrics detail with incomplete device data: " + biometricDevice);
                return;
            }
            String serialNumber = biometricDeviceUid;       // the serial number is legacy for holding biostrap serial #s. keeping the serialNumber for readibility.
            String batteryLevelStr = null;
            // Newer format: <status battery="93" />
            if (status != null) {
                batteryLevelStr = status.getAttribute("battery");
            }

            // Legacy format: <biometrics><device><bat>93</bat></device></biometrics>
            if (batteryLevelStr == null || batteryLevelStr.isEmpty()) {
                CotDetail batteryDetail = biometricDevice.getChild("bat");
                if (batteryDetail != null) {
                    batteryLevelStr = batteryDetail.getInnerText();
                }
            }
            String skinTempStr = biometricDevice.getChild("skt") != null
                    ? biometricDevice.getChild("skt").getInnerText()
                    : null;

            String exertionStr = biometricDevice.getChild("exert") != null
                    ? biometricDevice.getChild("exert").getInnerText()
                    : null;
            Double exertionPercent = BiometricValues.parseExertionPercent(exertionStr);
            Double skinTempCelsius = BiometricValues.parseSkinTemperatureCelsius(
                    skinTempStr, modelStr);
            Log.d("WEARTAK_PARSE",
                    "UID=" + biometricDeviceUid +
                            ", HR=" + heartRateStr +
                            ", BAT=" + batteryLevelStr +
                            ", MODEL=" + modelStr);
            Log.d("CotReceiver", "Callsign: " + callsign +"Model: " + modelStr + ", UID: " + biometricDeviceUid + ", HR: " + heartRateStr + ", Battery: " + batteryLevelStr);

           // String spo2Str = biometricDevice.getChild("spo2") != null
            //        ? biometricDevice.getChild("spo2").getInnerText()
              //      : null;
            String spo2Str = null;
            Log.d("WEARTAK_PARSE",
                    "UID=" + biometricDeviceUid +
                            ", HR=" + heartRateStr +
                            ", SKT=" + skinTempStr +
                            ", EXERT=" + exertionStr +
                            ", SPO2=" + spo2Str +
                            ", BAT=" + batteryLevelStr +
                            ", MODEL=" + modelStr);
            Boolean isFalsePositive = Boolean.valueOf(biometrics.getAttribute("isFalsePositive"));
            String hssiStr = biometrics.getAttribute("hssi");
            String cadenceStr = biometrics.getAttribute("cadence");
            String caloricBurnRateStr = biometrics.getAttribute("caloricBurnRate");
            String hrlvStr = biometrics.getAttribute("hrlv");
            String vo2Str = biometrics.getAttribute("vo2");
            String hrvStr = biometrics.getAttribute("hrv");

            CoordinatedTime staleTime = cotEvent.getStale();

            if (heartRateStr != null && deviceId != null) {
                try {
                    boolean enableTestAlerts = !retained && PreferenceManager.getDefaultSharedPreferences(getMapView().getContext()).getBoolean(ENABLE_TEST_ALERTS, false);
                    Log.d("HR_DEBUG", "heartRateStr=" + heartRateStr);
                    int heartRate = -1;
                    if (heartRateStr != null &&
                            !heartRateStr.equals("N/A") &&
                            !heartRateStr.isEmpty()) {
                        heartRate = Integer.parseInt(heartRateStr);
                    }
                    Log.d("HR_DEBUG", "parsed heartRate=" + heartRate);

                    int batteryLevel = -1;
                    if (batteryLevelStr != null &&
                            !batteryLevelStr.isEmpty() &&
                            !batteryLevelStr.equalsIgnoreCase("N/A")) {
                        try {
                            batteryLevel = Integer.parseInt(batteryLevelStr);
                        } catch (NumberFormatException e) {
                            Log.w(TAG, "Invalid battery value: " + batteryLevelStr);
                        }
                    }
                    if (!retained) {
                        physioDataLogger.recordSample(new Date(), callsign, heartRateStr,
                                skinTempStr, exertionPercent == null
                                        ? null : String.valueOf(exertionPercent));
                    }
                    if (enableTestAlerts) {
                        TakBiometricsMapComponent.testCounter = TakBiometricsMapComponent.testCounter + 1;
                    } else {
                        TakBiometricsMapComponent.testCounter = 0;
                    }
                    //Log.d(TAG, "selfTitle = (" + selfTitle + ")");
                    if (isSelf || ((null != selfTitle) && selfTitle.equals(callsign))) {
                        callsign = callsign + " (Self)";
                        //Log.d(TAG, "Recieved Cot - LOCAL  - Model #: [ " + modelStr + " ]   Heart Rate: [ " + heartRateStr + " bpm ]   SerialNumber: [ " + serialNumber + " ] " + "    UID: [ " + deviceId +  " ]   Callsign: [ " + callsign + " ]" );
                        if (deviceDataMap.containsKey(deviceId)) {
                            // Update the data. No need to create a new DeviceData
                            DeviceData localDeviceData = deviceDataMap.get(deviceId);
                            if (null != localDeviceData) {
                                localDeviceData.updateBiometrics(callsign, true, serialNumber, staleTime, isFalsePositive,
                                        skinTempCelsius,
                                        (double) heartRate,
                                        ((null == spo2Str) || (spo2Str.equals("")) || (spo2Str.equals("None")) || (spo2Str.equals("N/A"))) ? null : Double.valueOf(spo2Str),
                                        exertionPercent,
                                        ((null == hssiStr) || (hssiStr.equals("")) || (hssiStr.equals("None")) || (hssiStr.equals("N/A"))) ? null : Double.valueOf(hssiStr),
                                        ((null == cadenceStr) || (cadenceStr.equals("")) || (cadenceStr.equals("None")) || (cadenceStr.equals("N/A"))) ? null : Double.valueOf(cadenceStr),
                                        ((null == caloricBurnRateStr) || (caloricBurnRateStr.equals("")) || (caloricBurnRateStr.equals("None")) || (caloricBurnRateStr.equals("N/A"))) ? null : Double.valueOf(caloricBurnRateStr),
                                        ((null == hrlvStr) || (hrlvStr.equals("")) || (hrlvStr.equals("None")) || (hrlvStr.equals("N/A"))) ? null : Double.valueOf(hrlvStr),
                                        ((null == vo2Str) || (vo2Str.equals("")) || (vo2Str.equals("None")) || (vo2Str.equals("N/A"))) ? null : Double.valueOf(vo2Str),
                                        ((null == hrvStr) || (hrvStr.equals("")) || (hrvStr.equals("None")) || (hrvStr.equals("N/A"))) ? null : Double.valueOf(hrvStr),
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
                                    skinTempCelsius,
                                    (double) heartRate,
                                    ((null == spo2Str) || (spo2Str.equals("")) || (spo2Str.equals("None")) || (spo2Str.equals("N/A"))) ? null : Double.valueOf(spo2Str),
                                    exertionPercent,
                                    ((null == hssiStr) || (hssiStr.equals("")) || (hssiStr.equals("None")) || (hssiStr.equals("N/A"))) ? null : Double.valueOf(hssiStr),
                                    ((null == cadenceStr) || (cadenceStr.equals("")) || (cadenceStr.equals("None")) || (cadenceStr.equals("N/A"))) ? null : Double.valueOf(cadenceStr),
                                    ((null == caloricBurnRateStr) || (caloricBurnRateStr.equals("")) || (caloricBurnRateStr.equals("None")) || (caloricBurnRateStr.equals("N/A"))) ? null : Double.valueOf(caloricBurnRateStr),
                                    ((null == hrlvStr) || (hrlvStr.equals("")) || (hrlvStr.equals("None")) || (hrlvStr.equals("N/A"))) ? null : Double.valueOf(hrlvStr),
                                    ((null == vo2Str) || (vo2Str.equals("")) || (vo2Str.equals("None")) || (vo2Str.equals("N/A"))) ? null : Double.valueOf(vo2Str),
                                    ((null == hrvStr) || (hrvStr.equals("")) || (hrvStr.equals("None")) || (hrvStr.equals("N/A"))) ? null : Double.valueOf(hrvStr),
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
                        getMapView().post(new Runnable() {
                            @Override
                            public void run() {
                                displayCotDeviceData();
                            }
                        });
                    } else {
                        //Log.d(TAG, "Recieved Cot - REMOTE - Model #: [ " + modelStr + " ]   Heart Rate: [ " + heartRateStr + " bpm ]   SerialNumber: [ " + serialNumber + " ] " + "    UID: [ " + deviceId +  " ]   Callsign: [ " + callsign + " ]" );
                        if (deviceDataMap.containsKey(deviceId)) {
                            // Update the data. No need to create a new DeviceData
                            DeviceData remoteDeviceData = deviceDataMap.get(deviceId);
                            remoteDeviceData.updateBiometrics(callsign, false, serialNumber, staleTime, isFalsePositive,
                                    skinTempCelsius,
                                    (double) heartRate,
                                    ((null == spo2Str) || (spo2Str.equals("")) || (spo2Str.equals("None")) || (spo2Str.equals("N/A"))) ? null : Double.valueOf(spo2Str),
                                    exertionPercent,
                                    ((null == hssiStr) || (hssiStr.equals("")) || (hssiStr.equals("None")) || (hssiStr.equals("N/A"))) ? null : Double.valueOf(hssiStr),
                                    ((null == cadenceStr) || (cadenceStr.equals("")) || (cadenceStr.equals("None")) || (cadenceStr.equals("N/A"))) ? null : Double.valueOf(cadenceStr),
                                    ((null == caloricBurnRateStr) || (caloricBurnRateStr.equals("")) || (caloricBurnRateStr.equals("None")) || (caloricBurnRateStr.equals("N/A"))) ? null : Double.valueOf(caloricBurnRateStr),
                                    ((null == hrlvStr) || (hrlvStr.equals("")) || (hrlvStr.equals("None")) || (hrlvStr.equals("N/A"))) ? null : Double.valueOf(hrlvStr),
                                    ((null == vo2Str) || (vo2Str.equals("")) || (vo2Str.equals("None")) || (vo2Str.equals("N/A"))) ? null : Double.valueOf(vo2Str),
                                    ((null == hrvStr) || (hrvStr.equals("")) || (hrvStr.equals("None")) || (hrvStr.equals("N/A"))) ? null : Double.valueOf(hrvStr),
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
                                    skinTempCelsius,
                                    (double) heartRate,
                                    ((null == spo2Str) || (spo2Str.equals("")) || (spo2Str.equals("None")) || (spo2Str.equals("N/A"))) ? null : Double.valueOf(spo2Str),
                                    exertionPercent,
                                    ((null == hssiStr) || (hssiStr.equals("")) || (hssiStr.equals("None")) || (hssiStr.equals("N/A"))) ? null : Double.valueOf(hssiStr),
                                    ((null == cadenceStr) || (cadenceStr.equals("")) || (cadenceStr.equals("None")) || (cadenceStr.equals("N/A"))) ? null : Double.valueOf(cadenceStr),
                                    ((null == caloricBurnRateStr) || (caloricBurnRateStr.equals("")) || (caloricBurnRateStr.equals("None")) || (caloricBurnRateStr.equals("N/A"))) ? null : Double.valueOf(caloricBurnRateStr),
                                    ((null == hrlvStr) || (hrlvStr.equals("")) || (hrlvStr.equals("None")) || (hrlvStr.equals("N/A"))) ? null : Double.valueOf(hrlvStr),
                                    ((null == vo2Str) || (vo2Str.equals("")) || (vo2Str.equals("None")) || (vo2Str.equals("N/A"))) ? null : Double.valueOf(vo2Str),
                                    ((null == hrvStr) || (hrvStr.equals("")) || (hrvStr.equals("None")) || (hrvStr.equals("N/A"))) ? null : Double.valueOf(hrvStr),
                                    (double) batteryLevel);
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
                        getMapView().post(new Runnable() {
                            @Override
                            public void run() {
                                displayCotDeviceData();
                            }
                        });

                    }

                }catch (Exception e) {
                    Log.e("WEARTAK_ERROR",
                            "cotReceiver failed. HR=" + heartRateStr +
                                    ", BAT=" + batteryLevelStr +
                                    ", SKT=" + skinTempStr +
                                    ", EXERT=" + exertionStr +
                                    ", SPO2=" + spo2Str,
                            e);
                }
            }
        } else {
            Log.d(TAG, "Received CoT event that contains the filter word, but not as a detail.");
            return;
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

    public void removeStaleDevices() {
        //Log.d(TAG, "Checking for stale CoT messages.");
        for (Map.Entry<String, DeviceData> entry : deviceDataMap.entrySet()) {
            String deviceId = entry.getKey();
            DeviceData deviceData = entry.getValue();
            long currentTime2 = System.currentTimeMillis(); // grab the current time to see if the cot message is stale

            if (isDeviceStale(deviceData)) {
                deviceData.setStale(true);
                long staleTime = deviceData.staleTime.getMilliseconds();
                Log.d("Stale", "Device " + deviceData.callsign + " is Stale |" + " Current ms: "+ currentTime2 + " Stale ms: " + staleTime);

                if (currentTime2 > staleTime + 30000) {
                    Log.d("Stale", "Device " + deviceId + " has been stale for too long (30 secs), removing it from the map.");
                    removeDeviceFromHash(deviceId); // Using the existing method to remove the device
                    getMapView().post(new Runnable() {
                        @Override
                        public void run() {
                            displayCotDeviceData();
                        }
                    });
                } else {
                    Log.d("Stale", "Device is stale, but not removable yet");
                }
                getMapView().post(new Runnable() {
                    @Override
                    public void run() {
                        displayCotDeviceData();
                    }
                });  // Update the UI to remove the device from the dropdown
            } else {
                deviceData.setStale(false);
            }
        }
    }

    public void removeDevices() {
        Log.d(TAG, "Removing Devices from Hash Map");
        for (Map.Entry<String, DeviceData> entry : deviceDataMap.entrySet()) {
            String deviceId = entry.getKey();
            removeDeviceFromHash(deviceId);                 // Using the existing method to remove the device
            getMapView().post(new Runnable() {
                @Override
                public void run() {
                    displayCotDeviceData();
                }
            });  // Update the UI to remove the device from the dropdown
        }
    }

    //scheduler is used to check when to dispose of stale CoT messages, so the UI can inform user device is no longer sending data
    private void startScheduler() {
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newScheduledThreadPool(1);
            scheduler.scheduleWithFixedDelay(
                    new Runnable() {
                        @Override
                        public void run() {
                            removeStaleDevices();
                        }
                    },
                    0,
                    10,
                    TimeUnit.SECONDS
            );
            Log.d(TAG, "Scheduler started.");
        }
    }

    public void stopScheduler() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            Log.d(TAG, "Scheduler stopped.");
        }
    }

    public void startCollecting() {
        selfTitle = getMapView().getDeviceCallsign();
        registerCotHandler();
        getMapView().post(new Runnable() {
            @Override
            public void run() {
                if (disposed) {
                    return;
                }
                restoreRetainedBiometrics();
                startScheduler();
            }
        });
    }

    private synchronized void processBiometrics(CotEvent event, boolean retained) {
        String key = cardKey(event, biometricDeviceUid(event));
        if (disposed || (retained && deviceDataMap.containsKey(key))) {
            return;
        }
        cotReceiver(event, retained);
        DeviceData data = deviceDataMap.get(key);
        if (data != null) {
            data.setStale(isDeviceStale(data));
        }
    }

    private String cardKey(CotEvent event, String biometricDeviceUid) {
        return SelfBiometrics.cardKey(event.getUID(), biometricDeviceUid,
                MapView.getDeviceUid(), selfBiometrics.getWatchUid());
    }

    private String biometricDeviceUid(CotEvent event) {
        CotDetail detail = event.getDetail();
        CotDetail biometrics = detail == null ? null : detail.getFirstChildByName(0, "biometrics");
        return childTextOrAttribute(biometrics == null ? null
                : biometrics.getFirstChildByName(0, "device"), "uid");
    }

    private void onSelfEventSent(CotEvent event) {
        if (disposed) {
            return;
        }
        try {
            String previousWatchUid = selfBiometrics.getWatchUid();
            if (!selfBiometrics.accept(event, MapView.getDeviceUid())) {
                return;
            }
            String watchUid = selfBiometrics.getWatchUid();
            if (watchUid != null && !watchUid.equals(previousWatchUid)
                    && deviceDataMap.remove(watchUid) != null) {
                // A separate card created by older Companion builds is now merged into self.
                Log.i(TAG, "Merged legacy watch card " + watchUid + " into self card");
            }
            processBiometrics(event, false);
        } catch (Exception e) {
            Log.e(TAG, "Failed to process self biometrics", e);
        }
    }

    private void registerSelfBiometricsLogger() {
        if (selfBiometricsLogger != null) {
            return;
        }
        selfBiometricsLogger = new CommsLogger() {
            @Override
            public void logSend(CotEvent event, String destination) {
                onSelfEventSent(event);
            }

            @Override
            public void logSend(CotEvent event, String[] toUIDs) {
                onSelfEventSent(event);
            }

            @Override
            public void logReceive(CotEvent event, String rxid, String server) {
            }

            @Override
            public void dispose() {
            }
        };
        CommsMapComponent comms = CommsMapComponent.getInstance();
        if (comms == null) {
            Log.w(TAG, "CommsMapComponent unavailable; self biometrics logger not registered");
            selfBiometricsLogger = null;
            return;
        }
        comms.registerCommsLogger(selfBiometricsLogger);
        Log.d(TAG, "Registered self biometrics comms logger");
    }

    private void unregisterSelfBiometricsLogger() {
        if (selfBiometricsLogger == null) {
            return;
        }
        CommsMapComponent comms = CommsMapComponent.getInstance();
        if (comms != null) {
            comms.unregisterCommsLogger(selfBiometricsLogger);
        }
        selfBiometricsLogger = null;
    }

    private void restoreRetainedBiometrics() {
        for (MapItem item : getMapView().getRootGroup().getItemsRecursive()) {
            CotDetail details = new CotDetail("detail");
            OpaqueHandler.getInstance().toCotDetail(item, details);
            CotEvent event = RetainedBiometrics.snapshot(item.getUID(),
                    item.getMetaString("callsign", null), details,
                    item.getMetaLong("lastUpdateTime", -1));
            if (event != null) {
                Log.i(TAG, "Restoring opaque biometrics as stale for " + item.getUID());
                processBiometrics(event, true);
            }
        }
    }

    private void registerCotHandler() {
        registerSelfBiometricsLogger();
//        if (!LicenseManager.isLicenseValid()) {
//            Log.d(TAG, "License is invalid, not registering cot handler");
//            return;
//        }
        if (biometricsDetailHandler != null) {
            Log.d(TAG, "BiometricsDetailHandler is already registered; skipping re-register.");
            return;
        }

        biometricsDetailHandler = new CotDetailHandler("biometrics") {
            private final String TAG = "BiometricsDetailHandler";

            @Override
            public CommsMapComponent.ImportResult toItemMetadata(MapItem item, CotEvent event, CotDetail detail) {
//                Log.d("LICENSE_DEBUG",
//                        "registerCotHandler called. isLicenseValid = "
//                                + LicenseManager.isLicenseValid());
//                if (!LicenseManager.isLicenseValid()) {
//                    Log.d(TAG, "License is invalid, not processing CotDetail");
//                    return CommsMapComponent.ImportResult.FAILURE;
//                }
                Log.d("BIOMETRICS_HANDLER", "Received detail: " + detail.toString());

                // grab the cot detail
                CotDetail deviceDetail = detail.getFirstChildByName(0, "device");
                boolean isBiometricsAlert = detail.getAttribute("alertState") != null;
                if (deviceDetail != null && !isBiometricsAlert) {
                    Log.d(TAG, "Handling <biometrics> detail with device child: " + deviceDetail.toString());
                    CotDetail contact = event.getDetail().getFirstChildByName(0, "contact");
                    if (contact != null) {
                        processBiometrics(event, false);
                    }
                }
                if (isBiometricsAlert) {
                    handleBiometricsAlert(event, detail);
                }
                return CommsMapComponent.ImportResult.SUCCESS;
            }
            @Override
            public boolean toCotDetail(MapItem mapItem, CotEvent cotEvent, CotDetail cotDetail) {
                return false;
            }
        };
        Log.d("BIOMETRICS_HANDLER", "Registering biometrics handler");
        CotDetailManager.getInstance().registerHandler(biometricsDetailHandler);
        Log.d(TAG, "Registered the biometricsDetailHandler");
    }

    private void handleBiometricsAlert(CotEvent event, CotDetail detail) {
        String alertUid = detail.getAttribute("alertUid");
        CotDetail device = detail.getFirstChildByName(0, "device");
        String deviceUid = childTextOrAttribute(device, "uid");
        String state = detail.getAttribute("alertState");
        String category = detail.getAttribute("alertCategory");
        String description = detail.getAttribute("alertDescription");
        String priority = detail.getAttribute("alertPriority");

        if (isBlank(deviceUid)) {
            Log.w(TAG, "Ignoring biometric alert without device UID");
            return;
        }

        DeviceData deviceData = deviceDataMap.get(deviceUid);
        if (deviceData == null) {
            Log.w(TAG, "No device found for biometric alert deviceUid=" + deviceUid);
            return;
        }

        boolean clear = "CANCEL".equalsIgnoreCase(state);
        String eventType = category != null && !category.isEmpty()
                ? category : description;
        AlertLevel level = parsePhysioAlertLevel(priority);

        deviceData.addAlertOrClearEvent(
                alertUid,
                new Date(),
                !clear,
                clear ? AlertLevel.NONE : level,
                eventType,
                0,
                0,
                null
        );

        Log.i(TAG, "Processed biometric alert uid=" + alertUid +
                " deviceUid=" + deviceUid + " state=" + state +
                " category=" + eventType + " priority=" + priority);

        getMapView().post(new Runnable() {
            @Override
            public void run() {
                displayCotDeviceData();
            }
        });
    }

    private String childTextOrAttribute(CotDetail detail, String name) {
        if (detail == null) {
            return null;
        }
        CotDetail child = detail.getChild(name);
        if (child != null) {
            return child.getInnerText();
        }
        return detail.getAttribute(name);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private AlertLevel parsePhysioAlertLevel(String priority) {
        if ("1".equals(priority)) {
            return AlertLevel.HIGH;
        }
        if ("2".equals(priority) || "3".equals(priority)) {
            return AlertLevel.LOW;
        }
        return AlertLevel.NONE;
    }

    public void unregisterCotHandler() {
        unregisterSelfBiometricsLogger();
        if (biometricsDetailHandler != null) {
            Log.d(TAG, "Unregistering Cot Handler");
            CotDetailManager.getInstance().unregisterHandler(biometricsDetailHandler);
            biometricsDetailHandler = null;  // set it to null so we know it’s gone
        } else {
            Log.d(TAG, "biometricsDetailHandler was null, nothing to unregister");
        }
    }

    @Override
    public void onDropDownClose() {
        // Collection belongs to the plugin lifecycle, not the panel visibility.
    }

    @Override
    public void onDropDownSizeChanged(double v, double v1) {
    }

    @Override
    public void onDropDownVisible(boolean b) {
    }

    @Override
    public void disposeImpl() {
        disposed = true;
        stopScheduler();
        unregisterCotHandler();
        physioDataLogger.shutdown();
    }

    public void refreshDropDown() {
        if (isClosed()) {
            return;  // Dropdown is not open, no need to refresh
        }

//        if (LicenseManager.isLicenseValid()) {
            startScheduler();         // Start stale device check scheduler if not running
            registerCotHandler();     // register the cot handler that listens for cots with biometric data
//        } else {
//            unregisterCotHandler();
//            stopScheduler();
//            displayInfoOnUI("License Key invalid");
//        }
        displayCotDeviceData();       // Update UI elements as needed


    }
}
