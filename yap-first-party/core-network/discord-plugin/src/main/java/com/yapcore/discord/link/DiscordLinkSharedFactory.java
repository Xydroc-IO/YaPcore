package com.yapcore.discord.link;

import com.yapcore.db.YapDb;
import com.yapcore.db.YapDbBootstrap;
import com.yapcore.db.YapDbEngine;
import com.yapcore.db.YapSqlDialect;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * YaPDB-backed link store. Only loaded when YaPDB is present (compileOnly {@code yap-db-api}).
 */
final class DiscordLinkSharedFactory {

    private DiscordLinkSharedFactory() {
    }

    static SharedStore open(JavaPlugin plugin) throws SQLException {
        Optional<YapDb> sharedOpt = YapDbBootstrap.openSharedOrEmpty(
                YapDbBootstrap.Settings.of(
                        "YaPDiscord-links",
                        "jdbc:mysql://127.0.0.1/yap",
                        "",
                        "",
                        2,
                        1,
                        5_000L,
                        true),
                msg -> plugin.getLogger().info(msg));
        if (sharedOpt.isEmpty()) {
            throw new SQLException("YaPDB service not available");
        }
        YapDb shared = sharedOpt.get();
        YapSqlDialect dialect = shared.dialect();
        migrate(shared, dialect);
        plugin.getLogger().info("YaPDiscord links using shared YaPDB pool");
        return new SharedStore(shared, dialect);
    }

    private static void migrate(YapDb shared, YapSqlDialect dialect) throws SQLException {
        String bool = dialect.booleanType();
        String boolTrue = dialect.engine() == YapDbEngine.POSTGRES ? "TRUE" : "1";
        try (Connection c = shared.connection(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_discord_links (
                      mc_uuid CHAR(36) NOT NULL PRIMARY KEY,
                      discord_id VARCHAR(32) NOT NULL,
                      linked_at BIGINT NOT NULL,
                      verified %s NOT NULL DEFAULT %s
                    )
                    """.formatted(bool, boolTrue));
            try {
                String sql = dialect.engine() == YapDbEngine.MYSQL
                        ? "CREATE UNIQUE INDEX idx_yap_discord_links_discord ON yap_discord_links (discord_id)"
                        : "CREATE UNIQUE INDEX IF NOT EXISTS idx_yap_discord_links_discord ON yap_discord_links (discord_id)";
                st.execute(sql);
            } catch (SQLException ignored) {
                // already exists
            }
        }
    }

    static final class SharedStore implements DiscordLinkConnections, AutoCloseable {
        private final YapDb shared;
        private final String upsertSql;

        SharedStore(YapDb shared, YapSqlDialect dialect) {
            this.shared = shared;
            this.upsertSql = dialect.upsert(
                    "yap_discord_links",
                    List.of("mc_uuid"),
                    List.of("mc_uuid", "discord_id", "linked_at", "verified"),
                    Map.of(
                            "discord_id", "EXCLUDED.discord_id",
                            "linked_at", "EXCLUDED.linked_at",
                            "verified", "EXCLUDED.verified"));
        }

        @Override
        public Connection connection() throws SQLException {
            return shared.connection();
        }

        @Override
        public String upsertSql() {
            return upsertSql;
        }

        DiscordLinkRepository repository() {
            return new SqlDiscordLinkRepository(this);
        }

        @Override
        public void close() {
            // pool owned by YaPDB
        }
    }
}
