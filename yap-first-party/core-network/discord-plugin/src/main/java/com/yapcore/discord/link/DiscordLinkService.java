package com.yapcore.discord.link;

import com.yapcore.discord.DiscordConfig;
import com.yapcore.discord.DiscordPlugin;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Short-lived link codes + durable Discord↔MC pairing. Role sync runs when YaPPerms is present.
 */
public final class DiscordLinkService {

    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final DiscordPlugin plugin;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, PendingCode> pendingByCode = new ConcurrentHashMap<>();
    private DiscordLinkDatabase database;
    private DiscordLinkRepository repository;

    public DiscordLinkService(DiscordPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void reload(DiscordConfig config) {
        closeDb();
        if (config == null || !config.linkEnabled()) {
            return;
        }
        try {
            database = new DiscordLinkDatabase(plugin);
            database.open(config.linkUseSharedYapDb());
            repository = database.repository();
        } catch (SQLException e) {
            plugin.getLogger().severe("Discord link store failed: " + e.getMessage());
            database = null;
            repository = null;
        }
        purgeExpiredPending();
    }

    public synchronized void shutdown() {
        closeDb();
        pendingByCode.clear();
    }

    private void closeDb() {
        if (database != null) {
            database.close();
            database = null;
        }
        repository = null;
    }

    public boolean isReady() {
        return repository != null;
    }

    public String storageStatus() {
        if (database == null) {
            return "disabled / unavailable";
        }
        return database.usingShared() ? "YaPDB" : "local SQLite";
    }

    /** Creates or refreshes a short code for the player. */
    public Optional<String> createLinkCode(Player player) {
        if (repository == null) {
            return Optional.empty();
        }
        DiscordConfig config = plugin.config();
        long ttlMs = config.linkCodeTtlSeconds() * 1000L;
        purgeExpiredPending();
        // Drop prior pending codes for this player.
        pendingByCode.entrySet().removeIf(e -> e.getValue().mcUuid().equals(player.getUniqueId()));
        String code = randomCode(6);
        pendingByCode.put(code, new PendingCode(player.getUniqueId(), player.getName(),
                System.currentTimeMillis() + ttlMs));
        return Optional.of(code);
    }

    public Optional<DiscordLink> findByMcUuid(UUID uuid) {
        if (repository == null) {
            return Optional.empty();
        }
        try {
            return repository.findByMcUuid(uuid);
        } catch (SQLException e) {
            plugin.getLogger().warning("link lookup failed: " + e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<DiscordLink> findByDiscordId(String discordId) {
        if (repository == null) {
            return Optional.empty();
        }
        try {
            return repository.findByDiscordId(discordId);
        } catch (SQLException e) {
            plugin.getLogger().warning("link lookup failed: " + e.getMessage());
            return Optional.empty();
        }
    }

    public List<DiscordLink> findAll() {
        if (repository == null) {
            return List.of();
        }
        try {
            return repository.findAll();
        } catch (SQLException e) {
            plugin.getLogger().warning("link list failed: " + e.getMessage());
            return List.of();
        }
    }

    /** Deletes by Minecraft UUID. Safe to call off the server thread (SQL only). */
    public UnlinkResult unlinkByMcUuid(UUID mcUuid) {
        if (repository == null) {
            return UnlinkResult.fail("Account linking is not available.");
        }
        if (mcUuid == null) {
            return UnlinkResult.fail("Missing Minecraft UUID.");
        }
        try {
            Optional<DiscordLink> existing = repository.findByMcUuid(mcUuid);
            if (existing.isEmpty()) {
                return UnlinkResult.fail("Not linked.");
            }
            boolean deleted = repository.deleteByMcUuid(mcUuid);
            if (!deleted) {
                return UnlinkResult.fail("Unlink failed — try again later.");
            }
            return UnlinkResult.ok(existing.get());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "unlink by mc failed", e);
            return UnlinkResult.fail("Storage error — try again later.");
        }
    }

    /** Deletes by Discord snowflake. Safe to call off the server thread (SQL only). */
    public UnlinkResult unlinkByDiscordId(String discordId) {
        if (repository == null) {
            return UnlinkResult.fail("Account linking is not available.");
        }
        if (discordId == null || discordId.isBlank()) {
            return UnlinkResult.fail("Missing Discord id.");
        }
        try {
            Optional<DiscordLink> existing = repository.findByDiscordId(discordId);
            if (existing.isEmpty()) {
                return UnlinkResult.fail("Not linked.");
            }
            boolean deleted = repository.deleteByDiscordId(discordId);
            if (!deleted) {
                return UnlinkResult.fail("Unlink failed — try again later.");
            }
            return UnlinkResult.ok(existing.get());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "unlink by discord failed", e);
            return UnlinkResult.fail("Storage error — try again later.");
        }
    }

    /**
     * Re-applies Discord role → YaPPerms mapping for one linked account.
     * Resolves an online/offline player name when possible.
     */
    public boolean resyncRoles(DiscordLink link) {
        if (link == null) {
            return false;
        }
        String name = resolvePlayerName(link.mcUuid());
        if (name == null || name.isBlank()) {
            name = link.mcUuid().toString();
        }
        syncRolesIfPresent(link.mcUuid(), name, link.discordId());
        return true;
    }

    /** Best-effort name for role sync / status display. Call from a Bukkit thread when possible. */
    public static String resolvePlayerName(UUID mcUuid) {
        if (mcUuid == null) {
            return null;
        }
        Player online = Bukkit.getPlayer(mcUuid);
        if (online != null) {
            return online.getName();
        }
        var offline = Bukkit.getOfflinePlayer(mcUuid);
        String name = offline.getName();
        return name == null || name.isBlank() ? null : name;
    }

    /**
     * Completes pairing from a Discord-side code submission.
     * Callbacks that touch Bukkit must already run on YapSched (caller schedules).
     */
    public LinkResult completeLink(String rawCode, String discordId) {
        if (repository == null) {
            return LinkResult.fail("Account linking is not available.");
        }
        if (discordId == null || discordId.isBlank()) {
            return LinkResult.fail("Missing Discord id.");
        }
        String code = normalizeCode(rawCode);
        if (code.isEmpty()) {
            return LinkResult.fail("Invalid code.");
        }
        purgeExpiredPending();
        PendingCode pending = pendingByCode.remove(code);
        if (pending == null || pending.expiresAtMs() < System.currentTimeMillis()) {
            return LinkResult.fail("Code expired or unknown. Run `/discord link` in-game for a new code.");
        }
        try {
            Optional<DiscordLink> existingDiscord = repository.findByDiscordId(discordId);
            if (existingDiscord.isPresent()
                    && !existingDiscord.get().mcUuid().equals(pending.mcUuid())) {
                return LinkResult.fail("This Discord account is already linked to another Minecraft player.");
            }
            DiscordLink link = new DiscordLink(pending.mcUuid(), discordId,
                    System.currentTimeMillis(), true);
            repository.upsert(link);
            syncRolesIfPresent(pending.mcUuid(), pending.playerName(), discordId);
            syncNicknameIfPresent(pending.mcUuid(), pending.playerName(), discordId);
            notifyPlayerLinked(pending.mcUuid(), discordId);
            return LinkResult.ok(pending.playerName(), pending.mcUuid());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "link complete failed", e);
            return LinkResult.fail("Storage error — try again later.");
        }
    }

    private void notifyPlayerLinked(UUID mcUuid, String discordId) {
        YapSched.global(plugin, () -> {
            Player player = Bukkit.getPlayer(mcUuid);
            if (player != null && player.isOnline()) {
                player.sendMessage("§aDiscord linked §7(id §f" + discordId + "§7).");
            }
        });
    }

    /**
     * When YaPPerms is loaded, map Discord role ids → groups via console {@code yapperm user … parent add}.
     * Config is always stored; sync is a no-op without YaPPerms.
     */
    public void syncRolesIfPresent(UUID mcUuid, String playerName, String discordId) {
        DiscordConfig config = plugin.config();
        if (config == null || !config.roleSyncEnabled() || config.roleSyncMap().isEmpty()) {
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("YaPPerms") == null
                || !Bukkit.getPluginManager().isPluginEnabled("YaPPerms")) {
            plugin.getLogger().fine("Role sync skipped — YaPPerms not present (maps remain in config).");
            return;
        }
        var bot = plugin.bot();
        if (bot == null || !bot.isConnected()) {
            plugin.getLogger().fine("Role sync deferred — bot not connected.");
            return;
        }
        bot.fetchMemberRoleIds(discordId, roleIds -> {
            if (roleIds == null || roleIds.isEmpty()) {
                return;
            }
            Map<String, String> map = config.roleSyncMap();
            YapSched.global(plugin, () -> {
                for (String roleId : roleIds) {
                    String group = map.get(roleId);
                    if (group == null || group.isBlank()) {
                        continue;
                    }
                    String g = group.toLowerCase(Locale.ROOT);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                            "yapperm user " + playerName + " parent add " + g);
                    plugin.getLogger().info("Role sync: " + playerName + " ← Discord role "
                            + roleId + " → group " + g);
                }
            });
        });
    }

    /**
     * Optional nickname sync (config {@code link.nickname-sync}). Requires bot + Manage Nicknames
     * for mc→Discord; Discord→MC only applies while the player is online.
     */
    public void syncNicknameIfPresent(UUID mcUuid, String playerName, String discordId) {
        DiscordConfig config = plugin.config();
        if (config == null || !config.nicknameSyncEnabled()) {
            return;
        }
        var bot = plugin.bot();
        if (bot == null || !bot.isConnected()) {
            plugin.getLogger().fine("Nickname sync deferred — bot not connected.");
            return;
        }
        String direction = config.nicknameSyncDirection();
        if ("discord-to-mc".equals(direction)) {
            bot.fetchMemberNickname(discordId, nick -> {
                String apply = nick == null || nick.isBlank() ? null : nick;
                if (apply == null) {
                    return;
                }
                YapSched.global(plugin, () -> {
                    Player player = Bukkit.getPlayer(mcUuid);
                    if (player == null || !player.isOnline()) {
                        return;
                    }
                    player.setDisplayName(apply);
                    player.setPlayerListName(apply.length() > 16 ? apply.substring(0, 16) : apply);
                    plugin.getLogger().fine("Nickname sync discord→mc: " + player.getName() + " → " + apply);
                });
            });
            return;
        }
        // default: mc-to-discord
        String nick = playerName == null ? "" : playerName;
        if (nick.isBlank()) {
            Player online = Bukkit.getPlayer(mcUuid);
            if (online != null) {
                nick = online.getName();
            }
        }
        if (nick.isBlank()) {
            return;
        }
        bot.modifyMemberNickname(discordId, nick);
    }

    /** Periodic pass for online linked players (interval from config; 0 disables). */
    public void syncNicknamesForOnlinePlayers() {
        DiscordConfig config = plugin.config();
        if (config == null || !config.nicknameSyncEnabled() || repository == null) {
            return;
        }
        var bot = plugin.bot();
        if (bot == null || !bot.isConnected()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                Optional<DiscordLink> link = repository.findByMcUuid(player.getUniqueId());
                if (link.isEmpty()) {
                    continue;
                }
                syncNicknameIfPresent(player.getUniqueId(), player.getName(), link.get().discordId());
            } catch (SQLException e) {
                plugin.getLogger().fine("nickname sync lookup: " + e.getMessage());
            }
        }
    }

    private void purgeExpiredPending() {
        long now = System.currentTimeMillis();
        pendingByCode.entrySet().removeIf(e -> e.getValue().expiresAtMs() < now);
    }

    private String randomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]);
        }
        return sb.toString();
    }

    public static String normalizeCode(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private record PendingCode(UUID mcUuid, String playerName, long expiresAtMs) {
    }

    public record LinkResult(boolean success, String message, String playerName, UUID mcUuid) {
        static LinkResult ok(String playerName, UUID mcUuid) {
            return new LinkResult(true, "Linked to **" + playerName + "**.", playerName, mcUuid);
        }

        static LinkResult fail(String message) {
            return new LinkResult(false, message, null, null);
        }
    }

    public record UnlinkResult(boolean success, String message, DiscordLink removed) {
        static UnlinkResult ok(DiscordLink removed) {
            return new UnlinkResult(true, "Unlinked.", removed);
        }

        static UnlinkResult fail(String message) {
            return new UnlinkResult(false, message, null);
        }
    }
}
