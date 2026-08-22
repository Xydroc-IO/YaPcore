package com.yapcore.crafting;

import com.yapcore.crafting.cmd.RecipeCommand;
import com.yapcore.crafting.cmd.SellCommand;
import com.yapcore.crafting.cmd.YCraftCommand;
import com.yapcore.crafting.economy.SellPriceRegistry;
import com.yapcore.crafting.gear.GearTierRegistry;
import com.yapcore.crafting.listener.AnvilStationListener;
import com.yapcore.crafting.listener.CraftingTableListener;
import com.yapcore.crafting.listener.FurnaceStationListener;
import com.yapcore.crafting.recipe.RecipePackLoader;
import com.yapcore.crafting.recipe.RecipeRegistry;
import com.yapcore.crafting.service.CraftingServiceImpl;
import com.yapcore.crafting.service.RecipeExecutor;
import com.yapcore.crafting.service.RecipeUnlockListener;
import com.yapcore.mmo.CraftingService;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

public final class CraftingPlugin extends JavaPlugin {

    private CraftingConfig config;
    private RecipePackLoader loader;
    private RecipeRegistry registry;
    private GearTierRegistry gearTiers;
    private SellPriceRegistry sellPrices;
    private RecipeExecutor executor;
    private CraftingServiceImpl craftingService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadCrafting();

        if (!config.enabled()) {
            getLogger().info("YaPCrafting disabled via config.");
            return;
        }

        getServer().getPluginManager().registerEvents(
                new FurnaceStationListener(this, registry, executor), this);
        getServer().getPluginManager().registerEvents(
                new AnvilStationListener(registry, executor), this);
        getServer().getPluginManager().registerEvents(
                new CraftingTableListener(registry, executor), this);
        getServer().getPluginManager().registerEvents(
                new RecipeUnlockListener(this, config, registry), this);

        bindCommand("recipe", new RecipeCommand(craftingService));
        bindCommand("sell", new SellCommand(config, sellPrices));
        bindCommand("ycraft", new YCraftCommand(this));

        getServer().getServicesManager().register(
                CraftingService.class, craftingService, this, ServicePriority.Normal);

        getLogger().info("YaPCrafting ready — recipes=" + recipeCount());
    }

    @Override
    public void onDisable() {
        if (craftingService != null) {
            getServer().getServicesManager().unregister(CraftingService.class, craftingService);
        }
    }

    public void reloadCrafting() {
        if (config == null) {
            config = new CraftingConfig(this);
        }
        config.reload();
        ensureDataFiles();

        Path recipesDir = getDataFolder().toPath().resolve(config.recipesDirectory());
        loader = new RecipePackLoader(recipesDir);
        loader.reload();
        registry = new RecipeRegistry(loader.recipes());

        gearTiers = new GearTierRegistry(loadGearTiers());
        sellPrices = new SellPriceRegistry(this, getDataFolder().toPath().resolve(config.sellPricesFile()));
        sellPrices.ensureDefaultFile();
        sellPrices.reload();

        executor = new RecipeExecutor(this, config, gearTiers);
        var sm = getServer().getServicesManager();
        if (craftingService != null) {
            sm.unregister(CraftingService.class, craftingService);
        }
        craftingService = new CraftingServiceImpl(registry);
    }

    public int recipeCount() {
        return registry == null ? 0 : registry.all().size();
    }

    private void ensureDataFiles() {
        Path recipesDir = getDataFolder().toPath().resolve(config.recipesDirectory());
        try {
            Files.createDirectories(recipesDir);
            copyResourceIfMissing(recipesDir.resolve("smithing.yml"), "recipes/smithing.yml");
            copyResourceIfMissing(recipesDir.resolve("cooking.yml"), "recipes/cooking.yml");
            copyResourceIfMissing(recipesDir.resolve("crafting.yml"), "recipes/crafting.yml");
            copyResourceIfMissing(getDataFolder().toPath().resolve(config.gearTiersFile()), "gear-tiers.yml");
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Could not prepare crafting data files", e);
        }
    }

    private void copyResourceIfMissing(Path target, String resource) throws IOException {
        if (Files.exists(target)) {
            return;
        }
        Files.createDirectories(target.getParent());
        saveResource(resource, false);
        Path saved = getDataFolder().toPath().resolve(resource);
        if (!saved.equals(target) && Files.exists(saved)) {
            Files.move(saved, target);
        }
    }

    private Map<String, GearTierRegistry.GearTier> loadGearTiers() {
        Path path = getDataFolder().toPath().resolve(config.gearTiersFile());
        if (!Files.exists(path)) {
            return Map.of();
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
        ConfigurationSection root = yaml.getConfigurationSection("tiers");
        if (root == null) {
            return Map.of();
        }
        Map<String, GearTierRegistry.GearTier> tiers = new HashMap<>();
        for (String tierId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(tierId);
            if (section == null) {
                continue;
            }
            tiers.put(tierId.toLowerCase(Locale.ROOT), new GearTierRegistry.GearTier(
                    tierId.toLowerCase(Locale.ROOT),
                    section.getInt("attack-bonus", 0),
                    section.getInt("strength-bonus", 0),
                    section.getInt("defence-bonus", 0),
                    section.getString("display-prefix")));
        }
        return Map.copyOf(tiers);
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
