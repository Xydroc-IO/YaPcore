package com.yapcore.admin.gui;

import com.yapcore.admin.AdminPlugin;
import org.bukkit.entity.Player;

/** Builds admin super-menu inventories. */
public final class AdminMenus {

    public static final int SLOT_BACK = AdminMenuSlots.SLOT_BACK;
    public static final int SLOT_CLOSE = AdminMenuSlots.SLOT_CLOSE;
    public static final int SLOT_INFO = AdminMenuSlots.SLOT_INFO;

    public static final int HUB_PLAYERS = AdminMenuSlots.HUB_PLAYERS;
    public static final int HUB_SELF = AdminMenuSlots.HUB_SELF;
    public static final int HUB_GIVE = AdminMenuSlots.HUB_GIVE;
    public static final int HUB_ITEMS = AdminMenuSlots.HUB_ITEMS;
    public static final int HUB_MOD = AdminMenuSlots.HUB_MOD;
    public static final int HUB_TROLLS = AdminMenuSlots.HUB_TROLLS;
    public static final int HUB_SERVER = AdminMenuSlots.HUB_SERVER;
    public static final int HUB_ECONOMY = AdminMenuSlots.HUB_ECONOMY;
    public static final int HUB_LINKS = AdminMenuSlots.HUB_LINKS;
    public static final int HUB_SCHEMATICS = AdminMenuSlots.HUB_SCHEMATICS;
    public static final int HUB_COMBAT = AdminMenuSlots.HUB_COMBAT;

    public static final int GIVE_PRESETS = AdminMenuSlots.GIVE_PRESETS;
    public static final int GIVE_KITS = AdminMenuSlots.GIVE_KITS;
    public static final int GIVE_MATS = AdminMenuSlots.GIVE_MATS;
    public static final int GIVE_AMOUNT = AdminMenuSlots.GIVE_AMOUNT;
    public static final int GIVE_TARGET = AdminMenuSlots.GIVE_TARGET;

    public static final int MAT_PREV = AdminMenuSlots.MAT_PREV;
    public static final int MAT_NEXT = AdminMenuSlots.MAT_NEXT;
    public static final int MAT_AMOUNT = AdminMenuSlots.MAT_AMOUNT;
    public static final int MAT_BACK = AdminMenuSlots.MAT_BACK;
    public static final int CAT_ALL = AdminMenuSlots.CAT_ALL;
    public static final int CAT_BLOCKS = AdminMenuSlots.CAT_BLOCKS;
    public static final int CAT_TOOLS = AdminMenuSlots.CAT_TOOLS;
    public static final int CAT_COMBAT = AdminMenuSlots.CAT_COMBAT;
    public static final int CAT_FOOD = AdminMenuSlots.CAT_FOOD;
    public static final int CAT_MISC = AdminMenuSlots.CAT_MISC;

    private final AdminMenusCore core;
    private final AdminMenusGive give;
    private final AdminMenusOps ops;
    private final AdminMenusCustomItemsBrowse customItemsBrowse;
    private final AdminMenusCustomItemsWizard customItemsWizard;

    public AdminMenus(AdminPlugin plugin) {
        this.core = new AdminMenusCore(plugin);
        this.give = new AdminMenusGive(plugin);
        this.ops = new AdminMenusOps(plugin);
        this.customItemsBrowse = new AdminMenusCustomItemsBrowse(plugin);
        this.customItemsWizard = new AdminMenusCustomItemsWizard(plugin);
    }

    public void openHub(Player player) {
        core.openHub(player);
    }

    /** JE chest hub (also used as fallback from Bedrock form callbacks). */
    public void openHubInventory(Player player) {
        core.openHubInventory(player);
    }

    public void openPlayers(Player player) {
        core.openPlayers(player);
    }

    public void openPlayerActions(Player player, Player target) {
        core.openPlayerActions(player, target);
    }

    public void openTrolls(Player player, Player target) {
        core.openTrolls(player, target);
    }

    public void openSelfTools(Player player) {
        core.openSelfTools(player);
    }

    public void openSpeedPicker(Player player, Player target, boolean fly) {
        core.openSpeedPicker(player, target, fly);
    }

    public void openGiveHub(Player player) {
        give.openGiveHub(player);
    }

    public void openGivePresets(Player player) {
        give.openGivePresets(player);
    }

    public void openGiveKits(Player player) {
        give.openGiveKits(player);
    }

    public void openGiveMaterials(Player player) {
        give.openGiveMaterials(player);
    }

    public void openServerOps(Player player) {
        ops.openServerOps(player);
    }

    public void openEconomy(Player player) {
        ops.openEconomy(player);
    }

    public void openDeepLinks(Player player) {
        ops.openDeepLinks(player);
    }

    public void openSchematics(Player player) {
        ops.openSchematics(player);
    }

    public void openCombatSkills(Player player) {
        ops.openCombatSkills(player);
    }

    public void openLeveledMobs(Player player) {
        ops.openLeveledMobs(player);
    }

    public void openCustomItemsHub(Player player) {
        customItemsBrowse.openCustomItemsHub(player);
    }

    public void openCustomItemsCooldownBrowse(Player player) {
        customItemsBrowse.openCustomItemsCooldownBrowse(player);
    }

    public void openCustomItemsCooldownEdit(Player player) {
        customItemsBrowse.openCustomItemsCooldownEdit(player);
    }

    public void openCustomItemsBrowse(Player player) {
        customItemsBrowse.openCustomItemsBrowse(player);
    }

    public void openCustomItemsManage(Player player) {
        customItemsBrowse.openCustomItemsManage(player);
    }

    public void openCustomItemsCreate(Player player) {
        customItemsWizard.openCustomItemsCreate(player);
    }

    public void openCustomItemsCreateBase(Player player) {
        customItemsWizard.openCustomItemsCreateBase(player);
    }

    public void openCustomItemsCreateBuild(Player player) {
        customItemsWizard.openCustomItemsCreateBuild(player);
    }

    public void openCustomItemsCreateAbility(Player player) {
        customItemsWizard.openCustomItemsCreateAbility(player);
    }

    public void openCustomItemsCreateEnchants(Player player) {
        customItemsWizard.openCustomItemsCreateEnchants(player);
    }

    public void openCustomItemsCreateTriggers(Player player) {
        customItemsWizard.openCustomItemsCreateTriggers(player);
    }
}
