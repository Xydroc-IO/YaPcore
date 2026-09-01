package com.yapcore.perms;

import com.yapcore.perms.cmd.PermsCommands;
import com.yapcore.perms.db.PermsDatabase;
import com.yapcore.perms.db.PermsRepository;
import com.yapcore.perms.engine.PermissionApplicator;
import com.yapcore.perms.engine.PermissionResolver;
import com.yapcore.perms.gui.RanksGui;
import com.yapcore.perms.gui.RanksGuiListener;
import com.yapcore.perms.hook.PermsPlaceholders;
import com.yapcore.perms.listener.JoinListener;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PermsPlugin extends JavaPlugin implements YaPPerms {

    private PermsConfig config;
    private PermsDatabase database;
    private PermsRepository repository;
    private PermissionResolver resolver;
    private PermissionApplicator applicator;
    private RanksGui ranksGui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = new PermsConfig(this);
        config.reload();

        database = new PermsDatabase(this, config);
        try {
            database.open();
        } catch (Exception e) {
            getLogger().severe("Failed to open DB — disabling YaPPerms: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        repository = new PermsRepository(database, config);
        resolver = new PermissionResolver(config, repository);
        applicator = new PermissionApplicator(this);

        try {
            reloadAllSync();
            if (config.applyStarterPackOnFirstBoot() && !repository.starterPackApplied()) {
                repository.applyStarterPackFromConfig();
                reloadAllSync();
                getLogger().info("Applied YaP starter rank pack.");
            }
        } catch (Exception e) {
            getLogger().severe("Initial load failed: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getServicesManager().register(YaPPerms.class, this, this, ServicePriority.Normal);
        getServer().getPluginManager().registerEvents(new JoinListener(this), this);

        ranksGui = new RanksGui(this);
        getServer().getPluginManager().registerEvents(new RanksGuiListener(this, ranksGui), this);

        PermsCommands commands = new PermsCommands(this);
        bind("yapperm", commands);
        bind("promote", commands);
        bind("demote", commands);

        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
        PermsPlaceholders.registerIfPresent(this);
        getLogger().info("YaPPerms ready — /yapperm gui for in-game ranks.");
    }

    public RanksGui ranksGui() {
        return ranksGui;
    }

    private void bind(String name, PermsCommands commands) {
        PluginCommand cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(commands);
            cmd.setTabCompleter(commands);
        }
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        applicator.detachAll();
        if (database != null) {
            database.close();
        }
    }

    public void reloadAll() {
        YapSched.async(this, () -> {
            try {
                reloadAllSync();
                YapSched.global(this, () -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        refresh(player);
                    }
                });
            } catch (Exception e) {
                getLogger().severe("Reload failed: " + e.getMessage());
            }
        });
    }

    private void reloadAllSync() throws Exception {
        config.reload();
        resolver.reloadCache();
    }

    public void refreshOnline(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            refresh(player);
        }
    }

    @Override
    public EffectiveUser resolve(UUID uuid, String name) {
        return resolver.resolve(uuid, name);
    }

    @Override
    public CompletableFuture<EffectiveUser> resolveAsync(UUID uuid, String name) {
        return CompletableFuture.supplyAsync(() -> resolve(uuid, name));
    }

    @Override
    public void refresh(Player player) {
        EffectiveUser effective = resolve(player.getUniqueId(), player.getName());
        applicator.apply(player, effective);
    }

    @Override
    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    @Override
    public Optional<String> getPrimaryGroup(UUID uuid) {
        return Optional.of(resolve(uuid, "unknown").primaryGroup());
    }

    @Override
    public Collection<String> getGroups(UUID uuid) {
        return resolver.membershipGroups(uuid, "unknown");
    }

    @Override
    public Optional<String> getPrefix(UUID uuid) {
        String prefix = resolve(uuid, "unknown").prefix();
        return prefix == null || prefix.isEmpty() ? Optional.empty() : Optional.of(prefix);
    }

    @Override
    public Optional<String> getSuffix(UUID uuid) {
        String suffix = resolve(uuid, "unknown").suffix();
        return suffix == null || suffix.isEmpty() ? Optional.empty() : Optional.of(suffix);
    }

    @Override
    public int getWeight(UUID uuid) {
        return resolve(uuid, "unknown").weight();
    }

    @Override
    public String displayGroup(UUID uuid) {
        return resolve(uuid, "unknown").primaryGroup();
    }

    @Override
    public Optional<String> promote(UUID uuid, String track) {
        try {
            return repository.trackStep(uuid, "unknown", track, 1);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> demote(UUID uuid, String track) {
        try {
            return repository.trackStep(uuid, "unknown", track, -1);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public void applyStarterPack() {
        YapSched.async(this, () -> {
            try {
                repository.applyStarterPackFromConfig();
                reloadAll();
            } catch (Exception e) {
                getLogger().severe("applyStarterPack failed: " + e.getMessage());
            }
        });
    }

    @Override
    public void reload() {
        reloadAll();
    }

    @Override
    public Map<String, Boolean> permissionMap(UUID uuid) {
        return resolve(uuid, "unknown").permissions();
    }

    @Override
    public boolean hasPermission(UUID uuid, String node) {
        return PermissionNodes.has(permissionMap(uuid), node);
    }

    public PermsConfig config() {
        return config;
    }

    public PermsRepository repository() {
        return repository;
    }

    public PermissionResolver resolver() {
        return resolver;
    }

    public PermissionApplicator applicator() {
        return applicator;
    }
}
