package com.yapcore.admin.gui;

/** Slot indices for admin menu inventories. */
final class AdminMenuSlots {

    static final int SLOT_BACK = 45;
    static final int SLOT_CLOSE = 49;
    static final int SLOT_INFO = 4;

    // Hub
    static final int HUB_PLAYERS = 10;
    static final int HUB_SELF = 12;
    static final int HUB_GIVE = 14;
    static final int HUB_ITEMS = 15;
    static final int HUB_MOD = 16;
    static final int HUB_TROLLS = 19;
    static final int HUB_SERVER = 28;
    static final int HUB_ECONOMY = 30;
    static final int HUB_LINKS = 32;
    static final int HUB_SCHEMATICS = 22;
    /** Skills — give XP / set levels (was 34, easy to miss). */
    static final int HUB_COMBAT = 21;
    /** YaP420 plant / cure / give hub. */
    static final int HUB_YAP420 = 23;

    // YaP420 hub
    static final int Y420_GIVE_SEEDS = 19;
    static final int Y420_GIVE_BUDS = 20;
    static final int Y420_GIVE_CONSUME = 21;
    static final int Y420_GIVE_RACK = 22;
    static final int Y420_GIVE_ALL = 23;
    static final int Y420_BROWSE = 24;
    static final int Y420_AMOUNT = 25;
    static final int Y420_RELOAD = 28;
    static final int Y420_REMOVE = 29;
    static final int Y420_INFO = 30;
    static final int Y420_STARTER = 31;
    static final int GIVE_PRESETS = 19;
    static final int GIVE_GEAR = 21;
    static final int GIVE_KITS = 23;
    static final int GIVE_MATS = 25;
    static final int GIVE_AMOUNT = 31;
    static final int GIVE_TARGET = 40;

    // Materials nav
    static final int MAT_PREV = 45;
    static final int MAT_NEXT = 53;
    static final int MAT_AMOUNT = 49;
    static final int MAT_BACK = 48;
    static final int CAT_ALL = 0;
    static final int CAT_BLOCKS = 1;
    static final int CAT_TOOLS = 2;
    static final int CAT_COMBAT = 3;
    static final int CAT_FOOD = 4;
    static final int CAT_MISC = 5;

    static final int PAGE_SIZE = 28;

    private AdminMenuSlots() {
    }
}
