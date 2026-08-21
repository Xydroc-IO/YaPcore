package com.yapcore.compat;

import com.yapcore.api.threading.ThreadPools;
import com.yapcore.bridge.CompatibilityBridge;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.Permissible;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Logger;

public class BridgedPlayer implements Player {

    private static final Logger LOG = Logger.getLogger("YaPcore.Player");

    private final UUID uuid;
    private final String name;
    private String displayName;
    private final BridgedPlayerInventory inventory;
    private final CompatibilityBridge bridge;
    private final World world;
    private volatile Location location;
    private volatile boolean online = true;
    private volatile GameMode gameMode = GameMode.SURVIVAL;
    private volatile Inventory openTop;
    private final Set<PermissionAttachment> attachments = new CopyOnWriteArraySet<>();
    private final ConcurrentHashMap<String, Boolean> effective = new ConcurrentHashMap<>();
    private final long firstPlayed;
    private volatile long lastPlayed;

    public BridgedPlayer(String name, CompatibilityBridge bridge, World world) {
        this.uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes());
        this.name = name;
        this.displayName = name;
        this.bridge = bridge;
        this.world = world;
        this.location = world.getSpawnLocation().clone();
        this.inventory = new BridgedPlayerInventory(name, "PlayerAPI", bridge);
        this.firstPlayed = System.currentTimeMillis();
        this.lastPlayed = firstPlayed;
        effective.put("*", true);
    }

    @Override
    public UUID getUniqueId() {
        return uuid;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public void setDisplayName(String name) {
        this.displayName = name;
    }

    @Override
    public Location getLocation() {
        return location.clone();
    }

    @Override
    public void teleport(Location location) {
        bridge.submitLegacyMutation("PlayerAPI", name + ":teleport",
                ThreadPools.wrap(com.yapcore.api.Pool.SYNC, name + ":teleport",
                        () -> this.location = location.clone()));
    }

    @Override
    public PlayerInventory getInventory() {
        return inventory;
    }

    @Override
    public Inventory getEnderChest() {
        return inventory;
    }

    @Override
    public boolean isOnline() {
        return online;
    }

    @Override
    public boolean isDead() {
        return !online;
    }

    @Override
    public boolean isValid() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
        this.lastPlayed = System.currentTimeMillis();
    }

    @Override
    public boolean hasPlayedBefore() {
        return true;
    }

    @Override
    public long getLastPlayed() {
        return lastPlayed;
    }

    @Override
    public long getFirstPlayed() {
        return firstPlayed;
    }

    @Override
    public void giveExp(int amount) {
        bridge.submitLegacyMutation("PlayerAPI", name + ":exp",
                ThreadPools.wrap(com.yapcore.api.Pool.SYNC, name + ":exp",
                        () -> LOG.info(name + " gained " + amount + " exp (bridged)")));
    }

    @Override
    public void sendTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        LOG.info("[Title→" + name + "] " + title + " / " + subtitle);
    }

    @Override
    public void closeInventory() {
        openTop = null;
    }

    @Override
    public void openInventory(Inventory inventory) {
        Runnable apply = () -> {
            this.openTop = inventory;
            LOG.info(name + " opened inventory '" + inventory.getTitle() + "'");
        };
        if (ThreadPools.isSync()) {
            apply.run();
        } else {
            bridge.submitLegacyMutation("PlayerAPI", name + ":openInv",
                    ThreadPools.wrap(com.yapcore.api.Pool.SYNC, name + ":openInv", apply));
        }
    }

    @Override
    public InventoryView getOpenInventory() {
        Inventory top = openTop != null ? openTop : inventory;
        return new InventoryView() {
            @Override
            public Inventory getTopInventory() {
                return top;
            }

            @Override
            public Inventory getBottomInventory() {
                return inventory;
            }

            @Override
            public Player getPlayer() {
                return BridgedPlayer.this;
            }

            @Override
            public String getTitle() {
                return top.getTitle();
            }

            @Override
            public void close() {
                closeInventory();
            }
        };
    }

    @Override
    public World getWorld() {
        return world;
    }

    @Override
    public void playSound(Location location, Sound sound, float volume, float pitch) {
        LOG.fine("[Sound→" + name + "] " + sound + " v=" + volume);
    }

    @Override
    public void kick(Component message) {
        kickPlayer(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().serialize(message));
    }

    @Override
    public void kickPlayer(String message) {
        LOG.info("Kick " + name + ": " + message);
        setOnline(false);
    }

    @Override
    public GameMode getGameMode() {
        return gameMode;
    }

    @Override
    public void setGameMode(GameMode mode) {
        bridge.submitLegacyMutation("PlayerAPI", name + ":gamemode",
                () -> this.gameMode = mode);
    }

    @Override
    public void sendPluginMessage(Plugin source, String channel, byte[] message) {
        LOG.fine("PluginMessage " + channel + " → " + name + " (" + message.length + "b)");
    }

    @Override
    public void sendMessage(String message) {
        LOG.info("[Chat→" + name + "] " + message);
    }

    @Override
    public void sendMessage(String... messages) {
        for (String m : messages) {
            sendMessage(m);
        }
    }

    @Override
    public boolean isOp() {
        return effective.getOrDefault("yap.op", true);
    }

    @Override
    public boolean isPermissionSet(String perm) {
        return effective.containsKey(perm.toLowerCase());
    }

    @Override
    public boolean hasPermission(String perm) {
        if (effective.getOrDefault("*", false)) {
            return true;
        }
        return effective.getOrDefault(perm.toLowerCase(), false);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin) {
        PermissionAttachment a = new PermissionAttachment(plugin, this);
        attachments.add(a);
        return a;
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) {
        PermissionAttachment a = addAttachment(plugin);
        a.setPermission(name, value);
        return a;
    }

    @Override
    public void removeAttachment(PermissionAttachment attachment) {
        attachments.remove(attachment);
        recalculatePermissions();
    }

    @Override
    public void recalculatePermissions() {
        effective.clear();
        effective.put("*", true);
        for (PermissionAttachment a : attachments) {
            effective.putAll(a.getPermissions());
        }
    }

    @Override
    public Set<PermissionAttachment> getEffectiveAttachments() {
        return Collections.unmodifiableSet(attachments);
    }

    public static final class Registry {
        private final ConcurrentHashMap<String, BridgedPlayer> byName = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<UUID, BridgedPlayer> byUuid = new ConcurrentHashMap<>();
        private final CompatibilityBridge bridge;
        private final World defaultWorld;

        public Registry(CompatibilityBridge bridge, World defaultWorld) {
            this.bridge = bridge;
            this.defaultWorld = defaultWorld;
        }

        public BridgedPlayer join(String name) {
            BridgedPlayer p = byName.computeIfAbsent(name.toLowerCase(),
                    n -> {
                        BridgedPlayer created = new org.bukkit.craftbukkit.entity.CraftPlayer(
                                name, bridge, defaultWorld);
                        byUuid.put(created.getUniqueId(), created);
                        return created;
                    });
            p.setOnline(true);
            return p;
        }

        public BridgedPlayer getOrCreate(String name) {
            return join(name);
        }

        public BridgedPlayer get(String name) {
            return byName.get(name.toLowerCase());
        }

        public BridgedPlayer get(UUID uuid) {
            return byUuid.get(uuid);
        }

        public java.util.Collection<BridgedPlayer> all() {
            return byName.values().stream().filter(BridgedPlayer::isOnline).toList();
        }
    }
}
