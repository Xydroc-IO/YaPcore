package com.yapcore.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Shared SQL access for YaP plugins (MariaDB/MySQL, PostgreSQL, or SQLite).
 * Provided by the {@code YaPDB} plugin (one Hikari pool per JVM).
 */
public interface YapDb {

    /** Live Hikari-backed data source. */
    DataSource dataSource();

    /** Borrow a connection (caller must close). */
    Connection connection() throws SQLException;

    boolean isOpen();

    /** JDBC URL (may include query params; never includes the password). */
    String jdbcUrl();

    String poolName();

    /** Detected / configured SQL engine for this pool. */
    YapDbEngine engine();

    /** Dialect helpers for portable DDL/DML. */
    YapSqlDialect dialect();
}
