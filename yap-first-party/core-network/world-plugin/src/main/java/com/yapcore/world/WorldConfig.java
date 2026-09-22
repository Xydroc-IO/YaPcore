package com.yapcore.world;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Typed config for YaPWorld — worlds, edit limits, resource regen. */
public final class WorldConfig {

    private final JavaPlugin plugin;
    private boolean allowLoad = true;
    private boolean allowUnload = true;
    private boolean selectionEnabled = true;
    private long maxVolume = 2_000_000L;
    private boolean schematicsEnabled = true;
    private String schematicsFolder = "schematics";
    private int maxBrushRadius = 32;
    private int undoSessions = 25;
    private String serverId = "lobby";
    private boolean editorEnabled = true;
    private int editorPort = 8092;
    private String editorBind = "0.0.0.0";
    private String editorPublicHost = "127.0.0.1";
    private long maxChanges = 2_000_000L;
    private int maxRadius = 128;
    private int parallelChunks = 4;
    private int parallelChunksLarge = 12;
    private int largePasteBlocks = 50_000;
    private boolean autoFastLarge = true;
    private boolean deferRelightLarge = true;
    private boolean progressMessages = true;
    private boolean cuiEnabled = true;
    private boolean clipboardWebEnabled = true;
    private boolean autoRelight = false;
    private boolean schemUseBlockBatch = true;

    private Map<String, ResourceWorldSpec> resourceWorlds = Map.of();
    private boolean replenishEnabled = false;
    private int replenishDelaySeconds = 300;
    private Set<Material> replenishMaterials = Set.of();
    private Set<String> replenishWorlds = Set.of();
    private Set<String> replenishExcludeWorlds = Set.of();

    public WorldConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        allowLoad = c.getBoolean("worlds.allow-load", true);
        allowUnload = c.getBoolean("worlds.allow-unload", true);
        selectionEnabled = c.getBoolean("selection.enabled", true);
        maxVolume = Math.max(1L, c.getLong("selection.max-volume", maxVolume));
        schematicsEnabled = c.getBoolean("schematics.enabled", true);
        schematicsFolder = c.getString("schematics.folder", schematicsFolder);
        maxBrushRadius = Math.max(1, Math.min(64, c.getInt("brush.max-radius", maxBrushRadius)));
        undoSessions = Math.max(1, Math.min(100, c.getInt("undo.max-sessions", undoSessions)));
        serverId = c.getString("server-id", serverId);
        editorEnabled = c.getBoolean("editor.enabled", editorEnabled);
        editorPort = Math.max(1024, Math.min(65535, c.getInt("editor.port", editorPort)));
        editorBind = c.getString("editor.bind", editorBind);
        editorPublicHost = c.getString("editor.public-host", editorPublicHost);
        maxChanges = Math.max(1L, c.getLong("limits.max-changes", maxChanges));
        maxRadius = Math.max(1, Math.min(512, c.getInt("limits.max-radius", maxRadius)));
        parallelChunks = Math.max(1, Math.min(32, c.getInt("limits.parallel-chunks", parallelChunks)));
        parallelChunksLarge = Math.max(1, Math.min(48, c.getInt("limits.parallel-chunks-large", parallelChunksLarge)));
        largePasteBlocks = Math.max(1_000, c.getInt("limits.large-paste-blocks", largePasteBlocks));
        autoFastLarge = c.getBoolean("limits.auto-fast-large", true);
        deferRelightLarge = c.getBoolean("limits.defer-relight-large", true);
        progressMessages = c.getBoolean("limits.progress-messages", true);
        cuiEnabled = c.getBoolean("cui.enabled", true);
        clipboardWebEnabled = c.getBoolean("editor.clipboard-web", true);
        autoRelight = c.getBoolean("limits.auto-relight", false);
        schemUseBlockBatch = c.getBoolean("limits.schem-use-block-batch", true);
        loadResource(c);
    }

    private void loadResource(FileConfiguration c) {
        Map<String, ResourceWorldSpec> worlds = new LinkedHashMap<>();
        ConfigurationSection section = c.getConfigurationSection("resource.worlds");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection w = section.getConfigurationSection(key);
                if (w == null) {
                    continue;
                }
                String name = w.getString("name", key);
                if (name == null || name.isBlank()) {
                    continue;
                }
                List<Integer> announce = new ArrayList<>();
                for (int m : w.getIntegerList("announce-minutes")) {
                    if (m > 0) {
                        announce.add(m);
                    }
                }
                if (announce.isEmpty()) {
                    announce = List.of(60, 15, 5);
                }
                Long seed = w.contains("seed") ? w.getLong("seed") : null;
                worlds.put(name.toLowerCase(Locale.ROOT), new ResourceWorldSpec(
                        name.trim(),
                        Math.max(0L, w.getLong("reset-interval-hours", 24L)),
                        List.copyOf(announce),
                        w.getString("environment", "NORMAL"),
                        w.getString("type", "NORMAL"),
                        seed,
                        w.getBoolean("generate-structures", true),
                        w.getBoolean("deny-claims", true)));
            }
        }
        resourceWorlds = worlds.isEmpty() ? Map.of() : Map.copyOf(worlds);

        replenishEnabled = c.getBoolean("resource.wilderness-replenish.enabled", false);
        replenishDelaySeconds = Math.max(30, c.getInt("resource.wilderness-replenish.delay-seconds", 300));
        EnumSet<Material> mats = EnumSet.noneOf(Material.class);
        for (String raw : c.getStringList("resource.wilderness-replenish.materials")) {
            Material m = Material.matchMaterial(raw == null ? "" : raw.trim());
            if (m != null && m.isBlock()) {
                mats.add(m);
            }
        }
        if (mats.isEmpty() && replenishEnabled) {
            mats.add(Material.COAL_ORE);
            mats.add(Material.DEEPSLATE_COAL_ORE);
            mats.add(Material.IRON_ORE);
            mats.add(Material.DEEPSLATE_IRON_ORE);
            mats.add(Material.COPPER_ORE);
            mats.add(Material.DEEPSLATE_COPPER_ORE);
            mats.add(Material.GOLD_ORE);
            mats.add(Material.DEEPSLATE_GOLD_ORE);
            mats.add(Material.REDSTONE_ORE);
            mats.add(Material.DEEPSLATE_REDSTONE_ORE);
            mats.add(Material.LAPIS_ORE);
            mats.add(Material.DEEPSLATE_LAPIS_ORE);
            mats.add(Material.DIAMOND_ORE);
            mats.add(Material.DEEPSLATE_DIAMOND_ORE);
            mats.add(Material.EMERALD_ORE);
            mats.add(Material.DEEPSLATE_EMERALD_ORE);
        }
        replenishMaterials = mats.isEmpty() ? Set.of() : Set.copyOf(mats);
        replenishWorlds = lowerSet(c.getStringList("resource.wilderness-replenish.worlds"));
        replenishExcludeWorlds = lowerSet(c.getStringList("resource.wilderness-replenish.exclude-worlds"));
    }

    private static Set<String> lowerSet(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (String s : raw) {
            if (s != null && !s.isBlank()) {
                out.add(s.trim().toLowerCase(Locale.ROOT));
            }
        }
        return out.isEmpty() ? Set.of() : Set.copyOf(out);
    }

    public boolean allowLoad() {
        return allowLoad;
    }

    public boolean allowUnload() {
        return allowUnload;
    }

    public boolean selectionEnabled() {
        return selectionEnabled;
    }

    public long maxVolume() {
        return maxVolume;
    }

    public boolean schematicsEnabled() {
        return schematicsEnabled;
    }

    public String schematicsFolder() {
        return schematicsFolder;
    }

    public int maxBrushRadius() {
        return maxBrushRadius;
    }

    public int undoSessions() {
        return undoSessions;
    }

    public String serverId() {
        return serverId;
    }

    public boolean editorEnabled() {
        return editorEnabled;
    }

    public int editorPort() {
        return editorPort;
    }

    public String editorBind() {
        return editorBind;
    }

    public String editorPublicHost() {
        return editorPublicHost;
    }

    public long maxChanges() {
        return maxChanges;
    }

    public int maxRadius() {
        return maxRadius;
    }

    public int parallelChunks() {
        return parallelChunks;
    }

    public int parallelChunksLarge() {
        return parallelChunksLarge;
    }

    public int largePasteBlocks() {
        return largePasteBlocks;
    }

    public boolean autoFastLarge() {
        return autoFastLarge;
    }

    public boolean deferRelightLarge() {
        return deferRelightLarge;
    }

    public boolean progressMessages() {
        return progressMessages;
    }

    public boolean cuiEnabled() {
        return cuiEnabled;
    }

    public boolean clipboardWebEnabled() {
        return clipboardWebEnabled;
    }

    public boolean autoRelight() {
        return autoRelight;
    }

    public boolean schemUseBlockBatch() {
        return schemUseBlockBatch;
    }

    public Map<String, ResourceWorldSpec> resourceWorlds() {
        return resourceWorlds;
    }

    public Optional<ResourceWorldSpec> resourceWorld(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(resourceWorlds.get(name.trim().toLowerCase(Locale.ROOT)));
    }

    public boolean replenishEnabled() {
        return replenishEnabled;
    }

    public int replenishDelaySeconds() {
        return replenishDelaySeconds;
    }

    public Set<Material> replenishMaterials() {
        return replenishMaterials;
    }

    public boolean replenishAppliesTo(String worldName) {
        if (!replenishEnabled || worldName == null) {
            return false;
        }
        String key = worldName.toLowerCase(Locale.ROOT);
        if (replenishExcludeWorlds.contains(key)) {
            return false;
        }
        if (resourceWorlds.containsKey(key)) {
            return false;
        }
        return replenishWorlds.isEmpty() || replenishWorlds.contains(key);
    }

    public boolean isReplenishMaterial(Material material) {
        return material != null && replenishMaterials.contains(material);
    }
}
