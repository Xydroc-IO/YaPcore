package com.yapcore.games;

import com.yapcore.games.arena.ArenaLoader;
import com.yapcore.games.cmd.DuelCommand;
import com.yapcore.games.cmd.GameCommand;
import com.yapcore.games.cmd.QueueCommand;
import com.yapcore.games.cmd.YGamesCommand;
import com.yapcore.games.db.GamesDatabase;
import com.yapcore.games.db.StatsRepository;
import com.yapcore.games.economy.MatchRewards;
import com.yapcore.games.kit.KitLoader;
import com.yapcore.games.listener.ArenaBoundaryListener;
import com.yapcore.games.listener.MatchListener;
import com.yapcore.games.listener.MatchProtectionListener;
import com.yapcore.games.listener.QueueSignListener;
import com.yapcore.games.match.MatchManager;
import com.yapcore.games.match.MatchUi;
import com.yapcore.games.mode.GameModeLoader;
import com.yapcore.games.papi.GamesPlaceholders;
import com.yapcore.games.reset.ArenaResetter;
import com.yapcore.games.service.GameServiceImpl;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

public final class GamesPlugin extends JavaPlugin {

    private GamesConfig config;
    private GamesDatabase database;
    private StatsRepository statsRepository;
    private GameModeLoader modeLoader;
    private ArenaLoader arenaLoader;
    private KitLoader kitLoader;
    private ArenaResetter resetter;
    private MatchUi matchUi;
    private MatchRewards matchRewards;
    private MatchManager matchManager;
    private GameServiceImpl gameService;
    private GamesPlaceholders placeholders;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadGames();

        if (!config.enabled()) {
            getLogger().info("YaPGames disabled via config.");
            return;
        }

        getServer().getPluginManager().registerEvents(new MatchListener(matchManager), this);
        getServer().getPluginManager().registerEvents(new MatchProtectionListener(matchManager), this);
        if (config.enforceBoundary()) {
            getServer().getPluginManager().registerEvents(new ArenaBoundaryListener(this, matchManager), this);
        }
        if (config.signsEnabled()) {
            getServer().getPluginManager().registerEvents(new QueueSignListener(config, matchManager), this);
        }

        bindCommand("queue", new QueueCommand(matchManager));
        bindCommand("duel", new DuelCommand(matchManager));
        bindCommand("game", new GameCommand(matchManager));
        bindCommand("ygames", new YGamesCommand(this, matchManager));

        getServer().getServicesManager().register(GameService.class, gameService, this, ServicePriority.Normal);

        placeholders = new GamesPlaceholders(matchManager);
        placeholders.tryRegister();

        getLogger().info("YaPGames ready — modes=" + modeCount() + " arenas=" + arenaCount());
    }

    @Override
    public void onDisable() {
        if (gameService != null) {
            getServer().getServicesManager().unregister(GameService.class, gameService);
        }
        if (database != null) {
            database.close();
            database = null;
        }
    }

    public void reloadGames() {
        if (config == null) {
            config = new GamesConfig(this);
        }
        config.reload();
        ensureDataFiles();

        if (database != null) {
            database.close();
        }
        database = new GamesDatabase(this, config);
        try {
            database.open();
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "YaPGames database unavailable — stats disabled", e);
        }
        statsRepository = new StatsRepository(database);

        Path arenasDir = getDataFolder().toPath().resolve(config.arenasDirectory());
        Path modesDir = getDataFolder().toPath().resolve(config.modesDirectory());
        Path kitsFile = getDataFolder().toPath().resolve(config.kitsFile());

        modeLoader = new GameModeLoader(modesDir);
        modeLoader.reload();
        arenaLoader = new ArenaLoader(arenasDir);
        arenaLoader.reload();
        kitLoader = new KitLoader(kitsFile);
        kitLoader.reload();
        resetter = new ArenaResetter(this, config.resetDrops());
        matchUi = new MatchUi(this, config);
        matchRewards = new MatchRewards(config);
        matchManager = new MatchManager(
                this, config, modeLoader, arenaLoader, kitLoader, resetter,
                statsRepository, matchRewards, matchUi);

        var sm = getServer().getServicesManager();
        if (gameService != null) {
            sm.unregister(GameService.class, gameService);
        }
        gameService = new GameServiceImpl(matchManager, modeLoader.modes().keySet());
        if (isEnabled()) {
            sm.register(GameService.class, gameService, this, ServicePriority.Normal);
        }
    }

    public MatchManager matchManager() {
        return matchManager;
    }

    public int modeCount() {
        return modeLoader == null ? 0 : modeLoader.modes().size();
    }

    public int arenaCount() {
        return arenaLoader == null ? 0 : arenaLoader.arenas().size();
    }

    private void ensureDataFiles() {
        try {
            Files.createDirectories(getDataFolder().toPath().resolve(config.arenasDirectory()));
            Files.createDirectories(getDataFolder().toPath().resolve(config.modesDirectory()));
            copyResourceIfMissing(getDataFolder().toPath().resolve(config.kitsFile()), "kits.yml");
            copyResourceIfMissing(
                    getDataFolder().toPath().resolve(config.arenasDirectory()).resolve("default.yml"),
                    "arenas/default.yml");
            copyResourceIfMissing(
                    getDataFolder().toPath().resolve(config.modesDirectory()).resolve("ffa.yml"),
                    "modes/ffa.yml");
            copyResourceIfMissing(
                    getDataFolder().toPath().resolve(config.modesDirectory()).resolve("duels.yml"),
                    "modes/duels.yml");
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Could not prepare games data files", e);
        }
    }

    private void copyResourceIfMissing(Path target, String resource) throws IOException {
        if (Files.exists(target)) {
            return;
        }
        Files.createDirectories(target.getParent());
        saveResource(resource, false);
        Path saved = getDataFolder().toPath().resolve(resource);
        if (!saved.equals(target) && Files.exists(saved)) {
            Files.move(saved, target);
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
