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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * After a fleet portal Connect, land the player on this backend's spawn,
 * a random wild spot ({@code rtp}), their YaPPlayerData home ({@code home}),
 * or their YaPblock island ({@code island}).
 */
public final class PortalArrivalListener implements Listener {

    private static final long FIRST_DELAY_TICKS = 30L;
    private static final long RETRY_DELAY_TICKS = 20L;
    private static final int MAX_ATTEMPTS = 8;

    private final JavaPlugin plugin;
    private final PortalsConfig config;
    private final PortalArrivalPending pending;
    private final PortalServiceImpl portals;
    private final ConcurrentHashMap<UUID, Boolean> applying = new ConcurrentHashMap<>();

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
        // Always honor a fresh fleet Connect pending — even if PlayerData restored
        // the player into The End/Nether from a prior visit. Skipping here made
        // hub→survival land in The End after using an End door.
        UUID uuid = player.getUniqueId();
        if (applying.putIfAbsent(uuid, Boolean.TRUE) != null) {
            return;
        }
        AtomicInteger attempt = new AtomicInteger(0);
        scheduleAttempt(player, req, attempt);
    }

    private void scheduleAttempt(Player player, PortalArrivalPending.ArrivalRequest req,
                                 AtomicInteger attempt) {
        long delay = attempt.get() == 0 ? FIRST_DELAY_TICKS : RETRY_DELAY_TICKS;
        YapSched.entityLater(plugin, player, () -> runAttempt(player, req, attempt), delay);
    }

    private void runAttempt(Player player, PortalArrivalPending.ArrivalRequest req,
                            AtomicInteger attempt) {
        if (!player.isOnline()) {
            applying.remove(player.getUniqueId());
            return;
        }
        int n = attempt.incrementAndGet();
        if (!playerDataReady(player) && n < MAX_ATTEMPTS) {
            scheduleAttempt(player, req, attempt);
            return;
        }
        PortalArrival mode = req.arrival();
        String homeName = req.homeName();
        try {
            if (mode == PortalArrival.HOME && tryPlayerHome(player, homeName)) {
                plugin.getLogger().info("Portal arrival " + player.getName() + " → "
                        + config.serverId() + " home " + homeName);
                finishArrival(player);
                return;
            }
            if (mode == PortalArrival.ISLAND && tryIslandArrival(player)) {
                plugin.getLogger().info("Portal arrival " + player.getName() + " → "
                        + config.serverId() + " island");
                // finishArrival runs after async island teleport/create
                return;
            }
            if (mode == PortalArrival.RTP && tryEssentialsRtp(player)) {
                plugin.getLogger().info("Portal arrival " + player.getName() + " → "
                        + config.serverId() + " rtp");
                finishArrival(player);
                return;
            }
            if (mode == PortalArrival.HOME) {
                player.sendMessage("§cNo home §f" + homeName
                        + "§c on this server — landing at spawn. Use §f/sethome§c.");
            }
            if (mode == PortalArrival.ISLAND) {
                player.sendMessage("§cIsland arrival needs YaPblock on this server — landing at spawn.");
            }
            Location spawn = resolveSpawn(player);
            if (spawn == null || spawn.getWorld() == null) {
                if (n < MAX_ATTEMPTS) {
                    scheduleAttempt(player, req, attempt);
                    return;
                }
                plugin.getLogger().warning("Portal arrival for " + player.getName()
                        + " — no spawn resolved on " + config.serverId());
                applying.remove(player.getUniqueId());
                return;
            }
            plugin.getLogger().info("Portal arrival " + player.getName() + " → "
                    + config.serverId() + " spawn "
                    + spawn.getBlockX() + "," + spawn.getBlockY() + "," + spawn.getBlockZ()
                    + " (attempt " + n + ")");
            Location dest = spawn;
            // Soft Link may already place Bedrock on the pad; only skip a yank when
            // already standing on it. A wider radius left creative→lobby joins on
            // nearby ground (~12 blocks from /setspawn) in front of hub portals.
            if (nearSpawn(player.getLocation(), dest, 2.0)) {
                plugin.getLogger().info("Portal arrival " + player.getName()
                        + " already on pad — skip yank");
                player.sendMessage("§7Arrived at §f" + config.serverId() + " §7spawn.");
                portals.visuals().playArrive(player);
                finishArrival(player);
                return;
            }
            player.teleportAsync(dest).thenAccept(ok -> {
                if (!Boolean.TRUE.equals(ok) || !player.isOnline()) {
                    if (n < MAX_ATTEMPTS) {
                        YapSched.entity(plugin, player, () -> scheduleAttempt(player, req, attempt));
                        return;
                    }
                    applying.remove(player.getUniqueId());
                    return;
                }
                YapSched.entity(plugin, player, () -> {
                    if (!player.isOnline()) {
                        applying.remove(player.getUniqueId());
                        return;
                    }
                    Location again = resolveSpawn(player);
                    if (again != null && again.getWorld() != null
                            && !nearSpawn(player.getLocation(), again, 2.0)) {
                        player.teleportAsync(again);
                    }
                    player.sendMessage("§7Arrived at §f" + config.serverId() + " §7spawn.");
                    portals.visuals().playArrive(player);
                    finishArrival(player);
                });
            });
        } catch (Exception e) {
            plugin.getLogger().warning("Portal arrival failed for " + player.getName()
                    + ": " + e.getMessage());
            if (n < MAX_ATTEMPTS) {
                scheduleAttempt(player, req, attempt);
            } else {
                applying.remove(player.getUniqueId());
            }
        }
    }

    private static boolean nearSpawn(Location at, Location spawn, double maxBlocks) {
        if (at == null || spawn == null || at.getWorld() == null || spawn.getWorld() == null) {
            return false;
        }
        if (!at.getWorld().equals(spawn.getWorld())) {
            return false;
        }
        return at.distanceSquared(spawn) <= maxBlocks * maxBlocks;
    }

    private void finishArrival(Player player) {
        portals.armJoinGrace(player.getUniqueId());
        portals.seedInsideFromLocation(player);
        applyEssentialsJoinGamemode(player);
        applying.remove(player.getUniqueId());
    }

    /** Hub adventure (etc.) via YaPEssentials spawn.force-gamemode. */
    private static void applyEssentialsJoinGamemode(Player player) {
        Plugin ess = Bukkit.getPluginManager().getPlugin("YaPEssentials");
        if (ess == null || !ess.isEnabled()) {
            return;
        }
        try {
            Object cfg = ess.getClass().getMethod("essentialsConfig").invoke(ess);
            if (cfg == null) {
                return;
            }
            Object opt = cfg.getClass().getMethod("spawnForceGamemode").invoke(cfg);
            if (!(opt instanceof Optional<?> optional) || optional.isEmpty()) {
                return;
            }
            Object mode = optional.get();
            if (mode instanceof org.bukkit.GameMode gameMode) {
                if (player.getGameMode() != gameMode) {
                    player.setGameMode(gameMode);
                }
                if (gameMode != org.bukkit.GameMode.CREATIVE
                        && gameMode != org.bukkit.GameMode.SPECTATOR
                        && player.getAllowFlight()) {
                    player.setFlying(false);
                    player.setAllowFlight(false);
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // optional — Essentials may be older
        }
    }

    /** Prefer waiting until YaPPlayerData has applied the profile (avoids last-logout overwrite). */
    private static boolean playerDataReady(Player player) {
        Plugin pd = Bukkit.getPluginManager().getPlugin("YaPPlayerData");
        if (pd == null || !pd.isEnabled()) {
            return true;
        }
        try {
            Object sync = pd.getClass().getMethod("sync").invoke(pd);
            if (sync == null) {
                return true;
            }
            Object ready = sync.getClass().getMethod("isReady", UUID.class)
                    .invoke(sync, player.getUniqueId());
            return Boolean.TRUE.equals(ready);
        } catch (ReflectiveOperationException e) {
            return true;
        }
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

    /**
     * Soft-dep YaPblock: teleport to island home, or create then teleport on first visit.
     * Returns true when YaPblock accepted the request (finishArrival runs when the future completes).
     */
    private boolean tryIslandArrival(Player player) {
        try {
            Class<?> services = Class.forName("com.yapcore.yapblock.IslandServices");
            Object opt = services.getMethod("find").invoke(null);
            if (!(opt instanceof Optional<?> optional) || optional.isEmpty()) {
                return false;
            }
            Object service = optional.get();
            Object future = service.getClass()
                    .getMethod("arriveOrCreate", Player.class)
                    .invoke(service, player);
            if (!(future instanceof java.util.concurrent.CompletableFuture<?> cf)) {
                return false;
            }
            cf.whenComplete((ok, err) -> YapSched.entity(plugin, player, () -> {
                if (!player.isOnline()) {
                    applying.remove(player.getUniqueId());
                    return;
                }
                if (err != null) {
                    plugin.getLogger().warning("Portal island arrival failed for "
                            + player.getName() + ": " + err.getMessage());
                    applying.remove(player.getUniqueId());
                    return;
                }
                if (Boolean.TRUE.equals(ok)) {
                    portals.visuals().playArrive(player);
                    player.sendMessage("§aArrived at your island.");
                    finishArrival(player);
                } else {
                    applying.remove(player.getUniqueId());
                    player.sendMessage("§cCould not open your island — try §f/is create§c or §f/is home§c.");
                }
            }));
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("Portal island arrival failed: " + e.getMessage());
            return false;
        }
    }

    private static Location resolveSpawn(Player player) {
        Location essentials = essentialsSpawn();
        if (essentials != null && essentials.getWorld() != null) {
            return essentials;
        }
        // Prefer overworld — never land a SPAWN arrival on The End/Nether spawn
        World world = null;
        for (World w : Bukkit.getWorlds()) {
            if (w.getEnvironment() == World.Environment.NORMAL) {
                world = w;
                break;
            }
        }
        if (world == null) {
            world = player.getWorld();
        }
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
