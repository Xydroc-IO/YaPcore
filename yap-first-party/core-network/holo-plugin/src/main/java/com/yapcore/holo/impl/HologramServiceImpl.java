package com.yapcore.holo.impl;

import com.yapcore.holo.HoloConfig;
import com.yapcore.holo.Hologram;
import com.yapcore.holo.HologramService;
import com.yapcore.holo.nms.NmsHologramPackets;
import com.yapcore.lib.PacketService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class HologramServiceImpl implements HologramService, Listener {

    private final JavaPlugin plugin;
    private final HoloConfig config;
    private final PacketService packets;
    private final HologramRuntime runtime;
    private final HologramStore store;
    private final HologramTracker tracker;
    private final HologramAnimations animations;
    private final Map<String, HologramImpl> holograms = new ConcurrentHashMap<>();
    private HologramClickListener clicks;

    public HologramServiceImpl(JavaPlugin plugin, HoloConfig config, PacketService packets,
                               NmsHologramPackets nms) {
        this.plugin = plugin;
        this.config = config;
        this.packets = packets;
        this.animations = new HologramAnimations(plugin);
        this.runtime = new HologramRuntime(plugin, packets, nms, new HologramPlaceholders(config.placeholders()),
                animations, config.lineSpacing());
        this.store = new HologramStore(plugin);
        this.tracker = new HologramTracker(plugin, animations);
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        clicks = new HologramClickListener(plugin, this, config.clickCooldownTicks());
        packets.addListener(plugin, clicks);
        if (config.hologramsEnabled() && config.hologramsPersist()) {
            loadStored();
        }
        tracker.start(config.refreshTicks(), holograms.values());
    }

    public void shutdown() {
        tracker.stop();
        if (clicks != null) {
            packets.removeListener(clicks);
        }
        store.save(holograms.values(), config.hologramsPersist());
        for (HologramImpl holo : holograms.values()) {
            holo.despawn();
        }
        holograms.clear();
    }

    @Override
    public Hologram create(String id, Location location, List<String> lines) {
        return create(id, location, lines, null);
    }

    public Hologram create(String id, Location location, List<String> lines, String worldName) {
        String key = normalize(id);
        HologramImpl existing = holograms.remove(key);
        if (existing != null) {
            existing.despawn();
        }
        HologramImpl created = new HologramImpl(key, location, lines, config.viewDistance(), runtime);
        created.applyWorldName(worldName);
        holograms.put(key, created);
        store.save(holograms.values(), config.hologramsPersist());
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            created.refresh(player);
        }
        return created;
    }

    @Override
    public Optional<Hologram> get(String id) {
        return Optional.ofNullable(holograms.get(normalize(id)));
    }

    @Override
    public boolean delete(String id) {
        HologramImpl removed = holograms.remove(normalize(id));
        if (removed == null) {
            return false;
        }
        removed.despawn();
        store.save(holograms.values(), config.hologramsPersist());
        return true;
    }

    @Override
    public Collection<Hologram> all() {
        return List.copyOf(holograms.values());
    }

    @Override
    public void save() {
        store.save(holograms.values(), true);
    }

    @Override
    public void reload() {
        animations.reload(plugin);
        runtime.placeholders.setEnabled(config.placeholders());
        for (HologramImpl holo : holograms.values()) {
            holo.despawn();
        }
        holograms.clear();
        if (config.hologramsEnabled()) {
            loadStored();
        }
        tracker.start(config.refreshTicks(), holograms.values());
    }

    @Override
    public boolean enabled() {
        return config.hologramsEnabled() && runtime.nms.ready();
    }

    public Hologram byEntityId(int entityId) {
        for (HologramImpl holo : holograms.values()) {
            if (holo.matchesEntityId(entityId)) {
                return holo;
            }
        }
        return null;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        for (HologramImpl holo : holograms.values()) {
            holo.forget(event.getPlayer().getUniqueId());
        }
    }

    public boolean nmsReady() {
        return runtime.nms.ready();
    }

    public boolean textDisplay() {
        return runtime.nms.textDisplay();
    }

    private void loadStored() {
        for (HologramStore.Stored stored : store.load()) {
            List<String> first = stored.pages().isEmpty() ? List.of("&f") : stored.pages().get(0);
            HologramImpl holo = new HologramImpl(stored.id(), stored.resolved(), first,
                    stored.viewDistance(), runtime);
            holo.applyWorldName(stored.worldName());
            if (stored.pages().size() > 1) {
                holo.setPages(stored.pages());
            }
            holo.attach(stored.attach());
            holo.setClicks(stored.clicks());
            holo.setSeePermission(stored.seePermission());
            holograms.put(stored.id(), holo);
        }
    }

    private static String normalize(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("hologram id");
        }
        return id.toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
