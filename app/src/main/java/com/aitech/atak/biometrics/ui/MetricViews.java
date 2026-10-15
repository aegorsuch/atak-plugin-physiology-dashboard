package com.aitech.atak.biometrics.ui;

import android.view.View;
import android.widget.TextView;

import com.aitech.atak.biometrics.DeviceData;
import com.aitech.atak.biometrics.plugin.R;

final class MetricViews {
    private MetricViews() {
    }

    static void bind(View row, DeviceData data) {
        set(row, R.id.textExertion, MetricValues.format(data.exertion, "%.0f %%"));
        set(row, R.id.textHeartRate, MetricValues.format(data.heartRate, "%.0f bpm"));
        set(row, R.id.textCoreBodyTemp, MetricValues.skinTemperature(data.coreTemp));
        set(row, R.id.textBattery, MetricValues.format(data.batteryLevel, "%.0f%%"));
        set(row, R.id.textCaloricBurnRate, MetricValues.format(data.caloricBurnRate, "%.0f kCal/hr"));
        set(row, R.id.textHrlv, MetricValues.format(data.hrlv, "%.0f%%"));
        set(row, R.id.textVo2, MetricValues.format(data.vo2, "%.0f ml/(kg * min)"));
        set(row, R.id.textHrv, MetricValues.format(data.hrv, "%.0f ms"));
    }

    private static void set(View row, int id, String value) {
        TextView view = row.findViewById(id);
        if (view != null) {
            view.setText(value);
            view.setVisibility(value.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }
}
