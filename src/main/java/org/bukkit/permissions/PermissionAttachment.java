package org.bukkit.permissions;

import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PermissionAttachment {

    private final Plugin plugin;
    private final Permissible permissible;
    private final Map<String, Boolean> permissions = new ConcurrentHashMap<>();

    public PermissionAttachment(Plugin plugin, Permissible permissible) {
        this.plugin = plugin;
        this.permissible = permissible;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public Permissible getPermissible() {
        return permissible;
    }

    public void setPermission(String name, boolean value) {
        permissions.put(name.toLowerCase(), value);
        permissible.recalculatePermissions();
    }

    public void unsetPermission(String name) {
        permissions.remove(name.toLowerCase());
        permissible.recalculatePermissions();
    }

    public Map<String, Boolean> getPermissions() {
        return Collections.unmodifiableMap(permissions);
    }

    public void remove() {
        permissible.removeAttachment(this);
    }
}
