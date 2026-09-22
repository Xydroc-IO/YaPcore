package com.yapcore.portals.listener;

import com.yapcore.portals.service.PortalServiceImpl;
import com.yapcore.portals.store.SelectionDrafts;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wand selection: left=pos1, right=pos2.
 * Runs even when region interact-deny cancels the click (spawn pads).
 * Arm-swing covers adventure gamemode, where LEFT_CLICK_BLOCK often never fires.
 */
public final class PortalWandListener implements Listener {

    public static final String WAND_NAME = "YaP Portal Wand";
    private static final int REACH = 6;
    /** Ignore duplicate left-set from Interact + Animation in the same tick window. */
    private static final long LEFT_DEBOUNCE_MS = 80L;

    private final PortalServiceImpl portals;
    private final NamespacedKey wandKey;
    private final Map<UUID, Long> lastLeftMs = new ConcurrentHashMap<>();

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

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
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
        boolean left = action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR;
        boolean right = action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR;
        if (!left && !right) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            block = rayTarget(player);
        }
        denyUse(event);
        if (block == null || block.getType().isAir()) {
            player.sendMessage(portals.painting(player.getUniqueId()).isPresent()
                    ? "§cLook at a block inside the portal."
                    : "§cLook at a block to set the portal corner.");
            return;
        }
        if (portals.painting(player.getUniqueId()).isPresent()) {
            if (left) {
                long now = System.currentTimeMillis();
                Long prev = lastLeftMs.put(player.getUniqueId(), now);
                if (prev != null && now - prev < LEFT_DEBOUNCE_MS) {
                    return;
                }
            }
            portals.paintBlock(player, block, left);
            return;
        }
        if (left) {
            setCorner(player, block, true);
        } else {
            setCorner(player, block, false);
        }
    }

    /**
     * Adventure / spawn gamemode often suppresses LEFT_CLICK_* interact packets.
     * Arm swing still fires — use it for pos1 with a short debounce against Interact.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("yapportals.admin")) {
            return;
        }
        if (!isWand(player.getInventory().getItemInMainHand())) {
            return;
        }
        Block block = rayTarget(player);
        if (block == null || block.getType().isAir()) {
            return;
        }
        if (portals.painting(player.getUniqueId()).isPresent()) {
            long now = System.currentTimeMillis();
            Long prev = lastLeftMs.put(player.getUniqueId(), now);
            if (prev != null && now - prev < LEFT_DEBOUNCE_MS) {
                return;
            }
            portals.paintBlock(player, block, true);
            return;
        }
        setCorner(player, block, true);
    }

    private void setCorner(Player player, Block block, boolean pos1) {
        if (pos1) {
            long now = System.currentTimeMillis();
            Long prev = lastLeftMs.put(player.getUniqueId(), now);
            if (prev != null && now - prev < LEFT_DEBOUNCE_MS) {
                return;
            }
        }
        SelectionDrafts.Corner corner = SelectionDrafts.Corner.of(block.getLocation());
        if (pos1) {
            portals.drafts().setPos1(player.getUniqueId(), corner);
            player.sendMessage("§aPortal pos1 §f" + corner.world() + " "
                    + corner.x() + "," + corner.y() + "," + corner.z());
        } else {
            portals.drafts().setPos2(player.getUniqueId(), corner);
            player.sendMessage("§aPortal pos2 §f" + corner.world() + " "
                    + corner.x() + "," + corner.y() + "," + corner.z());
        }
    }

    private static Block rayTarget(Player player) {
        return player.getTargetBlockExact(REACH, FluidCollisionMode.NEVER);
    }

    private static void denyUse(PlayerInteractEvent event) {
        event.setCancelled(true);
        try {
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        } catch (Throwable ignored) {
        }
    }
}
