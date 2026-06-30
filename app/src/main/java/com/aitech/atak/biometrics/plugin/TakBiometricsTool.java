/*
 * Copyright (C) 2024, Ascent Integrated Tech. All rights reserved.
 *
 * The copyright to the computer software herein is the property of Ascent Integrated Tech.
 * This software may not be used and/or copied without the explicit written permission
 * of Ascent Integrated Tech, and only in accordance with the terms and conditions stipulated
 * in the license agreement and/or contract under which the software has been supplied.
 */

package com.aitech.atak.biometrics.plugin;


import static com.aitech.atak.biometrics.ui.TakBiometricsDropDown.SHOW_DROP_DOWN;

import android.content.Context;
import android.util.Log;

import com.atak.plugins.impl.AbstractPluginTool;


public class TakBiometricsTool extends AbstractPluginTool {
    private static final String TAG = "TakBiometricsTool";

    public TakBiometricsTool(final Context context) {
        super(context, context.getString(R.string.app_name), context.getString(R.string.app_name), context.getResources().getDrawable(R.drawable.physio_atak_logo), SHOW_DROP_DOWN);
        Log.d(TAG, "Loaded TakBiometricsTool");
     }
}
