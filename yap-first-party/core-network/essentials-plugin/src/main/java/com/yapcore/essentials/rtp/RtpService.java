package com.yapcore.essentials.rtp;

import com.yapcore.essentials.EssentialsConfig;
import com.yapcore.essentials.EssentialsPlugin;
import com.yapcore.essentials.store.BackStore;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Folia-safe random teleport. Samples XZ off-thread, loads the chunk
 * asynchronously (never sync-generates on a region tick), evaluates the
 * surface on the owning region, then {@link Player#teleportAsync(Location)}.
 */
public final class RtpService implements Listener {

    /** Soft deadline so a stuck search cannot pin {@code inFlight} forever. */
    private static final long SEARCH_BUDGET_MS = 45_000L;

    private final JavaPlugin plugin;
    private final BackStore back;
    private final Map<UUID, Long> cooldownUntil = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicBoolean> inFlight = new ConcurrentHashMap<>();
    private final Map<UUID, Long> searchDeadline = new ConcurrentHashMap<>();
    private final Map<UUID, Consumer<Boolean>> completions = new ConcurrentHashMap<>();

    public RtpService(JavaPlugin plugin, BackStore back) {
        this.plugin = plugin;
        this.back = back;
    }

    private EssentialsConfig config() {
        if (plugin instanceof EssentialsPlugin ess) {
            return ess.essentialsConfig();
        }
        throw new IllegalStateException("RtpService requires EssentialsPlugin");
    }

    public void clearCooldown(UUID uuid) {
        if (uuid != null) {
            cooldownUntil.remove(uuid);
        }
    }

    /** Drop in-flight search state (quit / cancel). */
    public void cancel(UUID uuid) {
        if (uuid == null) {
            return;
        }
        AtomicBoolean busy = inFlight.get(uuid);
        if (busy != null) {
            busy.set(false);
        }
        searchDeadline.remove(uuid);
        completions.remove(uuid);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer().getUniqueId());
    }

    /**
     * Start a random teleport. Messages the player on success or failure.
     *
     * @return false if the request was rejected immediately (disabled, perm, cooldown, busy)
     */
    public boolean start(Player player) {
        return start(player, null, null);
    }

    public boolean start(Player player, World preferredWorld) {
        return start(player, preferredWorld, null);
    }

    /**
     * @param done optional callback after success ({@code true}) or terminal failure ({@code false});
     *             not invoked when the request is rejected immediately
     */
    public boolean start(Player player, World preferredWorld, Consumer<Boolean> done) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        EssentialsConfig config = config();
        if (!config.rtpEnabled()) {
            player.sendMessage("§cRandom teleport is disabled on this server.");
            return false;
        }
        if (!player.hasPermission("yapessentials.rtp")
                && !player.hasPermission("yapessentials.rtp.bypass")) {
            player.sendMessage("§cYou do not have permission for /rtp.");
            return false;
        }
        World world = resolveWorld(player, preferredWorld, config);
        if (world == null) {
            player.sendMessage("§cNo RTP world is available.");
            return false;
        }
        long now = System.currentTimeMillis();
        if (!player.hasPermission("yapessentials.rtp.bypass")) {
            Long until = cooldownUntil.get(player.getUniqueId());
            if (until != null && until > now) {
                int rem = (int) Math.ceil((until - now) / 1000.0);
                player.sendMessage("§cRTP cooldown: §f" + rem + "s§c.");
                return false;
            }
        }
        AtomicBoolean busy = inFlight.computeIfAbsent(player.getUniqueId(), id -> new AtomicBoolean(false));
        if (!busy.compareAndSet(false, true)) {
            player.sendMessage("§cAlready searching for a safe spot…");
            return false;
        }
        UUID uuid = player.getUniqueId();
        searchDeadline.put(uuid, now + SEARCH_BUDGET_MS);
        if (done != null) {
            completions.put(uuid, done);
        } else {
            completions.remove(uuid);
        }
        player.sendMessage("§7Searching for a safe spot…");
        attempt(player, world, config.rtpMaxAttempts(), busy);
        return true;
    }

    private void attempt(Player player, World world, int left, AtomicBoolean busy) {
        UUID uuid = player.getUniqueId();
        if (!player.isOnline()) {
            release(uuid, busy);
            return;
        }
        Long deadline = searchDeadline.get(uuid);
        if (deadline != null && System.currentTimeMillis() > deadline) {
            fail(player, busy, "§cCould not find a safe spot in time. Try again.");
            return;
        }
        if (left <= 0) {
            fail(player, busy, "§cCould not find a safe spot. Try again.");
            return;
        }
        EssentialsConfig config = config();
        YapSched.async(plugin, () -> {
            try {
                int[] xz = pickXZ(world, config);
                if (xz == null) {
                    fail(player, busy, "§cRTP radius is invalid for this world.");
                    return;
                }
                int x = xz[0];
                int z = xz[1];
                // Far wilderness (100+ chunks) is almost never pre-generated — always allow
                // async gen so the search does not burn all attempts on missing chunks.
                world.getChunkAtAsync(x >> 4, z >> 4, true).whenComplete((chunk, err) -> {
                    if (err != null || chunk == null) {
                        attempt(player, world, left - 1, busy);
                        return;
                    }
                    YapSched.region(plugin, world, x, z, () -> evaluateAndTeleport(
                            player, world, x, z, left, busy, config));
                });
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "RTP attempt failed", t);
                fail(player, busy, "§cTeleport failed. Try again.");
            }
        });
    }

    private void evaluateAndTeleport(Player player, World world, int x, int z,
                                     int left, AtomicBoolean busy, EssentialsConfig config) {
        try {
            if (!player.isOnline()) {
                release(player.getUniqueId(), busy);
                return;
            }
            Location safe = evaluate(world, x, z, config);
            if (safe == null) {
                attempt(player, world, left - 1, busy);
                return;
            }
            YapSched.entity(plugin, player, () -> {
                if (!player.isOnline()) {
                    release(player.getUniqueId(), busy);
                    return;
                }
                back.remember(player);
                Location dest = safe.clone();
                player.teleportAsync(dest).whenComplete((ok, err) -> YapSched.entity(plugin, player, () -> {
                    release(player.getUniqueId(), busy);
                    if (!player.isOnline()) {
                        notifyDone(player.getUniqueId(), false);
                        return;
                    }
                    if (err != null || !Boolean.TRUE.equals(ok)) {
                        player.sendMessage("§cTeleport failed. Try again.");
                        notifyDone(player.getUniqueId(), false);
                        return;
                    }
                    if (!player.hasPermission("yapessentials.rtp.bypass")) {
                        cooldownUntil.put(player.getUniqueId(),
                                System.currentTimeMillis() + config().rtpCooldownSeconds() * 1000L);
                    }
                    player.sendMessage("§aTeleported to the wild §7("
                            + dest.getBlockX() + ", " + dest.getBlockY() + ", " + dest.getBlockZ() + ")§a.");
                    plugin.getLogger().info("RTP " + player.getName() + " → "
                            + dest.getWorld().getName() + " "
                            + dest.getBlockX() + "," + dest.getBlockY() + "," + dest.getBlockZ());
                    notifyDone(player.getUniqueId(), true);
                }));
            });
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "RTP evaluate failed at " + x + "," + z, t);
            attempt(player, world, left - 1, busy);
        }
    }

    private void fail(Player player, AtomicBoolean busy, String message) {
        UUID uuid = player.getUniqueId();
        release(uuid, busy);
        notifyDone(uuid, false);
        YapSched.entity(plugin, player, () -> {
            if (player.isOnline()) {
                player.sendMessage(message);
            }
        });
    }

    private void notifyDone(UUID uuid, boolean ok) {
        Consumer<Boolean> done = completions.remove(uuid);
        if (done != null) {
            try {
                done.accept(ok);
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINE, "RTP completion callback failed", e);
            }
        }
    }

    private void release(UUID uuid, AtomicBoolean busy) {
        if (busy != null) {
            busy.set(false);
        }
        searchDeadline.remove(uuid);
    }

    private World resolveWorld(Player player, World preferred, EssentialsConfig config) {
        List<String> allowed = config.rtpWorlds();
        if (preferred != null && worldAllowed(preferred.getName(), allowed)) {
            return preferred;
        }
        World current = player.getWorld();
        if (current != null && worldAllowed(current.getName(), allowed)) {
            return current;
        }
        for (String name : allowed) {
            World w = Bukkit.getWorld(name);
            if (w != null) {
                return w;
            }
        }
        return current;
    }

    private static boolean worldAllowed(String name, List<String> allowed) {
        if (allowed == null || allowed.isEmpty()) {
            return true;
        }
        for (String a : allowed) {
            if (a != null && a.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private int[] pickXZ(World world, EssentialsConfig config) {
        int minR = Math.max(0, config.rtpMinRadius());
        int maxR = Math.max(minR + 1, config.rtpMaxRadius());
        double centerX = config.rtpCenterX();
        double centerZ = config.rtpCenterZ();
        if (config.rtpCenterSpawn()) {
            Location spawn = essentialsOrWorldSpawn(world);
            centerX = spawn.getX();
            centerZ = spawn.getZ();
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < 16; i++) {
            double angle = rng.nextDouble() * Math.PI * 2.0;
            int radius = rng.nextInt(minR, maxR + 1);
            int x = (int) Math.floor(centerX + Math.cos(angle) * radius);
            int z = (int) Math.floor(centerZ + Math.sin(angle) * radius);
            if (insideBorder(world, x, z)) {
                return new int[]{x, z};
            }
        }
        return null;
    }

    /** Prefer YaPEssentials /setspawn so hub pads center RTP on the town, not vanilla 0,0. */
    private Location essentialsOrWorldSpawn(World world) {
        if (plugin instanceof EssentialsPlugin ess) {
            try {
                Location fromStore = ess.spawnStore().spawn();
                if (fromStore != null && fromStore.getWorld() != null
                        && fromStore.getWorld().equals(world)) {
                    return fromStore;
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        return world.getSpawnLocation();
    }

    private static boolean insideBorder(World world, int x, int z) {
        WorldBorder border = world.getWorldBorder();
        if (border == null) {
            return true;
        }
        Location center = border.getCenter();
        double half = border.getSize() / 2.0 - 1.0;
        return Math.abs(x - center.getX()) <= half && Math.abs(z - center.getZ()) <= half;
    }

    private Location evaluate(World world, int x, int z, EssentialsConfig config) {
        int minY = Math.max(world.getMinHeight() + 1, config.rtpMinY());
        int maxY = Math.min(world.getMaxHeight() - 2, config.rtpMaxY());
        // Chunk is already loaded via getChunkAtAsync — safe to probe on this region.
        int surface = world.getHighestBlockYAt(x, z);
        if (surface < minY || surface > maxY) {
            return null;
        }
        Block ground = world.getBlockAt(x, surface, z);
        Material groundType = ground.getType();
        if (!groundType.isSolid()) {
            return null;
        }
        Block feet = world.getBlockAt(x, surface + 1, z);
        Block head = world.getBlockAt(x, surface + 2, z);
        if (!feet.isPassable() || !head.isPassable()) {
            return null;
        }
        if (isDangerous(feet.getType()) || isDangerous(head.getType()) || isDangerous(groundType)) {
            return null;
        }
        if (config.rtpAvoidClaims() && isClaimed(world, x, z)) {
            return null;
        }
        Location loc = new Location(world, x + 0.5, surface + 1.0, z + 0.5);
        loc.setYaw(ThreadLocalRandom.current().nextFloat(0f, 360f));
        loc.setPitch(0f);
        return loc;
    }

    private static boolean isDangerous(Material type) {
        if (type == null) {
            return true;
        }
        return type == Material.LAVA
                || type == Material.WATER
                || type == Material.FIRE
                || type == Material.SOUL_FIRE
                || type == Material.CACTUS
                || type == Material.MAGMA_BLOCK
                || type == Material.POWDER_SNOW
                || type.name().contains("CAMPFIRE");
    }

    private static boolean isClaimed(World world, int x, int z) {
        Plugin claims = Bukkit.getPluginManager().getPlugin("YaPClaims");
        if (claims == null || !claims.isEnabled()) {
            return false;
        }
        try {
            Object service = claims.getClass().getMethod("claims").invoke(claims);
            if (service == null) {
                return false;
            }
            Location probe = new Location(world, x + 0.5, 64, z + 0.5);
            Object opt = service.getClass().getMethod("getAt", Location.class).invoke(service, probe);
            if (opt instanceof Optional<?> optional) {
                return optional.isPresent();
            }
        } catch (ReflectiveOperationException ignored) {
            // Soft bridge — treat as unclaimed if API shifts.
        }
        return false;
    }

    /** Soft entry for other plugins (portals). Same as {@link #start(Player)}. */
    public void startQuiet(Player player, Consumer<Boolean> done) {
        boolean accepted = start(player, null, done);
        if (!accepted && done != null) {
            done.accept(false);
        }
    }
}
