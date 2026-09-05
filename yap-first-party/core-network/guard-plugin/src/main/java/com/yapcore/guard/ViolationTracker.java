package com.yapcore.guard;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ViolationTracker {

    private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();
    private volatile GuardConfig config;

    public ViolationTracker(GuardConfig config) {
        this.config = config;
    }

    public void setConfig(GuardConfig config) {
        this.config = config;
    }

    public int count(UUID uuid) {
        PlayerState state = states.get(uuid);
        return state == null ? 0 : state.violations;
    }

    public void reset(UUID uuid) {
        states.remove(uuid);
    }

    public int recordViolation(Player player, String check) {
        if (player == null) {
            return 0;
        }
        if (player.hasPermission("yapguard.bypass")) {
            return 0;
        }
        PlayerState state = states.computeIfAbsent(player.getUniqueId(), ignored -> new PlayerState());
        long now = System.currentTimeMillis();
        if (now - state.lastViolationMs > config.violationDecaySeconds() * 1000L) {
            state.violations = 0;
        }
        state.violations++;
        state.lastViolationMs = now;
        state.lastCheck = check;
        if (config.alertsEnabled()) {
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("yapguard.alerts")) {
                    staff.sendMessage("§c[YaPGuard] §7" + player.getName() + " §f" + check
                            + " §7(" + state.violations + "/" + config.maxViolationsBeforeKick() + ")");
                }
            }
        }
        return state.violations;
    }

    public PlayerState state(UUID uuid) {
        return states.computeIfAbsent(uuid, ignored -> new PlayerState());
    }

    public void clearOffline(UUID uuid) {
        states.remove(uuid);
    }

    public static final class PlayerState {
        public int violations;
        public long lastViolationMs;
        public String lastCheck = "";
        public long lastMoveMs;
        public double lastX;
        public double lastY;
        public double lastZ;
        public boolean hasLastMove;
        public long scaffoldWindowStartMs;
        public int scaffoldPlaces;
        /** Ignore movement checks until this time (ms) — proxy join / spawn settle. */
        public long joinGraceUntilMs;
        /** Consecutive suspicious fly samples before a violation counts. */
        public int flyAirborneStreak;
        /** Consecutive overspeed samples before a speed violation. */
        public int speedOverStreak;
    }
}
