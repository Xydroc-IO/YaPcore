package com.yapcore.portals.listener;

import com.yapcore.portals.PortalArrival;
import com.yapcore.portals.PortalsConfig;
import com.yapcore.portals.service.PortalServiceImpl;
import com.yapcore.portals.store.PortalArrivalPending;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

/**
 * After a fleet portal Connect, land the player on this backend's spawn,
 * a random wild spot ({@code rtp}), or their YaPPlayerData home ({@code home}).
 */
public final class PortalArrivalListener implements Listener {

    private final JavaPlugin plugin;
    private final PortalsConfig config;
    private final PortalArrivalPending pending;
    private final PortalServiceImpl portals;

    public PortalArrivalListener(JavaPlugin plugin, PortalsConfig config,
                                 PortalArrivalPending pending, PortalServiceImpl portals) {
        this.plugin = plugin;
        this.config = config;
        this.pending = pending;
        this.portals = portals;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        portals.armJoinGrace(player.getUniqueId());
        portals.seedInsideFromLocation(player);
        Optional<PortalArrivalPending.ArrivalRequest> arrival =
                pending.consume(player.getUniqueId(), config.serverId());
        if (arrival.isEmpty()) {
            return;
        }
        PortalArrivalPending.ArrivalRequest req = arrival.get();
        PortalArrival mode = req.arrival();
        String homeName = req.homeName();
        // Reconnect into nether/end after a crash must not yank to overworld spawn
        // from a leftover fleet Connect pending file.
        World joinWorld = player.getWorld();
        if (mode == PortalArrival.SPAWN && joinWorld != null) {
            String env = joinWorld.getEnvironment().name();
            if ("NETHER".equals(env) || "THE_END".equals(env)) {
                plugin.getLogger().info("Portal arrival skipped for " + player.getName()
                        + " — already in " + joinWorld.getName());
                return;
            }
        }
        YapSched.entityLater(plugin, player, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (mode == PortalArrival.HOME && tryPlayerHome(player, homeName)) {
                plugin.getLogger().info("Portal arrival " + player.getName() + " → "
                        + config.serverId() + " home " + homeName);
                portals.armJoinGrace(player.getUniqueId());
                portals.seedInsideFromLocation(player);
                return;
            }
            if (mode == PortalArrival.RTP && tryEssentialsRtp(player)) {
                plugin.getLogger().info("Portal arrival " + player.getName() + " → "
                        + config.serverId() + " rtp");
                portals.armJoinGrace(player.getUniqueId());
                portals.seedInsideFromLocation(player);
                return;
            }
            if (mode == PortalArrival.HOME) {
                player.sendMessage("§cNo home §f" + homeName
                        + "§c on this server — landing at spawn. Use §f/sethome§c.");
            }
            Location spawn = resolveSpawn(player);
            if (spawn == null || spawn.getWorld() == null) {
                plugin.getLogger().warning("Portal arrival for " + player.getName()
                        + " — no spawn resolved on " + config.serverId());
                return;
            }
            plugin.getLogger().info("Portal arrival " + player.getName() + " → "
                    + config.serverId() + " spawn "
                    + spawn.getBlockX() + "," + spawn.getBlockY() + "," + spawn.getBlockZ());
            player.teleportAsync(spawn).thenAccept(ok -> {
                if (!Boolean.TRUE.equals(ok) || !player.isOnline()) {
                    return;
                }
                YapSched.entity(plugin, player, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    player.sendMessage("§7Arrived at §f" + config.serverId() + " §7spawn.");
                    portals.visuals().playArrive(player);
                    portals.armJoinGrace(player.getUniqueId());
                    portals.seedInsideFromLocation(player);
                });
            });
        }, 25L);
    }

    private boolean tryPlayerHome(Player player, String homeName) {
        try {
            Class<?> provider = Class.forName("com.yapcore.playerdata.HomeAccessProvider");
            Object opt = provider.getMethod("find").invoke(null);
            if (!(opt instanceof Optional<?> optional) || optional.isEmpty()) {
                player.sendMessage("§cHome arrival needs YaPPlayerData homes on this server.");
                return false;
            }
            Object access = optional.get();
            Object result = access.getClass()
                    .getMethod("teleportHome", Player.class, String.class)
                    .invoke(access, player, homeName == null ? "home" : homeName);
            boolean ok = result instanceof Optional<?> out && out.isPresent();
            if (ok) {
                YapSched.entityLater(plugin, player, () -> {
                    if (player.isOnline()) {
                        portals.visuals().playArrive(player);
                        player.sendMessage("§aArrived at home §f"
                                + (homeName == null ? "home" : homeName) + "§a.");
                    }
                }, 5L);
            }
            return ok;
        } catch (ClassNotFoundException e) {
            player.sendMessage("§cHome arrival needs YaPPlayerData on this server.");
            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("Portal home arrival failed: " + e.getMessage());
            return false;
        }
    }

    private boolean tryEssentialsRtp(Player player) {
        Plugin ess = Bukkit.getPluginManager().getPlugin("YaPEssentials");
        if (ess == null || !ess.isEnabled()) {
            player.sendMessage("§cWild arrival needs YaPEssentials RTP on this server.");
            return false;
        }
        try {
            Object service = ess.getClass().getMethod("rtpService").invoke(ess);
            if (service == null) {
                return false;
            }
            Object ok = service.getClass().getMethod("start", Player.class).invoke(service, player);
            boolean accepted = Boolean.TRUE.equals(ok);
            if (accepted) {
                YapSched.entityLater(plugin, player, () -> {
                    if (player.isOnline()) {
                        portals.visuals().playArrive(player);
                    }
                }, 40L);
            }
            return accepted;
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Portal RTP arrival failed: " + e.getMessage());
            return false;
        }
    }

    private static Location resolveSpawn(Player player) {
        Location essentials = essentialsSpawn();
        if (essentials != null && essentials.getWorld() != null) {
            return essentials;
        }
        World world = player.getWorld();
        if (world == null) {
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }
        return world == null ? null : world.getSpawnLocation().clone().add(0.5, 0, 0.5);
    }

    private static Location essentialsSpawn() {
        Plugin ess = Bukkit.getPluginManager().getPlugin("YaPEssentials");
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
