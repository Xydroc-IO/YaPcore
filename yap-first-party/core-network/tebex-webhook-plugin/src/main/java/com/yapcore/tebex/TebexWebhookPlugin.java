package com.yapcore.tebex;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Hub Folia first-party Tebex webhook inbound plugin ({@code yap-tebex.jar}).
 * Coexists with official GPLv3 {@code tebex.jar} (GUI / forcecheck).
 */
public final class TebexWebhookPlugin extends JavaPlugin {

    private TebexWebhookConfig config;
    private TebexWebhookServer inboundServer;
    private TebexWebhookDedupe dedupe;
    private TebexWebhookStatusStore statusStore;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = new TebexWebhookConfig(this);
        statusStore = new TebexWebhookStatusStore(getDataFolder().toPath());
        dedupe = new TebexWebhookDedupe(getDataFolder().toPath(), getLogger());
        inboundServer = new TebexWebhookServer(this);
        PluginCommand cmd = getCommand("yaptebex");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
        reloadWebhook();
        getLogger().info("YaPTebex webhook inbound ready (use /yaptebex reload).");
    }

    @Override
    public void onDisable() {
        if (inboundServer != null) {
            inboundServer.stop();
        }
        if (dedupe != null) {
            dedupe.close();
        }
    }

    public void reloadWebhook() {
        config.reload();
        try {
            dedupe.open(config.dedupeRetentionDays());
        } catch (SQLException | java.io.IOException e) {
            getLogger().warning("Tebex dedupe DB failed: " + e.getMessage());
        }
        inboundServer.start(config);
    }

    public TebexWebhookConfig config() {
        return config;
    }

    public TebexWebhookDedupe dedupe() {
        return dedupe;
    }

    public TebexWebhookStatusStore statusStore() {
        return statusStore;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("yaptebex")) {
            return false;
        }
        if (!sender.hasPermission("yaptebex.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                reloadWebhook();
                sender.sendMessage("§aYaPTebex reloaded. Listening="
                        + (inboundServer != null && inboundServer.isRunning()));
            }
            case "status" -> {
                boolean enabled = config != null && config.inboundEnabled();
                boolean listening = inboundServer != null && inboundServer.isRunning();
                sender.sendMessage("§7YaPTebex inbound enabled=" + enabled
                        + " listening=" + listening
                        + " packages=" + (config == null ? 0 : config.packages().size()));
                if (config != null) {
                    sender.sendMessage("§7  "
                            + config.inboundBind() + ":" + config.inboundPort() + config.inboundPath());
                }
                Map<String, Object> last = statusStore == null ? Map.of() : statusStore.snapshot();
                if (!last.isEmpty()) {
                    sender.sendMessage("§7  last: " + last.getOrDefault("type", "")
                            + " ok=" + last.getOrDefault("ok", "")
                            + " " + last.getOrDefault("detail", ""));
                }
            }
            default -> sender.sendMessage("§eUsage: /yaptebex reload|status");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("yaptebex") || !sender.hasPermission("yaptebex.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            return List.of("reload", "status").stream().filter(s -> s.startsWith(p)).toList();
        }
        return List.of();
    }
}
