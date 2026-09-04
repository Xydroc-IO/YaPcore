package com.yapcore.world;

import com.sk89q.worldedit.extent.EditSession;
import com.yapcore.world.cmd.WorldCommands;
import com.yapcore.world.cmd.WorldEditOps;
import com.yapcore.world.cui.WorldEditCuiBridge;
import com.yapcore.world.edit.BrushService;
import com.yapcore.world.edit.ClipboardService;
import com.yapcore.world.edit.GenerationService;
import com.yapcore.world.edit.MaskEngine;
import com.yapcore.world.edit.PlayerEditState;
import com.yapcore.world.edit.SelectionEditService;
import com.yapcore.world.edit.SelectionShape;
import com.yapcore.world.edit.TerrainService;
import com.yapcore.world.edit.UndoService;
import com.yapcore.world.edit.WorldEditBridge;
import com.yapcore.world.gui.WorldEditGui;
import com.yapcore.world.gui.WorldEditGuiListener;
import com.yapcore.world.listener.BrushListener;
import com.yapcore.world.listener.SelectionWandListener;
import com.yapcore.world.listener.ToolModeListener;
import com.yapcore.world.listener.WorldEditSlashBridge;
import com.yapcore.world.listener.WorldEditToolListener;
import com.yapcore.world.schem.SchematicPaster;
import com.yapcore.world.service.EditApplyServiceImpl;
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
    private TerrainService terrainService;
    private MaskEngine maskEngine;
    private SelectionShape selectionShape;
    private PlayerEditState playerEditState;
    private WorldEditOps editOps;
    private WorldEditTool worldEditTool;
    private WorldEditGui worldEditGui;
    private WorldEditSessionRegistry editSessions;
    private WorldEditHttpServer editHttp;
    private WorldCommands commands;
    private EditApplyServiceImpl editApply;
    private WorldEditBridge worldEditBridge;
    private WorldEditCuiBridge cuiBridge;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadWorld();

        getServer().getPluginManager().registerEvents(
                new SelectionWandListener(config, selection, selectionShape, this::notifyCui), this);
        getServer().getPluginManager().registerEvents(new BrushListener(brushService), this);
        getServer().getPluginManager().registerEvents(
                new WorldEditToolListener(config, selection, brushService, worldEditTool, this::openInGameGui), this);
        getServer().getPluginManager().registerEvents(
                new WorldEditGuiListener(this, config, worldEditGui, selection, brushService,
                        selectionEditService, clipboardService, generationService, undoService, paster), this);
        getServer().getPluginManager().registerEvents(new WorldEditSlashBridge(this), this);
        getServer().getPluginManager().registerEvents(
                new ToolModeListener(this, playerEditState, selection, selectionShape, terrainService), this);

        var sm = getServer().getServicesManager();
        sm.register(com.yapcore.world.WorldManagerService.class, worldManager, this, ServicePriority.Normal);
        sm.register(com.yapcore.world.SelectionService.class, selection, this, ServicePriority.Normal);
        sm.register(com.yapcore.world.EditApplyService.class, editApply, this, ServicePriority.Normal);

        PluginCommand cmd = getCommand("yapworld");
        if (cmd != null) {
            cmd.setExecutor(commands);
            cmd.setTabCompleter(commands);
        }
        getLogger().info("YaPWorld ready — FAWE-class Phase 5 (clipboard entities/flags, generate, brushes, schem interop).");
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
        if (maskEngine == null) {
            maskEngine = new MaskEngine();
        }
        if (selectionShape == null) {
            selectionShape = new SelectionShape();
        }
        if (playerEditState == null) {
            playerEditState = new PlayerEditState();
        }
        undoService = new UndoService(this, config.undoSessions());
        brushService = new BrushService(this, undoService);
        brushService.setMaxRadius(config.maxBrushRadius());
        brushService.setParallelChunks(config.parallelChunks());
        brushService.setMasks(maskEngine);
        brushService.setEditState(playerEditState);

        selectionEditService = new SelectionEditService(this, undoService);
        selectionEditService.setMasks(maskEngine);
        selectionEditService.setShapes(selectionShape);
        selectionEditService.setMaxChanges(config.maxChanges());
        selectionEditService.setEditState(playerEditState);
        selectionEditService.setParallelChunks(config.parallelChunks());
        selectionEditService.setLargePasteTuning(
                config.largePasteBlocks(), config.parallelChunksLarge(), config.autoFastLarge());
        if (config.progressMessages()) {
            selectionEditService.setProgressListener((uuid, blocks, total, chunksDone, chunksTotal) -> {
                Player p = getServer().getPlayer(uuid);
                if (p == null || !p.isOnline() || chunksTotal < 4) {
                    return;
                }
                if (chunksDone == chunksTotal || chunksDone % Math.max(1, chunksTotal / 10) == 0) {
                    int pct = total <= 0 ? 0 : (int) Math.min(100, (blocks * 100L) / total);
                    p.sendMessage("§7Edit… §f" + pct + "% §7(§f" + chunksDone + "§7/§f" + chunksTotal + " §7chunks)");
                }
            });
        }

        clipboardService = new ClipboardService(this, undoService);
        clipboardService.setMasks(maskEngine);
        clipboardService.setShapes(selectionShape);
        clipboardService.setEditState(playerEditState);
        clipboardService.setParallelChunks(config.parallelChunks());
        clipboardService.setLargePasteTuning(
                config.largePasteBlocks(), config.parallelChunksLarge(), config.autoFastLarge());
        clipboardService.setSelectHook((player, bounds) -> {
            String worldName = bounds[0];
            int minX = Integer.parseInt(bounds[1]);
            int minY = Integer.parseInt(bounds[2]);
            int minZ = Integer.parseInt(bounds[3]);
            int maxX = Integer.parseInt(bounds[4]);
            int maxY = Integer.parseInt(bounds[5]);
            int maxZ = Integer.parseInt(bounds[6]);
            selection.setPos1(player.getUniqueId(), worldName, minX, minY, minZ);
            selection.setPos2(player.getUniqueId(), worldName, maxX, maxY, maxZ);
            notifyCui(player);
            player.sendMessage("§aSelection set to pasted region.");
        });
        if (config.progressMessages()) {
            clipboardService.setProgressListener((uuid, blocks, total, chunksDone, chunksTotal) -> {
                Player p = getServer().getPlayer(uuid);
                if (p == null || !p.isOnline() || chunksTotal < 4) {
                    return;
                }
                if (chunksDone == chunksTotal || chunksDone % Math.max(1, chunksTotal / 10) == 0) {
                    int pct = total <= 0 ? 0 : (int) Math.min(100, (blocks * 100L) / total);
                    p.sendMessage("§7Paste… §f" + pct + "% §7(§f" + chunksDone + "§7/§f" + chunksTotal + " §7chunks)");
                }
            });
        }

        generationService = new GenerationService(this, undoService);
        generationService.setMasks(maskEngine);
        generationService.setEditState(playerEditState);
        generationService.setParallelChunks(config.parallelChunks());

        terrainService = new TerrainService(this, undoService);
        terrainService.setMasks(maskEngine);
        terrainService.setShapes(selectionShape);
        terrainService.setEditState(playerEditState);
        terrainService.setParallelChunks(config.parallelChunks());

        brushService.setClipboard(clipboardService);

        worldEditBridge = new WorldEditBridge(this, undoService);
        worldEditBridge.setEditState(playerEditState);
        worldEditBridge.setParallelChunks(config.parallelChunks());
        EditSession.YaPEditBridge.set(worldEditBridge);

        if (cuiBridge == null) {
            cuiBridge = new WorldEditCuiBridge(this, selection, selectionShape);
            cuiBridge.register();
        }
        cuiBridge.setEnabled(config.cuiEnabled());

        editOps = new WorldEditOps(this, selection, selectionEditService, generationService,
                clipboardService, undoService, brushService, maskEngine, selectionShape,
                playerEditState, terrainService, cuiBridge);
        editApply = new EditApplyServiceImpl(selectionEditService, clipboardService, selection);
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
                selectionEditService, undoService, paster, worldEditTool, clipboardService);
        try {
            editHttp.start();
        } catch (IOException e) {
            getLogger().warning("World edit studio failed to start: " + e.getMessage());
            editHttp = null;
        }
    }

    public void notifyCui(Player player) {
        if (cuiBridge != null) {
            cuiBridge.update(player);
        }
    }

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

    public WorldConfig worldConfig() {
        return config;
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

    public MaskEngine masks() {
        return maskEngine;
    }

    public SelectionShape shapes() {
        return selectionShape;
    }

    public PlayerEditState editState() {
        return playerEditState;
    }

    public TerrainService terrain() {
        return terrainService;
    }

    public WorldEditCuiBridge cui() {
        return cuiBridge;
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
        if (cuiBridge != null) {
            cuiBridge.unregister();
            cuiBridge = null;
        }
        EditSession.YaPEditBridge.set(null);
        var sm = getServer().getServicesManager();
        if (worldManager != null) {
            sm.unregister(com.yapcore.world.WorldManagerService.class, worldManager);
        }
        if (selection != null) {
            sm.unregister(com.yapcore.world.SelectionService.class, selection);
        }
        if (editApply != null) {
            sm.unregister(com.yapcore.world.EditApplyService.class, editApply);
        }
    }
}
