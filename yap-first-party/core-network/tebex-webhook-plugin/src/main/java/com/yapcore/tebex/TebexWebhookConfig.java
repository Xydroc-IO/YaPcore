package com.yapcore.tebex;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loaded {@code config.yml} for Tebex webhook inbound. */
public final class TebexWebhookConfig {

    public static final Set<String> TEBEX_SOURCE_IPS = Set.of(
            "18.209.80.3",
            "54.87.231.232"
    );

    private final JavaPlugin plugin;
    private boolean inboundEnabled;
    private String inboundBind = "127.0.0.1";
    private int inboundPort = 8766;
    private String inboundPath = "/tebex/webhook";
    private String inboundSecret = "change-me";
    private boolean enforceTebexIps = true;
    private int maxBodyBytes = 1_048_576;
    private int dedupeRetentionDays = 30;
    private Map<String, List<String>> packages = Map.of();

    public TebexWebhookConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        inboundEnabled = c.getBoolean("inbound.enabled", false);
        inboundBind = c.getString("inbound.bind", "127.0.0.1");
        if (inboundBind == null || inboundBind.isBlank()) {
            inboundBind = "127.0.0.1";
        }
        inboundPort = c.getInt("inbound.port", 8766);
        inboundPath = c.getString("inbound.path", "/tebex/webhook");
        if (inboundPath == null || inboundPath.isBlank()) {
            inboundPath = "/tebex/webhook";
        }
        if (!inboundPath.startsWith("/")) {
            inboundPath = "/" + inboundPath;
        }
        inboundSecret = c.getString("inbound.secret", "change-me");
        enforceTebexIps = c.getBoolean("inbound.enforce-tebex-ips", true);
        maxBodyBytes = Math.max(4096, c.getInt("inbound.max-body-bytes", 1_048_576));
        dedupeRetentionDays = Math.max(1, c.getInt("dedupe.retention-days", 30));

        Map<String, List<String>> map = new LinkedHashMap<>();
        ConfigurationSection section = c.getConfigurationSection("packages");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                List<String> cmds = section.getStringList(key);
                if (cmds == null || cmds.isEmpty()) {
                    String single = section.getString(key);
                    if (single != null && !single.isBlank()) {
                        cmds = List.of(single);
                    } else {
                        cmds = List.of();
                    }
                }
                List<String> cleaned = new ArrayList<>();
                for (String cmd : cmds) {
                    if (cmd != null && !cmd.isBlank()) {
                        cleaned.add(cmd.trim());
                    }
                }
                if (!cleaned.isEmpty()) {
                    map.put(key.trim(), List.copyOf(cleaned));
                }
            }
        }
        packages = Collections.unmodifiableMap(map);
    }

    public boolean inboundEnabled() {
        return inboundEnabled;
    }

    public String inboundBind() {
        return inboundBind;
    }

    public int inboundPort() {
        return inboundPort;
    }

    public String inboundPath() {
        return inboundPath == null || inboundPath.isBlank() ? "/tebex/webhook" : inboundPath;
    }

    public String inboundSecret() {
        return inboundSecret == null ? "" : inboundSecret;
    }

    public boolean enforceTebexIps() {
        return enforceTebexIps;
    }

    public int maxBodyBytes() {
        return maxBodyBytes;
    }

    public int dedupeRetentionDays() {
        return dedupeRetentionDays;
    }

    public Map<String, List<String>> packages() {
        return packages;
    }

    public List<String> commandsForPackage(String packageId) {
        if (packageId == null) {
            return List.of();
        }
        List<String> cmds = packages.get(packageId.trim());
        return cmds == null ? List.of() : cmds;
    }

    /** True when inbound is enabled but secret is still the unsafe default / blank. */
    public boolean inboundSecretUnsafe() {
        return inboundSecret == null || inboundSecret.isBlank() || "change-me".equals(inboundSecret);
    }
}
