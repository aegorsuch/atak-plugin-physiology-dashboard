package com.aitech.atak.ssband.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.aitech.atak.ssband.DeviceData;

import com.aitech.atak.ssband.plugin.R;

import java.util.ArrayList;
public class CustomAdapter extends ArrayAdapter<DeviceData> {

    private final Context context;
    private final ArrayList<DeviceData> deviceDataList;

    public CustomAdapter(Context context, ArrayList<DeviceData> deviceDataList) {
        super(context, 0, deviceDataList);
        this.context = context;
        this.deviceDataList = deviceDataList;
    }

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
        TextView textHeartRate = convertView.findViewById(R.id.textHeartRate);
        TextView textExertion = convertView.findViewById(R.id.textExertion);
        TextView textCoreBodyTemp = convertView.findViewById(R.id.textCoreBodyTemp);
        TextView textBattery = convertView.findViewById(R.id.textBattery);

        // Populate the views with data from DeviceData
        textCallsign.setText(currentDeviceData.callsign != null ? currentDeviceData.callsign : "N/A");
        textHeartRate.setText(currentDeviceData.heartRate != null ? currentDeviceData.heartRate + " bpm" : "N/A");
        textExertion.setText(currentDeviceData.exertion != null ? currentDeviceData.exertion + " %" : "N/A");
        textCoreBodyTemp.setText(currentDeviceData.coreTemp != null ? currentDeviceData.coreTemp + " °F" : "N/A");
        textBattery.setText(currentDeviceData.batteryLevel != null ? currentDeviceData.batteryLevel + "%" : "N/A");


        return convertView;
    }
}
