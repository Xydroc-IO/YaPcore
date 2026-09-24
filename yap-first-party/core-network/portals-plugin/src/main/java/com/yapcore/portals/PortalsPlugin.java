package com.yapcore.portals;

import com.yapcore.portals.cmd.PortalCommands;
import com.yapcore.portals.enddoor.EndDoorListener;
import com.yapcore.portals.enddoor.EndDoorRegistry;
import com.yapcore.portals.enddoor.EndDoorRehydrate;
import com.yapcore.portals.enddoor.EndDoorStore;
import com.yapcore.portals.enddoor.EndDoorStructure;
import com.yapcore.portals.enddoor.EndDoorTags;
import com.yapcore.portals.enddoor.EndDoorVisuals;
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
            EndDoorStore endStore = new EndDoorStore(this);
            endStore.load();
            getServer().getPluginManager().registerEvents(
                    new EndDoorListener(this, config, endStructure, endTags, service.cooldown(), service, endStore), this);
            // Chunks already loaded at enable never fire ChunkLoadEvent — scan once after worlds settle
            YapSched.globalLater(this, () -> {
                EndDoorRehydrate.scanLoaded(this, endStructure, endTags, endStore);
                getLogger().info("End door rehydrate scan — registry=" + EndDoorRegistry.all().size()
                        + " persisted=" + endStore.all().size());
            }, 80L);
            // Periodic catch-up: register keystones in loaded chunks that missed ChunkLoad
            YapSched.globalTimer(this, () -> {
                EndDoorRehydrate.scanLoaded(this, endStructure, endTags, endStore);
            }, 20L * 15, 20L * 30);
            // Soft keep-alive: ensure swirl present, do not clear/rebuild every pass
            YapSched.globalTimer(this, () -> {
                for (var frame : EndDoorRegistry.all()) {
                    int midAlong = frame.minAlong() + (frame.sizeAlong() / 2);
                    int midX = frame.axis() == org.bukkit.Axis.X ? midAlong : frame.fixed();
                    int midZ = frame.axis() == org.bukkit.Axis.X ? frame.fixed() : midAlong;
                    YapSched.region(this, frame.world(), midX, midZ,
                            () -> EndDoorVisuals.ensureFace(frame));
                }
            }, 20L * 40, 20L * 40);
            final int[] endPulse = {0};
            YapSched.globalTimer(this, () -> {
                if (EndDoorRegistry.all().isEmpty()) {
                    return;
                }
                float spin = (float) ((endPulse[0]++ % 10) * (Math.PI * 2.0 / 10.0));
                for (var frame : EndDoorRegistry.all()) {
                    int midAlong = frame.minAlong() + (frame.sizeAlong() / 2);
                    int midX = frame.axis() == org.bukkit.Axis.X ? midAlong : frame.fixed();
                    int midZ = frame.axis() == org.bukkit.Axis.X ? frame.fixed() : midAlong;
                    YapSched.region(this, frame.world(), midX, midZ,
                            () -> EndDoorVisuals.spin(frame, spin));
                }
            }, 20L, 5L);
            getLogger().info("End doors enabled — build=" + EndDoorListener.BUILD
                    + " frame=" + config.endDoorFrame()
                    + " " + config.endDoorWidth() + "x" + config.endDoorHeight()
                    + " → " + config.endDoorWorld()
                    + " persisted=" + endStore.all().size());
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

        getLogger().info("YaPPortals ready — build=" + EndDoorListener.BUILD
                + " server-id=" + config.serverId()
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
