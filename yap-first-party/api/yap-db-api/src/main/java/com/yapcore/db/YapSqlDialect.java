package com.yapcore.db;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Portable SQL fragments for MariaDB/MySQL, PostgreSQL, and SQLite.
 * <p>
 * Upsert set-clauses may use {@code EXCLUDED.col} for the inserted value and bare
 * {@code col} for the existing row. Dialects rewrite {@code EXCLUDED.*} as needed.
 */
public interface YapSqlDialect {

    YapDbEngine engine();

    /** BIGINT AUTO_INCREMENT PK / BIGSERIAL / INTEGER PRIMARY KEY AUTOINCREMENT */
    String autoIncrementPk();

    /** TINYINT(1) / BOOLEAN / INTEGER */
    String booleanType();

    /** MEDIUMBLOB / BYTEA / BLOB */
    String blobType();

    /** MEDIUMTEXT / TEXT */
    String longTextType();

    /**
     * Column DDL for an auto-touched timestamp.
     * MySQL: {@code TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP}
     * Others: {@code TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP} (callers should set on write).
     */
    String timestampTouchColumn(String columnName);

    /** NOW() / CURRENT_TIMESTAMP / CURRENT_TIMESTAMP */
    String nowFn();

    /**
     * {@code INSERT … ON DUPLICATE} / {@code ON CONFLICT DO UPDATE} / SQLite conflict upsert.
     *
     * @param conflictCols unique / PK columns that define the conflict target
     * @param insertCols   columns in the INSERT list (same order as {@code ?} placeholders)
     * @param setClauses   map of column → expression using {@code EXCLUDED.x} and/or bare columns
     */
    String upsert(String table, List<String> conflictCols, List<String> insertCols, Map<String, String> setClauses);

    /**
     * Full INSERT statement that ignores conflicts:
     * {@code INSERT IGNORE INTO t (cols) VALUES (?,?,…)} /
     * {@code INSERT INTO t (cols) VALUES (…) ON CONFLICT DO NOTHING} /
     * {@code INSERT OR IGNORE INTO t (cols) VALUES (…)}
     */
    String insertIgnore(String table, List<String> cols);

    /** Rewrite {@code EXCLUDED.col} tokens for this engine inside a SET expression. */
    String rewriteExcluded(String expression);

    /** True when Hikari MySQL prep-stmt cache properties are useful. */
    default boolean preferMysqlPrepStmtCache() {
        return engine() == YapDbEngine.MYSQL;
    }

    /** SQLite wants a tiny pool; networked engines use config. */
    default int preferMaxPoolSize(int configured) {
        return engine() == YapDbEngine.SQLITE ? 1 : Math.max(1, configured);
    }

    default String placeholders(int n) {
        StringJoiner j = new StringJoiner(", ");
        for (int i = 0; i < n; i++) {
            j.add("?");
        }
        return j.toString();
    }

    default String joinCols(Collection<String> cols) {
        return String.join(", ", cols);
    }
}
