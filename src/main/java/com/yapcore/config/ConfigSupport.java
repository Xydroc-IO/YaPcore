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
}
