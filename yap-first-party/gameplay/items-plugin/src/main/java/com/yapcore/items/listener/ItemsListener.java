package com.yapcore.items.listener;

import com.yapcore.items.ItemsPlugin;
import com.yapcore.items.ability.AbilityDefinition;
import com.yapcore.items.ability.AbilityEngine;
import com.yapcore.items.furniture.FurnitureService;
import com.yapcore.sched.YapSched;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

public final class ItemsListener implements Listener {

    private static final double FURNITURE_REACH = 6.0;

    private final ItemsPlugin plugin;
    private final AbilityEngine abilities;
    private final FurnitureService furniture;

    public ItemsListener(ItemsPlugin plugin, AbilityEngine abilities, FurnitureService furniture) {
        this.plugin = plugin;
        this.abilities = abilities;
        this.furniture = furniture;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        Action action = event.getAction();

        // Left-click raytrace break for ItemDisplay furniture (damage events don't hit displays).
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            ItemDisplay looked = rayFurniture(player);
            if (looked != null && furniture.tryBreak(player, looked)) {
                event.setCancelled(true);
                return;
            }
        }

        ItemStack stack = player.getInventory().getItemInMainHand();
        if (stack.getType().isAir()) {
            return;
        }
        if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null && !player.isSneaking()) {
            if (furniture.tryPlace(player, stack, event.getClickedBlock(), event.getBlockFace())) {
                event.setCancelled(true);
                return;
            }
        }
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            AbilityDefinition.Trigger trigger = player.isSneaking()
                    ? AbilityDefinition.Trigger.SNEAK_RIGHT_CLICK
                    : AbilityDefinition.Trigger.RIGHT_CLICK;
            if (abilities.tryUse(player, stack, trigger)) {
                event.setCancelled(true);
            }
        } else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            AbilityDefinition.Trigger trigger = player.isSneaking()
                    ? AbilityDefinition.Trigger.SNEAK_LEFT_CLICK
                    : AbilityDefinition.Trigger.LEFT_CLICK;
            if (abilities.tryUse(player, stack, trigger)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (event.getEntity() instanceof ItemDisplay display && furniture.tryBreak(player, display)) {
            event.setCancelled(true);
            return;
        }
        ItemStack stack = player.getInventory().getItemInMainHand();
        abilities.tryUse(player, stack, AbilityDefinition.Trigger.ATTACK);
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (event.getRightClicked() instanceof ItemDisplay display) {
            if (player.isSneaking() && furniture.tryBreak(player, display)) {
                event.setCancelled(true);
                return;
            }
        }
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (stack.getType().isAir()) {
            return;
        }
        AbilityDefinition.Trigger trigger = player.isSneaking()
                ? AbilityDefinition.Trigger.SNEAK_RIGHT_CLICK
                : AbilityDefinition.Trigger.RIGHT_CLICK;
        if (abilities.tryUse(player, stack, trigger)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack stack = event.getItemDrop().getItemStack();
        if (abilities.tryUse(player, stack, AbilityDefinition.Trigger.DROP)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack main = event.getMainHandItem();
        ItemStack off = event.getOffHandItem();
        ItemStack custom = null;
        if (main != null && !main.getType().isAir() && plugin.factory().isCustom(main)) {
            custom = main;
        } else if (off != null && !off.getType().isAir() && plugin.factory().isCustom(off)) {
            custom = off;
        }
        if (custom == null) {
            return;
        }
        if (abilities.tryUse(player, custom, AbilityDefinition.Trigger.SWAP_HANDS)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack stack = event.getItem();
        var defOpt = plugin.factory().definitionOf(stack);
        if (defOpt.isEmpty()) {
            return;
        }
        if (!defOpt.get().hasAbilityTrigger(AbilityDefinition.Trigger.CONSUME)) {
            return;
        }
        event.setCancelled(true);
        abilities.tryUse(event.getPlayer(), stack, AbilityDefinition.Trigger.CONSUME);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        YapSched.regionChunk(plugin, event.getWorld(), event.getChunk().getX(), event.getChunk().getZ(),
                () -> furniture.respawnChunk(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.cooldowns().clear(event.getPlayer().getUniqueId());
    }

    private static ItemDisplay rayFurniture(Player player) {
        RayTraceResult hit = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getLocation().getDirection(),
                FURNITURE_REACH,
                0.4,
                e -> e instanceof ItemDisplay);
        if (hit == null) {
            return null;
        }
        Entity entity = hit.getHitEntity();
        return entity instanceof ItemDisplay display ? display : null;
    }
}
