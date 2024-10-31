package com.aitech.atak.ssband.ui;

import static com.aitech.atak.ssband.preferences.SSbandPreferences.PREFERENCES_KEYS;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.util.Log;
import android.widget.ListView;
import android.widget.TextView; // for displaying text in the dropdown
import android.widget.Button; //add button widget android
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.aitech.atak.ssband.AlertLevel;
import com.aitech.atak.ssband.TakSSbandMapComponent;
import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.SettingsActivity;
import com.atakmap.comms.CotStreamListener;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.comms.CotServiceRemote;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.aitech.atak.ssband.plugin.R;
import com.aitech.atak.ssband.DeviceData;
import com.aitech.atak.ssband.preferences.SSbandPreferencesResolver;
import com.aitech.atak.ssband.services.LicenseManager;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TakSSbandDropDown extends DropDownReceiver implements com.atakmap.android.dropdown.DropDown.OnStateListener {

    public static final String SHOW_DROP_DOWN = "com.aitech.atak.ssband.SHOW_DROP_DOWN";
    public static final String TAG = "TakSSbandDropDown";
    private final View dropDownView;
    private CotStreamListener cotStreamListener;
    private ScheduledExecutorService scheduler;
    private String localDeviceSerialNumber;

    // hashmap to store heart rates with ssband devices
    private final Map<String, Integer> heartRateData = new ConcurrentHashMap<>();
    private final Map<String, DeviceData> deviceDataMap = new ConcurrentHashMap<>();
    private DeviceData localDeviceData;

    private ArrayList<DeviceData> arrayList;
    private CustomAdapter customAdapter;
    private ListView listView;

    public TakSSbandDropDown(final MapView mapView, final Context context) {
        super(mapView);
        dropDownView = PluginLayoutInflater.inflate(context, R.layout.drop_down_view);

        localDeviceSerialNumber = SSbandPreferencesResolver.resolveDeviceSerialNumber();

        // Initialize the arrayList and customAdapter
        arrayList = new ArrayList<>();
        customAdapter = new CustomAdapter(context, arrayList);
        // Find the ListView and set the adapter
        listView = dropDownView.findViewById(R.id.listView);
        listView.setAdapter(customAdapter);

        startScheduler();
    }

    private void loadSSbandPreferencesButton(Context context) {
        if (context == null) {
            Log.e(TAG, "Context is null, can't load the SSband Status Information");
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
            if (data.isLocal()) {
                // Add local device at the top
                arrayList.add(0, data);
            } else {
                // Add remote devices afterward
                arrayList.add(data);
            }
        }

//        Log.d(TAG, "Updating DropDown with device data");

        // Update the adapter to reflect the new data
        customAdapter.notifyDataSetChanged();
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

    public void displayDeviceSerialNumber(String deviceSerialNumber) {
        String displaySerialNumber = "Serial Number: " + deviceSerialNumber;
        Log.d(TAG, displaySerialNumber);
        TextView textView = dropDownView.findViewById(R.id.deviceSerialNumber);
        textView.setText(displaySerialNumber);
    }

    public void displayConnectionStatus(String connectionStatus) {
        String displayConnectionStatus = "Connection Status: " + connectionStatus;
        Log.d(TAG, displayConnectionStatus);
        TextView textView = dropDownView.findViewById(R.id.connectionStatus);
        textView.setText(displayConnectionStatus);
    }

    public void displayBatteryLevel(Integer batteryLevel, boolean displayBattery) {
        if (!displayBattery) {
            String batteryLevelDisplay = "Battery Level:";
            Log.d(TAG, batteryLevelDisplay);
            TextView textView = dropDownView.findViewById(R.id.batteryLevel);
            textView.setText(batteryLevelDisplay);
        } else {
            String batteryLevelDisplay = "Battery Level: " + batteryLevel.toString() + "%";
            Log.d(TAG, batteryLevelDisplay);
            TextView textView = dropDownView.findViewById(R.id.batteryLevel);
            textView.setText(batteryLevelDisplay);
        }
    }

    public void displayInfoOnUI(String info) {
        getMapView().post(() -> {
            TextView textView = dropDownView.findViewById(R.id.infoString);
            Log.d(TAG, "Displaying info string to the UI: " + info);
            textView.setText(info);
        });
    }


    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        Log.d(TAG, "onReceive action: " + action);
        if (action == null) return;

        if (action.equals(SHOW_DROP_DOWN)) {
            if (!isClosed()) {
                //hideDropDown();
                closeDropDown();
                return;
            }
            showDropDown(dropDownView, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH, HALF_HEIGHT, false, this);

            if (LicenseManager.isLicenseValid()) {
                if (cotStreamListener == null) {
                    cotStreamListener = new CotStreamListener(context, "SSbandListener", new CotServiceRemote.CotEventListener() {
                        @Override
                        public void onCotEvent(CotEvent cotEvent, Bundle bundle) {
                            //                        Log.d(TAG, "onCotEvent called");
                            CotDetail detail = cotEvent.getDetail();
                            cotReceiver(detail, cotEvent);
                        }
                    });
                }
                if (!SSbandPreferencesResolver.resolveSSbandConnectionEnabled()) {
                    Log.d(TAG, "SSband Preferences are disabled, updating UI");
                    displayInfoOnUI("SSband Connections Disabled");
                }
            } else {
                Log.d(TAG, "License is invalid");
                displayInfoOnUI("License Key Invalid.");
                stopScheduler();
                unregisterCotStreamListener(); // Ensure listener is unregistered

            }

            // create prefs button, listen for click. if clicked, launch ssband preferences screen.
            loadSSbandPreferencesButton(context);
            setAssociationKey(PREFERENCES_KEYS);
        }
    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    //Takes in details from cot event
    private void cotReceiver(CotDetail detail, CotEvent cotEvent) {
        if (!LicenseManager.isLicenseValid()) {
            Log.d(TAG, "License is invalid, ignoring the CoT event.");
            return;
        }
        detail = cotEvent.getDetail();
        if (detail != null) {
            // Find the 'status' child element
            CotDetail statusDetail = detail.getFirstChildByName(0, "status");
            if (statusDetail != null) {
                // Extract data
                String heartRateStr = statusDetail.getAttribute("heartRate");
                String serialNumber = statusDetail.getAttribute("serialNumber");
                String batteryLevelStr = statusDetail.getAttribute("batteryLevel");
                String callsign = statusDetail.getAttribute("callsign");
                String deviceId = cotEvent.getUID(); // Using UID as deviceId
                CoordinatedTime staleTime = cotEvent.getStale();
                if (heartRateStr != null && deviceId != null) {
                    try {
                        int heartRate = Integer.parseInt(heartRateStr);
                        int batteryLevel = batteryLevelStr != null ? Integer.parseInt(batteryLevelStr) : -1;
                        TakSSbandMapComponent.testCounter = TakSSbandMapComponent.testCounter + 1;
                        String modelStr = statusDetail.getAttribute("model");
                        Boolean isFalsePositive = Boolean.valueOf(statusDetail.getAttribute("isFalsePositive"));
                        String coreTempStr = statusDetail.getAttribute("coreTemp");
                        String exertionStr = statusDetail.getAttribute("exertion");
                        String hssiStr = statusDetail.getAttribute("hssi");
                        String cadenceStr = statusDetail.getAttribute("cadence");
                        String caloricBurnRateStr = statusDetail.getAttribute("caloricBurnRate");
                        String hrlvStr = statusDetail.getAttribute("hrlv");
                        String vo2Str = statusDetail.getAttribute("vo2");
                        String hrvStr = statusDetail.getAttribute("hrv");

                        if (serialNumber != null && serialNumber.equals(SSbandPreferencesResolver.resolveDeviceSerialNumber())) {
//                            Log.d(TAG, "Received heart rate data from local device; storing in deviceDataMap.");
                            callsign = callsign + " (You)";
                            Log.d(TAG, "Recieved Cot - LOCAL  - Heart Rate: [ " + heartRateStr + " bpm ]   Device: [ " + serialNumber + " ] " + "   Callsign: [ " + callsign + " ]" );
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
                                    if ((TakSSbandMapComponent.testCounter % 36) == 0) {
                                        localDeviceData.addAlertOrClearEvent(new Date(), false, AlertLevel.NONE, "HEART_RATE", 150, 1, null);
                                    } else if ((TakSSbandMapComponent.testCounter % 24) == 0) {
                                        localDeviceData.addAlertOrClearEvent(new Date(), true, AlertLevel.HIGH, "HEART_RATE", 150, 1, null);
                                    } else if ((TakSSbandMapComponent.testCounter % 12) == 0) {
                                        localDeviceData.addAlertOrClearEvent(new Date(), true, AlertLevel.LOW, "HEART_RATE", 120, 1, null);
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
                                if ((TakSSbandMapComponent.testCounter % 36) == 0) {
                                    localDeviceData.addAlertOrClearEvent(new Date(), false, AlertLevel.NONE, "HEART_RATE", 150, 1, null);
                                } else if ((TakSSbandMapComponent.testCounter % 24) == 0) {
                                    localDeviceData.addAlertOrClearEvent(new Date(), true, AlertLevel.HIGH, "HEART_RATE", 150, 1, null);
                                } else if ((TakSSbandMapComponent.testCounter % 12) == 0) {
                                    localDeviceData.addAlertOrClearEvent(new Date(), true, AlertLevel.LOW, "HEART_RATE", 120, 1, null);
                                }
                            }
                            getMapView().post(this::displayCotDeviceData);
                        } else {
                            Log.d(TAG, "Recieved Cot - REMOTE - Heart Rate: [ " + heartRateStr + " bpm ]   Device: [ " + serialNumber + " ] " + "   Callsign: [ " + callsign + " ]" );

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
                                Log.d(TAG, "Adding testcounter: " + TakSSbandMapComponent.testCounter);
                                if ((TakSSbandMapComponent.testCounter % 36) == 0) {
                                    Log.d(TAG, deviceId + ": Adding clear event");
                                    remoteDeviceData.addAlertOrClearEvent(new Date(), false, AlertLevel.NONE, "HEART_RATE", 110, 1, null);
                                } else if ((TakSSbandMapComponent.testCounter % 24) == 0) {
                                    Log.d(TAG, deviceId + ": Adding HIGH alert event");
                                    remoteDeviceData.addAlertOrClearEvent(new Date(), true, AlertLevel.HIGH, "HEART_RATE", 150, 1, null);
                                } else if ((TakSSbandMapComponent.testCounter % 12) == 0) {
                                    Log.d(TAG, deviceId + ": Adding LOW alert event");
                                    remoteDeviceData.addAlertOrClearEvent(new Date(), true, AlertLevel.LOW, "HEART_RATE", 120, 1, null);
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
                                deviceDataMap.put(deviceId, remoteDeviceData); // Store in the map
                                Log.d(TAG, "Adding testcounter: " + TakSSbandMapComponent.testCounter);
                                if ((TakSSbandMapComponent.testCounter % 36) == 0) {
                                    Log.d(TAG, deviceId + ": Adding clear event");
                                    remoteDeviceData.addAlertOrClearEvent(new Date(), false, AlertLevel.NONE, "HEART_RATE", 150, 1, null);
                                } else if ((TakSSbandMapComponent.testCounter % 24) == 0) {
                                    Log.d(TAG, deviceId + ": Adding HIGH alert event");
                                    remoteDeviceData.addAlertOrClearEvent(new Date(), true, AlertLevel.HIGH, "HEART_RATE", 150, 1, null);
                                } else if ((TakSSbandMapComponent.testCounter % 12) == 0) {
                                    Log.d(TAG, deviceId + ": Adding LOW alert event");
                                    remoteDeviceData.addAlertOrClearEvent(new Date(), true, AlertLevel.LOW, "HEART_RATE", 120, 1, null);
                                }
                            }
                            getMapView().post(this::displayCotDeviceData);
                        }

                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Invalid heart rate value: " + heartRateStr, e);
                    }
                }
            } else {
                Log.e(TAG, "Received CoT event without 'status' detail");
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
//        Log.d(TAG, "Removing stale CoT messages.");
        for (Map.Entry<String, DeviceData> entry : deviceDataMap.entrySet()) {
            String deviceId = entry.getKey();
            DeviceData deviceData = entry.getValue();

            if (isDeviceStale(deviceData)) {
                Log.d(TAG, "Device " + deviceId + " is stale, removing it from the map.");
                removeDeviceFromHash(deviceId); // Using the existing method to remove the device

                // Update the UI to remove the device from the dropdown
                getMapView().post(this::displayCotDeviceData);
            } else {
                Log.d("Stale", "Device " + deviceId + " is not stale.");
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



    @Override
    public void onDropDownClose() {
        // Dispose of the CoT listener when dropdown is closed.
        if (cotStreamListener != null) {
            cotStreamListener.dispose();
            cotStreamListener = null;
        }

        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow(); // Shut down the scheduler
            Log.d(TAG, "Scheduler shut down.");
        }

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
            // Re-register the cotStreamListener if necessary
            if (cotStreamListener == null) {
                cotStreamListener = new CotStreamListener(getMapView().getContext(), "SSbandListener", new CotServiceRemote.CotEventListener() {
                    @Override
                    public void onCotEvent(CotEvent cotEvent, Bundle bundle) {
                        CotDetail detail = cotEvent.getDetail();
                        cotReceiver(detail, cotEvent);
                    }
                });
            }
            // Start scheduler if not running
            startScheduler();
        } else {
            // License invalid, unregister listener and stop scheduler
            unregisterCotStreamListener();
            stopScheduler();
            displayInfoOnUI("License Key Invalid.");
        }

        // Update UI elements as needed
        displayCotDeviceData();
    }


}
