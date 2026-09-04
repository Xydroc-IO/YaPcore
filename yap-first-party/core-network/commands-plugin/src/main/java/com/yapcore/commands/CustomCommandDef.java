package com.yapcore.commands;

import java.util.List;

/** One YAML-defined custom command. */
public record CustomCommandDef(
        String name,
        boolean enabled,
        List<String> aliases,
        String permission,
        String description,
        int cooldownSeconds,
        boolean hideNoPermission,
        List<String> messages,
        List<String> playerCommands,
        List<String> consoleCommands,
        String broadcast
) {
    public CustomCommandDef {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        messages = messages == null ? List.of() : List.copyOf(messages);
        playerCommands = playerCommands == null ? List.of() : List.copyOf(playerCommands);
        consoleCommands = consoleCommands == null ? List.of() : List.copyOf(consoleCommands);
        permission = permission == null ? "" : permission;
        description = description == null ? "" : description;
        broadcast = broadcast == null ? "" : broadcast;
        name = name == null ? "" : name.toLowerCase();
    }

    public String effectivePermission() {
        if (permission != null && !permission.isBlank()) {
            return permission.trim();
        }
        return "yapcommands.cmd." + name;
    }
}
