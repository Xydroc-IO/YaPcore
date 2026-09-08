package com.yapcore.admin.gui;

/** Menu kind for {@link AdminMenuHolder} — top-level to avoid PluginClassLoader issues with nested enums. */
public enum AdminMenuKind {
    HUB,
    PLAYERS,
    PLAYER_ACTIONS,
    SELF_TOOLS,
    GIVE_HUB,
    GIVE_PRESETS,
    GIVE_KITS,
    GIVE_MATERIALS,
    SERVER_OPS,
    ECONOMY,
    DEEP_LINKS,
    COMBAT_SKILLS,
    TROLLS,
    CUSTOM_ITEMS,
    CUSTOM_ITEMS_BROWSE,
    CUSTOM_ITEMS_CREATE,
    CUSTOM_ITEMS_CREATE_BASE,
    CUSTOM_ITEMS_CREATE_BUILD,
    CUSTOM_ITEMS_CREATE_ABILITY,
    CUSTOM_ITEMS_CREATE_TRIGGERS,
    CUSTOM_ITEMS_COOLDOWN,
    CUSTOM_ITEMS_COOLDOWN_EDIT,
    CUSTOM_ITEMS_MANAGE
}
