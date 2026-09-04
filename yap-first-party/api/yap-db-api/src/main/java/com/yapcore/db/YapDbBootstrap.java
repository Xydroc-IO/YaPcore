package com.yapcore.db;

import com.zaxxer.hikari.HikariConfig;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Shared open path for first-party plugins: prefer YaPDB service, else configure an
 * embedded Hikari pool. Callers own {@link HikariConfig} / {@link com.zaxxer.hikari.HikariDataSource}
 * construction so plugin shade/relocate of Hikari keeps working.
 */
public final class YapDbBootstrap {

    private YapDbBootstrap() {
    }

    /**
     * Pool settings copied from plugin config. YAML key names stay plugin-local.
     *
     * @param richMysqlPrepCache when true, also set prepStmtCacheSize / SqlLimit (playerdata-style)
     */
    public record Settings(
            String poolName,
            String jdbcUrl,
            String username,
            String password,
            int poolMax,
            int poolMinIdle,
            long connectionTimeoutMs,
            boolean preferShared,
            boolean richMysqlPrepCache
    ) {
        public Settings {
            Objects.requireNonNull(poolName, "poolName");
            Objects.requireNonNull(jdbcUrl, "jdbcUrl");
            username = username == null ? "" : username;
            password = password == null ? "" : password;
        }

        public static Settings of(
                String poolName,
                String jdbcUrl,
                String username,
                String password,
                int poolMax,
                int poolMinIdle,
                long connectionTimeoutMs,
                boolean preferShared
        ) {
            return new Settings(
                    poolName, jdbcUrl, username, password,
                    poolMax, poolMinIdle, connectionTimeoutMs, preferShared, false);
        }
    }

    /** Try YaPDB when {@code preferShared}; empty if disabled or service missing. */
    public static Optional<YapDb> findShared(boolean preferShared) {
        if (!preferShared) {
            return Optional.empty();
        }
        return YapDbProvider.find();
    }

    /**
     * Prefer shared YaPDB; otherwise leave caller to build an embedded pool via
     * {@link #configureEmbedded(HikariConfig, Settings)}.
     *
     * @param warn invoked once when shared was requested but unavailable (may be null)
     */
    public static Optional<YapDb> openSharedOrEmpty(Settings settings, Consumer<String> warn) {
        Optional<YapDb> shared = findShared(settings.preferShared());
        if (shared.isPresent()) {
            return shared;
        }
        if (settings.preferShared() && warn != null) {
            warn.accept("use-shared-yapdb=true but YaPDB is not available — using embedded pool");
        }
        return Optional.empty();
    }

    /**
     * Apply dialect-aware embedded pool settings onto a <strong>caller-owned</strong>
     * {@link HikariConfig} (relocate-safe).
     *
     * @return dialect derived from the JDBC URL
     */
    public static YapSqlDialect configureEmbedded(HikariConfig config, Settings settings) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(settings, "settings");
        YapSqlDialect dialect = YapSqlDialects.fromJdbcUrl(settings.jdbcUrl());
        config.setJdbcUrl(settings.jdbcUrl());
        if (dialect.engine() != YapDbEngine.SQLITE) {
            config.setUsername(settings.username());
            config.setPassword(settings.password());
        }
        int max = dialect.preferMaxPoolSize(settings.poolMax());
        int minIdle = dialect.engine() == YapDbEngine.SQLITE ? 1 : settings.poolMinIdle();
        config.setMaximumPoolSize(max);
        config.setMinimumIdle(Math.min(Math.max(1, minIdle), max));
        config.setConnectionTimeout(settings.connectionTimeoutMs());
        config.setPoolName(settings.poolName());
        if (dialect.preferMysqlPrepStmtCache()) {
            config.addDataSourceProperty("cachePrepStmts", "true");
            if (settings.richMysqlPrepCache()) {
                config.addDataSourceProperty("prepStmtCacheSize", "250");
                config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            }
        }
        return dialect;
    }

    /** Logger-backed warn consumer for Bukkit plugins. */
    public static Consumer<String> warnTo(Logger logger) {
        return msg -> logger.warning(msg);
    }
}
