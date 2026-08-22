package com.yapcore.config.web;

import com.yapcore.config.ConfigSupport;

import java.util.Properties;

/** Headless web control dashboard settings. */
public final class WebDashboardConfig {

    private final Properties props;

    public WebDashboardConfig(Properties props) {
        this.props = props;
    }

    public static void applyDefaults(Properties props) {
        props.setProperty("web-dashboard-enabled", "true");
        props.setProperty("web-dashboard-port", "8080");
        props.setProperty("web-dashboard-bind", "127.0.0.1");
        props.setProperty("web-dashboard-token", "");
        props.setProperty("web-dashboard-localhost-only", "true");
    }

    public boolean isWebDashboardEnabled() {
        // MSPT benches: no dashboard bind (8080) and avoid JLine stdin when headless.
        String bench = System.getProperty("yap.bench.scenario");
        if (bench != null && !bench.isBlank()) {
            return false;
        }
        return Boolean.parseBoolean(props.getProperty("web-dashboard-enabled", "true"));
    }

    public void setWebDashboardEnabled(boolean enabled) {
        props.setProperty("web-dashboard-enabled", Boolean.toString(enabled));
    }

    public int getWebDashboardPort() {
        return ConfigSupport.parseInt(props, "web-dashboard-port", 8080);
    }

    public void setWebDashboardPort(int port) {
        props.setProperty("web-dashboard-port", Integer.toString(port));
    }

    public String getWebDashboardBind() {
        return props.getProperty("web-dashboard-bind", "127.0.0.1");
    }

    public void setWebDashboardBind(String bind) {
        props.setProperty("web-dashboard-bind", bind == null ? "127.0.0.1" : bind);
    }

    public String getWebDashboardToken() {
        return props.getProperty("web-dashboard-token", "");
    }

    public void setWebDashboardToken(String token) {
        props.setProperty("web-dashboard-token", token == null ? "" : token);
    }

    public boolean isWebDashboardLocalhostOnly() {
        return Boolean.parseBoolean(props.getProperty("web-dashboard-localhost-only", "true"));
    }

    public void setWebDashboardLocalhostOnly(boolean localhostOnly) {
        props.setProperty("web-dashboard-localhost-only", Boolean.toString(localhostOnly));
    }
}
