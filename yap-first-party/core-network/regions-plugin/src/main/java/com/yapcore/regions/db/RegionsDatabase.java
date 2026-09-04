package com.yapcore.regions.db;

import com.yapcore.db.YapDb;
import com.yapcore.db.YapDbEngine;
import com.yapcore.db.YapDbProvider;
import com.yapcore.db.YapSqlDialect;
import com.yapcore.db.YapSqlDialects;
import com.yapcore.regions.RegionsConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class RegionsDatabase implements AutoCloseable {

    private final JavaPlugin plugin;
    private final RegionsConfig config;
    private HikariDataSource embedded;
    private YapDb shared;
    private boolean usingShared;
    private YapSqlDialect dialect = YapSqlDialects.mysql();

    public RegionsDatabase(JavaPlugin plugin, RegionsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void open() throws SQLException {
        var opt = YapDbProvider.find();
        if (opt.isPresent()) {
            shared = opt.get();
            usingShared = true;
            dialect = shared.dialect();
            migrate();
            plugin.getLogger().info("YaPRegions using shared YaPDB pool");
            return;
        }
        usingShared = false;
        String jdbcUrl = "jdbc:mysql://127.0.0.1:3306/yap?useSSL=false&allowPublicKeyRetrieval=true";
        dialect = YapSqlDialects.fromJdbcUrl(jdbcUrl);
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(jdbcUrl);
        hc.setUsername("yap");
        hc.setPassword("yap");
        hc.setMaximumPoolSize(dialect.preferMaxPoolSize(4));
        hc.setMinimumIdle(1);
        hc.setPoolName("YaPRegions");
        if (dialect.preferMysqlPrepStmtCache()) {
            hc.addDataSourceProperty("cachePrepStmts", "true");
        }
        embedded = new HikariDataSource(hc);
        migrate();
        plugin.getLogger().warning("YaPRegions using embedded pool — configure YaPDB for production");
    }

    private void migrate() throws SQLException {
        try (Connection c = connection(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_admin_regions (
                      id %s,
                      server_id VARCHAR(64) NOT NULL,
                      name VARCHAR(64) NOT NULL,
                      world VARCHAR(64) NOT NULL,
                      min_x INT NOT NULL,
                      max_x INT NOT NULL,
                      min_y INT NOT NULL,
                      max_y INT NOT NULL,
                      min_z INT NOT NULL,
                      max_z INT NOT NULL,
                      UNIQUE (server_id, name)
                    )
                    """.formatted(dialect.autoIncrementPk()));
            createIndex(st, "idx_yap_admin_region_server", "yap_admin_regions", "server_id");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_admin_region_flags (
                      region_id BIGINT NOT NULL,
                      flag_name VARCHAR(32) NOT NULL,
                      flag_value VARCHAR(8) NOT NULL,
                      PRIMARY KEY (region_id, flag_name)
                    )
                    """);
            createIndex(st, "idx_yap_admin_region_flags", "yap_admin_region_flags", "region_id");
        }
    }

    private void createIndex(Statement st, String name, String table, String cols) {
        try {
            String sql = dialect.engine() == YapDbEngine.MYSQL
                    ? "CREATE INDEX " + name + " ON " + table + " (" + cols + ")"
                    : "CREATE INDEX IF NOT EXISTS " + name + " ON " + table + " (" + cols + ")";
            st.execute(sql);
        } catch (SQLException ignored) {
            // already exists
        }
    }

    public YapSqlDialect dialect() {
        return dialect;
    }

    public Connection connection() throws SQLException {
        if (usingShared) {
            return shared.connection();
        }
        if (embedded == null) {
            throw new SQLException("YaPRegions pool not open");
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
