package com.yapcore.playerdata.db;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Reflective bridge to YaPDB so playerdata does not hard-require yap-db classes
 * on the classpath when the shared plugin is absent.
 */
final class YapDbBridge {

    record Handle(Plugin plugin, Method connectionMethod, Method isOpenMethod, Method jdbcUrlMethod) {
        boolean open() {
            try {
                return Boolean.TRUE.equals(isOpenMethod.invoke(plugin));
            } catch (ReflectiveOperationException e) {
                return false;
            }
        }

        Connection borrow() throws SQLException {
            try {
                return (Connection) connectionMethod.invoke(plugin);
            } catch (ReflectiveOperationException e) {
                throw new SQLException("YaPDB connection() failed", e);
            }
        }

        String url() {
            try {
                Object v = jdbcUrlMethod.invoke(plugin);
                return v != null ? v.toString() : "";
            } catch (ReflectiveOperationException e) {
                return "";
            }
        }
    }

    private YapDbBridge() {
    }

    static Optional<Handle> find(Logger log) {
        Plugin p = Bukkit.getPluginManager().getPlugin("YaPDB");
        if (p == null || !p.isEnabled()) {
            return Optional.empty();
        }
        try {
            Method connection = p.getClass().getMethod("connection");
            Method isOpen = p.getClass().getMethod("isOpen");
            Method jdbcUrl = p.getClass().getMethod("jdbcUrl");
            Handle h = new Handle(p, connection, isOpen, jdbcUrl);
            if (!h.open()) {
                return Optional.empty();
            }
            return Optional.of(h);
        } catch (NoSuchMethodException e) {
            log.warning("YaPDB found but does not expose YapDb methods — using embedded pool");
            return Optional.empty();
        }
    }
}
