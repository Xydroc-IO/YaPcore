package com.yapcore.playerdata.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JobRepository {
    public record Progress(String job, double xp, int level) {
    }

    private final Database database;

    public JobRepository(Database database) {
        this.database = database;
    }

    public List<Progress> list(UUID uuid) throws SQLException {
        List<Progress> out = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT job, xp, level FROM job_progress WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Progress(rs.getString("job"), rs.getDouble("xp"), rs.getInt("level")));
                }
            }
        }
        return out;
    }

    public Optional<Progress> get(UUID uuid, String job) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT job, xp, level FROM job_progress WHERE uuid = ? AND job = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, job);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Progress(rs.getString("job"), rs.getDouble("xp"), rs.getInt("level")));
            }
        }
    }

    public void join(UUID uuid, String job) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT IGNORE INTO job_progress (uuid, job, xp, level) VALUES (?, ?, 0, 1)
                     """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, job);
            ps.executeUpdate();
        }
    }

    public void leave(UUID uuid, String job) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM job_progress WHERE uuid = ? AND job = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, job);
            ps.executeUpdate();
        }
    }

    public Progress addXp(UUID uuid, String job, double xpGain) throws SQLException {
        Progress cur = get(uuid, job).orElse(new Progress(job, 0, 1));
        double xp = cur.xp() + xpGain;
        int level = cur.level();
        while (xp >= level * 100.0) {
            xp -= level * 100.0;
            level++;
        }
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO job_progress (uuid, job, xp, level) VALUES (?, ?, ?, ?)
                     ON DUPLICATE KEY UPDATE xp = VALUES(xp), level = VALUES(level)
                     """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, job);
            ps.setDouble(3, xp);
            ps.setInt(4, level);
            ps.executeUpdate();
        }
        return new Progress(job, xp, level);
    }
}
