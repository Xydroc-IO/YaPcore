package com.yapcore.yapblock;

import com.yapcore.sched.YapSched;
import com.yapcore.world.WorldServices;
import com.yapcore.yapblock.cmd.IslandCommand;
import com.yapcore.yapblock.cmd.IslandCommandTab;
import com.yapcore.yapblock.cmd.YapblockAdminCommand;
import com.yapcore.yapblock.db.IslandRepository;
import com.yapcore.yapblock.db.MemberRepository;
import com.yapcore.yapblock.db.YapblockDatabase;
import com.yapcore.yapblock.gen.CobbleGenTables;
import com.yapcore.yapblock.gen.IslandGeneratorListener;
import com.yapcore.yapblock.gen.IslandStarterPack;
import com.yapcore.yapblock.gen.SchematicIslandPaster;
import com.yapcore.yapblock.gen.VoidChunkGenerator;
import com.yapcore.yapblock.grid.GridAllocator;
import com.yapcore.yapblock.grid.IslandGrid;
import com.yapcore.yapblock.grid.IslandIndex;
import com.yapcore.yapblock.gui.IslandSettingsListener;
import com.yapcore.yapblock.gui.IslandSettingsMenu;
import com.yapcore.yapblock.level.BlockValueTable;
import com.yapcore.yapblock.level.IslandLevelScanner;
import com.yapcore.yapblock.level.IslandTopCache;
import com.yapcore.yapblock.papi.YapblockPlaceholders;
import com.yapcore.yapblock.protect.IslandListener;
import com.yapcore.yapblock.service.IslandRoleCache;
import com.yapcore.yapblock.service.IslandServiceImpl;
import org.bukkit.command.PluginCommand;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

public final class YapblockPlugin extends JavaPlugin {

    private YapblockConfig config;
    private YapblockDatabase database;
    private IslandRepository islandRepository;
    private MemberRepository memberRepository;
    private IslandServiceImpl islandService;
    private YapblockPlaceholders placeholders;
    private CobbleGenTables cobbleGenTables;
    private BlockValueTable blockValueTable;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("generators.yml");
        saveResourceIfMissing("block-values.yml");
        saveResourceIfMissing("schematics/README.txt");
        reloadYapblock();
        if (!config.enabled()) {
            getLogger().info("YaPblock disabled via config.");
            return;
        }
        if (WorldServices.worldManager().isEmpty()) {
            getLogger().warning("YaPWorld not found — using Bukkit WorldCreator fallback.");
        }
        IslandSettingsMenu menu = new IslandSettingsMenu(this);
        getServer().getPluginManager().registerEvents(new IslandSettingsListener(this), this);
        new IslandListener(this);
        getServer().getPluginManager().registerEvents(
                new IslandGeneratorListener(this, cobbleGenTables), this);

        IslandCommand islandCmd = new IslandCommand(this, menu);
        IslandCommandTab tab = new IslandCommandTab();
        bind("is", islandCmd, tab);
        bind("yapblock", new YapblockAdminCommand(this), null);

        getServer().getServicesManager().register(
                IslandService.class, islandService, this, ServicePriority.Normal);
        placeholders = new YapblockPlaceholders(this);
        placeholders.tryRegister();

        YapSched.globalLater(this, () -> islandService.createOps().ensureWorld(), 40L);
        getLogger().info("YaPblock ready — islands=" + islandService.index().all().size());
    }

    @Override
    public void onDisable() {
        var sm = getServer().getServicesManager();
        if (islandService != null) {
            sm.unregister(IslandService.class, islandService);
        }
        if (placeholders != null) {
            placeholders.unregisterSafe();
        }
        if (database != null) {
            database.close();
        }
    }

    @Override
    public @Nullable ChunkGenerator getDefaultWorldGenerator(@NotNull String worldName, @Nullable String id) {
        return new VoidChunkGenerator();
    }

    public IslandServiceImpl service() {
        return islandService;
    }

    public YapblockConfig yapblockConfig() {
        return config;
    }

    public void reloadYapblock() {
        if (config == null) {
            config = new YapblockConfig(this);
        }
        config.reload();

        if (database == null) {
            database = new YapblockDatabase(this, config);
        }
        try {
            database.open();
        } catch (Exception e) {
            getLogger().severe("YaPblock database failed: " + e.getMessage());
            return;
        }
        if (islandRepository == null) {
            islandRepository = new IslandRepository(database);
        }
        if (memberRepository == null) {
            memberRepository = new MemberRepository(database);
        }

        if (cobbleGenTables == null) {
            cobbleGenTables = new CobbleGenTables();
        }
        cobbleGenTables.reload(this);
        if (blockValueTable == null) {
            blockValueTable = new BlockValueTable();
        }
        blockValueTable.reload(this);

        IslandGrid grid = new IslandGrid(config.gridDistance(), config.pasteY());
        GridAllocator allocator = new GridAllocator(grid);
        IslandIndex index = new IslandIndex(config, grid);
        IslandRoleCache roles = new IslandRoleCache();
        IslandTopCache topCache = new IslandTopCache(index);
        IslandLevelScanner scanner = new IslandLevelScanner(
                this, grid, index, islandRepository, blockValueTable, topCache);
        IslandStarterPack starter = new IslandStarterPack(this);
        SchematicIslandPaster paster = new SchematicIslandPaster(this, config, starter);

        var sm = getServer().getServicesManager();
        if (islandService != null) {
            sm.unregister(IslandService.class, islandService);
        }
        islandService = new IslandServiceImpl(
                this, config, grid, allocator, index, islandRepository, memberRepository,
                roles, paster, scanner, topCache);
        islandService.loadFromDatabase(allocator);

        if (config.enabled() && getServer().getPluginManager().isPluginEnabled(this)) {
            sm.register(IslandService.class, islandService, this, ServicePriority.Normal);
            if (placeholders != null) {
                placeholders.unregisterSafe();
            }
            placeholders = new YapblockPlaceholders(this);
            placeholders.tryRegister();
        }
    }

    private void saveResourceIfMissing(String path) {
        Path dest = getDataFolder().toPath().resolve(path);
        try {
            Files.createDirectories(dest.getParent());
            if (!Files.exists(dest)) {
                saveResource(path, false);
            }
        } catch (Exception e) {
            getLogger().warning("Could not prepare " + path + ": " + e.getMessage());
        }
    }

    private void bind(String name, Object executor, IslandCommandTab tab) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            return;
        }
        cmd.setExecutor((org.bukkit.command.CommandExecutor) executor);
        if (tab != null) {
            cmd.setTabCompleter(tab);
        } else if (executor instanceof org.bukkit.command.TabCompleter completer) {
            cmd.setTabCompleter(completer);
        }
    }
}
