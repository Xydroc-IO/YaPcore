package com.yapcore.sched;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/**
 * Unified staff / creative bypass for YaP first-party rules.
 * <ul>
 *   <li>{@link #staff} — OP, {@code yap.bypass}, or playerdata admin</li>
 *   <li>{@link #creative} — creative/spectator</li>
 *   <li>{@link #mmo} — skip skills XP (and legacy MMO-named bypass nodes)</li>
 *   <li>{@link #land} — skip claim/region build/access gates</li>
 * </ul>
 */
public final class StaffBypass {

    public static final String ALL = "yap.bypass";
    /** Preferred skills bypass. */
    public static final String SKILLS = "yapskills.bypass";
    /** Legacy alias — still honored. */
    public static final String MMO = "yap.bypass.mmo";

    private StaffBypass() {
    }

    public static boolean creative(Player player) {
        GameMode mode = player.getGameMode();
        return mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
    }

    /** Admins, OPs, and explicit {@code yap.bypass}. */
    public static boolean staff(Player player) {
        return player.isOp()
                || player.hasPermission(ALL)
                || player.hasPermission("yapdata.admin");
    }

    /** Land protection (claims / admin regions). */
    public static boolean land(Player player) {
        return creative(player)
                || staff(player)
                || player.hasPermission("yapdata.claims.admin")
                || player.hasPermission("yapregions.admin");
    }

    /** Skills XP (and any leftover MMO-named bypass grants). */
    public static boolean mmo(Player player) {
        return creative(player)
                || staff(player)
                || player.hasPermission(SKILLS)
                || player.hasPermission(MMO)
                || player.hasPermission("yapskills.admin");
    }

    /** Anti-cheat and similar enforcement. */
    public static boolean guard(Player player) {
        return creative(player)
                || staff(player)
                || player.hasPermission("yapguard.bypass");
    }

    /** Lag budgets near the player (redstone / hoppers / spawn). */
    public static boolean lag(Player player) {
        return creative(player)
                || staff(player)
                || player.hasPermission("yaplagguard.bypass");
    }

    /** Chat filter / slow mode. */
    public static boolean chat(Player player) {
        return staff(player)
                || player.hasPermission("yapchat.bypass.filter")
                || player.hasPermission("yapchat.bypass.slow")
                || player.hasPermission("yapchat.admin");
    }
}
