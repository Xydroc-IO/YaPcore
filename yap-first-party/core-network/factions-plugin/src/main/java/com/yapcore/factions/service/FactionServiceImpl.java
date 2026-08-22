package com.yapcore.factions.service;

import com.yapcore.factions.Faction;
import com.yapcore.factions.FactionClaimOverlay;
import com.yapcore.factions.FactionHome;
import com.yapcore.factions.FactionInvite;
import com.yapcore.factions.FactionJoinMode;
import com.yapcore.factions.FactionMember;
import com.yapcore.factions.FactionPowerCalculator;
import com.yapcore.factions.FactionRelation;
import com.yapcore.factions.FactionRole;
import com.yapcore.factions.FactionService;
import com.yapcore.factions.FactionsConfig;
import com.yapcore.factions.chat.FactionChatState;
import com.yapcore.factions.db.FactionRepository;
import com.yapcore.factions.integration.ClaimIntegration;
import com.yapcore.factions.integration.EconomyIntegration;
import com.yapcore.playerdata.claims.Claim;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class FactionServiceImpl implements FactionService {

    private final JavaPlugin plugin;
    private final FactionsConfig config;
    private final FactionRepository repository;
    private final FactionChatState chatState;
    private final Map<Long, Optional<FactionClaimOverlay>> overlayCache = new ConcurrentHashMap<>();

    public FactionServiceImpl(
            JavaPlugin plugin,
            FactionsConfig config,
            FactionRepository repository,
            FactionChatState chatState) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.chatState = chatState;
    }

    public FactionChatState chatState() {
        return chatState;
    }

    @Override
    public Optional<Faction> getFaction(long factionId) {
        try {
            return repository.get(factionId);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "getFaction", e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<Faction> findByName(String name) {
        try {
            return repository.findByName(normalizeName(name));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "findByName", e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<Faction> findByTag(String tag) {
        try {
            return repository.findByTag(normalizeTag(tag));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "findByTag", e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<Faction> findByPlayer(UUID playerId) {
        try {
            return repository.member(playerId).flatMap(m -> {
                try {
                    return repository.get(m.factionId());
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "findByPlayer", e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<FactionMember> member(UUID playerId) {
        try {
            return repository.member(playerId);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "member", e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<FactionClaimOverlay> overlayForClaim(long claimId) {
        Optional<FactionClaimOverlay> cached = overlayCache.get(claimId);
        if (cached != null) {
            return cached;
        }
        try {
            Optional<FactionClaimOverlay> loaded = repository.overlay(claimId);
            overlayCache.put(claimId, loaded);
            return loaded;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "overlayForClaim", e);
            return Optional.empty();
        }
    }

    @Override
    public Collection<Faction> listFactions() {
        try {
            return repository.listAll();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "listFactions", e);
            return List.of();
        }
    }

    @Override
    public List<FactionMember> listMembers(long factionId) {
        try {
            return repository.members(factionId);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "listMembers", e);
            return List.of();
        }
    }

    @Override
    public List<FactionClaimOverlay> listClaims(long factionId) {
        try {
            return repository.overlaysForFaction(factionId);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "listClaims", e);
            return List.of();
        }
    }

    @Override
    public List<Faction> topFactions(int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(pageSize, 50));
        int offset = (safePage - 1) * safeSize;
        try {
            return repository.topByPower(offset, safeSize);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "topFactions", e);
            return List.of();
        }
    }

    @Override
    public List<FactionInvite> listInvites(UUID playerId) {
        try {
            return repository.invitesForPlayer(playerId).stream()
                    .filter(inv -> !inv.isExpired())
                    .toList();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "listInvites", e);
            return List.of();
        }
    }

    @Override
    public Optional<FactionInvite> inviteFor(long factionId, UUID playerId) {
        try {
            return repository.invite(factionId, playerId).filter(inv -> !inv.isExpired());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "inviteFor", e);
            return Optional.empty();
        }
    }

    @Override
    public CompletableFuture<Faction> create(String name, String tag, UUID leaderId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                validateName(name);
                validateTag(tag);
                if (repository.member(leaderId).isPresent()) {
                    throw new IllegalStateException("already in a faction");
                }
                if (repository.findByName(normalizeName(name)).isPresent()) {
                    throw new IllegalStateException("name taken");
                }
                if (repository.findByTag(normalizeTag(tag)).isPresent()) {
                    throw new IllegalStateException("tag taken");
                }
                int max = maxPowerForMembers(1);
                Faction draft = new Faction(
                        0,
                        normalizeName(name),
                        normalizeTag(tag),
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
                long id = repository.create(draft);
                repository.addMember(new FactionMember(id, leaderId, FactionRole.LEADER));
                return repository.get(id).orElseThrow();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> disband(long factionId, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                FactionMember actor = requireMember(factionId, actorId);
                if (!actor.role().atLeast(FactionRole.LEADER)) {
                    throw new IllegalStateException("leader only");
                }
                for (FactionMember m : repository.members(factionId)) {
                    chatState.clear(m.playerId());
                }
                repository.deleteFaction(factionId);
                overlayCache.clear();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> invite(long factionId, UUID targetId, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                FactionMember actor = requireMember(factionId, actorId);
                if (!actor.role().atLeast(FactionRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                if (repository.member(targetId).isPresent()) {
                    throw new IllegalStateException("player already in a faction");
                }
                repository.get(factionId).orElseThrow(() -> new IllegalStateException("faction not found"));
                Instant expires = Instant.now().plus(config.inviteExpireHours(), ChronoUnit.HOURS);
                repository.upsertInvite(new FactionInvite(factionId, targetId, actorId, Instant.now(), expires));
                Player target = Bukkit.getPlayer(targetId);
                if (target != null && target.isOnline()) {
                    Faction faction = repository.get(factionId).orElseThrow();
                    YapSched.entity(plugin, target, () -> target.sendMessage(
                            "§aYou were invited to join §f" + faction.name()
                                    + "§a. Use §f/f accept " + faction.name() + "§a or §f/f deny "
                                    + faction.name()));
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> acceptInvite(long factionId, UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            try {
                FactionInvite invite = repository.invite(factionId, playerId)
                        .orElseThrow(() -> new IllegalStateException("no invite"));
                if (invite.isExpired()) {
                    repository.deleteInvite(factionId, playerId);
                    throw new IllegalStateException("invite expired");
                }
                repository.deleteInvite(factionId, playerId);
                joinInternal(factionId, playerId, FactionRole.MEMBER);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> denyInvite(long factionId, UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            try {
                repository.deleteInvite(factionId, playerId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> join(long factionId, UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            try {
                Faction faction = repository.get(factionId).orElseThrow(() -> new IllegalStateException("faction not found"));
                if (faction.joinMode() == FactionJoinMode.CLOSED) {
                    throw new IllegalStateException("faction is closed");
                }
                if (faction.joinMode() == FactionJoinMode.INVITE) {
                    FactionInvite invite = repository.invite(factionId, playerId)
                            .orElseThrow(() -> new IllegalStateException("invite required"));
                    if (invite.isExpired()) {
                        repository.deleteInvite(factionId, playerId);
                        throw new IllegalStateException("invite expired");
                    }
                    repository.deleteInvite(factionId, playerId);
                }
                joinInternal(factionId, playerId, FactionRole.MEMBER);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> leave(UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            try {
                FactionMember member = repository.member(playerId)
                        .orElseThrow(() -> new IllegalStateException("not in a faction"));
                if (member.role() == FactionRole.LEADER) {
                    throw new IllegalStateException("leaders must disband or transfer leadership");
                }
                repository.removeMember(member.factionId(), playerId);
                chatState.clear(playerId);
                refreshPower(member.factionId());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> kick(long factionId, UUID targetId, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                FactionMember actor = requireMember(factionId, actorId);
                if (!actor.role().atLeast(FactionRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                FactionMember target = repository.member(targetId)
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
                repository.removeMember(factionId, targetId);
                chatState.clear(targetId);
                refreshPower(factionId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> promote(long factionId, UUID targetId, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                FactionMember actor = requireMember(factionId, actorId);
                if (!actor.role().atLeast(FactionRole.LEADER)) {
                    throw new IllegalStateException("leader only");
                }
                FactionMember target = requireMember(factionId, targetId);
                FactionRole next = switch (target.role()) {
                    case RECRUIT -> FactionRole.MEMBER;
                    case MEMBER -> FactionRole.OFFICER;
                    case OFFICER, LEADER -> throw new IllegalStateException("cannot promote further");
                };
                repository.updateMemberRole(factionId, targetId, next);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> demote(long factionId, UUID targetId, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                FactionMember actor = requireMember(factionId, actorId);
                if (!actor.role().atLeast(FactionRole.LEADER)) {
                    throw new IllegalStateException("leader only");
                }
                FactionMember target = requireMember(factionId, targetId);
                if (target.role() == FactionRole.LEADER) {
                    throw new IllegalStateException("cannot demote leader");
                }
                FactionRole next = switch (target.role()) {
                    case OFFICER -> FactionRole.MEMBER;
                    case MEMBER -> FactionRole.RECRUIT;
                    case RECRUIT -> throw new IllegalStateException("already lowest rank");
                    case LEADER -> throw new IllegalStateException("cannot demote leader");
                };
                repository.updateMemberRole(factionId, targetId, next);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> transferLeadership(long factionId, UUID targetId, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                FactionMember actor = requireMember(factionId, actorId);
                if (actor.role() != FactionRole.LEADER) {
                    throw new IllegalStateException("leader only");
                }
                FactionMember target = requireMember(factionId, targetId);
                if (target.role() == FactionRole.LEADER) {
                    throw new IllegalStateException("already leader");
                }
                repository.updateMemberRole(factionId, actorId, FactionRole.OFFICER);
                repository.updateMemberRole(factionId, targetId, FactionRole.LEADER);
                repository.updateLeader(factionId, targetId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> setDescription(long factionId, String description, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                FactionMember actor = requireMember(factionId, actorId);
                if (!actor.role().atLeast(FactionRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                String trimmed = trimText(description, config.descriptionMax());
                repository.updateDescription(factionId, trimmed);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> setMotd(long factionId, String motd, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                FactionMember actor = requireMember(factionId, actorId);
                if (!actor.role().atLeast(FactionRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                String trimmed = trimText(motd, config.motdMax());
                repository.updateMotd(factionId, trimmed);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> setJoinMode(long factionId, FactionJoinMode mode, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                FactionMember actor = requireMember(factionId, actorId);
                if (!actor.role().atLeast(FactionRole.LEADER)) {
                    throw new IllegalStateException("leader only");
                }
                repository.updateJoinMode(factionId, mode);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> setHome(long factionId, Location location, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                FactionMember actor = requireMember(factionId, actorId);
                if (!actor.role().atLeast(FactionRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                if (location.getWorld() == null) {
                    throw new IllegalStateException("invalid location");
                }
                FactionHome home = new FactionHome(
                        location.getWorld().getName(),
                        location.getX(),
                        location.getY(),
                        location.getZ(),
                        location.getYaw(),
                        location.getPitch());
                repository.updateHome(factionId, home);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> clearHome(long factionId, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                FactionMember actor = requireMember(factionId, actorId);
                if (!actor.role().atLeast(FactionRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                repository.updateHome(factionId, FactionHome.unset());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> bankDeposit(long factionId, UUID actorId, double amount) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (!config.bankEnabled()) {
                    throw new IllegalStateException("bank disabled");
                }
                if (amount < config.bankMinDeposit()) {
                    throw new IllegalArgumentException("amount too small");
                }
                requireMember(factionId, actorId);
                Player player = Bukkit.getPlayer(actorId);
                if (player == null || !player.isOnline()) {
                    throw new IllegalStateException("must be online");
                }
                if (!EconomyIntegration.withdraw(player, amount)) {
                    throw new IllegalStateException("insufficient funds");
                }
                Faction faction = repository.get(factionId).orElseThrow();
                repository.updateBank(factionId, faction.bankBalance() + amount);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> bankWithdraw(long factionId, UUID actorId, double amount) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (!config.bankEnabled()) {
                    throw new IllegalStateException("bank disabled");
                }
                if (amount < config.bankMinWithdraw()) {
                    throw new IllegalArgumentException("amount too small");
                }
                FactionMember actor = requireMember(factionId, actorId);
                if (!actor.role().atLeast(FactionRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                Player player = Bukkit.getPlayer(actorId);
                if (player == null || !player.isOnline()) {
                    throw new IllegalStateException("must be online");
                }
                Faction faction = repository.get(factionId).orElseThrow();
                if (faction.bankBalance() < amount) {
                    throw new IllegalStateException("insufficient faction funds");
                }
                repository.updateBank(factionId, faction.bankBalance() - amount);
                EconomyIntegration.deposit(player, amount);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> setRelation(
            long factionId, long otherFactionId, FactionRelation relation, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                FactionMember actor = requireMember(factionId, actorId);
                if (!actor.role().atLeast(FactionRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                repository.get(otherFactionId).orElseThrow(() -> new IllegalStateException("faction not found"));
                repository.setRelation(factionId, otherFactionId, relation);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public FactionRelation relationBetween(long factionIdA, long factionIdB) {
        if (factionIdA == factionIdB) {
            return FactionRelation.NEUTRAL;
        }
        try {
            return repository.relation(factionIdA, factionIdB).orElse(FactionRelation.NEUTRAL);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "relationBetween", e);
            return FactionRelation.NEUTRAL;
        }
    }

    @Override
    public CompletableFuture<FactionClaimOverlay> linkClaim(
            long claimId, long factionId, UUID actorId, int claimArea) {
        return CompletableFuture.supplyAsync(() -> linkClaimInternal(claimId, factionId, actorId, claimArea));
    }

    @Override
    public CompletableFuture<Integer> linkAllClaims(
            long factionId, UUID actorId, List<Long> claimIds, List<Integer> claimAreas) {
        return CompletableFuture.supplyAsync(() -> {
            int linked = 0;
            for (int i = 0; i < claimIds.size(); i++) {
                long claimId = claimIds.get(i);
                int area = claimAreas.get(i);
                try {
                    if (repository.overlay(claimId).isPresent()) {
                        continue;
                    }
                    linkClaimInternal(claimId, factionId, actorId, area);
                    linked++;
                } catch (RuntimeException | SQLException ignored) {
                    // stop when out of power
                    break;
                }
            }
            return linked;
        });
    }

    @Override
    public CompletableFuture<Void> unlinkClaim(long claimId, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                FactionClaimOverlay overlay = repository.overlay(claimId)
                        .orElseThrow(() -> new IllegalStateException("claim not linked"));
                FactionMember actor = requireMember(overlay.factionId(), actorId);
                if (!actor.role().atLeast(FactionRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                repository.unlinkClaim(claimId);
                overlayCache.put(claimId, Optional.empty());
                refreshPower(overlay.factionId());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public int claimPowerCost(int claimArea) {
        return FactionPowerCalculator.claimCost(config.powerConfig(), claimArea);
    }

    @Override
    public int maxPowerForMembers(int memberCount) {
        return FactionPowerCalculator.maxPower(config.powerConfig(), memberCount);
    }

    @Override
    public Optional<Long> factionAt(Location location) {
        return ClaimIntegration.claimAt(location)
                .flatMap(c -> overlayForClaim(c.id()).map(FactionClaimOverlay::factionId));
    }

    @Override
    public void sendFactionChat(Player sender, String message) {
        Optional<FactionMember> member = member(sender.getUniqueId());
        if (member.isEmpty()) {
            sender.sendMessage("§cYou are not in a faction.");
            return;
        }
        Faction faction = getFaction(member.get().factionId()).orElse(null);
        if (faction == null) {
            return;
        }
        String formatted = formatChat(config.factionChatFormat(), faction, sender, message);
        for (FactionMember m : listMembers(faction.id())) {
            Player online = Bukkit.getPlayer(m.playerId());
            if (online != null && online.isOnline()) {
                online.sendMessage(formatted);
            }
        }
    }

    @Override
    public void sendAllyChat(Player sender, String message) {
        Optional<FactionMember> member = member(sender.getUniqueId());
        if (member.isEmpty()) {
            sender.sendMessage("§cYou are not in a faction.");
            return;
        }
        Faction faction = getFaction(member.get().factionId()).orElse(null);
        if (faction == null) {
            return;
        }
        String formatted = formatChat(config.allyChatFormat(), faction, sender, message);
        for (FactionMember m : listMembers(faction.id())) {
            Player online = Bukkit.getPlayer(m.playerId());
            if (online != null && online.isOnline()) {
                online.sendMessage(formatted);
            }
        }
        for (Faction other : listFactions()) {
            if (other.id() == faction.id()) {
                continue;
            }
            if (relationBetween(faction.id(), other.id()) != FactionRelation.ALLY) {
                continue;
            }
            for (FactionMember ally : listMembers(other.id())) {
                Player online = Bukkit.getPlayer(ally.playerId());
                if (online != null && online.isOnline()) {
                    online.sendMessage(formatted);
                }
            }
        }
    }

    @Override
    public Optional<Boolean> evaluateBuild(Player player, long claimId, UUID claimOwnerId) {
        Optional<FactionClaimOverlay> overlay = overlayForClaim(claimId);
        if (overlay.isEmpty()) {
            return Optional.empty();
        }
        if (claimOwnerId.equals(player.getUniqueId())) {
            return Optional.empty();
        }
        Optional<Faction> territoryFaction = getFaction(overlay.get().factionId());
        if (territoryFaction.isPresent() && territoryFaction.get().isShielded()) {
            Optional<FactionMember> member = member(player.getUniqueId());
            if (member.isEmpty() || member.get().factionId() != overlay.get().factionId()) {
                return Optional.of(false);
            }
        }
        long factionId = overlay.get().factionId();
        Optional<FactionMember> member = member(player.getUniqueId());
        if (member.isPresent() && member.get().factionId() == factionId) {
            return Optional.of(true);
        }
        if (member.isPresent() && config.alliesCanBuild()) {
            FactionRelation rel = relationBetween(member.get().factionId(), factionId);
            if (rel == FactionRelation.ALLY) {
                return Optional.of(true);
            }
        }
        return Optional.of(false);
    }

    @Override
    public Optional<Boolean> evaluatePvp(Player attacker, Player victim, long claimId) {
        Optional<FactionClaimOverlay> overlay = overlayForClaim(claimId);
        if (overlay.isEmpty()) {
            return Optional.empty();
        }
        Optional<Faction> territoryFaction = getFaction(overlay.get().factionId());
        if (territoryFaction.isPresent() && territoryFaction.get().isShielded() && config.shieldBlocksPvp()) {
            return Optional.of(false);
        }
        Optional<FactionMember> atk = member(attacker.getUniqueId());
        Optional<FactionMember> vic = member(victim.getUniqueId());
        if (atk.isEmpty() || vic.isEmpty()) {
            return Optional.empty();
        }
        long factionClaim = overlay.get().factionId();
        if (atk.get().factionId() == vic.get().factionId()) {
            return Optional.of(false);
        }
        FactionRelation rel = relationBetween(atk.get().factionId(), vic.get().factionId());
        if (rel == FactionRelation.ALLY) {
            return Optional.of(false);
        }
        if (rel == FactionRelation.ENEMY && config.enemyPvpOnly()) {
            return Optional.of(true);
        }
        if (atk.get().factionId() == factionClaim || vic.get().factionId() == factionClaim) {
            return Optional.of(rel == FactionRelation.ENEMY);
        }
        return Optional.empty();
    }

    @Override
    public Map<String, Object> dashboardSnapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", config.enabled());
        try {
            out.putAll(repository.dashboardCounts());
            List<Map<String, Object>> preview = repository.listAll().stream().limit(10).map(f -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", f.id());
                row.put("name", f.name());
                row.put("tag", f.tag());
                row.put("power", f.power());
                row.put("maxPower", f.maxPower());
                row.put("joinMode", f.joinMode().name());
                row.put("bank", f.bankBalance());
                row.put("shielded", f.isShielded());
                return row;
            }).toList();
            out.put("preview", preview);
        } catch (SQLException e) {
            out.put("error", e.getMessage());
        }
        return out;
    }

    public void invalidateOverlay(long claimId) {
        overlayCache.remove(claimId);
    }

    public void applyDeathPowerLoss(UUID playerId) {
        try {
            Optional<FactionMember> member = repository.member(playerId);
            if (member.isEmpty() || config.powerLossOnDeath() <= 0) {
                return;
            }
            long factionId = member.get().factionId();
            Faction faction = repository.get(factionId).orElse(null);
            if (faction == null) {
                return;
            }
            int used = repository.totalOverlayPower(factionId);
            int cap = Math.max(0, faction.maxPower() - used);
            int next = Math.max(0, faction.power() - config.powerLossOnDeath());
            repository.updatePowerOnly(factionId, next);
            if (next <= 0 && cap <= 0 && config.shieldSeconds() > 0) {
                repository.updateShield(factionId, Instant.now().plusSeconds(config.shieldSeconds()));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "applyDeathPowerLoss", e);
        }
    }

    public void regenPowerTick() {
        try {
            for (Faction faction : repository.listAll()) {
                int used = repository.totalOverlayPower(faction.id());
                int cap = Math.max(0, faction.maxPower() - used);
                if (cap <= 0 || config.powerRegenAmount() <= 0) {
                    continue;
                }
                int next = Math.min(cap, faction.power() + config.powerRegenAmount());
                if (next != faction.power()) {
                    repository.updatePowerOnly(faction.id(), next);
                }
                if (next > 0 && faction.isShielded()) {
                    repository.updateShield(faction.id(), null);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "regenPowerTick", e);
        }
    }

    public void adminSetPower(String factionRef, int power, Integer maxPower) throws SQLException {
        Faction faction = resolveFactionRef(factionRef).orElseThrow(() -> new IllegalStateException("faction not found"));
        int max = maxPower == null ? faction.maxPower() : maxPower;
        repository.updatePower(faction.id(), power, max);
    }

    public void adminSetJoinMode(String factionRef, FactionJoinMode mode) throws SQLException {
        Faction faction = resolveFactionRef(factionRef).orElseThrow(() -> new IllegalStateException("faction not found"));
        repository.updateJoinMode(faction.id(), mode);
    }

    public void adminForceDisband(String factionRef) throws SQLException {
        Faction faction = resolveFactionRef(factionRef).orElseThrow(() -> new IllegalStateException("faction not found"));
        for (FactionMember m : repository.members(faction.id())) {
            chatState.clear(m.playerId());
        }
        repository.deleteFaction(faction.id());
        overlayCache.clear();
    }

    private Optional<Faction> resolveFactionRef(String ref) throws SQLException {
        Optional<Faction> byName = repository.findByName(ref);
        if (byName.isPresent()) {
            return byName;
        }
        return repository.findByTag(ref);
    }

    private FactionClaimOverlay linkClaimInternal(long claimId, long factionId, UUID actorId, int claimArea) {
        try {
            FactionMember actor = requireMember(factionId, actorId);
            if (!actor.role().atLeast(FactionRole.OFFICER)) {
                throw new IllegalStateException("officer only");
            }
            int cost = claimPowerCost(claimArea);
            Faction faction = repository.get(factionId).orElseThrow();
            int used = repository.totalOverlayPower(factionId);
            if (used + cost > faction.maxPower()) {
                throw new IllegalStateException("not enough faction power");
            }
            FactionClaimOverlay overlay = new FactionClaimOverlay(claimId, factionId, cost, Instant.now());
            repository.linkClaim(overlay);
            overlayCache.put(claimId, Optional.of(overlay));
            refreshPower(factionId);
            return overlay;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void joinInternal(long factionId, UUID playerId, FactionRole role) throws SQLException {
        if (repository.member(playerId).isPresent()) {
            throw new IllegalStateException("already in a faction");
        }
        repository.deleteInvitesForPlayer(playerId);
        repository.addMember(new FactionMember(factionId, playerId, role));
        refreshPower(factionId);
        Faction faction = repository.get(factionId).orElseThrow();
        if (!faction.motd().isBlank()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                YapSched.entity(plugin, player, () -> player.sendMessage("§6" + faction.motd()));
            }
        }
    }

    private void refreshPower(long factionId) {
        try {
            int members = repository.memberCount(factionId);
            int max = maxPowerForMembers(members);
            int used = repository.totalOverlayPower(factionId);
            Faction faction = repository.get(factionId).orElse(null);
            int current = faction == null ? 0 : faction.power();
            int cap = Math.max(0, max - used);
            int available = FactionPowerCalculator.clampPower(Math.min(current, cap), cap);
            if (faction != null && current > cap) {
                available = cap;
            } else if (faction == null) {
                available = FactionPowerCalculator.clampPower(max - used, max);
            }
            repository.updatePower(factionId, available, max);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private FactionMember requireMember(long factionId, UUID playerId) {
        try {
            FactionMember member = repository.member(playerId)
                    .orElseThrow(() -> new IllegalStateException("not in a faction"));
            if (member.factionId() != factionId) {
                throw new IllegalStateException("wrong faction");
            }
            return member;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void validateName(String name) {
        String norm = normalizeName(name);
        if (norm.length() < config.nameMin() || norm.length() > config.nameMax()) {
            throw new IllegalArgumentException("name length");
        }
    }

    private void validateTag(String tag) {
        String norm = normalizeTag(tag);
        if (norm.length() < config.tagMin() || norm.length() > config.tagMax()) {
            throw new IllegalArgumentException("tag length");
        }
    }

    private static String trimText(String text, int max) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static String formatChat(String template, Faction faction, Player sender, String message) {
        return template
                .replace("%tag%", faction.tag())
                .replace("%faction%", faction.name())
                .replace("%player%", sender.getName())
                .replace("%message%", message);
    }

    private static String normalizeName(String name) {
        return name.trim();
    }

    private static String normalizeTag(String tag) {
        return tag.trim().toUpperCase(Locale.ROOT);
    }
}
