package com.yapcore.db.dialect;

import com.yapcore.db.YapDbEngine;

import java.util.List;
import java.util.Map;

public final class SqliteDialect extends AbstractYapSqlDialect {

    public static final SqliteDialect INSTANCE = new SqliteDialect();

    private SqliteDialect() {}

    @Override
    public YapDbEngine engine() {
        return YapDbEngine.SQLITE;
    }

    @Override
    public String autoIncrementPk() {
        return "INTEGER PRIMARY KEY AUTOINCREMENT";
    }

    @Override
    public String booleanType() {
        return "INTEGER";
    }

    @Override
    public String blobType() {
        return "BLOB";
    }

    @Override
    public String longTextType() {
        return "TEXT";
    }

    @Override
    public String timestampTouchColumn(String columnName) {
        return columnName + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP";
    }

    @Override
    public String nowFn() {
        return "CURRENT_TIMESTAMP";
    }

    @Override
    protected String excludedRef(String column) {
        return "excluded." + column;
    }

    @Override
    public String upsert(String table, List<String> conflictCols, List<String> insertCols, Map<String, String> setClauses) {
        return buildInsertValues(table, insertCols)
                + " ON CONFLICT (" + joinCols(conflictCols) + ") DO UPDATE SET "
                + buildSetClause(setClauses);
    }

    @Override
    public String insertIgnore(String table, List<String> cols) {
        return "INSERT OR IGNORE INTO " + table + " (" + joinCols(cols) + ") VALUES ("
                + placeholders(cols.size()) + ")";
    }
}
