package com.yapcore.discord;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Guild member role/nickname helpers for {@link DiscordBotService}.
 */
final class DiscordBotMemberOps {

    private final DiscordPlugin plugin;
    private final DiscordBotService bot;

    DiscordBotMemberOps(DiscordPlugin plugin, DiscordBotService bot) {
        this.plugin = plugin;
        this.bot = bot;
    }

    /**
     * Fetches Discord member role snowflakes for role sync. Runs on JDA threads; callback may be async.
     */
    void fetchMemberRoleIds(String discordUserId, Consumer<Set<String>> callback) {
        if (!bot.isConnected() || bot.jda() == null || discordUserId == null || discordUserId.isBlank()) {
            callback.accept(Set.of());
            return;
        }
        DiscordConfig config = plugin.config();
        String guildId = config == null ? "" : config.botGuildId();
        if (guildId.isBlank()) {
            callback.accept(Set.of());
            return;
        }
        var guild = bot.jda().getGuildById(guildId);
        if (guild == null) {
            callback.accept(Set.of());
            return;
        }
        guild.retrieveMemberById(discordUserId).queue(
                member -> {
                    Set<String> ids = member.getRoles().stream()
                            .map(Role::getId)
                            .collect(Collectors.toUnmodifiableSet());
                    callback.accept(ids);
                },
                err -> {
                    plugin.getLogger().fine("member role fetch failed: " + err.getMessage());
                    callback.accept(Set.of());
                });
    }

    /**
     * Sets guild nickname for a linked Discord user (needs Manage Nicknames + role hierarchy).
     * Failures are logged at fine — never breaks the link flow.
     */
    void modifyMemberNickname(String discordUserId, String nickname) {
        if (!bot.isConnected() || bot.jda() == null || discordUserId == null || discordUserId.isBlank()) {
            return;
        }
        if (nickname == null || nickname.isBlank()) {
            return;
        }
        String nick = nickname.length() > 32 ? nickname.substring(0, 32) : nickname;
        DiscordConfig config = plugin.config();
        String guildId = config == null ? "" : config.botGuildId();
        if (guildId.isBlank()) {
            return;
        }
        var guild = bot.jda().getGuildById(guildId);
        if (guild == null) {
            return;
        }
        guild.retrieveMemberById(discordUserId).queue(
                member -> member.modifyNickname(nick).queue(
                        ok -> plugin.getLogger().fine("nickname sync mc→discord: " + nick),
                        err -> plugin.getLogger().fine("nickname sync failed: " + err.getMessage())),
                err -> plugin.getLogger().fine("nickname member fetch failed: " + err.getMessage()));
    }

    void grantRole(String discordUserId, String roleId) {
        mutateRole(discordUserId, roleId, true);
    }

    void revokeRole(String discordUserId, String roleId) {
        mutateRole(discordUserId, roleId, false);
    }

    private void mutateRole(String discordUserId, String roleId, boolean grant) {
        if (!bot.isConnected() || bot.jda() == null || discordUserId == null || discordUserId.isBlank()
                || roleId == null || roleId.isBlank()) {
            return;
        }
        DiscordConfig config = plugin.config();
        String guildId = config == null ? "" : config.botGuildId();
        if (guildId.isBlank()) {
            return;
        }
        var guild = bot.jda().getGuildById(guildId);
        if (guild == null) {
            return;
        }
        Role role = guild.getRoleById(roleId);
        if (role == null) {
            plugin.getLogger().fine("discord role missing: " + roleId);
            return;
        }
        guild.retrieveMemberById(discordUserId).queue(
                member -> {
                    var action = grant
                            ? guild.addRoleToMember(member, role)
                            : guild.removeRoleFromMember(member, role);
                    action.queue(
                            ok -> plugin.getLogger().fine((grant ? "granted" : "revoked")
                                    + " role " + roleId + " for " + discordUserId),
                            err -> plugin.getLogger().fine("role mutate failed: " + err.getMessage()));
                },
                err -> plugin.getLogger().fine("role member fetch failed: " + err.getMessage()));
    }

    /** Effective guild nickname (or username) for discord→mc sync. */
    void fetchMemberNickname(String discordUserId, Consumer<String> callback) {
        if (!bot.isConnected() || bot.jda() == null || discordUserId == null || discordUserId.isBlank()) {
            callback.accept(null);
            return;
        }
        DiscordConfig config = plugin.config();
        String guildId = config == null ? "" : config.botGuildId();
        if (guildId.isBlank()) {
            callback.accept(null);
            return;
        }
        var guild = bot.jda().getGuildById(guildId);
        if (guild == null) {
            callback.accept(null);
            return;
        }
        guild.retrieveMemberById(discordUserId).queue(
                member -> {
                    String nick = member.getNickname();
                    if (nick == null || nick.isBlank()) {
                        nick = member.getUser().getName();
                    }
                    callback.accept(nick);
                },
                err -> {
                    plugin.getLogger().fine("nickname fetch failed: " + err.getMessage());
                    callback.accept(null);
                });
    }

    static Set<String> memberRoleIds(Member member) {
        if (member == null) {
            return Set.of();
        }
        Set<String> ids = new HashSet<>();
        for (Role role : member.getRoles()) {
            ids.add(role.getId());
        }
        return ids;
    }

    /**
     * Admin slash allowlist: any configured role, else Discord Administrator permission.
     */
    static boolean memberAllowedAdminSlash(Member member, DiscordConfig config) {
        if (member == null || config == null) {
            return false;
        }
        List<String> allowed = config.slashRolesAdmin();
        if (allowed != null && !allowed.isEmpty()) {
            return TextCommandParser.hasAnyRole(memberRoleIds(member), allowed);
        }
        return member.hasPermission(Permission.ADMINISTRATOR);
    }
}
