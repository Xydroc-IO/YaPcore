package com.yapcore.db.dialect;

import com.yapcore.db.YapSqlDialect;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractYapSqlDialect implements YapSqlDialect {

    private static final Pattern EXCLUDED = Pattern.compile("(?i)\\bEXCLUDED\\.([A-Za-z_][A-Za-z0-9_]*)\\b");

    @Override
    public String rewriteExcluded(String expression) {
        if (expression == null || expression.isEmpty()) {
            return expression;
        }
        Matcher m = EXCLUDED.matcher(expression);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(excludedRef(m.group(1))));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Engine-specific reference to the inserted row's column. */
    protected abstract String excludedRef(String column);

    protected String buildInsertValues(String table, List<String> insertCols) {
        return "INSERT INTO " + table + " (" + joinCols(insertCols) + ") VALUES ("
                + placeholders(insertCols.size()) + ")";
    }

    protected String buildSetClause(Map<String, String> setClauses) {
        StringJoiner j = new StringJoiner(", ");
        for (Map.Entry<String, String> e : setClauses.entrySet()) {
            j.add(e.getKey() + " = " + rewriteExcluded(e.getValue()));
        }
        return j.toString();
    }
}
