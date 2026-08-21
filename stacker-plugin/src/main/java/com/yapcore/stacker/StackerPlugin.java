package com.yapcore.stacker;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

/**
 * Full PDC mob/item/spawner stacker for YaPcore / Paper. No NMS.
 */
public final class StackerPlugin extends JavaPlugin {

    private StackerConfig config;
    private StackerMetrics metrics;
    private HookService hooks;
    private StackService stacks;
    private ItemStackService items;
    private SpawnerStackService spawners;
    private StackerItems tools;
    private AdminGui adminGui;
    private SpawnerGui spawnerGui;
    private StackerTasks tasks;
    private StackerPlaceholders placeholders;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = new StackerConfig(this);
        config.reload();
        metrics = new StackerMetrics();
        hooks = new HookService(config);
        stacks = new StackService(this, config, hooks, metrics);
        items = new ItemStackService(config, stacks.keys(), metrics);
        spawners = new SpawnerStackService(config, stacks.keys(), metrics);
        tools = new StackerItems(config, stacks.keys());

        ToolListener toolListener = new ToolListener(stacks, tools);
        adminGui = new AdminGui(this);
        spawnerGui = new SpawnerGui(spawners);

        getServer().getPluginManager().registerEvents(new MergeListener(stacks, items), this);
        getServer().getPluginManager().registerEvents(new EntityDeathListener(this, stacks), this);
        getServer().getPluginManager().registerEvents(new SpawnerListener(this, spawners, tools), this);
        getServer().getPluginManager().registerEvents(toolListener, this);
        getServer().getPluginManager().registerEvents(adminGui, this);
        getServer().getPluginManager().registerEvents(spawnerGui, this);

        tasks = new StackerTasks(this, stacks, items, toolListener);
        tasks.start();

        registerPlaceholders();

        getLogger().info("YaP Stacker online — mobs=" + config.mobsEnabled()
                + " items=" + config.itemsEnabled()
                + " spawners=" + config.spawnersEnabled()
                + " kill-mode=" + config.killMode());
        getLogger().info("Hooks: Citizens=" + hooks.citizensPresent()
                + " MythicMobs=" + hooks.mythicPresent()
                + " PAPI=" + (placeholders != null && placeholders.isRegistered()));
        getLogger().info("Edit plugins/YaPStacker/config.yml · /yapstacker gui");
    }

    @Override
    public void onDisable() {
        if (tasks != null) {
            tasks.stop();
        }
        if (placeholders != null) {
            try {
                placeholders.unregister();
            } catch (Throwable ignored) {
            }
            placeholders = null;
        }
        stacks = null;
    }

    private void registerPlaceholders() {
        if (!config.placeholderApi()) {
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            placeholders = new StackerPlaceholders(this);
            if (placeholders.register()) {
                getLogger().info("Registered PlaceholderAPI expansion %yapstacker_%");
            } else {
                placeholders = null;
            }
        } catch (NoClassDefFoundError | Exception e) {
            getLogger().warning("Could not register PlaceholderAPI expansion: " + e.getMessage());
            placeholders = null;
        }
    }

    public StackerConfig stackerConfig() {
        return config;
    }

    public StackService stacks() {
        return stacks;
    }

    public ItemStackService items() {
        return items;
    }

    public SpawnerStackService spawners() {
        return spawners;
    }

    public StackerItems tools() {
        return tools;
    }

    public StackerMetrics metrics() {
        return metrics;
    }

    public SpawnerGui spawnerGui() {
        return spawnerGui;
    }

    public AdminGui adminGui() {
        return adminGui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("yapstacker")) {
            return false;
        }
        if (args.length == 0 || "status".equalsIgnoreCase(args[0])) {
            sender.sendMessage("YaPStacker enabled=" + config.enabled()
                    + " mobs=" + config.mobsEnabled()
                    + " items=" + config.itemsEnabled()
                    + " spawners=" + config.spawnersEnabled()
                    + " kill-mode=" + config.killMode());
            return true;
        }
        if (!sender.hasPermission("yapstacker.admin")
                && !"gui".equalsIgnoreCase(args[0])) {
            sender.sendMessage("No permission.");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                reloadConfig();
                config.reload();
                tasks.stop();
                tasks.start();
                sender.sendMessage("YaP Stacker reloaded.");
            }
            case "gui" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                if (!player.hasPermission("yapstacker.gui") && !player.hasPermission("yapstacker.admin")) {
                    player.sendMessage("No permission.");
                    return true;
                }
                adminGui.open(player);
            }
            case "stats" -> sender.sendMessage(
                    "merges=" + metrics.mobMerges()
                            + " kills=" + metrics.mobKillsProcessed()
                            + " items=" + metrics.itemMerges()
                            + " spawners=" + metrics.spawnerStacks()
                            + " aura=" + metrics.auraKills());
            case "give" -> {
                if (!sender.hasPermission("yapstacker.give") && !sender.hasPermission("yapstacker.admin")) {
                    sender.sendMessage("No permission.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("Usage: /yapstacker give <wand|tool|aura> [player]");
                    return true;
                }
                Player target = args.length >= 3
                        ? Bukkit.getPlayerExact(args[2])
                        : (sender instanceof Player p ? p : null);
                if (target == null) {
                    sender.sendMessage("Player not found.");
                    return true;
                }
                try {
                    tools.give(target, args[1]);
                    sender.sendMessage("Gave " + args[1] + " to " + target.getName());
                } catch (IllegalArgumentException e) {
                    sender.sendMessage("Unknown tool. Use wand|tool|aura");
                }
            }
            default -> sender.sendMessage(
                    "Usage: /yapstacker <reload|status|gui|stats|give <wand|tool|aura> [player]>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("yapstacker")) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("reload", "status", "gui", "stats", "give").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && "give".equalsIgnoreCase(args[0])) {
            return List.of("wand", "tool", "aura").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
