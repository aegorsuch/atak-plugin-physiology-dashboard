package com.aitech.atak.ssband;

import static android.content.Context.RECEIVER_EXPORTED;
import static com.aitech.atak.ssband.events.Events.HR_RECEIVED;
import static com.aitech.atak.ssband.preferences.SSbandPreferences.ENABLE_BT_CONNECTIONS;
import static com.aitech.atak.ssband.preferences.SSbandPreferences.PREFERENCES_KEYS;
import static com.aitech.atak.ssband.preferences.SSbandPreferencesResolver.resolveDeviceSerialNumber;
import static com.aitech.atak.ssband.ui.TakSSbandDropDown.SHOW_DROP_DOWN;
import static com.aitech.atak.ssband.preferences.SSbandPreferencesResolver.resolveSSbandConnectionEnabled;

import com.aitech.atak.ssband.preferences.SSbandPreferenceFragment;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.preference.PreferenceManager;
import android.content.SharedPreferences;
import android.util.Log;

import com.atakmap.android.contact.ContactLocationView;
import com.atakmap.android.cot.detail.CotDetailManager;
import com.atakmap.android.dropdown.DropDownMapComponent;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.preferences.ToolsPreferenceFragment;
import com.aitech.atak.ssband.events.TakSSbandEventController;
import com.aitech.atak.ssband.heartrate.ExtendedUserDetails;
import com.aitech.atak.ssband.heartrate.HeartRateCodHandler;
import com.aitech.atak.ssband.plugin.R;
import com.aitech.atak.ssband.preferences.SSbandPreferenceFragment;
import com.aitech.atak.ssband.services.SSbandService;
import com.aitech.atak.ssband.ui.TakSSbandDropDown;
import com.aitech.atak.ssband.preferences.SSbandPreferencesResolver;

public class TakSSbandMapComponent extends DropDownMapComponent {

    private Context context;
    private TakSSbandEventController takSSbandEventController;
    private static TakSSbandDropDown takSSbandDropDown;        // will have to make this public so we can access the updateHeartRate functionality
    private final String TAG = "TakSSbandMapComponent";
    private final static SSbandService ssbandService = new SSbandService();
    private HeartRateCodHandler heartRateCodHandler;
    private ExtendedUserDetails extendedUserDetails;
    // TODO test only: remove line below
    public static int testCounter = 0;

    @Override
    public void onCreate(final Context context, Intent intent, final MapView view) {
        this.context = context;
        context.setTheme(R.style.ATAKPluginTheme);
        // find out if ssband is enabled from the preferences
        boolean enableSSbandConnection = resolveSSbandConnectionEnabled();

        registerTakSSbandDropDown(context, view);         // register the Drop Down menu for the plugin, where preferences button, heart rate, etc are displayed to the user.
        registerHeartRateCotHandler();
        registerUserDetailsExtension(context, view);
        registerEventController();
        registerPreferences();

        // this is run once once bc onCreate(). Check the license, check if SSband is enabled in prefs.
        ssbandService.addViewContexts(view.getContext(), context);
        if (licenseKeyCheck()) {
            Log.d(TAG, "License Key is valid. Checking if SSband is enabled");
            if (enableSSbandConnection) {
                Log.d(TAG, "SSband is enabled. Initializing Services...");
                TakSSbandMapComponent.getTakSSbandDropDown().displayInfoOnUI("Scanning...");

                ssbandService.initializeSSband();
            } else {
                Log.d(TAG, "SSband Services are disabled in preferences.");
                TakSSbandMapComponent.getTakSSbandDropDown().displayInfoOnUI("SSband Connections Disabled.");

                getTakSSbandDropDown().displayConnectionStatus("SSband Services are Disabled in Preferences");
            }
        } else {
            Log.d(TAG, "License Key is invalid. Will not initialize SSband Services");
            TakSSbandMapComponent.getTakSSbandDropDown().displayInfoOnUI("License Key invalid.");
            ssbandService.disposeSSband();
            getTakSSbandDropDown().displayConnectionStatus("SSband Services are disabled due to License Incompatibility. Check your license in Preferences!");
        }
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        super.onDestroyImpl(context, view);
        takSSbandDropDown.closeDropDown();
        takSSbandDropDown.disposeImpl();
        context.unregisterReceiver(takSSbandEventController);
        CotDetailManager.getInstance().unregisterHandler(heartRateCodHandler);
        ContactLocationView.unregister(extendedUserDetails);
        ssbandService.disposeSSband();
        ToolsPreferenceFragment.unregister(PREFERENCES_KEYS);
    }

    // make SSbandService accessible to SSbandPreferenceFragment so we can start/stop services based on the Connections
    public static SSbandService getSSbandService() {
        return ssbandService;
    }

    public static TakSSbandDropDown getTakSSbandDropDown() {
        return takSSbandDropDown;
    }

    private void registerPreferences() {
        ToolsPreferenceFragment.register(
                new ToolsPreferenceFragment.ToolPreference(
                        context.getResources().getString(R.string.preferences_name),
                        context.getResources().getString(R.string.preferences_description),
                        PREFERENCES_KEYS,
                        context.getResources().getDrawable(R.drawable.slatesafety_shield, null),
                        new SSbandPreferenceFragment(context))
        );
    }

    private static boolean licenseKeyCheck() {
        return SSbandPreferencesResolver.resolveLicenseKeyCheck();
        // check to see if the license is valid so we know whether to initialize SSband services or not.
    }

    // need this for SSband Prefs Fragment listener that restarts ssband services. We need to check the license here as well.
    public static boolean getLicenseKeyCheck() {
        return licenseKeyCheck();
    }

    private void registerUserDetailsExtension(Context context, MapView view) {
        extendedUserDetails = new ExtendedUserDetails(context, view.getContext());
        ContactLocationView.register(extendedUserDetails);
    }

    private void registerHeartRateCotHandler() {
        heartRateCodHandler = new HeartRateCodHandler();
        CotDetailManager.getInstance().registerHandler(heartRateCodHandler);
    }

    private void registerTakSSbandDropDown(Context context, MapView view) {
        takSSbandDropDown = new TakSSbandDropDown(view, context);
        AtakBroadcast.DocumentedIntentFilter ddFilter = new AtakBroadcast.DocumentedIntentFilter();
        ddFilter.addAction(SHOW_DROP_DOWN, "Show plugin's drop-down menu");
        this.registerDropDownReceiver(takSSbandDropDown, ddFilter);
        Log.d(TAG, "Registered TakSSbandDropDown");
    }


    private void registerEventController() {
        takSSbandEventController = new TakSSbandEventController(heartRateCodHandler, takSSbandDropDown);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(HR_RECEIVED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.registerReceiver(takSSbandEventController, intentFilter, RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(takSSbandEventController, intentFilter);
        }
    }

}
