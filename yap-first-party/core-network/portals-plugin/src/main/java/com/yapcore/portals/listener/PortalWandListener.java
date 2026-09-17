package com.yapcore.portals.listener;

import com.yapcore.portals.service.PortalServiceImpl;
import com.yapcore.portals.store.SelectionDrafts;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.NamespacedKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Wand selection: left=pos1, right=pos2. */
public final class PortalWandListener implements Listener {

    public static final String WAND_NAME = "YaP Portal Wand";

    private final PortalServiceImpl portals;
    private final NamespacedKey wandKey;

    public PortalWandListener(JavaPlugin plugin, PortalServiceImpl portals) {
        this.portals = portals;
        this.wandKey = new NamespacedKey(plugin, "portal_wand");
    }

    public ItemStack createWand() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(WAND_NAME, NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false));
            meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isWand(ItemStack stack) {
        if (stack == null || stack.getType() != Material.BLAZE_ROD || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null
                && meta.getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("yapportals.admin")) {
            return;
        }
        if (!isWand(player.getInventory().getItemInMainHand())) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        event.setCancelled(true);
        SelectionDrafts.Corner corner = SelectionDrafts.Corner.of(block.getLocation());
        if (action == Action.LEFT_CLICK_BLOCK) {
            portals.drafts().setPos1(player.getUniqueId(), corner);
            player.sendMessage("§aPortal pos1 §f" + corner.world() + " "
                    + corner.x() + "," + corner.y() + "," + corner.z());
        } else {
            portals.drafts().setPos2(player.getUniqueId(), corner);
            player.sendMessage("§aPortal pos2 §f" + corner.world() + " "
                    + corner.x() + "," + corner.y() + "," + corner.z());
        }
    }
}
