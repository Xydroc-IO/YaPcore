package com.yapcore.moderation.db;

import com.yapcore.moderation.Punishment;
import com.yapcore.moderation.PunishmentType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ModerationRepository {

    private final ModerationDatabase database;

    public ModerationRepository(ModerationDatabase database) {
        this.database = database;
    }

    public Punishment insert(PunishmentType type, UUID targetUuid, String targetName, UUID actorUuid,
                             String actorName, String reason, String ip, long expiresAt) throws SQLException {
        long now = System.currentTimeMillis();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     """
                             INSERT INTO yap_mod_punishments
                             (type, target_uuid, target_name, actor_uuid, actor_name, reason, ip_address, created_at, expires_at, active)
                             VALUES (?,?,?,?,?,?,?,?,?,?)
                             """,
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, type.name());
            ps.setString(2, targetUuid != null ? targetUuid.toString() : null);
            ps.setString(3, targetName);
            ps.setString(4, actorUuid != null ? actorUuid.toString() : null);
            ps.setString(5, actorName);
            ps.setString(6, reason);
            ps.setString(7, ip);
            ps.setLong(8, now);
            ps.setLong(9, expiresAt);
            ps.setBoolean(10, true);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                long id = keys.next() ? keys.getLong(1) : 0L;
                return new Punishment(id, type, targetUuid, targetName, actorUuid, actorName,
                        reason, ip, now, expiresAt, true);
            }
        }
    }

    public void deactivateType(UUID targetUuid, PunishmentType type) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE yap_mod_punishments SET active=? WHERE target_uuid=? AND type=? AND active=?")) {
            ps.setBoolean(1, false);
            ps.setString(2, targetUuid.toString());
            ps.setString(3, type.name());
            ps.setBoolean(4, true);
            ps.executeUpdate();
        }
    }

    public void deactivateIp(String ip) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE yap_mod_punishments SET active=? WHERE ip_address=? AND type=? AND active=?")) {
            ps.setBoolean(1, false);
            ps.setString(2, ip);
            ps.setString(3, PunishmentType.IP_BAN.name());
            ps.setBoolean(4, true);
            ps.executeUpdate();
        }
    }

    public Optional<Punishment> findActive(UUID targetUuid, PunishmentType type) throws SQLException {
        expireStale();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     """
                             SELECT id, type, target_uuid, target_name, actor_uuid, actor_name, reason, ip_address,
                                    created_at, expires_at, active
                             FROM yap_mod_punishments
                             WHERE target_uuid=? AND type=? AND active=?
                             ORDER BY id DESC LIMIT 1
                             """)) {
            ps.setString(1, targetUuid.toString());
            ps.setString(2, type.name());
            ps.setBoolean(3, true);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(readRow(rs));
            }
        }
    }

    public Optional<Punishment> findActiveIp(String ip) throws SQLException {
        expireStale();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     """
                             SELECT id, type, target_uuid, target_name, actor_uuid, actor_name, reason, ip_address,
                                    created_at, expires_at, active
                             FROM yap_mod_punishments
                             WHERE ip_address=? AND type=? AND active=?
                             ORDER BY id DESC LIMIT 1
                             """)) {
            ps.setString(1, ip);
            ps.setString(2, PunishmentType.IP_BAN.name());
            ps.setBoolean(3, true);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(readRow(rs));
            }
        }
    }

    public List<Punishment> history(UUID targetUuid, int limit) throws SQLException {
        expireStale();
        List<Punishment> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     """
                             SELECT id, type, target_uuid, target_name, actor_uuid, actor_name, reason, ip_address,
                                    created_at, expires_at, active
                             FROM yap_mod_punishments
                             WHERE target_uuid=?
                             ORDER BY id DESC LIMIT ?
                             """)) {
            ps.setString(1, targetUuid.toString());
            ps.setInt(2, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(readRow(rs));
                }
            }
        }
        return out;
    }

    public void expireStale() throws SQLException {
        long now = System.currentTimeMillis();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE yap_mod_punishments SET active=? WHERE active=? AND expires_at > 0 AND expires_at <= ?")) {
            ps.setBoolean(1, false);
            ps.setBoolean(2, true);
            ps.setLong(3, now);
            ps.executeUpdate();
        }
    }

    private static Punishment readRow(ResultSet rs) throws SQLException {
        String targetUuidRaw = rs.getString("target_uuid");
        UUID targetUuid = targetUuidRaw != null ? UUID.fromString(targetUuidRaw) : null;
        String actorUuidRaw = rs.getString("actor_uuid");
        UUID actorUuid = actorUuidRaw != null ? UUID.fromString(actorUuidRaw) : null;
        return new Punishment(
                rs.getLong("id"),
                PunishmentType.valueOf(rs.getString("type")),
                targetUuid,
                rs.getString("target_name"),
                actorUuid,
                rs.getString("actor_name"),
                rs.getString("reason"),
                rs.getString("ip_address"),
                rs.getLong("created_at"),
                rs.getLong("expires_at"),
                rs.getBoolean("active")
        );
    }

    public List<Punishment> listActiveBans(int limit) throws SQLException {
        expireStale();
        List<Punishment> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     """
                             SELECT id, type, target_uuid, target_name, actor_uuid, actor_name, reason, ip_address,
                                    created_at, expires_at, active
                             FROM yap_mod_punishments
                             WHERE active=? AND type IN ('BAN','IP_BAN')
                             ORDER BY id DESC LIMIT ?
                             """)) {
            ps.setBoolean(1, true);
            ps.setInt(2, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(readRow(rs));
                }
            }
        }
        return out;
    }

    public int countWarnings(UUID uuid) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM yap_mod_punishments WHERE target_uuid=? AND type='WARN'")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public void insertKick(UUID target, String targetName, UUID actor, String actorName, String reason)
            throws SQLException {
        insert(PunishmentType.KICK, target, targetName, actor, actorName, reason, null, 0L);
    }
}
