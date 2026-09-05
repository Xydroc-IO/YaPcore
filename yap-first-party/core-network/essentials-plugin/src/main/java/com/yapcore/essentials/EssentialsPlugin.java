package com.yapcore.essentials;

import com.yapcore.essentials.cmd.EssentialsCommands;
import com.yapcore.essentials.db.EssentialsDatabase;
import com.yapcore.essentials.listener.FreezeListener;
import com.yapcore.essentials.listener.TeleportListener;
import com.yapcore.essentials.listener.VanishListener;
import com.yapcore.essentials.listener.WaterWavesListener;
import com.yapcore.essentials.store.AfkService;
import com.yapcore.essentials.store.BackStore;
import com.yapcore.essentials.store.SpawnStore;
import com.yapcore.essentials.store.StaffService;
import com.yapcore.essentials.store.TpaService;
import com.yapcore.essentials.store.VanishService;
import com.yapcore.essentials.water.WaterWaves;
import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class EssentialsPlugin extends JavaPlugin {

    private EssentialsConfig config;
    private EssentialsDatabase database;
    private SpawnStore spawnStore;
    private BackStore back;
    private TpaService tpa;
    private AfkService afk;
    private VanishService vanish;
    private StaffService staff;
    private WaterWaves waterWaves;
    private YapTask waterWavesTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadEssentials();

        EssentialsCommands commands = new EssentialsCommands(
                this, config, spawnStore, back, tpa, afk, vanish, staff);
        String[] names = {
                "spawn", "setspawn", "back", "tpa", "tpahere", "tpaccept", "tpdeny",
                "gm", "gmc", "gms", "gma", "gmsp", "i",
                "fly", "god", "speed", "heal", "feed", "repair", "clear", "vanish",
                "invsee", "echest", "nick", "afk", "list", "ptime", "pweather", "weather",
                "near", "workbench", "disposal",
                "broadcast", "rules", "motd", "suicide", "hat", "tp", "tphere",
                "freeze", "check", "yapess"
        };
        for (String name : names) {
            PluginCommand cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(commands);
                cmd.setTabCompleter(commands);
            }
        }

        var pm = getServer().getPluginManager();
        pm.registerEvents(new TeleportListener(back), this);
        pm.registerEvents(new VanishListener(vanish), this);
        if (config.feature("staff")) {
            pm.registerEvents(new FreezeListener(staff), this);
        }
        startWaterWaves();

        getLogger().info("YaPEssentials ready (server-id=" + config.serverId()
                + ", spawn-scope=" + config.spawnScopeKey()
                + ", water-waves=" + config.waterWavesEnabled() + ").");
    }

    @Override
    public void onDisable() {
        if (waterWavesTask != null) {
            waterWavesTask.cancel();
            waterWavesTask = null;
        }
        if (database != null) {
            database.close();
        }
    }

    public EssentialsConfig essentialsConfig() {
        return config;
    }

    public WaterWaves waterWaves() {
        return waterWaves;
    }

    public void reloadEssentials() {
        config = new EssentialsConfig(this);
        config.reload();

        if (database == null) {
            database = new EssentialsDatabase(this, config);
            try {
                database.open();
            } catch (Exception e) {
                getLogger().warning("Database unavailable — spawn falls back to config.yml only: "
                        + e.getMessage());
            }
        }

        if (spawnStore == null) {
            spawnStore = new SpawnStore(this, config, database);
        }
        spawnStore.load();

        if (back == null) {
            back = new BackStore();
        }
        if (tpa == null) {
            tpa = new TpaService(this);
        }
        if (afk == null) {
            afk = new AfkService();
        }
        if (vanish == null) {
            vanish = new VanishService(this);
        }
        if (staff == null) {
            staff = new StaffService();
        }
        if (waterWaves != null) {
            waterWaves = new WaterWaves(this, this::essentialsConfig);
        }
    }

    private void startWaterWaves() {
        waterWaves = new WaterWaves(this, this::essentialsConfig);
        getServer().getPluginManager().registerEvents(new WaterWavesListener(this), this);
        waterWavesTask = YapSched.globalTimer(this, () -> {
            if (waterWaves == null || !config.waterWavesEnabled()) {
                return;
            }
            waterWaves.tick(getServer().getOnlinePlayers());
        }, 10L, 2L);
    }
}
