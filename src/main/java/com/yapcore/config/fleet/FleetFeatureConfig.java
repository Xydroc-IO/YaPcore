package com.yapcore.config.fleet;

import java.util.Properties;

/** Fleet control-plane keys ({@code fleet-enabled}). Default off — single Folia path unchanged. */
public final class FleetFeatureConfig {

    private final Properties props;

    public FleetFeatureConfig(Properties props) {
        this.props = props;
    }

    public static void applyDefaults(Properties props) {
        props.setProperty("fleet-enabled", "false");
        props.setProperty("fleet-primary-id", "lobby");
    }

    public boolean isFleetEnabled() {
        return Boolean.parseBoolean(props.getProperty("fleet-enabled", "false"));
    }

    public void setFleetEnabled(boolean enabled) {
        props.setProperty("fleet-enabled", Boolean.toString(enabled));
    }

    public String getFleetPrimaryId() {
        String v = props.getProperty("fleet-primary-id", "lobby");
        return v == null || v.isBlank() ? "lobby" : v.trim();
    }

    public void setFleetPrimaryId(String id) {
        props.setProperty("fleet-primary-id", id == null || id.isBlank() ? "lobby" : id.trim());
    }
}
