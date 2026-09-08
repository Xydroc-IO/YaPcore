package com.yapcore.admin.gui;

import com.yapcore.admin.AdminPlugin;
import com.yapcore.admin.action.AdminActions;
import com.yapcore.admin.session.AdminSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Custom items hub, browse, manage, and cooldown menus. */
final class AdminMenusCustomItemsBrowse {

    private final AdminPlugin plugin;

    AdminMenusCustomItemsBrowse(AdminPlugin plugin) {
        this.plugin = plugin;
    }

    void openCustomItemsHub(Player player) {
        if (!plugin.actions().pluginEnabled("YaPItems")) {
            player.sendMessage("§cYaPItems is not installed.");
            return;
        }
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.CUSTOM_ITEMS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Custom items", NamedTextColor.AQUA));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        AdminSession session = plugin.session(player.getUniqueId());
        inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuHolder.icon(Material.NETHERITE_SWORD, "Custom items",
                session.hasTarget() ? "Player: " + session.targetName() : "Giving to yourself",
                "Amount ×" + session.giveAmount()));
        inv.setItem(20, AdminMenuHolder.icon(Material.CHEST, "Browse & give", "Paged list of registered items"));
        inv.setItem(22, AdminMenuHolder.icon(Material.ANVIL, "Create item", "Wizard presets → items/custom/"));
        inv.setItem(24, AdminMenuHolder.icon(Material.CLOCK, "Ability cooldowns", "Set ability cooldown per item"));
        inv.setItem(29, AdminMenuHolder.icon(Material.EMERALD, "Reload", "Reload YaPItems registry"));
        inv.setItem(31, AdminMenuHolder.icon(Material.GOLD_INGOT, "Amount ×" + session.giveAmount(), "Cycle give amount"));
        inv.setItem(AdminMenuSlots.SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    void openCustomItemsCooldownBrowse(Player player) {
        AdminSession session = plugin.session(player.getUniqueId());
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.CUSTOM_ITEMS_COOLDOWN);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Ability cooldowns", NamedTextColor.YELLOW));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuHolder.icon(Material.CLOCK, "Ability cooldowns",
                "Click an item → pick a time",
                "Writes YAML + reloads (takes effect immediately)"));
        List<String> ids = AdminMenuItemSupport.listYapItemIds();
        int page = Math.max(0, session.materialPage());
        int maxPage = Math.max(0, (ids.size() - 1) / AdminMenuSlots.PAGE_SIZE);
        if (page > maxPage) {
            page = maxPage;
            session.setMaterialPage(page);
        }
        int start = page * AdminMenuSlots.PAGE_SIZE;
        int slot = 10;
        for (int i = start; i < Math.min(start + AdminMenuSlots.PAGE_SIZE, ids.size()); i++) {
            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            if (slot >= 44) {
                break;
            }
            String id = ids.get(i);
            String cd = AdminActions.itemAbilityCooldownLabel(id);
            ItemStack icon = AdminMenuItemSupport.createYapItemIcon(id);
            if (icon == null) {
                icon = AdminMenuHolder.icon(Material.CLOCK, id, "Current CD: " + cd, "Click to change");
            } else {
                icon.editMeta(meta -> {
                    meta.displayName(Component.text(id).color(NamedTextColor.YELLOW)
                            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                    meta.lore(List.of(
                            Component.text("Current CD: " + cd).color(NamedTextColor.AQUA)
                                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                            Component.text("Click to change").color(NamedTextColor.GRAY)
                                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
                });
            }
            inv.setItem(slot++, icon);
        }
        if (page > 0) {
            inv.setItem(AdminMenuSlots.MAT_PREV, AdminMenuHolder.icon(Material.ARROW, "Previous"));
        }
        if (page < maxPage) {
            inv.setItem(AdminMenuSlots.MAT_NEXT, AdminMenuHolder.icon(Material.ARROW, "Next"));
        }
        inv.setItem(AdminMenuSlots.MAT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    void openCustomItemsCooldownEdit(Player player) {
        AdminSession session = plugin.session(player.getUniqueId());
        String id = session.customItemId();
        if (id.isBlank()) {
            openCustomItemsCooldownBrowse(player);
            return;
        }
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.CUSTOM_ITEMS_COOLDOWN_EDIT);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Cooldown: " + id, NamedTextColor.GOLD));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        String current = AdminActions.itemAbilityCooldownLabel(id);
        inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuHolder.icon(Material.CLOCK, id,
                "Current: " + current,
                "Click a preset to apply now"));
        String[] presets = {"0s", "1s", "2s", "3s", "5s", "8s", "10s", "12s", "15s", "20s", "30s", "60s"};
        int slot = 19;
        for (String preset : presets) {
            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            if (slot >= 40) {
                break;
            }
            boolean selected = preset.equalsIgnoreCase(current);
            inv.setItem(slot++, AdminMenuHolder.icon(
                    selected ? Material.LIME_DYE : Material.CLOCK,
                    (selected ? "▶ " : "") + preset,
                    selected ? "Already set to " + preset : "Set " + id + " → " + preset));
        }
        inv.setItem(40, AdminMenuHolder.icon(Material.NAME_TAG, "Custom…",
                "Type a duration in chat",
                "Examples: 4s · 2.5s · 500ms · cancel"));
        inv.setItem(AdminMenuSlots.SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    void openCustomItemsBrowse(Player player) {
        AdminSession session = plugin.session(player.getUniqueId());
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.CUSTOM_ITEMS_BROWSE);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Browse custom items", NamedTextColor.AQUA));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuHolder.icon(Material.BOOK, "Browse items",
                "Click an item to manage",
                "Give · Edit · Delete · Cooldown"));
        List<String> ids = AdminMenuItemSupport.listYapItemIds();
        int page = Math.max(0, session.materialPage());
        int maxPage = Math.max(0, (ids.size() - 1) / AdminMenuSlots.PAGE_SIZE);
        if (page > maxPage) {
            page = maxPage;
            session.setMaterialPage(page);
        }
        int start = page * AdminMenuSlots.PAGE_SIZE;
        int slot = 10;
        for (int i = start; i < Math.min(start + AdminMenuSlots.PAGE_SIZE, ids.size()); i++) {
            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            if (slot >= 44) {
                break;
            }
            String id = ids.get(i);
            ItemStack icon = AdminMenuItemSupport.createYapItemIcon(id);
            if (icon == null) {
                icon = AdminMenuHolder.icon(Material.PAPER, id, "Click to manage");
            } else {
                icon.editMeta(meta -> {
                    meta.displayName(Component.text(id).color(NamedTextColor.AQUA)
                            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                    meta.lore(List.of(
                            Component.text("Click to manage").color(NamedTextColor.GRAY)
                                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                            Component.text("Give / Edit / Delete / CD").color(NamedTextColor.DARK_GRAY)
                                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
                });
            }
            inv.setItem(slot++, icon);
        }
        if (page > 0) {
            inv.setItem(AdminMenuSlots.MAT_PREV, AdminMenuHolder.icon(Material.ARROW, "Previous"));
        }
        if (page < maxPage) {
            inv.setItem(AdminMenuSlots.MAT_NEXT, AdminMenuHolder.icon(Material.ARROW, "Next"));
        }
        inv.setItem(AdminMenuSlots.MAT_AMOUNT, AdminMenuHolder.icon(Material.GOLD_INGOT, "Amount ×" + session.giveAmount()));
        inv.setItem(AdminMenuSlots.MAT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    void openCustomItemsManage(Player player) {
        AdminSession session = plugin.session(player.getUniqueId());
        String id = session.customItemId();
        if (id.isBlank()) {
            openCustomItemsBrowse(player);
            return;
        }
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.CUSTOM_ITEMS_MANAGE);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Manage: " + id, NamedTextColor.GOLD));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        ItemStack icon = AdminMenuItemSupport.createYapItemIcon(id);
        if (icon == null) {
            inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuHolder.icon(Material.BOOK, id, "Custom / registered item"));
        } else {
            icon.editMeta(meta -> meta.displayName(Component.text(id).color(NamedTextColor.YELLOW)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
            inv.setItem(AdminMenuSlots.SLOT_INFO, icon);
        }
        inv.setItem(20, AdminMenuHolder.icon(Material.CHEST, "Give ×" + session.giveAmount(),
                session.hasTarget() ? "To " + session.targetName() : "To yourself"));
        inv.setItem(22, AdminMenuHolder.icon(Material.ANVIL, "Edit item",
                "Opens the builder with this item loaded",
                "Only items/custom/ can be overwritten"));
        inv.setItem(24, AdminMenuHolder.icon(Material.CLOCK, "Ability cooldown",
                "Current: " + AdminActions.itemAbilityCooldownLabel(id)));
        inv.setItem(31, AdminMenuHolder.icon(Material.BARRIER, "Delete item",
                "Removes items/custom/" + id + ".yml",
                "Builtins cannot be deleted — cannot undo"));
        inv.setItem(AdminMenuSlots.SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }
}
