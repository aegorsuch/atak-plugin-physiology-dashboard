package com.aitech.atak.ssband.services;

import static com.aitech.atak.ssband.preferences.SSbandPreferencesResolver.resolveWipeEnabled;
import static com.aitech.atak.ssband.services.AlertService.sendTroopInContactAlert;
import static com.aitech.atak.ssband.services.MarkerService.dropPoint;
import static com.aitech.atak.ssband.services.WipeDataService.wipeData;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import com.biostrap.biostrapDeviceSDK.Device;
import com.biostrap.biostrapDeviceSDK.Structures.ppgAlgorithmConfiguration;
import com.biostrap.biostrapDeviceSDK.biostrapDeviceSDK;
import com.biostrap.biostrapDeviceSDK.kairosBTSDK;
import com.aitech.atak.ssband.TakSSbandMapComponent;
import com.aitech.atak.ssband.events.HrEventThrottledBroadcaster;
import com.aitech.atak.ssband.preferences.SSbandPreferencesResolver;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import kotlin.Unit;
import kotlin.jvm.functions.Function2;
import kotlin.jvm.functions.Function5;


public class SSbandService {


    private static final String TAG = "SSbandService";
    private static kairosBTSDK sdk;
    private final HrEventThrottledBroadcaster hrEventThrottledBroadcaster = new HrEventThrottledBroadcaster();
    private String deviceSerialNumber;

    private String localDeviceId; // To store the MAC address
    private Device device; // idk but breaks if removed lol
    private int batteryLvl; // store the battery level

    private ScheduledFuture<?> scanTimeoutFuture;
    private final ScheduledExecutorService scanTimeoutScheduler = Executors.newSingleThreadScheduledExecutor();


    private Context context = null;
    private Context context1 = null;

    public void initializeSSband() {

        this.deviceSerialNumber = SSbandPreferencesResolver.resolveDeviceSerialNumber();
        Log.d(TAG, "Preferred Serial Number is: " + this.deviceSerialNumber);

        if (deviceSerialNumber == null || deviceSerialNumber.isEmpty()) {
            Log.d(TAG, "No device serial number set in preferences: Not initializing SSband.");
            return;  // Exit the method or handle accordingly
        }

        if (context == null || context1==null) {
            Log.e(TAG, "NO CONTEXT!!!!");
            return;
        }

        sdk = new kairosBTSDK(context);
        registerLicense(context1);
        setupSdkLogger();
        addWornListener();
        startScan();
        Log.d(TAG, "Initializing SSband");

        connect(deviceId -> {
            stopScan();
            addButtonClickListener(deviceId);
            startHrListening(deviceId, this.deviceSerialNumber);
        });
    }

    public void disposeSSband() {
        if (sdk == null) return;

        Log.d(TAG, "dispose SSband");

        sdk.setDiscovered(null);
        sdk.setDisconnected(s -> {
            Log.d(TAG, "Device " + s + " has been disconnected. Removing deviceId from dropdown's hashmap");
            hrEventThrottledBroadcaster.broadcastHrEvent("",-1, "",-1,true);
            // remove device from the cot hashmap!
//            TakSSbandMapComponent.getTakSSbandDropDown().removeDeviceFromHash(localDeviceId);
            TakSSbandMapComponent.getTakSSbandDropDown().displayInfoOnUI("Disconnected from SSband.");

            return null;

        });

        sdk.stopScan();
        for (Device d : sdk.getConnectedDevices()) {
            String id = d.getId();
            Log.d(TAG, "Disconnecting " + id);


            try {
                sdk.stopScan();
            } catch (Exception e) {
                Log.e(TAG, "Error during stopping scan. Probably not started. Ignoring.");
            }

            try {
                sdk.stopManual(id);
            } catch (Exception e) {
                Log.e(TAG, "Error during stopping Manual Id. Ignoring.");
                sdk.disconnect(id);
            }

            sdk.setStopManualComplete((s, aBoolean) -> {
                sdk.disconnect(id);

                return null;
            });
        }

    }

    // add ssband contexts for the tak ssband map component.
    public void addViewContexts(Context context, Context context1) {
        Log.d(TAG, "Adding view contexts");
        this.context = context;
        this.context1 = context1;
    }

    private void registerLicense(Context context) {
        try {
            biostrapDeviceSDK.acquireLicenseReturn acquireLicenseReturn = sdk.acquireLicense(getLicense(context));
            boolean isValid = acquireLicenseReturn.component1();
            int days = acquireLicenseReturn.component2();
            String message = acquireLicenseReturn.component3();
            Log.d(TAG, "License: Valid = " + isValid + ", Days = " + days + ", Message = '" + message + "'");
        } catch (Exception e) {
            Log.e(TAG, "License error:" + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private void setupSdkLogger() {
        Function5<String, String, Integer, String, String, Unit> logFn = (s1, s2, integer, s3, level) -> {
            Log.d(TAG, level + " from SSband SDK: " + s1 + " " + s2 + " " + integer + " " + s3);
            return null;
        };
        sdk.setLogD((s1, s2, integer, s3) -> logFn.invoke(s1, s2, integer, s3, "DEBUG"));
        sdk.setLogE((s1, s2, integer, s3) -> logFn.invoke(s1, s2, integer, s3, "ERROR"));
        sdk.setLogW((s1, s2, integer, s3) -> logFn.invoke(s1, s2, integer, s3, "WARNING"));
        sdk.setLogI((s1, s2, integer, s3) -> logFn.invoke(s1, s2, integer, s3, "INFO"));
    }

    private void addWornListener() {
        sdk.setDeviceWornStatus((id, isWorn) -> {
            Log.d(TAG, "Worn status change. Is worn: " + isWorn);
            // this is a good spot to call the isWorn display for the drop down!
            return null;
        });
    }

    private void connect(Consumer<String> onConnect) {
        sdk.setDiscovered((deviceId, device) -> {
            this.localDeviceId = deviceId; // Store the MAC address
            this.device = device; // Store the device object

            String deviceSerial = device.getName();  // Get the discovered device's serial number
            Log.d(TAG, "Discovered " + device.getName() + " | MAC ID: " + deviceId);

            // check if the discovered device matches the preference device serial number, if so connect. Else, continue.
            if (deviceSerial != null && deviceSerial.equals(deviceSerialNumber)) {
                Log.d(TAG, "Found match! serial number: " + deviceSerialNumber + ". Connecting...");
                sdk.connect(deviceId);
                TakSSbandMapComponent.getTakSSbandDropDown().displayInfoOnUI("Connecting...");

                sdk.setConnected(s -> {
                    Log.d(TAG, "Device " + device.getName() + " | MAC ID:" + s + " has been connected");
                    onConnect.accept(deviceId);
                    batteryLvl = device.getBatteryLevel();
                    TakSSbandMapComponent.getTakSSbandDropDown().displayInfoOnUI("Connected.");
                    return null;
                });

                sdk.setDisconnected(s -> {
                    Log.d(TAG, "Device " + device.getName() + " | MAC ID:" + s + " has been disconnected. Broadcasting HR event change");
                    hrEventThrottledBroadcaster.broadcastHrEvent("",-1, "",-1,true);
                    TakSSbandMapComponent.getTakSSbandDropDown().displayInfoOnUI("Disconnected from SSband.");

                    startScan();
                    return null;
                });
                // stop scanning since we found the device
                //stopScan();
;            } else {
                Log.d(TAG, "Discovered device serial number " + deviceSerial + " does not match your pref device:" + deviceSerialNumber);
                // optionally, keep scanning for other devices. Maybe place a timeout in preferences for this. huge maybe.
            }
            return null;
        });
    }

    private void startHrListening(String deviceId, String serialNumber) {
        sdk.setHeartRate((id, time, hr, rr) -> {

            // Fetch the latest battery level dynamically from the device object
            if (device != null) {
                batteryLvl = device.getBatteryLevel();
            } else {
                Log.e(TAG, "device is null");
            }
            Log.d(TAG, "Heart rate received from device |  Heart Rate: [" + hr + " bpm]   BatteryLvl: " + batteryLvl + " %] ");
            // Broadcast the heart rate and the latest battery level
            hrEventThrottledBroadcaster.broadcastHrEvent(deviceId, hr, serialNumber, batteryLvl, false);

            return null;
        });

        // Set the listener for the result of starting manual monitoring
        sdk.setStartManualComplete((id, successful) -> {
            if (successful) {
                Log.d(TAG, "Manual heart rate monitoring started successfully for device: " + id);
            } else {
                Log.e(TAG, "Failed to start manual heart rate monitoring for device: " + id);
            }
            return null;
        });

        Log.d(TAG, "Start listening to HR from the device");
        sdk.startManual(deviceId, createHrConfig());
    }

    private void stopScan() {
        sdk.stopScan();
        Log.d(TAG, "Scan stopped");
    }

    private void addButtonClickListener(String deviceId) {
        sdk.setButtonClicked((id, presses) -> {
            Log.d(TAG, "Button " + id + " clicked: " + presses + " times");
            if (presses == 1) {
                dropPoint();
            }
            if (presses == 5) {
                sendTroopInContactAlert();
            }
            if (presses == 10 && resolveWipeEnabled()) {
                wipeData();
            }
            return null;
        });
        sdk.setAskForButtonResponse(deviceId, true);
        sdk.setSetAskForButtonResponseComplete(((id, successful, enabled) -> {
            Log.d(TAG, "set button response complete " + id + " successful:" + successful + " is enabled: " + enabled);
            return null;
        }));

    }

    private void startScan() {
        boolean scanned = sdk.startScan(false, true, true, true);

        if (!scanned) {
            Log.d(TAG, "Cannot scan. Probably bluetooth is disabled.");
            TakSSbandMapComponent.getTakSSbandDropDown().displayInfoOnUI("Bluetooth is off. Turn on & Toggle Enable Connections");
        } else {
            Log.d(TAG, "Scanning started");
            TakSSbandMapComponent.getTakSSbandDropDown().displayInfoOnUI("Scanning...");
        }
    }

    private ppgAlgorithmConfiguration createHrConfig() {
        ppgAlgorithmConfiguration ppgAlgorithmConfiguration = new ppgAlgorithmConfiguration();
        ppgAlgorithmConfiguration.setHr(true);
        ppgAlgorithmConfiguration.setHrv(false);
        ppgAlgorithmConfiguration.setRr(false);
        ppgAlgorithmConfiguration.setSpo2(false);
        ppgAlgorithmConfiguration.setContinuous(true);
        ppgAlgorithmConfiguration.setFda_spo2(false);
        return ppgAlgorithmConfiguration;
    }

    public String getLicense(Context context) throws IOException {
        Properties properties = new Properties();
        AssetManager assetManager = context.getAssets();
        InputStream inputStream = assetManager.open("ssband.properties");
        properties.load(inputStream);
        return properties.getProperty("license");
    }

    public static boolean isBluetoothEnabled() {
        Log.d(TAG, "Bluetooth enabled is: " + sdk.bluetoothOn());
        return sdk.bluetoothOn();
    }

}
