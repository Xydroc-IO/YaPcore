package com.yapcore.regions.db;

import com.yapcore.db.YapSqlDialect;

import java.sql.Connection;
import java.sql.SQLException;

/** Connection + dialect for admin region persistence (production pool or test SQLite). */
public interface RegionSql {
    Connection connection() throws SQLException;

    YapSqlDialect dialect();
}
