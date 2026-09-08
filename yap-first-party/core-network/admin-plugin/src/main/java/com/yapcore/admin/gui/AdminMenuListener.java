package com.yapcore.admin.gui;

import com.yapcore.admin.AdminPlugin;
import com.yapcore.admin.session.AdminSession;
import com.yapcore.admin.session.ItemCreateDraft;
import com.yapcore.sched.YapSched;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

public final class AdminMenuListener implements Listener {

    private final AdminPlugin plugin;
    private final AdminMenuClickCore core;
    private final AdminMenuClickGive give;
    private final AdminMenuClickOps ops;
    private final AdminMenuClickCustomItemsBrowse customItemsBrowse;
    private final AdminMenuClickCustomItemsWizard customItemsWizard;

    public AdminMenuListener(AdminPlugin plugin) {
        this.plugin = plugin;
        this.core = new AdminMenuClickCore(plugin);
        this.give = new AdminMenuClickGive(plugin);
        this.ops = new AdminMenuClickOps(plugin);
        this.customItemsBrowse = new AdminMenuClickCustomItemsBrowse(plugin);
        this.customItemsWizard = new AdminMenuClickCustomItemsWizard(plugin);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof AdminMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        if (!player.hasPermission("yapadmin.menu")) {
            player.closeInventory();
            return;
        }
        int slot = event.getSlot();
        ItemStack clicked = event.getCurrentItem();
        boolean shift = event.isShiftClick();
        switch (holder.kind()) {
            case HUB -> core.handleHub(player, slot);
            case PLAYERS -> core.handlePlayers(player, slot, clicked);
            case PLAYER_ACTIONS -> core.handlePlayerActions(player, holder, slot);
            case SELF_TOOLS -> core.handleSelfTools(player, slot);
            case GIVE_HUB -> give.handleGiveHub(player, slot);
            case GIVE_PRESETS -> give.handleGivePresets(player, slot, clicked, shift);
            case GIVE_KITS -> give.handleGiveKits(player, slot, clicked);
            case GIVE_MATERIALS -> give.handleGiveMaterials(player, slot, clicked);
            case SERVER_OPS -> ops.handleServerOps(player, slot, clicked);
            case ECONOMY -> ops.handleEconomy(player, slot, clicked);
            case DEEP_LINKS -> ops.handleDeepLinks(player, slot);
            case COMBAT_SKILLS -> ops.handleCombatSkills(player, slot);
            case TROLLS -> core.handleTrolls(player, holder, slot);
            case CUSTOM_ITEMS -> customItemsBrowse.handleCustomItems(player, slot);
            case CUSTOM_ITEMS_BROWSE -> customItemsBrowse.handleCustomItemsBrowse(player, slot, clicked, shift);
            case CUSTOM_ITEMS_CREATE -> customItemsWizard.handleCustomItemsCreate(player, slot, clicked);
            case CUSTOM_ITEMS_CREATE_BASE -> customItemsWizard.handleCustomItemsCreateBase(player, slot, clicked);
            case CUSTOM_ITEMS_CREATE_BUILD -> customItemsWizard.handleCustomItemsCreateBuild(player, slot);
            case CUSTOM_ITEMS_CREATE_ABILITY -> customItemsWizard.handleCustomItemsCreateAbility(player, slot, clicked);
            case CUSTOM_ITEMS_CREATE_ENCHANTS -> customItemsWizard.handleCustomItemsCreateEnchants(player, slot, clicked);
            case CUSTOM_ITEMS_CREATE_TRIGGERS -> customItemsWizard.handleCustomItemsCreateTriggers(player, slot, clicked);
            case CUSTOM_ITEMS_COOLDOWN -> customItemsBrowse.handleCustomItemsCooldown(player, slot, clicked);
            case CUSTOM_ITEMS_COOLDOWN_EDIT -> customItemsBrowse.handleCustomItemsCooldownEdit(player, slot, clicked);
            case CUSTOM_ITEMS_MANAGE -> customItemsBrowse.handleCustomItemsManage(player, slot);
            default -> {
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof AdminMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onAbilityCooldownChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        AdminSession session = plugin.session(player.getUniqueId());
        if (!session.pendingAbilityCooldownChat()) {
            return;
        }
        event.setCancelled(true);
        String raw = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        String id = session.customItemId();
        YapSched.entity(plugin, player, () -> {
            session.setPendingAbilityCooldownChat(false);
            if (raw.equalsIgnoreCase("cancel") || raw.equalsIgnoreCase("c")) {
                player.sendMessage("§7Cancelled.");
                plugin.menus().openCustomItemsCooldownEdit(player);
                return;
            }
            if (id.isBlank()) {
                player.sendMessage("§cNo item selected.");
                return;
            }
            if (!raw.matches("(?i)\\d+(\\.\\d+)?(ms|s|t|ticks)?")) {
                player.sendMessage("§cInvalid duration. Examples: §f8s §7· §f2.5s §7· §f500ms");
                session.setPendingAbilityCooldownChat(true);
                return;
            }
            if (plugin.actions().setItemAbilityCooldown(player, id, raw)) {
                plugin.menus().openCustomItemsCooldownEdit(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onItemCreateChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        AdminSession session = plugin.session(player.getUniqueId());
        ItemCreateDraft draft = session.itemCreate();
        int pending = draft.pendingChat();
        if (pending == 0) {
            return;
        }
        event.setCancelled(true);
        String raw = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        YapSched.entity(plugin, player, () -> {
            draft.setPendingChat(0);
            if (raw.equalsIgnoreCase("cancel") || raw.equalsIgnoreCase("c")) {
                player.sendMessage("§7Cancelled.");
                plugin.menus().openCustomItemsCreateBuild(player);
                return;
            }
            if (pending == 1) {
                draft.setDisplayName(raw);
                player.sendMessage("§aDisplay name set to §f" + raw);
            } else if (pending == 2) {
                draft.setId(raw);
                if (draft.id().isBlank()) {
                    player.sendMessage("§cId must be [a-z0-9_]+");
                    draft.setPendingChat(2);
                    return;
                }
                player.sendMessage("§aId set to §f" + draft.id());
            }
            plugin.menus().openCustomItemsCreateBuild(player);
        });
    }
}
