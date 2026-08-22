package com.yapcore.config.core;

import com.yapcore.config.ConfigSupport;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Core server identity, capacity, paths, and access settings. */
public final class CoreServerConfig {

    private final Properties props;

    public CoreServerConfig(Properties props) {
        this.props = props;
    }

    public static void applyDefaults(Properties props) {
        props.setProperty("server-name", "YaPcore");
        props.setProperty("bind-host", "0.0.0.0");
        props.setProperty("port", "25566");
        props.setProperty("max-players", "300");
        props.setProperty("ram-mb", "2048");
        props.setProperty("ram-min-mb", "512");
        props.setProperty("view-distance", "10");
        props.setProperty("motd", "YaPcore · Folia Game · Yap Edge");
        props.setProperty("plugins-dir", "plugins");
        props.setProperty("modules-dir", "modules");
        props.setProperty("logs-dir", "logs");
        props.setProperty("online-mode", "false");
        props.setProperty("auto-op", "false");
        props.setProperty("ops", "");
        props.setProperty("gui-enabled", "true");
        props.setProperty("backwards-compatible", "true");
    }

    public String getServerName() {
        return props.getProperty("server-name", "YaPcore");
    }

    public void setServerName(String value) {
        props.setProperty("server-name", value);
    }

    public String getBindHost() {
        return props.getProperty("bind-host", "0.0.0.0");
    }

    public void setBindHost(String host) {
        props.setProperty("bind-host", host == null || host.isBlank() ? "0.0.0.0" : host.trim());
    }

    public int getPort() {
        return ConfigSupport.parseInt(props, "port", 25566);
    }

    public void setPort(int port) {
        props.setProperty("port", Integer.toString(port));
    }

    public int getMaxPlayers() {
        return Math.max(1, ConfigSupport.parseInt(props, "max-players", 300));
    }

    public void setMaxPlayers(int max) {
        props.setProperty("max-players", Integer.toString(Math.max(1, max)));
    }

    public int getRamMb() {
        return Math.max(256, ConfigSupport.parseInt(props, "ram-mb", 2048));
    }

    public void setRamMb(int mb) {
        props.setProperty("ram-mb", Integer.toString(Math.max(256, mb)));
    }

    public int getRamMinMb() {
        return Math.max(128, Math.min(getRamMb(), ConfigSupport.parseInt(props, "ram-min-mb", 512)));
    }

    public void setRamMinMb(int mb) {
        props.setProperty("ram-min-mb", Integer.toString(Math.max(128, mb)));
    }

    public int getViewDistance() {
        return ConfigSupport.parseInt(props, "view-distance", 10);
    }

    public void setViewDistance(int chunks) {
        props.setProperty("view-distance", Integer.toString(Math.max(2, Math.min(32, chunks))));
    }

    public String getMotd() {
        return props.getProperty("motd", "YaPcore");
    }

    public void setMotd(String motd) {
        props.setProperty("motd", motd);
    }

    public Path getPluginsDir() {
        return Path.of(props.getProperty("plugins-dir", "plugins"));
    }

    public Path getModulesDir() {
        return Path.of(props.getProperty("modules-dir", "modules"));
    }

    public Path getLogsDir() {
        return Path.of(props.getProperty("logs-dir", "logs"));
    }

    public boolean isOnlineMode() {
        return Boolean.parseBoolean(props.getProperty("online-mode", "false"));
    }

    public void setOnlineMode(boolean online) {
        props.setProperty("online-mode", Boolean.toString(online));
    }

    public boolean isAutoOp() {
        return Boolean.parseBoolean(props.getProperty("auto-op", "false"));
    }

    public List<String> getOps() {
        String raw = props.getProperty("ops", "");
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        ArrayList<String> out = new ArrayList<>();
        for (String p : raw.split(",")) {
            String n = p.trim();
            if (!n.isEmpty()) {
                out.add(n);
            }
        }
        return out;
    }

    public boolean isBackwardsCompatible() {
        return Boolean.parseBoolean(props.getProperty("backwards-compatible", "true"));
    }

    public void setBackwardsCompatible(boolean enabled) {
        props.setProperty("backwards-compatible", Boolean.toString(enabled));
    }

    public boolean isGuiEnabled() {
        return Boolean.parseBoolean(props.getProperty("gui-enabled", "true"));
    }

    public void setGuiEnabled(boolean enabled) {
        props.setProperty("gui-enabled", Boolean.toString(enabled));
    }
}
