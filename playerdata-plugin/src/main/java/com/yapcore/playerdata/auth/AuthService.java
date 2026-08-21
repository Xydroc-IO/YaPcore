package com.yapcore.playerdata.auth;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.yapcore.playerdata.PlayerDataConfig;
import com.yapcore.playerdata.db.AuthRepository;
import com.yapcore.playerdata.sync.SyncService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Offline password auth (AuthMe-class): register / login / changepassword.
 */
public final class AuthService {

    private final JavaPlugin plugin;
    private final PlayerDataConfig config;
    private final AuthRepository repo;
    private SyncService sync;

    private final Set<UUID> authenticated = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> loginAttempts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> joinAt = new ConcurrentHashMap<>();

    public AuthService(JavaPlugin plugin, PlayerDataConfig config, AuthRepository repo) {
        this.plugin = plugin;
        this.config = config;
        this.repo = repo;
    }

    public void bindSync(SyncService sync) {
        this.sync = sync;
    }

    /**
     * Whether password auth is active on this server right now.
     * <ul>
     *   <li>{@code auth.enabled=false} → off</li>
     *   <li>{@code auth.force=true} → always on</li>
     *   <li>else skip when {@code online-mode=true} or {@code auth.trust-velocity=true}</li>
     * </ul>
     */
    public boolean isActive() {
        if (!config.authEnabled()) {
            return false;
        }
        if (config.authForce()) {
            return true;
        }
        if (Bukkit.getOnlineMode()) {
            return false;
        }
        return !config.authTrustVelocity();
    }

    public boolean isAuthenticated(UUID uuid) {
        if (!isActive()) {
            return true;
        }
        return authenticated.contains(uuid);
    }

    public boolean needsAuth(Player player) {
        return isActive() && !isAuthenticated(player.getUniqueId());
    }

    public void onJoin(Player player) {
        if (!isActive()) {
            authenticated.add(player.getUniqueId());
            return;
        }
        authenticated.remove(player.getUniqueId());
        loginAttempts.put(player.getUniqueId(), 0);
        joinAt.put(player.getUniqueId(), System.currentTimeMillis());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                boolean registered = repo.findByUuid(player.getUniqueId()).isPresent();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (registered) {
                        player.sendMessage("§ePlease §f/login <password>");
                    } else {
                        player.sendMessage("§ePlease §f/register <password> <password>");
                    }
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "auth join lookup", e);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.kick(net.kyori.adventure.text.Component.text("Auth database error."));
                    }
                });
            }
        });

        int timeout = config.authTimeoutSeconds();
        if (timeout > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && needsAuth(player)) {
                    player.kick(net.kyori.adventure.text.Component.text(
                            "Login timed out. Please reconnect and /login."));
                }
            }, timeout * 20L);
        }
    }

    public void onQuit(UUID uuid) {
        authenticated.remove(uuid);
        loginAttempts.remove(uuid);
        joinAt.remove(uuid);
    }

    public String register(Player player, String pass, String confirm) {
        if (!isActive()) {
            return "§cPassword auth is disabled on this server.";
        }
        if (isAuthenticated(player.getUniqueId())) {
            return "§cYou are already logged in.";
        }
        if (pass == null || pass.length() < config.authMinPasswordLength()) {
            return "§cPassword must be at least " + config.authMinPasswordLength() + " characters.";
        }
        if (pass.length() > 64) {
            return "§cPassword too long.";
        }
        if (!pass.equals(confirm)) {
            return "§cPasswords do not match.";
        }
        try {
            if (repo.findByUuid(player.getUniqueId()).isPresent()) {
                return "§cAlready registered. Use §f/login <password>";
            }
            String hash = BCrypt.withDefaults().hashToString(12, pass.toCharArray());
            String ip = player.getAddress() != null
                    ? player.getAddress().getAddress().getHostAddress() : null;
            repo.create(player.getUniqueId(), player.getName(), hash, ip);
            markLoggedIn(player);
            return "§aRegistered and logged in.";
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "register", e);
            return "§cRegistration failed (database).";
        }
    }

    public String login(Player player, String pass) {
        if (!isActive()) {
            return "§cPassword auth is disabled on this server.";
        }
        if (isAuthenticated(player.getUniqueId())) {
            return "§cYou are already logged in.";
        }
        try {
            var opt = repo.findByUuid(player.getUniqueId());
            if (opt.isEmpty()) {
                return "§cNot registered. Use §f/register <password> <password>";
            }
            BCrypt.Result result = BCrypt.verifyer().verify(pass.toCharArray(), opt.get().passwordHash());
            if (!result.verified) {
                int attempts = loginAttempts.merge(player.getUniqueId(), 1, Integer::sum);
                if (attempts >= config.authMaxAttempts()) {
                    Bukkit.getScheduler().runTask(plugin, () -> player.kick(
                            net.kyori.adventure.text.Component.text("Too many failed login attempts.")));
                    return "§cToo many failed attempts.";
                }
                return "§cWrong password. (" + attempts + "/" + config.authMaxAttempts() + ")";
            }
            String ip = player.getAddress() != null
                    ? player.getAddress().getAddress().getHostAddress() : null;
            repo.touchLogin(player.getUniqueId(), ip);
            markLoggedIn(player);
            return "§aLogged in.";
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "login", e);
            return "§cLogin failed (database).";
        }
    }

    public String changePassword(Player player, String oldPass, String newPass) {
        if (!isActive()) {
            return "§cPassword auth is disabled.";
        }
        if (!isAuthenticated(player.getUniqueId())) {
            return "§cYou must /login first.";
        }
        if (newPass == null || newPass.length() < config.authMinPasswordLength()) {
            return "§cNew password too short.";
        }
        try {
            var opt = repo.findByUuid(player.getUniqueId());
            if (opt.isEmpty()) {
                return "§cNot registered.";
            }
            if (!BCrypt.verifyer().verify(oldPass.toCharArray(), opt.get().passwordHash()).verified) {
                return "§cCurrent password incorrect.";
            }
            String hash = BCrypt.withDefaults().hashToString(12, newPass.toCharArray());
            repo.updatePassword(player.getUniqueId(), hash);
            return "§aPassword changed.";
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "changepassword", e);
            return "§cFailed to change password.";
        }
    }

    public String unregister(UUID uuid) {
        try {
            if (repo.delete(uuid)) {
                authenticated.remove(uuid);
                return "§aUnregistered.";
            }
            return "§cNo auth account.";
        } catch (SQLException e) {
            return "§cDatabase error.";
        }
    }

    public void logout(Player player) {
        authenticated.remove(player.getUniqueId());
        if (sync != null) {
            sync.revokeReady(player.getUniqueId());
        }
        player.getInventory().clear();
        player.sendMessage("§eLogged out. Use §f/login <password>");
    }

    private void markLoggedIn(Player player) {
        authenticated.add(player.getUniqueId());
        loginAttempts.remove(player.getUniqueId());
        if (sync != null) {
            sync.completeAfterAuth(player);
        }
    }

    public boolean isAllowedCommand(String label) {
        String l = label.toLowerCase(Locale.ROOT);
        if (l.contains(":")) {
            l = l.substring(l.indexOf(':') + 1);
        }
        return switch (l) {
            case "login", "l", "register", "reg", "changepassword", "changepass", "cp",
                 "logout", "unregister" -> true;
            default -> false;
        };
    }
}
