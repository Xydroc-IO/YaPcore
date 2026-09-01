package com.yapcore.config;

import java.util.Properties;

/** Shared property parsing for config section helpers. */
public final class ConfigSupport {

    private ConfigSupport() {
    }

    public static int parseInt(Properties props, String key, int fallback) {
        try {
            return Integer.parseInt(props.getProperty(key, Integer.toString(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static long parseLong(Properties props, String key, long fallback) {
        try {
            return Long.parseLong(props.getProperty(key, Long.toString(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
