package com.yapcore.admin.gui;

import com.yapcore.admin.AdminConfig;
import com.yapcore.admin.AdminPlugin;
import com.yapcore.admin.action.AdminActions;
import com.yapcore.admin.session.AdminSession;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class AdminMenuListener implements Listener {

    private static final String DEFAULT_REASON = "Staff action";

    private final AdminPlugin plugin;

    public AdminMenuListener(AdminPlugin plugin) {
        this.plugin = plugin;
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
            case HUB -> handleHub(player, slot);
            case PLAYERS -> handlePlayers(player, slot, clicked);
            case PLAYER_ACTIONS -> handlePlayerActions(player, holder, slot);
            case SELF_TOOLS -> handleSelfTools(player, slot);
            case GIVE_HUB -> handleGiveHub(player, slot);
            case GIVE_PRESETS -> handleGivePresets(player, slot, clicked, shift);
            case GIVE_KITS -> handleGiveKits(player, slot, clicked);
            case GIVE_MATERIALS -> handleGiveMaterials(player, slot, clicked);
            case SERVER_OPS -> handleServerOps(player, slot, clicked);
            case ECONOMY -> handleEconomy(player, slot, clicked);
            case DEEP_LINKS -> handleDeepLinks(player, slot);
            case COMBAT_SKILLS -> handleCombatSkills(player, slot);
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

    private void handleHub(Player player, int slot) {
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        switch (slot) {
            case AdminMenus.HUB_PLAYERS, AdminMenus.HUB_MOD -> plugin.menus().openPlayers(player);
            case AdminMenus.HUB_SELF -> plugin.menus().openSelfTools(player);
            case AdminMenus.HUB_GIVE -> plugin.menus().openGiveHub(player);
            case AdminMenus.HUB_SERVER -> {
                if (player.hasPermission("yapadmin.server")) {
                    plugin.menus().openServerOps(player);
                }
            }
            case AdminMenus.HUB_ECONOMY -> {
                if (player.hasPermission("yapadmin.economy")) {
                    plugin.menus().openEconomy(player);
                }
            }
            case AdminMenus.HUB_LINKS -> plugin.menus().openDeepLinks(player);
            case AdminMenus.HUB_COMBAT -> plugin.menus().openCombatSkills(player);
            default -> {
            }
        }
    }

    private void handlePlayers(Player player, int slot, ItemStack clicked) {
        if (slot == AdminMenus.SLOT_BACK) {
            plugin.menus().openHub(player);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) {
            return;
        }
        if (!(clicked.getItemMeta() instanceof SkullMeta meta) || meta.getOwningPlayer() == null) {
            return;
        }
        Player target = meta.getOwningPlayer().getPlayer();
        if (target == null || !target.isOnline()) {
            player.sendMessage("§cPlayer is offline.");
            return;
        }
        plugin.menus().openPlayerActions(player, target);
    }

    private void handlePlayerActions(Player player, AdminMenuHolder holder, int slot) {
        if (slot == AdminMenus.SLOT_BACK) {
            plugin.menus().openPlayers(player);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        Player target = holder.targetUuid() == null ? null : Bukkit.getPlayer(holder.targetUuid());
        if (target == null || !target.isOnline()) {
            player.sendMessage("§cPlayer is offline.");
            plugin.menus().openPlayers(player);
            return;
        }
        AdminActions actions = plugin.actions();
        AdminSession session = plugin.session(player.getUniqueId());
        switch (slot) {
            case 10 -> actions.teleportToPlayer(player, target);
            case 11 -> actions.teleportHere(player, target);
            case 12 -> actions.teleportSpawn(player, target);
            case 14 -> actions.closeAndRun(player, "freeze " + target.getName());
            case 15 -> actions.closeAndRun(player, "invsee " + target.getName());
            case 16 -> actions.closeAndRun(player, "echest " + target.getName());
            case 19 -> actions.heal(player, target);
            case 20 -> actions.feed(player, target);
            case 21 -> {
                if (!session.confirmClear()) {
                    session.setConfirmClear(true);
                    player.sendMessage("§eClick Clear again to confirm.");
                    return;
                }
                session.setConfirmClear(false);
                actions.clearInventory(player, target);
            }
            case 23 -> actions.closeAndRun(player, "promote " + target.getName());
            case 24 -> actions.closeAndRun(player, "demote " + target.getName());
            case 25 -> plugin.menus().openGiveHub(player);
            case 28 -> {
                player.closeInventory();
                actions.kick(player, target, DEFAULT_REASON);
            }
            case 29 -> actions.warn(player, target, DEFAULT_REASON);
            case 30 -> actions.muteHour(player, target, DEFAULT_REASON);
            case 31 -> {
                player.closeInventory();
                actions.tempbanDay(player, target, DEFAULT_REASON);
            }
            default -> {
            }
        }
    }

    private void handleSelfTools(Player player, int slot) {
        if (slot == AdminMenus.SLOT_BACK) {
            plugin.menus().openHub(player);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        AdminActions actions = plugin.actions();
        switch (slot) {
            case 19 -> actions.closeAndRun(player, "fly");
            case 20 -> actions.closeAndRun(player, "god");
            case 21 -> actions.closeAndRun(player, "vanish");
            case 22 -> actions.heal(player, player);
            case 23 -> actions.feed(player, player);
            case 24 -> actions.closeAndRun(player, "socialspy");
            case 25 -> {
                if (player.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
                    player.removePotionEffect(PotionEffectType.NIGHT_VISION);
                    player.sendMessage("§7Night vision off.");
                } else {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 20 * 300, 0, false, false));
                    player.sendMessage("§aNight vision on (5m).");
                }
            }
            default -> {
            }
        }
    }

    private void handleGiveHub(Player player, int slot) {
        if (slot == AdminMenus.SLOT_BACK) {
            plugin.menus().openHub(player);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        AdminSession session = plugin.session(player.getUniqueId());
        switch (slot) {
            case AdminMenus.GIVE_PRESETS -> plugin.menus().openGivePresets(player);
            case AdminMenus.GIVE_KITS -> plugin.menus().openGiveKits(player);
            case AdminMenus.GIVE_MATS -> plugin.menus().openGiveMaterials(player);
            case AdminMenus.GIVE_AMOUNT -> {
                session.cycleGiveAmount();
                plugin.menus().openGiveHub(player);
            }
            case AdminMenus.GIVE_TARGET -> plugin.menus().openPlayers(player);
            default -> {
            }
        }
    }

    private void handleGivePresets(Player player, int slot, ItemStack clicked, boolean shift) {
        if (slot == AdminMenus.SLOT_BACK) {
            plugin.menus().openGiveHub(player);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (clicked == null || clicked.getType().isAir() || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) {
            return;
        }
        String name = plainName(clicked);
        AdminConfig.ItemPreset match = null;
        for (AdminConfig.ItemPreset preset : plugin.adminConfig().presets()) {
            if (preset.displayName().equalsIgnoreCase(name) || preset.material() == clicked.getType()) {
                match = preset;
                break;
            }
        }
        if (match == null) {
            return;
        }
        int amount = shift ? match.amount() * 4 : match.amount();
        Player target = plugin.actions().resolveGiveTarget(player);
        plugin.actions().giveItem(player, target, match.material(), amount);
    }

    private void handleGiveKits(Player player, int slot, ItemStack clicked) {
        if (slot == AdminMenus.SLOT_BACK) {
            plugin.menus().openGiveHub(player);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (clicked == null || clicked.getType() != Material.CHEST) {
            return;
        }
        String kit = plainName(clicked);
        if (kit.isBlank() || "Kits".equalsIgnoreCase(kit)) {
            return;
        }
        Player target = plugin.actions().resolveGiveTarget(player);
        plugin.actions().giveKit(player, target, kit);
    }

    private void handleGiveMaterials(Player player, int slot, ItemStack clicked) {
        AdminSession session = plugin.session(player.getUniqueId());
        if (slot == AdminMenus.MAT_BACK) {
            plugin.menus().openGiveHub(player);
            return;
        }
        if (slot == AdminMenus.MAT_AMOUNT) {
            session.cycleGiveAmount();
            plugin.menus().openGiveMaterials(player);
            return;
        }
        if (slot == AdminMenus.MAT_PREV) {
            session.setMaterialPage(session.materialPage() - 1);
            plugin.menus().openGiveMaterials(player);
            return;
        }
        if (slot == AdminMenus.MAT_NEXT) {
            session.setMaterialPage(session.materialPage() + 1);
            plugin.menus().openGiveMaterials(player);
            return;
        }
        if (slot == AdminMenus.CAT_ALL) {
            session.setCategory(AdminSession.MaterialCategory.ALL);
            plugin.menus().openGiveMaterials(player);
            return;
        }
        if (slot == AdminMenus.CAT_BLOCKS) {
            session.setCategory(AdminSession.MaterialCategory.BLOCKS);
            plugin.menus().openGiveMaterials(player);
            return;
        }
        if (slot == AdminMenus.CAT_TOOLS) {
            session.setCategory(AdminSession.MaterialCategory.TOOLS);
            plugin.menus().openGiveMaterials(player);
            return;
        }
        if (slot == AdminMenus.CAT_COMBAT) {
            session.setCategory(AdminSession.MaterialCategory.COMBAT);
            plugin.menus().openGiveMaterials(player);
            return;
        }
        if (slot == AdminMenus.CAT_FOOD) {
            session.setCategory(AdminSession.MaterialCategory.FOOD);
            plugin.menus().openGiveMaterials(player);
            return;
        }
        if (slot == AdminMenus.CAT_MISC) {
            session.setCategory(AdminSession.MaterialCategory.MISC);
            plugin.menus().openGiveMaterials(player);
            return;
        }
        if (clicked == null || clicked.getType().isAir() || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) {
            return;
        }
        if (slot < 9 || slot >= 45) {
            return;
        }
        Material mat = clicked.getType();
        if (!mat.isItem()) {
            return;
        }
        Player target = plugin.actions().resolveGiveTarget(player);
        plugin.actions().giveItem(player, target, mat, session.giveAmount());
    }

    private void handleServerOps(Player player, int slot, ItemStack clicked) {
        if (slot == AdminMenus.SLOT_BACK) {
            plugin.menus().openHub(player);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == 28) {
            plugin.actions().closeAndRun(player, "yapperm gui");
            return;
        }
        if (clicked == null || clicked.getType() != Material.NOTE_BLOCK) {
            return;
        }
        String title = plainName(clicked);
        if (!title.startsWith("Broadcast #")) {
            return;
        }
        try {
            int idx = Integer.parseInt(title.replace("Broadcast #", "").trim()) - 1;
            var presets = plugin.adminConfig().broadcastPresets();
            if (idx >= 0 && idx < presets.size()) {
                plugin.actions().broadcast(player, presets.get(idx));
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private void handleEconomy(Player player, int slot, ItemStack clicked) {
        if (slot == AdminMenus.SLOT_BACK) {
            plugin.menus().openHub(player);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (clicked == null || clicked.getType() != Material.EMERALD) {
            return;
        }
        String name = plainName(clicked);
        if (!name.startsWith("+")) {
            return;
        }
        try {
            int amount = Integer.parseInt(name.substring(1).replace(",", "").trim());
            Player target = plugin.actions().resolveGiveTarget(player);
            plugin.actions().giveMoney(player, target, amount);
        } catch (NumberFormatException ignored) {
        }
    }

    private void handleDeepLinks(Player player, int slot) {
        if (slot == AdminMenus.SLOT_BACK) {
            plugin.menus().openHub(player);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        AdminActions actions = plugin.actions();
        switch (slot) {
            case 19 -> actions.closeAndRun(player, "yapperm gui");
            case 20 -> actions.closeAndRun(player, "yapworld gui");
            case 21 -> actions.closeAndRun(player, "yapstacker gui");
            case 22 -> actions.closeAndRun(player, "menu");
            default -> {
            }
        }
    }

    private void handleCombatSkills(Player player, int slot) {
        if (slot == AdminMenus.SLOT_BACK) {
            plugin.menus().openHub(player);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        AdminActions actions = plugin.actions();
        switch (slot) {
            case 20 -> actions.closeAndRun(player, "skills");
            case 22 -> actions.heal(player, player);
            case 24 -> actions.closeAndRun(player, "prayer list");
            default -> {
            }
        }
    }

    private static String plainName(ItemStack item) {
        if (item == null || !item.hasItemMeta() || item.getItemMeta().displayName() == null) {
            return "";
        }
        return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
    }
}
