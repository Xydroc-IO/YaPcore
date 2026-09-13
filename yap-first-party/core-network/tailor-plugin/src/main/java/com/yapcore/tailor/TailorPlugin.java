package com.yapcore.tailor;

import com.yapcore.tailor.cmd.EmoteCommand;
import com.yapcore.tailor.cmd.SkinCommand;
import com.yapcore.tailor.cmd.WardrobeCommand;
import com.yapcore.tailor.db.TailorDatabase;
import com.yapcore.tailor.gui.WardrobeGui;
import com.yapcore.tailor.papi.TailorPlaceholders;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class TailorPlugin extends JavaPlugin {

    private TailorConfig config;
    private TailorDatabase database;
    private SkinImageService images;
    private TailorServiceImpl service;
    private WardrobeGui wardrobeGui;
    private TailorPlaceholders placeholders;
    private PresenceChannel presenceChannel;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = new TailorConfig(this);
        config.reload();

        database = new TailorDatabase(this, config);
        try {
            database.open();
        } catch (Exception e) {
            getLogger().severe("Failed to open tailor database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        images = new SkinImageService(this, config);
        try {
            images.ensureDirs();
        } catch (TailorException e) {
            getLogger().warning("Skin storage dirs: " + e.getMessage());
        }

        service = new TailorServiceImpl(this, config, database, images);
        wardrobeGui = new WardrobeGui(this, service);
        presenceChannel = new PresenceChannel(this, service);
        presenceChannel.register();
        service.setPresenceChannel(presenceChannel);

        getServer().getServicesManager().register(TailorService.class, service, this, ServicePriority.Normal);

        SkinCommand skinCommand = new SkinCommand(this, service);
        PluginCommand skin = getCommand("skin");
        if (skin != null) {
            skin.setExecutor(skinCommand);
            skin.setTabCompleter(skinCommand);
        }
        WardrobeCommand wardrobeCommand = new WardrobeCommand(this, service, wardrobeGui);
        PluginCommand wardrobe = getCommand("wardrobe");
        if (wardrobe != null) {
            wardrobe.setExecutor(wardrobeCommand);
            wardrobe.setTabCompleter(wardrobeCommand);
        }
        EmoteCommand emoteCommand = new EmoteCommand(this);
        PluginCommand emote = getCommand("emote");
        if (emote != null) {
            emote.setExecutor(emoteCommand);
            emote.setTabCompleter(emoteCommand);
        }

        var pm = getServer().getPluginManager();
        pm.registerEvents(new TailorListener(this, service), this);
        pm.registerEvents(wardrobeGui, this);

        placeholders = new TailorPlaceholders(service, getLogger());
        placeholders.tryRegister();

        getLogger().info("YaPTailor ready (max-slots=" + config.maxSlots()
                + ", cooldown-ms=" + config.cooldownMs()
                + ", default-model=" + config.defaultModel() + ").");
    }

    @Override
    public void onDisable() {
        if (placeholders != null) {
            placeholders.unregisterSafe();
            placeholders = null;
        }
        if (presenceChannel != null) {
            presenceChannel.unregister();
            presenceChannel = null;
        }
        getServer().getServicesManager().unregisterAll(this);
        if (database != null) {
            database.close();
            database = null;
        }
        service = null;
        images = null;
    }

    public PresenceChannel presenceChannel() {
        return presenceChannel;
    }

    public void reloadTailor() {
        config.reload();
        try {
            images.ensureDirs();
        } catch (TailorException e) {
            getLogger().warning(e.getMessage());
        }
    }

    public TailorConfig tailorConfig() {
        return config;
    }

    public TailorServiceImpl tailorService() {
        return service;
    }
}
