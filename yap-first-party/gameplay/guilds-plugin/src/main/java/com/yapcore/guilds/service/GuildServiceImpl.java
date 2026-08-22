package com.yapcore.guilds.service;

import com.yapcore.guilds.Guild;
import com.yapcore.guilds.GuildHome;
import com.yapcore.guilds.GuildInvite;
import com.yapcore.guilds.GuildJoinMode;
import com.yapcore.guilds.GuildMember;
import com.yapcore.guilds.GuildRelation;
import com.yapcore.guilds.GuildRole;
import com.yapcore.guilds.GuildService;
import com.yapcore.guilds.GuildXpCalculator;
import com.yapcore.guilds.GuildsConfig;
import com.yapcore.guilds.chat.GuildChatState;
import com.yapcore.guilds.db.GuildRepository;
import com.yapcore.guilds.integration.EconomyIntegration;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class GuildServiceImpl implements GuildService {

    private final JavaPlugin plugin;
    private final GuildsConfig config;
    private final GuildRepository repository;
    private final GuildChatState chatState;

    public GuildServiceImpl(
            JavaPlugin plugin, GuildsConfig config, GuildRepository repository, GuildChatState chatState) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.chatState = chatState;
    }

    public GuildChatState chatState() {
        return chatState;
    }

    @Override
    public Optional<Guild> getGuild(long guildId) {
        try {
            return repository.get(guildId);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "getGuild", e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<Guild> findByName(String name) {
        try {
            return repository.findByName(normalizeName(name));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "findByName", e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<Guild> findByTag(String tag) {
        try {
            return repository.findByTag(normalizeTag(tag));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "findByTag", e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<Guild> findByPlayer(UUID playerId) {
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

    @Override
    public Optional<GuildMember> member(UUID playerId) {
        try {
            return repository.member(playerId);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "member", e);
            return Optional.empty();
        }
    }

    @Override
    public Collection<Guild> listGuilds() {
        try {
            return repository.listAll();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "listGuilds", e);
            return List.of();
        }
    }

    @Override
    public List<GuildMember> listMembers(long guildId) {
        try {
            return repository.members(guildId);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "listMembers", e);
            return List.of();
        }
    }

    @Override
    public List<Guild> topGuilds(int page, int pageSize) {
        int offset = (Math.max(1, page) - 1) * Math.max(1, Math.min(pageSize, 50));
        try {
            return repository.topByLevel(offset, Math.max(1, Math.min(pageSize, 50)));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "topGuilds", e);
            return List.of();
        }
    }

    @Override
    public List<GuildInvite> listInvites(UUID playerId) {
        try {
            return repository.invitesForPlayer(playerId).stream().filter(i -> !i.isExpired()).toList();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "listInvites", e);
            return List.of();
        }
    }

    @Override
    public Optional<GuildInvite> inviteFor(long guildId, UUID playerId) {
        try {
            return repository.invite(guildId, playerId).filter(i -> !i.isExpired());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "inviteFor", e);
            return Optional.empty();
        }
    }

    @Override
    public int maxMembers(long guildId) {
        return getGuild(guildId).map(g -> GuildXpCalculator.maxMembers(config.xpConfig(), g.level())).orElse(0);
    }

    @Override
    public double bankCap(long guildId) {
        return getGuild(guildId).map(g -> GuildXpCalculator.bankCap(config.xpConfig(), g.level())).orElse(0.0);
    }

    @Override
    public CompletableFuture<Guild> create(String name, String tag, UUID leaderId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                validateName(name);
                validateTag(tag);
                if (repository.member(leaderId).isPresent()) {
                    throw new IllegalStateException("already in a guild");
                }
                if (repository.findByName(normalizeName(name)).isPresent()) {
                    throw new IllegalStateException("name taken");
                }
                if (repository.findByTag(normalizeTag(tag)).isPresent()) {
                    throw new IllegalStateException("tag taken");
                }
                Guild draft = new Guild(
                        0, normalizeName(name), normalizeTag(tag), leaderId, 1, 0,
                        "", "", GuildJoinMode.OPEN, 0, GuildHome.unset(), Instant.now());
                long id = repository.create(draft);
                repository.addMember(new GuildMember(id, leaderId, GuildRole.LEADER, 0));
                return repository.get(id).orElseThrow();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> disband(long guildId, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                GuildMember actor = requireMember(guildId, actorId);
                if (!actor.role().atLeast(GuildRole.LEADER)) {
                    throw new IllegalStateException("leader only");
                }
                for (GuildMember m : repository.members(guildId)) {
                    chatState.clear(m.playerId());
                }
                repository.deleteGuild(guildId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> invite(long guildId, UUID targetId, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                GuildMember actor = requireMember(guildId, actorId);
                if (!actor.role().atLeast(GuildRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                ensureMemberCapacity(guildId);
                if (repository.member(targetId).isPresent()) {
                    throw new IllegalStateException("player already in a guild");
                }
                Guild guild = repository.get(guildId).orElseThrow();
                Instant expires = Instant.now().plus(config.inviteExpireHours(), ChronoUnit.HOURS);
                repository.upsertInvite(new GuildInvite(guildId, targetId, actorId, Instant.now(), expires));
                Player target = Bukkit.getPlayer(targetId);
                if (target != null && target.isOnline()) {
                    YapSched.entity(plugin, target, () -> target.sendMessage(
                            "§dGuild invite from §f" + guild.name()
                                    + "§d. Use §f/g accept " + guild.name() + "§d or §f/g deny " + guild.name()));
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> acceptInvite(long guildId, UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            try {
                GuildInvite invite = repository.invite(guildId, playerId)
                        .orElseThrow(() -> new IllegalStateException("no invite"));
                if (invite.isExpired()) {
                    repository.deleteInvite(guildId, playerId);
                    throw new IllegalStateException("invite expired");
                }
                repository.deleteInvite(guildId, playerId);
                joinInternal(guildId, playerId, GuildRole.MEMBER);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> denyInvite(long guildId, UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            try {
                repository.deleteInvite(guildId, playerId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> join(long guildId, UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            try {
                Guild guild = repository.get(guildId).orElseThrow(() -> new IllegalStateException("guild not found"));
                if (guild.joinMode() == GuildJoinMode.CLOSED) {
                    throw new IllegalStateException("guild is closed");
                }
                if (guild.joinMode() == GuildJoinMode.INVITE) {
                    GuildInvite invite = repository.invite(guildId, playerId)
                            .orElseThrow(() -> new IllegalStateException("invite required"));
                    if (invite.isExpired()) {
                        repository.deleteInvite(guildId, playerId);
                        throw new IllegalStateException("invite expired");
                    }
                    repository.deleteInvite(guildId, playerId);
                }
                joinInternal(guildId, playerId, GuildRole.RECRUIT);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> leave(UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            try {
                GuildMember member = repository.member(playerId)
                        .orElseThrow(() -> new IllegalStateException("not in a guild"));
                if (member.role() == GuildRole.LEADER) {
                    throw new IllegalStateException("leaders must disband or transfer leadership");
                }
                repository.removeMember(member.guildId(), playerId);
                chatState.clear(playerId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> kick(long guildId, UUID targetId, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                GuildMember actor = requireMember(guildId, actorId);
                if (!actor.role().atLeast(GuildRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                GuildMember target = repository.member(targetId)
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
                repository.removeMember(guildId, targetId);
                chatState.clear(targetId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> promote(long guildId, UUID targetId, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                GuildMember actor = requireMember(guildId, actorId);
                if (!actor.role().atLeast(GuildRole.LEADER)) {
                    throw new IllegalStateException("leader only");
                }
                GuildMember target = requireMember(guildId, targetId);
                GuildRole next = switch (target.role()) {
                    case RECRUIT -> GuildRole.MEMBER;
                    case MEMBER -> GuildRole.VETERAN;
                    case VETERAN -> GuildRole.OFFICER;
                    case OFFICER, LEADER -> throw new IllegalStateException("cannot promote further");
                };
                repository.updateMemberRole(guildId, targetId, next);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> demote(long guildId, UUID targetId, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                GuildMember actor = requireMember(guildId, actorId);
                if (!actor.role().atLeast(GuildRole.LEADER)) {
                    throw new IllegalStateException("leader only");
                }
                GuildMember target = requireMember(guildId, targetId);
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
                repository.updateMemberRole(guildId, targetId, next);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> transferLeadership(long guildId, UUID targetId, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                GuildMember actor = requireMember(guildId, actorId);
                if (actor.role() != GuildRole.LEADER) {
                    throw new IllegalStateException("leader only");
                }
                requireMember(guildId, targetId);
                repository.updateMemberRole(guildId, actorId, GuildRole.OFFICER);
                repository.updateMemberRole(guildId, targetId, GuildRole.LEADER);
                repository.updateLeader(guildId, targetId);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> setDescription(long guildId, String description, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                GuildMember actor = requireMember(guildId, actorId);
                if (!actor.role().atLeast(GuildRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                repository.updateDescription(guildId, trimText(description, config.descriptionMax()));
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> setMotd(long guildId, String motd, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                GuildMember actor = requireMember(guildId, actorId);
                if (!actor.role().atLeast(GuildRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                repository.updateMotd(guildId, trimText(motd, config.motdMax()));
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> setJoinMode(long guildId, GuildJoinMode mode, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                GuildMember actor = requireMember(guildId, actorId);
                if (!actor.role().atLeast(GuildRole.LEADER)) {
                    throw new IllegalStateException("leader only");
                }
                repository.updateJoinMode(guildId, mode);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> setHome(long guildId, Location location, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                GuildMember actor = requireMember(guildId, actorId);
                if (!actor.role().atLeast(GuildRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                if (location.getWorld() == null) {
                    throw new IllegalStateException("invalid location");
                }
                repository.updateHome(guildId, new GuildHome(
                        location.getWorld().getName(),
                        location.getX(), location.getY(), location.getZ(),
                        location.getYaw(), location.getPitch()));
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> clearHome(long guildId, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                GuildMember actor = requireMember(guildId, actorId);
                if (!actor.role().atLeast(GuildRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                repository.updateHome(guildId, GuildHome.unset());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> bankDeposit(long guildId, UUID actorId, double amount) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (!config.bankEnabled()) {
                    throw new IllegalStateException("bank disabled");
                }
                if (amount < config.bankMinDeposit()) {
                    throw new IllegalArgumentException("amount too small");
                }
                requireMember(guildId, actorId);
                Player player = Bukkit.getPlayer(actorId);
                if (player == null || !player.isOnline()) {
                    throw new IllegalStateException("must be online");
                }
                if (!EconomyIntegration.withdraw(player, amount)) {
                    throw new IllegalStateException("insufficient funds");
                }
                Guild guild = repository.get(guildId).orElseThrow();
                double cap = bankCap(guildId);
                double next = guild.bankBalance() + amount;
                if (next > cap) {
                    throw new IllegalStateException("bank cap reached (" + (int) cap + ")");
                }
                repository.updateBank(guildId, next);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> bankWithdraw(long guildId, UUID actorId, double amount) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (!config.bankEnabled()) {
                    throw new IllegalStateException("bank disabled");
                }
                if (amount < config.bankMinWithdraw()) {
                    throw new IllegalArgumentException("amount too small");
                }
                GuildMember actor = requireMember(guildId, actorId);
                if (!actor.role().atLeast(GuildRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                Player player = Bukkit.getPlayer(actorId);
                if (player == null || !player.isOnline()) {
                    throw new IllegalStateException("must be online");
                }
                Guild guild = repository.get(guildId).orElseThrow();
                if (guild.bankBalance() < amount) {
                    throw new IllegalStateException("insufficient guild funds");
                }
                repository.updateBank(guildId, guild.bankBalance() - amount);
                EconomyIntegration.deposit(player, amount);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> setRelation(long guildId, long otherGuildId, GuildRelation relation, UUID actorId) {
        return CompletableFuture.runAsync(() -> {
            try {
                GuildMember actor = requireMember(guildId, actorId);
                if (!actor.role().atLeast(GuildRole.OFFICER)) {
                    throw new IllegalStateException("officer only");
                }
                repository.get(otherGuildId).orElseThrow(() -> new IllegalStateException("guild not found"));
                repository.setRelation(guildId, otherGuildId, relation);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public GuildRelation relationBetween(long guildIdA, long guildIdB) {
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

    @Override
    public void addGuildXp(long guildId, UUID contributorId, long amount, String source) {
        if (guildId <= 0 || amount <= 0) {
            return;
        }
        try {
            Optional<GuildMember> contributor = contributorId == null ? Optional.empty() : repository.member(contributorId);
            if (contributor.isPresent() && contributor.get().guildId() != guildId) {
                return;
            }
            Guild guild = repository.get(guildId).orElse(null);
            if (guild == null) {
                return;
            }
            GuildXpCalculator.LevelResult result = GuildXpCalculator.applyXp(
                    config.xpConfig(), guild.level(), guild.xp(), amount);
            repository.updateLevelXp(guildId, result.level(), result.xp());
            if (contributorId != null && contributor.isPresent()) {
                repository.addContribution(guildId, contributorId, amount);
            }
            if (result.level() > guild.level()) {
                broadcastLevelUp(guild, result.level());
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "addGuildXp " + source, e);
        }
    }

    @Override
    public void sendGuildChat(Player sender, String message) {
        Optional<GuildMember> member = member(sender.getUniqueId());
        if (member.isEmpty()) {
            sender.sendMessage("§cYou are not in a guild.");
            return;
        }
        Guild guild = getGuild(member.get().guildId()).orElse(null);
        if (guild == null) {
            return;
        }
        String formatted = formatChat(config.guildChatFormat(), guild, sender, message);
        for (GuildMember m : listMembers(guild.id())) {
            Player online = Bukkit.getPlayer(m.playerId());
            if (online != null && online.isOnline()) {
                online.sendMessage(formatted);
            }
        }
    }

    @Override
    public void sendOfficerChat(Player sender, String message) {
        Optional<GuildMember> member = member(sender.getUniqueId());
        if (member.isEmpty() || !member.get().role().atLeast(GuildRole.OFFICER)) {
            sender.sendMessage("§cOfficers only.");
            return;
        }
        Guild guild = getGuild(member.get().guildId()).orElse(null);
        if (guild == null) {
            return;
        }
        String formatted = formatChat(config.officerChatFormat(), guild, sender, message);
        for (GuildMember m : listMembers(guild.id())) {
            if (!m.role().atLeast(GuildRole.OFFICER)) {
                continue;
            }
            Player online = Bukkit.getPlayer(m.playerId());
            if (online != null && online.isOnline()) {
                online.sendMessage(formatted);
            }
        }
    }

    @Override
    public void sendAllyChat(Player sender, String message) {
        Optional<GuildMember> member = member(sender.getUniqueId());
        if (member.isEmpty()) {
            sender.sendMessage("§cYou are not in a guild.");
            return;
        }
        Guild guild = getGuild(member.get().guildId()).orElse(null);
        if (guild == null || guild.level() < 20) {
            sender.sendMessage("§cAlly chat unlocks at guild level 20.");
            return;
        }
        String formatted = formatChat(config.allyChatFormat(), guild, sender, message);
        for (GuildMember m : listMembers(guild.id())) {
            Player online = Bukkit.getPlayer(m.playerId());
            if (online != null && online.isOnline()) {
                online.sendMessage(formatted);
            }
        }
        for (Guild other : listGuilds()) {
            if (other.id() == guild.id()) {
                continue;
            }
            if (relationBetween(guild.id(), other.id()) != GuildRelation.ALLY) {
                continue;
            }
            for (GuildMember ally : listMembers(other.id())) {
                Player online = Bukkit.getPlayer(ally.playerId());
                if (online != null && online.isOnline()) {
                    online.sendMessage(formatted);
                }
            }
        }
    }

    @Override
    public Map<String, Object> dashboardSnapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", config.enabled());
        try {
            out.putAll(repository.dashboardCounts());
            List<Map<String, Object>> preview = repository.listAll().stream().limit(10).map(g -> {
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

    public void adminSetLevel(String guildRef, int level, Long xp) throws SQLException {
        Guild guild = resolveGuildRef(guildRef).orElseThrow(() -> new IllegalStateException("guild not found"));
        int lv = Math.max(1, Math.min(level, config.xpConfig().maxLevel()));
        long xpVal = xp == null ? guild.xp() : Math.max(0, xp);
        repository.updateLevelXp(guild.id(), lv, xpVal);
    }

    public void adminForceDisband(String guildRef) throws SQLException {
        Guild guild = resolveGuildRef(guildRef).orElseThrow(() -> new IllegalStateException("guild not found"));
        for (GuildMember m : repository.members(guild.id())) {
            chatState.clear(m.playerId());
        }
        repository.deleteGuild(guild.id());
    }

    private void joinInternal(long guildId, UUID playerId, GuildRole role) throws SQLException {
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

    private void ensureMemberCapacity(long guildId) throws SQLException {
        Guild guild = repository.get(guildId).orElseThrow(() -> new IllegalStateException("guild not found"));
        int cap = GuildXpCalculator.maxMembers(config.xpConfig(), guild.level());
        if (repository.memberCount(guildId) >= cap) {
            throw new IllegalStateException("guild is full");
        }
    }

    private void broadcastLevelUp(Guild guild, int newLevel) {
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

    private GuildMember requireMember(long guildId, UUID playerId) {
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

    private Optional<Guild> resolveGuildRef(String ref) throws SQLException {
        Optional<Guild> byName = repository.findByName(ref);
        return byName.isPresent() ? byName : repository.findByTag(ref);
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

    private static String formatChat(String template, Guild guild, Player sender, String message) {
        return template
                .replace("%tag%", guild.tag())
                .replace("%guild%", guild.name())
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
