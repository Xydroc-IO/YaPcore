package com.yapcore.tebex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.logging.Logger;

/**
 * Remembers processed webhook / transaction IDs so Tebex retries do not double-grant.
 */
public final class TebexWebhookDedupe implements AutoCloseable {

    private final Path dbFile;
    private final Logger logger;
    private Connection connection;
    private int retentionDays = 30;

    public TebexWebhookDedupe(Path dataFolder, Logger logger) {
        this.dbFile = dataFolder.resolve("webhook-dedupe.db");
        this.logger = logger;
    }

    public void open(int retentionDays) throws SQLException, IOException {
        this.retentionDays = Math.max(1, retentionDays);
        Files.createDirectories(dbFile.getParent());
        closeQuietly();
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("sqlite JDBC missing", e);
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS seen (
                      key TEXT PRIMARY KEY NOT NULL,
                      kind TEXT NOT NULL,
                      seen_at INTEGER NOT NULL
                    )
                    """);
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_seen_at ON seen(seen_at)");
        }
        purgeExpired();
    }

    /**
     * @return true if this key was newly recorded (first delivery); false if already seen
     */
    public synchronized boolean tryMark(String kind, String key) {
        if (key == null || key.isBlank() || connection == null) {
            return true;
        }
        String k = kind + ":" + key.trim();
        long now = Instant.now().getEpochSecond();
        try {
            try (PreparedStatement check = connection.prepareStatement(
                    "SELECT 1 FROM seen WHERE key = ? LIMIT 1")) {
                check.setString(1, k);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) {
                        return false;
                    }
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO seen(key, kind, seen_at) VALUES (?, ?, ?)")) {
                insert.setString(1, k);
                insert.setString(2, kind == null ? "id" : kind);
                insert.setLong(3, now);
                insert.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            // Unique race → treat as duplicate
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("unique")) {
                return false;
            }
            if (logger != null) {
                logger.warning("Tebex dedupe write failed: " + e.getMessage());
            }
            return true;
        }
    }

    public void purgeExpired() {
        if (connection == null) {
            return;
        }
        long cutoff = Instant.now().getEpochSecond() - (retentionDays * 86_400L);
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM seen WHERE seen_at < ?")) {
            ps.setLong(1, cutoff);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (logger != null) {
                logger.warning("Tebex dedupe purge failed: " + e.getMessage());
            }
        }
    }

    @Override
    public void close() {
        closeQuietly();
    }

    private void closeQuietly() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
            connection = null;
        }
    }
}
