package com.yapcore.abilities.bar;

import com.yapcore.sched.YapSched;
import io.papermc.paper.event.player.PlayerPickItemEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class AbilityBarListener implements Listener {

    private final JavaPlugin plugin;
    private final AbilityBarConfig config;
    private final AbilityBarService bar;

    public AbilityBarListener(JavaPlugin plugin, AbilityBarConfig config, AbilityBarService bar) {
        this.plugin = plugin;
        this.config = config;
        this.bar = bar;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        if (!config.enabled() || !bar.isCombat(event.getPlayer())) {
            return;
        }
        Player player = event.getPlayer();
        int newSlot = event.getNewSlot();
        int barIndex = config.barIndexFromHotbar(newSlot);
        if (barIndex >= 0) {
            event.setCancelled(true);
            YapSched.entity(plugin, player, () -> bar.castFromBar(player, barIndex));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!config.enabled() || !config.castOnRightClick() || !bar.isCombat(event.getPlayer())) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        int held = player.getInventory().getHeldItemSlot();
        int barIndex = config.barIndexFromHotbar(held);
        if (barIndex < 0) {
            return;
        }
        ItemStack hand = event.getItem();
        if (!AbilityBarItems.isBarToken(hand)) {
            return;
        }
        event.setCancelled(true);
        bar.castFromBar(player, barIndex);
    }

    /** Middle-mouse pick block / pick item → swap hotbar page (Paper). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickItem(PlayerPickItemEvent event) {
        if (!config.enabled() || !config.dualHotbar() || !config.swapTrigger(AbilityBarConfig.SwapTrigger.PICK_BLOCK)) {
            return;
        }
        event.setCancelled(true);
        bar.trySwap(event.getPlayer());
    }

    /** Swap hands (default {@code F}) → swap hotbar page; rebind to middle mouse in Controls. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (!config.enabled() || !config.dualHotbar()) {
            if (AbilityBarItems.isBarToken(event.getMainHandItem()) || AbilityBarItems.isBarToken(event.getOffHandItem())) {
                event.setCancelled(true);
            }
            return;
        }
        if (config.swapTrigger(AbilityBarConfig.SwapTrigger.SWAP_HANDS)) {
            event.setCancelled(true);
            bar.trySwap(event.getPlayer());
            return;
        }
        if (bar.isCombat(event.getPlayer())
                && (AbilityBarItems.isBarToken(event.getMainHandItem()) || AbilityBarItems.isBarToken(event.getOffHandItem()))) {
            event.setCancelled(true);
        }
    }

    /** Sneak + drop ({@code Q}) → swap hotbar page. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!config.enabled()) {
            return;
        }
        Player player = event.getPlayer();
        if (config.dualHotbar()
                && config.swapTrigger(AbilityBarConfig.SwapTrigger.SNEAK_DROP)
                && player.isSneaking()) {
            event.setCancelled(true);
            bar.trySwap(player);
            return;
        }
        if (bar.isCombat(player) && AbilityBarItems.isBarToken(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        if (!config.enabled()) {
            return;
        }
        bar.initPlayer(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        bar.cleanupPlayer(event.getPlayer());
        bar.store().saveAll();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!config.enabled() || !(event.getWhoClicked() instanceof Player player) || !bar.isCombat(player)) {
            return;
        }
        if (event.getClickedInventory() == null) {
            return;
        }
        int raw = event.getRawSlot();
        if (raw < 0) {
            return;
        }
        if (raw >= 36 && raw <= 44) {
            int hotbar = raw - 36;
            if (bar.protectAbilitySlot(hotbar)) {
                event.setCancelled(true);
                if (event.getAction() == InventoryAction.HOTBAR_SWAP
                        || event.getClick() == ClickType.NUMBER_KEY) {
                    bar.syncBar(player);
                }
                return;
            }
        }
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        if (AbilityBarItems.isBarToken(current) || AbilityBarItems.isBarToken(cursor)) {
            if (raw >= 36 && raw <= 44 && bar.protectAbilitySlot(raw - 36)) {
                event.setCancelled(true);
                bar.syncBar(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!config.enabled() || !(event.getWhoClicked() instanceof Player player) || !bar.isCombat(player)) {
            return;
        }
        for (int raw : event.getRawSlots()) {
            if (raw >= 36 && raw <= 44 && bar.protectAbilitySlot(raw - 36)) {
                event.setCancelled(true);
                bar.syncBar(player);
                return;
            }
        }
    }
}
