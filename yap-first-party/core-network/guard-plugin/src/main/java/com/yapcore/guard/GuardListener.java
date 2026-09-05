package com.yapcore.guard;

import com.yapcore.sched.StaffBypass;
import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

public final class GuardListener implements Listener {

    private final JavaPlugin plugin;
    private GuardConfig config;
    private final ViolationTracker tracker;
    private final GuardServiceImpl service;
    private YapTask movementTask;

    public GuardListener(JavaPlugin plugin, GuardConfig config, ViolationTracker tracker,
                         GuardServiceImpl service) {
        this.plugin = plugin;
        this.config = config;
        this.tracker = tracker;
        this.service = service;
    }

    public void setConfig(GuardConfig config) {
        this.config = config;
    }

    public void startMovementChecks() {
        stopMovementChecks();
        movementTask = YapSched.globalTimer(plugin, this::checkOnlinePlayers, 20L, 10L);
    }

    public void stopMovementChecks() {
        if (movementTask != null) {
            movementTask.cancel();
            movementTask = null;
        }
    }

    private void checkOnlinePlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            YapSched.entity(plugin, player, () -> checkPlayerMovement(player));
        }
    }

    private void checkPlayerMovement(Player player) {
        if (shouldBypass(player)) {
            return;
        }
        ViolationTracker.PlayerState state = tracker.state(player.getUniqueId());
        if (System.currentTimeMillis() < state.joinGraceUntilMs) {
            return;
        }
        if (config.flyEnabled()) {
            checkFly(player, state);
        }
        if (config.speedEnabled()) {
            checkSpeed(player, state);
        }
    }

    private void checkFly(Player player, ViolationTracker.PlayerState state) {
        if (player.getAllowFlight() || player.isFlying()) {
            state.flyAirborneStreak = 0;
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            state.flyAirborneStreak = 0;
            return;
        }
        // Elytra / vehicle / riptide — clear grace (do not double-flag with Grim when both on)
        if (player.isGliding() || player.isInsideVehicle() || player.isRiptiding()) {
            state.flyAirborneStreak = 0;
            return;
        }
        if (player.hasPotionEffect(PotionEffectType.LEVITATION)
                || player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
            state.flyAirborneStreak = 0;
            return;
        }
        if (player.isOnGround() || player.isInWater() || player.isClimbing() || player.isSwimming()) {
            state.flyAirborneStreak = 0;
            return;
        }
        if (player.getLocation().getBlock().isLiquid()) {
            state.flyAirborneStreak = 0;
            return;
        }
        if (hasSolidGroundNearby(player)) {
            state.flyAirborneStreak = 0;
            return;
        }
        // Jumping / knockback upward — not fly hacks
        if (player.getVelocity().getY() > 0.05) {
            state.flyAirborneStreak = 0;
            return;
        }
        // Genuine falling
        if (player.getFallDistance() > 0.5f && player.getVelocity().getY() < -0.08) {
            state.flyAirborneStreak = 0;
            return;
        }

        state.flyAirborneStreak++;
        if (state.flyAirborneStreak < config.flyMinAirborneChecks()) {
            return;
        }
        // Hover / no-fall: airborne with near-zero fall distance after streak
        boolean hoverNoFall = player.getFallDistance() < 0.2f
                && Math.abs(player.getVelocity().getY()) < 0.12;
        if (!GuardHeuristics.shouldFlagSample(true, config.flySensitivity(), config.sampleRandomly())) {
            return;
        }
        state.flyAirborneStreak = 0;
        flag(player, hoverNoFall ? "nofall" : "fly");
    }

    /** Solid block within ~1.3 blocks under feet (covers stairs/slabs + laggy isOnGround). */
    private static boolean hasSolidGroundNearby(Player player) {
        Location loc = player.getLocation();
        var world = loc.getWorld();
        if (world == null) {
            return false;
        }
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        double feetY = loc.getY();
        for (double dy = 0.05; dy <= 1.35; dy += 0.45) {
            Block block = world.getBlockAt(x, (int) Math.floor(feetY - dy), z);
            if (GuardHeuristics.isGroundLike(block.getType())) {
                return true;
            }
        }
        return false;
    }

    private void checkSpeed(Player player, ViolationTracker.PlayerState state) {
        Location loc = player.getLocation();
        long now = System.currentTimeMillis();
        if (!state.hasLastMove) {
            state.hasLastMove = true;
            state.lastX = loc.getX();
            state.lastY = loc.getY();
            state.lastZ = loc.getZ();
            state.lastMoveMs = now;
            return;
        }
        long elapsed = Math.max(1L, now - state.lastMoveMs);
        double dx = loc.getX() - state.lastX;
        double dy = loc.getY() - state.lastY;
        double dz = loc.getZ() - state.lastZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double ticks = elapsed / 50.0;
        double blocksPerTick = dist / ticks;
        state.lastX = loc.getX();
        state.lastY = loc.getY();
        state.lastZ = loc.getZ();
        state.lastMoveMs = now;

        if (player.isGliding() || player.isInsideVehicle() || player.isRiptiding()
                || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR) {
            state.speedOverStreak = 0;
            return;
        }

        double allowed = GuardHeuristics.speedAllowedBlocksPerTick(
                config.maxBlocksPerTick(), config.speedSensitivity(),
                player.isSprinting(), false);

        if (blocksPerTick <= allowed) {
            state.speedOverStreak = 0;
            return;
        }
        state.speedOverStreak++;
        if (state.speedOverStreak < config.speedConsecutiveHits()) {
            return;
        }
        if (!GuardHeuristics.shouldFlagSample(true, config.speedSensitivity(), config.sampleRandomly())) {
            return;
        }
        state.speedOverStreak = 0;
        flag(player, "speed");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onReach(EntityDamageByEntityEvent event) {
        if (!config.reachEnabled()) {
            return;
        }
        Entity damager = event.getDamager();
        if (!(damager instanceof Player player)) {
            return;
        }
        YapSched.entity(plugin, player, () -> {
            if (shouldBypass(player)) {
                return;
            }
            if (player.getGameMode() == GameMode.CREATIVE) {
                return;
            }
            Entity victim = event.getEntity();
            double max = config.maxReachDistance() + (1.0 - config.reachSensitivity()) * 1.5;
            double dist = player.getLocation().distance(victim.getLocation());
            if (dist <= max) {
                return;
            }
            if (!GuardHeuristics.shouldFlagSample(true, config.reachSensitivity(), config.sampleRandomly())) {
                return;
            }
            flag(player, "reach");
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onScaffold(BlockPlaceEvent event) {
        if (!config.scaffoldEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        YapSched.entity(plugin, player, () -> {
            if (shouldBypass(player)) {
                return;
            }
            Block below = event.getBlockAgainst();
            if (config.scaffoldRequireAirBelow() && below.getType() != Material.AIR) {
                return;
            }
            ViolationTracker.PlayerState state = tracker.state(player.getUniqueId());
            long now = System.currentTimeMillis();
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
            flag(player, "scaffold");
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        tracker.clearOffline(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        service.resetViolations(event.getPlayer().getUniqueId());
        ViolationTracker.PlayerState state = tracker.state(event.getPlayer().getUniqueId());
        long graceMs = config.joinGraceSeconds() * 1000L;
        state.joinGraceUntilMs = System.currentTimeMillis() + graceMs;
        state.flyAirborneStreak = 0;
        state.speedOverStreak = 0;
    }

    private void flag(Player player, String check) {
        int count = tracker.recordViolation(player, check);
        if (count >= config.maxViolationsBeforeKick()) {
            YapSched.entity(plugin, player, () -> {
                player.kick(net.kyori.adventure.text.Component.text(
                        "YaPGuard: suspected " + check));
            });
            tracker.reset(player.getUniqueId());
        }
    }

    private boolean shouldBypass(Player player) {
        return StaffBypass.guard(player);
    }
}
