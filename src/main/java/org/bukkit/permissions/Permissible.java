package org.bukkit.permissions;

import org.bukkit.plugin.Plugin;

import java.util.Set;

public interface Permissible {

    boolean isPermissionSet(String name);

    boolean hasPermission(String name);

    PermissionAttachment addAttachment(Plugin plugin);

    PermissionAttachment addAttachment(Plugin plugin, String name, boolean value);

    void removeAttachment(PermissionAttachment attachment);

    void recalculatePermissions();

    Set<PermissionAttachment> getEffectiveAttachments();
}
