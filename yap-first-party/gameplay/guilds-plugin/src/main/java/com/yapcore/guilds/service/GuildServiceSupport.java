package com.yapcore.guilds.service;

import com.yapcore.guilds.Guild;
import com.yapcore.guilds.GuildInvite;
import com.yapcore.guilds.GuildMember;
import com.yapcore.guilds.GuildRelation;
import com.yapcore.guilds.GuildRole;
import com.yapcore.guilds.GuildXpCalculator;
import com.yapcore.guilds.GuildsConfig;
import com.yapcore.guilds.chat.GuildChatState;
import com.yapcore.guilds.db.GuildRepository;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

final class GuildServiceSupport {

    final JavaPlugin plugin;
    final GuildsConfig config;
    final GuildRepository repository;
    final GuildChatState chatState;

    GuildServiceSupport(
            JavaPlugin plugin, GuildsConfig config, GuildRepository repository, GuildChatState chatState) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.chatState = chatState;
    }

    Optional<Guild> getGuild(long guildId) {
        try {
            return repository.get(guildId);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "getGuild", e);
            return Optional.empty();
        }
    }

    Optional<Guild> findByName(String name) {
        try {
            return repository.findByName(normalizeName(name));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "findByName", e);
            return Optional.empty();
        }
    }

    Optional<Guild> findByTag(String tag) {
        try {
            return repository.findByTag(normalizeTag(tag));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "findByTag", e);
            return Optional.empty();
        }
    }

    Optional<Guild> findByPlayer(UUID playerId) {
        try {
            return repository.member(playerId).flatMap(m -> {
                try {
                    return repository.get(m.guildId());
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "findByPlayer", e);
            return Optional.empty();
        }
    }

    Optional<GuildMember> member(UUID playerId) {
        try {
            return repository.member(playerId);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "member", e);
            return Optional.empty();
        }
    }

    Collection<Guild> listGuilds() {
        try {
            return repository.listAll();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "listGuilds", e);
            return List.of();
        }
    }

    List<GuildMember> listMembers(long guildId) {
        try {
            return repository.members(guildId);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "listMembers", e);
            return List.of();
        }
    }

    List<Guild> topGuilds(int page, int pageSize) {
        int offset = (Math.max(1, page) - 1) * Math.max(1, Math.min(pageSize, 50));
        try {
            return repository.topByLevel(offset, Math.max(1, Math.min(pageSize, 50)));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "topGuilds", e);
            return List.of();
        }
    }

    List<GuildInvite> listInvites(UUID playerId) {
        try {
            return repository.invitesForPlayer(playerId).stream().filter(i -> !i.isExpired()).toList();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "listInvites", e);
            return List.of();
        }
    }

    Optional<GuildInvite> inviteFor(long guildId, UUID playerId) {
        try {
            return repository.invite(guildId, playerId).filter(i -> !i.isExpired());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "inviteFor", e);
            return Optional.empty();
        }
    }

    int maxMembers(long guildId) {
        return getGuild(guildId).map(g -> GuildXpCalculator.maxMembers(config.xpConfig(), g.level())).orElse(0);
    }

    double bankCap(long guildId) {
        return getGuild(guildId).map(g -> GuildXpCalculator.bankCap(config.xpConfig(), g.level())).orElse(0.0);
    }

    GuildRelation relationBetween(long guildIdA, long guildIdB) {
        if (guildIdA == guildIdB) {
            return GuildRelation.NEUTRAL;
        }
        try {
            return repository.relation(guildIdA, guildIdB).orElse(GuildRelation.NEUTRAL);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "relationBetween", e);
            return GuildRelation.NEUTRAL;
        }
    }

    void joinInternal(long guildId, UUID playerId, GuildRole role) throws SQLException {
        if (repository.member(playerId).isPresent()) {
            throw new IllegalStateException("already in a guild");
        }
        ensureMemberCapacity(guildId);
        repository.deleteInvitesForPlayer(playerId);
        repository.addMember(new GuildMember(guildId, playerId, role, 0));
        Guild guild = repository.get(guildId).orElseThrow();
        if (!guild.motd().isBlank()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                YapSched.entity(plugin, player, () -> player.sendMessage("§d" + guild.motd()));
            }
        }
    }

    void ensureMemberCapacity(long guildId) throws SQLException {
        Guild guild = repository.get(guildId).orElseThrow(() -> new IllegalStateException("guild not found"));
        int cap = GuildXpCalculator.maxMembers(config.xpConfig(), guild.level());
        if (repository.memberCount(guildId) >= cap) {
            throw new IllegalStateException("guild is full");
        }
    }

    void broadcastLevelUp(Guild guild, int newLevel) {
        String msg = "§dGuild §f" + guild.name() + " §dreached level §f" + newLevel + "§d!";
        for (GuildMember m : listMembers(guild.id())) {
            Player online = Bukkit.getPlayer(m.playerId());
            if (online != null && online.isOnline()) {
                online.sendMessage(msg);
            }
        }
        String perk = config.perkDescriptions().get(newLevel);
        if (perk != null && !perk.isBlank()) {
            for (GuildMember m : listMembers(guild.id())) {
                Player online = Bukkit.getPlayer(m.playerId());
                if (online != null && online.isOnline()) {
                    online.sendMessage("§6Perk unlocked: §f" + perk);
                }
            }
        }
    }

    GuildMember requireMember(long guildId, UUID playerId) {
        try {
            GuildMember member = repository.member(playerId)
                    .orElseThrow(() -> new IllegalStateException("not in a guild"));
            if (member.guildId() != guildId) {
                throw new IllegalStateException("wrong guild");
            }
            return member;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    Optional<Guild> resolveGuildRef(String ref) throws SQLException {
        Optional<Guild> byName = repository.findByName(ref);
        return byName.isPresent() ? byName : repository.findByTag(ref);
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

    static String formatChat(String template, Guild guild, Player sender, String message) {
        return template
                .replace("%tag%", guild.tag())
                .replace("%guild%", guild.name())
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
