package com.yapcore.essentials;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class EssentialsConfig {

    private final JavaPlugin plugin;
    private Location fileSpawn;
    private int tpaTimeoutSeconds = 60;
    private String afkPrefix = "&7[AFK] ";
    private List<String> rules = List.of("&6Server Rules");
    private List<String> motd = List.of("&aWelcome!");
    private String broadcastFormat = "&6[Broadcast] &f{message}";

    private boolean useSharedYapDb = true;
    private String jdbcUrl = "jdbc:mysql://127.0.0.1:3306/yap?useSSL=false&allowPublicKeyRetrieval=true";
    private String jdbcUser = "yap";
    private String jdbcPassword = "yap";
    private int poolMax = 4;
    private int poolMinIdle = 1;
    private long connectionTimeoutMs = 10_000L;

    private String serverId = "lobby";
    private String spawnScope = "server";
    private boolean spawnPersistDb = true;

    private boolean featureSpawn = true;
    private boolean featureBack = true;
    private boolean featureTpa = true;
    private boolean featureTeleport = true;
    private boolean featureItem = true;
    private boolean featureGamemode = true;
    private boolean featureFly = true;
    private boolean featureGod = true;
    private boolean featureSpeed = true;
    private boolean featureHeal = true;
    private boolean featureFeed = true;
    private boolean featureRepair = true;
    private boolean featureClear = true;
    private boolean featureVanish = true;
    private boolean featureInvsee = true;
    private boolean featureEchest = true;
    private boolean featureNick = true;
    private boolean featureAfk = true;
    private boolean featureList = true;
    private boolean featurePtime = true;
    private boolean featurePweather = true;
    private boolean featureBroadcast = true;
    private boolean featureRules = true;
    private boolean featureMotd = true;
    private boolean featureSuicide = true;
    private boolean featureHat = true;
    private boolean featureStaff = true;

    public EssentialsConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        String world = c.getString("spawn.world", "world");
        var w = Bukkit.getWorld(world);
        if (w != null) {
            fileSpawn = new Location(
                    w,
                    c.getDouble("spawn.x", 0.5),
                    c.getDouble("spawn.y", 80.0),
                    c.getDouble("spawn.z", 0.5),
                    (float) c.getDouble("spawn.yaw", 0.0),
                    (float) c.getDouble("spawn.pitch", 0.0));
        } else {
            fileSpawn = null;
        }
        tpaTimeoutSeconds = Math.max(10, c.getInt("tpa.timeout-seconds", 60));
        afkPrefix = c.getString("afk.prefix", afkPrefix);
        rules = c.getStringList("messages.rules");
        motd = c.getStringList("messages.motd");
        broadcastFormat = c.getString("broadcast-format", broadcastFormat);

        useSharedYapDb = c.getBoolean("use-shared-yapdb", true);
        jdbcUrl = c.getString("database.jdbc-url", jdbcUrl);
        jdbcUser = c.getString("database.user", jdbcUser);
        jdbcPassword = c.getString("database.password", jdbcPassword);
        poolMax = c.getInt("database.pool-max", poolMax);
        poolMinIdle = c.getInt("database.pool-min-idle", poolMinIdle);
        connectionTimeoutMs = c.getLong("database.connection-timeout-ms", connectionTimeoutMs);

        serverId = c.getString("server-id", serverId);
        spawnScope = c.getString("spawn.scope", spawnScope);
        spawnPersistDb = c.getBoolean("spawn.persist-db", spawnPersistDb);

        featureSpawn = c.getBoolean("features.spawn", true);
        featureBack = c.getBoolean("features.back", true);
        featureTpa = c.getBoolean("features.tpa", true);
        featureTeleport = c.getBoolean("features.teleport", true);
        featureItem = c.getBoolean("features.item", true);
        featureGamemode = c.getBoolean("features.gamemode", true);
        featureFly = c.getBoolean("features.fly", true);
        featureGod = c.getBoolean("features.god", true);
        featureSpeed = c.getBoolean("features.speed", true);
        featureHeal = c.getBoolean("features.heal", true);
        featureFeed = c.getBoolean("features.feed", true);
        featureRepair = c.getBoolean("features.repair", true);
        featureClear = c.getBoolean("features.clear", true);
        featureVanish = c.getBoolean("features.vanish", true);
        featureInvsee = c.getBoolean("features.invsee", true);
        featureEchest = c.getBoolean("features.echest", true);
        featureNick = c.getBoolean("features.nick", true);
        featureAfk = c.getBoolean("features.afk", true);
        featureList = c.getBoolean("features.list", true);
        featurePtime = c.getBoolean("features.ptime", true);
        featurePweather = c.getBoolean("features.pweather", true);
        featureBroadcast = c.getBoolean("features.broadcast", true);
        featureRules = c.getBoolean("features.rules", true);
        featureMotd = c.getBoolean("features.motd", true);
        featureSuicide = c.getBoolean("features.suicide", true);
        featureHat = c.getBoolean("features.hat", true);
        featureStaff = c.getBoolean("features.staff", true);
    }

    public Location fileSpawn() {
        return fileSpawn == null ? null : fileSpawn.clone();
    }

    public void saveFileSpawn(Location location) {
        this.fileSpawn = location.clone();
        FileConfiguration c = plugin.getConfig();
        c.set("spawn.world", location.getWorld().getName());
        c.set("spawn.x", location.getX());
        c.set("spawn.y", location.getY());
        c.set("spawn.z", location.getZ());
        c.set("spawn.yaw", location.getYaw());
        c.set("spawn.pitch", location.getPitch());
        plugin.saveConfig();
    }

    public String spawnScopeKey() {
        return "global".equalsIgnoreCase(spawnScope) ? "global" : serverId;
    }

    public int tpaTimeoutSeconds() {
        return tpaTimeoutSeconds;
    }

    public String afkPrefix() {
        return afkPrefix;
    }

    public List<String> rules() {
        return rules;
    }

    public List<String> motd() {
        return motd;
    }

    public String broadcastFormat() {
        return broadcastFormat;
    }

    public boolean useSharedYapDb() {
        return useSharedYapDb;
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

    public int poolMinIdle() {
        return poolMinIdle;
    }

    public long connectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    public String serverId() {
        return serverId;
    }

    public boolean spawnPersistDb() {
        return spawnPersistDb;
    }

    public boolean feature(String name) {
        return switch (name) {
            case "spawn" -> featureSpawn;
            case "back" -> featureBack;
            case "tpa" -> featureTpa;
            case "teleport" -> featureTeleport;
            case "item" -> featureItem;
            case "gamemode" -> featureGamemode;
            case "fly" -> featureFly;
            case "god" -> featureGod;
            case "speed" -> featureSpeed;
            case "heal" -> featureHeal;
            case "feed" -> featureFeed;
            case "repair" -> featureRepair;
            case "clear" -> featureClear;
            case "vanish" -> featureVanish;
            case "invsee" -> featureInvsee;
            case "echest" -> featureEchest;
            case "nick" -> featureNick;
            case "afk" -> featureAfk;
            case "list" -> featureList;
            case "ptime" -> featurePtime;
            case "pweather" -> featurePweather;
            case "broadcast" -> featureBroadcast;
            case "rules" -> featureRules;
            case "motd" -> featureMotd;
            case "suicide" -> featureSuicide;
            case "hat" -> featureHat;
            case "staff" -> featureStaff;
            default -> true;
        };
    }
}
