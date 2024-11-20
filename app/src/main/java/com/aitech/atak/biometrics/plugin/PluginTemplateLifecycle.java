/*
 * Copyright (C) 2024, Ascent Integrated Tech. All rights reserved.
 *
 * The copyright to the computer software herein is the property of Ascent Integrated Tech.
 * This software may not be used and/or copied without the explicit written permission
 * of Ascent Integrated Tech, and only in accordance with the terms and conditions stipulated
 * in the license agreement and/or contract under which the software has been supplied.
 */

package com.aitech.atak.biometrics.plugin;

import com.atak.plugins.impl.AbstractPlugin;
import gov.tak.api.plugin.IServiceController;
import com.atak.plugins.impl.PluginContextProvider;
import com.aitech.atak.biometrics.TakBiometricsMapComponent;

public class PluginTemplateLifecycle extends AbstractPlugin {

   public PluginTemplateLifecycle(IServiceController serviceController) {
        super(serviceController, new TakBiometricsTool(serviceController.getService(PluginContextProvider.class).getPluginContext()), new TakBiometricsMapComponent());
        PluginNativeLoader.init(serviceController.getService(PluginContextProvider.class).getPluginContext());
    }
}

