package com.yapcore.dungeons;

import com.yapcore.dungeons.cmd.DungeonCommand;
import com.yapcore.dungeons.cmd.YapDungeonsCommand;
import com.yapcore.dungeons.db.DungeonDatabase;
import com.yapcore.dungeons.db.DungeonRepository;
import com.yapcore.dungeons.gate.GateEvaluator;
import com.yapcore.dungeons.gate.GateTable;
import com.yapcore.dungeons.gen.DifficultyTable;
import com.yapcore.dungeons.gen.DungeonCarver;
import com.yapcore.dungeons.gen.ThemeTable;
import com.yapcore.dungeons.gui.DungeonMenu;
import com.yapcore.dungeons.gui.DungeonMenuListener;
import com.yapcore.dungeons.listener.DungeonListener;
import com.yapcore.dungeons.loot.LootService;
import com.yapcore.dungeons.loot.LootTable;
import com.yapcore.dungeons.papi.DungeonsPlaceholders;
import com.yapcore.dungeons.portal.DungeonPortalRegistry;
import com.yapcore.dungeons.portal.DungeonPortalRehydrate;
import com.yapcore.dungeons.portal.DungeonPortalStore;
import com.yapcore.dungeons.portal.DungeonPortalVisuals;
import com.yapcore.dungeons.portal.PortalItems;
import com.yapcore.dungeons.portal.PortalStructure;
import com.yapcore.dungeons.portal.PortalStructureTags;
import com.yapcore.dungeons.service.DungeonInstanceManager;
import com.yapcore.dungeons.service.DungeonServiceImpl;
import com.yapcore.dungeons.service.DungeonWorldOps;
import com.yapcore.mmo.SkillServices;
import com.yapcore.sched.YapSched;
import com.yapcore.world.WorldServices;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class DungeonsPlugin extends JavaPlugin {

    private static final List<String> PACK_FILES = List.of(
            "gates.yml", "difficulty.yml", "themes.yml", "loot.yml");

    private DungeonsConfig config;
    private DungeonDatabase database;
    private DungeonRepository repository;
    private GateTable gateTable;
    private GateEvaluator gateEvaluator;
    private DifficultyTable difficultyTable;
    private ThemeTable themeTable;
    private LootTable lootTable;
    private LootService lootService;
    private PortalItems portalItems;
    private PortalStructure portalStructure;
    private PortalStructureTags portalStructureTags;
    private DungeonInstanceManager instances;
    private DungeonServiceImpl dungeonService;
    private DungeonMenu menu;
    private DungeonsPlaceholders placeholders;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadDungeons();
        if (!config.enabled()) {
            getLogger().info("YaPDungeons disabled via config.");
            return;
        }
        if (SkillServices.find().isEmpty()) {
            getLogger().severe("YaPSkills not found — dungeon entry gates require it. Disable or install YaPSkills.");
        }
        if (WorldServices.worldManager().isEmpty()) {
            getLogger().warning("YaPWorld not found — using Bukkit world create/delete fallback.");
        }

        getServer().getPluginManager().registerEvents(new DungeonMenuListener(dungeonService, menu), this);
        DungeonPortalStore portalStore = new DungeonPortalStore(this);
        if (config.structureEnabled()) {
            portalStore.load();
        }
        getServer().getPluginManager().registerEvents(
                new DungeonListener(this, config, portalItems, portalStructure, portalStructureTags,
                        portalStore, menu, instances),
                this);

        bind("dungeon", new DungeonCommand(dungeonService, menu));
        bind("yapdungeons", new YapDungeonsCommand(this, dungeonService));

        getServer().getServicesManager().register(
                DungeonService.class, dungeonService, this, ServicePriority.Normal);
        placeholders = new DungeonsPlaceholders(dungeonService);
        placeholders.tryRegister();

        instances.recoverOrphans();
        instances.startGcTimer();
        if (config.structureEnabled()) {
            YapSched.globalLater(this, () -> {
                DungeonPortalRehydrate.scanLoaded(this, portalStructure, portalStructureTags, portalStore);
                getLogger().info("Dungeon portal rehydrate — registry="
                        + DungeonPortalRegistry.all().size()
                        + " persisted=" + portalStore.all().size());
            }, 80L);
            // Soft keep-alive: ensure swirl present, do not clear/rebuild every pass
            YapSched.globalTimer(this, () -> {
                for (PortalStructure.Frame frame : DungeonPortalRegistry.all()) {
                    int midAlong = frame.minAlong() + (frame.sizeAlong() / 2);
                    int midX = frame.axis() == org.bukkit.Axis.X ? midAlong : frame.fixed();
                    int midZ = frame.axis() == org.bukkit.Axis.X ? frame.fixed() : midAlong;
                    YapSched.region(this, frame.world(), midX, midZ,
                            () -> DungeonPortalVisuals.ensureFace(frame));
                }
            }, 20L * 40, 20L * 40);
            // Occasional full rehydrate for keystones after chunk loads (less aggressive)
            YapSched.globalTimer(this, () -> {
                DungeonPortalRehydrate.scanLoaded(this, portalStructure, portalStructureTags, portalStore);
            }, 20L * 60, 20L * 120);
            final int[] pulse = {0};
            YapSched.globalTimer(this, () -> {
                if (DungeonPortalRegistry.all().isEmpty()) {
                    return;
                }
                float spin = (float) ((pulse[0]++ % 10) * (Math.PI * 2.0 / 10.0));
                for (PortalStructure.Frame frame : DungeonPortalRegistry.all()) {
                    int midAlong = frame.minAlong() + (frame.sizeAlong() / 2);
                    int midX = frame.axis() == org.bukkit.Axis.X ? midAlong : frame.fixed();
                    int midZ = frame.axis() == org.bukkit.Axis.X ? frame.fixed() : midAlong;
                    YapSched.region(this, frame.world(), midX, midZ,
                            () -> DungeonPortalVisuals.spin(frame, spin));
                }
            }, 20L, 5L);
        }
        getLogger().info("YaPDungeons ready — activeRuns=" + dungeonService.activeRuns().size());
    }

    @Override
    public void onDisable() {
        var sm = getServer().getServicesManager();
        if (dungeonService != null) {
            sm.unregister(DungeonService.class, dungeonService);
            for (var run : List.copyOf(dungeonService.activeRuns())) {
                instances.cleanup(run.runId(), "shutdown");
            }
        }
        if (placeholders != null) {
            placeholders.unregister();
        }
        if (database != null) {
            database.close();
        }
    }

    public void reloadDungeons() {
        if (config == null) {
            config = new DungeonsConfig(this);
        }
        config.reload();

        if (database == null) {
            database = new DungeonDatabase(this, config);
        }
        try {
            database.open();
        } catch (Exception e) {
            getLogger().severe("YaPDungeons database failed: " + e.getMessage());
            return;
        }
        if (repository == null) {
            repository = new DungeonRepository(database);
        }

        Path packs = getDataFolder().toPath().resolve(config.packsDirectory());
        try {
            Files.createDirectories(packs);
            for (String pack : PACK_FILES) {
                Path dest = packs.resolve(pack);
                if (!Files.exists(dest)) {
                    saveResource("dungeons/" + pack, false);
                }
            }
        } catch (Exception e) {
            getLogger().warning("Could not prepare packs: " + e.getMessage());
        }

        gateTable = new GateTable();
        gateTable.reload(this, packs.resolve("gates.yml").toFile());
        gateEvaluator = new GateEvaluator(gateTable);

        difficultyTable = new DifficultyTable();
        difficultyTable.reload(this, packs.resolve("difficulty.yml").toFile());
        themeTable = new ThemeTable();
        themeTable.reload(this, packs.resolve("themes.yml").toFile());
        lootTable = new LootTable();
        lootTable.reload(this, packs.resolve("loot.yml").toFile());
        lootService = new LootService(this, config, lootTable);

        portalItems = new PortalItems(this, config);
        portalStructure = new PortalStructure(
                config.structureFrame(),
                config.structureInterior(),
                config.structureWidth(),
                config.structureHeight());
        portalStructureTags = new PortalStructureTags(this);
        if (config.enabled()) {
            portalItems.registerRecipe();
        }

        DungeonWorldOps worldOps = new DungeonWorldOps(this, config);
        DungeonCarver carver = new DungeonCarver(this);
        instances = new DungeonInstanceManager(
                this, config, repository, worldOps, difficultyTable, themeTable, carver, lootService);

        var sm = getServer().getServicesManager();
        if (dungeonService != null) {
            sm.unregister(DungeonService.class, dungeonService);
        }
        dungeonService = new DungeonServiceImpl(this, config, repository, gateEvaluator, instances, portalItems);
        menu = new DungeonMenu(this, dungeonService, gateEvaluator);

        if (config.enabled() && getServer().getPluginManager().isPluginEnabled(this)) {
            sm.register(DungeonService.class, dungeonService, this, ServicePriority.Normal);
            if (placeholders != null) {
                placeholders.unregister();
            }
            placeholders = new DungeonsPlaceholders(dungeonService);
            placeholders.tryRegister();
        }
    }

    private void bind(String name, Object executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            return;
        }
        cmd.setExecutor((org.bukkit.command.CommandExecutor) executor);
        if (executor instanceof org.bukkit.command.TabCompleter completer) {
            cmd.setTabCompleter(completer);
        }
    }
}
