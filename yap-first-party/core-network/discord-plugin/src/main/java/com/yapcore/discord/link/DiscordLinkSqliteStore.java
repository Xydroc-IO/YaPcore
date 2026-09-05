package com.yapcore.discord.link;

import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Level;

/** Local SQLite store under {@code plugins/YaPDiscord/discord-links.db}. */
final class DiscordLinkSqliteStore implements DiscordLinkConnections, AutoCloseable {

    private static final String UPSERT = """
            INSERT INTO yap_discord_links (mc_uuid, discord_id, linked_at, verified)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(mc_uuid) DO UPDATE SET
              discord_id = excluded.discord_id,
              linked_at = excluded.linked_at,
              verified = excluded.verified
            """;

    private final String jdbcUrl;
    private final Properties props;

    private DiscordLinkSqliteStore(String jdbcUrl, Properties props) {
        this.jdbcUrl = jdbcUrl;
        this.props = props;
    }

    static DiscordLinkSqliteStore open(JavaPlugin plugin) throws SQLException {
        Path dbFile = plugin.getDataFolder().toPath().resolve("discord-links.db");
        try {
            Files.createDirectories(dbFile.getParent());
        } catch (Exception e) {
            throw new SQLException("Cannot create Discord link data folder", e);
        }
        String url = "jdbc:sqlite:" + dbFile.toAbsolutePath();
        Properties props = new Properties();
        props.setProperty("busy_timeout", "5000");
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().log(Level.WARNING, "sqlite JDBC missing", e);
        }
        DiscordLinkSqliteStore store = new DiscordLinkSqliteStore(url, props);
        store.migrate();
        plugin.getLogger().info("YaPDiscord links using local SQLite (" + dbFile.getFileName() + ")");
        return store;
    }

    private void migrate() throws SQLException {
        try (Connection c = connection(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_discord_links (
                      mc_uuid TEXT NOT NULL PRIMARY KEY,
                      discord_id TEXT NOT NULL,
                      linked_at INTEGER NOT NULL,
                      verified INTEGER NOT NULL DEFAULT 1
                    )
                    """);
            try {
                st.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_yap_discord_links_discord ON yap_discord_links (discord_id)");
            } catch (SQLException ignored) {
                // already exists
            }
        }
    }

    @Override
    public Connection connection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, props);
    }

    @Override
    public String upsertSql() {
        return UPSERT;
    }

    DiscordLinkRepository repository() {
        return new SqlDiscordLinkRepository(this);
    }

    @Override
    public void close() {
        // connections are per-call
    }
}
