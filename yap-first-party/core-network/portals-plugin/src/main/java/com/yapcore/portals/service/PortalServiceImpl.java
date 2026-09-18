package com.yapcore.portals.service;

import com.yapcore.portals.Portal;
import com.yapcore.portals.PortalCuboid;
import com.yapcore.portals.PortalService;
import com.yapcore.portals.PortalTransfer;
import com.yapcore.portals.PortalsConfig;
import com.yapcore.portals.store.PortalArrivalPending;
import com.yapcore.portals.store.PortalYamlStore;
import com.yapcore.portals.store.SelectionDrafts;
import com.yapcore.sched.YapSched;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** SYNC: lookups / transfer queue on entity thread. HEAVY: YAML save/load via async. */
public final class PortalServiceImpl implements PortalService, PortalTransfer {

    private final JavaPlugin plugin;
    private final PortalsConfig config;
    private final PortalYamlStore store;
    private final PortalArrivalPending arrivals;
    private final PortalVisuals visuals;
    private final SelectionDrafts drafts = new SelectionDrafts();
    private final PortalCooldown cooldown = new PortalCooldown();
    /** Last portal name the player stood in (boundary fire). */
    private final Map<UUID, String> inside = new ConcurrentHashMap<>();
    /**
     * Soft-switch / join often restores the player next to a pad (lobby logout at the
     * creative portal). Suppress transfer until they leave the volume or grace ends.
     */
    private final Map<UUID, Long> joinGraceUntilMs = new ConcurrentHashMap<>();
    /** ~3s — covers arrival teleport (25 ticks) plus a beat to stand still. */
    private static final long JOIN_GRACE_MS = 3_000L;

    public PortalServiceImpl(JavaPlugin plugin, PortalsConfig config, PortalYamlStore store,
                             PortalArrivalPending arrivals) {
        this.plugin = plugin;
        this.config = config;
        this.store = store;
        this.arrivals = arrivals;
        this.visuals = new PortalVisuals(plugin);
    }

    public PortalVisuals visuals() {
        return visuals;
    }

    public SelectionDrafts drafts() {
        return drafts;
    }

    public PortalCooldown cooldown() {
        return cooldown;
    }

    public void clearPlayerState(UUID uuid) {
        cooldown.clear(uuid);
        inside.remove(uuid);
        joinGraceUntilMs.remove(uuid);
        drafts.clear(uuid);
    }

    public Map<UUID, String> insideTracker() {
        return inside;
    }

    /** Call on join / after arrival teleport so pads at the restore point do not bounce. */
    public void armJoinGrace(UUID uuid) {
        if (uuid == null) {
            return;
        }
        joinGraceUntilMs.put(uuid, System.currentTimeMillis() + JOIN_GRACE_MS);
    }

    public boolean inJoinGrace(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        Long until = joinGraceUntilMs.get(uuid);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until) {
            joinGraceUntilMs.remove(uuid);
            return false;
        }
        return true;
    }

    /**
     * If the player already stands in / against a portal, mark them inside without
     * transferring — they must walk out and back in to fire Connect.
     */
    public void seedInsideFromLocation(Player player) {
        if (player == null) {
            return;
        }
        Optional<Portal> at = at(player.getLocation());
        if (at.isPresent()) {
            inside.put(player.getUniqueId(), at.get().name());
        } else {
            inside.remove(player.getUniqueId());
        }
    }

    @Override
    public Collection<Portal> list() {
        return store.all();
    }

    @Override
    public Optional<Portal> get(String name) {
        return store.get(name);
    }

    @Override
    public Optional<Portal> at(Location location) {
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        return store.findAt(
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
    }

    @Override
    public Portal define(String name, String world, PortalCuboid cuboid, String targetServer) {
        return define(name, world, cuboid, targetServer, config.defaultColor());
    }

    public Portal define(String name, String world, PortalCuboid cuboid, String targetServer, String color) {
        String id = name.trim().toLowerCase(Locale.ROOT);
        Portal portal = new Portal(
                id,
                world,
                cuboid,
                targetServer.trim(),
                config.defaultPermission(),
                config.defaultCooldownSeconds(),
                true,
                "",
                color);
        store.put(portal);
        visuals.fill(portal);
        persistAsync();
        return portal;
    }

    @Override
    public boolean remove(String name) {
        Optional<Portal> existing = store.get(name);
        boolean ok = store.remove(name);
        if (ok) {
            existing.ifPresent(visuals::clear);
            persistAsync();
        }
        return ok;
    }

    @Override
    public void save(Portal portal) {
        store.put(portal);
        if (portal.enabled()) {
            visuals.fill(portal);
        } else {
            visuals.clear(portal);
        }
        persistAsync();
    }

    @Override
    public void reload() {
        store.load();
        cooldown.clearAll();
        inside.clear();
        visuals.applyAll(store.all());
    }

    @Override
    public boolean transfer(Player player, String targetServer) {
        if (player == null || targetServer == null || targetServer.isBlank()) {
            return false;
        }
        return queueConnect(player, targetServer.trim(), config.defaultCooldownSeconds(), null);
    }

    @Override
    public boolean transfer(Player player, Portal portal) {
        if (player == null || portal == null || !portal.enabled()) {
            return false;
        }
        if (!canUse(player, portal)) {
            player.sendMessage(config.msgDenied());
            return false;
        }
        String custom = portal.enterMessage();
        return queueConnect(player, portal.targetServer(), portal.cooldownSeconds(),
                custom == null || custom.isBlank() ? null : custom);
    }

    public boolean canUse(Player player, Portal portal) {
        if (!player.hasPermission("yapportals.use")
                && !player.hasPermission("yapportals.bypass.permission")) {
            return false;
        }
        String extra = portal.permission();
        if (extra != null && !extra.isBlank()
                && !player.hasPermission(extra)
                && !player.hasPermission("yapportals.bypass.permission")) {
            return false;
        }
        return true;
    }

    private boolean queueConnect(Player player, String targetServer, int cooldownSec, String customMsg) {
        if (!config.enabled()) {
            return false;
        }
        String local = config.serverId();
        if (local != null && local.equalsIgnoreCase(targetServer)) {
            player.sendMessage(config.msgAlreadyHere().replace("{server}", targetServer));
            return false;
        }
        long now = System.currentTimeMillis();
        if (!player.hasPermission("yapportals.bypass.cooldown")
                && !cooldown.ready(player.getUniqueId(), now)) {
            int rem = cooldown.remainingSeconds(player.getUniqueId(), now);
            player.sendMessage(config.msgCooldown().replace("{seconds}", String.valueOf(rem)));
            return false;
        }
        final String message = customMsg != null
                ? customMsg.replace("{server}", targetServer)
                : config.msgTransferring().replace("{server}", targetServer);
        YapSched.entity(plugin, player, () -> {
            try {
                releaseSessionLockForTransfer(player);
                if (arrivals != null) {
                    arrivals.mark(player.getUniqueId(), targetServer);
                }
                byte[] payload = LinkConnect.connectPayload(targetServer);
                // Paper remaps BungeeCord → bungeecord:main; send both for proxy compat.
                try {
                    player.sendPluginMessage(plugin, LinkConnect.CHANNEL_LEGACY, payload);
                } catch (IllegalArgumentException ignored) {
                    // channel may be unregistered on some Paper builds
                }
                try {
                    player.sendPluginMessage(plugin, LinkConnect.CHANNEL_MODERN, payload);
                } catch (IllegalArgumentException ignored) {
                    // optional modern id
                }
                String configured = config.connectChannel();
                if (configured != null
                        && !configured.equals(LinkConnect.CHANNEL_LEGACY)
                        && !configured.equals(LinkConnect.CHANNEL_MODERN)) {
                    player.sendPluginMessage(plugin, configured, payload);
                }
                player.sendMessage(message);
                plugin.getLogger().info("Connect queued " + player.getName() + " → " + targetServer);
                try {
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.1f);
                } catch (Exception ignored) {
                    // sound optional
                }
                cooldown.mark(player.getUniqueId(), cooldownSec, System.currentTimeMillis());
            } catch (IOException e) {
                player.sendMessage(config.msgNoProxy());
                plugin.getLogger().log(Level.WARNING, "Connect encode failed", e);
            } catch (IllegalArgumentException e) {
                player.sendMessage(config.msgNoProxy());
                plugin.getLogger().warning("sendPluginMessage: " + e.getMessage());
            }
        });
        return true;
    }

    /** Drop dual-login lock before Connect so hub/survival soft-switch is not rejected. */
    private void releaseSessionLockForTransfer(Player player) {
        try {
            Class<?> provider = Class.forName("com.yapcore.playerdata.PlayerDataServiceProvider");
            Object opt = provider.getMethod("find").invoke(null);
            if (!(opt instanceof Optional<?> optional) || optional.isEmpty()) {
                return;
            }
            Object service = optional.get();
            String serverId = (String) service.getClass().getMethod("serverId").invoke(service);
            service.getClass()
                    .getMethod("releaseSessionLock", UUID.class, String.class)
                    .invoke(service, player.getUniqueId(), serverId);
        } catch (ClassNotFoundException ignored) {
            // YaPPlayerData optional
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "releaseSessionLock before Connect", e);
        }
    }

    private void persistAsync() {
        YapSched.async(plugin, () -> {
            try {
                store.saveAll();
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save portals.yml", e);
            }
        });
    }
}
