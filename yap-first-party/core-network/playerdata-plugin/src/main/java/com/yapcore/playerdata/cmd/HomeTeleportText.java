package com.yapcore.playerdata.cmd;

import java.util.Locale;

/** Arrival line for {@code /home} so a home named {@code home} is not “home home”. */
public final class HomeTeleportText {

    private HomeTeleportText() {
    }

    public static String arrived(String name) {
        if (name == null || name.isBlank() || "home".equalsIgnoreCase(name.trim())) {
            return "§aTeleported home.";
        }
        return "§aTeleported to home §f" + name.trim().toLowerCase(Locale.ROOT);
    }
}
