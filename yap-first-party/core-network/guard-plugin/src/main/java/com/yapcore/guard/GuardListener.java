package com.yapcore.guard;

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
        if (config.flyEnabled()) {
            checkFly(player);
        }
        if (config.speedEnabled()) {
            checkSpeed(player);
        }
    }

    private void checkFly(Player player) {
        if (player.getAllowFlight() || player.isFlying()) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (player.isGliding() || player.isInsideVehicle() || player.isRiptiding()) {
            return;
        }
        if (player.isOnGround() || player.isInWater() || player.isClimbing()) {
            return;
        }
        if (player.getFallDistance() > 0.5f && player.getVelocity().getY() < -0.08) {
            return;
        }
        if (player.getLocation().getBlock().isLiquid()) {
            return;
        }
        if (Math.random() > config.flySensitivity()) {
            return;
        }
        flag(player, "fly");
    }

    private void checkSpeed(Player player) {
        ViolationTracker.PlayerState state = tracker.state(player.getUniqueId());
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

        double allowed = config.maxBlocksPerTick();
        if (player.isSprinting()) {
            allowed *= 1.35;
        }
        if (player.isGliding()) {
            allowed *= 2.5;
        }
        allowed *= (0.5 + config.speedSensitivity());

        if (blocksPerTick <= allowed) {
            return;
        }
        if (Math.random() > config.speedSensitivity()) {
            return;
        }
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
            Entity victim = event.getEntity();
            double max = config.maxReachDistance() + (1.0 - config.reachSensitivity()) * 1.5;
            double dist = player.getLocation().distance(victim.getLocation());
            if (dist <= max) {
                return;
            }
            if (Math.random() > config.reachSensitivity()) {
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
            if (Math.random() > config.scaffoldSensitivity()) {
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
        return player.hasPermission("yapguard.bypass")
                || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR;
    }
}
