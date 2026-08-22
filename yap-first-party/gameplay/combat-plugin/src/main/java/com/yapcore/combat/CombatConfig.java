package com.yapcore.combat;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public final class CombatConfig {

    private final JavaPlugin plugin;
    private boolean enabled = true;
    private boolean useSharedYapdb = true;
    private String jdbcUrl;
    private String jdbcUser;
    private String jdbcPassword;
    private int poolMax = 6;
    private int poolMin = 1;
    private long poolTimeoutMs = 10_000;

    private boolean customHpEnabled = true;
    private int heartsDisplay = 10;
    private int baseHp = 100;
    private int hpPerHitpointsLevel = 10;

    private boolean pvp = false;
    private boolean keepInventory = false;
    private boolean restoreHpOnRespawn = true;

    private double levelFactor = 0.5;
    private int minDamageOnHit = 1;
    private double critChance = 0.05;
    private double critMultiplier = 1.5;

    private int attackCooldownTicks = 4;
    private int rangedCooldownTicks = 8;

    private boolean knockbackEnabled = true;
    private double knockbackBase = 0.15;
    private double knockbackDamageScale = 0.25;
    private double knockbackVertical = 0.12;

    private long skillCacheTtlMs = 5000;

    private double xpDamageMultiplier = 4.0;
    private double xpKillBase = 25.0;
    private double xpAttackShare = 0.25;
    private double xpStrengthShare = 0.25;
    private double xpDefenceShare = 0.25;
    private double xpHitpointsShare = 0.25;
    private double xpRangedShare = 0.5;
    private double xpMagicShare = 0.5;

    private int foodCooldownTicks = 3;
    private int prayerDrainIntervalTicks = 20;
    private Map<String, PotionDef> potions = new HashMap<>();
    private String itemsFile = "items.yml";
    private String foodFile = "food.yml";
    private String spellsFile = "spells.yml";
    private String prayersFile = "prayers.yml";
    private String statusEffectsFile = "status-effects.yml";

    private boolean comboEnabled = true;
    private long comboWindowMs = 3000;
    private int comboMax = 20;
    private double comboBonusPerStack = 0.03;
    private boolean comboResetOnMiss = true;

    private boolean projectilesEnabled = true;
    private double projectileVelocityScale = 1.15;
    private double projectileGravityMultiplier = 0.92;
    private int projectilePiercePerRangedLevels = 25;
    private boolean projectileDropOffEnabled = true;
    private double projectileDropOffPerBlock = 0.004;
    private double projectileMinDropOffMultiplier = 0.55;
    private double projectileHeadshotMultiplier = 1.25;

    private java.util.List<OnHitProc> onHitProcs = java.util.List.of();

    public CombatConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        enabled = c.getBoolean("enabled", true);
        useSharedYapdb = c.getBoolean("use-shared-yapdb", true);
        jdbcUrl = c.getString("jdbc.url", "jdbc:mysql://127.0.0.1:3306/yap");
        jdbcUser = c.getString("jdbc.user", "yap");
        jdbcPassword = c.getString("jdbc.password", "change-me");
        poolMax = c.getInt("pool.maximum-pool-size", 6);
        poolMin = c.getInt("pool.minimum-idle", 1);
        poolTimeoutMs = c.getLong("pool.connection-timeout-ms", 10_000);

        customHpEnabled = c.getBoolean("custom-hp.enabled", true);
        heartsDisplay = Math.max(1, c.getInt("custom-hp.hearts-display", 10));
        baseHp = Math.max(1, c.getInt("custom-hp.base-hp", 100));
        hpPerHitpointsLevel = Math.max(0, c.getInt("custom-hp.hp-per-hitpoints-level", 10));

        pvp = c.getBoolean("pvp", false);
        keepInventory = c.getBoolean("death.keep-inventory", false);
        restoreHpOnRespawn = c.getBoolean("death.restore-hp-on-respawn", true);

        levelFactor = c.getDouble("formula.level-factor", 0.5);
        minDamageOnHit = c.getInt("formula.min-damage-on-hit", 1);
        critChance = c.getDouble("formula.crit-chance", 0.05);
        critMultiplier = c.getDouble("formula.crit-multiplier", 1.5);

        attackCooldownTicks = Math.max(0, c.getInt("combat.attack-cooldown-ticks", 4));
        rangedCooldownTicks = Math.max(0, c.getInt("combat.ranged-cooldown-ticks", 8));
        skillCacheTtlMs = Math.max(1000L, c.getLong("combat.skill-cache-ttl-ms", 5000L));

        knockbackEnabled = c.getBoolean("physics.knockback-enabled", true);
        knockbackBase = c.getDouble("physics.knockback-base", 0.15);
        knockbackDamageScale = c.getDouble("physics.knockback-damage-scale", 0.25);
        knockbackVertical = c.getDouble("physics.knockback-vertical", 0.12);

        xpDamageMultiplier = c.getDouble("xp.damage-multiplier", 4.0);
        xpKillBase = c.getDouble("xp.kill-base", 25.0);
        xpAttackShare = c.getDouble("xp.attack-share", 0.25);
        xpStrengthShare = c.getDouble("xp.strength-share", 0.25);
        xpDefenceShare = c.getDouble("xp.defence-share", 0.25);
        xpHitpointsShare = c.getDouble("xp.hitpoints-share", 0.25);
        xpRangedShare = c.getDouble("xp.ranged-share", 0.5);
        xpMagicShare = c.getDouble("xp.magic-share", 0.5);

        foodCooldownTicks = Math.max(0, c.getInt("food.cooldown-ticks", 3));
        prayerDrainIntervalTicks = Math.max(5, c.getInt("prayer.drain-interval-ticks", 20));

        Map<String, PotionDef> loaded = new HashMap<>();
        var section = c.getConfigurationSection("potions");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                var ps = section.getConfigurationSection(key);
                if (ps == null) {
                    continue;
                }
                loaded.put(key, new PotionDef(
                        key,
                        ps.getString("material", "POTION"),
                        ps.getInt("boost", 5),
                        ps.getInt("duration-seconds", 120),
                        ps.getInt("cooldown-seconds", 60)));
            }
        }
        potions = Map.copyOf(loaded);
        itemsFile = c.getString("items-file", "items.yml");
        foodFile = c.getString("food-file", "food.yml");
        spellsFile = c.getString("spells-file", "spells.yml");
        prayersFile = c.getString("prayers-file", "prayers.yml");
        statusEffectsFile = c.getString("status-effects-file", "status-effects.yml");

        comboEnabled = c.getBoolean("combo.enabled", true);
        comboWindowMs = Math.max(500L, c.getLong("combo.window-ms", 3000L));
        comboMax = Math.max(2, c.getInt("combo.max", 20));
        comboBonusPerStack = Math.max(0, c.getDouble("combo.bonus-per-stack", 0.03));
        comboResetOnMiss = c.getBoolean("combo.reset-on-miss", true);

        projectilesEnabled = c.getBoolean("projectiles.enabled", true);
        projectileVelocityScale = c.getDouble("projectiles.velocity-scale", 1.15);
        projectileGravityMultiplier = c.getDouble("projectiles.gravity-multiplier", 0.92);
        projectilePiercePerRangedLevels = Math.max(0, c.getInt("projectiles.pierce-per-ranged-levels", 25));
        projectileDropOffEnabled = c.getBoolean("projectiles.drop-off-enabled", true);
        projectileDropOffPerBlock = c.getDouble("projectiles.drop-off-per-block", 0.004);
        projectileMinDropOffMultiplier = c.getDouble("projectiles.min-drop-off-multiplier", 0.55);
        projectileHeadshotMultiplier = c.getDouble("projectiles.headshot-multiplier", 1.25);

        java.util.List<OnHitProc> procs = new java.util.ArrayList<>();
        var procSection = c.getConfigurationSection("on-hit-procs");
        if (procSection != null) {
            for (String key : procSection.getKeys(false)) {
                var ps = procSection.getConfigurationSection(key);
                if (ps == null) {
                    continue;
                }
                procs.add(new OnHitProc(
                        key,
                        ps.getString("trigger", "any"),
                        ps.getString("effect", ""),
                        ps.getDouble("chance", 1.0),
                        ps.getInt("stacks", 1)));
            }
        }
        onHitProcs = java.util.List.copyOf(procs);
    }

    public record OnHitProc(String id, String trigger, String effectId, double chance, int stacks) {
        public boolean matches(com.yapcore.combat.formula.DamageCalculator.Result result,
                               com.yapcore.mmo.CombatStyle style) {
            if (effectId == null || effectId.isBlank()) {
                return false;
            }
            return switch (trigger.toLowerCase(java.util.Locale.ROOT)) {
                case "crit" -> result.critical();
                case "melee" -> style == com.yapcore.mmo.CombatStyle.MELEE;
                case "ranged" -> style == com.yapcore.mmo.CombatStyle.RANGED;
                case "magic" -> style == com.yapcore.mmo.CombatStyle.MAGIC;
                default -> true;
            };
        }
    }

    public record ComboConfig(
            boolean enabled,
            long windowMs,
            int maxCombo,
            double bonusPerStack,
            boolean resetOnMiss) {
    }

    public record ProjectileConfig(
            boolean enabled,
            double velocityScale,
            double gravityMultiplier,
            int piercePerRangedLevels,
            boolean dropOffEnabled,
            double dropOffPerBlock,
            double minDropOffMultiplier,
            double headshotMultiplier) {
    }

    public record PotionDef(String id, String material, int boost, int durationSeconds, int cooldownSeconds) {
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean useSharedYapdb() {
        return useSharedYapdb;
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    public String jdbcUser() {
        return jdbcUser;
    }

    public String jdbcPassword() {
        return jdbcPassword;
    }

    public int poolMax() {
        return poolMax;
    }

    public int poolMin() {
        return poolMin;
    }

    public long poolTimeoutMs() {
        return poolTimeoutMs;
    }

    public boolean customHpEnabled() {
        return customHpEnabled;
    }

    public int heartsDisplay() {
        return heartsDisplay;
    }

    public int baseHp() {
        return baseHp;
    }

    public int hpPerHitpointsLevel() {
        return hpPerHitpointsLevel;
    }

    public boolean pvp() {
        return pvp;
    }

    public boolean keepInventory() {
        return keepInventory;
    }

    public boolean restoreHpOnRespawn() {
        return restoreHpOnRespawn;
    }

    public double levelFactor() {
        return levelFactor;
    }

    public int minDamageOnHit() {
        return minDamageOnHit;
    }

    public double critChance() {
        return critChance;
    }

    public double critMultiplier() {
        return critMultiplier;
    }

    public int attackCooldownTicks() {
        return attackCooldownTicks;
    }

    public int rangedCooldownTicks() {
        return rangedCooldownTicks;
    }

    public long skillCacheTtlMs() {
        return skillCacheTtlMs;
    }

    public PhysicsConfig physics() {
        return new PhysicsConfig(knockbackEnabled, knockbackBase, knockbackDamageScale, knockbackVertical);
    }

    public record PhysicsConfig(
            boolean enabled,
            double baseKnockback,
            double damageScale,
            double verticalBoost) {
    }

    public double xpDamageMultiplier() {
        return xpDamageMultiplier;
    }

    public double xpKillBase() {
        return xpKillBase;
    }

    public double xpAttackShare() {
        return xpAttackShare;
    }

    public double xpStrengthShare() {
        return xpStrengthShare;
    }

    public double xpDefenceShare() {
        return xpDefenceShare;
    }

    public double xpHitpointsShare() {
        return xpHitpointsShare;
    }

    public double xpRangedShare() {
        return xpRangedShare;
    }

    public double xpMagicShare() {
        return xpMagicShare;
    }

    public int foodCooldownTicks() {
        return foodCooldownTicks;
    }

    public int prayerDrainIntervalTicks() {
        return prayerDrainIntervalTicks;
    }

    public Map<String, PotionDef> potions() {
        return potions;
    }

    public String itemsFile() {
        return itemsFile;
    }

    public String foodFile() {
        return foodFile;
    }

    public String spellsFile() {
        return spellsFile;
    }

    public String prayersFile() {
        return prayersFile;
    }

    public String statusEffectsFile() {
        return statusEffectsFile;
    }

    public ComboConfig combo() {
        return new ComboConfig(comboEnabled, comboWindowMs, comboMax, comboBonusPerStack, comboResetOnMiss);
    }

    public ProjectileConfig projectiles() {
        return new ProjectileConfig(
                projectilesEnabled,
                projectileVelocityScale,
                projectileGravityMultiplier,
                projectilePiercePerRangedLevels,
                projectileDropOffEnabled,
                projectileDropOffPerBlock,
                projectileMinDropOffMultiplier,
                projectileHeadshotMultiplier);
    }

    public java.util.List<OnHitProc> onHitProcs() {
        return onHitProcs;
    }
}
