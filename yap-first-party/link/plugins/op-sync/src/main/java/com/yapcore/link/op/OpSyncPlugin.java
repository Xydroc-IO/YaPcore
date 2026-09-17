package com.yapcore.link.op;

import com.yapcore.link.api.ChannelIdentifier;
import com.yapcore.link.api.LinkPlayer;
import com.yapcore.link.api.LinkPlugin;
import com.yapcore.link.api.LinkProxy;
import com.yapcore.link.api.RegisteredServer;
import com.yapcore.link.api.SimpleCommand;
import com.yapcore.link.api.annotation.Subscribe;
import com.yapcore.link.api.event.PostConnectEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Network OP authority on YaP Link: {@code /op} / {@code /deop} update shared
 * {@code config/network-ops.json} + chassis {@code ops=}, then push {@code yap:op}
 * to every backend so Folia applies {@code setOp}.
 */
public final class OpSyncPlugin implements LinkPlugin {

    public static final ChannelIdentifier CHANNEL = ChannelIdentifier.of("yap", "op");
    private static final Pattern ENTRY = Pattern.compile(
            "\"uuid\"\\s*:\\s*\"([0-9a-fA-F\\-]{36})\"\\s*,\\s*\"name\"\\s*:\\s*\"([^\"]+)\"",
            Pattern.DOTALL);

    private LinkProxy proxy;
    private Logger logger;
    private Path root;

    @Override
    public void onLoad(LinkPluginContext context) {
        this.proxy = context.proxy();
        this.logger = context.logger();
        Path home = context.proxy().home();
        this.root = home.getParent() != null ? home.getParent() : home;
        // link-data → repo root; also try home itself
        if (!Files.isRegularFile(root.resolve("config").resolve("server.properties"))
                && Files.isRegularFile(home.resolve("config").resolve("server.properties"))) {
            root = home;
        }
    }

    @Override
    public void onEnable() {
        proxy.registerChannel(CHANNEL);
        proxy.registerCommand("op", new OpCommand(true));
        proxy.registerCommand("deop", new OpCommand(false));
        try {
            syncChassisOpsIntoStore();
        } catch (Exception e) {
            logger.warning("network-ops seed: " + e.getMessage());
        }
        logger.info("YaP Link OP Sync ready — root=" + root + " (network OP → all backends)");
    }

    @Subscribe
    public void onPostConnect(PostConnectEvent event) {
        LinkPlayer player = event.player();
        if (!isNetworkOp(player.uuid(), player.username())) {
            return;
        }
        player.grantPermission("yaplink.*");
        pushToServer(event.server(), player.uuid(), player.username(), true);
        // Persist real (online) UUID
        try {
            addOp(player.uuid(), player.username());
        } catch (IOException e) {
            logger.warning("network-ops update: " + e.getMessage());
        }
    }

    private final class OpCommand implements SimpleCommand {
        private final boolean grant;

        OpCommand(boolean grant) {
            this.grant = grant;
        }

        @Override
        public void execute(CommandSource source, String[] args) {
            if (!canManage(source)) {
                source.sendMessage("§cNo permission.");
                return;
            }
            if (args.length < 1) {
                source.sendMessage("§eUsage: /" + (grant ? "op" : "deop") + " <player>");
                return;
            }
            String name = args[0].trim();
            Optional<LinkPlayer> online = proxy.player(name);
            UUID uuid = online.map(LinkPlayer::uuid).orElse(offlineUuid(name));
            String display = online.map(LinkPlayer::username).orElse(name);
            try {
                if (grant) {
                    addOp(uuid, display);
                    addChassisOpsName(display);
                } else {
                    removeOp(display);
                    removeChassisOpsName(display);
                }
            } catch (IOException e) {
                source.sendMessage("§cFailed to update network ops: " + e.getMessage());
                return;
            }
            byte[] payload = payload(uuid, display, grant);
            for (RegisteredServer server : proxy.servers()) {
                server.sendPluginMessage(CHANNEL, payload);
            }
            source.sendMessage((grant ? "§aNetwork OP granted to §f" : "§eNetwork OP removed from §f")
                    + display + " §7(all servers)");
            if (online.isPresent()) {
                if (grant) {
                    online.get().grantPermission("yaplink.*");
                }
                online.get().sendMessage(grant
                        ? "§aYou are now a network operator on all YaP servers."
                        : "§eYour network operator status was removed.");
            }
        }

        private boolean canManage(CommandSource source) {
            if (!source.isPlayer()) {
                return true;
            }
            LinkPlayer self = source.asPlayer();
            return isNetworkOp(self.uuid(), self.username());
        }
    }

    private void pushToServer(RegisteredServer server, UUID uuid, String name, boolean op) {
        server.sendPluginMessage(CHANNEL, payload(uuid, name, op));
    }

    private static byte[] payload(UUID uuid, String name, boolean op) {
        String body = "OP|" + uuid + "|" + name + "|" + (op ? "1" : "0");
        return body.getBytes(StandardCharsets.UTF_8);
    }

    private boolean isNetworkOp(UUID uuid, String name) {
        Map<UUID, String> map = loadStore();
        if (uuid != null && map.containsKey(uuid)) {
            return true;
        }
        if (name != null) {
            for (String n : map.values()) {
                if (n.equalsIgnoreCase(name)) {
                    return true;
                }
            }
            return chassisOpsNames().stream().anyMatch(n -> n.equalsIgnoreCase(name));
        }
        return false;
    }

    private void addOp(UUID uuid, String name) throws IOException {
        Map<UUID, String> map = loadStore();
        map.entrySet().removeIf(e -> e.getValue().equalsIgnoreCase(name) && !e.getKey().equals(uuid));
        map.put(uuid, name);
        saveStore(map);
    }

    private void removeOp(String name) throws IOException {
        Map<UUID, String> map = loadStore();
        map.entrySet().removeIf(e -> e.getValue().equalsIgnoreCase(name));
        saveStore(map);
    }

    private Map<UUID, String> loadStore() {
        Map<UUID, String> out = new LinkedHashMap<>();
        Path file = networkOpsFile();
        if (!Files.isRegularFile(file)) {
            return out;
        }
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            Matcher m = ENTRY.matcher(raw);
            while (m.find()) {
                try {
                    out.put(UUID.fromString(m.group(1)), m.group(2));
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (IOException e) {
            logger.warning("read network-ops: " + e.getMessage());
        }
        return out;
    }

    private void saveStore(Map<UUID, String> map) throws IOException {
        Path file = networkOpsFile();
        Files.createDirectories(file.getParent());
        StringBuilder json = new StringBuilder("[\n");
        boolean first = true;
        for (Map.Entry<UUID, String> e : map.entrySet()) {
            if (!first) {
                json.append(",\n");
            }
            first = false;
            json.append("  {\n")
                    .append("    \"uuid\": \"").append(e.getKey()).append("\",\n")
                    .append("    \"name\": \"").append(e.getValue().replace("\"", "\\\"")).append("\"\n")
                    .append("  }");
        }
        json.append("\n]\n");
        Files.writeString(file, json.toString(), StandardCharsets.UTF_8);
    }

    private Path networkOpsFile() {
        return root.resolve("config").resolve("network-ops.json");
    }

    private Path chassisProps() {
        return root.resolve("config").resolve("server.properties");
    }

    private void syncChassisOpsIntoStore() throws IOException {
        List<String> names = chassisOpsNames();
        if (names.isEmpty()) {
            return;
        }
        Map<UUID, String> map = loadStore();
        boolean changed = false;
        for (String name : names) {
            boolean found = map.values().stream().anyMatch(n -> n.equalsIgnoreCase(name));
            if (!found) {
                map.put(offlineUuid(name), name);
                changed = true;
            }
        }
        if (changed) {
            saveStore(map);
        }
    }

    private List<String> chassisOpsNames() {
        List<String> out = new ArrayList<>();
        Path props = chassisProps();
        if (!Files.isRegularFile(props)) {
            return out;
        }
        try {
            Properties p = new Properties();
            try (var in = Files.newInputStream(props)) {
                p.load(in);
            }
            String ops = p.getProperty("ops", "");
            for (String part : ops.split("[,\\s]+")) {
                if (!part.isBlank()) {
                    out.add(part.trim());
                }
            }
        } catch (IOException e) {
            logger.warning("read ops=: " + e.getMessage());
        }
        return out;
    }

    private void addChassisOpsName(String name) throws IOException {
        List<String> names = new ArrayList<>(chassisOpsNames());
        if (names.stream().noneMatch(n -> n.equalsIgnoreCase(name))) {
            names.add(name);
            writeChassisOps(names);
        }
    }

    private void removeChassisOpsName(String name) throws IOException {
        List<String> names = new ArrayList<>(chassisOpsNames());
        names.removeIf(n -> n.equalsIgnoreCase(name));
        writeChassisOps(names);
    }

    private void writeChassisOps(List<String> names) throws IOException {
        Path props = chassisProps();
        if (!Files.isRegularFile(props)) {
            return;
        }
        List<String> lines = Files.readAllLines(props, StandardCharsets.UTF_8);
        boolean found = false;
        String joined = String.join(",", names);
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().startsWith("ops=")) {
                lines.set(i, "ops=" + joined);
                found = true;
                break;
            }
        }
        if (!found) {
            lines.add("ops=" + joined);
        }
        Files.write(props, lines, StandardCharsets.UTF_8);
    }

    private static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }
}
