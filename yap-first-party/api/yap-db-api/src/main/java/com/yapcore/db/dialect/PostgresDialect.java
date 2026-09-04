package com.yapcore.db.dialect;

import com.yapcore.db.YapDbEngine;

import java.util.List;
import java.util.Map;

public final class PostgresDialect extends AbstractYapSqlDialect {

    public static final PostgresDialect INSTANCE = new PostgresDialect();

    private PostgresDialect() {}

    @Override
    public YapDbEngine engine() {
        return YapDbEngine.POSTGRES;
    }

    @Override
    public String autoIncrementPk() {
        return "BIGSERIAL PRIMARY KEY";
    }

    @Override
    public String booleanType() {
        return "BOOLEAN";
    }

    @Override
    public String blobType() {
        return "BYTEA";
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
        return "EXCLUDED." + column;
    }

    @Override
    public String upsert(String table, List<String> conflictCols, List<String> insertCols, Map<String, String> setClauses) {
        return buildInsertValues(table, insertCols)
                + " ON CONFLICT (" + joinCols(conflictCols) + ") DO UPDATE SET "
                + buildSetClause(setClauses);
    }

    @Override
    public String insertIgnore(String table, List<String> cols) {
        return buildInsertValues(table, cols) + " ON CONFLICT DO NOTHING";
    }
}
