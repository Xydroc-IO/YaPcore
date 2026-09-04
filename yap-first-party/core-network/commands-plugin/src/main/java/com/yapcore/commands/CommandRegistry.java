package com.yapcore.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/** Load / register / unregister YAML custom commands. */
public final class CommandRegistry {

    private final CommandsPlugin plugin;
    private final List<DynamicCustomCommand> registered = new ArrayList<>();
    private final Map<String, CustomCommandDef> defs = new LinkedHashMap<>();
    private boolean requireUsePerm = true;

    public CommandRegistry(CommandsPlugin plugin) {
        this.plugin = plugin;
    }

    public Map<String, CustomCommandDef> defs() {
        return Map.copyOf(defs);
    }

    public boolean requireUsePerm() {
        return requireUsePerm;
    }

    public synchronized void reload() {
        unregisterAll();
        defs.clear();
        File file = new File(plugin.getDataFolder(), "commands.yml");
        if (!file.exists()) {
            plugin.saveResource("commands.yml", false);
        }
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        requireUsePerm = yaml.getBoolean("require-use-perm", true);
        ConfigurationSection root = yaml.getConfigurationSection("commands");
        if (root == null) {
            plugin.getLogger().info("No custom commands defined in commands.yml");
            return;
        }
        CommandMap map = Bukkit.getCommandMap();
        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }
            String name = key.toLowerCase(Locale.ROOT).trim();
            if (name.isEmpty() || "yapcommands".equals(name) || "ycmd".equals(name) || "customcmd".equals(name)) {
                plugin.getLogger().warning("Skipping reserved command name: " + key);
                continue;
            }
            CustomCommandDef def = new CustomCommandDef(
                    name,
                    sec.getBoolean("enabled", true),
                    sec.getStringList("aliases"),
                    sec.getString("permission", ""),
                    sec.getString("description", ""),
                    Math.max(0, sec.getInt("cooldown-seconds", 0)),
                    sec.getBoolean("hide-no-permission", true),
                    sec.getStringList("messages"),
                    sec.getStringList("player-commands"),
                    sec.getStringList("console-commands"),
                    sec.getString("broadcast", "")
            );
            defs.put(name, def);
            if (!def.enabled()) {
                continue;
            }
            DynamicCustomCommand cmd = new DynamicCustomCommand(plugin, def);
            map.register("yapcommands", cmd);
            registered.add(cmd);
        }
        plugin.getLogger().info("Registered " + registered.size() + " custom command(s)");
    }

    @SuppressWarnings("unchecked")
    public synchronized void unregisterAll() {
        CommandMap map = Bukkit.getCommandMap();
        try {
            Field knownField = map.getClass().getDeclaredField("knownCommands");
            knownField.setAccessible(true);
            Map<String, Command> known = (Map<String, Command>) knownField.get(map);
            for (DynamicCustomCommand cmd : registered) {
                known.values().removeIf(c -> c == cmd);
                known.remove(cmd.getName().toLowerCase(Locale.ROOT));
                known.remove("yapcommands:" + cmd.getName().toLowerCase(Locale.ROOT));
                for (String alias : cmd.getAliases()) {
                    known.remove(alias.toLowerCase(Locale.ROOT));
                    known.remove("yapcommands:" + alias.toLowerCase(Locale.ROOT));
                }
            }
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().log(Level.WARNING, "Could not fully unregister custom commands", e);
        }
        registered.clear();
    }

    public void saveDef(CustomCommandDef def) throws IOException {
        File file = new File(plugin.getDataFolder(), "commands.yml");
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String path = "commands." + def.name();
        yaml.set(path + ".enabled", def.enabled());
        yaml.set(path + ".aliases", def.aliases());
        yaml.set(path + ".permission", def.permission());
        yaml.set(path + ".description", def.description());
        yaml.set(path + ".cooldown-seconds", def.cooldownSeconds());
        yaml.set(path + ".hide-no-permission", def.hideNoPermission());
        yaml.set(path + ".messages", def.messages());
        yaml.set(path + ".player-commands", def.playerCommands());
        yaml.set(path + ".console-commands", def.consoleCommands());
        yaml.set(path + ".broadcast", def.broadcast());
        yaml.save(file);
    }

    public void deleteDef(String name) throws IOException {
        File file = new File(plugin.getDataFolder(), "commands.yml");
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set("commands." + name.toLowerCase(Locale.ROOT), null);
        yaml.save(file);
    }

    public void bindAdmin(PluginCommand admin) {
        if (admin == null) {
            return;
        }
        CommandsAdmin handler = new CommandsAdmin(plugin);
        admin.setExecutor(handler);
        admin.setTabCompleter(handler);
    }
}
