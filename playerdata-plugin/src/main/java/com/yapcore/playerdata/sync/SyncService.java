package com.yapcore.playerdata.sync;

import com.yapcore.playerdata.PlayerDataConfig;
import com.yapcore.playerdata.auth.AuthService;
import com.yapcore.playerdata.db.PlayerRecord;
import com.yapcore.playerdata.db.PlayerRepository;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Load / apply / save player profiles; freeze until ready (+ auth); autosave.
 * Session lock prevents double-login across Velocity backends.
 */
public final class SyncService {

    private final JavaPlugin plugin;
    private final PlayerDataConfig config;
    private final PlayerRepository repository;
    private final SessionLock sessionLock;
    private AuthService auth;

    private final Set<UUID> ready = ConcurrentHashMap.newKeySet();
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Double> balances = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerRecord> pendingApply = new ConcurrentHashMap<>();
    private BukkitTask autosaveTask;

    public SyncService(JavaPlugin plugin, PlayerDataConfig config,
                       PlayerRepository repository, SessionLock sessionLock) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.sessionLock = sessionLock;
    }

    public void bindAuth(AuthService auth) {
        this.auth = auth;
    }

    public SessionLock sessionLock() {
        return sessionLock;
    }

    public void startAutosave() {
        long period = config.autosaveSeconds() * 20L;
        autosaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::autosaveAll, period, period);
    }

    public void stopAutosave() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
    }

    public boolean isReady(UUID uuid) {
        return ready.contains(uuid);
    }

    public boolean isLoading(UUID uuid) {
        return loading.contains(uuid);
    }

    public double getBalance(UUID uuid) {
        return balances.getOrDefault(uuid, 0.0);
    }

    public void setBalanceLocal(UUID uuid, double amount) {
        balances.put(uuid, amount);
    }

    public void revokeReady(UUID uuid) {
        ready.remove(uuid);
        pendingApply.remove(uuid);
    }

    /**
     * Async load + session lock. Inventory apply waits for auth when auth is active.
     */
    public void beginJoin(Player player) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        loading.add(uuid);
        ready.remove(uuid);
        pendingApply.remove(uuid);

        if (auth != null && auth.isActive()) {
            player.getInventory().clear();
            player.getEnderChest().clear();
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                repository.ensure(uuid, name);
                Optional<String> holder = sessionLock.tryAcquireOrHolder(uuid);
                if (holder.isPresent()) {
                    String server = holder.get();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            player.kick(net.kyori.adventure.text.Component.text(
                                    "Already logged in on server '" + server
                                            + "'. Wait a few seconds or ask staff to /yapdata unlock."));
                        }
                        loading.remove(uuid);
                    });
                    return;
                }
                PlayerRecord record = repository.find(uuid, config.inventoryProfile())
                        .orElseThrow(() -> new SQLException("Missing row after ensure"));
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        releaseQuiet(uuid);
                        loading.remove(uuid);
                        return;
                    }
                    try {
                        balances.put(uuid, record.balance());
                        boolean waitAuth = auth != null && auth.isActive() && !auth.isAuthenticated(uuid);
                        if (waitAuth) {
                            pendingApply.put(uuid, record);
                            // keep not-ready until /login
                        } else {
                            apply(player, record);
                            ready.add(uuid);
                            player.sendMessage("§7Synced profile §f" + config.inventoryProfile()
                                    + " §7· balance §a$" + String.format("%.2f", record.balance()));
                        }
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.SEVERE, "Failed to apply data for " + name, e);
                        player.kick(net.kyori.adventure.text.Component.text(
                                "Failed to load your synced data. Contact staff."));
                        releaseQuiet(uuid);
                    } finally {
                        loading.remove(uuid);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load data for " + name, e);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.kick(net.kyori.adventure.text.Component.text(
                                "Database unavailable. Try again later."));
                    }
                    loading.remove(uuid);
                });
            }
        });
    }

    /** Called after successful /login or /register. */
    public void completeAfterAuth(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerRecord record = pendingApply.remove(uuid);
        if (record == null) {
            // Data may still be loading — wait briefly
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                PlayerRecord again = pendingApply.remove(uuid);
                if (again != null) {
                    finishApply(player, again);
                } else if (!ready.contains(uuid) && !loading.contains(uuid)) {
                    // reload
                    beginJoin(player);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline() && auth != null && auth.isAuthenticated(uuid)) {
                            PlayerRecord r = pendingApply.remove(uuid);
                            if (r != null) {
                                finishApply(player, r);
                            }
                        }
                    }, 40L);
                }
            }, 10L);
            return;
        }
        finishApply(player, record);
    }

    private void finishApply(Player player, PlayerRecord record) {
        try {
            apply(player, record);
            balances.put(player.getUniqueId(), record.balance());
            ready.add(player.getUniqueId());
            player.sendMessage("§7Synced profile §f" + config.inventoryProfile()
                    + " §7· balance §a$" + String.format("%.2f", record.balance()));
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "apply after auth", e);
            player.kick(net.kyori.adventure.text.Component.text("Failed to apply synced data."));
            releaseQuiet(player.getUniqueId());
        }
    }

    public void handleQuit(Player player) {
        UUID uuid = player.getUniqueId();
        pendingApply.remove(uuid);
        if (!ready.contains(uuid) && !loading.contains(uuid)) {
            // still release lock if we held it while waiting for auth
            releaseQuiet(uuid);
            balances.remove(uuid);
            return;
        }
        PlayerRecord snapshot = ready.contains(uuid) ? snapshot(player) : null;
        ready.remove(uuid);
        loading.remove(uuid);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (snapshot != null) {
                    mergeUnsyncedFields(snapshot);
                    repository.saveProfile(snapshot);
                }
                sessionLock.release(uuid);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save data for " + player.getName(), e);
                try {
                    sessionLock.forceRelease(uuid);
                } catch (SQLException ignored) {
                }
            } finally {
                balances.remove(uuid);
            }
        });
    }

    public void shutdown() {
        stopAutosave();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (!ready.contains(uuid) && !loading.contains(uuid)) {
                releaseQuiet(uuid);
                continue;
            }
            PlayerRecord snap = snapshot(player);
            ready.remove(uuid);
            loading.remove(uuid);
            pendingApply.remove(uuid);
            try {
                if (snap != null) {
                    mergeUnsyncedFields(snap);
                    repository.saveProfile(snap);
                }
                sessionLock.release(uuid);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Shutdown save failed for " + player.getName(), e);
                try {
                    sessionLock.forceRelease(uuid);
                } catch (SQLException ignored) {
                }
            } finally {
                balances.remove(uuid);
            }
        }
    }

    public void saveAllOnlineBlocking() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!ready.contains(player.getUniqueId())) {
                continue;
            }
            PlayerRecord snap = snapshot(player);
            if (snap == null) {
                continue;
            }
            try {
                mergeUnsyncedFields(snap);
                repository.saveProfile(snap);
                sessionLock.refresh(player.getUniqueId());
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Force-save failed for " + player.getName(), e);
            }
        }
    }

    public void saveBalanceAsync(UUID uuid, double balance) {
        balances.put(uuid, balance);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                repository.saveBalance(uuid, balance);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to persist balance for " + uuid, e);
            }
        });
    }

    private void autosaveAll() {
        java.util.List<PlayerRecord> snaps = new java.util.ArrayList<>();
        try {
            Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!ready.contains(player.getUniqueId())) {
                        continue;
                    }
                    PlayerRecord snap = snapshot(player);
                    if (snap != null) {
                        snaps.add(snap);
                    }
                }
                return null;
            }).get();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Autosave snapshot failed", e);
            return;
        }
        for (PlayerRecord snap : snaps) {
            try {
                mergeUnsyncedFields(snap);
                repository.saveProfile(snap);
                sessionLock.refresh(snap.uuid());
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Autosave failed for " + snap.name(), e);
            }
        }
    }

    private void mergeUnsyncedFields(PlayerRecord snap) throws SQLException {
        if (config.syncInventory() && config.syncEnderchest() && config.syncXp() && config.syncVitals()) {
            return;
        }
        PlayerRecord existing = repository.find(snap.uuid(), snap.profile() != null
                ? snap.profile() : config.inventoryProfile()).orElse(null);
        if (existing == null) {
            return;
        }
        if (!config.syncInventory()) {
            snap.setInventory(existing.inventory());
        }
        if (!config.syncEnderchest()) {
            snap.setEnderchest(existing.enderchest());
        }
        if (!config.syncXp()) {
            snap.setXp(existing.xp());
            snap.setLevel(existing.level());
        }
        if (!config.syncVitals()) {
            snap.setHealth(existing.health());
            snap.setFood(existing.food());
            snap.setSaturation(existing.saturation());
        }
        if (!config.syncEconomy()) {
            snap.setBalance(existing.balance());
        }
    }

    private void apply(Player player, PlayerRecord record) {
        if (config.syncInventory()) {
            ItemStack[] inv = ItemSerializer.deserialize(record.inventory(), 41);
            player.getInventory().setContents(pad(inv, 41));
        }
        if (config.syncEnderchest()) {
            ItemStack[] ender = ItemSerializer.deserialize(record.enderchest(), 27);
            player.getEnderChest().setContents(pad(ender, 27));
        }
        if (config.syncXp()) {
            player.setLevel(Math.max(0, record.level()));
            player.setTotalExperience(0);
            player.setExp(0f);
            player.giveExp(Math.max(0, record.xp()));
        }
        if (config.syncVitals()) {
            AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
            double max = maxHealth != null ? maxHealth.getValue() : 20.0;
            player.setHealth(Math.min(Math.max(0.1, record.health()), max));
            player.setFoodLevel(Math.min(20, Math.max(0, record.food())));
            player.setSaturation(Math.max(0f, record.saturation()));
        }
    }

    private PlayerRecord snapshot(Player player) {
        if (!player.isOnline()) {
            return null;
        }
        PlayerRecord r = new PlayerRecord(player.getUniqueId());
        r.setProfile(config.inventoryProfile());
        r.setName(player.getName());
        r.setBalance(balances.getOrDefault(player.getUniqueId(), 0.0));
        if (config.syncXp()) {
            r.setLevel(player.getLevel());
            r.setXp(player.getTotalExperience());
        }
        if (config.syncVitals()) {
            r.setHealth(player.getHealth());
            r.setFood(player.getFoodLevel());
            r.setSaturation(player.getSaturation());
        }
        if (config.syncInventory()) {
            r.setInventory(ItemSerializer.serialize(player.getInventory().getContents()));
        } else {
            r.setInventory(ItemSerializer.empty(41));
        }
        if (config.syncEnderchest()) {
            r.setEnderchest(ItemSerializer.serialize(player.getEnderChest().getContents()));
        } else {
            r.setEnderchest(ItemSerializer.empty(27));
        }
        return r;
    }

    private void releaseQuiet(UUID uuid) {
        try {
            sessionLock.release(uuid);
        } catch (SQLException e) {
            try {
                sessionLock.forceRelease(uuid);
            } catch (SQLException ignored) {
            }
            plugin.getLogger().log(Level.WARNING, "Failed to release lock for " + uuid, e);
        }
    }

    private static ItemStack[] pad(ItemStack[] src, int size) {
        ItemStack[] out = new ItemStack[size];
        if (src != null) {
            System.arraycopy(src, 0, out, 0, Math.min(src.length, size));
        }
        return out;
    }
}
