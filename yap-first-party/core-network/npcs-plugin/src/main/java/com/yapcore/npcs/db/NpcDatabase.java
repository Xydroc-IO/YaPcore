package com.yapcore.npcs.db;

import com.yapcore.db.YapDb;
import com.yapcore.db.YapDbBootstrap;
import com.yapcore.db.YapDbEngine;
import com.yapcore.db.YapSqlDialect;
import com.yapcore.db.YapSqlDialects;
import com.yapcore.npcs.NpcsConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class NpcDatabase implements AutoCloseable {

    private static final String FALLBACK_JDBC =
            "jdbc:mysql://127.0.0.1:3306/yap?useSSL=false&allowPublicKeyRetrieval=true";

    private final JavaPlugin plugin;
    private final NpcsConfig config;
    private HikariDataSource embedded;
    private YapDb shared;
    private boolean usingShared;
    private YapSqlDialect dialect = YapSqlDialects.mysql();

    public NpcDatabase(JavaPlugin plugin, NpcsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public YapSqlDialect dialect() {
        return dialect;
    }

    public void open() throws SQLException {
        YapDbBootstrap.Settings settings = new YapDbBootstrap.Settings(
                "YaPNpcs",
                FALLBACK_JDBC,
                "yap",
                "yap",
                4,
                1,
                30_000L,
                true,
                true);
        var sharedOpt = YapDbBootstrap.openSharedOrEmpty(settings, YapDbBootstrap.warnTo(plugin.getLogger()));
        if (sharedOpt.isPresent()) {
            shared = sharedOpt.get();
            dialect = shared.dialect();
            usingShared = true;
            migrate();
            plugin.getLogger().info("YaPNpcs using shared YaPDB pool (" + dialect.engine() + ")");
            return;
        }
        usingShared = false;
        HikariConfig hc = new HikariConfig();
        dialect = YapDbBootstrap.configureEmbedded(hc, settings);
        embedded = new HikariDataSource(hc);
        migrate();
        plugin.getLogger().warning("YaPNpcs using embedded pool (" + dialect.engine()
                + ") — configure YaPDB for production");
    }

    public void migrate() throws SQLException {
        try (Connection c = connection(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_npcs (
                      id VARCHAR(64) NOT NULL,
                      server_id VARCHAR(64) NOT NULL,
                      display_name VARCHAR(64) NOT NULL,
                      world VARCHAR(64) NOT NULL,
                      x DOUBLE NOT NULL,
                      y DOUBLE NOT NULL,
                      z DOUBLE NOT NULL,
                      yaw FLOAT NOT NULL,
                      entity_uuid CHAR(36) NULL,
                      dialogue TEXT NULL,
                      quest_id VARCHAR(64) NULL,
                      action TEXT NULL,
                      PRIMARY KEY (server_id, id)
                    )
                    """);
            try {
                st.execute("ALTER TABLE yap_npcs ADD COLUMN action TEXT NULL");
            } catch (SQLException ignored) {
                // already present
            }
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_quest_progress (
                      player_uuid CHAR(36) NOT NULL,
                      quest_id VARCHAR(64) NOT NULL,
                      objective_id VARCHAR(64) NOT NULL,
                      progress INT NOT NULL DEFAULT 0,
                      completed %s NOT NULL DEFAULT 0,
                      PRIMARY KEY (player_uuid, quest_id, objective_id)
                    )
                    """.formatted(dialect.booleanType()));
            try {
                st.execute("CREATE INDEX IF NOT EXISTS idx_player ON yap_quest_progress (player_uuid)");
            } catch (SQLException ignored) {
            }
        }
    }

    public Connection connection() throws SQLException {
        if (usingShared) {
            return shared.connection();
        }
        if (embedded == null) {
            throw new SQLException("YaPNpcs pool not open");
        }
        return embedded.getConnection();
    }

    @Override
    public void close() {
        if (embedded != null) {
            embedded.close();
            embedded = null;
        }
    }
}
