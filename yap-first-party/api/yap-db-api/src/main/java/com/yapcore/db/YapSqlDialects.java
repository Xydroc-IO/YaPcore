package com.yapcore.db;

import com.yapcore.db.dialect.MysqlDialect;
import com.yapcore.db.dialect.PostgresDialect;
import com.yapcore.db.dialect.SqliteDialect;

/**
 * Resolves {@link YapSqlDialect} from JDBC URLs or engine names.
 */
public final class YapSqlDialects {

    private YapSqlDialects() {}

    public static YapSqlDialect mysql() {
        return MysqlDialect.INSTANCE;
    }

    public static YapSqlDialect postgres() {
        return PostgresDialect.INSTANCE;
    }

    public static YapSqlDialect sqlite() {
        return SqliteDialect.INSTANCE;
    }

    public static YapSqlDialect of(YapDbEngine engine) {
        return switch (engine) {
            case MYSQL -> mysql();
            case POSTGRES -> postgres();
            case SQLITE -> sqlite();
        };
    }

    /**
     * @param engineOverride {@code auto}, {@code mysql}, {@code mariadb}, {@code postgres}/{@code postgresql},
     *                       {@code sqlite}, or empty/null for URL detection
     */
    public static YapSqlDialect resolve(String jdbcUrl, String engineOverride) {
        YapDbEngine fromOverride = parseEngineName(engineOverride);
        if (fromOverride != null) {
            return of(fromOverride);
        }
        return fromJdbcUrl(jdbcUrl);
    }

    public static YapSqlDialect fromJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return mysql();
        }
        String u = jdbcUrl.trim().toLowerCase();
        if (u.startsWith("jdbc:postgresql:") || u.startsWith("jdbc:pgsql:")) {
            return postgres();
        }
        if (u.startsWith("jdbc:sqlite:")) {
            return sqlite();
        }
        // jdbc:mysql: / jdbc:mariadb: / unknown → MySQL family
        return mysql();
    }

    public static YapDbEngine engineFromJdbcUrl(String jdbcUrl) {
        return fromJdbcUrl(jdbcUrl).engine();
    }

    private static YapDbEngine parseEngineName(String name) {
        if (name == null || name.isBlank() || "auto".equalsIgnoreCase(name.trim())) {
            return null;
        }
        String n = name.trim().toLowerCase();
        return switch (n) {
            case "mysql", "mariadb" -> YapDbEngine.MYSQL;
            case "postgres", "postgresql", "pgsql" -> YapDbEngine.POSTGRES;
            case "sqlite" -> YapDbEngine.SQLITE;
            default -> throw new IllegalArgumentException("Unknown jdbc.engine: " + name);
        };
    }
}
