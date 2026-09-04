package com.yapcore.guilds.service;

import com.yapcore.guilds.Guild;
import com.yapcore.guilds.GuildHome;
import com.yapcore.guilds.GuildInvite;
import com.yapcore.guilds.GuildJoinMode;
import com.yapcore.guilds.GuildMember;
import com.yapcore.guilds.GuildRole;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class GuildMembershipOps {

    private final GuildServiceSupport s;

    GuildMembershipOps(GuildServiceSupport support) {
        this.s = support;
    }

    CompletableFuture<Guild> create(String name, String tag, UUID leaderId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                s.validateName(name);
                s.validateTag(tag);
                if (s.repository.member(leaderId).isPresent()) {
                    throw new IllegalStateException("already in a guild");
                }
                if (s.repository.findByName(GuildServiceSupport.normalizeName(name)).isPresent()) {
                    throw new IllegalStateException("name taken");
                }
                if (s.repository.findByTag(GuildServiceSupport.normalizeTag(tag)).isPresent()) {
                    throw new IllegalStateException("tag taken");
                }
                Guild draft = new Guild(
                        0, GuildServiceSupport.normalizeName(name), GuildServiceSupport.normalizeTag(tag),
                        leaderId, 1, 0,
                        "", "", GuildJoinMode.OPEN, 0, GuildHome.unset(), Instant.now());
                long id = s.repository.create(draft);
                s.repository.addMember(new GuildMember(id, leaderId, GuildRole.LEADER, 0));
                return s.repository.get(id).orElseThrow();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    CompletableFuture<Void> disband(long guildId, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                GuildMember actor = s.requireMember(guildId, actorId);
                if (!actor.role().atLeast(GuildRole.LEADER)) {
                    throw new IllegalStateException("leader only");
                }
                for (GuildMember m : s.repository.members(guildId)) {
                    s.chatState.clear(m.playerId());
                }
                s.repository.deleteGuild(guildId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    CompletableFuture<Void> invite(long guildId, UUID targetId, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                GuildMember actor = s.requireMember(guildId, actorId);
                if (!actor.role().atLeast(GuildRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                s.ensureMemberCapacity(guildId);
                if (s.repository.member(targetId).isPresent()) {
                    throw new IllegalStateException("player already in a guild");
                }
                Guild guild = s.repository.get(guildId).orElseThrow();
                Instant expires = Instant.now().plus(s.config.inviteExpireHours(), ChronoUnit.HOURS);
                s.repository.upsertInvite(new GuildInvite(guildId, targetId, actorId, Instant.now(), expires));
                Player target = Bukkit.getPlayer(targetId);
                if (target != null && target.isOnline()) {
                    YapSched.entity(s.plugin, target, () -> target.sendMessage(
                            "§dGuild invite from §f" + guild.name()
                                    + "§d. Use §f/g accept " + guild.name() + "§d or §f/g deny " + guild.name()));
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    CompletableFuture<Void> acceptInvite(long guildId, UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            try {
                GuildInvite invite = s.repository.invite(guildId, playerId)
                        .orElseThrow(() -> new IllegalStateException("no invite"));
                if (invite.isExpired()) {
                    s.repository.deleteInvite(guildId, playerId);
                    throw new IllegalStateException("invite expired");
                }
                s.repository.deleteInvite(guildId, playerId);
                s.joinInternal(guildId, playerId, GuildRole.MEMBER);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    CompletableFuture<Void> denyInvite(long guildId, UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            try {
                s.repository.deleteInvite(guildId, playerId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    CompletableFuture<Void> join(long guildId, UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            try {
                Guild guild = s.repository.get(guildId).orElseThrow(() -> new IllegalStateException("guild not found"));
                if (guild.joinMode() == GuildJoinMode.CLOSED) {
                    throw new IllegalStateException("guild is closed");
                }
                if (guild.joinMode() == GuildJoinMode.INVITE) {
                    GuildInvite invite = s.repository.invite(guildId, playerId)
                            .orElseThrow(() -> new IllegalStateException("invite required"));
                    if (invite.isExpired()) {
                        s.repository.deleteInvite(guildId, playerId);
                        throw new IllegalStateException("invite expired");
                    }
                    s.repository.deleteInvite(guildId, playerId);
                }
                s.joinInternal(guildId, playerId, GuildRole.RECRUIT);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    CompletableFuture<Void> leave(UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            try {
                GuildMember member = s.repository.member(playerId)
                        .orElseThrow(() -> new IllegalStateException("not in a guild"));
                if (member.role() == GuildRole.LEADER) {
                    throw new IllegalStateException("leaders must disband or transfer leadership");
                }
                s.repository.removeMember(member.guildId(), playerId);
                s.chatState.clear(playerId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    CompletableFuture<Void> kick(long guildId, UUID targetId, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                GuildMember actor = s.requireMember(guildId, actorId);
                if (!actor.role().atLeast(GuildRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                GuildMember target = s.repository.member(targetId)
                        .orElseThrow(() -> new IllegalStateException("player not in guild"));
                if (target.guildId() != guildId) {
                    throw new IllegalStateException("wrong guild");
                }
                if (target.role().atLeast(GuildRole.OFFICER) && actor.role() != GuildRole.LEADER) {
                    throw new IllegalStateException("cannot kick officer");
                }
                if (target.role() == GuildRole.LEADER) {
                    throw new IllegalStateException("cannot kick leader");
                }
                s.repository.removeMember(guildId, targetId);
                s.chatState.clear(targetId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    CompletableFuture<Void> promote(long guildId, UUID targetId, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                GuildMember actor = s.requireMember(guildId, actorId);
                if (!actor.role().atLeast(GuildRole.LEADER)) {
                    throw new IllegalStateException("leader only");
                }
                GuildMember target = s.requireMember(guildId, targetId);
                GuildRole next = switch (target.role()) {
                    case RECRUIT -> GuildRole.MEMBER;
                    case MEMBER -> GuildRole.VETERAN;
                    case VETERAN -> GuildRole.OFFICER;
                    case OFFICER, LEADER -> throw new IllegalStateException("cannot promote further");
                };
                s.repository.updateMemberRole(guildId, targetId, next);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    CompletableFuture<Void> demote(long guildId, UUID targetId, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                GuildMember actor = s.requireMember(guildId, actorId);
                if (!actor.role().atLeast(GuildRole.LEADER)) {
                    throw new IllegalStateException("leader only");
                }
                GuildMember target = s.requireMember(guildId, targetId);
                if (target.role() == GuildRole.LEADER) {
                    throw new IllegalStateException("cannot demote leader");
                }
                GuildRole next = switch (target.role()) {
                    case OFFICER -> GuildRole.VETERAN;
                    case VETERAN -> GuildRole.MEMBER;
                    case MEMBER -> GuildRole.RECRUIT;
                    case RECRUIT -> throw new IllegalStateException("already lowest rank");
                    case LEADER -> throw new IllegalStateException("cannot demote leader");
                };
                s.repository.updateMemberRole(guildId, targetId, next);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    CompletableFuture<Void> transferLeadership(long guildId, UUID targetId, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                GuildMember actor = s.requireMember(guildId, actorId);
                if (actor.role() != GuildRole.LEADER) {
                    throw new IllegalStateException("leader only");
                }
                s.requireMember(guildId, targetId);
                s.repository.updateMemberRole(guildId, actorId, GuildRole.OFFICER);
                s.repository.updateMemberRole(guildId, targetId, GuildRole.LEADER);
                s.repository.updateLeader(guildId, targetId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
