package com.yapcore.playerdata.claims;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class ClaimListener implements Listener {

    private final JavaPlugin plugin;
    private final ClaimService claims;

    public ClaimListener(JavaPlugin plugin, ClaimService claims) {
        this.plugin = plugin;
        this.claims = claims;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!claims.canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cClaimed land — you cannot build here"
                    + (claims.getAt(event.getBlock().getLocation()).map(c -> c.taxFrozen() ? " (tax frozen)" : "").orElse(""))
                    + ".");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!claims.canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cClaimed land — you cannot build here"
                    + (claims.getAt(event.getBlock().getLocation()).map(c -> c.taxFrozen() ? " (tax frozen)" : "").orElse(""))
                    + ".");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!claims.canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cClaimed land — you cannot build here.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!claims.canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cClaimed land — you cannot build here.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        var claim = claims.getAt(victim.getLocation());
        if (claim.isEmpty()) {
            return;
        }
        if (!attacker.hasPermission("yapdata.claims.admin")
                && !claims.isPvpAllowed(attacker, victim)) {
            event.setCancelled(true);
            attacker.sendMessage("§cPvP disabled in this claim.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (event.getDamager() instanceof Player) {
            return;
        }
        if (!claims.isMobDamageAllowed(victim)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(org.bukkit.event.player.PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        var fromClaim = claims.getAt(event.getFrom());
        var toClaim = claims.getAt(event.getTo());
        if (fromClaim.map(Claim::id).equals(toClaim.map(Claim::id))) {
            return;
        }
        if (!claims.canEnter(event.getPlayer(), event.getTo())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cEntry denied in this claim.");
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
                    || type == Material.SMOKER || type == Material.HOPPER
                    || n.contains("DOOR") || n.contains("GATE")
                    || n.contains("BUTTON") || n.contains("LEVER")) {
                if (!claims.canOpenContainer(player, block.getLocation())) {
                    event.setCancelled(true);
                    player.sendMessage("§cClaimed — no chest access.");
                }
            }
        }
    }
}
