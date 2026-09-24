package com.yapcore.claims;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

final class ClaimListenerInteract implements Listener {

    private final JavaPlugin plugin;
    private final ClaimService claims;

    ClaimListenerInteract(JavaPlugin plugin, ClaimService claims) {
        this.plugin = plugin;
        this.claims = claims;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPressurePlate(EntityInteractEvent event) {
        Block block = event.getBlock();
        if (block == null) {
            return;
        }
        if (!ClaimPressurePlateRules.allow(
                claims.config().claimsEnabled(),
                claims.config().claimsMobsActivatePressurePlates(),
                event.getEntity() instanceof Player,
                ClaimPressurePlateRules.isPressurePlate(block.getType().name()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        Material hand = player.getInventory().getItemInMainHand().getType();
        Material claimTool = claims.config().claimsTool();
        Material inspect = claims.config().claimsInspectTool();

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && hand == claimTool) {
            event.setCancelled(true);
            try {
                player.sendMessage(claims.handleShovel(player, block.getLocation()));
            } catch (Exception e) {
                player.sendMessage("§cClaim error: " + e.getMessage());
                plugin.getLogger().log(Level.WARNING, "claim shovel", e);
            }
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && hand == inspect) {
            event.setCancelled(true);
            var opt = claims.getAt(block.getLocation());
            if (opt.isEmpty()) {
                player.sendMessage("§7Wilderness — not claimed.");
            } else {
                Claim c = opt.get();
                player.sendMessage("§aClaim §f#" + c.id() + " §7· " + c.area() + " blocks · §f"
                        + c.minX() + "," + c.minZ() + " → " + c.maxX() + "," + c.maxZ());
                ClaimVisualizer.show(plugin, player, c, claims.config().claimsVisualSeconds());
            }
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Material type = block.getType();
            String n = type.name();
            if (n.contains("CHEST") || n.contains("BARREL") || n.contains("SHULKER")
                    || type == Material.FURNACE || type == Material.BLAST_FURNACE
                    || type == Material.SMOKER || type == Material.HOPPER) {
                if (!claims.canOpenContainer(player, block.getLocation())) {
                    event.setCancelled(true);
                    player.sendMessage("§cClaimed — no chest access.");
                }
                return;
            }
            if (n.contains("DOOR") || n.contains("GATE") || n.contains("BUTTON")
                    || n.contains("LEVER") || n.contains("PRESSURE_PLATE")) {
                if (!claims.canUse(player, block.getLocation())) {
                    event.setCancelled(true);
                    player.sendMessage("§cClaimed — use denied.");
                }
            }
        }
    }
}
