package com.yapcore.protect.listener;

import com.yapcore.protect.service.ProtectServiceImpl;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Toggleable inspect wand: click a block to lookup recent changes. */
public final class InspectListener implements Listener {

    private final ProtectServiceImpl service;
    private final Set<UUID> inspectors = ConcurrentHashMap.newKeySet();

    public InspectListener(ProtectServiceImpl service) {
        this.service = service;
    }

    public boolean toggle(Player player) {
        UUID id = player.getUniqueId();
        if (inspectors.contains(id)) {
            inspectors.remove(id);
            return false;
        }
        inspectors.add(id);
        return true;
    }

    public boolean isInspecting(Player player) {
        return inspectors.contains(player.getUniqueId());
    }

    public void clear(UUID id) {
        inspectors.remove(id);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!inspectors.contains(player.getUniqueId())) {
            return;
        }
        if (!player.hasPermission("yapprotect.lookup") && !player.hasPermission("yapprotect.admin")) {
            return;
        }
        var block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        event.setCancelled(true);
        long to = System.currentTimeMillis();
        long from = to - TimeUnit.DAYS.toMillis(7);
        String world = block.getWorld().getName();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        service.lookupBlock(world, x, y, z, from, to, 15).thenAccept(rows -> YapSched.entity(
                Bukkit.getPluginManager().getPlugin("YaPProtect"),
                player,
                () -> {
                    player.sendMessage("§6Protect inspect §7" + world + " " + x + "," + y + "," + z
                            + " §8(" + rows.size() + ")");
                    if (rows.isEmpty()) {
                        player.sendMessage("§7No changes in the last 7 days.");
                        return;
                    }
                    for (var row : rows) {
                        String flags = (row.rolledBack() ? " §crolled-back" : "")
                                + (row.restorable() ? "" : " §8(lookup-only)");
                        player.sendMessage("§7#" + row.id() + " §8[" + row.changeType() + "] §f" + row.actorName()
                                + " §7" + row.blockBefore() + " → " + row.blockAfter() + flags);
                    }
                }));
    }
}
