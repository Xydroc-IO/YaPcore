package com.yapcore.popsim;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight stand-in for claims + economy + combat-log plugins, plus a soft
 * PlaceholderAPI touch so highpop pays the same PAPI tax YaP ships by default.
 */
public final class YapPopSimPlugin extends JavaPlugin implements Listener {

    private final Map<Long, UUID> claims = new ConcurrentHashMap<>();
    private final Map<UUID, Double> balances = new ConcurrentHashMap<>();
    private final AtomicLong moveChecks = new AtomicLong();
    private final AtomicLong combatLogs = new AtomicLong();
    private final AtomicLong papiTouches = new AtomicLong();
    private Method setPlaceholders;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        if (papi != null && papi.isEnabled()) {
            try {
                Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                setPlaceholders = api.getMethod("setPlaceholders", org.bukkit.OfflinePlayer.class, String.class);
                getLogger().info("PlaceholderAPI hooked (parity tax on move)");
            } catch (ReflectiveOperationException e) {
                getLogger().warning("PlaceholderAPI present but API missing: " + e.getMessage());
            }
        }
        getLogger().info("YaP pop-sim online (claims/economy/combat listeners)");
    }

    @Override
    public void onDisable() {
        getLogger().info("pop-sim stats moveChecks=" + moveChecks.get()
                + " combatLogs=" + combatLogs.get()
                + " papiTouches=" + papiTouches.get()
                + " claims=" + claims.size());
    }

    private static long chunkKey(Chunk c) {
        return (((long) c.getX()) << 32) ^ (c.getZ() & 0xffffffffL);
    }

    private void touchPapi(Player p) {
        if (setPlaceholders == null) {
            return;
        }
        try {
            setPlaceholders.invoke(null, p, "%player_name% %server_online%");
            papiTouches.incrementAndGet();
        } catch (ReflectiveOperationException ignored) {
            // ignore — bench tax only
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        moveChecks.incrementAndGet();
        long key = chunkKey(event.getTo().getChunk());
        if (claims.get(key) == null) {
            claims.putIfAbsent(key, event.getPlayer().getUniqueId());
        }
        balances.merge(event.getPlayer().getUniqueId(), 0.01, Double::sum);
        touchPapi(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p)) {
            return;
        }
        combatLogs.incrementAndGet();
        balances.merge(p.getUniqueId(), -0.05, Double::sum);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInv(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player p) {
            balances.merge(p.getUniqueId(), -0.001, Double::sum);
            touchPapi(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        claims.put(chunkKey(event.getBlock().getChunk()), event.getPlayer().getUniqueId());
        balances.merge(event.getPlayer().getUniqueId(), 0.02, Double::sum);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        claims.put(chunkKey(event.getBlock().getChunk()), event.getPlayer().getUniqueId());
    }
}
