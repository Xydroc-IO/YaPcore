package com.yapcore.qol;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Loaded view of plugins/YaP-QoL/config.yml. */
public final class QolConfig {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final JavaPlugin plugin;
    private boolean enabled;
    private boolean timberEnabled;
    private int maxLogs;
    private boolean breakLeaves;
    private int maxLeaves;
    private Material timberMaterial;
    private String timberName;
    private List<String> timberLore;
    private boolean timberGlow;
    private boolean timberUnbreakable;
    private boolean excavatorEnabled;
    private List<Integer> excavatorSizes;
    private int defaultSize;
    private int maxBlocksPerSwing;
    private boolean requireCorrectTool;
    private Material excavatorMaterial;
    private String excavatorName;
    private List<String> excavatorLore;
    private boolean excavatorGlow;
    private boolean excavatorUnbreakable;
    private String msgTimberFell;
    private String msgDisabled;

    public QolConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        enabled = c.getBoolean("enabled", true);

        timberEnabled = c.getBoolean("timber.enabled", true);
        maxLogs = Math.max(1, c.getInt("timber.max-logs", 256));
        breakLeaves = c.getBoolean("timber.break-leaves", true);
        maxLeaves = Math.max(0, c.getInt("timber.max-leaves", 512));
        timberMaterial = material(c.getString("timber.material"), Material.NETHERITE_AXE);
        timberName = c.getString("timber.name", "&aTimber Axe");
        timberLore = stringList(c.getStringList("timber.lore"));
        timberGlow = c.getBoolean("timber.glow", true);
        timberUnbreakable = c.getBoolean("timber.unbreakable", false);

        excavatorEnabled = c.getBoolean("excavator.enabled", true);
        excavatorSizes = parseSizes(c.getIntegerList("excavator.sizes"));
        if (excavatorSizes.isEmpty()) {
            excavatorSizes = List.of(3, 6, 9);
        }
        defaultSize = nearestSize(c.getInt("excavator.default-size", 3));
        maxBlocksPerSwing = Math.max(1, c.getInt("excavator.max-blocks-per-swing", 81));
        requireCorrectTool = c.getBoolean("excavator.require-correct-tool", true);
        excavatorMaterial = material(c.getString("excavator.material"), Material.NETHERITE_PICKAXE);
        excavatorName = c.getString("excavator.name", "&bArea Excavator");
        excavatorLore = stringList(c.getStringList("excavator.lore"));
        excavatorGlow = c.getBoolean("excavator.glow", true);
        excavatorUnbreakable = c.getBoolean("excavator.unbreakable", false);

        msgTimberFell = c.getString("messages.timber-fell", "&aFelled &f{count} &alog blocks.");
        msgDisabled = c.getString("messages.disabled", "&cYaP-QoL is disabled.");
    }

    private static Material material(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Material.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static List<String> stringList(List<String> in) {
        return in == null ? List.of() : List.copyOf(in);
    }

    private static List<Integer> parseSizes(List<Integer> raw) {
        List<Integer> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (Integer n : raw) {
            if (n != null && n >= 1 && n <= 15 && !out.contains(n)) {
                out.add(n);
            }
        }
        out.sort(Integer::compareTo);
        return List.copyOf(out);
    }

    public int nearestSize(int wanted) {
        int best = excavatorSizes.getFirst();
        int bestDist = Math.abs(best - wanted);
        for (int s : excavatorSizes) {
            int d = Math.abs(s - wanted);
            if (d < bestDist) {
                best = s;
                bestDist = d;
            }
        }
        return best;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        this.enabled = value;
        plugin.getConfig().set("enabled", value);
        plugin.saveConfig();
    }

    public boolean timberEnabled() {
        return timberEnabled;
    }

    public void setTimberEnabled(boolean value) {
        this.timberEnabled = value;
        plugin.getConfig().set("timber.enabled", value);
        plugin.saveConfig();
    }

    public int maxLogs() {
        return maxLogs;
    }

    public boolean breakLeaves() {
        return breakLeaves;
    }

    public int maxLeaves() {
        return maxLeaves;
    }

    public Material timberMaterial() {
        return timberMaterial;
    }

    public String timberName() {
        return timberName;
    }

    public List<String> timberLore() {
        return timberLore;
    }

    public boolean timberGlow() {
        return timberGlow;
    }

    public boolean timberUnbreakable() {
        return timberUnbreakable;
    }

    public boolean excavatorEnabled() {
        return excavatorEnabled;
    }

    public void setExcavatorEnabled(boolean value) {
        this.excavatorEnabled = value;
        plugin.getConfig().set("excavator.enabled", value);
        plugin.saveConfig();
    }

    public List<Integer> excavatorSizes() {
        return excavatorSizes;
    }

    public int defaultSize() {
        return defaultSize;
    }

    public void setDefaultSize(int value) {
        this.defaultSize = nearestSize(value);
        plugin.getConfig().set("excavator.default-size", this.defaultSize);
        plugin.saveConfig();
    }

    /** Cycle through configured excavator sizes; returns the new default. */
    public int cycleDefaultSize() {
        if (excavatorSizes.isEmpty()) {
            return defaultSize;
        }
        int idx = excavatorSizes.indexOf(defaultSize);
        int next = excavatorSizes.get((idx < 0 ? 0 : idx + 1) % excavatorSizes.size());
        setDefaultSize(next);
        return next;
    }

    public int maxBlocksPerSwing() {
        return maxBlocksPerSwing;
    }

    public boolean requireCorrectTool() {
        return requireCorrectTool;
    }

    public Material excavatorMaterial() {
        return excavatorMaterial;
    }

    public String excavatorName() {
        return excavatorName;
    }

    public List<String> excavatorLore() {
        return excavatorLore;
    }

    public boolean excavatorGlow() {
        return excavatorGlow;
    }

    public boolean excavatorUnbreakable() {
        return excavatorUnbreakable;
    }

    public String msgTimberFell() {
        return msgTimberFell;
    }

    public String msgDisabled() {
        return msgDisabled;
    }

    public static LegacyComponentSerializer legacy() {
        return LEGACY;
    }
}
