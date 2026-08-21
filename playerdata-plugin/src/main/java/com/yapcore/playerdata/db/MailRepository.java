package com.yapcore.playerdata.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MailRepository {
    public record MailMessage(long id, String fromName, String message, boolean read, java.sql.Timestamp created) {
    }

    private final Database database;

    public MailRepository(Database database) {
        this.database = database;
    }

    public void send(UUID to, String fromName, String message) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO mail (to_uuid, from_name, message) VALUES (?, ?, ?)")) {
            ps.setString(1, to.toString());
            ps.setString(2, fromName.length() > 16 ? fromName.substring(0, 16) : fromName);
            ps.setString(3, message.length() > 512 ? message.substring(0, 512) : message);
            ps.executeUpdate();
        }
    }

    public int unreadCount(UUID uuid) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM mail WHERE to_uuid = ? AND read_flag = 0")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public List<MailMessage> list(UUID uuid, int limit) throws SQLException {
        List<MailMessage> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, from_name, message, read_flag, created_at FROM mail WHERE to_uuid = ? ORDER BY id DESC LIMIT ?")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new MailMessage(
                            rs.getLong("id"),
                            rs.getString("from_name"),
                            rs.getString("message"),
                            rs.getBoolean("read_flag"),
                            rs.getTimestamp("created_at")));
                }
            }
        }
        return out;
    }

    public void markAllRead(UUID uuid) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE mail SET read_flag = 1 WHERE to_uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }

    public void clear(UUID uuid) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM mail WHERE to_uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }
}
