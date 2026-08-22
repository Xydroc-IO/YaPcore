package com.yapcore.config.authority;

import com.yapcore.config.GameAuthority;

import java.util.Properties;

/** Game authority selection and rank-pack automation. */
public final class GameAuthorityConfig {

    private final Properties props;

    public GameAuthorityConfig(Properties props) {
        this.props = props;
    }

    public static void applyDefaults(Properties props) {
        props.setProperty("game-authority", "folia");
        props.setProperty("yap-ranks-auto-apply", "true");
    }

    public GameAuthority getGameAuthority() {
        String raw = props.getProperty("game-authority");
        if (raw == null || raw.isBlank()) {
            if (Boolean.parseBoolean(props.getProperty("game-kernel-enabled", "false"))) {
                return GameAuthority.MOJANG;
            }
            return GameAuthority.FOLIA;
        }
        return GameAuthority.parse(raw);
    }

    public void setGameAuthority(GameAuthority authority) {
        props.setProperty("game-authority", authority.name().toLowerCase());
    }

    public boolean isNativeAuthority() {
        return getGameAuthority() == GameAuthority.NATIVE;
    }

    public boolean isYapRanksAutoApply() {
        return Boolean.parseBoolean(props.getProperty("yap-ranks-auto-apply", "false"));
    }

    public void setYapRanksAutoApply(boolean enabled) {
        props.setProperty("yap-ranks-auto-apply", Boolean.toString(enabled));
    }
}
