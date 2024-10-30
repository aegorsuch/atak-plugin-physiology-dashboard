package com.aitech.atak.ssband;

import android.util.Log;

import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.google.common.collect.EvictingQueue;

import java.util.Date;

public class DeviceData {
    public static final String TAG = "TakSSbandDeviceData";
    class Location {
        double lat;
        double lon;
        double height;
        double accuracy;
        double speed;
    }
    class AlertOrClearEvent {
        boolean isAlert; // true if alert event, false if clear event
        AlertLevel alertLevel; // Only valid if isAlert == true
        String type; // could be "HEART_RATE", "CORE_TEMP, "EXERTION", "HRLV"
        double limit;
        int durationMins; // Duration that limit has to be exceeded or not for alert or clear to be invoked
        Location location;

        public AlertOrClearEvent(boolean isAlert, AlertLevel alertLevel, String type, double limit, int durationMins, Location location) {
            this.isAlert = isAlert;
            this.alertLevel = alertLevel;
            this.type = type;
            this.limit = limit;
            this.durationMins = durationMins;
            this.location = location;
        }
    }

    public String model;
    public String callsign;
    private boolean isStale = false;
    private Boolean isLocal;
    public CoordinatedTime staleTime;
    public String serialNumber;
    // usually false; When a measurement has isFalsePositive set to true,
    // it means that the BAND V2 was able to get a reading, but through a
    // variety of heuristics determined that it was not fully confident in the reading's accuracy.
    // The most common cause of "false positive" readings is the band not being worn properly
    // or loosely, i.e. the band's sensor is not able to make consistent enough contact with the skin for confident readings.
    public Boolean isFalsePositive;
    public Double coreTemp; // in deg C
    public Double heartRate; // in beats per minute
    public Double exertion; // in percentage; This roughly corresponds to the
                            // physiological strain the wearer is under. It is never less than 0%,
                            // but can in extreme circumstances go over 100%.
    public Double hssi; // in percentage; Heart Rate Signal Strenth Indicator
    public Double cadence; // in steps/min; Corresponds to the walking speed of the wearer
    public Double caloricBurnRate; // in kCal/hr
    public Double hrlv; // in percentage; This is another measure of physiological strain that places a greater emphasis on the user's age
    public Double vo2; // in ml/(kg * min); This measures blood oxygen levels
    public Double hrv; // in ms; This stands for Heart Rate Variability
    public Double batteryLevel; // in percentage
    public boolean isAlerting; // true if device detects an alert (Not TAP based)
    public Date timeHighestAlertLevel;
    public AlertLevel highestAlertLevel; // SlateSafety only uses LOW or HIGH
    public int numberOfActiveAlerts;
    public Location location;
    public EvictingQueue<AlertOrClearEvent> alertOrClearEventsEQ = EvictingQueue.create(10);

    // We should never have to call this in run time but it can happen.
    public DeviceData(String model, String callsign, Boolean isLocal, String serialNumber) {
        this.model = model;
        this.callsign = callsign;
        this.isLocal = isLocal;
        this.serialNumber = serialNumber;

        this.staleTime = null;
        this.isFalsePositive = null;
        this.coreTemp = null;
        this.heartRate = null;
        this.exertion = null;
        this.hssi = null;
        this.cadence = null;
        this.caloricBurnRate = null;
        this.hrlv = null;
        this.vo2 = null;
        this.hrv = null;
        this.isAlerting = false;
        this.timeHighestAlertLevel = null;
        this.highestAlertLevel = null;
        this.numberOfActiveAlerts = 0;
        this.location = null;
        // We already created alertOrClearEvents
    }
    // call this for biometrics
    public DeviceData(String model, String callsign, Boolean isLocal, String serialNumber, CoordinatedTime staleTime,
                      Boolean isFalsePositive, Double coreTemp, Double heartRate, Double exertion,
                      Double hssi, Double cadence, Double caloricBurnRate, Double hrlv, Double vo2, Double hrv, Double batteryLevel) {
        this.model = model;
        this.callsign = callsign;
        this.isLocal = isLocal;
        this.serialNumber = serialNumber;

        this.staleTime = staleTime;
        this.isFalsePositive = isFalsePositive;
        this.coreTemp = coreTemp;
        this.heartRate = heartRate;
        this.exertion = exertion;
        this.hssi = hssi;
        this.cadence = cadence;
        this.caloricBurnRate = caloricBurnRate;
        this.hrlv = hrlv;
        this.vo2 = vo2;
        this.hrv = hrv;
        this.batteryLevel = batteryLevel;
        this.isAlerting = false;
        this.timeHighestAlertLevel = null;
        this.highestAlertLevel = null;
        this.numberOfActiveAlerts = 0;
        this.location = null;
        // We already created alertOrClearEvents
    }

    public void updateBiometrics(String callsign, Boolean isLocal, String serialNumber, CoordinatedTime staleTime,
                                 Boolean isFalsePositive, Double coreTemp, Double heartRate, Double exertion,
                      Double hssi, Double cadence, Double caloricBurnRate, Double hrlv, Double vo2, Double hrv, Double batteryLevel) {
        this.callsign = callsign;
        this.isLocal = isLocal;
        this.staleTime = staleTime;
        this.serialNumber = serialNumber;

        this.isFalsePositive = isFalsePositive;
        this.coreTemp = (coreTemp * 9/5) + 32;
        this.heartRate = heartRate;
        this.exertion = exertion;
        this.hssi = hssi;
        this.cadence = cadence;
        this.caloricBurnRate = caloricBurnRate;
        this.hrlv = hrlv;
        this.vo2 = vo2;
        this.hrv = hrv;
        this.batteryLevel = batteryLevel;
    }

    public void addAlertOrClearEvent(Date time, boolean isAlert, AlertLevel alertLevel, String type, double limit, int durationMins, Location location) {
        AlertOrClearEvent event = new AlertOrClearEvent(isAlert, alertLevel, type, limit, durationMins, location);
        if (isAlert) {
            if (!this.isAlerting) {
                this.numberOfActiveAlerts = 1;
                this.timeHighestAlertLevel = time;
                this.highestAlertLevel = alertLevel;
            } else {
                this.numberOfActiveAlerts++;
                if (this.highestAlertLevel != AlertLevel.HIGH) {
                    // So highestAlertLevel is LOW and cannot be NONE as isAlerting is true, so if this alert is high, update
                    if (alertLevel == AlertLevel.HIGH) {
                        this.timeHighestAlertLevel = time;
                        this.highestAlertLevel = alertLevel;
                    }
                }
            }
            this.isAlerting = true;
        } else {
            this.isAlerting = false;
            this.numberOfActiveAlerts = 0;
            this.timeHighestAlertLevel = null;
            this.highestAlertLevel = AlertLevel.NONE;
        }
        this.alertOrClearEventsEQ.add(event);
        Log.d(TAG, "alertOrClearEventsEQ: size(" + alertOrClearEventsEQ.size() + "); isAlerting: " + this.isAlerting + " highestAlertLevel: " + highestAlertLevel + "; numberOfActiveAlerts: " + numberOfActiveAlerts + "; timeHighestAlertLevel: " + ((null == timeHighestAlertLevel)?"null": timeHighestAlertLevel.toString()));
    }

    public boolean isLocal() {
        return isLocal;
    }

    public void setLocal(boolean local) {
        isLocal = local;
    }

    public void setStale(boolean stale) { isStale = stale;}

    public synchronized boolean isStale() {
        return isStale;
    }
}

