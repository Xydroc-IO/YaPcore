package com.yapcore.db.plugin;

import com.yapcore.db.YapDbEngine;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Lightweight DB discovery: TCP listeners on common ports + JDBC product name after connect.
 * Does not invent credentials — only reports what is reachable / what the open pool is.
 */
final class YapDbProbe {

    record PortHit(String label, YapDbEngine engine, String host, int port, boolean open, String note) {}

    private YapDbProbe() {
    }

    /** Probe common MariaDB/MySQL/Postgres ports on {@code host} (default 127.0.0.1). */
    static List<PortHit> probePorts(String host, int timeoutMs) {
        String h = (host == null || host.isBlank()) ? "127.0.0.1" : host.trim();
        int t = Math.max(200, Math.min(timeoutMs, 5_000));
        List<PortHit> out = new ArrayList<>(4);
        out.add(tryPort("MariaDB/MySQL", YapDbEngine.MYSQL, h, 3306, t, "default docker/host"));
        out.add(tryPort("MariaDB (YaP docker)", YapDbEngine.MYSQL, h, 3316, t, "deploy/mariadb maps 3316→3306"));
        out.add(tryPort("PostgreSQL", YapDbEngine.POSTGRES, h, 5432, t, "default Postgres"));
        return out;
    }

    private static PortHit tryPort(String label, YapDbEngine engine, String host, int port, int timeoutMs, String note) {
        boolean open = false;
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            open = true;
        } catch (IOException ignored) {
            // closed / filtered
        }
        return new PortHit(label, engine, host, port, open, note);
    }

    /** {@code product} + {@code version} from an open JDBC connection, or empty on failure. */
    static Optional<String> readProductLabel(Connection conn) {
        if (conn == null) {
            return Optional.empty();
        }
        try {
            DatabaseMetaData md = conn.getMetaData();
            String product = md.getDatabaseProductName();
            String version = md.getDatabaseProductVersion();
            if (product == null || product.isBlank()) {
                return Optional.empty();
            }
            if (version == null || version.isBlank()) {
                return Optional.of(product.trim());
            }
            return Optional.of(product.trim() + " " + version.trim());
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    /** Map JDBC {@link DatabaseMetaData#getDatabaseProductName()} to a YaP engine. */
    static Optional<YapDbEngine> engineFromProductName(String productName) {
        if (productName == null || productName.isBlank()) {
            return Optional.empty();
        }
        String p = productName.toLowerCase(Locale.ROOT);
        if (p.contains("postgres")) {
            return Optional.of(YapDbEngine.POSTGRES);
        }
        if (p.contains("sqlite")) {
            return Optional.of(YapDbEngine.SQLITE);
        }
        if (p.contains("mysql") || p.contains("mariadb") || p.contains("maria")) {
            return Optional.of(YapDbEngine.MYSQL);
        }
        return Optional.empty();
    }

    static List<String> formatPortReport(List<PortHit> hits) {
        List<String> lines = new ArrayList<>();
        lines.add("TCP probe (open = something accepts connections):");
        boolean any = false;
        for (PortHit hit : hits) {
            String mark = hit.open() ? "OPEN" : "closed";
            lines.add("  [" + mark + "] " + hit.host() + ":" + hit.port()
                    + "  " + hit.label() + " (" + hit.engine().name().toLowerCase(Locale.ROOT) + ") — " + hit.note());
            if (hit.open()) {
                any = true;
            }
        }
        if (!any) {
            lines.add("  No common DB ports answered. Start MariaDB/Postgres or use SQLite.");
            lines.add("  ./scripts/db/start-mariadb.sh · ./scripts/db/start-postgres.sh · --engine sqlite");
        } else {
            lines.add("  Tip: ./scripts/db/configure-db.sh --engine <mysql|postgres|sqlite>");
        }
        return lines;
    }
}
