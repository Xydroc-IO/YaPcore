package com.yapcore.admin.gui;

import com.yapcore.admin.AdminConfig;
import com.yapcore.admin.AdminPlugin;
import com.yapcore.admin.action.AdminActions;
import com.yapcore.admin.gui.AbilityCatalog;
import com.yapcore.admin.gui.ItemTemplateCatalog;
import com.yapcore.admin.session.AdminSession;
import com.yapcore.admin.session.ItemCreateDraft;
import com.yapcore.sched.YapSched;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

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
            case TROLLS -> handleTrolls(player, holder, slot);
            case CUSTOM_ITEMS -> handleCustomItems(player, slot);
            case CUSTOM_ITEMS_BROWSE -> handleCustomItemsBrowse(player, slot, clicked, shift);
            case CUSTOM_ITEMS_CREATE -> handleCustomItemsCreate(player, slot, clicked);
            case CUSTOM_ITEMS_CREATE_BASE -> handleCustomItemsCreateBase(player, slot, clicked);
            case CUSTOM_ITEMS_CREATE_BUILD -> handleCustomItemsCreateBuild(player, slot);
            case CUSTOM_ITEMS_CREATE_ABILITY -> handleCustomItemsCreateAbility(player, slot, clicked);
            case CUSTOM_ITEMS_CREATE_TRIGGERS -> handleCustomItemsCreateTriggers(player, slot, clicked);
            case CUSTOM_ITEMS_COOLDOWN -> handleCustomItemsCooldown(player, slot, clicked);
            case CUSTOM_ITEMS_COOLDOWN_EDIT -> handleCustomItemsCooldownEdit(player, slot, clicked);
            case CUSTOM_ITEMS_MANAGE -> handleCustomItemsManage(player, slot);
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
            case AdminMenus.HUB_PLAYERS, AdminMenus.HUB_MOD -> {
                plugin.session(player.getUniqueId()).setPickForTrolls(false);
                plugin.menus().openPlayers(player);
            }
            case AdminMenus.HUB_TROLLS -> {
                if (player.hasPermission("yapadmin.troll")) {
                    plugin.session(player.getUniqueId()).setPickForTrolls(true);
                    plugin.menus().openPlayers(player);
                }
            }
            case AdminMenus.HUB_SELF -> plugin.menus().openSelfTools(player);
            case AdminMenus.HUB_GIVE -> plugin.menus().openGiveHub(player);
            case AdminMenus.HUB_ITEMS -> plugin.menus().openCustomItemsHub(player);
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
        AdminSession session = plugin.session(player.getUniqueId());
        if (session.pickForTrolls()) {
            session.setPickForTrolls(false);
            plugin.menus().openTrolls(player, target);
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
            case 33 -> actions.trollSmite(player, target);
            case 34 -> actions.trollLaunch(player, target);
            case 35 -> actions.trollBurn(player, target);
            case 36 -> actions.trollBlind(player, target);
            case 37 -> actions.trollSlap(player, target);
            case 38 -> actions.trollRocket(player, target);
            case 39 -> actions.trollSquash(player, target);
            case 40 -> actions.trollConfuse(player, target);
            case 41 -> actions.trollDropHand(player, target);
            case 42 -> actions.closeAndRun(player, "check " + target.getName());
            case 43 -> actions.closeAndRun(player, "modhistory " + target.getName());
            default -> {
            }
        }
    }

    private void handleTrolls(Player player, AdminMenuHolder holder, int slot) {
        if (slot == AdminMenus.SLOT_BACK) {
            plugin.menus().openHub(player);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        Player target = holder.targetUuid() == null ? null : Bukkit.getPlayer(holder.targetUuid());
        if (target == null || !target.isOnline()) {
            player.sendMessage("§cPlayer is offline.");
            plugin.menus().openHub(player);
            return;
        }
        AdminActions actions = plugin.actions();
        switch (slot) {
            case 19 -> actions.trollSmite(player, target);
            case 20 -> actions.trollLaunch(player, target);
            case 21 -> actions.trollBurn(player, target);
            case 22 -> actions.trollRocket(player, target);
            case 23 -> actions.trollSquash(player, target);
            case 24 -> actions.trollBlind(player, target);
            case 25 -> actions.trollConfuse(player, target);
            case 28 -> actions.trollSlap(player, target);
            case 29 -> actions.trollDropHand(player, target);
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
            case 24 -> actions.toggleNightVision(player);
            case 28 -> actions.closeAndRun(player, "gms");
            case 29 -> actions.closeAndRun(player, "gmc");
            case 30 -> actions.closeAndRun(player, "gma");
            case 31 -> actions.closeAndRun(player, "gmsp");
            case 33 -> actions.closeAndRun(player, "repair");
            case 34 -> actions.closeAndRun(player, "speed 5 walk");
            case 35 -> actions.closeAndRun(player, "speed 5 fly");
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
        if (slot == 30) {
            if (plugin.actions().pluginEnabled("YaPDisasters")) {
                plugin.actions().closeAndRun(player, "yapdisaster");
            } else {
                // Essentials /weather with no args prints clear|rain|… help (or disasters install tip).
                plugin.actions().closeAndRun(player, "weather clear");
            }
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
            case 23 -> actions.closeAndRun(player, "yapworld schem browse");
            case 24 -> actions.closeAndRun(player, "yappregen status");
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

    private void handleCustomItems(Player player, int slot) {
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
            case 20 -> {
                session.setMaterialPage(0);
                plugin.menus().openCustomItemsBrowse(player);
            }
            case 22 -> plugin.menus().openCustomItemsCreate(player);
            case 24 -> {
                session.setMaterialPage(0);
                plugin.menus().openCustomItemsCooldownBrowse(player);
            }
            case 29 -> plugin.actions().closeAndRun(player, "yapitems reload");
            case 31 -> {
                session.cycleGiveAmount();
                plugin.menus().openCustomItemsHub(player);
            }
            default -> {
            }
        }
    }

    private void handleCustomItemsCooldown(Player player, int slot, ItemStack clicked) {
        AdminSession session = plugin.session(player.getUniqueId());
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == AdminMenus.MAT_BACK) {
            plugin.menus().openCustomItemsHub(player);
            return;
        }
        if (slot == AdminMenus.MAT_PREV) {
            session.setMaterialPage(Math.max(0, session.materialPage() - 1));
            plugin.menus().openCustomItemsCooldownBrowse(player);
            return;
        }
        if (slot == AdminMenus.MAT_NEXT) {
            session.setMaterialPage(session.materialPage() + 1);
            plugin.menus().openCustomItemsCooldownBrowse(player);
            return;
        }
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        String id = plainName(clicked);
        if (id.isBlank()) {
            return;
        }
        session.setCustomItemId(id);
        plugin.menus().openCustomItemsCooldownEdit(player);
    }

    private void handleCustomItemsCooldownEdit(Player player, int slot, ItemStack clicked) {
        AdminSession session = plugin.session(player.getUniqueId());
        if (slot == AdminMenus.SLOT_BACK) {
            plugin.menus().openCustomItemsCooldownBrowse(player);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == 40) {
            String id = session.customItemId();
            if (id.isBlank()) {
                return;
            }
            session.setPendingAbilityCooldownChat(true);
            player.closeInventory();
            player.sendMessage("§eType ability cooldown for §f" + id + "§e (e.g. §f4s§e / §f2.5s§e / §f500ms§e).");
            player.sendMessage("§7Or type §fcancel§7 to abort.");
            return;
        }
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        String preset = plainName(clicked).replace("▶ ", "").trim();
        if (!preset.matches("\\d+(\\.\\d+)?s")) {
            return;
        }
        String id = session.customItemId();
        if (id.isBlank()) {
            return;
        }
        if (plugin.actions().setItemAbilityCooldown(player, id, preset)) {
            plugin.menus().openCustomItemsCooldownEdit(player);
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

    private void handleCustomItemsBrowse(Player player, int slot, ItemStack clicked, boolean shift) {
        AdminSession session = plugin.session(player.getUniqueId());
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == AdminMenus.MAT_BACK) {
            plugin.menus().openCustomItemsHub(player);
            return;
        }
        if (slot == AdminMenus.MAT_PREV) {
            session.setMaterialPage(Math.max(0, session.materialPage() - 1));
            plugin.menus().openCustomItemsBrowse(player);
            return;
        }
        if (slot == AdminMenus.MAT_NEXT) {
            session.setMaterialPage(session.materialPage() + 1);
            plugin.menus().openCustomItemsBrowse(player);
            return;
        }
        if (slot == AdminMenus.MAT_AMOUNT) {
            session.cycleGiveAmount();
            plugin.menus().openCustomItemsBrowse(player);
            return;
        }
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        String id = plainName(clicked);
        if (id.isBlank()) {
            return;
        }
        if (shift) {
            int amount = 64;
            String who = session.hasTarget() ? session.targetName() : player.getName();
            plugin.actions().closeAndRun(player, "yapitems give " + id + " " + amount + " " + who);
            return;
        }
        session.setCustomItemId(id);
        plugin.menus().openCustomItemsManage(player);
    }

    private void handleCustomItemsManage(Player player, int slot) {
        AdminSession session = plugin.session(player.getUniqueId());
        String id = session.customItemId();
        if (slot == AdminMenus.SLOT_BACK) {
            plugin.menus().openCustomItemsBrowse(player);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        switch (slot) {
            case 20 -> {
                if (id.isBlank()) {
                    return;
                }
                String who = session.hasTarget() ? session.targetName() : player.getName();
                plugin.actions().closeAndRun(player, "yapitems give " + id + " " + session.giveAmount() + " " + who);
            }
            case 22 -> {
                if (id.isBlank()) {
                    return;
                }
                ItemCreateDraft draft = session.itemCreate();
                if (!draft.loadFromCustomFile(id)) {
                    player.sendMessage("§cCannot edit §f" + id + "§c — only items under §fitems/custom/§c.");
                    return;
                }
                plugin.menus().openCustomItemsCreateBuild(player);
            }
            case 24 -> {
                if (id.isBlank()) {
                    return;
                }
                plugin.menus().openCustomItemsCooldownEdit(player);
            }
            case 31 -> {
                if (id.isBlank()) {
                    return;
                }
                plugin.actions().closeAndRun(player, "yapitems delete " + id);
            }
            default -> {
            }
        }
    }

    private void handleCustomItemsCreate(Player player, int slot, ItemStack clicked) {
        AdminSession session = plugin.session(player.getUniqueId());
        if (slot == AdminMenus.SLOT_BACK) {
            plugin.menus().openCustomItemsHub(player);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        String group = switch (slot) {
            case 19 -> "weapon";
            case 21 -> "tool";
            case 23 -> "gem";
            case 25 -> "prop";
            case 31 -> "other";
            default -> null;
        };
        if (group == null) {
            return;
        }
        session.setCreateTemplateGroup(group);
        plugin.menus().openCustomItemsCreateBase(player);
    }

    private void handleCustomItemsCreateBase(Player player, int slot, ItemStack clicked) {
        AdminSession session = plugin.session(player.getUniqueId());
        if (slot == AdminMenus.SLOT_BACK) {
            plugin.menus().openCustomItemsCreate(player);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        String name = plainName(clicked).trim();
        String template = null;
        for (ItemTemplateCatalog.Entry e : ItemTemplateCatalog.byGroup(session.createTemplateGroup())) {
            if (e.label().equalsIgnoreCase(name) || e.id().equalsIgnoreCase(name)) {
                template = e.id();
                break;
            }
        }
        if (template == null) {
            return;
        }
        ItemCreateDraft draft = session.itemCreate();
        draft.setId("");
        draft.setDisplayName("");
        draft.setReplaceExisting(false);
        draft.resetForTemplate(template);
        plugin.menus().openCustomItemsCreateBuild(player);
    }

    private void handleCustomItemsCreateBuild(Player player, int slot) {
        AdminSession session = plugin.session(player.getUniqueId());
        ItemCreateDraft d = session.itemCreate();
        if (slot == AdminMenus.SLOT_BACK) {
            if (d.replaceExisting()) {
                plugin.menus().openCustomItemsManage(player);
            } else {
                plugin.menus().openCustomItemsCreateBase(player);
            }
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        switch (slot) {
            case 12 -> {
                d.toggleGlow();
                plugin.menus().openCustomItemsCreateBuild(player);
            }
            case 13 -> {
                d.toggleUnbreakable();
                plugin.menus().openCustomItemsCreateBuild(player);
            }
            case 19 -> {
                d.setPendingChat(1);
                player.closeInventory();
                player.sendMessage("§eType the §fdisplay name§e for this item (e.g. §c&c&lGod Killer§e).");
                player.sendMessage("§7Supports & color codes. Type §fcancel§7 to abort.");
            }
            case 20 -> {
                d.setPendingChat(2);
                player.closeInventory();
                player.sendMessage("§eType the §finternal id§e (e.g. §fgod_killer§e). Letters, numbers, underscores.");
                player.sendMessage("§7Type §fcancel§7 to abort.");
            }
            case 21 -> plugin.menus().openCustomItemsCreateAbility(player);
            case 25 -> plugin.menus().openCustomItemsCreateTriggers(player);
            case 22 -> {
                d.cycleDamage();
                plugin.menus().openCustomItemsCreateBuild(player);
            }
            case 23 -> {
                d.cycleRange();
                plugin.menus().openCustomItemsCreateBuild(player);
            }
            case 24 -> {
                d.cycleCooldown();
                plugin.menus().openCustomItemsCreateBuild(player);
            }
            case 28 -> {
                if (d.usesBreakVolume()) {
                    d.cycleBreakRadius();
                } else {
                    d.cycleRadius();
                }
                plugin.menus().openCustomItemsCreateBuild(player);
            }
            case 29 -> {
                d.cycleGearAttack();
                plugin.menus().openCustomItemsCreateBuild(player);
            }
            case 30 -> {
                d.cycleGearStrength();
                plugin.menus().openCustomItemsCreateBuild(player);
            }
            case 31 -> {
                d.cyclePotionEffect();
                plugin.menus().openCustomItemsCreateBuild(player);
            }
            case 32 -> {
                d.cycleProjectileKind();
                plugin.menus().openCustomItemsCreateBuild(player);
            }
            case 34 -> {
                if (d.usesBreakVolume()) {
                    d.cycleBreakCount();
                } else {
                    d.cycleHealAmount();
                }
                plugin.menus().openCustomItemsCreateBuild(player);
            }
            case 37 -> {
                d.cyclePotionDurationSec();
                plugin.menus().openCustomItemsCreateBuild(player);
            }
            case 38 -> {
                d.cyclePotionAmplifier();
                plugin.menus().openCustomItemsCreateBuild(player);
            }
            case 39 -> {
                d.cycleHealAmount();
                plugin.menus().openCustomItemsCreateBuild(player);
            }
            case 33 -> {
                if (d.id().isBlank()) {
                    player.sendMessage("§cSet an id first.");
                    return;
                }
                plugin.actions().closeAndRun(player, d.buildCreateCommand());
            }
            default -> {
            }
        }
    }

    private void handleCustomItemsCreateAbility(Player player, int slot, ItemStack clicked) {
        AdminSession session = plugin.session(player.getUniqueId());
        if (slot == AdminMenus.SLOT_BACK) {
            plugin.menus().openCustomItemsCreateBuild(player);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        ItemCreateDraft d = session.itemCreate();
        if (slot == 8) {
            d.toggleShowAllAbilities();
            plugin.menus().openCustomItemsCreateAbility(player);
            return;
        }
        if (clicked == null || clicked.getType().isAir() || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) {
            return;
        }
        String name = plainName(clicked).replace("▶ ", "").trim();
        if (name.isBlank()) {
            return;
        }
        if ("No ability".equalsIgnoreCase(name) || "Clear abilities".equalsIgnoreCase(name)
                || name.startsWith("No ability") || name.startsWith("Clear abilities")) {
            d.setAbility("");
            plugin.menus().openCustomItemsCreateAbility(player);
            return;
        }
        if (name.equalsIgnoreCase("Show suited only") || name.equalsIgnoreCase("Show all abilities")) {
            d.toggleShowAllAbilities();
            plugin.menus().openCustomItemsCreateAbility(player);
            return;
        }
        String id = AbilityCatalog.idByLabel(name);
        if (id == null) {
            return;
        }
        if ("none".equalsIgnoreCase(id)) {
            d.setAbility("");
        } else {
            d.toggleAbility(id);
        }
        plugin.menus().openCustomItemsCreateAbility(player);
    }

    private void handleCustomItemsCreateTriggers(Player player, int slot, ItemStack clicked) {
        AdminSession session = plugin.session(player.getUniqueId());
        ItemCreateDraft d = session.itemCreate();
        if (slot == AdminMenus.SLOT_BACK) {
            plugin.menus().openCustomItemsCreateBuild(player);
            return;
        }
        if (slot == AdminMenus.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (clicked == null || clicked.getType().isAir() || clicked.getType() == Material.BARRIER) {
            return;
        }
        var slots = d.abilitySlots();
        int index = 0;
        int probe = 10;
        while (probe < 44 && index < slots.size()) {
            if (probe % 9 != 0 && probe % 9 != 8) {
                if (probe == slot) {
                    d.cycleTrigger(index);
                    plugin.menus().openCustomItemsCreateTriggers(player);
                    return;
                }
                index++;
            }
            probe++;
        }
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

    private static String plainName(ItemStack item) {
        if (item == null || !item.hasItemMeta() || item.getItemMeta().displayName() == null) {
            return "";
        }
        return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
    }
}
