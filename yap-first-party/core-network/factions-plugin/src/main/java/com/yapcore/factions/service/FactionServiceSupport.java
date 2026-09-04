package com.yapcore.factions.service;

import com.yapcore.factions.Faction;
import com.yapcore.factions.FactionClaimOverlay;
import com.yapcore.factions.FactionMember;
import com.yapcore.factions.FactionPowerCalculator;
import com.yapcore.factions.FactionRelation;
import com.yapcore.factions.FactionRole;
import com.yapcore.factions.FactionsConfig;
import com.yapcore.factions.chat.FactionChatState;
import com.yapcore.factions.db.FactionRepository;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Shared deps and helpers for faction service collaborators. */
final class FactionServiceSupport {

    final JavaPlugin plugin;
    final FactionsConfig config;
    final FactionRepository repository;
    final FactionChatState chatState;
    final Map<Long, Optional<FactionClaimOverlay>> overlayCache = new ConcurrentHashMap<>();

    FactionServiceSupport(
            JavaPlugin plugin,
            FactionsConfig config,
            FactionRepository repository,
            FactionChatState chatState) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.chatState = chatState;
    }

    Optional<Faction> getFaction(long factionId) {
        try {
            return repository.get(factionId);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "getFaction", e);
            return Optional.empty();
        }
    }

    Optional<Faction> findByName(String name) {
        try {
            return repository.findByName(normalizeName(name));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "findByName", e);
            return Optional.empty();
        }
    }

    Optional<Faction> findByTag(String tag) {
        try {
            return repository.findByTag(normalizeTag(tag));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "findByTag", e);
            return Optional.empty();
        }
    }

    Optional<Faction> findByPlayer(UUID playerId) {
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

    Optional<FactionMember> member(UUID playerId) {
        try {
            return repository.member(playerId);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "member", e);
            return Optional.empty();
        }
    }

    Optional<FactionClaimOverlay> overlayForClaim(long claimId) {
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

    Collection<Faction> listFactions() {
        try {
            return repository.listAll();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "listFactions", e);
            return List.of();
        }
    }

    List<FactionMember> listMembers(long factionId) {
        try {
            return repository.members(factionId);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "listMembers", e);
            return List.of();
        }
    }

    List<FactionClaimOverlay> listClaims(long factionId) {
        try {
            return repository.overlaysForFaction(factionId);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "listClaims", e);
            return List.of();
        }
    }

    List<Faction> topFactions(int page, int pageSize) {
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

    List<com.yapcore.factions.FactionInvite> listInvites(UUID playerId) {
        try {
            return repository.invitesForPlayer(playerId).stream()
                    .filter(inv -> !inv.isExpired())
                    .toList();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "listInvites", e);
            return List.of();
        }
    }

    Optional<com.yapcore.factions.FactionInvite> inviteFor(long factionId, UUID playerId) {
        try {
            return repository.invite(factionId, playerId).filter(inv -> !inv.isExpired());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "inviteFor", e);
            return Optional.empty();
        }
    }

    FactionRelation relationBetween(long factionIdA, long factionIdB) {
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

    int claimPowerCost(int claimArea) {
        return FactionPowerCalculator.claimCost(config.powerConfig(), claimArea);
    }

    int maxPowerForMembers(int memberCount) {
        return FactionPowerCalculator.maxPower(config.powerConfig(), memberCount);
    }

    void joinInternal(long factionId, UUID playerId, FactionRole role) throws SQLException {
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

    void refreshPower(long factionId) {
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

    FactionMember requireMember(long factionId, UUID playerId) {
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

    Optional<Faction> resolveFactionRef(String ref) throws SQLException {
        Optional<Faction> byName = repository.findByName(ref);
        if (byName.isPresent()) {
            return byName;
        }
        return repository.findByTag(ref);
    }

    void validateName(String name) {
        String norm = normalizeName(name);
        if (norm.length() < config.nameMin() || norm.length() > config.nameMax()) {
            throw new IllegalArgumentException("name length");
        }
    }

    void validateTag(String tag) {
        String norm = normalizeTag(tag);
        if (norm.length() < config.tagMin() || norm.length() > config.tagMax()) {
            throw new IllegalArgumentException("tag length");
        }
    }

    static String trimText(String text, int max) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    static String formatChat(String template, Faction faction, Player sender, String message) {
        return template
                .replace("%tag%", faction.tag())
                .replace("%faction%", faction.name())
                .replace("%player%", sender.getName())
                .replace("%message%", message);
    }

    static String normalizeName(String name) {
        return name.trim();
    }

    static String normalizeTag(String tag) {
        return tag.trim().toUpperCase(Locale.ROOT);
    }
}
