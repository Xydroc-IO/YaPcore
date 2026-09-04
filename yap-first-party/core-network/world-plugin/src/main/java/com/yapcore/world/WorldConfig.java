package com.yapcore.world;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

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
}
