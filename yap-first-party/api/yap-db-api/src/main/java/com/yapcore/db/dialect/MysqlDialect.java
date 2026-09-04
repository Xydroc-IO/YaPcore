package com.yapcore.db.dialect;

import com.yapcore.db.YapDbEngine;

import java.util.List;
import java.util.Map;

public final class MysqlDialect extends AbstractYapSqlDialect {

    public static final MysqlDialect INSTANCE = new MysqlDialect();

    private MysqlDialect() {}

    @Override
    public YapDbEngine engine() {
        return YapDbEngine.MYSQL;
    }

    @Override
    public String autoIncrementPk() {
        return "BIGINT AUTO_INCREMENT PRIMARY KEY";
    }

    @Override
    public String booleanType() {
        return "TINYINT(1)";
    }

    @Override
    public String blobType() {
        return "MEDIUMBLOB";
    }

    @Override
    public String longTextType() {
        return "MEDIUMTEXT";
    }

    @Override
    public String timestampTouchColumn(String columnName) {
        return columnName + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP";
    }

    @Override
    public String nowFn() {
        return "NOW()";
    }

    @Override
    protected String excludedRef(String column) {
        return "VALUES(" + column + ")";
    }

    @Override
    public String upsert(String table, List<String> conflictCols, List<String> insertCols, Map<String, String> setClauses) {
        return buildInsertValues(table, insertCols) + " ON DUPLICATE KEY UPDATE " + buildSetClause(setClauses);
    }

    @Override
    public String insertIgnore(String table, List<String> cols) {
        return "INSERT IGNORE INTO " + table + " (" + joinCols(cols) + ") VALUES ("
                + placeholders(cols.size()) + ")";
    }
}
