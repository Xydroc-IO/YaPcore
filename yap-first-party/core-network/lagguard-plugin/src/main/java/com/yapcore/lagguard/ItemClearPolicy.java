package com.yapcore.lagguard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure ClearLagg-style item-clear policy (countdown + world filters + message templates).
 */
public final class ItemClearPolicy {

    private ItemClearPolicy() {
    }

    /**
     * Whether this world is in scope. Empty allow-list means all worlds;
     * blacklist always wins.
     */
    public static boolean worldAllowed(String world, List<String> allow, List<String> deny) {
        if (world == null || world.isBlank()) {
            return false;
        }
        String key = world.toLowerCase(Locale.ROOT);
        if (deny != null) {
            for (String d : deny) {
                if (d != null && key.equals(d.toLowerCase(Locale.ROOT))) {
                    return false;
                }
            }
        }
        if (allow == null || allow.isEmpty()) {
            return true;
        }
        for (String a : allow) {
            if (a != null && key.equals(a.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Seconds remaining that should fire a warn broadcast, or empty if none.
     * Matches exact remaining seconds against the configured warn list.
     */
    public static boolean shouldWarnAt(int secondsRemaining, List<Integer> warnSeconds) {
        if (secondsRemaining <= 0 || warnSeconds == null || warnSeconds.isEmpty()) {
            return false;
        }
        for (Integer w : warnSeconds) {
            if (w != null && w == secondsRemaining) {
                return true;
            }
        }
        return false;
    }

    /** Normalize warn seconds: positive, unique, descending. */
    public static List<Integer> normalizeWarnSeconds(List<Integer> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of(60, 30, 10);
        }
        List<Integer> out = new ArrayList<>();
        for (Integer v : raw) {
            if (v == null || v <= 0) {
                continue;
            }
            if (!out.contains(v)) {
                out.add(v);
            }
        }
        out.sort((a, b) -> Integer.compare(b, a));
        return List.copyOf(out);
    }

    public static String format(String template, int seconds, int items, int xp) {
        String t = template == null ? "" : template;
        return t.replace("{seconds}", Integer.toString(seconds))
                .replace("{items}", Integer.toString(items))
                .replace("{xp}", Integer.toString(xp));
    }

    /** True when the ground item is old enough to clear. */
    public static boolean oldEnough(int ticksLived, int minAgeTicks) {
        return ticksLived >= Math.max(0, minAgeTicks);
    }
}
