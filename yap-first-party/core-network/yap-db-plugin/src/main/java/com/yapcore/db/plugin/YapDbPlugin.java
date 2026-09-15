package com.yapcore.db.plugin;

import com.yapcore.db.YapDb;
import com.yapcore.db.YapDbEngine;
import com.yapcore.db.YapSqlDialect;
import com.yapcore.db.YapSqlDialects;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/**
 * Owns the single shared Hikari pool for this JVM (MySQL/MariaDB, PostgreSQL, or SQLite).
 */
public final class YapDbPlugin extends JavaPlugin implements YapDb {

    private HikariDataSource dataSource;
    private String jdbcUrl = "";
    private YapSqlDialect dialect = YapSqlDialects.mysql();
    /** Last JDBC {@code DatabaseMetaData} product label, when pool opened. */
    private volatile String productLabel = "";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Exception primaryFailure = null;
        try {
            openPool();
        } catch (Exception e) {
            primaryFailure = e;
            closePool();
            String attempted = getConfig().getString("jdbc.url", "jdbc:mysql://127.0.0.1:3306/yap_playerdata");
            String user = getConfig().getString("jdbc.user", "yap");
            getLogger().severe("Failed to open SQL pool: " + e.getMessage());
            getLogger().severe("Attempted JDBC: " + attempted + " (user=" + user + ")");
            getLogger().severe("Engines: jdbc:mysql://… | jdbc:postgresql://… | jdbc:sqlite:path");
            getLogger().severe("MariaDB: ./scripts/db/ensure-db.sh · Postgres: ./scripts/db/ensure-postgres.sh");
            getLogger().severe("SQLite:  ./scripts/db/configure-db.sh --engine sqlite · Docs: docs/data/YAPDB.md");
            // Never disable YaPDB on pool failure — dependents depend: [YaPDB] and load
            // YapSqlDialects / YapDbBootstrap from this classloader. Disabling cascades into
            // ClassNotFoundException (e.g. YaPPerms) instead of letting them use embedded pools.
            if (dialect.engine() != YapDbEngine.SQLITE && trySqliteFallback()) {
                getLogger().warning("Primary JDBC failed — using local SQLite fallback (" + jdbcUrl + ")");
            } else {
                getLogger().severe("Shared pool unavailable — staying enabled so API classes remain "
                        + "visible; first-party plugins will use embedded pools. Cause: "
                        + primaryFailure.getMessage());
            }
        }

        if (isOpen()) {
            getServer().getServicesManager().register(YapDb.class, this, this, ServicePriority.Normal);
            String product = productLabel.isBlank() ? dialect.engine().name() : productLabel;
            getLogger().info("Shared YapDb pool ready [" + dialect.engine() + "] " + product + " (" + jdbcUrl + ")");
        }
        var cmd = getCommand("yapdb");
        if (cmd != null) {
            YapDbCommand handler = new YapDbCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }
    }

    /**
     * Open {@code plugins/YaPDB/yap-fallback.db} when the configured networked engine fails.
     * Single-node only — operators should fix MariaDB/Postgres for multi-backend fleets.
     */
    private boolean trySqliteFallback() {
        Path file = getDataFolder().toPath().resolve("yap-fallback.db").toAbsolutePath().normalize();
        String url = "jdbc:sqlite:" + file;
        try {
            ensureSqliteParent(url);
            jdbcUrl = url;
            dialect = YapSqlDialects.sqlite();
            HikariConfig hc = new HikariConfig();
            hc.setJdbcUrl(jdbcUrl);
            hc.setMaximumPoolSize(1);
            hc.setMinimumIdle(1);
            hc.setConnectionTimeout(10_000L);
            hc.setPoolName("YaPDB-fallback");
            dataSource = new HikariDataSource(hc);
            try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
                conn.isValid(5);
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA busy_timeout=5000");
                st.execute("PRAGMA foreign_keys=ON");
            }
            return true;
        } catch (Exception e) {
            closePool();
            getLogger().severe("SQLite fallback also failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        closePool();
    }

    public void reloadPool() throws SQLException {
        reloadConfig();
        closePool();
        openPool();
        getServer().getServicesManager().unregister(YapDb.class, this);
        getServer().getServicesManager().register(YapDb.class, this, this, ServicePriority.Normal);
    }

    private void openPool() throws SQLException {
        var c = getConfig();
        jdbcUrl = c.getString("jdbc.url", "jdbc:mysql://127.0.0.1:3306/yap_playerdata");
        String engineOverride = c.getString("jdbc.engine", "auto");
        dialect = YapSqlDialects.resolve(jdbcUrl, engineOverride);

        if (dialect.engine() == YapDbEngine.SQLITE) {
            ensureSqliteParent(jdbcUrl);
        }

        String user = c.getString("jdbc.user", "yap");
        String password = c.getString("jdbc.password", "change-me");
        String poolName = c.getString("pool.name", "YaPDB");
        int max = dialect.preferMaxPoolSize(c.getInt("pool.maximum-pool-size", 16));
        int minIdle = dialect.engine() == YapDbEngine.SQLITE
                ? 1
                : Math.max(0, c.getInt("pool.minimum-idle", 2));
        long timeout = Math.max(1000L, c.getLong("pool.connection-timeout-ms", 10_000L));

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(jdbcUrl);
        if (dialect.engine() != YapDbEngine.SQLITE) {
            hc.setUsername(user);
            hc.setPassword(password);
        }
        hc.setMaximumPoolSize(max);
        hc.setMinimumIdle(Math.min(minIdle, max));
        hc.setConnectionTimeout(timeout);
        hc.setPoolName(poolName);
        if (dialect.preferMysqlPrepStmtCache()) {
            hc.addDataSourceProperty("cachePrepStmts", "true");
            hc.addDataSourceProperty("prepStmtCacheSize", "250");
            hc.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        }
        dataSource = new HikariDataSource(hc);

        try (Connection conn = dataSource.getConnection()) {
            conn.isValid(5);
            productLabel = YapDbProbe.readProductLabel(conn).orElse("");
            YapDbProbe.engineFromProductName(productLabel).ifPresent(detected -> {
                if (detected != dialect.engine()) {
                    getLogger().warning("JDBC product reports " + detected + " but dialect resolved to "
                            + dialect.engine() + " — check jdbc.url / jdbc.engine");
                }
            });
            if (dialect.engine() == YapDbEngine.SQLITE) {
                try (Statement st = conn.createStatement()) {
                    st.execute("PRAGMA journal_mode=WAL");
                    st.execute("PRAGMA busy_timeout=5000");
                    st.execute("PRAGMA foreign_keys=ON");
                }
            }
        }
    }

    /** Package-visible for JDBC URL / path unit tests. */
    static void ensureSqliteParent(String url) throws SQLException {
        String pathPart = url;
        int q = pathPart.indexOf('?');
        if (q >= 0) {
            pathPart = pathPart.substring(0, q);
        }
        String prefix = "jdbc:sqlite:";
        if (!pathPart.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return;
        }
        String file = pathPart.substring(prefix.length());
        if (file.isBlank() || ":memory:".equalsIgnoreCase(file) || file.startsWith("file::memory:")) {
            return;
        }
        try {
            Path p = Path.of(file);
            Path parent = p.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (Exception e) {
            throw new SQLException("Cannot create SQLite parent directory for " + file + ": " + e.getMessage(), e);
        }
    }

    private void closePool() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
        productLabel = "";
    }

    /** JDBC product string from last successful open, or empty. */
    String productLabel() {
        return productLabel;
    }

    /** TCP probe of common DB ports (async-safe; short timeouts). */
    List<YapDbProbe.PortHit> probePorts(String host) {
        return YapDbProbe.probePorts(host, 750);
    }

    Optional<String> readLiveProduct() {
        if (!isOpen()) {
            return Optional.empty();
        }
        try (Connection conn = connection()) {
            return YapDbProbe.readProductLabel(conn);
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    @Override
    public DataSource dataSource() {
        if (!isOpen()) {
            throw new IllegalStateException("YaPDB pool is not open");
        }
        return dataSource;
    }

    @Override
    public Connection connection() throws SQLException {
        if (!isOpen()) {
            throw new SQLException("YaPDB pool is not open");
        }
        return dataSource.getConnection();
    }

    @Override
    public boolean isOpen() {
        return dataSource != null && !dataSource.isClosed();
    }

    @Override
    public String jdbcUrl() {
        return jdbcUrl;
    }

    @Override
    public String poolName() {
        return dataSource != null ? dataSource.getPoolName() : "YaPDB";
    }

    @Override
    public YapDbEngine engine() {
        return dialect.engine();
    }

    @Override
    public YapSqlDialect dialect() {
        return dialect;
    }
}
