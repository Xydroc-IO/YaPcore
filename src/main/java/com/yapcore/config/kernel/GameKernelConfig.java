package com.yapcore.config.kernel;

import com.yapcore.config.ConfigSupport;
import com.yapcore.config.GameAuthority;
import com.yapcore.config.ServerConfig;

import java.util.Properties;

/** Legacy Mojang dedicated-server kernel settings. */
public final class GameKernelConfig {

    private final ServerConfig config;
    private final Properties props;

    public GameKernelConfig(ServerConfig config, Properties props) {
        this.config = config;
        this.props = props;
    }

    public static void applyDefaults(Properties props) {
        props.setProperty("game-kernel-enabled", "false");
        props.setProperty("game-kernel-dir", "game-kernel");
        props.setProperty("game-kernel-port", "25567");
        props.setProperty("game-kernel-version", "26.2");
        props.setProperty("game-kernel-jar-url", "");
        props.setProperty("game-kernel-ready-timeout-sec", "180");
    }

    public boolean isGameKernelEnabled() {
        return config.getGameAuthority() == GameAuthority.MOJANG;
    }

    public void setGameKernelEnabled(boolean enabled) {
        props.setProperty("game-kernel-enabled", Boolean.toString(enabled));
    }

    public String getGameKernelDir() {
        return props.getProperty("game-kernel-dir", "game-kernel");
    }

    public int getGameKernelPort() {
        return ConfigSupport.parseInt(props, "game-kernel-port", 25567);
    }

    public String getGameKernelVersion() {
        return props.getProperty("game-kernel-version", "26.2");
    }

    public String getGameKernelJarUrl() {
        return props.getProperty("game-kernel-jar-url", "");
    }

    public int getGameKernelReadyTimeoutSec() {
        return ConfigSupport.parseInt(props, "game-kernel-ready-timeout-sec", 180);
    }
}
