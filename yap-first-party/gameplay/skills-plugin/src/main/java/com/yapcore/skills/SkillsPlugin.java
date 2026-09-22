package com.yapcore.skills;

import com.yapcore.mmo.SkillService;
import com.yapcore.mmo.XpTable;
import com.yapcore.skills.cmd.SkillAdminCommand;
import com.yapcore.skills.cmd.SkillsCommand;
import com.yapcore.skills.cmd.YSkillsCommand;
import com.yapcore.skills.db.SkillDatabase;
import com.yapcore.skills.db.SkillRepository;
import com.yapcore.skills.gui.SkillsMenu;
import com.yapcore.skills.gui.SkillsMenuListener;
import com.yapcore.skills.listener.AlchemySkillListener;
import com.yapcore.skills.listener.BreakSkillListener;
import com.yapcore.skills.listener.BuilderSkillListener;
import com.yapcore.skills.listener.CombatSkillListener;
import com.yapcore.skills.listener.ExcavationSkillListener;
import com.yapcore.skills.listener.FishingSkillListener;
import com.yapcore.skills.listener.HealthSkillListener;
import com.yapcore.skills.listener.HerbalismSkillListener;
import com.yapcore.skills.listener.MarathonSkillListener;
import com.yapcore.skills.listener.SkillAbilityListener;
import com.yapcore.skills.listener.SkillLevelListener;
import com.yapcore.skills.listener.SkillPowerBreakListener;
import com.yapcore.skills.listener.SkillPowerCombatListener;
import com.yapcore.skills.listener.SmeltSkillListener;
import com.yapcore.skills.listener.SwimmingSkillListener;
import com.yapcore.skills.papi.SkillsPlaceholders;
import com.yapcore.skills.power.SkillAbilities;
import com.yapcore.skills.power.SkillBreakSpeed;
import com.yapcore.skills.power.SkillLevelCache;
import com.yapcore.skills.power.SkillMaxHealth;
import com.yapcore.skills.power.SkillMoveSpeed;
import com.yapcore.skills.power.SkillPlaceReach;
import com.yapcore.skills.power.SkillPowerMath;
import com.yapcore.skills.power.SkillPowerSettings;
import com.yapcore.skills.power.SkillSwimPower;
import com.yapcore.skills.service.SkillServiceImpl;
import com.yapcore.skills.skill.SkillPackLoader;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class SkillsPlugin extends JavaPlugin {

    private static final List<String> DEFAULT_SKILL_PACKS = List.of(
            "mining.yml", "woodcutting.yml", "strength.yml", "marathon.yml", "swimming.yml", "builder.yml",
            "herbalism.yml", "excavation.yml", "alchemy.yml", "health.yml");

    private SkillsConfig config;
    private SkillDatabase database;
    private SkillRepository repository;
    private SkillPackLoader loader;
    private final SkillLevelCache levelCache = new SkillLevelCache();
    private SkillPowerSettings powerSettings = SkillPowerSettings.defaults();
    private SkillServiceImpl skillService;
    private SkillsMenu menu;
    private SkillsPlaceholders placeholders;
    private SkillLevelListener levelListener;
    private MarathonSkillListener marathon;
    private SwimmingSkillListener swimming;
    private HealthSkillListener health;
    private final SkillAbilities abilities = new SkillAbilities();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSkills();

        if (!config.enabled()) {
            getLogger().info("YaPSkills disabled via config.");
            return;
        }

        getServer().getPluginManager().registerEvents(new BreakSkillListener(this), this);
        getServer().getPluginManager().registerEvents(new FishingSkillListener(this), this);
        getServer().getPluginManager().registerEvents(new SmeltSkillListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatSkillListener(this), this);
        getServer().getPluginManager().registerEvents(new SkillPowerBreakListener(this), this);
        // After CombatSkillListener so the hit-context clear runs last at MONITOR.
        getServer().getPluginManager().registerEvents(new SkillPowerCombatListener(this), this);
        levelListener = new SkillLevelListener(this);
        getServer().getPluginManager().registerEvents(levelListener, this);
        marathon = new MarathonSkillListener(this);
        getServer().getPluginManager().registerEvents(marathon, this);
        swimming = new SwimmingSkillListener(this);
        getServer().getPluginManager().registerEvents(swimming, this);
        getServer().getPluginManager().registerEvents(new BuilderSkillListener(this), this);
        getServer().getPluginManager().registerEvents(new SkillAbilityListener(this), this);
        getServer().getPluginManager().registerEvents(new HerbalismSkillListener(this), this);
        getServer().getPluginManager().registerEvents(new ExcavationSkillListener(this), this);
        getServer().getPluginManager().registerEvents(new AlchemySkillListener(this), this);
        health = new HealthSkillListener(this);
        getServer().getPluginManager().registerEvents(health, this);
        getServer().getPluginManager().registerEvents(new SkillsMenuListener(), this);

        bindPlayerCommands();
        bindCommand("yskills", new YSkillsCommand(this));

        getServer().getServicesManager().register(SkillService.class, skillService, this, ServicePriority.Normal);
        placeholders = new SkillsPlaceholders(skillService);
        placeholders.tryRegister();

        if (config.preferOverJobs()) {
            getLogger().info("Tip: set playerdata features.jobs=false when using YaPSkills.");
        }
        warmLevels();
        getLogger().info("YaPSkills ready — skills=" + loader.skills().size());
    }

    @Override
    public void onDisable() {
        var sm = getServer().getServicesManager();
        if (skillService != null) {
            sm.unregister(SkillService.class, skillService);
        }
        if (placeholders != null) {
            placeholders.unregister();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                SkillBreakSpeed.clear(this, player);
                SkillMoveSpeed.clear(this, player);
                SkillSwimPower.clear(this, player);
                SkillPlaceReach.clear(this, player);
                SkillMaxHealth.clear(this, player);
            } catch (Throwable ignored) {
                // Disable is not always the owning region. The modifier is transient either way.
            }
        }
        if (marathon != null) {
            marathon.untrackAll();
        }
        if (swimming != null) {
            swimming.untrackAll();
        }
        if (health != null) {
            health.stopAll();
        }
        if (database != null) {
            database.close();
        }
    }

    public void reloadSkills() {
        if (config == null) {
            config = new SkillsConfig(this);
        }
        config.reload();
        powerSettings = SkillPowerSettings.from(getConfig());

        if (database == null) {
            database = new SkillDatabase(this, config);
        }
        try {
            database.open();
        } catch (Exception e) {
            getLogger().severe("YaPSkills database failed: " + e.getMessage());
            return;
        }

        if (repository == null) {
            repository = new SkillRepository(database);
        }

        Path skillsDir = getDataFolder().toPath().resolve(config.skillsDirectory());
        try {
            Files.createDirectories(skillsDir);
            for (String pack : DEFAULT_SKILL_PACKS) {
                Path dest = skillsDir.resolve(pack);
                if (!Files.exists(dest)) {
                    saveResource("skills/" + pack, false);
                }
            }
        } catch (Exception e) {
            getLogger().warning("Could not prepare skills dir: " + e.getMessage());
        }

        loader = new SkillPackLoader(skillsDir);
        loader.reload();

        XpTable table = XpTable.runescape(config.maxLevel(), config.xpMultiplier());
        XpTable overallTable = XpTable.runescape(config.overallMaxLevel(), config.overallXpMultiplier());
        var sm = getServer().getServicesManager();
        if (skillService != null) {
            sm.unregister(SkillService.class, skillService);
        }
        levelCache.clear();
        skillService = new SkillServiceImpl(
                this, config, repository, loader, table, overallTable, levelCache, powerSettings);
        menu = new SkillsMenu(this, skillService);
        warmLevels();
    }

    public void reregisterService() {
        if (!config.enabled() || skillService == null) {
            return;
        }
        var sm = getServer().getServicesManager();
        sm.unregister(SkillService.class, skillService);
        sm.register(SkillService.class, skillService, this, ServicePriority.Normal);
        if (placeholders != null) {
            placeholders.unregister();
        }
        placeholders = new SkillsPlaceholders(skillService);
        placeholders.tryRegister();
        bindPlayerCommands();
    }

    private void bindPlayerCommands() {
        if (menu == null || skillService == null) {
            return;
        }
        bindCommand("skills", new SkillsCommand(menu));
        bindCommand("skill", new SkillAdminCommand(skillService, menu));
    }

    public SkillServiceImpl skillService() {
        return skillService;
    }

    public SkillLevelCache levels() {
        return levelCache;
    }

    public SkillPowerSettings power() {
        return powerSettings;
    }

    public SkillAbilities abilities() {
        return abilities;
    }

    public void warmLevels() {
        if (levelListener != null && skillService != null) {
            levelListener.warmOnline();
        }
        if (marathon != null) {
            marathon.trackOnline();
        }
        if (swimming != null) {
            swimming.trackOnline();
        }
        if (health != null) {
            health.startOnline();
        }
    }

    public void applyMarathonSpeed(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR || !powerSettings.enabled()) {
            SkillMoveSpeed.clear(this, player);
            return;
        }
        if (skillService == null) {
            SkillMoveSpeed.clear(this, player);
            return;
        }
        var def = skillService.definition(MarathonSkillListener.MARATHON).orElse(null);
        if (def == null || !def.enabled()) {
            SkillMoveSpeed.clear(this, player);
            return;
        }
        int level = levelCache.loaded(player.getUniqueId())
                ? levelCache.level(player.getUniqueId(), def.id())
                : 1;
        double multiplier = SkillPowerMath.moveSpeed(
                level, skillService.xpTable().maxLevel(), powerSettings.movementSpeedBonusAtMax());
        SkillMoveSpeed.apply(this, player, multiplier);
    }

    public void applySwimming(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR || !powerSettings.enabled()) {
            SkillSwimPower.clear(this, player);
            return;
        }
        if (skillService == null) {
            SkillSwimPower.clear(this, player);
            return;
        }
        var def = skillService.definition(SwimmingSkillListener.SWIMMING).orElse(null);
        if (def == null || !def.enabled()) {
            SkillSwimPower.clear(this, player);
            return;
        }
        int level = levelCache.loaded(player.getUniqueId())
                ? levelCache.level(player.getUniqueId(), def.id())
                : 1;
        int max = skillService.xpTable().maxLevel();
        double water = SkillPowerMath.swimWaterEfficiency(
                level, max, powerSettings.swimWaterEfficiencyAtMax());
        double oxygen = SkillPowerMath.swimOxygenBonus(
                level, max, powerSettings.swimOxygenBonusAtMax());
        SkillSwimPower.apply(this, player, water, oxygen);
    }

    public void applyBuilderReach(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR || !powerSettings.enabled()) {
            SkillPlaceReach.clear(this, player);
            return;
        }
        if (skillService == null) {
            SkillPlaceReach.clear(this, player);
            return;
        }
        var def = skillService.definition(BuilderSkillListener.BUILDER).orElse(null);
        if (def == null || !def.enabled()) {
            SkillPlaceReach.clear(this, player);
            return;
        }
        int level = levelCache.loaded(player.getUniqueId())
                ? levelCache.level(player.getUniqueId(), def.id())
                : 1;
        double extra = SkillPowerMath.placeReach(
                level, skillService.xpTable().maxLevel(), powerSettings.placeReachBonusAtMax());
        SkillPlaceReach.apply(this, player, extra);
    }

    public void applyHealth(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR || !powerSettings.enabled()) {
            SkillMaxHealth.clear(this, player);
            return;
        }
        if (skillService == null) {
            SkillMaxHealth.clear(this, player);
            return;
        }
        var def = skillService.definition(HealthSkillListener.HEALTH).orElse(null);
        if (def == null || !def.enabled()) {
            SkillMaxHealth.clear(this, player);
            return;
        }
        int level = levelCache.loaded(player.getUniqueId())
                ? levelCache.level(player.getUniqueId(), def.id())
                : 1;
        double extra = SkillPowerMath.extraHearts(
                level, skillService.xpTable().maxLevel(), powerSettings.extraHeartsAtMax());
        SkillMaxHealth.apply(this, player, extra);
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
