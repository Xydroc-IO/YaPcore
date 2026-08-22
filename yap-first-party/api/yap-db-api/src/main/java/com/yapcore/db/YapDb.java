package com.yapcore.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Shared MariaDB/MySQL access for YaP plugins.
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
}
