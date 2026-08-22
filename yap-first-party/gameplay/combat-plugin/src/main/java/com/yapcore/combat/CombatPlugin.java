package com.yapcore.combat;

import com.yapcore.abilities.AbilityCombatBridge;
import com.yapcore.combat.integration.CombatAbilityBridge;
import com.yapcore.combat.cmd.CombatCommands;
import com.yapcore.combat.cmd.MagicPrayerCommands;
import com.yapcore.combat.combo.ComboService;
import com.yapcore.combat.db.CombatDatabase;
import com.yapcore.combat.db.CombatRepository;
import com.yapcore.combat.food.FoodLoader;
import com.yapcore.combat.formula.CombatAttackGate;
import com.yapcore.combat.gear.GearBonusLoader;
import com.yapcore.combat.listener.CombatDamageListener;
import com.yapcore.combat.listener.CombatProjectileListener;
import com.yapcore.combat.listener.CombatSkillCacheListener;
import com.yapcore.combat.listener.FoodPotionListener;
import com.yapcore.combat.listener.GearEquipListener;
import com.yapcore.combat.listener.PlayerLifecycleListener;
import com.yapcore.combat.listener.PrayerDrainListener;
import com.yapcore.combat.listener.StatusEffectTicker;
import com.yapcore.combat.prayer.PrayerBookLoader;
import com.yapcore.combat.projectile.CombatProjectileKeys;
import com.yapcore.combat.service.CombatHitPipeline;
import com.yapcore.combat.service.CombatServiceImpl;
import com.yapcore.combat.service.CombatXpAwarder;
import com.yapcore.combat.service.PrayerService;
import com.yapcore.combat.service.SpellBookService;
import com.yapcore.combat.spell.SpellBookLoader;
import com.yapcore.combat.status.StatusEffectRegistry;
import com.yapcore.combat.status.StatusEffectService;
import com.yapcore.mmo.CombatService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;

public final class CombatPlugin extends JavaPlugin {

    private CombatConfig config;
    private CombatDatabase database;
    private CombatRepository repository;
    private GearBonusLoader gearLoader;
    private FoodLoader foodLoader;
    private SpellBookLoader spellLoader;
    private PrayerBookLoader prayerLoader;
    private StatusEffectRegistry statusRegistry;
    private StatusEffectService statusService;
    private ComboService comboService;
    private CombatAttackGate attackGate;
    private CombatProjectileKeys projectileKeys;
    private CombatServiceImpl combatService;
    private CombatXpAwarder xpAwarder;
    private CombatHitPipeline hitPipeline;
    private SpellBookService spellBook;
    private PrayerService prayerService;
    private PrayerDrainListener prayerDrainTask;
    private StatusEffectTicker statusTicker;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadCombat();

        if (!config.enabled()) {
            getLogger().info("YaPCombat disabled via config.");
            return;
        }
        if (combatService == null) {
            getLogger().severe("YaPCombat failed to start — database or config not ready.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(new CombatDamageListener(this), this);
        getServer().getPluginManager().registerEvents(
                new CombatProjectileListener(this, config, combatService, hitPipeline, projectileKeys), this);
        getServer().getPluginManager().registerEvents(new FoodPotionListener(this, config, combatService, foodLoader), this);
        getServer().getPluginManager().registerEvents(new PlayerLifecycleListener(this, config, combatService), this);
        getServer().getPluginManager().registerEvents(new GearEquipListener(this, combatService), this);
        getServer().getPluginManager().registerEvents(new CombatSkillCacheListener(combatService), this);

        statusTicker = new StatusEffectTicker(this, statusService, combatService);
        getServer().getPluginManager().registerEvents(statusTicker, this);
        statusTicker.start();

        MagicPrayerCommands magicPrayer = new MagicPrayerCommands(spellBook, prayerService);
        bindCommand("spells", magicPrayer);
        bindCommand("cast", magicPrayer);
        bindCommand("prayer", magicPrayer);

        CombatCommands commands = new CombatCommands(this, combatService, statusService, comboService);
        bindCommand("combat", commands);
        bindCommand("yapcombat", commands);

        prayerDrainTask = new PrayerDrainListener(this, config, prayerService);
        prayerDrainTask.start();

        getServer().getServicesManager().register(CombatService.class, combatService, this, ServicePriority.Normal);
        getServer().getServicesManager().register(
                com.yapcore.abilities.AbilityCombatBridge.class,
                new com.yapcore.combat.integration.CombatAbilityBridge(this, combatService, xpAwarder),
                this,
                ServicePriority.Normal);
        getLogger().info("YaPCombat ready — pvp=" + config.pvp()
                + ", spells=" + spellLoader.spells().size()
                + ", prayers=" + prayerLoader.prayers().size()
                + ", status=" + statusRegistry.effects().size());
    }

    @Override
    public void onDisable() {
        var sm = getServer().getServicesManager();
        if (combatService != null) {
            sm.unregister(CombatService.class, combatService);
        }
        sm.unregisterAll(this);
        if (database != null) {
            database.close();
        }
    }

    public void reloadCombat() {
        if (config == null) {
            config = new CombatConfig(this);
        }
        config.reload();

        if (database == null) {
            database = new CombatDatabase(this, config);
        }
        try {
            database.open();
        } catch (Exception e) {
            getLogger().severe("YaPCombat database failed: " + e.getMessage());
            return;
        }
        if (repository == null) {
            repository = new CombatRepository(database);
        }

        if (gearLoader == null) {
            gearLoader = new GearBonusLoader(this);
        }
        if (foodLoader == null) {
            foodLoader = new FoodLoader();
        }
        if (spellLoader == null) {
            spellLoader = new SpellBookLoader();
        }
        if (prayerLoader == null) {
            prayerLoader = new PrayerBookLoader();
        }
        if (statusRegistry == null) {
            statusRegistry = new StatusEffectRegistry();
        }
        if (projectileKeys == null) {
            projectileKeys = new CombatProjectileKeys(this);
        }
        if (attackGate == null) {
            attackGate = new CombatAttackGate();
        }
        try {
            Path data = getDataFolder().toPath();
            Files.createDirectories(data);
            copyResourceIfMissing(data, config.spellsFile());
            copyResourceIfMissing(data, config.prayersFile());
            copyResourceIfMissing(data, config.statusEffectsFile());
            gearLoader.reload(data.resolve(config.itemsFile()));
            foodLoader.reload(this, data.resolve(config.foodFile()));
            spellLoader.load(data.resolve(config.spellsFile()));
            prayerLoader.load(data.resolve(config.prayersFile()));
            statusRegistry.load(data.resolve(config.statusEffectsFile()));
        } catch (Exception e) {
            getLogger().warning("YaPCombat config files: " + e.getMessage());
        }

        statusService = new StatusEffectService(statusRegistry);
        comboService = new ComboService(config.combo());

        var sm = getServer().getServicesManager();
        if (combatService != null) {
            sm.unregister(CombatService.class, combatService);
        }
        combatService = new CombatServiceImpl(this, config, repository, gearLoader, prayerLoader);
        xpAwarder = new CombatXpAwarder(this, config);
        hitPipeline = new CombatHitPipeline(
                this, config, combatService, xpAwarder, statusService, comboService, attackGate);
        spellBook = new SpellBookService(this, combatService, xpAwarder, spellLoader, statusService);
        prayerService = new PrayerService(this, config, combatService, prayerLoader);
        if (isEnabled()) {
            sm.register(CombatService.class, combatService, this, ServicePriority.Normal);
        }
    }

    public CombatConfig combatConfig() {
        return config;
    }

    public CombatServiceImpl combatService() {
        return combatService;
    }

    public CombatXpAwarder xpAwarder() {
        return xpAwarder;
    }

    public CombatHitPipeline hitPipeline() {
        return hitPipeline;
    }

    public StatusEffectService statusService() {
        return statusService;
    }

    public ComboService comboService() {
        return comboService;
    }

    public CombatProjectileKeys projectileKeys() {
        return projectileKeys;
    }

    private void copyResourceIfMissing(Path data, String fileName) throws java.io.IOException {
        Path target = data.resolve(fileName);
        if (Files.exists(target)) {
            return;
        }
        try (var in = getResource(fileName)) {
            if (in != null) {
                Files.copy(in, target);
            }
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
