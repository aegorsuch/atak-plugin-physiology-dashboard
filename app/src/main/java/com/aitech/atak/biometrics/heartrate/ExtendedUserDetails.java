/*
 * Copyright (C) 2024, Ascent Integrated Tech. All rights reserved.
 *
 * The copyright to the computer software herein is the property of Ascent Integrated Tech.
 * This software may not be used and/or copied without the explicit written permission
 * of Ascent Integrated Tech, and only in accordance with the terms and conditions stipulated
 * in the license agreement and/or contract under which the software has been supplied.
 */

package com.aitech.atak.biometrics.heartrate;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.widget.LinearLayout.LayoutParams.WRAP_CONTENT;
import static com.aitech.atak.biometrics.heartrate.Constants.DETAILS_META_KEY_HEART_RATE;

import android.content.Context;
import android.graphics.Color;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.atakmap.android.contact.ContactLocationView;
import com.atakmap.android.cotdetails.ExtendedInfoView;
import com.atakmap.android.maps.PointMapItem;
import com.aitech.atak.biometrics.plugin.R;

public class ExtendedUserDetails implements ContactLocationView.ExtendedSelfInfoFactory {

    private static final String TAG = "SSbandUserDetails";
    private final Context pluginContext;
    private final Context viewContext;

    public ExtendedUserDetails(Context pluginContext, Context viewContext) {
        this.pluginContext = pluginContext;
        this.viewContext = viewContext;
    }

    @Override
    public ExtendedInfoView createView() {
        return new ExtendedInfoView(viewContext) {
            @Override
            public void setMarker(PointMapItem m) {
                TextView label = new TextView(pluginContext);
                label.setText(R.string.heart_rate);
                label.setTextColor(Color.parseColor("#d4b246"));
                label.setTextSize(10);
                label.setLayoutParams(new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
                label.setPadding(0, 0, 0, 0);

                int heartRate = m.getMetaInteger(DETAILS_META_KEY_HEART_RATE, -1);

                TextView heartBeatTextField = new TextView(viewContext);
                heartBeatTextField.setLayoutParams(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
                heartBeatTextField.setTextColor(Color.WHITE);
                heartBeatTextField.setText(heartRate == -1 ? pluginContext.getString(R.string.no_data) : Integer.toString(heartRate));

                LinearLayout parent = new LinearLayout(pluginContext);
                parent.setOrientation(VERTICAL);
                parent.setLayoutParams(new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
                parent.addView(label);
                parent.addView(heartBeatTextField);
                this.addView(parent);
            }
        };
    }
}
