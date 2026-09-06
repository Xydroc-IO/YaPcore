package com.yapcore.factions;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class FactionsConfig {

    private final JavaPlugin plugin;
    private boolean enabled;
    private boolean useSharedYapdb = true;
    private String jdbcUrl;
    private String jdbcUser;
    private String jdbcPassword;
    private int poolMax = 6;
    private int poolMin = 1;
    private long poolTimeoutMs = 10_000;
    private String labelSingular = "Faction";
    private String labelPlural = "Factions";
    private String labelCommand = "f";
    private int baseMaxPower = 50;
    private int powerPerMember = 10;
    private int claimBlocksPerPower = 100;
    private int powerLossOnDeath = 2;
    private int powerRegenAmount = 1;
    private int powerRegenIntervalMinutes = 30;
    private int shieldSeconds = 3600;
    private boolean shieldBlocksPvp = true;
    private int inviteExpireHours = 48;
    private String factionChatFormat;
    private String allyChatFormat;
    private String territoryEnterMessage;
    private String territoryLeaveMessage;
    private boolean membersCanOpenChests = true;
    private boolean alliesCanOpenChests = true;
    private int mapRadius = 4;
    private int mapCellBlocks = 32;
    private int nameMin = 3;
    private int nameMax = 24;
    private int tagMin = 2;
    private int tagMax = 6;
    private int descriptionMax = 200;
    private int motdMax = 200;
    private int warpNameMax = 24;
    private int maxWarps = 10;
    private boolean alliesCanBuild = true;
    private boolean enemyPvpOnly = true;
    private boolean bankEnabled = true;
    private double bankMinDeposit = 1.0;
    private double bankMinWithdraw = 1.0;
    private boolean warpsRequireInTerritory = true;
    private boolean upkeepEnabled;
    private int upkeepPeriodHours = 24;
    private double upkeepCostPerClaim = 10.0;
    private int upkeepGraceHours = 48;
    private boolean perksTabPrefix = true;
    private boolean perksChatChannelToggle = true;
    private String perksPrefixFormat = "&7[%tag%] ";
    private boolean discordRoleSync;
    private Map<String, String> discordRoles = Map.of();

    public FactionsConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        enabled = c.getBoolean("enabled", false);
        useSharedYapdb = c.getBoolean("use-shared-yapdb", true);
        jdbcUrl = c.getString("jdbc.url", "jdbc:mysql://127.0.0.1:3306/yap");
        jdbcUser = c.getString("jdbc.user", "yap");
        jdbcPassword = c.getString("jdbc.password", "change-me");
        poolMax = c.getInt("pool.maximum-pool-size", 6);
        poolMin = c.getInt("pool.minimum-idle", 1);
        poolTimeoutMs = c.getLong("pool.connection-timeout-ms", 10_000);
        labelSingular = c.getString("labels.singular", "Faction");
        labelPlural = c.getString("labels.plural", "Factions");
        labelCommand = c.getString("labels.command", "f");
        baseMaxPower = c.getInt("power.base-max", 50);
        powerPerMember = c.getInt("power.per-member", 10);
        claimBlocksPerPower = Math.max(1, c.getInt("power.claim-blocks-per-power", 100));
        powerLossOnDeath = Math.max(0, c.getInt("power.loss-on-death", 2));
        powerRegenAmount = Math.max(0, c.getInt("power.regen-amount", 1));
        powerRegenIntervalMinutes = Math.max(1, c.getInt("power.regen-interval-minutes", 30));
        shieldSeconds = Math.max(0, c.getInt("shield.seconds", 3600));
        shieldBlocksPvp = c.getBoolean("shield.blocks-pvp", true);
        inviteExpireHours = Math.max(1, c.getInt("invites.expire-hours", 48));
        factionChatFormat = c.getString("chat.faction-format",
                "§7[§aF§7] §f%tag% §7| §f%player%§7: §f%message%");
        allyChatFormat = c.getString("chat.ally-format",
                "§7[§bA§7] §f%tag% §7| §f%player%§7: §f%message%");
        territoryEnterMessage = c.getString("territory.enter-message",
                "§7Entering §f%faction% §7territory.");
        territoryLeaveMessage = c.getString("territory.leave-message",
                "§7Leaving §f%faction% §7territory.");
        membersCanOpenChests = c.getBoolean("territory.members-can-open-chests", true);
        alliesCanOpenChests = c.getBoolean("territory.allies-can-open-chests", true);
        mapRadius = Math.max(1, c.getInt("map.radius", 4));
        mapCellBlocks = Math.max(8, c.getInt("map.cell-blocks", 32));
        nameMin = c.getInt("limits.name-min", 3);
        nameMax = c.getInt("limits.name-max", 24);
        tagMin = c.getInt("limits.tag-min", 2);
        tagMax = c.getInt("limits.tag-max", 6);
        descriptionMax = c.getInt("limits.description-max", 200);
        motdMax = c.getInt("limits.motd-max", 200);
        warpNameMax = Math.max(1, c.getInt("limits.warp-name-max", 24));
        maxWarps = Math.max(1, c.getInt("limits.max-warps", 10));
        alliesCanBuild = c.getBoolean("relations.allies-can-build", true);
        enemyPvpOnly = c.getBoolean("relations.enemy-pvp-only", true);
        bankEnabled = c.getBoolean("bank.enabled", true);
        bankMinDeposit = c.getDouble("bank.min-deposit", 1.0);
        bankMinWithdraw = c.getDouble("bank.min-withdraw", 1.0);
        warpsRequireInTerritory = c.getBoolean("warps.require-in-territory", true);
        upkeepEnabled = c.getBoolean("upkeep.enabled", false);
        upkeepPeriodHours = Math.max(1, c.getInt("upkeep.period-hours", 24));
        upkeepCostPerClaim = Math.max(0, c.getDouble("upkeep.cost-per-linked-claim", 10.0));
        upkeepGraceHours = Math.max(0, c.getInt("upkeep.grace-hours", 48));
        perksTabPrefix = c.getBoolean("perks.tab-prefix", true);
        perksChatChannelToggle = c.getBoolean("perks.chat-channel-toggle", true);
        perksPrefixFormat = c.getString("perks.prefix-format", "&7[%tag%] ");
        discordRoleSync = c.getBoolean("discord.role-sync", false);
        Map<String, String> roles = new LinkedHashMap<>();
        ConfigurationSection section = c.getConfigurationSection("discord.roles");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String roleId = section.getString(key, "");
                if (roleId != null && !roleId.isBlank()) {
                    roles.put(key.trim().toUpperCase(Locale.ROOT), roleId.trim());
                }
            }
        }
        discordRoles = Collections.unmodifiableMap(roles);
    }

    public FactionPowerCalculator.Config powerConfig() {
        return new FactionPowerCalculator.Config(baseMaxPower, powerPerMember, claimBlocksPerPower);
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

    public String labelSingular() {
        return labelSingular;
    }

    public String labelPlural() {
        return labelPlural;
    }

    public String labelCommand() {
        return labelCommand;
    }

    public int nameMin() {
        return nameMin;
    }

    public int nameMax() {
        return nameMax;
    }

    public int tagMin() {
        return tagMin;
    }

    public int tagMax() {
        return tagMax;
    }

    public int descriptionMax() {
        return descriptionMax;
    }

    public int motdMax() {
        return motdMax;
    }

    public int warpNameMax() {
        return warpNameMax;
    }

    public int maxWarps() {
        return maxWarps;
    }

    public boolean alliesCanBuild() {
        return alliesCanBuild;
    }

    public boolean enemyPvpOnly() {
        return enemyPvpOnly;
    }

    public int powerLossOnDeath() {
        return powerLossOnDeath;
    }

    public int powerRegenAmount() {
        return powerRegenAmount;
    }

    public long powerRegenIntervalTicks() {
        return powerRegenIntervalMinutes * 60L * 20L;
    }

    public int shieldSeconds() {
        return shieldSeconds;
    }

    public boolean shieldBlocksPvp() {
        return shieldBlocksPvp;
    }

    public int inviteExpireHours() {
        return inviteExpireHours;
    }

    public String factionChatFormat() {
        return factionChatFormat;
    }

    public String allyChatFormat() {
        return allyChatFormat;
    }

    public String territoryEnterMessage() {
        return territoryEnterMessage;
    }

    public String territoryLeaveMessage() {
        return territoryLeaveMessage;
    }

    public boolean membersCanOpenChests() {
        return membersCanOpenChests;
    }

    public boolean alliesCanOpenChests() {
        return alliesCanOpenChests;
    }

    public int mapRadius() {
        return mapRadius;
    }

    public int mapCellBlocks() {
        return mapCellBlocks;
    }

    public boolean bankEnabled() {
        return bankEnabled;
    }

    public double bankMinDeposit() {
        return bankMinDeposit;
    }

    public double bankMinWithdraw() {
        return bankMinWithdraw;
    }

    public boolean warpsRequireInTerritory() {
        return warpsRequireInTerritory;
    }

    public boolean upkeepEnabled() {
        return upkeepEnabled;
    }

    public long upkeepPeriodTicks() {
        return upkeepPeriodHours * 60L * 60L * 20L;
    }

    public double upkeepCostPerClaim() {
        return upkeepCostPerClaim;
    }

    public int upkeepGraceHours() {
        return upkeepGraceHours;
    }

    public boolean perksTabPrefix() {
        return perksTabPrefix;
    }

    public boolean perksChatChannelToggle() {
        return perksChatChannelToggle;
    }

    public String perksPrefixFormat() {
        return perksPrefixFormat;
    }

    public boolean discordRoleSync() {
        return discordRoleSync;
    }

    public Map<String, String> discordRoles() {
        return discordRoles;
    }
}
