package com.yapcore.items;

import com.yapcore.items.ability.AbilityEngine;
import com.yapcore.items.ability.CooldownService;
import com.yapcore.items.api.ItemService;
import com.yapcore.items.cmd.ItemsCommand;
import com.yapcore.items.combat.ItemsCombatService;
import com.yapcore.items.furniture.FurnitureService;
import com.yapcore.items.gui.ItemsGui;
import com.yapcore.items.gui.ItemsGuiListener;
import com.yapcore.items.item.ItemFactory;
import com.yapcore.items.item.ItemRegistry;
import com.yapcore.items.item.ItemWriter;
import com.yapcore.items.item.RainbowNameService;
import com.yapcore.items.item.RecipeRegistrar;
import com.yapcore.items.listener.ItemsListener;
import com.yapcore.mmo.CombatService;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public final class ItemsPlugin extends JavaPlugin {

    private ItemsConfig config;
    private ItemsKeys keys;
    private ItemRegistry registry;
    private ItemFactory factory;
    private ItemWriter writer;
    private CooldownService cooldowns;
    private AbilityEngine abilities;
    private FurnitureService furniture;
    private RecipeRegistrar recipes;
    private ItemsGui gui;
    private ItemsCombatService combatService;
    private ItemServiceImpl itemService;
    private ItemsPermissions permissions;
    private RainbowNameService rainbowNames;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ensureDefaultItems();
        this.config = new ItemsConfig(getConfig());
        this.keys = new ItemsKeys(this);
        this.registry = new ItemRegistry(this, config);
        this.factory = new ItemFactory(this, keys, registry);
        this.writer = new ItemWriter(this);
        this.cooldowns = new CooldownService();
        this.abilities = new AbilityEngine(this, config, factory, cooldowns);
        this.furniture = new FurnitureService(this, config, keys, factory);
        this.recipes = new RecipeRegistrar(this, factory, registry);
        this.gui = new ItemsGui(this);
        this.itemService = new ItemServiceImpl(this);
        this.permissions = new ItemsPermissions(this);
        this.rainbowNames = new RainbowNameService(this, keys, registry);

        registry.reload();
        permissions.sync(registry);
        furniture.load();
        recipes.registerAll();
        rainbowNames.start();

        getServer().getServicesManager().register(ItemService.class, itemService, this, ServicePriority.Normal);
        if (config.registerCombatService()) {
            combatService = new ItemsCombatService(registry, factory);
            getServer().getServicesManager().register(CombatService.class, combatService, this, ServicePriority.Normal);
        }

        ItemsCommand cmd = new ItemsCommand(this);
        var yapitems = getCommand("yapitems");
        if (yapitems != null) {
            yapitems.setExecutor(cmd);
            yapitems.setTabCompleter(cmd);
        }

        getServer().getPluginManager().registerEvents(new ItemsListener(this, abilities, furniture), this);
        getServer().getPluginManager().registerEvents(new ItemsGuiListener(this), this);

        getLogger().info("YaPItems enabled — " + registry.size() + " items");
    }

    @Override
    public void onDisable() {
        if (rainbowNames != null) {
            rainbowNames.stop();
        }
        if (permissions != null) {
            permissions.clear();
        }
        if (furniture != null) {
            furniture.shutdown();
        }
        if (recipes != null) {
            recipes.unregisterAll();
        }
        getServer().getServicesManager().unregisterAll(this);
    }

    public void reloadAll() {
        reloadConfig();
        this.config = new ItemsConfig(getConfig());
        registry.reload();
        permissions.sync(registry);
        recipes.registerAll();
        furniture.load();
    }

    private void ensureDefaultItems() {
        File examples = new File(getDataFolder(), "items/examples.yml");
        if (!examples.isFile()) {
            saveResource("items/examples.yml", false);
        }
        File rainbow = new File(getDataFolder(), "items/rainbow.yml");
        if (!rainbow.isFile()) {
            saveResource("items/rainbow.yml", false);
        }
        File custom = new File(getDataFolder(), "items/custom");
        if (!custom.isDirectory()) {
            custom.mkdirs();
        }
        // Mirror defaults for product installs
        File defaultsRoot = new File(getDataFolder().getParentFile().getParentFile(), "config/defaults/plugins/YaPItems");
        // no-op if missing; jar resources are enough
        try {
            Files.createDirectories(custom.toPath());
        } catch (IOException ignored) {
        }
    }

    public ItemsConfig config() {
        return config;
    }

    public ItemsKeys keys() {
        return keys;
    }

    public ItemRegistry registry() {
        return registry;
    }

    public ItemFactory factory() {
        return factory;
    }

    public ItemWriter writer() {
        return writer;
    }

    public CooldownService cooldowns() {
        return cooldowns;
    }

    public AbilityEngine abilities() {
        return abilities;
    }

    public FurnitureService furniture() {
        return furniture;
    }

    public ItemsGui gui() {
        return gui;
    }
}
