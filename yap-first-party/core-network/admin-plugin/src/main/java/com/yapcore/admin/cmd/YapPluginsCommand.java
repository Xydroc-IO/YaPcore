package com.yapcore.admin.cmd;

import com.yapcore.admin.AdminPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * In-game mirror of dashboard plugin soft/hard control.
 * Soft = YAML {@code enabled}; hard = {@code .jar.disabled} rename (restart to apply).
 */
public final class YapPluginsCommand implements CommandExecutor, TabCompleter {

    private static final Set<String> CORE = Set.of(
            "yap-db", "yap-folia-bridge", "yap-perms", "yap-playerdata", "yap-essentials",
            "yap-chat", "yap-moderation", "yap-protect", "yap-admin");

    private record SoftSpec(String dataDir, String file, String key, String reload) {
    }

    private static final Map<String, SoftSpec> SOFT = buildSoft();

    private final AdminPlugin plugin;

    public YapPluginsCommand(AdminPlugin plugin) {
        this.plugin = plugin;
    }

    private Path pluginsDir() {
        return plugin.getDataFolder().toPath().getParent();
    }

    private Path serverRoot() {
        return pluginsDir().getParent();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yapadmin.plugins")) {
            sender.sendMessage("§cNo permission (yapadmin.plugins).");
            return true;
        }
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sender.sendMessage("§e/yapplugins list");
            sender.sendMessage("§e/yapplugins enable|disable <id> [soft|hard] [--force]");
            sender.sendMessage("§e/yapplugins install <path-under-server>");
            sender.sendMessage("§e/yapplugins uninstall <id> [--force]");
            sender.sendMessage("§7Soft = config enabled; hard = .jar.disabled (Folia restart).");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        try {
            switch (sub) {
                case "list" -> list(sender);
                case "enable", "disable" -> {
                    if (args.length < 2) {
                        sender.sendMessage("§cUsage: /yapplugins " + sub + " <id> [soft|hard] [--force]");
                        return true;
                    }
                    boolean enable = "enable".equals(sub);
                    String mode = args.length >= 3 && !args[2].startsWith("-")
                            ? args[2].toLowerCase(Locale.ROOT) : "soft";
                    boolean force = hasFlag(args, "--force");
                    toggle(sender, args[1], enable, mode, force);
                }
                case "install" -> {
                    if (args.length < 2) {
                        sender.sendMessage("§cUsage: /yapplugins install <path>");
                        return true;
                    }
                    install(sender, args[1]);
                }
                case "uninstall" -> {
                    if (args.length < 2) {
                        sender.sendMessage("§cUsage: /yapplugins uninstall <id> [--force]");
                        return true;
                    }
                    uninstall(sender, args[1], hasFlag(args, "--force"));
                }
                default -> sender.sendMessage("§cUnknown subcommand. Try /yapplugins help");
            }
        } catch (Exception e) {
            sender.sendMessage("§c" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
        return true;
    }

    private void list(CommandSender sender) throws IOException {
        Path dir = pluginsDir();
        if (!Files.isDirectory(dir)) {
            sender.sendMessage("§cNo plugins directory.");
            return;
        }
        sender.sendMessage("§6Plugins (soft=YAML enabled, hard=jar present):");
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> jars = stream
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.endsWith(".jar") || n.endsWith(".jar.disabled");
                    })
                    .sorted((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()))
                    .collect(Collectors.toList());
            for (Path jar : jars) {
                String name = jar.getFileName().toString();
                boolean hard = !name.toLowerCase(Locale.ROOT).endsWith(".jar.disabled");
                String active = hard ? name : name.substring(0, name.length() - ".disabled".length());
                String token = jarToken(active);
                SoftSpec soft = findSoft(token, active);
                String softLabel = "n/a";
                if (soft != null) {
                    Boolean v = readSoft(soft);
                    softLabel = v == null ? "?" : (v ? "on" : "off");
                }
                String tier = CORE.contains(token) ? "CORE" : (soft != null ? "YAP" : "3P");
                sender.sendMessage("§7- §f" + token + " §8[" + tier + "] §7soft=" + softLabel
                        + " hard=" + (hard ? "on" : "off") + " §8(" + name + ")");
            }
        }
    }

    private void toggle(CommandSender sender, String id, boolean enable, String mode, boolean force)
            throws IOException {
        Path jar = resolveJar(id);
        String name = jar.getFileName().toString();
        boolean hardOn = !name.toLowerCase(Locale.ROOT).endsWith(".jar.disabled");
        String active = hardOn ? name : name.substring(0, name.length() - ".disabled".length());
        String token = jarToken(active);
        if (CORE.contains(token) && !enable && !force) {
            throw new IOException("CORE plugin requires --force to disable");
        }
        if ("hard".equals(mode)) {
            Path next = hardToggle(jar, enable);
            sender.sendMessage("§aHard " + (enable ? "enabled" : "disabled") + ": " + next.getFileName()
                    + " §7(Folia restart required)");
            return;
        }
        SoftSpec soft = findSoft(token, active);
        if (soft == null) {
            throw new IOException("Soft toggle not supported for " + token + "; use hard");
        }
        writeSoft(soft, enable);
        sender.sendMessage("§aSoft " + (enable ? "enabled" : "disabled") + ": " + token
                + " (" + soft.dataDir + "/" + soft.file + " " + soft.key + ")");
        if (soft.reload != null && !soft.reload.isBlank()) {
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), soft.reload);
            sender.sendMessage("§7Ran: " + soft.reload);
        } else {
            sender.sendMessage("§7No reload command — Folia restart may be needed for full effect.");
        }
    }

    private void install(CommandSender sender, String rawPath) throws IOException {
        Path src = Path.of(rawPath);
        if (!src.isAbsolute()) {
            src = serverRoot().resolve(rawPath).normalize();
        } else {
            src = src.toAbsolutePath().normalize();
        }
        Path root = serverRoot().toAbsolutePath().normalize();
        if (!src.startsWith(root)) {
            throw new IOException("Install path must be under server root (" + root + ")");
        }
        if (!Files.isRegularFile(src)) {
            throw new IOException("Not a file: " + src);
        }
        String name = src.getFileName().toString();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            throw new IOException("Must be a .jar");
        }
        Path dest = pluginsDir().resolve(name);
        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
        sender.sendMessage("§aInstalled " + name + " §7(Folia restart to load)");
    }

    private void uninstall(CommandSender sender, String id, boolean force) throws IOException {
        Path jar = resolveJar(id);
        String name = jar.getFileName().toString();
        String active = name.toLowerCase(Locale.ROOT).endsWith(".jar.disabled")
                ? name.substring(0, name.length() - ".disabled".length()) : name;
        String token = jarToken(active);
        if (CORE.contains(token) && !force) {
            throw new IOException("CORE uninstall requires --force");
        }
        Files.delete(jar);
        sender.sendMessage("§aUninstalled " + name + " §7(Folia restart to unload)");
    }

    private Path resolveJar(String id) throws IOException {
        Path dir = pluginsDir();
        String want = id.trim();
        if (want.contains("..") || want.contains("/") || want.contains("\\")) {
            throw new IOException("Invalid id");
        }
        Path exact = dir.resolve(want);
        if (Files.isRegularFile(exact)) {
            return exact;
        }
        if (Files.isRegularFile(dir.resolve(want + ".jar"))) {
            return dir.resolve(want + ".jar");
        }
        if (Files.isRegularFile(dir.resolve(want + ".jar.disabled"))) {
            return dir.resolve(want + ".jar.disabled");
        }
        String token = jarToken(want.endsWith(".jar") ? want : want + ".jar");
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path p : stream.collect(Collectors.toList())) {
                String n = p.getFileName().toString();
                String lower = n.toLowerCase(Locale.ROOT);
                if (!(lower.endsWith(".jar") || lower.endsWith(".jar.disabled"))) {
                    continue;
                }
                if (jarToken(n).equals(token) || lower.startsWith(token + "-") || lower.startsWith(token + ".")) {
                    return p;
                }
            }
        }
        throw new IOException("Plugin not found: " + id);
    }

    private Path hardToggle(Path jarPath, boolean enable) throws IOException {
        String name = jarPath.getFileName().toString();
        boolean disabled = name.toLowerCase(Locale.ROOT).endsWith(".jar.disabled");
        Path dir = pluginsDir();
        if (enable) {
            if (!disabled) {
                return jarPath;
            }
            String live = name.substring(0, name.length() - ".disabled".length());
            Path dest = dir.resolve(live);
            if (Files.exists(dest)) {
                throw new IOException("Cannot enable: " + live + " already exists");
            }
            move(jarPath, dest);
            return dest;
        }
        if (disabled) {
            return jarPath;
        }
        Path dest = dir.resolve(name + ".disabled");
        if (Files.exists(dest)) {
            throw new IOException("Cannot disable: already disabled");
        }
        move(jarPath, dest);
        return dest;
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Boolean readSoft(SoftSpec soft) {
        Path file = pluginsDir().resolve(soft.dataDir).resolve(soft.file);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        if (!yaml.contains(soft.key)) {
            return null;
        }
        return yaml.getBoolean(soft.key);
    }

    private void writeSoft(SoftSpec soft, boolean enable) throws IOException {
        Path file = pluginsDir().resolve(soft.dataDir).resolve(soft.file);
        Files.createDirectories(file.getParent());
        YamlConfiguration yaml = Files.isRegularFile(file)
                ? YamlConfiguration.loadConfiguration(file.toFile())
                : new YamlConfiguration();
        yaml.set(soft.key, enable);
        yaml.save(file.toFile());
    }

    private static SoftSpec findSoft(String token, String activeName) {
        SoftSpec direct = SOFT.get(token);
        if (direct != null) {
            return direct;
        }
        String lower = activeName.toLowerCase(Locale.ROOT);
        SoftSpec best = null;
        int bestLen = -1;
        for (var e : SOFT.entrySet()) {
            String jt = e.getKey();
            if (lower.startsWith(jt + "-") || lower.startsWith(jt + ".") || lower.equals(jt + ".jar")
                    || lower.equals(jt + ".jar.disabled")) {
                if (jt.length() > bestLen) {
                    best = e.getValue();
                    bestLen = jt.length();
                }
            }
        }
        return best;
    }

    static String jarToken(String fileName) {
        String n = fileName.toLowerCase(Locale.ROOT);
        if (n.endsWith(".jar.disabled")) {
            n = n.substring(0, n.length() - ".disabled".length());
        }
        if (n.endsWith(".jar")) {
            n = n.substring(0, n.length() - 4);
        }
        while (true) {
            int dash = n.lastIndexOf('-');
            if (dash <= 0) {
                break;
            }
            String suffix = n.substring(dash + 1);
            if (suffix.matches("\\d+(?:\\.\\d+)*(?:[a-z].*)?")) {
                n = n.substring(0, dash);
            } else {
                break;
            }
        }
        return n;
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String a : args) {
            if (flag.equalsIgnoreCase(a)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("yapadmin.plugins")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(args[0], List.of("list", "enable", "disable", "install", "uninstall", "help"));
        }
        if (args.length == 2 && ("enable".equalsIgnoreCase(args[0]) || "disable".equalsIgnoreCase(args[0])
                || "uninstall".equalsIgnoreCase(args[0]))) {
            try {
                return filter(args[1], listTokens());
            } catch (IOException e) {
                return List.of();
            }
        }
        if (args.length == 3 && ("enable".equalsIgnoreCase(args[0]) || "disable".equalsIgnoreCase(args[0]))) {
            return filter(args[2], List.of("soft", "hard", "--force"));
        }
        if (args.length >= 3) {
            return filter(args[args.length - 1], List.of("--force"));
        }
        return List.of();
    }

    private List<String> listTokens() throws IOException {
        List<String> out = new ArrayList<>();
        Path dir = pluginsDir();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.forEach(p -> {
                String n = p.getFileName().toString();
                String lower = n.toLowerCase(Locale.ROOT);
                if (lower.endsWith(".jar") || lower.endsWith(".jar.disabled")) {
                    out.add(jarToken(n));
                }
            });
        }
        return out;
    }

    private static List<String> filter(String prefix, List<String> options) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(o);
            }
        }
        return out;
    }

    private static Map<String, SoftSpec> buildSoft() {
        Map<String, SoftSpec> m = new LinkedHashMap<>();
        put(m, "yap-perms", "YaPPerms", "config.yml", "enabled", "yapperm reload");
        put(m, "yap-playerdata", "YaPPlayerData", "config.yml", "enabled", "yapdata reload");
        put(m, "yap-moderation", "YaPModeration", "config.yml", "enabled", "yapmod reload");
        put(m, "yap-essentials", "YaPEssentials", "config.yml", "enabled", "yapess reload");
        put(m, "yap-admin", "YaPAdmin", "config.yml", "enabled", "");
        put(m, "yap-protect", "YaPProtect", "config.yml", "enabled", "yapprotect reload");
        put(m, "yap-world", "YaPWorld", "config.yml", "enabled", "yapworld reload");
        put(m, "yap-packs", "YaPPacks", "config.yml", "enabled", "yappacks reload");
        put(m, "yap-commands", "YaPCommands", "config.yml", "enabled", "yapcommands reload");
        put(m, "yap-chat", "YaPChat", "config.yml", "enabled", "yapchat reload");
        put(m, "yap-tab", "YaPTab", "config.yml", "enabled", "yaptab reload");
        put(m, "yap-discord", "YaPDiscord", "config.yml", "enabled", "yapdiscord reload");
        put(m, "yap-floodgate", "YaPFloodgate", "config.yml", "enabled", "yapfloodgate reload");
        put(m, "yap-folia-bridge", "YaPFoliaBridge", "config.yml", "enabled", "");
        put(m, "yap-regions", "YaPRegions", "config.yml", "enabled", "region reload");
        put(m, "yap-npcs", "YaPNpcs", "config.yml", "enabled", "npc reload");
        put(m, "yap-guard", "YaPGuard", "config.yml", "enabled", "yapguard reload");
        put(m, "yap-lagguard", "YaPLagGuard", "config.yml", "enabled", "yaplagguard reload");
        put(m, "yap-map", "YaPMap", "config.yml", "enabled", "yapmap reload");
        put(m, "yap-factions", "YaPFactions", "config.yml", "enabled", "yapfactions reload");
        put(m, "yap-db", "YaPDB", "config.yml", "enabled", "yapdb reload");
        put(m, "yap-stacker", "YaPStacker", "config.yml", "enabled", "yapstacker reload");
        put(m, "yap-gameplay-knobs", "YaPGameplayKnobs", "knobs.yml", "settings.enabled", "yapknobs reload");
        put(m, "yap-skills", "YaPSkills", "config.yml", "enabled", "yskills reload");
        put(m, "yap-disasters", "YaPDisasters", "config.yml", "enabled", "yapdisaster reload");
        return m;
    }

    private static void put(Map<String, SoftSpec> m, String token, String dir, String file, String key, String reload) {
        m.put(token, new SoftSpec(dir, file, key, reload));
    }
}
