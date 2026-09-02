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

    /** Rank name color ({@code &a}) for chat / tab. Empty if the group has none set. */
    default Optional<String> getNameColor(UUID uuid) {
        return Optional.empty();
    }

    /** Rank message color ({@code &f}) for public chat. Empty if the group has none set. */
    default Optional<String> getChatColor(UUID uuid) {
        return Optional.empty();
    }

    int getWeight(UUID uuid);

    /** Highest-weight inherited group name (LuckPerms display group). */
    String displayGroup(UUID uuid);

    /** Resolve against a world/server context (empty = global-only nodes). */
    default EffectiveUser resolve(UUID uuid, String name, String world, String server) {
        return resolve(uuid, name);
    }

    /** LuckPerms-style verbose: why a node is granted or denied. */
    default String explain(UUID uuid, String name, String node, String world) {
        return hasPermission(uuid, node) ? "granted" : "denied";
    }

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
