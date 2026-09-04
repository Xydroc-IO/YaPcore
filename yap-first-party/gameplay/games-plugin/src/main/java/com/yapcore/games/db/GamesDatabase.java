package com.yapcore.games.db;

import com.yapcore.db.YapDb;
import com.yapcore.db.YapDbBootstrap;
import com.yapcore.db.YapDbEngine;
import com.yapcore.db.YapSqlDialect;
import com.yapcore.db.YapSqlDialects;
import com.yapcore.games.GamesConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class GamesDatabase implements AutoCloseable {

    private final JavaPlugin plugin;
    private final GamesConfig config;
    private HikariDataSource embedded;
    private YapDb shared;
    private boolean usingShared;
    private YapSqlDialect dialect = YapSqlDialects.mysql();

    public GamesDatabase(JavaPlugin plugin, GamesConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public YapSqlDialect dialect() {
        return dialect;
    }

    public void open() throws SQLException {
        String jdbc = config.jdbcUrl() == null ? "" : config.jdbcUrl();
        YapDbBootstrap.Settings settings = new YapDbBootstrap.Settings(
                "YaPGames",
                jdbc.isBlank() ? "jdbc:sqlite::memory:" : jdbc,
                config.jdbcUser(),
                config.jdbcPassword(),
                config.poolMax(),
                config.poolMin(),
                config.poolTimeoutMs(),
                config.useSharedYapdb(),
                true);
        var sharedOpt = YapDbBootstrap.openSharedOrEmpty(settings, YapDbBootstrap.warnTo(plugin.getLogger()));
        if (sharedOpt.isPresent()) {
            shared = sharedOpt.get();
            dialect = shared.dialect();
            usingShared = true;
            migrate();
            plugin.getLogger().info("YaPGames using shared YaPDB pool (" + dialect.engine() + ")");
            return;
        }
        usingShared = false;
        if (jdbc.isBlank()) {
            plugin.getLogger().warning("YaPGames stats disabled — no JDBC URL");
            return;
        }
        HikariConfig hc = new HikariConfig();
        dialect = YapDbBootstrap.configureEmbedded(hc, settings);
        embedded = new HikariDataSource(hc);
        migrate();
    }

    public void migrate() throws SQLException {
        if (!isOpen()) {
            return;
        }
        try (Connection c = connection(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_games_stats (
                      player_uuid CHAR(36) NOT NULL,
                      mode_id VARCHAR(32) NOT NULL,
                      wins INT NOT NULL DEFAULT 0,
                      kills INT NOT NULL DEFAULT 0,
                      deaths INT NOT NULL DEFAULT 0,
                      %s,
                      PRIMARY KEY (player_uuid, mode_id)
                    )
                    """.formatted(dialect.timestampTouchColumn("updated_at")));
            try {
                st.execute("CREATE INDEX IF NOT EXISTS idx_mode_wins ON yap_games_stats (mode_id, wins DESC)");
            } catch (SQLException ignored) {
            }
        }
    }

    public boolean isOpen() {
        if (usingShared) {
            return shared != null && shared.isOpen();
        }
        return embedded != null && !embedded.isClosed();
    }

    public Connection connection() throws SQLException {
        if (usingShared) {
            return shared.connection();
        }
        if (embedded == null) {
            throw new SQLException("YaPGames pool not open");
        }
        return embedded.getConnection();
    }

    @Override
    public void close() {
        if (!usingShared && embedded != null) {
            embedded.close();
            embedded = null;
        }
    }
}
