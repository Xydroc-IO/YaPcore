package com.yapcore.playerdata.kit;

/** Human cooldown text for kit GUI / messages. */
public final class CooldownFormat {

    private CooldownFormat() {
    }

    public static String formatSeconds(long seconds) {
        if (seconds <= 0) {
            return "ready";
        }
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) {
            sb.append(d).append('d');
        }
        if (h > 0) {
            sb.append(h).append('h');
        }
        if (m > 0) {
            sb.append(m).append('m');
        }
        if (s > 0 || sb.isEmpty()) {
            sb.append(s).append('s');
        }
        return sb.toString();
    }
}
