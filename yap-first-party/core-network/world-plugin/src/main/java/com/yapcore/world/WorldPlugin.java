package com.yapcore.world;

import com.yapcore.world.cmd.WorldCommands;
import com.yapcore.world.cmd.WorldEditOps;
import com.yapcore.world.edit.BrushService;
import com.yapcore.world.edit.ClipboardService;
import com.yapcore.world.edit.GenerationService;
import com.yapcore.world.edit.SelectionEditService;
import com.yapcore.world.edit.UndoService;
import com.yapcore.world.gui.WorldEditGui;
import com.yapcore.world.gui.WorldEditGuiListener;
import com.yapcore.world.listener.BrushListener;
import com.yapcore.world.listener.SelectionWandListener;
import com.yapcore.world.listener.WorldEditSlashBridge;
import com.yapcore.world.listener.WorldEditToolListener;
import com.yapcore.world.schem.SchematicPaster;
import com.yapcore.world.service.SelectionServiceImpl;
import com.yapcore.world.service.WorldManagerServiceImpl;
import com.yapcore.world.tool.WorldEditTool;
import com.yapcore.world.web.WorldEditBrowser;
import com.yapcore.world.web.WorldEditHttpServer;
import com.yapcore.world.web.WorldEditSessionRegistry;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Path;

public final class WorldPlugin extends JavaPlugin {

    private WorldConfig config;
    private WorldManagerServiceImpl worldManager;
    private SelectionServiceImpl selection;
    private SchematicPaster paster;
    private UndoService undoService;
    private BrushService brushService;
    private SelectionEditService selectionEditService;
    private ClipboardService clipboardService;
    private GenerationService generationService;
    private WorldEditOps editOps;
    private WorldEditTool worldEditTool;
    private WorldEditGui worldEditGui;
    private WorldEditSessionRegistry editSessions;
    private WorldEditHttpServer editHttp;
    private WorldCommands commands;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadWorld();

        getServer().getPluginManager().registerEvents(new SelectionWandListener(config, selection), this);
        getServer().getPluginManager().registerEvents(new BrushListener(brushService), this);
        getServer().getPluginManager().registerEvents(
                new WorldEditToolListener(config, selection, brushService, worldEditTool, this::openInGameGui), this);
        getServer().getPluginManager().registerEvents(
                new WorldEditGuiListener(this, config, worldEditGui, selection, brushService,
                        selectionEditService, clipboardService, generationService, undoService, paster), this);
        getServer().getPluginManager().registerEvents(new WorldEditSlashBridge(this), this);

        var sm = getServer().getServicesManager();
        sm.register(com.yapcore.world.WorldManagerService.class, worldManager, this, ServicePriority.Normal);
        sm.register(com.yapcore.world.SelectionService.class, selection, this, ServicePriority.Normal);

        PluginCommand cmd = getCommand("yapworld");
        if (cmd != null) {
            cmd.setExecutor(commands);
            cmd.setTabCompleter(commands);
        }
        getLogger().info("YaPWorld ready — //set //copy /yapworld gui (Folia-safe WorldEdit-class).");
    }

    public void reloadWorld() {
        config = new WorldConfig(this);
        config.reload();
        if (worldManager == null) {
            worldManager = new WorldManagerServiceImpl(this, config);
        } else {
            worldManager.setConfig(config);
        }
        if (selection == null) {
            selection = new SelectionServiceImpl(config);
        } else {
            selection.setConfig(config);
        }
        if (paster == null) {
            paster = new SchematicPaster(this);
        }
        undoService = new UndoService(this, config.undoSessions());
        brushService = new BrushService(this, undoService);
        selectionEditService = new SelectionEditService(this, undoService);
        clipboardService = new ClipboardService(this, undoService);
        generationService = new GenerationService(this, undoService);
        editOps = new WorldEditOps(this, selection, selectionEditService, generationService,
                clipboardService, undoService, brushService);
        worldEditTool = new WorldEditTool(this);
        worldEditGui = new WorldEditGui(this, config, selection, worldEditTool);
        if (editSessions == null) {
            editSessions = new WorldEditSessionRegistry();
        }
        restartEditorHttp();
        commands = new WorldCommands(this, config, worldManager, selection, paster, brushService,
                undoService, selectionEditService, editOps, worldEditTool, worldEditGui);
    }

    private void restartEditorHttp() {
        if (editHttp != null) {
            editHttp.stop();
            editHttp = null;
        }
        if (!config.editorEnabled()) {
            return;
        }
        editHttp = new WorldEditHttpServer(this, config, editSessions, worldManager, selection, brushService,
                selectionEditService, undoService, paster, worldEditTool);
        try {
            editHttp.start();
        } catch (IOException e) {
            getLogger().warning("World edit studio failed to start: " + e.getMessage());
            editHttp = null;
        }
    }

    /** Primary in-game editor (inventory GUI). */
    public void openInGameGui(Player player) {
        if (!player.hasPermission("yapworld.selection") && !player.hasPermission("yapworld.brush")
                && !player.hasPermission("yapworld.admin")) {
            player.sendMessage("§cNo permission.");
            return;
        }
        if (!config.selectionEnabled()) {
            player.sendMessage("§cWorld edit is disabled on this server.");
            return;
        }
        worldEditGui.openMain(player);
    }

    /** Optional browser World Edit Studio. */
    public void openBrowserEditor(Player player) {
        if (!canUseEditor(player)) {
            player.sendMessage("§cNo permission.");
            return;
        }
        if (!config.selectionEnabled()) {
            player.sendMessage("§cWorld edit is disabled on this server.");
            return;
        }
        if (!config.editorEnabled() || editHttp == null) {
            player.sendMessage("§cBrowser editor is disabled. Enable editor.enabled in config.yml.");
            return;
        }
        String token = editSessions.openSession(player.getUniqueId(), player.getName());
        WorldEditBrowser.openEditor(player, editHttp.editorUrl(token));
    }

    public static boolean canUseEditor(Player player) {
        return player.hasPermission("yapworld.editor") || player.hasPermission("yapworld.selection");
    }

    public WorldEditGui gui() {
        return worldEditGui;
    }

    public WorldEditOps editOps() {
        return editOps;
    }

    public ClipboardService clipboard() {
        return clipboardService;
    }

    public GenerationService generation() {
        return generationService;
    }

    public SelectionEditService selectionEdit() {
        return selectionEditService;
    }

    public Path schematicsDir() {
        Path dir = getDataFolder().toPath().resolve(config.schematicsFolder());
        dir.toFile().mkdirs();
        return dir;
    }

    @Override
    public void onDisable() {
        if (editHttp != null) {
            editHttp.stop();
            editHttp = null;
        }
        var sm = getServer().getServicesManager();
        if (worldManager != null) {
            sm.unregister(com.yapcore.world.WorldManagerService.class, worldManager);
        }
        if (selection != null) {
            sm.unregister(com.yapcore.world.SelectionService.class, selection);
        }
    }
}
