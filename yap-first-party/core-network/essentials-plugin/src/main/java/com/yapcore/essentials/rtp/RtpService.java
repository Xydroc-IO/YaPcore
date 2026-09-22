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

/**
 * Folia-safe random teleport. Samples XZ off-thread, evaluates the surface on the
 * owning region, then {@link Player#teleportAsync(Location)}.
 */
public final class RtpService {

    private final JavaPlugin plugin;
    private final BackStore back;
    private final Map<UUID, Long> cooldownUntil = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicBoolean> inFlight = new ConcurrentHashMap<>();

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

    /**
     * Start a random teleport. Messages the player on success or failure.
     *
     * @return false if the request was rejected immediately (disabled, perm, cooldown, busy)
     */
    public boolean start(Player player) {
        return start(player, null);
    }

    public boolean start(Player player, World preferredWorld) {
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
        player.sendMessage("§7Searching for a safe spot…");
        attempt(player, world, config.rtpMaxAttempts(), busy);
        return true;
    }

    private void attempt(Player player, World world, int left, AtomicBoolean busy) {
        if (!player.isOnline()) {
            busy.set(false);
            return;
        }
        if (left <= 0) {
            busy.set(false);
            YapSched.entity(plugin, player, () -> {
                if (player.isOnline()) {
                    player.sendMessage("§cCould not find a safe spot. Try again.");
                }
            });
            return;
        }
        EssentialsConfig config = config();
        YapSched.async(plugin, () -> {
            int[] xz = pickXZ(world, config);
            if (xz == null) {
                busy.set(false);
                YapSched.entity(plugin, player, () -> {
                    if (player.isOnline()) {
                        player.sendMessage("§cRTP radius is invalid for this world.");
                    }
                });
                return;
            }
            int x = xz[0];
            int z = xz[1];
            YapSched.region(plugin, world, x, z, () -> {
                Location safe = evaluate(world, x, z, config);
                if (safe == null) {
                    attempt(player, world, left - 1, busy);
                    return;
                }
                YapSched.entity(plugin, player, () -> {
                    if (!player.isOnline()) {
                        busy.set(false);
                        return;
                    }
                    back.remember(player);
                    Location dest = safe.clone();
                    player.teleportAsync(dest).thenAccept(ok -> YapSched.entity(plugin, player, () -> {
                        busy.set(false);
                        if (!player.isOnline()) {
                            return;
                        }
                        if (!Boolean.TRUE.equals(ok)) {
                            player.sendMessage("§cTeleport failed. Try again.");
                            return;
                        }
                        if (!player.hasPermission("yapessentials.rtp.bypass")) {
                            cooldownUntil.put(player.getUniqueId(),
                                    System.currentTimeMillis() + config().rtpCooldownSeconds() * 1000L);
                        }
                        player.sendMessage("§aTeleported to the wild §7("
                                + dest.getBlockX() + ", " + dest.getBlockY() + ", " + dest.getBlockZ() + ")§a.");
                    }));
                });
            });
        });
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
            Location spawn = world.getSpawnLocation();
            centerX = spawn.getX();
            centerZ = spawn.getZ();
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < 8; i++) {
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
        boolean accepted = start(player);
        if (done != null) {
            done.accept(accepted);
        }
    }
}
