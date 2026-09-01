package com.yapcore.playerdata.sync;

import com.yapcore.playerdata.db.MailRepository;
import com.yapcore.playerdata.kit.KitGrantService;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Join/quit sync + freeze until profile is ready.
 */
public final class JoinQuitListener implements Listener {

    private final JavaPlugin plugin;
    private final SyncService sync;
    private final MailRepository mail;
    private final KitGrantService kitGrants;

    public JoinQuitListener(JavaPlugin plugin, SyncService sync, MailRepository mail, KitGrantService kitGrants) {
        this.plugin = plugin;
        this.sync = sync;
        this.mail = mail;
        this.kitGrants = kitGrants;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        sync.beginJoin(player);
        if (kitGrants != null) {
            kitGrants.scheduleDelivery(player);
        }
        YapSched.asyncLater(plugin, () -> {
            if (!player.isOnline() || mail == null) {
                return;
            }
            try {
                int unread = mail.unreadCount(player.getUniqueId());
                if (unread > 0) {
                    YapSched.entity(plugin, player, () -> {
                        if (player.isOnline()) {
                            player.sendMessage("§eYou have §f" + unread + " §eunread mail. §7/mail read");
                        }
                    });
                }
            } catch (Exception ignored) {
            }
        }, 60L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        sync.handleQuit(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!frozen(event.getPlayer())) {
            return;
        }
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (frozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (frozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && frozen(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInvClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && frozen(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInvOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && frozen(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && frozen(player)) {
            event.setCancelled(true);
        }
    }

    private boolean frozen(Player player) {
        return !sync.isReady(player.getUniqueId());
    }
}
