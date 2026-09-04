package com.yapcore.factions.service;

import com.yapcore.factions.Faction;
import com.yapcore.factions.FactionMember;
import com.yapcore.factions.FactionRelation;
import com.yapcore.factions.FactionRole;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class FactionRelationChatOps {

    private final FactionServiceSupport s;

    FactionRelationChatOps(FactionServiceSupport support) {
        this.s = support;
    }

    CompletableFuture<Void> setRelation(
            long factionId, long otherFactionId, FactionRelation relation, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                FactionMember actor = s.requireMember(factionId, actorId);
                if (!actor.role().atLeast(FactionRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                s.repository.get(otherFactionId).orElseThrow(() -> new IllegalStateException("faction not found"));
                s.repository.setRelation(factionId, otherFactionId, relation);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    void sendFactionChat(Player sender, String message) {
        Optional<FactionMember> member = s.member(sender.getUniqueId());
        if (member.isEmpty()) {
            sender.sendMessage("§cYou are not in a faction.");
            return;
        }
        Faction faction = s.getFaction(member.get().factionId()).orElse(null);
        if (faction == null) {
            return;
        }
        String formatted = FactionServiceSupport.formatChat(s.config.factionChatFormat(), faction, sender, message);
        for (FactionMember m : s.listMembers(faction.id())) {
            Player online = Bukkit.getPlayer(m.playerId());
            if (online != null && online.isOnline()) {
                online.sendMessage(formatted);
            }
        }
    }

    void sendAllyChat(Player sender, String message) {
        Optional<FactionMember> member = s.member(sender.getUniqueId());
        if (member.isEmpty()) {
            sender.sendMessage("§cYou are not in a faction.");
            return;
        }
        Faction faction = s.getFaction(member.get().factionId()).orElse(null);
        if (faction == null) {
            return;
        }
        String formatted = FactionServiceSupport.formatChat(s.config.allyChatFormat(), faction, sender, message);
        for (FactionMember m : s.listMembers(faction.id())) {
            Player online = Bukkit.getPlayer(m.playerId());
            if (online != null && online.isOnline()) {
                online.sendMessage(formatted);
            }
        }
        for (Faction other : s.listFactions()) {
            if (other.id() == faction.id()) {
                continue;
            }
            if (s.relationBetween(faction.id(), other.id()) != FactionRelation.ALLY) {
                continue;
            }
            for (FactionMember ally : s.listMembers(other.id())) {
                Player online = Bukkit.getPlayer(ally.playerId());
                if (online != null && online.isOnline()) {
                    online.sendMessage(formatted);
                }
            }
        }
    }
}
