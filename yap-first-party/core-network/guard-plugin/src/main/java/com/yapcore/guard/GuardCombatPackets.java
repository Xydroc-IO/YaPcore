package com.yapcore.guard;

import com.yapcore.lib.packet.PacketAdapter;
import com.yapcore.lib.packet.PacketEvent;
import com.yapcore.lib.packet.PacketThreadMode;
import com.yapcore.lib.packet.PacketTypes;
import com.yapcore.sched.StaffBypass;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Packet reach (interact) + scaffold rate. Speed is {@link GuardSpeedPackets}. */
public final class GuardCombatPackets extends PacketAdapter {

    private final GuardPlugin plugin;

    public GuardCombatPackets(GuardPlugin plugin) {
        super(plugin, com.yapcore.lib.packet.PacketPriority.LOW, PacketThreadMode.REGION,
                PacketTypes.Play.Client.INTERACT, PacketTypes.Play.Client.USE_ENTITY);
        this.plugin = plugin;
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        Player player = event.player();
        GuardConfig config = plugin.guardConfig();
        if (player == null || !config.reachEnabled() || StaffBypass.guard(player)) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        UUID id = player.getUniqueId();
        ViolationTracker.PlayerState state = plugin.tracker().state(id);
        if (System.currentTimeMillis() < state.joinGraceUntilMs) {
            return;
        }
        Integer entityId = event.packet().readOrNull(Integer.class, 0);
        if (entityId == null) {
            entityId = event.packet().readOrNull(int.class, 0);
        }
        if (entityId == null) {
            return;
        }
        Entity victim = entityById(player, entityId);
        if (victim == null || victim.equals(player)) {
            return;
        }
        double max = config.maxReachDistance() + (1.0 - config.reachSensitivity()) * 1.5;
        double dist = player.getLocation().distance(victim.getLocation());
        if (dist <= max) {
            return;
        }
        if (!GuardHeuristics.shouldFlagSample(true, config.reachSensitivity(), config.sampleRandomly())) {
            return;
        }
        event.setCancelled(true);
        plugin.flag(player, "reach");
    }

    static Entity entityById(Player player, int entityId) {
        if (player.getEntityId() == entityId) {
            return player;
        }
        for (Entity entity : player.getNearbyEntities(16, 16, 16)) {
            if (entity.getEntityId() == entityId) {
                return entity;
            }
        }
        return null;
    }
}
