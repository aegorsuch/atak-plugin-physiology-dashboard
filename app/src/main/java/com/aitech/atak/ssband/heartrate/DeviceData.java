package com.aitech.atak.ssband.heartrate;

import com.atakmap.coremap.maps.time.CoordinatedTime;

public class DeviceData {
    public String callsign;
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

    public DeviceData(String callsign, Boolean isLocal, CoordinatedTime staleTime, String serialNumber,
                      Boolean isFalsePositive, Double coreTemp, Double heartRate, Double exertion,
                      Double hssi, Double cadence, Double caloricBurnRate, Double hrlv, Double vo2, Double hrv, Double batteryLevel) {
        this.callsign = callsign;
        this.isLocal = isLocal;
        this.staleTime = staleTime;
        this.serialNumber = serialNumber;

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
    }

    public boolean isLocal() {
        return isLocal;
    }

    public void setLocal(boolean local) {
        isLocal = local;
    }

}

