package com.yapcore.link.console;

import com.yapcore.link.LinkServer;
import com.yapcore.link.api.SimpleCommand;
import com.yapcore.link.backend.BackendMonitor;
import com.yapcore.link.session.PlayerHub;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.logging.Logger;

/** Stdin console: built-ins + Link plugin commands ({@code op}, {@code hub}, …). */
public final class LinkConsole implements Runnable {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Console");

    private final LinkServer server;
    private volatile boolean running = true;

    public LinkConsole(LinkServer server) {
        this.server = server;
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        LOG.info("Console ready — commands: help | reload | list | servers | say <msg> | stop | <plugin cmds>");
        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while (running && (line = in.readLine()) != null) {
                handle(line.trim());
            }
        } catch (Exception e) {
            if (running) {
                LOG.warning("Console closed: " + e.getMessage());
            }
        }
    }

    private void handle(String line) {
        if (line.isEmpty()) {
            return;
        }
        if ("help".equalsIgnoreCase(line)) {
            LOG.info("Commands: help | reload | list | servers | say <message> | stop");
            LOG.info("Plugin commands (when plugins-enabled): op | deop | hub | server | …");
            return;
        }
        if ("reload".equalsIgnoreCase(line)) {
            try {
                server.reloadConfig();
                LOG.info("Config reloaded");
            } catch (Exception e) {
                LOG.warning("Reload failed: " + e.getMessage());
            }
            return;
        }
        if ("list".equalsIgnoreCase(line)) {
            PlayerHub hub = server.playerHub();
            LOG.info("Online (" + hub.onlineCount() + "):");
            hub.all().forEach(p ->
                    LOG.info("  " + p.username() + " @ " + p.backendName() + " (" + p.id() + ")"));
            return;
        }
        if ("servers".equalsIgnoreCase(line)) {
            BackendMonitor mon = server.backendMonitor();
            mon.allSnapshots().forEach((name, snap) ->
                    LOG.info("  " + name + " up=" + snap.up()
                            + (snap.status() != null ? " online=" + snap.status().online() : "")
                            + (snap.error() != null ? " err=" + snap.error() : "")));
            return;
        }
        if ("stop".equalsIgnoreCase(line)) {
            LOG.info("Stopping YaP Link…");
            try {
                server.stop();
            } finally {
                running = false;
                // Main thread joins forever — exit so chassis stop/restart is not stuck
                // waiting for waitFor(30s) + destroyForcibly.
                System.exit(0);
            }
            return;
        }
        if (line.regionMatches(true, 0, "say ", 0, 4)) {
            String msg = line.substring(4).trim();
            if (!msg.isEmpty()) {
                server.chatRelay().relayNetworkMessage(
                        server.config().chatRelayChannel(),
                        "proxy",
                        java.util.UUID.randomUUID(),
                        "YaP Link",
                        msg
                );
            }
            return;
        }
        if (tryPluginCommand(line)) {
            return;
        }
        LOG.info("Unknown command — type help");
    }

    /** Dispatch registered Link plugin commands (e.g. {@code op Player}). */
    private boolean tryPluginCommand(String line) {
        if (!server.config().pluginsEnabled()) {
            return false;
        }
        String trimmed = line.startsWith("/") ? line.substring(1).trim() : line.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        String[] parts = trimmed.split("\\s+");
        String name = parts[0].toLowerCase(Locale.ROOT);
        SimpleCommand cmd = server.plugins().command(name);
        if (cmd == null) {
            return false;
        }
        ConsoleSource source = new ConsoleSource();
        if (!cmd.hasPermission(source)) {
            LOG.warning("No permission for /" + name);
            return true;
        }
        String[] args = parts.length > 1
                ? Arrays.copyOfRange(parts, 1, parts.length)
                : new String[0];
        try {
            cmd.execute(source, args);
        } catch (Exception e) {
            LOG.warning("Command /" + name + " failed: " + e.getMessage());
        }
        return true;
    }

    private static final class ConsoleSource implements SimpleCommand.CommandSource {
        @Override
        public String name() {
            return "CONSOLE";
        }

        @Override
        public boolean isPlayer() {
            return false;
        }

        @Override
        public void sendMessage(String legacyText) {
            if (legacyText == null || legacyText.isBlank()) {
                return;
            }
            // Strip § codes for log readability
            String plain = legacyText.replaceAll("§[0-9a-fk-or]", "");
            LOG.info(plain);
        }
    }
}
