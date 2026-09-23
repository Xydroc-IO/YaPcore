package com.yapcore.portals.enddoor;

import com.yapcore.claims.ClaimLookups;
import com.yapcore.portals.PortalsConfig;
import com.yapcore.portals.service.PortalCooldown;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/** Same-server End hop for lit vertical End doors. */
final class EndDoorTravel {

    private final JavaPlugin plugin;
    private final PortalsConfig config;
    private final PortalCooldown cooldown;
    private final AtomicBoolean creatingEnd = new AtomicBoolean(false);

    EndDoorTravel(JavaPlugin plugin, PortalsConfig config, PortalCooldown cooldown) {
        this.plugin = plugin;
        this.config = config;
        this.cooldown = cooldown;
    }

    /** Deferred so Folia / YaPWorld finish enabling first. */
    void ensureEndWorldAsync() {
        YapSched.globalLater(plugin, () -> {
            if (resolveEndWorld() != null) {
                World end = resolveEndWorld();
                plugin.getLogger().info("End door: End world already loaded → "
                        + end.getName() + " env=" + end.getEnvironment()
                        + " worlds=" + Bukkit.getWorlds().stream()
                        .map(w -> w.getName() + "/" + w.getEnvironment())
                        .reduce((a, b) -> a + "," + b).orElse(""));
                return;
            }
            requestCreateEnd(null);
        }, 60L);
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
            player.sendMessage("§eCreating The End… §7walk through again in a few seconds.");
            requestCreateEnd(player);
            return false;
        }
        // Folia: End block reads must run on an End-region thread, not the overworld entity thread.
        Location spawnHint = end.getSpawnLocation();
        int sx = spawnHint != null ? spawnHint.getBlockX() : 0;
        int sz = spawnHint != null ? spawnHint.getBlockZ() : 0;
        int cd = config.endDoorCooldownSeconds();
        player.sendMessage("§7Entering The End…");
        YapSched.region(plugin, end, sx, sz, () -> {
            try {
                Location dest = safeSpawn(end);
                if (dest == null) {
                    YapSched.entity(plugin, player, () -> {
                        if (player.isOnline()) {
                            player.sendMessage("§cCould not find a safe End spawn.");
                        }
                    });
                    return;
                }
                plugin.getLogger().info("End door travel " + player.getName() + " → "
                        + dest.getWorld().getName()
                        + " " + dest.getBlockX() + "," + dest.getBlockY() + "," + dest.getBlockZ());
                player.teleportAsync(dest).thenAccept(ok -> YapSched.entity(plugin, player, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (!Boolean.TRUE.equals(ok)) {
                        plugin.getLogger().warning("End door teleport failed for "
                                + player.getName() + " ok=" + ok);
                        player.sendMessage("§cEnd door teleport failed — try again.");
                        return;
                    }
                    player.sendMessage("§aEntered The End.");
                    cooldown.mark(player.getUniqueId(), cd, System.currentTimeMillis());
                }));
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "End door travel failed for " + player.getName(), e);
                YapSched.entity(plugin, player, () -> {
                    if (player.isOnline()) {
                        player.sendMessage("§cEnd door teleport failed — try again.");
                    }
                });
            }
        });
        return true;
    }

    private void requestCreateEnd(Player notify) {
        if (!creatingEnd.compareAndSet(false, true)) {
            return;
        }
        String name = config.endDoorWorld();
        if (name == null || name.isBlank()) {
            name = "world_the_end";
        }
        final String worldName = name;
        plugin.getLogger().info("End door: creating missing End world '" + worldName + "'");
        // Prefer YaPWorld via reflection so yap-world-api need not be on the portals classpath at runtime
        if (tryCreateViaYapWorld(worldName, notify)) {
            return;
        }
        createEndVanilla(worldName, notify);
    }

    private boolean tryCreateViaYapWorld(String worldName, Player notify) {
        try {
            Class<?> services = Class.forName("com.yapcore.world.WorldServices");
            Object opt = services.getMethod("worldManager").invoke(null);
            boolean present = (Boolean) opt.getClass().getMethod("isPresent").invoke(opt);
            if (!present) {
                return false;
            }
            Object wm = opt.getClass().getMethod("get").invoke(opt);
            Class<?> optsCl = Class.forName("com.yapcore.world.WorldCreateOptions");
            Object builder = optsCl.getMethod("builder").invoke(null);
            builder.getClass().getMethod("environment", String.class).invoke(builder, "THE_END");
            builder.getClass().getMethod("type", String.class).invoke(builder, "NORMAL");
            builder.getClass().getMethod("generateStructures", boolean.class).invoke(builder, true);
            Object opts = builder.getClass().getMethod("build").invoke(builder);
            Object future = wm.getClass()
                    .getMethod("createWorld", String.class, optsCl)
                    .invoke(wm, worldName, opts);
            future.getClass().getMethod("whenComplete", java.util.function.BiConsumer.class)
                    .invoke(future, (java.util.function.BiConsumer<Object, Throwable>) (ok, err) -> {
                        creatingEnd.set(false);
                        if (err != null) {
                            plugin.getLogger().log(Level.WARNING, "YaPWorld failed to create End", err);
                            createEndVanilla(worldName, notify);
                            return;
                        }
                        if (!Boolean.TRUE.equals(ok)) {
                            createEndVanilla(worldName, notify);
                            return;
                        }
                        plugin.getLogger().info("End world ready via YaPWorld: " + worldName);
                        if (notify != null && notify.isOnline()) {
                            YapSched.entity(plugin, notify, () ->
                                    notify.sendMessage("§aThe End is ready — walk through the End door again."));
                        }
                    });
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "YaPWorld End create reflection failed", e);
            return false;
        }
    }

    private void createEndVanilla(String worldName, Player notify) {
        YapSched.global(plugin, () -> {
            try {
                if (Bukkit.getWorld(worldName) != null) {
                    creatingEnd.set(false);
                    return;
                }
                WorldCreator creator = WorldCreator.name(worldName)
                        .environment(World.Environment.THE_END);
                World world = Bukkit.createWorld(creator);
                creatingEnd.set(false);
                if (world == null) {
                    plugin.getLogger().severe("Bukkit.createWorld returned null for " + worldName);
                    if (notify != null && notify.isOnline()) {
                        notify.sendMessage("§cCould not create The End on this server.");
                    }
                    return;
                }
                plugin.getLogger().info("End world created: " + world.getName());
                if (notify != null && notify.isOnline()) {
                    notify.sendMessage("§aThe End is ready — walk through the End door again.");
                }
            } catch (Exception e) {
                creatingEnd.set(false);
                plugin.getLogger().log(Level.SEVERE, "Failed creating End world", e);
                if (notify != null && notify.isOnline()) {
                    notify.sendMessage("§cCould not create The End: " + e.getMessage());
                }
            }
        });
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
