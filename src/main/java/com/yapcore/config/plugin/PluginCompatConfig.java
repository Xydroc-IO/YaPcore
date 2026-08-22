package com.yapcore.config.plugin;

import java.util.Properties;

/** Paper 1.20–1.21 plugin jar compatibility settings. */
public final class PluginCompatConfig {

    private final Properties props;

    public PluginCompatConfig(Properties props) {
        this.props = props;
    }

    public static void applyDefaults(Properties props) {
        props.setProperty("plugin-compat-enabled", "true");
        props.setProperty("plugin-compat-rewrite", "true");
        props.setProperty("plugin-compat-backup", "true");
    }

    public boolean isPluginCompatEnabled() {
        return Boolean.parseBoolean(props.getProperty("plugin-compat-enabled", "true"));
    }

    public void setPluginCompatEnabled(boolean enabled) {
        props.setProperty("plugin-compat-enabled", Boolean.toString(enabled));
    }

    public boolean isPluginCompatRewrite() {
        return Boolean.parseBoolean(props.getProperty("plugin-compat-rewrite", "true"));
    }

    public void setPluginCompatRewrite(boolean enabled) {
        props.setProperty("plugin-compat-rewrite", Boolean.toString(enabled));
    }

    public boolean isPluginCompatBackup() {
        return Boolean.parseBoolean(props.getProperty("plugin-compat-backup", "true"));
    }

    public void setPluginCompatBackup(boolean enabled) {
        props.setProperty("plugin-compat-backup", Boolean.toString(enabled));
    }
}
