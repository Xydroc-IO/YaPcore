package com.yapcore.admin.gui;

import com.yapcore.admin.AdminPlugin;
import com.yapcore.admin.action.AdminActions;
import com.yapcore.admin.session.AdminSession;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/** Click handlers for hub, players, actions, trolls, and self tools. */
final class AdminMenuClickCore {

    static final String DEFAULT_REASON = "Staff action";

    private final AdminPlugin plugin;

    AdminMenuClickCore(AdminPlugin plugin) {
        this.plugin = plugin;
    }

    void handleHub(Player player, int slot) {
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
            case AdminMenus.HUB_SCHEMATICS -> plugin.menus().openWorldTools(player);
            case AdminMenus.HUB_COMBAT -> plugin.menus().openCombatSkills(player);
            default -> {
            }
        }
    }

    void handlePlayers(Player player, int slot, ItemStack clicked) {
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

    void handlePlayerActions(Player player, AdminMenuHolder holder, int slot) {
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
            case 17 -> plugin.menus().openSpeedPicker(player, target, false);
            case 18 -> plugin.menus().openSpeedPicker(player, target, true);
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
            case 22 -> actions.closeAndRun(player, "god " + target.getName());
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

    void handleTrolls(Player player, AdminMenuHolder holder, int slot) {
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

    void handleSelfTools(Player player, int slot) {
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
            case 34 -> plugin.menus().openSpeedPicker(player, null, false);
            case 35 -> plugin.menus().openSpeedPicker(player, null, true);
            default -> {
            }
        }
    }

    void handleSpeedPicker(Player player, AdminMenuHolder holder, int slot) {
        AdminSession session = plugin.session(player.getUniqueId());
        boolean fly = session.speedFly();
        if (slot == AdminMenus.SLOT_BACK) {
            Player target = holder.targetUuid() == null ? null : Bukkit.getPlayer(holder.targetUuid());
            boolean self = target == null || target.getUniqueId().equals(player.getUniqueId());
            if (self) {
                plugin.menus().openSelfTools(player);
            } else if (target != null && target.isOnline()) {
                plugin.menus().openPlayerActions(player, target);
            } else {
                plugin.menus().openHub(player);
            }
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
        int level = -1;
        if (slot >= 10 && slot <= 19) {
            level = slot - 9; // slots 10..19 → 1..10
        } else if (slot == 22) {
            level = fly ? 1 : 2;
        }
        if (level < 1) {
            return;
        }
        String mode = fly ? "fly" : "walk";
        boolean self = target.getUniqueId().equals(player.getUniqueId());
        AdminActions actions = plugin.actions();
        if (self) {
            actions.closeAndRun(player, "speed " + level + " " + mode);
        } else {
            actions.closeAndRun(player, "speed " + target.getName() + " " + level + " " + mode);
        }
    }
}
