package com.yapcore.guard;

import com.yapcore.lib.packet.PacketAdapter;
import com.yapcore.lib.packet.PacketEvent;
import com.yapcore.lib.packet.PacketTypes;
import com.yapcore.sched.StaffBypass;
import com.yapcore.sched.YapSched;
import org.bukkit.entity.Player;

/** Packet place-rate scaffold (Netty cancel). */
public final class GuardScaffoldPackets extends PacketAdapter {

    private final GuardPlugin plugin;

    public GuardScaffoldPackets(GuardPlugin plugin) {
        super(plugin, PacketTypes.Play.Client.USE_ITEM_ON, PacketTypes.Play.Client.BLOCK_PLACE);
        this.plugin = plugin;
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        Player player = event.player();
        GuardConfig config = plugin.guardConfig();
        if (player == null || !config.scaffoldEnabled() || StaffBypass.guard(player)) {
            return;
        }
        ViolationTracker.PlayerState state = plugin.tracker().state(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (now < state.joinGraceUntilMs) {
            return;
        }
        if (now - state.scaffoldWindowStartMs > 1000L) {
            state.scaffoldWindowStartMs = now;
            state.scaffoldPlaces = 0;
        }
        state.scaffoldPlaces++;
        int limit = (int) Math.ceil(config.maxPlacesPerSecond() * (0.5 + config.scaffoldSensitivity()));
        if (state.scaffoldPlaces <= limit) {
            return;
        }
        if (!GuardHeuristics.shouldFlagSample(true, config.scaffoldSensitivity(), config.sampleRandomly())) {
            return;
        }
        event.setCancelled(true);
        YapSched.entity(plugin, player, () -> plugin.flag(player, "scaffold"));
    }
}
