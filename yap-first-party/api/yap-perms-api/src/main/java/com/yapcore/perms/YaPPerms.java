package com.yapcore.perms;

import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Native YaP permissions service — LuckPerms-class groups, inheritance, tracks, meta.
 * Provided by {@code YaPPerms} plugin via {@code ServicesManager}.
 */
public interface YaPPerms {

    /** Resolved effective permissions for a user (includes inherited group nodes). */
    EffectiveUser resolve(UUID uuid, String name);

    CompletableFuture<EffectiveUser> resolveAsync(UUID uuid, String name);

    /** Re-apply Bukkit attachments for one online player. */
    void refresh(Player player);

    /** Re-apply attachments for every online player (after reload / group edit). */
    void refreshAll();

    Optional<String> getPrimaryGroup(UUID uuid);

    Collection<String> getGroups(UUID uuid);

    Optional<String> getPrefix(UUID uuid);

    Optional<String> getSuffix(UUID uuid);

    int getWeight(UUID uuid);

    /** Highest-weight parent group name. */
    String displayGroup(UUID uuid);

    /** Promote one step on a track; returns new group or empty if at top / unknown. */
    Optional<String> promote(UUID uuid, String track);

    /** Demote one step on a track; returns new group or empty if at bottom / unknown. */
    Optional<String> demote(UUID uuid, String track);

    /** Apply starter pack groups + permissions (idempotent). */
    void applyStarterPack();

    /** Reload groups/users from DB and refresh online players. */
    void reload();

    /** Live permission map for API consumers (node → granted). */
    Map<String, Boolean> permissionMap(UUID uuid);

    /** Wildcard-aware permission check using resolved nodes. */
    default boolean hasPermission(UUID uuid, String node) {
        return PermissionNodes.has(permissionMap(uuid), node);
    }
}
