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
import com.yapcore.skills.listener.BreakSkillListener;
import com.yapcore.skills.listener.CombatSkillListener;
import com.yapcore.skills.listener.FishingSkillListener;
import com.yapcore.skills.listener.SmeltSkillListener;
import com.yapcore.skills.papi.SkillsPlaceholders;
import com.yapcore.skills.service.SkillServiceImpl;
import com.yapcore.skills.skill.SkillPackLoader;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class SkillsPlugin extends JavaPlugin {

    private static final List<String> DEFAULT_SKILL_PACKS = List.of(
            "mining.yml", "woodcutting.yml", "fishing.yml", "cooking.yml", "smithing.yml", "crafting.yml",
            "attack.yml", "strength.yml", "defence.yml", "hitpoints.yml",
            "ranged.yml", "magic.yml", "prayer.yml");

    private SkillsConfig config;
    private SkillDatabase database;
    private SkillRepository repository;
    private SkillPackLoader loader;
    private SkillServiceImpl skillService;
    private SkillsMenu menu;
    private SkillsPlaceholders placeholders;

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
        getServer().getPluginManager().registerEvents(new SkillsMenuListener(), this);

        bindCommand("skills", new SkillsCommand(menu));
        bindCommand("skill", new SkillAdminCommand(skillService));
        bindCommand("yskills", new YSkillsCommand(this));

        getServer().getServicesManager().register(SkillService.class, skillService, this, ServicePriority.Normal);
        placeholders = new SkillsPlaceholders(skillService);
        placeholders.tryRegister();

        if (config.preferOverJobs()) {
            getLogger().info("Tip: set playerdata features.jobs=false when using YaPSkills.");
        }
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
        if (database != null) {
            database.close();
        }
    }

    public void reloadSkills() {
        if (config == null) {
            config = new SkillsConfig(this);
        }
        config.reload();

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
        var sm = getServer().getServicesManager();
        if (skillService != null) {
            sm.unregister(SkillService.class, skillService);
        }
        skillService = new SkillServiceImpl(this, config, repository, loader, table);
        menu = new SkillsMenu(this, skillService);
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
    }

    public SkillServiceImpl skillService() {
        return skillService;
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
