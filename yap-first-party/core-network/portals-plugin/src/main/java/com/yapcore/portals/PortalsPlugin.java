package com.yapcore.portals;

import com.yapcore.portals.cmd.PortalCommands;
import com.yapcore.portals.enddoor.EndDoorListener;
import com.yapcore.portals.enddoor.EndDoorStructure;
import com.yapcore.portals.enddoor.EndDoorTags;
import com.yapcore.portals.listener.PortalArrivalListener;
import com.yapcore.portals.listener.PortalMoveListener;
import com.yapcore.portals.listener.PortalPhysicsListener;
import com.yapcore.portals.listener.PortalQuitListener;
import com.yapcore.portals.listener.PortalWandListener;
import com.yapcore.portals.service.LinkConnect;
import com.yapcore.portals.service.PortalServiceImpl;
import com.yapcore.portals.store.PortalArrivalPending;
import com.yapcore.portals.store.PortalCatalogMirror;
import com.yapcore.portals.store.PortalYamlStore;
import com.yapcore.sched.YapSched;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Fleet walk-through portals for YaP-Folia backends.
 * <p>
 * Thread map: {@code PlayerMoveEvent} / wand interact on the owning region thread;
 * {@code sendPluginMessage(Connect)} on entity scheduler; YAML persist on async scheduler.
 * YaP Link {@code server-selector} receives Connect and routes to the target backend.
 */
public final class PortalsPlugin extends JavaPlugin {

    private PortalsConfig config;
    private PortalServiceImpl service;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = new PortalsConfig(this);
        PortalCatalogMirror mirror = new PortalCatalogMirror(
                getDataFolder().toPath(), config.serverId(), getLogger());
        PortalYamlStore store = new PortalYamlStore(this, mirror);
        store.load();
        PortalArrivalPending arrivals = new PortalArrivalPending(mirror, getLogger());
        service = new PortalServiceImpl(this, config, store, arrivals);

        getServer().getMessenger().registerOutgoingPluginChannel(this, LinkConnect.CHANNEL_LEGACY);
        getServer().getMessenger().registerOutgoingPluginChannel(this, LinkConnect.CHANNEL_MODERN);
        // Touch Connect encoder at enable so a broken jar fails here, not on first walk-in.
        try {
            LinkConnect.connectPayload("_boot");
        } catch (Exception e) {
            getLogger().log(java.util.logging.Level.SEVERE, "LinkConnect unavailable — transfers will fail", e);
        }

        getServer().getServicesManager().register(PortalService.class, service, this, ServicePriority.Normal);
        getServer().getServicesManager().register(PortalTransfer.class, service, this, ServicePriority.Normal);

        PortalWandListener wandListener = new PortalWandListener(this, service);
        getServer().getPluginManager().registerEvents(new PortalMoveListener(config, service), this);
        getServer().getPluginManager().registerEvents(new PortalPhysicsListener(service), this);
        getServer().getPluginManager().registerEvents(wandListener, this);
        getServer().getPluginManager().registerEvents(new PortalQuitListener(service), this);
        getServer().getPluginManager().registerEvents(
                new PortalArrivalListener(this, config, arrivals, service), this);

        if (config.endDoorsEnabled()) {
            EndDoorStructure endStructure = new EndDoorStructure(
                    config.endDoorFrame(),
                    config.endDoorInterior(),
                    config.endDoorWidth(),
                    config.endDoorHeight());
            EndDoorTags endTags = new EndDoorTags(this);
            getServer().getPluginManager().registerEvents(
                    new EndDoorListener(this, config, endStructure, endTags, service.cooldown()), this);
            getLogger().info("End doors enabled — frame=" + config.endDoorFrame()
                    + " " + config.endDoorWidth() + "x" + config.endDoorHeight()
                    + " → " + config.endDoorWorld());
        }

        PortalCommands commands = new PortalCommands(this, service, wandListener);
        var cmd = getCommand("portal");
        if (cmd != null) {
            cmd.setExecutor(commands);
            cmd.setTabCompleter(commands);
        }

        // Worlds may still be loading — paint after a short delay, then keep cinematic FX alive.
        YapSched.globalLater(this, () -> service.visuals().applyAll(service.list()), 40L);
        YapSched.globalTimer(this, () -> service.visuals().tickParticles(service.list()), 20L, 5L);

        getLogger().info("YaPPortals ready — server-id=" + config.serverId()
                + " portals=" + service.list().size()
                + " enabled=" + config.enabled());
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        if (service != null) {
            try {
                // best-effort sync save on disable
                // store already flushed async on each mutate; no extra work required
            } catch (Exception ignored) {
                // shutdown path
            }
        }
    }

    public PortalsConfig portalsConfig() {
        return config;
    }

    public PortalServiceImpl portalService() {
        return service;
    }

    /** Reload config.yml + portals.yml (admin command). */
    public void reloadAll() {
        reloadConfig();
        config.reload(getConfig());
        if (service != null) {
            service.reload();
        }
    }
}
