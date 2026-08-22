package com.yapcore.mmocontent;

import com.yapcore.mmo.MmoSnapshotService;
import com.yapcore.mmo.RecipeUnlockService;
import com.yapcore.mmocontent.area.FishingAreaListener;
import com.yapcore.mmocontent.area.MiningGuildListener;
import com.yapcore.mmocontent.area.SkillAreaDefinition;
import com.yapcore.mmocontent.area.SkillAreaLoader;
import com.yapcore.mmocontent.area.SkillAreaDefinition.Type;
import com.yapcore.mmocontent.boss.BossManager;
import com.yapcore.mmocontent.boss.BossPackLoader;
import com.yapcore.mmocontent.cmd.HiscoresCommand;
import com.yapcore.mmocontent.cmd.YapMmoCommand;
import com.yapcore.mmocontent.db.BossKillRepository;
import com.yapcore.mmocontent.db.ContentDatabase;
import com.yapcore.mmocontent.db.HiscoreRepository;
import com.yapcore.mmocontent.db.RecipeUnlockRepository;
import com.yapcore.mmocontent.db.TeleportUnlockRepository;
import com.yapcore.mmocontent.listener.BossDeathListener;
import com.yapcore.mmocontent.listener.Level99BroadcastListener;
import com.yapcore.mmocontent.service.MmoSnapshotServiceImpl;
import com.yapcore.mmocontent.service.RecipeUnlockServiceImpl;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class MmoContentPlugin extends JavaPlugin {

    private MmoContentConfig config;
    private ContentDatabase database;
    private HiscoreRepository hiscoreRepository;
    private BossKillRepository bossKillRepository;
    private RecipeUnlockRepository recipeUnlockRepository;
    private TeleportUnlockRepository teleportUnlockRepository;
    private BossPackLoader bossLoader;
    private SkillAreaLoader areaLoader;
    private BossManager bossManager;
    private MmoSnapshotServiceImpl snapshotService;
    private RecipeUnlockServiceImpl recipeUnlockService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadContent();

        if (!config.enabled()) {
            getLogger().info("YaPMmoContent disabled via config.");
            return;
        }

        getServer().getPluginManager().registerEvents(new BossDeathListener(this, bossManager, bossKillRepository), this);
        getServer().getPluginManager().registerEvents(new MiningGuildListener(this, areaLoader), this);
        getServer().getPluginManager().registerEvents(new FishingAreaListener(areaLoader), this);
        getServer().getPluginManager().registerEvents(new Level99BroadcastListener(this, config), this);

        bindCommand("hiscores", new HiscoresCommand(this, config, hiscoreRepository));
        bindCommand("yapmmo", new YapMmoCommand(this, snapshotService, teleportUnlockRepository));

        var sm = getServer().getServicesManager();
        sm.register(MmoSnapshotService.class, snapshotService, this, ServicePriority.Normal);
        sm.register(RecipeUnlockService.class, recipeUnlockService, this, ServicePriority.Normal);

        seedMiningNodes();
        bossManager.spawnAll();

        YapSched.globalLater(this, () -> {
            if (Bukkit.getPluginManager().getPlugin("YaPNpcs") != null) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "npc reload");
            }
        }, 40L);

        getLogger().info("YaPMmoContent ready — bosses=" + bossLoader.bosses().size()
                + " areas=" + areaLoader.areas().size());
    }

    @Override
    public void onDisable() {
        var sm = getServer().getServicesManager();
        if (snapshotService != null) {
            sm.unregister(MmoSnapshotService.class, snapshotService);
        }
        if (recipeUnlockService != null) {
            sm.unregister(RecipeUnlockService.class, recipeUnlockService);
        }
        if (database != null) {
            database.close();
        }
    }

    public void reloadContent() {
        if (config == null) {
            config = new MmoContentConfig(this);
        }
        config.reload();
        extractDefaults();

        if (database == null) {
            database = new ContentDatabase(this, config);
        }
        try {
            database.open();
        } catch (Exception e) {
            getLogger().severe("YaPMmoContent database failed: " + e.getMessage());
            return;
        }

        if (hiscoreRepository == null) {
            hiscoreRepository = new HiscoreRepository(database);
        }
        if (bossKillRepository == null) {
            bossKillRepository = new BossKillRepository(database);
        }
        if (recipeUnlockRepository == null) {
            recipeUnlockRepository = new RecipeUnlockRepository(database);
        }
        if (teleportUnlockRepository == null) {
            teleportUnlockRepository = new TeleportUnlockRepository(database);
        }

        Path bossesPath = getDataFolder().toPath().resolve(config.bossesDir());
        Path areasFile = getDataFolder().toPath().resolve(config.areasFile());

        bossLoader = new BossPackLoader();
        bossLoader.load(bossesPath);
        areaLoader = new SkillAreaLoader();
        areaLoader.load(areasFile);
        bossManager = new BossManager(this, bossLoader);
        snapshotService = new MmoSnapshotServiceImpl(
                config, hiscoreRepository, bossKillRepository, bossLoader, areaLoader);
        recipeUnlockService = new RecipeUnlockServiceImpl(recipeUnlockRepository);
    }

    private void seedMiningNodes() {
        for (SkillAreaDefinition area : areaLoader.areas().values()) {
            if (area.type() != Type.MINING_GUILD) {
                continue;
            }
            var world = Bukkit.getWorld(area.world());
            if (world == null) {
                continue;
            }
            for (SkillAreaDefinition.OreNode node : area.nodes()) {
                var loc = world.getBlockAt(node.x(), node.y(), node.z()).getLocation();
                YapSched.region(this, loc, () -> {
                    var block = world.getBlockAt(node.x(), node.y(), node.z());
                    if (block.getType().isAir() || block.getType() == Material.STONE) {
                        block.setType(node.ore(), false);
                    }
                });
            }
        }
    }

    private void extractDefaults() {
        try {
            Files.createDirectories(getDataFolder().toPath());
            Files.createDirectories(getDataFolder().toPath().resolve(config.questsDir()));
            Files.createDirectories(getDataFolder().toPath().resolve(config.bossesDir()));
            copyResourceIfMissing(config.areasFile());
            extractFromManifest();
        } catch (IOException e) {
            getLogger().warning("Could not extract content pack: " + e.getMessage());
        }
    }

    private void extractFromManifest() throws IOException {
        try (var in = getResource("content-manifest.txt")) {
            if (in == null) {
                copyResourceIfMissing(config.questsDir() + "/starter_chain.yml");
                return;
            }
            String text = new String(in.readAllBytes());
            for (String line : text.split("\n")) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("DIR ")) {
                    Files.createDirectories(getDataFolder().toPath().resolve(line.substring(4).trim()));
                    continue;
                }
                copyResourceIfMissing(line);
            }
        }
    }

    private void copyResourceIfMissing(String resourcePath) throws IOException {
        Path target = getDataFolder().toPath().resolve(resourcePath);
        if (Files.exists(target)) {
            return;
        }
        Files.createDirectories(target.getParent());
        String resourceName = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        try (InputStream in = getResource(resourceName)) {
            if (in == null) {
                return;
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void bindCommand(String name, Object executor) {
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
