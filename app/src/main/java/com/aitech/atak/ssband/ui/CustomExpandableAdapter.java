package com.aitech.atak.ssband.ui;

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
import android.widget.BaseExpandableListAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.aitech.atak.ssband.AlertLevel;
import com.aitech.atak.ssband.DeviceData;
import com.aitech.atak.ssband.plugin.R;

import java.util.ArrayList;
import java.util.Locale;

public class CustomExpandableAdapter extends BaseExpandableListAdapter {
    public static final String TAG = "CustomAdapter";
    private final Context context;
    private final ArrayList<DeviceData> deviceDataList;
    private TakSSbandDropDown TakSSbandDropDown;

    public CustomExpandableAdapter(Context context, ArrayList<DeviceData> deviceDataList) {
        this.context = context;
        this.deviceDataList = deviceDataList;
    }



    @SuppressLint("ResourceAsColor")
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
        TextView textLastConnected = convertView.findViewById(R.id.textLastConnected);

        // Populate the views with data from DeviceData
        textCallsign.setText(currentDeviceData.callsign != null ? currentDeviceData.callsign : "- - -");
        textHeartRate.setText(currentDeviceData.heartRate != null ? (int) Math.round(currentDeviceData.heartRate) + " bpm" : "- - -");
        textExertion.setText(currentDeviceData.exertion != null ? (int) Math.round(currentDeviceData.exertion) + " %" : "- - -");
        textCoreBodyTemp.setText((currentDeviceData.coreTemp != null) ? String.format(Locale.ENGLISH, "%.2f", currentDeviceData.coreTemp * 9 / 5. + 32) + " °F" : "- - -");
        textBattery.setText(currentDeviceData.batteryLevel != null ? (int) Math.floor(currentDeviceData.batteryLevel) + "%" : "- - -");

        //TODO fix this
        long staleTimeSeconds = (currentDeviceData.staleTime.getMilliseconds() /1000) % 60;
        Log.d("connected", String.valueOf(staleTimeSeconds));

        textLastConnected.setText(currentDeviceData.staleTime != null ? "Last connected " + staleTimeSeconds + " seconds ago" : "- - -");

        //set isFalsePositive UI change
        TextView textisFalsePositive = convertView.findViewById(R.id.textisFalsePositive);
        ImageView iconWearableLogo = convertView.findViewById(R.id.iconWearableLogo);

        if (String.valueOf(currentDeviceData.model).equals("SS_BAND_V2")) {
            iconWearableLogo.setImageResource(R.drawable.slatesafety_shield);
        } else {
            iconWearableLogo.setImageResource(R.drawable.biostrap);
            textExertion.setVisibility(GONE);
            textCoreBodyTemp.setVisibility(GONE);
        }

        ImageView alertIcon = convertView.findViewById(R.id.alertIcon);

        if (!currentDeviceData.isStale()) {
            textLastConnected.setVisibility(GONE);
            convertView.setBackgroundResource(R.color.greenbg);
            alertIcon.setVisibility(INVISIBLE);
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
            //Set disconnect UI changes
            }
        } else if (currentDeviceData.isStale()){
            convertView.setBackgroundResource(R.color.graybg);
            textLastConnected.setVisibility(VISIBLE);
            alertIcon.setVisibility(VISIBLE);
            alertIcon.setImageResource(R.drawable.notconnected);
            Log.d(TAG, "disconnect UI run " + currentDeviceData.callsign);
        }

        return convertView;
    }

    @Override
    public int getGroupCount() {
        return deviceDataList.size();
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        return deviceDataList.get(groupPosition).alertOrClearEventsEQ.size() + 1;
    }

    @Override
    public Object getGroup(int groupPosition) {
        return deviceDataList.get(groupPosition);
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        if (childPosition == 0) {
            return deviceDataList.get(groupPosition);
        }
        return deviceDataList.get(groupPosition).alertOrClearEventsEQ.toArray()[childPosition];
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        // Inflate the layout if it's not already created
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.custom_group_view, parent, false);
        }

        // Get the current DeviceData object
        DeviceData currentDeviceData = deviceDataList.get(groupPosition);


        // Find the TextViews in the layout and populate them with the current device data
        TextView textCallsign = convertView.findViewById(R.id.textCallsign);
        TextView textHeartRate = convertView.findViewById(R.id.textHeartRate);
        TextView textExertion = convertView.findViewById(R.id.textExertion);
        TextView textCoreBodyTemp = convertView.findViewById(R.id.textCoreBodyTemp);
        TextView textBattery = convertView.findViewById(R.id.textBattery);
        TextView textLastConnected = convertView.findViewById(R.id.textLastConnected);

        // Populate the views with data from DeviceData
        textCallsign.setText(currentDeviceData.callsign != null ? currentDeviceData.callsign : "- - -");
        textHeartRate.setText(currentDeviceData.heartRate != null ? (int) Math.round(currentDeviceData.heartRate) + " bpm" : "- - -");
        textExertion.setText(currentDeviceData.exertion != null ? (int) Math.round(currentDeviceData.exertion) + " %" : "- - -");
        textCoreBodyTemp.setText((currentDeviceData.coreTemp != null) ? String.format(Locale.ENGLISH, "%.2f", currentDeviceData.coreTemp * 9 / 5. + 32) + " °F" : "- - -");
        textBattery.setText(currentDeviceData.batteryLevel != null ? (int) Math.floor(currentDeviceData.batteryLevel) + "%" : "- - -");

        //TODO fix this
        long staleTimeSeconds = (currentDeviceData.staleTime.getMilliseconds() /1000) % 60;
        Log.d("connected", String.valueOf(staleTimeSeconds));

        textLastConnected.setText(currentDeviceData.staleTime != null ? "Last connected " + staleTimeSeconds + " seconds ago" : "- - -");

        //set isFalsePositive UI change
        TextView textisFalsePositive = convertView.findViewById(R.id.textisFalsePositive);
        ImageView iconWearableLogo = convertView.findViewById(R.id.iconWearableLogo);

        if (String.valueOf(currentDeviceData.model).equals("SS_BAND_V2")) {
            iconWearableLogo.setImageResource(R.drawable.slatesafety_shield);
        } else {
            iconWearableLogo.setImageResource(R.drawable.biostrap);
            textExertion.setVisibility(GONE);
            textCoreBodyTemp.setVisibility(GONE);
        }

        ImageView alertIcon = convertView.findViewById(R.id.alertIcon);

        if (!currentDeviceData.isStale()) {
            textLastConnected.setVisibility(GONE);
            convertView.setBackgroundResource(R.color.greenbg);
            alertIcon.setVisibility(INVISIBLE);
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
                //Set disconnect UI changes
            }
        } else if (currentDeviceData.isStale()){
            convertView.setBackgroundResource(R.color.graybg);
            textLastConnected.setVisibility(VISIBLE);
            alertIcon.setVisibility(VISIBLE);
            alertIcon.setImageResource(R.drawable.notconnected);
            Log.d(TAG, "disconnect UI run " + currentDeviceData.callsign);
        }

        return convertView;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        // Inflate the layout if it's not already created
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.custom_child_view, parent, false);
        }

        DeviceData currentDeviceData = deviceDataList.get(groupPosition);

        TextView textCaloricBurnRate = convertView.findViewById(R.id.textCaloricBurnRate);
        TextView textHrlv = convertView.findViewById(R.id.textHrlv);
        TextView textVo2 = convertView.findViewById(R.id.textVo2);
        TextView textHrv = convertView.findViewById(R.id.textHrv);

        textCaloricBurnRate.setText(currentDeviceData.caloricBurnRate != null ? (int) Math.floor(currentDeviceData.caloricBurnRate) + " kCal/hr" : "- - -");
        textHrlv.setText(currentDeviceData.hrlv != null ? (int) Math.round(currentDeviceData.hrlv) + "%" : "- - -");
        textVo2.setText(currentDeviceData.vo2 != null ? (int) Math.round(currentDeviceData.vo2) + " ml/(kg * min)" : "- - -");
        textHrv.setText(currentDeviceData.hrv != null ? (int) Math.round(currentDeviceData.hrv) + " ms" : "- - -");

        TextView alertOrClearEventTextView = (TextView) convertView.findViewById(R.id.alertOrClearEvent);
        TextView alertPriorityTextView = (TextView) convertView.findViewById(R.id.alertPriority);
        RelativeLayout expandedData = convertView.findViewById(R.id.expandedHealthData);

        if (childPosition == 0) {
            alertOrClearEventTextView.setText("");
            alertPriorityTextView.setText("");
            expandedData.setVisibility(VISIBLE);

        } else {
            DeviceData.AlertOrClearEvent alertOrClearEvent = (DeviceData.AlertOrClearEvent) deviceDataList.get(groupPosition).alertOrClearEventsEQ.toArray()[childPosition-1];
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.custom_child_view, parent, false);
            }

            expandedData.setVisibility(GONE);

            if (alertOrClearEvent.isAlert) {
                alertOrClearEventTextView.setText("ALERT ");
            } else {
                alertOrClearEventTextView.setText("CLEAR ");
            }
            alertPriorityTextView.setText(alertOrClearEvent.alertLevel.toString());
        }
        return convertView;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }
}
