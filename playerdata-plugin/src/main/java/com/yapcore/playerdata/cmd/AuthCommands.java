package com.yapcore.playerdata.cmd;

import com.yapcore.playerdata.auth.AuthService;
import com.yapcore.playerdata.db.AuthRepository;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /register /login /changepassword /logout /unregister
 */
public final class AuthCommands implements CommandExecutor, TabCompleter {

    private final AuthService auth;
    private final AuthRepository authRepo;

    public AuthCommands(AuthService auth, AuthRepository authRepo) {
        this.auth = auth;
        this.authRepo = authRepo;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (!(sender instanceof Player player) && !name.equals("unregister")) {
            sender.sendMessage("Players only.");
            return true;
        }
        return switch (name) {
            case "register", "reg" -> {
                if (args.length < 2) {
                    sender.sendMessage("Usage: /register <password> <password>");
                    yield true;
                }
                sender.sendMessage(auth.register((Player) sender, args[0], args[1]));
                yield true;
            }
            case "login", "l" -> {
                if (args.length < 1) {
                    sender.sendMessage("Usage: /login <password>");
                    yield true;
                }
                sender.sendMessage(auth.login((Player) sender, args[0]));
                yield true;
            }
            case "changepassword", "changepass", "cp" -> {
                if (args.length < 2) {
                    sender.sendMessage("Usage: /changepassword <old> <new>");
                    yield true;
                }
                sender.sendMessage(auth.changePassword((Player) sender, args[0], args[1]));
                yield true;
            }
            case "logout" -> {
                auth.logout((Player) sender);
                yield true;
            }
            case "unregister" -> {
                if (!sender.hasPermission("yapdata.admin")) {
                    sender.sendMessage("No permission.");
                    yield true;
                }
                if (args.length < 1) {
                    sender.sendMessage("Usage: /unregister <player|uuid>");
                    yield true;
                }
                UUID uuid = resolveUuid(args[0]);
                if (uuid == null) {
                    sender.sendMessage("§cUnknown player.");
                    yield true;
                }
                sender.sendMessage(auth.unregister(uuid));
                Player online = Bukkit.getPlayer(uuid);
                if (online != null) {
                    online.kick(net.kyori.adventure.text.Component.text("Your account was unregistered."));
                }
                yield true;
            }
            default -> false;
        };
    }

    private UUID resolveUuid(String arg) {
        try {
            return UUID.fromString(arg);
        } catch (IllegalArgumentException ignored) {
        }
        Player p = Bukkit.getPlayerExact(arg);
        if (p != null) {
            return p.getUniqueId();
        }
        try {
            return authRepo.findByUsername(arg).map(AuthRepository.Account::uuid).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("unregister") && args.length == 1 && sender.hasPermission("yapdata.admin")) {
            String p = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(p))
                    .toList();
        }
        return List.of();
    }
}
