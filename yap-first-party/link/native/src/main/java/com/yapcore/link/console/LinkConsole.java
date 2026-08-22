package com.yapcore.link.console;

import com.yapcore.link.LinkServer;
import com.yapcore.link.backend.BackendMonitor;
import com.yapcore.link.session.PlayerHub;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/** Stdin console: reload, list, say, servers, stop. */
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
        LOG.info("Console ready — commands: help | reload | list | servers | say <msg> | stop");
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
            server.stop();
            running = false;
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
        LOG.info("Unknown command — type help");
    }
}
