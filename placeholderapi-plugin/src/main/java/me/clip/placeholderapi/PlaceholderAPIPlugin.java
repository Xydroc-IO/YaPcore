package me.clip.placeholderapi;

import java.text.SimpleDateFormat;
import me.clip.placeholderapi.builtin.PlayerExpansion;
import me.clip.placeholderapi.builtin.ServerExpansion;
import me.clip.placeholderapi.command.PapiCommand;
import me.clip.placeholderapi.configuration.PlaceholderAPIConfig;
import me.clip.placeholderapi.expansion.Version;
import me.clip.placeholderapi.expansion.manager.CloudExpansionManager;
import me.clip.placeholderapi.expansion.manager.LocalExpansionManager;
import me.clip.placeholderapi.metrics.MetricsBridge;
import me.clip.placeholderapi.update.UpdateChecker;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Main plugin class for YaP's PlaceholderAPI-compatible engine.
 *
 * <p>Registers as plugin name {@code PlaceholderAPI} so soft/hard depends and
 * {@code Bukkit.getPluginManager().getPlugin("PlaceholderAPI")} succeed without
 * the HelpChat / clip jar.
 *
 * <p>Clean-room implementation — not GPL PlaceholderAPI source.
 */
public final class PlaceholderAPIPlugin extends JavaPlugin {

    private static volatile Version version;
    private static PlaceholderAPIPlugin instance;

    private final PlaceholderAPIConfig config = new PlaceholderAPIConfig(this);
    private final LocalExpansionManager localExpansionManager = new LocalExpansionManager(this);
    private final CloudExpansionManager cloudExpansionManager = new CloudExpansionManager(this);

    private BukkitAudiences adventure;

    @NotNull
    public static PlaceholderAPIPlugin getInstance() {
        return instance;
    }

    @NotNull
    public static String booleanTrue() {
        return getInstance().getPlaceholderAPIConfig().booleanTrue();
    }

    @NotNull
    public static String booleanFalse() {
        return getInstance().getPlaceholderAPIConfig().booleanFalse();
    }

    @NotNull
    public static SimpleDateFormat getDateFormat() {
        try {
            return new SimpleDateFormat(getInstance().getPlaceholderAPIConfig().dateFormat());
        } catch (IllegalArgumentException ex) {
            return new SimpleDateFormat("MM/dd/yy HH:mm:ss");
        }
    }

    @Deprecated
    public static Version getServerVersion() {
        Version cached = version;
        if (cached != null) {
            return cached;
        }
        String ver = "unknown";
        boolean spigot = false;
        try {
            if (Bukkit.getServer() != null) {
                ver = Bukkit.getServer().getBukkitVersion();
            }
        } catch (Throwable ignored) {
            // unit tests / early class load
        }
        try {
            Class.forName("org.spigotmc.SpigotConfig");
            spigot = true;
        } catch (ClassNotFoundException | ExceptionInInitializerError ignored) {
            // facade / non-spigot
        }
        cached = new Version(ver, spigot);
        version = cached;
        return cached;
    }

    @Override
    public void onLoad() {
        saveDefaultConfig();
        instance = this;
    }

    @Override
    public void onEnable() {
        if (instance != this) {
            instance = this;
        }

        adventure = BukkitAudiences.create(this);

        PluginCommand cmd = getCommand("placeholderapi");
        if (cmd != null) {
            PapiCommand router = new PapiCommand(this);
            cmd.setExecutor(router);
            cmd.setTabCompleter(router);
        }

        Bukkit.getPluginManager().registerEvents(localExpansionManager, this);

        new PlayerExpansion().register();
        new ServerExpansion().register();

        Bukkit.getScheduler().runTaskLater(this,
                () -> localExpansionManager.load(Bukkit.getConsoleSender()), 1L);

        if (config.isCloudEnabled()) {
            cloudExpansionManager.load();
        }

        MetricsBridge.start(this);

        if (config.checkUpdates()) {
            new UpdateChecker(this).fetch();
        }

        getLogger().info("YaP PlaceholderAPI online (clip-compatible). Do not install HelpChat PlaceholderAPI.jar alongside this.");
        getLogger().info("Built-ins: player, server · /papi help · expansions → plugins/PlaceholderAPI/expansions/");
    }

    @Override
    public void onDisable() {
        cloudExpansionManager.kill();
        localExpansionManager.kill();
        if (adventure != null) {
            adventure.close();
            adventure = null;
        }
        instance = null;
    }

    public void reloadConf(@NotNull final CommandSender sender) {
        localExpansionManager.unregisterNonPersistent();
        reloadConfig();
        if (!PlaceholderAPI.isRegistered("player")) {
            new PlayerExpansion().register();
        }
        if (!PlaceholderAPI.isRegistered("server")) {
            new ServerExpansion().register();
        }
        localExpansionManager.load(sender);
        if (config.isCloudEnabled()) {
            cloudExpansionManager.load();
        } else {
            cloudExpansionManager.kill();
        }
    }

    @NotNull
    public LocalExpansionManager getLocalExpansionManager() {
        return localExpansionManager;
    }

    @NotNull
    public CloudExpansionManager getCloudExpansionManager() {
        return cloudExpansionManager;
    }

    @NotNull
    public BukkitAudiences getAdventure() {
        if (adventure == null) {
            throw new IllegalStateException("Tried to access Adventure while PlaceholderAPI was disabled!");
        }
        return adventure;
    }

    @NotNull
    public PlaceholderAPIConfig getPlaceholderAPIConfig() {
        return config;
    }
}
