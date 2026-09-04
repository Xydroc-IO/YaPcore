package com.yapcore.guilds.service;

import com.yapcore.guilds.Guild;
import com.yapcore.guilds.GuildMember;
import com.yapcore.guilds.GuildRelation;
import com.yapcore.guilds.GuildRole;
import com.yapcore.guilds.GuildXpCalculator;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

final class GuildRelationChatOps {

    private final GuildServiceSupport s;

    GuildRelationChatOps(GuildServiceSupport support) {
        this.s = support;
    }

    CompletableFuture<Void> setRelation(long guildId, long otherGuildId, GuildRelation relation, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                GuildMember actor = s.requireMember(guildId, actorId);
                if (!actor.role().atLeast(GuildRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                s.repository.get(otherGuildId).orElseThrow(() -> new IllegalStateException("guild not found"));
                s.repository.setRelation(guildId, otherGuildId, relation);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    void addGuildXp(long guildId, UUID contributorId, long amount, String source) {
        if (guildId <= 0 || amount <= 0) {
            return;
        }
        try {
            Optional<GuildMember> contributor = contributorId == null ? Optional.empty() : s.repository.member(contributorId);
            if (contributor.isPresent() && contributor.get().guildId() != guildId) {
                return;
            }
            Guild guild = s.repository.get(guildId).orElse(null);
            if (guild == null) {
                return;
            }
            GuildXpCalculator.LevelResult result = GuildXpCalculator.applyXp(
                    s.config.xpConfig(), guild.level(), guild.xp(), amount);
            s.repository.updateLevelXp(guildId, result.level(), result.xp());
            if (contributorId != null && contributor.isPresent()) {
                s.repository.addContribution(guildId, contributorId, amount);
            }
            if (result.level() > guild.level()) {
                s.broadcastLevelUp(guild, result.level());
            }
        } catch (SQLException e) {
            s.plugin.getLogger().log(Level.WARNING, "addGuildXp " + source, e);
        }
    }

    void sendGuildChat(Player sender, String message) {
        Optional<GuildMember> member = s.member(sender.getUniqueId());
        if (member.isEmpty()) {
            sender.sendMessage("§cYou are not in a guild.");
            return;
        }
        Guild guild = s.getGuild(member.get().guildId()).orElse(null);
        if (guild == null) {
            return;
        }
        String formatted = GuildServiceSupport.formatChat(s.config.guildChatFormat(), guild, sender, message);
        for (GuildMember m : s.listMembers(guild.id())) {
            Player online = Bukkit.getPlayer(m.playerId());
            if (online != null && online.isOnline()) {
                online.sendMessage(formatted);
            }
        }
    }

    void sendOfficerChat(Player sender, String message) {
        Optional<GuildMember> member = s.member(sender.getUniqueId());
        if (member.isEmpty() || !member.get().role().atLeast(GuildRole.OFFICER)) {
            sender.sendMessage("§cOfficers only.");
            return;
        }
        Guild guild = s.getGuild(member.get().guildId()).orElse(null);
        if (guild == null) {
            return;
        }
        String formatted = GuildServiceSupport.formatChat(s.config.officerChatFormat(), guild, sender, message);
        for (GuildMember m : s.listMembers(guild.id())) {
            if (!m.role().atLeast(GuildRole.OFFICER)) {
                continue;
            }
            Player online = Bukkit.getPlayer(m.playerId());
            if (online != null && online.isOnline()) {
                online.sendMessage(formatted);
            }
        }
    }

    void sendAllyChat(Player sender, String message) {
        Optional<GuildMember> member = s.member(sender.getUniqueId());
        if (member.isEmpty()) {
            sender.sendMessage("§cYou are not in a guild.");
            return;
        }
        Guild guild = s.getGuild(member.get().guildId()).orElse(null);
        if (guild == null || guild.level() < 20) {
            sender.sendMessage("§cAlly chat unlocks at guild level 20.");
            return;
        }
        String formatted = GuildServiceSupport.formatChat(s.config.allyChatFormat(), guild, sender, message);
        for (GuildMember m : s.listMembers(guild.id())) {
            Player online = Bukkit.getPlayer(m.playerId());
            if (online != null && online.isOnline()) {
                online.sendMessage(formatted);
            }
        }
        for (Guild other : s.listGuilds()) {
            if (other.id() == guild.id()) {
                continue;
            }
            if (s.relationBetween(guild.id(), other.id()) != GuildRelation.ALLY) {
                continue;
            }
            for (GuildMember ally : s.listMembers(other.id())) {
                Player online = Bukkit.getPlayer(ally.playerId());
                if (online != null && online.isOnline()) {
                    online.sendMessage(formatted);
                }
            }
        }
    }

    Map<String, Object> dashboardSnapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", s.config.enabled());
        try {
            out.putAll(s.repository.dashboardCounts());
            List<Map<String, Object>> preview = s.repository.listAll().stream().limit(10).map(g -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", g.id());
                row.put("name", g.name());
                row.put("tag", g.tag());
                row.put("level", g.level());
                row.put("xp", g.xp());
                row.put("bank", g.bankBalance());
                return row;
            }).toList();
            out.put("preview", preview);
        } catch (SQLException e) {
            out.put("error", e.getMessage());
        }
        return out;
    }

    void adminSetLevel(String guildRef, int level, Long xp) throws SQLException {
        Guild guild = s.resolveGuildRef(guildRef).orElseThrow(() -> new IllegalStateException("guild not found"));
        int lv = Math.max(1, Math.min(level, s.config.xpConfig().maxLevel()));
        long xpVal = xp == null ? guild.xp() : Math.max(0, xp);
        s.repository.updateLevelXp(guild.id(), lv, xpVal);
    }

    void adminForceDisband(String guildRef) throws SQLException {
        Guild guild = s.resolveGuildRef(guildRef).orElseThrow(() -> new IllegalStateException("guild not found"));
        for (GuildMember m : s.repository.members(guild.id())) {
            s.chatState.clear(m.playerId());
        }
        s.repository.deleteGuild(guild.id());
    }
}
