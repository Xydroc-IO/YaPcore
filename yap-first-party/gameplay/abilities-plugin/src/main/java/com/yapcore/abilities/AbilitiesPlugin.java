package com.yapcore.abilities;

import com.yapcore.abilities.cmd.AbilityCommands;
import com.yapcore.abilities.bar.AbilityBarConfig;
import com.yapcore.abilities.bar.AbilityBarListener;
import com.yapcore.abilities.bar.AbilityBarService;
import com.yapcore.abilities.bar.AbilityBarStore;
import com.yapcore.abilities.book.AbilityBookBedrockUi;
import com.yapcore.abilities.book.AbilityBookConfig;
import com.yapcore.abilities.book.AbilityBookKeys;
import com.yapcore.abilities.book.AbilityBookListener;
import com.yapcore.abilities.book.AbilityBookMenu;
import com.yapcore.abilities.book.AbilityBookPlayerStore;
import com.yapcore.abilities.book.AbilityBookService;
import com.yapcore.abilities.dashboard.AbilitiesDashboardSnapshot;
import com.yapcore.abilities.exec.AbilityExecutor;
import com.yapcore.abilities.exec.AbilityProjectileListener;
import com.yapcore.abilities.exec.EffectRunner;
import com.yapcore.abilities.exec.ProjectileTracker;
import com.yapcore.abilities.load.AbilityPackLoader;
import com.yapcore.abilities.load.StatusEffectPackLoader;
import com.yapcore.abilities.service.AbilityServiceImpl;
import com.yapcore.abilities.service.StatusEffectManager;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class AbilitiesPlugin extends JavaPlugin {

    private AbilityPackLoader abilityLoader;
    private StatusEffectPackLoader statusLoader;
    private AbilityServiceImpl abilityService;
    private StatusEffectManager statusService;
    private ProjectileTracker projectileTracker;
    private AbilityBarService abilityBar;
    private AbilityBookService abilityBook;
    private AbilityBookKeys bookKeys;
    private boolean runtimeWired;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadAbilityPacks();

        if (!getConfig().getBoolean("enabled", true)) {
            getLogger().info("YaPAbilities disabled via config.");
            return;
        }

        wireRuntime();
    }

    @Override
    public void onDisable() {
        tearDownRuntime();
    }

    public AbilityServiceImpl abilityService() {
        return abilityService;
    }

    public AbilityBarService abilityBar() {
        return abilityBar;
    }

    public AbilityBookService abilityBook() {
        return abilityBook;
    }

    public Map<String, Object> dashboardSnapshot() {
        if (abilityService == null || abilityBar == null || abilityBook == null) {
            return Map.of("abilitiesInstalled", false);
        }
        return AbilitiesDashboardSnapshot.snapshot(
                abilityService,
                abilityBar.config(),
                abilityBar.store(),
                abilityBook.config(),
                getDataFolder().toPath().resolve("bars.yml"));
    }

    /**
     * Hot reload: config.yml, ability/effect YAML packs, bar/book settings.
     * Re-registers listeners and Bukkit services without a full server restart.
     */
    public void adminReload(CommandSender sender) {
        if (abilityBar != null) {
            abilityBar.store().saveAll();
        }
        tearDownRuntime();
        reloadConfig();
        reloadAbilityPacks();

        if (!getConfig().getBoolean("enabled", true)) {
            sender.sendMessage("§eYaPAbilities disabled via config — runtime torn down.");
            return;
        }

        wireRuntime();
        for (Player player : getServer().getOnlinePlayers()) {
            abilityBar.initPlayer(player);
            abilityBar.syncBar(player);
        }

        int abilities = abilityLoader == null ? 0 : abilityLoader.abilities().size();
        int effects = statusLoader == null ? 0 : statusLoader.effects().size();
        sender.sendMessage("§aYaP Abilities reloaded — §f" + abilities + " §aabilities, §f" + effects
                + " §astatus effects.");
        getLogger().info("Admin reload by " + sender.getName() + " — abilities=" + abilities
                + ", status-effects=" + effects);
    }

    private void wireRuntime() {
        AbilityBarConfig barConfig = new AbilityBarConfig(getConfig());
        AbilityBookConfig bookConfig = new AbilityBookConfig(getConfig());
        AbilityBarStore barStore = new AbilityBarStore(this, barConfig);
        abilityBar = new AbilityBarService(this, barConfig, barStore, abilityService);

        bookKeys = new AbilityBookKeys(this);
        AbilityBookMenu bookMenu = new AbilityBookMenu(this, bookConfig, bookKeys, barConfig, barStore, abilityService);
        AbilityBookBedrockUi bedrockUi = new AbilityBookBedrockUi(this, bookConfig, abilityService, abilityBar);
        AbilityBookPlayerStore bookPlayers = new AbilityBookPlayerStore(this);
        abilityBook = new AbilityBookService(this, bookConfig, bookKeys, bookMenu, bedrockUi, bookPlayers,
                abilityBar, abilityService);

        getServer().getPluginManager().registerEvents(
                new AbilityBarListener(this, barConfig, abilityBar), this);
        getServer().getPluginManager().registerEvents(
                new AbilityBookListener(this, abilityBook, abilityService), this);
        if (projectileTracker != null) {
            getServer().getPluginManager().registerEvents(
                    new AbilityProjectileListener(projectileTracker), this);
        }

        AbilityCommands commands = new AbilityCommands(this);
        bind("ability", commands);
        bind("abilities", commands);
        bind("spell", commands);
        bind("yapabilities", commands);

        getServer().getServicesManager().register(AbilityService.class, abilityService, this, ServicePriority.Normal);
        getServer().getServicesManager().register(StatusEffectService.class, statusService, this, ServicePriority.Normal);
        runtimeWired = true;

        getLogger().info("YaP Abilities ready — abilities=" + abilityLoader.abilities().size()
                + ", status-effects=" + statusLoader.effects().size()
                + ", ability-bar=keys " + barConfig.firstKey() + "-" + barConfig.lastKey()
                + ", ability-book=" + (bookConfig.enabled() ? "on" : "off"));
    }

    private void tearDownRuntime() {
        if (abilityBar != null) {
            abilityBar.store().saveAll();
        }
        if (statusService != null) {
            statusService.stopTicker();
        }
        if (runtimeWired) {
            getServer().getServicesManager().unregister(AbilityService.class, abilityService);
            getServer().getServicesManager().unregister(StatusEffectService.class, statusService);
            HandlerList.unregisterAll(this);
            runtimeWired = false;
        }
        abilityBar = null;
        abilityBook = null;
        bookKeys = null;
    }

    private void reloadAbilityPacks() {
        Path data = getDataFolder().toPath();
        try {
            Files.createDirectories(data);
        } catch (IOException e) {
            getLogger().warning("Could not create data folder: " + e.getMessage());
        }

        String abilitiesDir = getConfig().getString("abilities-directory", "abilities");
        String effectsDir = getConfig().getString("effects-directory", "effects");
        Path abilitiesPath = data.resolve(abilitiesDir);
        Path effectsPath = data.resolve(effectsDir);
        extractBundledDir(abilitiesDir, abilitiesPath);
        extractBundledDir(effectsDir, effectsPath);

        if (statusService != null) {
            statusService.stopTicker();
        }

        abilityLoader = new AbilityPackLoader();
        statusLoader = new StatusEffectPackLoader();
        abilityLoader.loadDirectory(abilitiesPath);
        statusLoader.loadDirectory(effectsPath);

        statusService = new StatusEffectManager(this, statusLoader);
        EffectRunner effectRunner = new EffectRunner(this, statusService);
        statusService.attachEffectRunner(effectRunner);

        ProjectileTracker projectiles = new ProjectileTracker(this, effectRunner);
        projectileTracker = projectiles;
        AbilityExecutor executor = new AbilityExecutor(this, effectRunner, projectiles);
        abilityService = new AbilityServiceImpl(abilityLoader, executor);
        statusService.startTicker(getConfig().getLong("tick-interval", 10L));
    }

    private void extractBundledDir(String dirName, Path targetDir) {
        try {
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            getLogger().warning("Could not create " + targetDir + ": " + e.getMessage());
            return;
        }
        try {
            Path jarPath = Path.of(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!Files.isRegularFile(jarPath)) {
                copyDevResources(dirName, targetDir);
                return;
            }
            try (JarFile jar = new JarFile(jarPath.toFile())) {
                for (JarEntry entry : jar.stream().toList()) {
                    String name = entry.getName();
                    if (!name.startsWith(dirName + "/") || !name.endsWith(".yml") || entry.isDirectory()) {
                        continue;
                    }
                    String fileName = name.substring(name.lastIndexOf('/') + 1);
                    Path out = targetDir.resolve(fileName);
                    if (Files.exists(out)) {
                        continue;
                    }
                    try (InputStream in = jar.getInputStream(entry)) {
                        Files.copy(in, out);
                    }
                }
            }
        } catch (Exception e) {
            getLogger().warning("Bundled extract " + dirName + ": " + e.getMessage());
            copyDevResources(dirName, targetDir);
        }
    }

    private void copyDevResources(String dirName, Path targetDir) {
        try {
            var urls = getClass().getClassLoader().getResources(dirName);
            while (urls.hasMoreElements()) {
                var url = urls.nextElement();
                if (!"file".equals(url.getProtocol())) {
                    continue;
                }
                Path srcDir = Path.of(url.toURI());
                if (!Files.isDirectory(srcDir)) {
                    continue;
                }
                try (var walk = Files.walk(srcDir)) {
                    walk.filter(p -> p.toString().endsWith(".yml")).forEach(src -> {
                        Path dest = targetDir.resolve(src.getFileName().toString());
                        if (!Files.exists(dest)) {
                            try {
                                Files.copy(src, dest);
                            } catch (IOException ignored) {
                            }
                        }
                    });
                }
            }
        } catch (Exception e) {
            getLogger().warning("Dev resource copy " + dirName + ": " + e.getMessage());
        }
    }

    private void bind(String name, AbilityCommands commands) {
        PluginCommand cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(commands);
            cmd.setTabCompleter(commands);
        }
    }
}
