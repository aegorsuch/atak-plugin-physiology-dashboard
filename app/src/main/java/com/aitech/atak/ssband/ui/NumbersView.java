package com.aitech.atak.ssband.ui;

public class NumbersView {

    // the resource ID for the imageView
//    private int ssbandImageId;

    // TextView 1
    private String mtextCallsign;

    // TextView 1
    private String mBatteryLevel;

    private String mheartRate;

    private String mSerialNumber;

    // create constructor to set the values for all the parameters of the each single view
    public NumbersView(String textCallsign, String heartRate, String batteryLevel, String serialNumber) {
//        ssbandImageId = ssbandImage;
        mtextCallsign = textCallsign;
        mheartRate = heartRate;
        mSerialNumber = serialNumber;
        mBatteryLevel = batteryLevel;
    }

    // getter method for returning the ID of the imageview
//    public int getssbandImageId() {
//        return ssbandImageId;
//    }

    // getter method for returning the ID of the TextView 1
    public String getCallsignText() {
        return mtextCallsign;
    }

    // getter method for returning the ID of the TextView 2
    public String getHeartRate() {
        return mheartRate;
    }

    public void setHeartRate(String heartRate) { mheartRate = heartRate; }

    // getter method for returning the ID of the TextView 2
    public String getBatteryText() {
        return mBatteryLevel;
    }

    // getter method for returning the ID of the TextView 2
    public String getSerialNumber() {
        return mSerialNumber;
    }
}
