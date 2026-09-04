package com.yapcore.playerdata.db;

import com.yapcore.playerdata.PlayerDataConfig;
import com.yapcore.playerdata.sync.ItemSerializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.util.Map.entry;

/**
 * Account (balance/lock) + inventory profile CRUD.
 */
public final class PlayerRepository {

    private final Database database;
    private final PlayerDataConfig config;

    public PlayerRepository(Database database, PlayerDataConfig config) {
        this.database = database;
        this.config = config;
    }

    public Optional<PlayerRecord> find(UUID uuid, String profile) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT p.uuid, p.name, p.balance, p.lock_server, p.lock_until,
                            pr.xp, pr.level, pr.health, pr.food, pr.saturation,
                            pr.inventory, pr.enderchest, pr.profile
                     FROM players p
                     LEFT JOIN player_profiles pr ON pr.uuid = p.uuid AND pr.profile = ?
                     WHERE p.uuid = ?
                     """)) {
            ps.setString(1, profile);
            ps.setString(2, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                PlayerRecord r = new PlayerRecord(uuid);
                r.setProfile(profile);
                r.setName(rs.getString("name"));
                r.setBalance(rs.getDouble("balance"));
                r.setLockServer(rs.getString("lock_server"));
                r.setLockUntil(rs.getTimestamp("lock_until"));
                if (rs.getBytes("inventory") != null) {
                    r.setXp(rs.getInt("xp"));
                    r.setLevel(rs.getInt("level"));
                    r.setHealth(rs.getDouble("health"));
                    r.setFood(rs.getInt("food"));
                    r.setSaturation(rs.getFloat("saturation"));
                    r.setInventory(rs.getBytes("inventory"));
                    r.setEnderchest(rs.getBytes("enderchest"));
                }
                return Optional.of(r);
            }
        }
    }

    /** @deprecated use {@link #find(UUID, String)} */
    public Optional<PlayerRecord> find(UUID uuid) throws SQLException {
        return find(uuid, config.inventoryProfile());
    }

    public PlayerRecord ensure(UUID uuid, String name) throws SQLException {
        return ensure(uuid, name, config.inventoryProfile());
    }

    public PlayerRecord ensure(UUID uuid, String name, String profile) throws SQLException {
        ensureAccount(uuid, name);
        ensureProfileRow(uuid, profile);
        return find(uuid, profile).orElseThrow(() -> new SQLException("Missing after ensure " + uuid));
    }

    private void ensureAccount(UUID uuid, String name) throws SQLException {
        String sql = database.dialect().insertIgnore(
                "players",
                List.of("uuid", "name", "balance", "xp", "level", "health", "food", "saturation",
                        "inventory", "enderchest"));
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, truncateName(name));
            ps.setDouble(3, config.startingBalance());
            ps.setInt(4, 0);
            ps.setInt(5, 0);
            ps.setDouble(6, 20);
            ps.setInt(7, 20);
            ps.setFloat(8, 5);
            ps.setBytes(9, ItemSerializer.empty(41));
            ps.setBytes(10, ItemSerializer.empty(27));
            ps.executeUpdate();
        }
        if (name != null) {
            try (Connection c = database.connection();
                 PreparedStatement ps = c.prepareStatement("UPDATE players SET name = ? WHERE uuid = ?")) {
                ps.setString(1, truncateName(name));
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            }
        }
    }

    private void ensureProfileRow(UUID uuid, String profile) throws SQLException {
        String sql = database.dialect().insertIgnore(
                "player_profiles",
                List.of("uuid", "profile", "xp", "level", "health", "food", "saturation",
                        "inventory", "enderchest"));
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, profile);
            ps.setInt(3, 0);
            ps.setInt(4, 0);
            ps.setDouble(5, 20);
            ps.setInt(6, 20);
            ps.setFloat(7, 5);
            ps.setBytes(8, ItemSerializer.empty(41));
            ps.setBytes(9, ItemSerializer.empty(27));
            ps.executeUpdate();
        }
    }

    public void saveProfile(PlayerRecord record) throws SQLException {
        String profile = record.profile() != null ? record.profile() : config.inventoryProfile();
        try (Connection c = database.connection()) {
            try (PreparedStatement bal = c.prepareStatement(
                    "UPDATE players SET name = ?, balance = ? WHERE uuid = ?")) {
                bal.setString(1, truncateName(record.name()));
                bal.setDouble(2, record.balance());
                bal.setString(3, record.uuid().toString());
                bal.executeUpdate();
            }
            String sql = database.dialect().upsert(
                    "player_profiles",
                    List.of("uuid", "profile"),
                    List.of("uuid", "profile", "xp", "level", "health", "food", "saturation",
                            "inventory", "enderchest"),
                    Map.ofEntries(
                            entry("xp", "EXCLUDED.xp"),
                            entry("level", "EXCLUDED.level"),
                            entry("health", "EXCLUDED.health"),
                            entry("food", "EXCLUDED.food"),
                            entry("saturation", "EXCLUDED.saturation"),
                            entry("inventory", "EXCLUDED.inventory"),
                            entry("enderchest", "EXCLUDED.enderchest")));
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, record.uuid().toString());
                ps.setString(2, profile);
                ps.setInt(3, record.xp());
                ps.setInt(4, record.level());
                ps.setDouble(5, record.health());
                ps.setInt(6, record.food());
                ps.setFloat(7, record.saturation());
                ps.setBytes(8, record.inventory() != null ? record.inventory() : ItemSerializer.empty(41));
                ps.setBytes(9, record.enderchest() != null ? record.enderchest() : ItemSerializer.empty(27));
                ps.executeUpdate();
            }
        }
    }

    public void saveBalance(UUID uuid, double balance) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("UPDATE players SET balance = ? WHERE uuid = ?")) {
            ps.setDouble(1, balance);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        }
    }

    public boolean acquireLock(UUID uuid, String serverId, Instant now, Instant until) throws SQLException {
        Timestamp nowTs = Timestamp.from(now);
        Timestamp untilTs = Timestamp.from(until);
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     UPDATE players SET lock_server = ?, lock_until = ?
                     WHERE uuid = ?
                       AND (lock_server IS NULL OR lock_until IS NULL OR lock_until < ? OR lock_server = ?)
                     """)) {
            ps.setString(1, serverId);
            ps.setTimestamp(2, untilTs);
            ps.setString(3, uuid.toString());
            ps.setTimestamp(4, nowTs);
            ps.setString(5, serverId);
            return ps.executeUpdate() == 1;
        }
    }

    public void refreshLock(UUID uuid, String serverId, Instant until) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE players SET lock_until = ? WHERE uuid = ? AND lock_server = ?")) {
            ps.setTimestamp(1, Timestamp.from(until));
            ps.setString(2, uuid.toString());
            ps.setString(3, serverId);
            ps.executeUpdate();
        }
    }

    public void releaseLock(UUID uuid, String serverId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE players SET lock_server = NULL, lock_until = NULL WHERE uuid = ? AND lock_server = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, serverId);
            ps.executeUpdate();
        }
    }

    public void forceReleaseLock(UUID uuid) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE players SET lock_server = NULL, lock_until = NULL WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }

    /** Read lock holder without profile join (pre-login). */
    public Optional<String> lockHolder(UUID uuid) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT lock_server, lock_until FROM players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                String server = rs.getString("lock_server");
                Timestamp until = rs.getTimestamp("lock_until");
                if (server == null || until == null) {
                    return Optional.empty();
                }
                if (until.toInstant().isBefore(Instant.now())) {
                    return Optional.empty();
                }
                return Optional.of(server);
            }
        }
    }

    public long getPlayMinutes(UUID uuid) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT play_minutes FROM players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return 0L;
                }
                return rs.getLong("play_minutes");
            }
        }
    }

    /** Adds session minutes and returns the new lifetime total. */
    public long addPlayMinutes(UUID uuid, long minutes) throws SQLException {
        if (minutes <= 0) {
            return getPlayMinutes(uuid);
        }
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     UPDATE players SET play_minutes = play_minutes + ? WHERE uuid = ?
                     """)) {
            ps.setLong(1, minutes);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        }
        return getPlayMinutes(uuid);
    }

    private static String truncateName(String name) {
        if (name == null || name.isEmpty()) {
            return "unknown";
        }
        return name.length() <= 16 ? name : name.substring(0, 16);
    }
}
