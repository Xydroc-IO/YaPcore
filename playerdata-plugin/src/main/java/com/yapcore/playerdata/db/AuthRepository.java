package com.yapcore.playerdata.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Offline password accounts (AuthMe-class). */
public final class AuthRepository {

    public record Account(UUID uuid, String username, String passwordHash,
                          Instant registeredAt, Instant lastLogin, String lastIp) {
    }

    private final Database database;

    public AuthRepository(Database database) {
        this.database = database;
    }

    public Optional<Account> findByUuid(UUID uuid) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT uuid, username, password_hash, registered_at, last_login, last_ip
                     FROM auth_accounts WHERE uuid = ?
                     """)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        }
    }

    public Optional<Account> findByUsername(String username) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT uuid, username, password_hash, registered_at, last_login, last_ip
                     FROM auth_accounts WHERE username = ?
                     """)) {
            ps.setString(1, username.toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        }
    }

    public void create(UUID uuid, String username, String hash, String ip) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO auth_accounts (uuid, username, password_hash, last_ip)
                     VALUES (?, ?, ?, ?)
                     """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, username.toLowerCase(Locale.ROOT));
            ps.setString(3, hash);
            ps.setString(4, ip);
            ps.executeUpdate();
        }
    }

    public void updatePassword(UUID uuid, String hash) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE auth_accounts SET password_hash = ? WHERE uuid = ?")) {
            ps.setString(1, hash);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        }
    }

    public void touchLogin(UUID uuid, String ip) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE auth_accounts SET last_login = ?, last_ip = ?, username = username WHERE uuid = ?")) {
            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            ps.setString(2, ip);
            ps.setString(3, uuid.toString());
            ps.executeUpdate();
        }
    }

    public boolean delete(UUID uuid) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM auth_accounts WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            return ps.executeUpdate() > 0;
        }
    }

    private static Account map(ResultSet rs) throws SQLException {
        Timestamp reg = rs.getTimestamp("registered_at");
        Timestamp login = rs.getTimestamp("last_login");
        return new Account(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("username"),
                rs.getString("password_hash"),
                reg != null ? reg.toInstant() : Instant.EPOCH,
                login != null ? login.toInstant() : null,
                rs.getString("last_ip"));
    }
}
