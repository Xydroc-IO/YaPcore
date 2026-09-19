package com.yapcore.guard;

import com.yapcore.lib.packet.PacketAdapter;
import com.yapcore.lib.packet.PacketEvent;
import com.yapcore.lib.packet.PacketTypes;
import com.yapcore.sched.StaffBypass;
import com.yapcore.sched.YapSched;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/**
 * Movement speed from move packets (Netty). Flags on the entity thread; does not
 * cancel (no simulation setback). Fly stays on the Bukkit timer (needs blocks).
 */
public final class GuardSpeedPackets extends PacketAdapter {

    private final GuardPlugin plugin;

    public GuardSpeedPackets(GuardPlugin plugin) {
        super(plugin, PacketTypes.Play.Client.MOVE_PLAYER_POS, PacketTypes.Play.Client.MOVE_PLAYER_POS_ROT,
                PacketTypes.Play.Client.POSITION, PacketTypes.Play.Client.POSITION_LOOK);
        this.plugin = plugin;
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        Player player = event.player();
        GuardConfig config = plugin.guardConfig();
        if (player == null || !config.speedEnabled() || StaffBypass.guard(player)) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        Double x = event.packet().readOrNull(Double.class, 0);
        Double y = event.packet().readOrNull(Double.class, 1);
        Double z = event.packet().readOrNull(Double.class, 2);
        if (x == null || y == null || z == null) {
            return;
        }
        ViolationTracker.PlayerState state = plugin.tracker().state(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (now < state.joinGraceUntilMs) {
            state.lastX = x;
            state.lastY = y;
            state.lastZ = z;
            state.lastMoveMs = now;
            state.hasLastMove = true;
            return;
        }
        if (!state.hasLastMove) {
            state.hasLastMove = true;
            state.lastX = x;
            state.lastY = y;
            state.lastZ = z;
            state.lastMoveMs = now;
            return;
        }
        long elapsed = Math.max(1L, now - state.lastMoveMs);
        double dx = x - state.lastX;
        double dy = y - state.lastY;
        double dz = z - state.lastZ;
        state.lastX = x;
        state.lastY = y;
        state.lastZ = z;
        state.lastMoveMs = now;
        if (player.isGliding() || player.isInsideVehicle() || player.isRiptiding()) {
            state.speedOverStreak = 0;
            return;
        }
        double blocksPerTick = Math.sqrt(dx * dx + dy * dy + dz * dz) / (elapsed / 50.0);
        double allowed = GuardHeuristics.speedAllowedBlocksPerTick(
                config.maxBlocksPerTick(), config.speedSensitivity(), player.isSprinting(), false);
        if (blocksPerTick <= allowed) {
            state.speedOverStreak = 0;
            return;
        }
        state.speedOverStreak++;
        if (state.speedOverStreak < config.speedConsecutiveHits()) {
            return;
        }
        if (!GuardHeuristics.shouldFlagSample(true, config.speedSensitivity(), config.sampleRandomly())) {
            return;
        }
        state.speedOverStreak = 0;
        YapSched.entity(plugin, player, () -> plugin.flag(player, "speed"));
    }
}
