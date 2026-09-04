package com.yapcore.db;

/**
 * SQL engines supported by YaPDB / first-party plugins.
 */
public enum YapDbEngine {
    MYSQL,
    POSTGRES,
    SQLITE;

    public boolean isMysqlFamily() {
        return this == MYSQL;
    }
}
