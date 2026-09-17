package com.yapcore.admin.op;

import com.yapcore.admin.AdminPlugin;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies network-wide OP on join (chassis {@code config/network-ops.json} + local
 * {@code ops.json} name match) and listens for Link {@code yap:op} plugin messages.
 */
public final class NetworkOpSync implements Listener, PluginMessageListener {

    public static final String CHANNEL = "yap:op";
    private static final Pattern ENTRY = Pattern.compile(
            "\"uuid\"\\s*:\\s*\"([0-9a-fA-F\\-]{36})\"\\s*,\\s*\"name\"\\s*:\\s*\"([^\"]+)\"",
            Pattern.DOTALL);

    private final AdminPlugin plugin;
    private volatile Path root;

    public NetworkOpSync(AdminPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        root = findRoot(Path.of("").toAbsolutePath())
                .or(() -> findRoot(Bukkit.getWorldContainer().toPath()))
                .orElse(null);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        var messenger = plugin.getServer().getMessenger();
        messenger.registerIncomingPluginChannel(plugin, CHANNEL, this);
        messenger.registerOutgoingPluginChannel(plugin, CHANNEL);
        if (root != null) {
            plugin.getLogger().info("Network OP sync root=" + root);
        } else {
            plugin.getLogger().info("Network OP sync: using local ops.json name match");
        }
    }

    public void unregister() {
        var messenger = plugin.getServer().getMessenger();
        messenger.unregisterIncomingPluginChannel(plugin, CHANNEL, this);
        messenger.unregisterOutgoingPluginChannel(plugin, CHANNEL);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        YapSched.entity(plugin, player, () -> apply(player, shouldBeOp(player)));
    }

    @Override
    public void onPluginMessageReceived(String channel, Player carrier, byte[] message) {
        if (!CHANNEL.equals(channel) || message == null || message.length == 0) {
            return;
        }
        String text = new String(message, StandardCharsets.UTF_8).trim();
        String[] parts = text.split("\\|", 4);
        if (parts.length < 4 || !"OP".equalsIgnoreCase(parts[0])) {
            return;
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(parts[1].trim());
        } catch (IllegalArgumentException e) {
            return;
        }
        String name = parts[2].trim();
        boolean op = "1".equals(parts[3].trim()) || "true".equalsIgnoreCase(parts[3].trim());
        Player target = Bukkit.getPlayer(uuid);
        if (target == null) {
            target = Bukkit.getPlayerExact(name);
        }
        if (target == null) {
            return;
        }
        Player applyTo = target;
        YapSched.entity(plugin, applyTo, () -> apply(applyTo, op));
    }

    private boolean shouldBeOp(Player player) {
        if (root != null && networkFileHas(player.getUniqueId(), player.getName())) {
            return true;
        }
        return opsJsonHasName(player.getName());
    }

    private boolean networkFileHas(UUID uuid, String name) {
        Path file = root.resolve("config").resolve("network-ops.json");
        if (!Files.isRegularFile(file)) {
            // Also treat chassis ops= as network ops.
            return chassisOpsHasName(name);
        }
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            if (uuid != null && raw.contains(uuid.toString())) {
                return true;
            }
            return nameMatchInJson(raw, name) || chassisOpsHasName(name);
        } catch (Exception e) {
            return chassisOpsHasName(name);
        }
    }

    private boolean chassisOpsHasName(String name) {
        if (root == null || name == null) {
            return false;
        }
        Path props = root.resolve("config").resolve("server.properties");
        if (!Files.isRegularFile(props)) {
            return false;
        }
        try {
            for (String line : Files.readAllLines(props, StandardCharsets.UTF_8)) {
                String t = line.trim();
                if (t.startsWith("ops=")) {
                    String list = t.substring(4);
                    for (String part : list.split("[,\\s]+")) {
                        if (part.equalsIgnoreCase(name)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static boolean opsJsonHasName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        try {
            Path ops = Path.of("ops.json");
            if (!Files.isRegularFile(ops)) {
                ops = Bukkit.getWorldContainer().toPath().resolve("ops.json");
            }
            if (!Files.isRegularFile(ops)) {
                return false;
            }
            return nameMatchInJson(Files.readString(ops), name);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean nameMatchInJson(String raw, String name) {
        Matcher m = ENTRY.matcher(raw);
        while (m.find()) {
            if (m.group(2).equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private void apply(Player player, boolean op) {
        if (!player.isOnline() || player.isOp() == op) {
            return;
        }
        player.setOp(op);
        if (op) {
            player.sendMessage("§7Network OP enabled on this server.");
        } else {
            player.sendMessage("§7Network OP removed on this server.");
        }
    }

    static Optional<Path> findRoot(Path start) {
        Path p = start == null ? Path.of("").toAbsolutePath() : start.toAbsolutePath().normalize();
        for (int i = 0; i < 8 && p != null; i++) {
            if (Files.isRegularFile(p.resolve("config").resolve("server.properties"))) {
                return Optional.of(p);
            }
            p = p.getParent();
        }
        return Optional.empty();
    }
}
