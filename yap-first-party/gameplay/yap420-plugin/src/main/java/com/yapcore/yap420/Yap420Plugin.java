package com.yapcore.yap420;

import com.yapcore.sched.YapSched;
import com.yapcore.yap420.channel.HazeChannel;
import com.yapcore.yap420.cmd.Yap420Command;
import com.yapcore.yap420.cure.CureTicker;
import com.yapcore.yap420.cure.RackDisplayService;
import com.yapcore.yap420.cure.RackListener;
import com.yapcore.yap420.cure.RackRegistry;
import com.yapcore.yap420.item.ItemBridge;
import com.yapcore.yap420.market.DealerGui;
import com.yapcore.yap420.market.DealerListener;
import com.yapcore.yap420.market.DealerService;
import com.yapcore.yap420.market.EconomyBridge;
import com.yapcore.yap420.market.PackService;
import com.yapcore.yap420.persist.PlotStore;
import com.yapcore.yap420.persist.RackStore;
import com.yapcore.yap420.plant.BoneMealListener;
import com.yapcore.yap420.plant.FarmlandProtectListener;
import com.yapcore.yap420.plant.GrowthTicker;
import com.yapcore.yap420.plant.HarvestListener;
import com.yapcore.yap420.plant.PlantDisplayService;
import com.yapcore.yap420.plant.PlantListener;
import com.yapcore.yap420.plant.PlotRegistry;
import com.yapcore.yap420.plant.WildSpawnListener;
import com.yapcore.yap420.press.PressDisplayService;
import com.yapcore.yap420.press.PressGui;
import com.yapcore.yap420.press.PressListener;
import com.yapcore.yap420.press.PressRegistry;
import com.yapcore.yap420.press.PressStore;
import com.yapcore.yap420.skill.HerbalismHook;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.logging.Level;

/** Folia-safe YaP420 plant / cure / press / market / haze entry. */
public final class Yap420Plugin extends JavaPlugin {

    private Yap420Config config;
    private Yap420Keys keys;
    private ItemBridge items;
    private PlotRegistry plots;
    private RackRegistry racks;
    private PressRegistry presses;
    private PlantDisplayService plantDisplays;
    private RackDisplayService rackDisplays;
    private PressDisplayService pressDisplays;
    private PlotStore plotStore;
    private RackStore rackStore;
    private PressStore pressStore;
    private GrowthTicker growthTicker;
    private CureTicker cureTicker;
    private HazeChannel haze;
    private HerbalismHook herbalism;
    private PackService packService;
    private DealerService dealerService;
    private DealerGui dealerGui;
    private DealerListener dealerListener;
    private PressGui pressGui;
    private EconomyBridge economy;

    @Override
    public void onEnable() {
        ensureDefaults();
        this.config = Yap420Config.load(this);
        if (!config.enabled()) {
            getLogger().info("YaP420 disabled in config");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.keys = new Yap420Keys(this);
        this.items = new ItemBridge();
        this.plots = new PlotRegistry();
        this.racks = new RackRegistry();
        this.presses = new PressRegistry();
        this.plantDisplays = new PlantDisplayService(this, keys, items, config);
        this.rackDisplays = new RackDisplayService(this, keys, items);
        this.pressDisplays = new PressDisplayService(this, keys);
        this.plotStore = new PlotStore(this, plots);
        this.rackStore = new RackStore(this, racks);
        this.pressStore = new PressStore(this, presses);
        this.haze = new HazeChannel(this, config);
        this.herbalism = new HerbalismHook(this, config);
        this.economy = new EconomyBridge();
        this.packService = new PackService(items, config.market());
        this.dealerService = new DealerService(items, economy, config.market());
        this.dealerGui = new DealerGui(items, dealerService, packService);
        this.dealerListener = new DealerListener(items, dealerService, packService, dealerGui);
        this.pressGui = new PressGui(items, packService);

        plotStore.loadSync();
        rackStore.loadSync();
        pressStore.loadSync();

        getServer().getPluginManager().registerEvents(
                new PlantListener(this, config, items, plots, plantDisplays, plotStore), this);
        getServer().getPluginManager().registerEvents(new FarmlandProtectListener(this), this);
        getServer().getPluginManager().registerEvents(
                new HarvestListener(this, config, keys, items, plots, plantDisplays, plotStore, herbalism), this);
        getServer().getPluginManager().registerEvents(
                new BoneMealListener(this, config, keys, plots, plantDisplays, plotStore), this);
        getServer().getPluginManager().registerEvents(
                new RackListener(this, config, items, racks, rackDisplays, rackStore), this);
        getServer().getPluginManager().registerEvents(
                new PressListener(this, config, items, presses, pressDisplays, pressStore, packService, pressGui), this);
        getServer().getPluginManager().registerEvents(
                new ChunkRespawnListener(this, plots, racks, presses, plantDisplays, rackDisplays, pressDisplays,
                        plotStore, rackStore, pressStore), this);
        getServer().getPluginManager().registerEvents(
                new WildSpawnListener(this, plots, plantDisplays, plotStore), this);
        getServer().getPluginManager().registerEvents(
                new ConsumeHazeListener(items, haze, config), this);
        getServer().getPluginManager().registerEvents(dealerListener, this);

        haze.register();
        var cmd = getCommand("yap420");
        if (cmd != null) {
            Yap420Command executor = new Yap420Command(this, config, items, plots, racks, presses,
                    plantDisplays, rackDisplays, pressDisplays, plotStore, rackStore, pressStore,
                    packService, dealerService, dealerGui, dealerListener);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        this.growthTicker = new GrowthTicker(this, config, plots, plantDisplays, plotStore);
        this.cureTicker = new CureTicker(this, config, racks, rackStore);
        growthTicker.start();
        cureTicker.start();

        YapSched.globalLater(this, () -> {
            // Only ensure loaded chunks — full respawnAll races Folia unloaded regions
            plantDisplays.ensureLoaded(plots, plotStore);
            rackDisplays.respawnAll(racks);
            pressDisplays.respawnAll(presses);
        }, 40L);

        getLogger().info("YaP420 enabled — plots=" + plots.size()
                + " racks=" + racks.size()
                + " presses=" + presses.size()
                + " market=" + (config.market().enabled() ? "on" : "off"));
    }

    @Override
    public void onDisable() {
        if (growthTicker != null) {
            growthTicker.stop();
        }
        if (cureTicker != null) {
            cureTicker.stop();
        }
        if (plotStore != null) {
            plotStore.saveSync();
        }
        if (rackStore != null) {
            rackStore.saveSync();
        }
        if (pressStore != null) {
            pressStore.saveSync();
        }
        if (haze != null) {
            haze.unregister();
        }
    }

    public void reloadAll() {
        reloadConfig();
        this.config = Yap420Config.load(this);
        if (packService != null) {
            packService.setMarket(config.market());
        }
        if (dealerService != null) {
            dealerService.setMarket(config.market());
        }
        if (growthTicker != null) {
            growthTicker.stop();
            growthTicker = new GrowthTicker(this, config, plots, plantDisplays, plotStore);
            growthTicker.start();
        }
        if (cureTicker != null) {
            cureTicker.stop();
            cureTicker = new CureTicker(this, config, racks, rackStore);
            cureTicker.start();
        }
        haze.unregister();
        haze = new HazeChannel(this, config);
        haze.register();
        herbalism = new HerbalismHook(this, config);
        YapSched.globalLater(this, () -> {
            // Re-read plots.yml so admin edits / purge files apply without full restart
            plotStore.loadSync();
            rackStore.loadSync();
            pressStore.loadSync();
            plantDisplays.ensureLoaded(plots, plotStore);
            rackDisplays.respawnAll(racks);
            pressDisplays.respawnAll(presses);
        }, 5L);
    }

    public Yap420Config yapConfig() {
        return config;
    }

    public Yap420Keys keys() {
        return keys;
    }

    public ItemBridge items() {
        return items;
    }

    public PlotRegistry plots() {
        return plots;
    }

    public RackRegistry racks() {
        return racks;
    }

    public PressRegistry presses() {
        return presses;
    }

    public HazeChannel haze() {
        return haze;
    }

    public PackService packService() {
        return packService;
    }

    public DealerService dealerService() {
        return dealerService;
    }

    public DealerGui dealerGui() {
        return dealerGui;
    }

    private void ensureDefaults() {
        if (!getDataFolder().isDirectory() && !getDataFolder().mkdirs()) {
            getLogger().warning("Could not create " + getDataFolder());
        }
        File cfg = new File(getDataFolder(), "config.yml");
        if (!cfg.isFile()) {
            saveResource("config.yml", false);
            seedFromNetworkDefaults(cfg);
        }
    }

    private void seedFromNetworkDefaults(File cfg) {
        File root = getDataFolder().getParentFile();
        if (root == null) {
            return;
        }
        File defaults = new File(root.getParentFile(), "config/defaults/plugins/YaP420/config.yml");
        if (!defaults.isFile()) {
            return;
        }
        try {
            Files.copy(defaults.toPath(), cfg.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Could not seed YaP420 defaults", e);
        }
    }

    public InputStream bundledConfig() {
        return getResource("config.yml");
    }
}
