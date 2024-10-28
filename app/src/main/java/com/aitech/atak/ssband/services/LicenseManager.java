package com.aitech.atak.ssband.services;

import com.aitech.atak.ssband.preferences.SSbandPreferences;

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
