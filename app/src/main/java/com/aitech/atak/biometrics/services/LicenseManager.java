/*
 * Copyright (C) 2024, Ascent Integrated Tech. All rights reserved.
 *
 * The copyright to the computer software herein is the property of Ascent Integrated Tech.
 * This software may not be used and/or copied without the explicit written permission
 * of Ascent Integrated Tech, and only in accordance with the terms and conditions stipulated
 * in the license agreement and/or contract under which the software has been supplied.
 */

package com.aitech.atak.biometrics.services;

public class LicenseManager {

    // A static boolean to hold the license validity status
    private static boolean isLicenseValid = false;

    // Public method to check if the license is valid
    public static boolean isLicenseValid() {
        return isLicenseValid;
    }

    // Public method to set the license validity status
    public static void setLicenseValid(boolean licenseValid) {
        isLicenseValid = licenseValid;
    }

    // Optionally, you can add logic here to validate the license as needed
    public static void validateLicense(String licenseKey) {
        // Logic to validate the license key
        if (licenseKey.equals("AscentFreeLicense")) {
            setLicenseValid(true);
        } else {
            setLicenseValid(false);
        }
    }
}
