package com.yapcore.items;

import com.yapcore.items.item.ItemDefinition;
import com.yapcore.items.item.ItemRegistry;
import org.bukkit.Bukkit;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Registers YAML-declared item/ability permission nodes and attaches them under
 * {@code yapitems.admin} so staff with admin always pass {@code hasPermission}.
 * <p>
 * Bukkit does <strong>not</strong> treat {@code yapitems.item.*} as a wildcard —
 * nodes must be registered explicitly (LuckPerms wildcards are separate).
 */
public final class ItemsPermissions {

    private final JavaPlugin plugin;
    private final Set<String> dynamic = new HashSet<>();

    public ItemsPermissions(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void sync(ItemRegistry registry) {
        clear();
        PluginManager pm = Bukkit.getPluginManager();
        Permission admin = pm.getPermission("yapitems.admin");
        if (admin == null) {
            admin = new Permission("yapitems.admin", "YaPItems admin", PermissionDefault.OP);
            pm.addPermission(admin);
        }

        int registered = 0;
        for (ItemDefinition def : registry.all().values()) {
            if (registerNode(pm, admin, def.permission(), "Use custom item " + def.id())) {
                registered++;
            }
            for (var ab : def.abilities()) {
                if (registerNode(pm, admin, ab.permission(), "Use ability on " + def.id())) {
                    registered++;
                }
            }
        }
        admin.recalculatePermissibles();
        if (registered > 0) {
            plugin.getLogger().info("Registered " + registered + " item/ability permission nodes under yapitems.admin");
        }
    }

    private boolean registerNode(PluginManager pm, Permission admin, String raw, String description) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String name = raw.trim().toLowerCase(Locale.ROOT);
        if (name.contains("*")) {
            plugin.getLogger().warning("Ignoring wildcard permission '" + name
                    + "' — register concrete nodes (e.g. yapitems.item.stormblade)");
            return false;
        }
        Permission existing = pm.getPermission(name);
        if (existing == null) {
            // FALSE: players need an explicit rank grant. Admins pass via yapitems.admin child.
            existing = new Permission(name, description, PermissionDefault.FALSE);
            pm.addPermission(existing);
        }
        dynamic.add(name);
        admin.getChildren().put(name, true);
        return true;
    }

    public void clear() {
        PluginManager pm = Bukkit.getPluginManager();
        Permission admin = pm.getPermission("yapitems.admin");
        for (String name : dynamic) {
            if (admin != null) {
                admin.getChildren().remove(name);
            }
            Permission p = pm.getPermission(name);
            if (p != null) {
                pm.removePermission(p);
            }
        }
        dynamic.clear();
        if (admin != null) {
            admin.recalculatePermissibles();
        }
    }
}
