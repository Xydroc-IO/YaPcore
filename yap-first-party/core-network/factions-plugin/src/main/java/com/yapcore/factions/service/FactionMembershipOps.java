package com.yapcore.factions.service;

import com.yapcore.factions.Faction;
import com.yapcore.factions.FactionHome;
import com.yapcore.factions.FactionInvite;
import com.yapcore.factions.FactionJoinMode;
import com.yapcore.factions.FactionMember;
import com.yapcore.factions.FactionRole;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class FactionMembershipOps {

    private final FactionServiceSupport s;

    FactionMembershipOps(FactionServiceSupport support) {
        this.s = support;
    }

    CompletableFuture<Faction> create(String name, String tag, UUID leaderId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                s.validateName(name);
                s.validateTag(tag);
                if (s.repository.member(leaderId).isPresent()) {
                    throw new IllegalStateException("already in a faction");
                }
                if (s.repository.findByName(FactionServiceSupport.normalizeName(name)).isPresent()) {
                    throw new IllegalStateException("name taken");
                }
                if (s.repository.findByTag(FactionServiceSupport.normalizeTag(tag)).isPresent()) {
                    throw new IllegalStateException("tag taken");
                }
                int max = s.maxPowerForMembers(1);
                Faction draft = new Faction(
                        0,
                        FactionServiceSupport.normalizeName(name),
                        FactionServiceSupport.normalizeTag(tag),
                        leaderId,
                        max,
                        max,
                        "",
                        "",
                        FactionJoinMode.OPEN,
                        0,
                        FactionHome.unset(),
                        null,
                        Instant.now());
                long id = s.repository.create(draft);
                s.repository.addMember(new FactionMember(id, leaderId, FactionRole.LEADER));
                return s.repository.get(id).orElseThrow();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    CompletableFuture<Void> disband(long factionId, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                FactionMember actor = s.requireMember(factionId, actorId);
                if (!actor.role().atLeast(FactionRole.LEADER)) {
                    throw new IllegalStateException("leader only");
                }
                for (FactionMember m : s.repository.members(factionId)) {
                    s.chatState.clear(m.playerId());
                }
                s.repository.deleteFaction(factionId);
                s.overlayCache.clear();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    CompletableFuture<Void> invite(long factionId, UUID targetId, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                FactionMember actor = s.requireMember(factionId, actorId);
                if (!actor.role().atLeast(FactionRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                if (s.repository.member(targetId).isPresent()) {
                    throw new IllegalStateException("player already in a faction");
                }
                s.repository.get(factionId).orElseThrow(() -> new IllegalStateException("faction not found"));
                Instant expires = Instant.now().plus(s.config.inviteExpireHours(), ChronoUnit.HOURS);
                s.repository.upsertInvite(new FactionInvite(factionId, targetId, actorId, Instant.now(), expires));
                Player target = Bukkit.getPlayer(targetId);
                if (target != null && target.isOnline()) {
                    Faction faction = s.repository.get(factionId).orElseThrow();
                    YapSched.entity(s.plugin, target, () -> target.sendMessage(
                            "§aYou were invited to join §f" + faction.name()
                                    + "§a. Use §f/f accept " + faction.name() + "§a or §f/f deny "
                                    + faction.name()));
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    CompletableFuture<Void> acceptInvite(long factionId, UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            try {
                FactionInvite invite = s.repository.invite(factionId, playerId)
                        .orElseThrow(() -> new IllegalStateException("no invite"));
                if (invite.isExpired()) {
                    s.repository.deleteInvite(factionId, playerId);
                    throw new IllegalStateException("invite expired");
                }
                s.repository.deleteInvite(factionId, playerId);
                s.joinInternal(factionId, playerId, FactionRole.MEMBER);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    CompletableFuture<Void> denyInvite(long factionId, UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            try {
                s.repository.deleteInvite(factionId, playerId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    CompletableFuture<Void> join(long factionId, UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            try {
                Faction faction = s.repository.get(factionId).orElseThrow(() -> new IllegalStateException("faction not found"));
                if (faction.joinMode() == FactionJoinMode.CLOSED) {
                    throw new IllegalStateException("faction is closed");
                }
                if (faction.joinMode() == FactionJoinMode.INVITE) {
                    FactionInvite invite = s.repository.invite(factionId, playerId)
                            .orElseThrow(() -> new IllegalStateException("invite required"));
                    if (invite.isExpired()) {
                        s.repository.deleteInvite(factionId, playerId);
                        throw new IllegalStateException("invite expired");
                    }
                    s.repository.deleteInvite(factionId, playerId);
                }
                s.joinInternal(factionId, playerId, FactionRole.MEMBER);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    CompletableFuture<Void> leave(UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            try {
                FactionMember member = s.repository.member(playerId)
                        .orElseThrow(() -> new IllegalStateException("not in a faction"));
                if (member.role() == FactionRole.LEADER) {
                    throw new IllegalStateException("leaders must disband or transfer leadership");
                }
                s.repository.removeMember(member.factionId(), playerId);
                s.chatState.clear(playerId);
                s.refreshPower(member.factionId());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    CompletableFuture<Void> kick(long factionId, UUID targetId, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                FactionMember actor = s.requireMember(factionId, actorId);
                if (!actor.role().atLeast(FactionRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                FactionMember target = s.repository.member(targetId)
                        .orElseThrow(() -> new IllegalStateException("player not in faction"));
                if (target.factionId() != factionId) {
                    throw new IllegalStateException("wrong faction");
                }
                if (target.role().atLeast(FactionRole.OFFICER) && actor.role() != FactionRole.LEADER) {
                    throw new IllegalStateException("cannot kick officer");
                }
                if (target.role() == FactionRole.LEADER) {
                    throw new IllegalStateException("cannot kick leader");
                }
                s.repository.removeMember(factionId, targetId);
                s.chatState.clear(targetId);
                s.refreshPower(factionId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    CompletableFuture<Void> promote(long factionId, UUID targetId, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                FactionMember actor = s.requireMember(factionId, actorId);
                if (!actor.role().atLeast(FactionRole.LEADER)) {
                    throw new IllegalStateException("leader only");
                }
                FactionMember target = s.requireMember(factionId, targetId);
                FactionRole next = switch (target.role()) {
                    case RECRUIT -> FactionRole.MEMBER;
                    case MEMBER -> FactionRole.OFFICER;
                    case OFFICER, LEADER -> throw new IllegalStateException("cannot promote further");
                };
                s.repository.updateMemberRole(factionId, targetId, next);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    CompletableFuture<Void> demote(long factionId, UUID targetId, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                FactionMember actor = s.requireMember(factionId, actorId);
                if (!actor.role().atLeast(FactionRole.LEADER)) {
                    throw new IllegalStateException("leader only");
                }
                FactionMember target = s.requireMember(factionId, targetId);
                if (target.role() == FactionRole.LEADER) {
                    throw new IllegalStateException("cannot demote leader");
                }
                FactionRole next = switch (target.role()) {
                    case OFFICER -> FactionRole.MEMBER;
                    case MEMBER -> FactionRole.RECRUIT;
                    case RECRUIT -> throw new IllegalStateException("already lowest rank");
                    case LEADER -> throw new IllegalStateException("cannot demote leader");
                };
                s.repository.updateMemberRole(factionId, targetId, next);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    CompletableFuture<Void> transferLeadership(long factionId, UUID targetId, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                FactionMember actor = s.requireMember(factionId, actorId);
                if (actor.role() != FactionRole.LEADER) {
                    throw new IllegalStateException("leader only");
                }
                FactionMember target = s.requireMember(factionId, targetId);
                if (target.role() == FactionRole.LEADER) {
                    throw new IllegalStateException("already leader");
                }
                s.repository.updateMemberRole(factionId, actorId, FactionRole.OFFICER);
                s.repository.updateMemberRole(factionId, targetId, FactionRole.LEADER);
                s.repository.updateLeader(factionId, targetId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
