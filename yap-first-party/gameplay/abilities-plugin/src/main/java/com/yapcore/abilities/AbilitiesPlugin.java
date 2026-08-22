package com.yapcore.abilities;

import com.yapcore.abilities.cmd.AbilityCommands;
import com.yapcore.abilities.exec.AbilityExecutor;
import com.yapcore.abilities.exec.EffectRunner;
import com.yapcore.abilities.exec.ProjectileTracker;
import com.yapcore.abilities.load.AbilityPackLoader;
import com.yapcore.abilities.load.StatusEffectPackLoader;
import com.yapcore.abilities.service.AbilityServiceImpl;
import com.yapcore.abilities.service.StatusEffectManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class AbilitiesPlugin extends JavaPlugin {

    private AbilityPackLoader abilityLoader;
    private StatusEffectPackLoader statusLoader;
    private AbilityServiceImpl abilityService;
    private StatusEffectManager statusService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadAbilities();

        if (!getConfig().getBoolean("enabled", true)) {
            getLogger().info("YaPAbilities disabled via config.");
            return;
        }

        AbilityCommands commands = new AbilityCommands(abilityService);
        bind("ability", commands);
        bind("yapabilities", commands);

        getServer().getServicesManager().register(AbilityService.class, abilityService, this, ServicePriority.Normal);
        getServer().getServicesManager().register(StatusEffectService.class, statusService, this, ServicePriority.Normal);

        getLogger().info("YaP Abilities ready — abilities=" + abilityLoader.abilities().size()
                + ", status-effects=" + statusLoader.effects().size());
    }

    @Override
    public void onDisable() {
        if (abilityService != null) {
            getServer().getServicesManager().unregister(AbilityService.class, abilityService);
        }
        if (statusService != null) {
            getServer().getServicesManager().unregister(StatusEffectService.class, statusService);
        }
    }

    public void reloadAbilities() {
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

        abilityLoader = new AbilityPackLoader();
        statusLoader = new StatusEffectPackLoader();
        abilityLoader.loadDirectory(abilitiesPath);
        statusLoader.loadDirectory(effectsPath);

        statusService = new StatusEffectManager(this, statusLoader);
        EffectRunner effectRunner = new EffectRunner(this, statusService);
        statusService.attachEffectRunner(effectRunner);

        ProjectileTracker projectiles = new ProjectileTracker(this, effectRunner);
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
