package com.aitech.atak.ssband.plugin;

import com.atak.plugins.impl.AbstractPlugin;
import gov.tak.api.plugin.IServiceController;
import com.atak.plugins.impl.PluginContextProvider;
import com.aitech.atak.ssband.TakSSbandMapComponent;

public class PluginTemplateLifecycle extends AbstractPlugin {

   public PluginTemplateLifecycle(IServiceController serviceController) {
        super(serviceController, new TakSSbandTool(serviceController.getService(PluginContextProvider.class).getPluginContext()), new TakSSbandMapComponent());
        PluginNativeLoader.init(serviceController.getService(PluginContextProvider.class).getPluginContext());
    }
}

