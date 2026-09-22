package com.yapcore.portals.service;

import com.yapcore.portals.Portal;
import com.yapcore.portals.PortalsConfig;
import com.yapcore.sched.YapSched;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.logging.Level;

/** Same-server portal pads (spawn / RTP / home) without Link Connect. */
final class PortalLocalTransferOps {

    private final JavaPlugin plugin;
    private final PortalsConfig config;
    private final PortalCooldown cooldown;
    private final PortalVisuals visuals;
    private final PortalServiceImpl host;

    PortalLocalTransferOps(PortalServiceImpl host) {
        this.host = host;
        this.plugin = host.plugin();
        this.config = host.config();
        this.cooldown = host.cooldown();
        this.visuals = host.visuals();
    }

    /** Same-server spawn pad: walk in → Essentials/world spawn without Link Connect. */
    boolean queueLocalSpawn(Player player, int cooldownSec, String customMsg, Portal portal) {
        long now = System.currentTimeMillis();
        if (!player.hasPermission("yapportals.bypass.cooldown")
                && !cooldown.ready(player.getUniqueId(), now)) {
            int rem = cooldown.remainingSeconds(player.getUniqueId(), now);
            player.sendMessage(config.msgCooldown().replace("{seconds}", String.valueOf(rem)));
            return false;
        }
        Location spawn = resolveSpawn(player);
        if (spawn == null || spawn.getWorld() == null) {
            player.sendMessage("§cNo spawn set on this server.");
            return false;
        }
        YapSched.entity(plugin, player, () -> {
            try {
                if (portal != null) {
                    visuals.playEnter(player, portal);
                }
            } catch (Exception fx) {
                plugin.getLogger().log(Level.FINE, "portal enter FX", fx);
            }
            player.teleportAsync(spawn).thenAccept(ok -> {
                if (!Boolean.TRUE.equals(ok) || !player.isOnline()) {
                    return;
                }
                YapSched.entity(plugin, player, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (customMsg != null && !customMsg.isBlank()) {
                        player.sendMessage(customMsg.replace("{server}", config.serverId()));
                    } else {
                        player.sendMessage("§aWarped to spawn.");
                    }
                    visuals.playArrive(player);
                    host.armJoinGrace(player.getUniqueId());
                    host.seedInsideFromLocation(player);
                    cooldown.mark(player.getUniqueId(), cooldownSec, System.currentTimeMillis());
                    plugin.getLogger().info("Local spawn portal " + player.getName()
                            + (portal == null ? "" : " via " + portal.name()));
                });
            });
        });
        return true;
    }

    /** Same-server wild pad: walk in → RTP without Link Connect. */
    boolean queueLocalRtp(Player player, int cooldownSec, String customMsg, Portal portal) {
        long now = System.currentTimeMillis();
        if (!player.hasPermission("yapportals.bypass.cooldown")
                && !cooldown.ready(player.getUniqueId(), now)) {
            int rem = cooldown.remainingSeconds(player.getUniqueId(), now);
            player.sendMessage(config.msgCooldown().replace("{seconds}", String.valueOf(rem)));
            return false;
        }
        YapSched.entity(plugin, player, () -> {
            try {
                if (portal != null) {
                    visuals.playEnter(player, portal);
                }
            } catch (Exception fx) {
                plugin.getLogger().log(Level.FINE, "portal enter FX", fx);
            }
            if (!tryEssentialsRtp(player)) {
                player.sendMessage("§cWild portal needs YaPEssentials RTP on this server.");
                return;
            }
            if (customMsg != null && !customMsg.isBlank()) {
                player.sendMessage(customMsg.replace("{server}", config.serverId()));
            }
            cooldown.mark(player.getUniqueId(), cooldownSec, System.currentTimeMillis());
            plugin.getLogger().info("Local RTP portal " + player.getName()
                    + (portal == null ? "" : " via " + portal.name()));
        });
        return true;
    }

    /** Same-server home pad: walk in → player's set home without Link Connect. */
    boolean queueLocalHome(Player player, int cooldownSec, String customMsg, Portal portal) {
        long now = System.currentTimeMillis();
        if (!player.hasPermission("yapportals.bypass.cooldown")
                && !cooldown.ready(player.getUniqueId(), now)) {
            int rem = cooldown.remainingSeconds(player.getUniqueId(), now);
            player.sendMessage(config.msgCooldown().replace("{seconds}", String.valueOf(rem)));
            return false;
        }
        String homeName = portal == null ? "home" : portal.homeName();
        YapSched.entity(plugin, player, () -> {
            try {
                if (portal != null) {
                    visuals.playEnter(player, portal);
                }
            } catch (Exception fx) {
                plugin.getLogger().log(Level.FINE, "portal enter FX", fx);
            }
            if (!tryPlayerHome(player, homeName)) {
                player.sendMessage("§cNo home §f" + homeName
                        + "§c set — use §f/sethome§c (needs YaPPlayerData homes).");
                return;
            }
            if (customMsg != null && !customMsg.isBlank()) {
                player.sendMessage(customMsg.replace("{server}", config.serverId()));
            } else {
                player.sendMessage("§aWarped home §f" + homeName + "§a.");
            }
            cooldown.mark(player.getUniqueId(), cooldownSec, System.currentTimeMillis());
            host.armJoinGrace(player.getUniqueId());
            host.seedInsideFromLocation(player);
            plugin.getLogger().info("Local home portal " + player.getName() + " → " + homeName
                    + (portal == null ? "" : " via " + portal.name()));
        });
        return true;
    }

    private boolean tryPlayerHome(Player player, String homeName) {
        try {
            Class<?> provider = Class.forName("com.yapcore.playerdata.HomeAccessProvider");
            Object opt = provider.getMethod("find").invoke(null);
            if (!(opt instanceof Optional<?> optional) || optional.isEmpty()) {
                return false;
            }
            Object access = optional.get();
            Object result = access.getClass()
                    .getMethod("teleportHome", Player.class, String.class)
                    .invoke(access, player, homeName == null ? "home" : homeName);
            return result instanceof Optional<?> out && out.isPresent();
        } catch (ClassNotFoundException e) {
            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("Local portal home failed: " + e.getMessage());
            return false;
        }
    }

    private boolean tryEssentialsRtp(Player player) {
        org.bukkit.plugin.Plugin ess = org.bukkit.Bukkit.getPluginManager().getPlugin("YaPEssentials");
        if (ess == null || !ess.isEnabled()) {
            return false;
        }
        try {
            Object service = ess.getClass().getMethod("rtpService").invoke(ess);
            if (service == null) {
                return false;
            }
            Object ok = service.getClass().getMethod("start", Player.class).invoke(service, player);
            return Boolean.TRUE.equals(ok);
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Local portal RTP failed: " + e.getMessage());
            return false;
        }
    }

    /** Prefer YaPEssentials /setspawn, else current/first world spawn. */
    private static Location resolveSpawn(Player player) {
        Location essentials = essentialsSpawn();
        if (essentials != null && essentials.getWorld() != null) {
            return essentials;
        }
        org.bukkit.World world = player.getWorld();
        if (world == null) {
            var worlds = org.bukkit.Bukkit.getWorlds();
            world = worlds.isEmpty() ? null : worlds.get(0);
        }
        return world == null ? null : world.getSpawnLocation().clone().add(0.5, 0, 0.5);
    }

    private static Location essentialsSpawn() {
        org.bukkit.plugin.Plugin ess = org.bukkit.Bukkit.getPluginManager().getPlugin("YaPEssentials");
        if (ess == null || !ess.isEnabled()) {
            return null;
        }
        try {
            Object store = ess.getClass().getMethod("spawnStore").invoke(ess);
            if (store == null) {
                return null;
            }
            Object loc = store.getClass().getMethod("spawn").invoke(store);
            return loc instanceof Location location ? location.clone() : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
