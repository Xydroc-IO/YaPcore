package com.yapcore.regions.listener;

import com.yapcore.regions.AdminRegion;
import com.yapcore.regions.RegionsConfig;
import com.yapcore.regions.service.RegionServiceImpl;
import com.yapcore.sched.StaffBypass;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Forces a region's configured gamemode on enter; on leave restores previous
 * or applies {@link RegionsConfig#outsideGameMode()} when set (e.g. survival
 * outside spawn). Staff with land bypass keep their current mode.
 */
public final class RegionGamemodeListener implements Listener {

    private final JavaPlugin plugin;
    private final RegionsConfig config;
    private final RegionServiceImpl regions;
    /** Previous gamemode before a region forced one (used when outside-gamemode unset). */
    private final Map<UUID, GameMode> previous = new ConcurrentHashMap<>();

    public RegionGamemodeListener(JavaPlugin plugin, RegionsConfig config, RegionServiceImpl regions) {
        this.plugin = plugin;
        this.config = config;
        this.regions = regions;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()
                && from.getWorld() != null
                && from.getWorld().equals(to.getWorld())) {
            return;
        }
        applyCross(event.getPlayer(), regions.at(from), regions.at(to));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) {
            return;
        }
        applyCross(event.getPlayer(), regions.at(event.getFrom()), regions.at(event.getTo()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Optional<AdminRegion> here = regions.at(player.getLocation());
        if (here.isPresent() && here.get().hasGameMode()) {
            enter(player, here.get());
        } else {
            applyOutside(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        previous.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Re-apply (or clear) gamemode for everyone currently online — call after
     * {@code /region gamemode} or {@code /region reload} so players already inside
     * do not need to leave/rejoin.
     */
    public void refreshOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            YapSched.entity(plugin, player, () -> refreshOne(player));
        }
    }

    private void refreshOne(Player player) {
        Optional<AdminRegion> here = regions.at(player.getLocation());
        if (here.isEmpty() || !here.get().hasGameMode()) {
            leave(player);
            return;
        }
        if (StaffBypass.land(player)) {
            return;
        }
        GameMode wanted = parse(here.get().gameMode());
        if (wanted == null) {
            leave(player);
            return;
        }
        previous.putIfAbsent(player.getUniqueId(), player.getGameMode());
        if (player.getGameMode() != wanted) {
            player.setGameMode(wanted);
        }
    }

    private void applyCross(Player player, Optional<AdminRegion> from, Optional<AdminRegion> to) {
        String fromId = from.map(r -> r.id() + ":" + nullToEmpty(r.gameMode())).orElse("");
        String toId = to.map(r -> r.id() + ":" + nullToEmpty(r.gameMode())).orElse("");
        if (fromId.equals(toId)) {
            return;
        }
        if (from.isPresent() && (to.isEmpty() || !sameGameMode(from.get(), to.get()))) {
            leave(player);
        }
        if (to.isPresent() && to.get().hasGameMode()) {
            enter(player, to.get());
        } else if (to.isEmpty() || !to.get().hasGameMode()) {
            applyOutside(player);
        }
    }

    private void enter(Player player, AdminRegion region) {
        if (StaffBypass.land(player) || !region.hasGameMode()) {
            return;
        }
        GameMode wanted = parse(region.gameMode());
        if (wanted == null || player.getGameMode() == wanted) {
            return;
        }
        previous.putIfAbsent(player.getUniqueId(), player.getGameMode());
        player.setGameMode(wanted);
    }

    private void leave(Player player) {
        if (config.outsideGameMode() != null) {
            previous.remove(player.getUniqueId());
            applyOutside(player);
            return;
        }
        GameMode restore = previous.remove(player.getUniqueId());
        if (restore != null && !StaffBypass.land(player) && player.getGameMode() != restore) {
            player.setGameMode(restore);
        }
    }

    /** Enforce configured outside mode when set. */
    private void applyOutside(Player player) {
        if (StaffBypass.land(player)) {
            return;
        }
        GameMode outside = config.outsideGameMode();
        if (outside != null && player.getGameMode() != outside) {
            player.setGameMode(outside);
        }
    }

    private static boolean sameGameMode(AdminRegion a, AdminRegion b) {
        return nullToEmpty(a.gameMode()).equals(nullToEmpty(b.gameMode()));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    public static GameMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return GameMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
