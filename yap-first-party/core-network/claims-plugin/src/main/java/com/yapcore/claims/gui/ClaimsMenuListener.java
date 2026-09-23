package com.yapcore.claims.gui;

import com.yapcore.claims.Claim;
import com.yapcore.claims.ClaimVisualizer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/** Click handler for {@link ClaimsMenus}. */
public final class ClaimsMenuListener implements Listener {

    private final ClaimsMenus menus;

    public ClaimsMenuListener(ClaimsMenus menus) {
        this.menus = menus;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof ClaimsMenus.Holder)) {
            return;
        }
        event.setCancelled(true);
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType().isAir()) {
            return;
        }
        String name = plainName(current);
        try {
            if ("Close".equals(name)) {
                player.closeInventory();
                return;
            }
            Map<Integer, String> meta = menus.clickMeta.getOrDefault(player.getUniqueId(), Map.of());
            String idStr = meta.get(event.getSlot());
            if (idStr == null) {
                return;
            }
            if ("expand".equals(idStr)) {
                player.closeInventory();
                var dir = com.yapcore.claims.ClaimExpandRules.fromYaw(player.getLocation().getYaw());
                player.sendMessage(menus.claims.expandAdjacent(player, dir));
                return;
            }
            long id = Long.parseLong(idStr);
            var opt = menus.claims.repo().get(id);
            if (opt.isEmpty()) {
                menus.openClaims(player);
                return;
            }
            Claim c = opt.get();
            if (event.isShiftClick()) {
                if (menus.claims.abandon(player, c)) {
                    player.sendMessage("§aAbandoned claim §f#" + id);
                } else {
                    player.sendMessage("§cCannot abandon.");
                }
                menus.openClaims(player);
                return;
            }
            player.closeInventory();
            if (c.serverId().equals(menus.config.serverId())
                    && player.getWorld().getName().equals(c.world())) {
                ClaimVisualizer.show(menus.plugin, player, c, menus.config.claimsVisualSeconds());
                player.sendMessage("§aShowing claim §f#" + id);
            } else {
                player.sendMessage("§cClaim is on §f" + c.serverId() + "/" + c.world());
            }
        } catch (Exception e) {
            player.sendMessage("§cClaims GUI error.");
            menus.plugin.getLogger().warning("claims gui click: " + e.getMessage());
        }
    }

    private static String plainName(ItemStack stack) {
        if (stack.getItemMeta() == null || stack.getItemMeta().displayName() == null) {
            return "";
        }
        return PlainTextComponentSerializer.plainText().serialize(stack.getItemMeta().displayName());
    }
}
