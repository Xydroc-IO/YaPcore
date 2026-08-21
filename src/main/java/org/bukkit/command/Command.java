package org.bukkit.command;

import java.util.Collections;
import java.util.List;

public abstract class Command {

    private final String name;
    private String description = "";
    private String usage = "";
    private List<String> aliases = List.of();
    private String permission;

    protected Command(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Command setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getUsage() {
        return usage;
    }

    public Command setUsage(String usage) {
        this.usage = usage;
        return this;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public Command setAliases(List<String> aliases) {
        this.aliases = aliases != null ? List.copyOf(aliases) : List.of();
        return this;
    }

    public String getPermission() {
        return permission;
    }

    public Command setPermission(String permission) {
        this.permission = permission;
        return this;
    }

    public abstract boolean execute(CommandSender sender, String commandLabel, String[] args);

    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        return Collections.emptyList();
    }
}
