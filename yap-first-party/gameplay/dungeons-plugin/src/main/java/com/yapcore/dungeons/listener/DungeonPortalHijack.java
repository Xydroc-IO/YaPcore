package com.yapcore.dungeons.listener;

import com.yapcore.claims.ClaimLookups;
import com.yapcore.dungeons.DungeonsConfig;
import com.yapcore.dungeons.gui.DungeonMenu;
import com.yapcore.dungeons.portal.DungeonPortalRegistry;
import com.yapcore.dungeons.portal.PortalStructure;
import com.yapcore.dungeons.portal.PortalStructureTags;
import com.yapcore.dungeons.service.DungeonInstanceManager;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Walk-through dungeon frame detection and nether-portal hijack. */
final class DungeonPortalHijack {

    private final JavaPlugin plugin;
    private final DungeonsConfig config;
    private final PortalStructure structure;
    private final PortalStructureTags structureTags;
    private final DungeonMenu menu;
    private final DungeonInstanceManager instances;
    private final Map<UUID, Long> recentEnterMs = new ConcurrentHashMap<>();

    DungeonPortalHijack(
            JavaPlugin plugin,
            DungeonsConfig config,
            PortalStructure structure,
            PortalStructureTags structureTags,
            DungeonMenu menu,
            DungeonInstanceManager instances) {
        this.plugin = plugin;
        this.config = config;
        this.structure = structure;
        this.structureTags = structureTags;
        this.menu = menu;
        this.instances = instances;
    }

    Map<UUID, Long> recentEnterMs() {
        return recentEnterMs;
    }

    static boolean denyClaimedPortal(Player player, Location location) {
        if (ClaimLookups.canUsePortal(player, location)) {
            return false;
        }
        player.sendMessage("§cClaimed dungeon portal — only the owner and trusted players can use it.");
        return true;
    }

    boolean hijackDungeonPortal(
            Player player, Location from, PlayerTeleportEvent.TeleportCause cause) {
        if (!config.enabled() || !config.structureEnabled()) {
            return false;
        }
        Location probe = from != null ? from : player.getLocation();
        Optional<PortalStructure.Frame> frame = dungeonFrameAt(probe);
        if (frame.isEmpty()) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long prev = recentEnterMs.get(player.getUniqueId());
        if (prev != null && now - prev < 1500L) {
            return true;
        }
        recentEnterMs.put(player.getUniqueId(), now);
        if (denyClaimedPortal(player, probe)) {
            return true;
        }
        boolean keyed = DungeonPortalRegistry.at(probe).isPresent()
                || structureTags.isKeystone(frame.get().keystone())
                || structureTags.findNearbyKeystone(frame.get().keystone(), 1).isPresent();
        if (keyed) {
            structure.ensureWalkable(frame.get());
            DungeonPortalRegistry.register(frame.get());
        }
        if (!keyed) {
            player.sendMessage("§eDungeon frame detected. §7Right-click the frame with an §fEnder Eye §7to activate.");
            return true;
        }
        if (instances.byPlayer(player.getUniqueId()).isPresent()) {
            player.sendMessage("§cYou are already in a dungeon. Use §e/dungeon leave §cfirst.");
            return true;
        }
        plugin.getLogger().info("Dungeon portal walk-in " + player.getName()
                + " at " + probe.getBlockX() + "," + probe.getBlockY() + "," + probe.getBlockZ());
        player.sendMessage("§7Opening dungeon menu…");
        menu.open(player, 0);
        return true;
    }

    Optional<PortalStructure.Frame> dungeonFrameAt(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return Optional.empty();
        }
        Optional<PortalStructure.Frame> registered = DungeonPortalRegistry.at(loc);
        if (registered.isPresent()) {
            return registered;
        }
        Block block = loc.getBlock();
        int radius = Math.max(structure.outerWidth(), structure.outerHeight());
        Optional<Block> keystone = structureTags.findNearbyKeystone(block, radius);
        if (keystone.isEmpty()) {
            keystone = structureTags.findNearbyKeystone(block.getRelative(0, -1, 0), radius);
        }
        if (keystone.isPresent()) {
            Optional<PortalStructure.Frame> fromKey = structureTags.frameFromKeystone(keystone.get());
            if (fromKey.isPresent()
                    && (structure.contains(fromKey.get(), block, 4)
                    || structure.contains(fromKey.get(), block.getRelative(0, -1, 0), 4))) {
                DungeonPortalRegistry.register(fromKey.get());
                return fromKey;
            }
        }
        Optional<PortalStructure.Frame> around = structure.findFrameContaining(block);
        if (around.isPresent()) {
            return around;
        }
        return structure.findFrameContaining(block.getRelative(0, -1, 0));
    }
}
