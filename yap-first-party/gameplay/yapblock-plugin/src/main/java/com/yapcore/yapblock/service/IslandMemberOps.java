package com.yapcore.yapblock.service;

import com.yapcore.sched.YapSched;
import com.yapcore.yapblock.IslandRole;
import com.yapcore.yapblock.IslandSnapshot;
import com.yapcore.yapblock.YapblockConfig;
import com.yapcore.yapblock.db.MemberRepository;
import com.yapcore.yapblock.grid.IslandIndex;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class IslandMemberOps {

    private final JavaPlugin plugin;
    private final YapblockConfig config;
    private final IslandIndex index;
    private final MemberRepository members;
    private final IslandRoleCache roles;
    private final IslandVisitOps visitOps;
    private final Map<UUID, Invite> invites = new ConcurrentHashMap<>();

    public IslandMemberOps(
            JavaPlugin plugin,
            YapblockConfig config,
            IslandIndex index,
            MemberRepository members,
            IslandRoleCache roles,
            IslandVisitOps visitOps) {
        this.plugin = plugin;
        this.config = config;
        this.index = index;
        this.members = members;
        this.roles = roles;
        this.visitOps = visitOps;
    }

    public void invite(Player owner, String targetName) {
        IslandSnapshot snap = requireOwnerIsland(owner);
        if (snap == null) {
            return;
        }
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            owner.sendMessage(Component.text(
                    "Player must be online on this server. Usage: /is invite <name>",
                    NamedTextColor.RED));
            return;
        }
        if (target.getUniqueId().equals(owner.getUniqueId())) {
            owner.sendMessage(Component.text("You cannot invite yourself.", NamedTextColor.RED));
            return;
        }
        if (index.ofPlayer(target.getUniqueId()).isPresent()) {
            owner.sendMessage(Component.text(
                    target.getName() + " already has an island. They must /is leave"
                            + " (or /is delete if owner) first.",
                    NamedTextColor.RED));
            target.sendMessage(Component.text(
                    owner.getName() + " wants you on their island — /is leave or /is delete,"
                            + " then ask them to re-invite.",
                    NamedTextColor.YELLOW));
            return;
        }
        if (roles.countNonBanned(snap.id()) >= snap.maxMembers()) {
            owner.sendMessage(Component.text(
                    "Island is at max members. /is upgrade members", NamedTextColor.RED));
            return;
        }
        Instant expires = Instant.now().plusSeconds(config.inviteExpireMinutes() * 60L);
        invites.put(target.getUniqueId(), new Invite(snap.id(), owner.getUniqueId(), expires));
        owner.sendMessage(Component.text(
                "Invite sent to " + target.getName() + " (expires in "
                        + config.inviteExpireMinutes() + "m).",
                NamedTextColor.GREEN));
        Component accept = Component.text("[Accept]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/is accept"))
                .hoverEvent(HoverEvent.showText(
                        Component.text("Join " + owner.getName() + "'s island")));
        Component deny = Component.text("[Deny]", NamedTextColor.RED, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/is deny"))
                .hoverEvent(HoverEvent.showText(Component.text("Decline invite")));
        target.sendMessage(Component.text(
                        owner.getName() + " invited you to their island. ", NamedTextColor.AQUA)
                .append(accept)
                .append(Component.text(" "))
                .append(deny)
                .append(Component.text("  (or /is accept)", NamedTextColor.GRAY)));
    }

    public CompletableFuture<Boolean> accept(Player player) {
        Invite invite = invites.remove(player.getUniqueId());
        if (invite == null || invite.expires().isBefore(Instant.now())) {
            player.sendMessage(Component.text(
                    "No valid invite. Ask the owner to /is invite you.", NamedTextColor.RED));
            return CompletableFuture.completedFuture(false);
        }
        if (index.ofPlayer(player.getUniqueId()).isPresent()) {
            player.sendMessage(Component.text(
                    "You already have an island. /is leave or /is delete first.",
                    NamedTextColor.RED));
            return CompletableFuture.completedFuture(false);
        }
        IslandSnapshot snap = index.byId(invite.islandId()).orElse(null);
        if (snap == null) {
            player.sendMessage(Component.text("Island no longer exists.", NamedTextColor.RED));
            return CompletableFuture.completedFuture(false);
        }
        if (roles.countNonBanned(snap.id()) >= snap.maxMembers()) {
            player.sendMessage(Component.text("Island is full.", NamedTextColor.RED));
            return CompletableFuture.completedFuture(false);
        }
        UUID inviterId = invite.inviterId();
        return setRole(player, snap.id(), player.getUniqueId(), IslandRole.MEMBER, "Joined island!")
                .thenCompose(ok -> {
                    if (!Boolean.TRUE.equals(ok)) {
                        return CompletableFuture.completedFuture(false);
                    }
                    Player inviter = Bukkit.getPlayer(inviterId);
                    if (inviter != null) {
                        YapSched.entity(plugin, inviter, () ->
                                inviter.sendMessage(Component.text(
                                        player.getName() + " joined your island.",
                                        NamedTextColor.GREEN)));
                    }
                    return visitOps.teleportHome(player);
                });
    }

    public void deny(Player player) {
        Invite invite = invites.remove(player.getUniqueId());
        if (invite == null) {
            player.sendMessage(Component.text("No pending invite.", NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text("Invite denied.", NamedTextColor.YELLOW));
        Player inviter = Bukkit.getPlayer(invite.inviterId());
        if (inviter != null) {
            YapSched.entity(plugin, inviter, () ->
                    inviter.sendMessage(Component.text(
                            player.getName() + " denied your invite.", NamedTextColor.YELLOW)));
        }
    }

    public CompletableFuture<Boolean> kick(Player actor, String targetName) {
        return changeRole(actor, targetName, null, true, false, "kicked");
    }

    public CompletableFuture<Boolean> ban(Player actor, String targetName) {
        return changeRole(actor, targetName, IslandRole.BANNED, false, false, "banned");
    }

    public CompletableFuture<Boolean> unban(Player actor, String targetName) {
        IslandSnapshot snap = requireOwnerIsland(actor);
        if (snap == null) {
            return CompletableFuture.completedFuture(false);
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        YapSched.async(plugin, () -> {
            try {
                members.remove(snap.id(), target.getUniqueId());
                roles.remove(snap.id(), target.getUniqueId());
                YapSched.entity(plugin, actor, () ->
                        actor.sendMessage(Component.text(
                                "Unbanned " + targetName + ".", NamedTextColor.GREEN)));
                future.complete(true);
            } catch (Exception e) {
                future.complete(false);
            }
        });
        return future;
    }

    public CompletableFuture<Boolean> trust(Player actor, String targetName) {
        return changeRole(actor, targetName, IslandRole.TRUSTED, false, true, "trusted");
    }

    public CompletableFuture<Boolean> untrust(Player actor, String targetName) {
        IslandSnapshot snap = requireOwnerIsland(actor);
        if (snap == null) {
            return CompletableFuture.completedFuture(false);
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        IslandRole current = roles.role(target.getUniqueId(), snap.id()).orElse(null);
        if (current != IslandRole.TRUSTED) {
            actor.sendMessage(Component.text("That player is not trusted.", NamedTextColor.RED));
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        YapSched.async(plugin, () -> {
            try {
                members.remove(snap.id(), target.getUniqueId());
                roles.remove(snap.id(), target.getUniqueId());
                YapSched.entity(plugin, actor, () ->
                        actor.sendMessage(Component.text(
                                "Untrusted " + targetName + ".", NamedTextColor.GREEN)));
                future.complete(true);
            } catch (Exception e) {
                future.complete(false);
            }
        });
        return future;
    }

    public CompletableFuture<Boolean> leave(Player player) {
        IslandSnapshot snap = index.ofPlayer(player.getUniqueId()).orElse(null);
        if (snap == null) {
            player.sendMessage(Component.text("You have no island.", NamedTextColor.RED));
            return CompletableFuture.completedFuture(false);
        }
        IslandRole role = roles.role(player.getUniqueId(), snap.id()).orElse(null);
        if (role == IslandRole.OWNER) {
            player.sendMessage(Component.text(
                    "Owners must /is delete instead of leave.", NamedTextColor.RED));
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        YapSched.async(plugin, () -> {
            try {
                members.remove(snap.id(), player.getUniqueId());
                roles.remove(snap.id(), player.getUniqueId());
                index.unbindMember(player.getUniqueId());
                YapSched.entity(plugin, player, () ->
                        player.sendMessage(Component.text("Left the island.", NamedTextColor.YELLOW)));
                future.complete(true);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "leave failed", e);
                future.complete(false);
            }
        });
        return future;
    }

    private CompletableFuture<Boolean> changeRole(
            Player actor,
            String targetName,
            IslandRole newRole,
            boolean remove,
            boolean allowOnlineOnly,
            String verb) {
        IslandSnapshot snap = requireOwnerIsland(actor);
        if (snap == null) {
            return CompletableFuture.completedFuture(false);
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (allowOnlineOnly && Bukkit.getPlayer(targetName) == null) {
            actor.sendMessage(Component.text("Player must be online.", NamedTextColor.RED));
            return CompletableFuture.completedFuture(false);
        }
        if (target.getUniqueId().equals(snap.ownerId())) {
            actor.sendMessage(Component.text("Cannot modify the owner.", NamedTextColor.RED));
            return CompletableFuture.completedFuture(false);
        }
        if (remove) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            YapSched.async(plugin, () -> {
                try {
                    members.remove(snap.id(), target.getUniqueId());
                    roles.remove(snap.id(), target.getUniqueId());
                    index.unbindMember(target.getUniqueId());
                    YapSched.entity(plugin, actor, () ->
                            actor.sendMessage(Component.text(
                                    "Player " + verb + ".", NamedTextColor.GREEN)));
                    Player online = target.getPlayer();
                    if (online != null) {
                        YapSched.entity(plugin, online, () ->
                                online.sendMessage(Component.text(
                                        "You were " + verb + " from the island.",
                                        NamedTextColor.RED)));
                    }
                    future.complete(true);
                } catch (Exception e) {
                    future.complete(false);
                }
            });
            return future;
        }
        return setRole(actor, snap.id(), target.getUniqueId(), newRole, "Player " + verb + ".");
    }

    private CompletableFuture<Boolean> setRole(
            Player notifier, long islandId, UUID targetId, IslandRole role, String okMsg) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        YapSched.async(plugin, () -> {
            try {
                members.upsert(islandId, targetId, role);
                roles.put(islandId, targetId, role);
                if (role == IslandRole.MEMBER || role == IslandRole.OWNER) {
                    index.bindMember(targetId, islandId);
                }
                YapSched.entity(plugin, notifier, () ->
                        notifier.sendMessage(Component.text(okMsg, NamedTextColor.GREEN)));
                future.complete(true);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "setRole failed", e);
                future.complete(false);
            }
        });
        return future;
    }

    private IslandSnapshot requireOwnerIsland(Player player) {
        IslandSnapshot snap = index.ofPlayer(player.getUniqueId()).orElse(null);
        if (snap == null) {
            player.sendMessage(Component.text("You have no island.", NamedTextColor.RED));
            return null;
        }
        if (roles.role(player.getUniqueId(), snap.id()).orElse(null) != IslandRole.OWNER) {
            player.sendMessage(Component.text("Only the owner can do that.", NamedTextColor.RED));
            return null;
        }
        return snap;
    }

    private record Invite(long islandId, UUID inviterId, Instant expires) {
    }
}
