package com.aitech.atak.ssband.plugin;


import static com.aitech.atak.ssband.ui.TakSSbandDropDown.SHOW_DROP_DOWN;

import android.content.Context;
import android.util.Log;

import com.atak.plugins.impl.AbstractPluginTool;


public class TakSSbandTool extends AbstractPluginTool {
    private static final String TAG = "TakSSbandTool";

    public TakSSbandTool(final Context context) {
        super(context, context.getString(R.string.app_name), context.getString(R.string.app_name), context.getResources().getDrawable(R.drawable.slatesafety_shield), SHOW_DROP_DOWN);
        Log.d(TAG, "Loaded TakSSband Tool");     
     }
}
