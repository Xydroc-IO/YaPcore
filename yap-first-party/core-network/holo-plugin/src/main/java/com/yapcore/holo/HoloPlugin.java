package com.yapcore.holo;

import com.yapcore.holo.cmd.HologramCommands;
import com.yapcore.holo.impl.HologramServiceImpl;
import com.yapcore.holo.nms.EntityIdAllocator;
import com.yapcore.holo.nms.NmsHologramPackets;
import com.yapcore.holo.nms.NmsLookup;
import com.yapcore.lib.PacketService;
import com.yapcore.lib.PacketServices;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class HoloPlugin extends JavaPlugin {

    private HoloConfig config;
    private HologramServiceImpl holograms;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new java.io.File(getDataFolder(), "animations.yml").isFile()) {
            saveResource("animations.yml", false);
        }
        config = new HoloConfig(this);
        config.reload();

        PacketService packets = PacketServices.packets();
        if (packets == null) {
            getLogger().severe("YaPLib PacketService missing — install yap-lib.jar (depend: YaPLib). Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        NmsLookup nms = new NmsLookup(this);
        NmsHologramPackets holoPackets = new NmsHologramPackets(nms, new EntityIdAllocator(nms),
                config.preferTextDisplay());
        holograms = new HologramServiceImpl(this, config, packets, holoPackets);
        holograms.start();
        getServer().getServicesManager().register(HologramService.class, holograms, this, ServicePriority.Highest);

        PluginCommand cmd = getCommand("yapholo");
        if (cmd != null) {
            HologramCommands commands = new HologramCommands(this, holograms);
            cmd.setExecutor(commands);
            cmd.setTabCompleter(commands);
        }
        getLogger().info("YaPHolo ready — nms="
                + (holoPackets.ready() ? (holoPackets.textDisplay() ? "text_display" : "armor_stand") : "unavailable")
                + " holograms=" + holograms.all().size());
    }

    public void reloadHolo() {
        config.reload();
        if (holograms != null) {
            holograms.reload();
        }
    }

    public HoloConfig holoConfig() {
        return config;
    }

    public HologramServiceImpl holograms() {
        return holograms;
    }

    @Override
    public void onDisable() {
        if (holograms != null) {
            holograms.shutdown();
            getServer().getServicesManager().unregister(HologramService.class, holograms);
        }
    }
}
