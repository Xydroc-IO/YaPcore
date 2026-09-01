package com.yapcore.abilities.book;

import com.yapcore.abilities.AbilityCategory;
import com.yapcore.abilities.AbilityDefinition;
import com.yapcore.abilities.AbilityService;
import com.yapcore.mmo.SkillProgress;
import com.yapcore.mmo.SkillService;
import com.yapcore.mmo.SkillServices;
import com.yapcore.sched.YapSched;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AbilityBookListener implements Listener {

    private final JavaPlugin plugin;
    private final AbilityBookService book;
    private final AbilityService abilities;

    public AbilityBookListener(JavaPlugin plugin, AbilityBookService book, AbilityService abilities) {
        this.plugin = plugin;
        this.book = book;
        this.abilities = abilities;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!book.config().enabled()) {
            return;
        }
        YapSched.entityLater(plugin, event.getPlayer(), () -> book.maybeGiveFirstJoinTome(event.getPlayer()), 20L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTomeInteract(PlayerInteractEvent event) {
        if (!book.config().enabled() || !book.config().openTrigger(AbilityBookConfig.OpenTrigger.TOME)) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack hand = event.getItem();
        if (!AbilityBookItems.isTome(book.keys(), hand)) {
            return;
        }
        event.setCancelled(true);
        book.open(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSneakSwapOpen(PlayerSwapHandItemsEvent event) {
        if (!book.config().enabled() || !book.config().openTrigger(AbilityBookConfig.OpenTrigger.SNEAK_SWAP)) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        event.setCancelled(true);
        book.open(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof AbilityBookHolder holder)) {
            return;
        }
        if (!holder.viewer().equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        int raw = event.getRawSlot();
        if (raw >= top.getSize()) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        ItemStack current = event.getCurrentItem();
        String nav = AbilityBookItems.navAction(book.keys(), current);
        if (nav != null) {
            book.handleNavClick(player, holder, nav);
            return;
        }

        if (AbilityBookHolder.isBarSlot(raw)) {
            int barIndex = AbilityBookHolder.barSlotIndex(raw);
            String cursorAbility = AbilityBookItems.abilityId(book.keys(), event.getCursor());
            boolean right = event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT;
            book.handleBarSlotClick(player, holder, barIndex, right, cursorAbility);
            return;
        }

        if (AbilityBookHolder.isAbilitySlot(raw)) {
            String abilityId = AbilityBookItems.abilityId(book.keys(), current);
            if (abilityId == null) {
                return;
            }
            abilities.get(abilityId).ifPresent(def -> loadSkillsAndHandleAbilityClick(
                    player, holder, def, event.isShiftClick(),
                    event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof AbilityBookHolder holder)) {
            return;
        }
        if (!holder.viewer().equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        Set<Integer> barTargets = new HashSet<>();
        String abilityId = null;
        for (int raw : event.getRawSlots()) {
            if (raw >= top.getSize()) {
                event.setCancelled(true);
                return;
            }
            if (AbilityBookHolder.isBarSlot(raw)) {
                barTargets.add(raw);
            }
            ItemStack old = event.getView().getItem(raw);
            if (old != null) {
                String id = AbilityBookItems.abilityId(book.keys(), old);
                if (id != null) {
                    abilityId = id;
                }
            }
        }
        if (abilityId == null) {
            abilityId = AbilityBookItems.abilityId(book.keys(), event.getOldCursor());
        }
        if (barTargets.isEmpty() || abilityId == null) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        String finalAbilityId = abilityId;
        for (int raw : barTargets) {
            int barIndex = AbilityBookHolder.barSlotIndex(raw);
            book.handleDragToBar(player, holder, finalAbilityId, barIndex);
        }
    }

    private void loadSkillsAndHandleAbilityClick(
            Player player,
            AbilityBookHolder holder,
            AbilityDefinition ability,
            boolean shiftClick,
            boolean rightClick
    ) {
        SkillService skills = SkillServices.find().orElse(null);
        if (skills != null) {
            skills.getAll(player.getUniqueId()).thenAccept(all ->
                    YapSched.entity(plugin, player, () ->
                            book.handleAbilityClick(player, holder, ability, shiftClick, rightClick, all)));
        } else {
            book.handleAbilityClick(player, holder, ability, shiftClick, rightClick, List.of());
        }
    }
}
