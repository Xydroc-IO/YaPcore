package com.yapcore.fleet.agent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

/** Entrypoint: {@code java -jar yap-fleet-agent.jar [--home DIR] [--bind HOST] [--port N] [--node ID]}. */
public final class FleetAgentMain {

    private static final Logger LOG = Logger.getLogger("YaP.FleetAgent");

    private FleetAgentMain() {
    }

    public static void main(String[] args) throws Exception {
        Path home = Path.of("fleet-agent-home").toAbsolutePath().normalize();
        String bind = "0.0.0.0";
        int port = 9095;
        String nodeId = "node-1";
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--home" -> home = Path.of(args[++i]).toAbsolutePath().normalize();
                case "--bind" -> bind = args[++i];
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--node" -> nodeId = args[++i];
                default -> LOG.warning("Unknown arg: " + args[i]);
            }
        }
        Files.createDirectories(home);
        Path tokenFile = home.resolve("agent.token");
        String token;
        if (Files.isRegularFile(tokenFile)) {
            token = Files.readString(tokenFile, StandardCharsets.UTF_8).trim();
        } else {
            token = UUID.randomUUID().toString().replace("-", "");
            Files.writeString(tokenFile, token + "\n", StandardCharsets.UTF_8);
            LOG.info("Wrote new agent token to " + tokenFile);
        }
        FleetAgentHttp http = new FleetAgentHttp(home, nodeId, token);
        http.start(bind, port);
        LOG.info("Copy token into chassis fleet/agents/" + nodeId + ".token");
        Runtime.getRuntime().addShutdownHook(new Thread(http::stop, "fleet-agent-stop"));
        Thread.currentThread().join();
    }
}
