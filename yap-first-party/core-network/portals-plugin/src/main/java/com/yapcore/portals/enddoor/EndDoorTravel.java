package com.yapcore.portals.enddoor;

import com.yapcore.claims.ClaimLookups;
import com.yapcore.portals.PortalsConfig;
import com.yapcore.portals.service.PortalCooldown;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Same-server End hop for lit vertical End doors. */
final class EndDoorTravel {

    private final JavaPlugin plugin;
    private final PortalsConfig config;
    private final PortalCooldown cooldown;

    EndDoorTravel(JavaPlugin plugin, PortalsConfig config, PortalCooldown cooldown) {
        this.plugin = plugin;
        this.config = config;
        this.cooldown = cooldown;
    }

    boolean tryEnter(Player player, Location from) {
        if (!config.endDoorsEnabled()) {
            return false;
        }
        if (!player.hasPermission("yapportals.enddoor.use")
                && !player.hasPermission("yapportals.bypass.permission")) {
            player.sendMessage(config.msgDenied());
            return false;
        }
        Location probe = from != null ? from : player.getLocation();
        if (!ClaimLookups.canUsePortal(player, probe)) {
            player.sendMessage("§cClaimed end portal — only the owner and trusted players can use it.");
            return false;
        }
        long now = System.currentTimeMillis();
        if (!player.hasPermission("yapportals.bypass.cooldown")
                && !cooldown.ready(player.getUniqueId(), now)) {
            int rem = cooldown.remainingSeconds(player.getUniqueId(), now);
            player.sendMessage(config.msgCooldown().replace("{seconds}", String.valueOf(rem)));
            return false;
        }
        World end = resolveEndWorld();
        if (end == null) {
            player.sendMessage("§cThe End is not available on this server.");
            return false;
        }
        Location dest = safeSpawn(end);
        if (dest == null) {
            player.sendMessage("§cCould not find a safe End spawn.");
            return false;
        }
        int cd = config.endDoorCooldownSeconds();
        YapSched.entity(plugin, player, () -> player.teleportAsync(dest).thenAccept(ok -> {
            if (!Boolean.TRUE.equals(ok) || !player.isOnline()) {
                return;
            }
            YapSched.entity(plugin, player, () -> {
                if (!player.isOnline()) {
                    return;
                }
                player.sendMessage("§aEntered The End.");
                cooldown.mark(player.getUniqueId(), cd, System.currentTimeMillis());
                plugin.getLogger().info("End door " + player.getName()
                        + " → " + dest.getWorld().getName());
            });
        }));
        return true;
    }

    World resolveEndWorld() {
        String named = config.endDoorWorld();
        if (named != null && !named.isBlank()) {
            World w = Bukkit.getWorld(named);
            if (w != null && w.getEnvironment() == World.Environment.THE_END) {
                return w;
            }
        }
        for (World w : Bukkit.getWorlds()) {
            if (w.getEnvironment() == World.Environment.THE_END) {
                return w;
            }
        }
        return null;
    }

    private static Location safeSpawn(World end) {
        Location spawn = end.getSpawnLocation();
        if (spawn == null) {
            return null;
        }
        int x = spawn.getBlockX();
        int z = spawn.getBlockZ();
        int y = Math.max(spawn.getBlockY(), end.getMinHeight() + 1);
        int maxY = Math.min(end.getMaxHeight() - 2, y + 64);
        for (int yy = y; yy <= maxY; yy++) {
            Block feet = end.getBlockAt(x, yy, z);
            Block head = end.getBlockAt(x, yy + 1, z);
            Block below = end.getBlockAt(x, yy - 1, z);
            if (isPassable(feet) && isPassable(head) && below.getType().isSolid()) {
                Location loc = below.getRelative(BlockFace.UP).getLocation().add(0.5, 0.0, 0.5);
                loc.setYaw(spawn.getYaw());
                loc.setPitch(0f);
                return loc;
            }
        }
        try {
            return spawn.clone();
        } catch (Exception e) {
            return spawn;
        }
    }

    private static boolean isPassable(Block b) {
        Material t = b.getType();
        return t.isAir() || !t.isSolid();
    }
}
