package com.yapcore.regions.listener;

import com.yapcore.regions.AdminRegion;
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
 * Forces a region's configured gamemode on enter; restores the previous mode on leave.
 * Staff with land bypass keep their current mode (builders stay creative).
 */
public final class RegionGamemodeListener implements Listener {

    private final JavaPlugin plugin;
    private final RegionServiceImpl regions;
    /** Previous gamemode before a region forced one. */
    private final Map<UUID, GameMode> previous = new ConcurrentHashMap<>();

    public RegionGamemodeListener(JavaPlugin plugin, RegionServiceImpl regions) {
        this.plugin = plugin;
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
        here.ifPresent(r -> enter(player, r));
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
        to.ifPresent(r -> enter(player, r));
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
        GameMode restore = previous.remove(player.getUniqueId());
        if (restore != null && !StaffBypass.land(player)) {
            player.setGameMode(restore);
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
