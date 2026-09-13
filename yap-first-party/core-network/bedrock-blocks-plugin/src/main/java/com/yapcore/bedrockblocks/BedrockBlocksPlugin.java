package com.yapcore.bedrockblocks;

import com.yapcore.bedrockblocks.cmd.PortBlockCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class BedrockBlocksPlugin extends JavaPlugin {

    private PortBlockCatalog catalog;
    private PortBlockKeys keys;
    private PortBlockService service;
    private BlocksChannel blocksChannel;

    @Override
    public void onEnable() {
        try {
            catalog = PortBlockCatalog.loadFromClasspath();
        } catch (Exception e) {
            getLogger().severe("Failed to load Bedrock port block catalog: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        keys = new PortBlockKeys();
        service = new PortBlockService(this, catalog, keys);

        getServer().getServicesManager().register(PortBlocksService.class, service, this, ServicePriority.Normal);

        PortBlockCommand command = new PortBlockCommand(service);
        PluginCommand yapblock = getCommand("yapblock");
        if (yapblock != null) {
            yapblock.setExecutor(command);
            yapblock.setTabCompleter(command);
        }

        getServer().getPluginManager().registerEvents(new PortBlockListener(service), this);

        blocksChannel = new BlocksChannel(this);
        blocksChannel.register();

        getLogger().info("YaPBedrockBlocks ready (" + catalog.size() + " catalog ports, yap:blocks channel).");
    }

    @Override
    public void onDisable() {
        if (blocksChannel != null) {
            blocksChannel.unregister();
            blocksChannel = null;
        }
        getServer().getServicesManager().unregisterAll(this);
        service = null;
        catalog = null;
        keys = null;
    }

    public PortBlockService portBlockService() {
        return service;
    }

    public PortBlockCatalog portBlockCatalog() {
        return catalog;
    }

    public BlocksChannel blocksChannel() {
        return blocksChannel;
    }
}
