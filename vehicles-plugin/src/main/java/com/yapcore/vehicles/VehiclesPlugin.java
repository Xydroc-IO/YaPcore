package com.yapcore.vehicles;

import com.yapcore.vehicles.api.VehicleAPI;
import com.yapcore.vehicles.builtin.BuiltinTypes;
import com.yapcore.vehicles.command.VehiclesCommand;
import com.yapcore.vehicles.compat.VehicleCompatBridge;
import com.yapcore.vehicles.compat.VehicleCompatListener;
import com.yapcore.vehicles.engine.VehicleKeys;
import com.yapcore.vehicles.engine.VehicleListener;
import com.yapcore.vehicles.engine.VehicleServiceImpl;
import com.yapcore.vehicles.engine.VehiclesConfig;
import com.yapcore.vehicles.upgrades.BuiltinUpgrades;
import com.yapcore.vehicles.upgrades.UpgradeService;
import com.yapcore.vehicles.upgrades.UpgradeShop;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * YaP Vehicles — real vehicle mechanics API for Paper plugins (not minecarts/boats).
 */
public final class VehiclesPlugin extends JavaPlugin {

    private VehiclesConfig config;
    private VehicleKeys keys;
    private VehicleServiceImpl api;
    private VehicleCompatBridge compat;
    private UpgradeService upgrades;
    private BukkitTask tickTask;

    @Override
    public void onEnable() {
        config = new VehiclesConfig(this);
        config.reload();
        keys = new VehicleKeys(this);
        api = new VehicleServiceImpl(this, keys);

        BuiltinTypes.register(api, config);

        upgrades = new UpgradeService(api, keys, config, getLogger());
        api.setUpgrades(upgrades);
        UpgradeShop shop = new UpgradeShop(upgrades, api, config);
        upgrades.setShopUi(shop);
        if (config.upgradesEnabled()) {
            BuiltinUpgrades.registerAll(upgrades);
            Bukkit.getPluginManager().registerEvents(shop, this);
        }

        compat = new VehicleCompatBridge(api, keys, config, getLogger());
        api.setCompat(compat);

        Bukkit.getServicesManager().register(VehicleAPI.class, api, this, ServicePriority.Normal);
        Bukkit.getPluginManager().registerEvents(new VehicleListener(api, upgrades), this);
        if (config.compatEnabled()) {
            Bukkit.getPluginManager().registerEvents(new VehicleCompatListener(compat, keys, api), this);
            getLogger().info("Vehicle compat layer ON — foreign minecart/boat → YaP chassis");
        }

        VehiclesCommand cmd = new VehiclesCommand(api);
        var pluginCommand = getCommand("yapvehicle");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(cmd);
            pluginCommand.setTabCompleter(cmd);
        }

        startTicker();
        getLogger().info("YaP Vehicles online — types=" + api.getTypes().size()
                + " upgrades=" + (config.upgradesEnabled() ? upgrades.getAll().size() : 0)
                + " fuel=" + config.fuelItem());
        getLogger().info("Pack: resourcepacks/yap-vehicles (upgrades 77101+ · HD bodies 77200+)");
    }

    @Override
    public void onDisable() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (upgrades != null) {
            upgrades.clearRecipes();
        }
        if (api != null) {
            api.destroyAll();
        }
        Bukkit.getServicesManager().unregisterAll(this);
    }

    public void reloadVehicles() {
        config.reload();
        if (tickTask != null) {
            tickTask.cancel();
        }
        startTicker();
    }

    private void startTicker() {
        long period = config.tickPeriod();
        tickTask = Bukkit.getScheduler().runTaskTimer(this, () -> api.tickAll(), 1L, period);
    }

    public VehiclesConfig config() {
        return config;
    }

    public VehicleAPI api() {
        return api;
    }

    public VehicleCompatBridge compat() {
        return compat;
    }

    public UpgradeService upgrades() {
        return upgrades;
    }
}
