package me.clip.placeholderapi.command;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.util.Msg;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Full local /papi command tree (parse, bcparse, cmdparse, parserel, dump, list, …).
 * Clean-room — not GPL PlaceholderAPI command sources.
 */
public final class PapiCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ROOT = List.of(
            "help", "parse", "bcparse", "cmdparse", "parserel",
            "dump", "list", "info", "reload", "register", "unregister", "version", "ecloud");

    private final PlaceholderAPIPlugin plugin;

    public PapiCommand(PlaceholderAPIPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "help", "?" -> {
                help(sender);
                yield true;
            }
            case "parse" -> parse(sender, args, false, false);
            case "bcparse" -> parse(sender, args, true, false);
            case "cmdparse" -> parse(sender, args, false, true);
            case "parserel" -> parseRel(sender, args);
            case "dump" -> dump(sender);
            case "list" -> list(sender);
            case "info" -> info(sender, args);
            case "reload" -> reload(sender);
            case "register" -> register(sender, args);
            case "unregister" -> unregister(sender, args);
            case "version", "ver" -> version(sender);
            case "ecloud" -> {
                Msg.msg(sender,
                        "&6YaP PlaceholderAPI &7uses &flocal expansions&7, not HelpChat eCloud.",
                        "&7Built-in: &fplayer&7, &fserver&7 · YaP plugins register &fyap*&7 placeholders automatically.",
                        "&7Third-party jars → &fplugins/PlaceholderAPI/expansions/",
                        "&7Then: &e/papi reload &7or &e/papi register <jar>",
                        "&7Docs: &fdocs/plugins/PLACEHOLDERAPI.md");
                yield true;
            }
            default -> {
                Msg.msg(sender, "&cUnknown subcommand. &7/papi help");
                yield true;
            }
        };
    }

    private void help(CommandSender sender) {
        Msg.msg(sender,
                "&6YaP PlaceholderAPI &7" + plugin.getDescription().getVersion(),
                "&e/papi parse &7<me|--null|player> <text…>",
                "&e/papi bcparse &7<target> <text…> &8— broadcast parsed text",
                "&e/papi cmdparse &7<target> <text…> &8— run as command",
                "&e/papi parserel &7<p1> <p2> <text…>",
                "&e/papi dump &8— write diagnostics (+ optional paste)",
                "&e/papi list|info [id]|reload|register <jar>|unregister <id>|version",
                "&e/papi ecloud &8— local expansions path (no HelpChat eCloud)");
    }

    private boolean parse(CommandSender sender, String[] args, boolean broadcast, boolean command) {
        if (!sender.hasPermission("placeholderapi.parse")
                && !sender.hasPermission("placeholderapi.admin")) {
            Msg.msg(sender, "&cNo permission.");
            return true;
        }
        if (args.length < 3) {
            Msg.msg(sender, "&cUsage: &e/papi " + args[0] + " &7{me|--null|player} &a{message}");
            return true;
        }

        OfflinePlayer player = resolveTarget(sender, args[1]);
        if (player == null && !"--null".equalsIgnoreCase(args[1])) {
            if ("me".equalsIgnoreCase(args[1])) {
                Msg.msg(sender, "&cYou must be a player to use &7me&c as a target!");
            } else {
                Msg.msg(sender, "&cFailed to find player: &7" + args[1]);
            }
            return true;
        }
        if ("--null".equalsIgnoreCase(args[1])) {
            player = null;
        }

        String message = PlaceholderAPI.setPlaceholders(
                player, String.join(" ", Arrays.copyOfRange(args, 2, args.length)));

        if (command) {
            Bukkit.dispatchCommand(sender, message);
            return true;
        }
        if (broadcast) {
            Bukkit.broadcastMessage(message);
        } else {
            sender.sendMessage(message);
        }
        return true;
    }

    private boolean parseRel(CommandSender sender, String[] args) {
        if (!sender.hasPermission("placeholderapi.parse")
                && !sender.hasPermission("placeholderapi.admin")) {
            Msg.msg(sender, "&cNo permission.");
            return true;
        }
        if (args.length < 4) {
            Msg.msg(sender, "&cUsage: &e/papi parserel &7{p1} {p2} &a{message}");
            return true;
        }
        OfflinePlayer one = resolveTarget(sender, args[1]);
        OfflinePlayer two = resolveTarget(sender, args[2]);
        if (one == null || !one.isOnline() || two == null || !two.isOnline()) {
            Msg.msg(sender, "&cBoth targets must be online players.");
            return true;
        }
        String message = PlaceholderAPI.setRelationalPlaceholders(
                one.getPlayer(), two.getPlayer(),
                String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
        sender.sendMessage(message);
        return true;
    }

    private boolean dump(CommandSender sender) {
        if (!sender.hasPermission("placeholderapi.admin")) {
            Msg.msg(sender, "&cNo permission.");
            return true;
        }
        String body = makeDump();
        Path dir = plugin.getDataFolder().toPath().resolve("dumps");
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve("dump-" + Instant.now().getEpochSecond() + ".txt");
            Files.writeString(file, body, StandardCharsets.UTF_8);
            Msg.msg(sender, "&aDump written: &f" + file.toAbsolutePath());
        } catch (IOException e) {
            Msg.msg(sender, "&cFailed to write dump: " + e.getMessage());
        }

        if (plugin.getConfig().getBoolean("dump-paste", true)) {
            Msg.msg(sender, "&7Uploading dump…");
            CompletableFuture.supplyAsync(() -> paste(body)).whenComplete((key, err) -> {
                YapSched.global(plugin, () -> {
                    if (err != null || key == null) {
                        Msg.msg(sender, "&cPaste upload failed (local dump still saved).");
                    } else {
                        Msg.msg(sender, "&aPaste: &fhttps://paste.helpch.at/" + key);
                    }
                });
            });
        }
        return true;
    }

    @Nullable
    private static String paste(String body) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create("https://paste.helpch.at/documents")
                    .toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
            String json = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int idx = json.indexOf("\"key\"");
            if (idx < 0) {
                return null;
            }
            int start = json.indexOf('"', idx + 5) + 1;
            int end = json.indexOf('"', start);
            return end > start ? json.substring(start, end) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String makeDump() {
        StringBuilder b = new StringBuilder();
        b.append("Generated: ").append(Instant.now()).append("\n\n");
        b.append("PlaceholderAPI: ").append(plugin.getDescription().getVersion()).append(" (YaP)\n\n");
        b.append("Expansions Registered:\n");
        List<PlaceholderExpansion> expansions = plugin.getLocalExpansionManager().getExpansions().stream()
                .sorted(Comparator.comparing(PlaceholderExpansion::getIdentifier))
                .toList();
        for (PlaceholderExpansion expansion : expansions) {
            b.append("  ").append(expansion.getIdentifier())
                    .append(" [Author: ").append(expansion.getAuthor())
                    .append(", Version: ").append(expansion.getVersion()).append("]\n");
        }
        b.append("\nExpansions Directory:\n");
        String[] jars = plugin.getLocalExpansionManager().getExpansionsFolder()
                .list((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (jars != null) {
            for (String jar : jars) {
                b.append("  ").append(jar).append('\n');
            }
        }
        b.append("\nServer Info: ").append(Bukkit.getBukkitVersion())
                .append('/').append(Bukkit.getVersion()).append('\n');
        b.append("Java Version: ").append(System.getProperty("java.version")).append("\n\n");
        b.append("Plugin Info:\n");
        for (Plugin other : Arrays.stream(Bukkit.getPluginManager().getPlugins())
                .sorted(Comparator.comparing(Plugin::getName)).toList()) {
            b.append("  ").append(other.getName())
                    .append(" [Version: ").append(other.getDescription().getVersion()).append("]\n");
        }
        return b.toString();
    }

    private boolean list(CommandSender sender) {
        if (!sender.hasPermission("placeholderapi.admin")) {
            Msg.msg(sender, "&cNo permission.");
            return true;
        }
        var ids = plugin.getLocalExpansionManager().getIdentifiers();
        Msg.msg(sender, "&aExpansions (&f" + ids.size() + "&a): &7" + String.join(", ", ids));
        return true;
    }

    private boolean info(CommandSender sender, String[] args) {
        if (!sender.hasPermission("placeholderapi.admin")) {
            Msg.msg(sender, "&cNo permission.");
            return true;
        }
        if (args.length < 2) {
            Msg.msg(sender, "&6YaP PlaceholderAPI &f" + plugin.getDescription().getVersion()
                    + " &7expansions=" + plugin.getLocalExpansionManager().getIdentifiers().size());
            return true;
        }
        plugin.getLocalExpansionManager().findExpansionByIdentifier(args[1]).ifPresentOrElse(
                ex -> Msg.msg(sender,
                        "&a" + ex.getIdentifier() + " &7v" + ex.getVersion() + " by " + ex.getAuthor(),
                        "&7type=" + ex.getExpansionType() + " persist=" + ex.persist(),
                        "&7placeholders: &f" + String.join(", ", ex.getPlaceholders())),
                () -> Msg.msg(sender, "&cUnknown expansion: &7" + args[1]));
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("placeholderapi.admin")) {
            Msg.msg(sender, "&cNo permission.");
            return true;
        }
        plugin.reloadConf(sender);
        Msg.msg(sender, "&aPlaceholderAPI reloaded.");
        return true;
    }

    private boolean register(CommandSender sender, String[] args) {
        if (!sender.hasPermission("placeholderapi.admin")) {
            Msg.msg(sender, "&cNo permission.");
            return true;
        }
        if (args.length < 2) {
            Msg.msg(sender, "&cUsage: &e/papi register &7<jar-name-in-expansions>");
            return true;
        }
        Path jar = plugin.getLocalExpansionManager().getExpansionsFolder().toPath().resolve(args[1]);
        if (!Files.isRegularFile(jar)) {
            if (!args[1].endsWith(".jar")) {
                jar = plugin.getLocalExpansionManager().getExpansionsFolder().toPath().resolve(args[1] + ".jar");
            }
        }
        if (!Files.isRegularFile(jar)) {
            Msg.msg(sender, "&cJar not found in expansions folder: &7" + args[1]);
            return true;
        }
        plugin.getLocalExpansionManager().load(sender);
        Msg.msg(sender, "&aRequested reload of expansions folder (includes &f" + jar.getFileName() + "&a).");
        return true;
    }

    private boolean unregister(CommandSender sender, String[] args) {
        if (!sender.hasPermission("placeholderapi.admin")) {
            Msg.msg(sender, "&cNo permission.");
            return true;
        }
        if (args.length < 2) {
            Msg.msg(sender, "&cUsage: &e/papi unregister &7<identifier>");
            return true;
        }
        boolean ok = plugin.getLocalExpansionManager()
                .findExpansionByIdentifier(args[1])
                .map(PlaceholderExpansion::unregister)
                .orElse(false);
        Msg.msg(sender, ok ? "&aUnregistered &f" + args[1] : "&cNot registered: &7" + args[1]);
        return true;
    }

    private boolean version(CommandSender sender) {
        Msg.msg(sender,
                "&6YaP PlaceholderAPI &f" + plugin.getDescription().getVersion(),
                "&7Clip-compatible engine · Paper " + Bukkit.getBukkitVersion());
        return true;
    }

    @Nullable
    private OfflinePlayer resolveTarget(CommandSender sender, String name) {
        if ("me".equalsIgnoreCase(name)) {
            return sender instanceof Player player ? player : null;
        }
        if ("--null".equalsIgnoreCase(name)) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() || offline.isOnline() ? offline : null;
    }

    @Override
    @Nullable
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {
        if (args.length == 1) {
            return filter(ROOT, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "parse", "bcparse", "cmdparse" -> {
                    List<String> names = Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .collect(Collectors.toCollection(ArrayList::new));
                    names.add("me");
                    names.add("--null");
                    yield filter(names, args[1]);
                }
                case "parserel" -> filter(Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName).toList(), args[1]);
                case "info", "unregister" -> filter(
                        new ArrayList<>(plugin.getLocalExpansionManager().getIdentifiers()), args[1]);
                case "register" -> {
                    String[] jars = plugin.getLocalExpansionManager().getExpansionsFolder()
                            .list((d, n) -> n.endsWith(".jar"));
                    yield filter(jars == null ? List.of() : Arrays.asList(jars), args[1]);
                }
                default -> List.of();
            };
        }
        if (args.length == 3 && sub.equals("parserel")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
