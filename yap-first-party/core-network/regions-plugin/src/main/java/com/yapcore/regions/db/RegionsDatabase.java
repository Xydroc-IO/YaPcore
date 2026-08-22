package com.yapcore.regions.db;

import com.yapcore.db.YapDb;
import com.yapcore.regions.RegionsConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
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

    public RegionsDatabase(JavaPlugin plugin, RegionsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void open() throws SQLException {
        var reg = Bukkit.getServicesManager().getRegistration(YapDb.class);
        if (reg != null && reg.getProvider().isOpen()) {
            shared = reg.getProvider();
            usingShared = true;
            migrate();
            plugin.getLogger().info("YaPRegions using shared YaPDB pool");
            return;
        }
        usingShared = false;
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:mysql://127.0.0.1:3306/yap?useSSL=false&allowPublicKeyRetrieval=true");
        hc.setUsername("yap");
        hc.setPassword("yap");
        hc.setMaximumPoolSize(4);
        hc.setMinimumIdle(1);
        hc.setPoolName("YaPRegions");
        embedded = new HikariDataSource(hc);
        migrate();
        plugin.getLogger().warning("YaPRegions using embedded pool — configure YaPDB for production");
    }

    private void migrate() throws SQLException {
        try (Connection c = connection(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_admin_regions (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      server_id VARCHAR(64) NOT NULL,
                      name VARCHAR(64) NOT NULL,
                      world VARCHAR(64) NOT NULL,
                      min_x INT NOT NULL,
                      max_x INT NOT NULL,
                      min_y INT NOT NULL,
                      max_y INT NOT NULL,
                      min_z INT NOT NULL,
                      max_z INT NOT NULL,
                      UNIQUE KEY uk_server_name (server_id, name),
                      INDEX idx_server (server_id)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_admin_region_flags (
                      region_id BIGINT NOT NULL,
                      flag_name VARCHAR(32) NOT NULL,
                      flag_value VARCHAR(8) NOT NULL,
                      PRIMARY KEY (region_id, flag_name),
                      INDEX idx_region (region_id)
                    )
                    """);
        }
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
