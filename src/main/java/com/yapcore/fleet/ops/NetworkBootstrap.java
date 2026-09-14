package com.yapcore.fleet.ops;

import com.yapcore.config.ServerConfig;
import com.yapcore.fleet.link.LinkFleetSync;
import com.yapcore.fleet.local.InstanceLayout;
import com.yapcore.fleet.model.FleetInstance;
import com.yapcore.fleet.store.FleetMigrator;
import com.yapcore.fleet.store.FleetStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Network bootstrap wizard: ensure-db per server-id → lobby+survival templates → Link sync → optional start.
 */
public final class NetworkBootstrap {

    private static final Logger LOG = Logger.getLogger("YaP.Fleet.Bootstrap");

    private NetworkBootstrap() {
    }

    public static Map<String, Object> run(
            Path rootDir,
            ServerConfig config,
            FleetStore store,
            FleetMigrator migrator,
            String jdbcUrl,
            boolean createSurvival,
            boolean enableVelocity,
            boolean startInstances,
            InstanceStarter starter) throws Exception {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> steps = new ArrayList<>();

        migrator.enableFleet();
        steps.add(step("enable-fleet", true, "fleet registry ready"));

        if (enableVelocity) {
            config.setVelocityEnabled(true);
            config.save();
            steps.add(step("velocity-enabled", true, "velocity-enabled=true"));
        }

        FleetInstance lobby = ensureInstance(rootDir, config, store, "lobby", config.foliaListenPort(), true);
        steps.add(step("lobby", true, "lobby layout port=" + lobby.port()));

        if (createSurvival) {
            int survivalPort = store.nextFreeGamePort(config);
            FleetInstance survival = ensureInstance(rootDir, config, store, "survival", survivalPort, false);
            steps.add(step("survival", true, "survival layout port=" + survival.port()));
        }

        if (jdbcUrl != null && !jdbcUrl.isBlank()) {
            for (FleetInstance inst : store.instances()) {
                Map<String, Object> db = runEnsureDb(rootDir, jdbcUrl, inst.serverId());
                steps.add(db);
            }
        }

        LinkFleetSync.syncAll(rootDir, config, store);
        steps.add(step("sync-link", true, "servers.* rewritten"));

        List<String> started = new ArrayList<>();
        if (startInstances && starter != null) {
            for (FleetInstance inst : store.instances()) {
                if (inst.autoStart() || "lobby".equals(inst.id())) {
                    starter.start(inst.id());
                    started.add(inst.id());
                }
            }
            steps.add(step("start", true, "started=" + started));
        }

        out.put("ok", true);
        out.put("steps", steps);
        out.put("started", started);
        out.put("instances", store.instances().stream().map(FleetInstance::toMap).toList());
        LOG.info("Network bootstrap complete — " + store.instances().size() + " instance(s)");
        return out;
    }

    private static FleetInstance ensureInstance(
            Path rootDir,
            ServerConfig config,
            FleetStore store,
            String id,
            int port,
            boolean autoStart) throws IOException {
        var existing = store.findInstance(id);
        if (existing.isPresent()) {
            InstanceLayout.ensure(rootDir, config, existing.get());
            return existing.get();
        }
        FleetInstance inst = new FleetInstance(
                id, id, "local", "fleet/instances/" + id, port, "127.0.0.1", autoStart, id);
        InstanceLayout.ensure(rootDir, config, inst);
        store.putInstance(inst);
        return inst;
    }

    private static Map<String, Object> runEnsureDb(Path rootDir, String jdbcUrl, String serverId)
            throws IOException, InterruptedException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("step", "ensure-db:" + serverId);
        String url = jdbcUrl == null ? "" : jdbcUrl.trim();
        List<String> cmd = new ArrayList<>();
        cmd.add("bash");
        if (url.toLowerCase().contains("postgres")) {
            Path script = rootDir.resolve("scripts/db/ensure-postgres.sh");
            if (!Files.isRegularFile(script)) {
                row.put("ok", false);
                row.put("error", "missing " + script);
                return row;
            }
            cmd.add(script.toString());
            cmd.add("--server-id");
            cmd.add(serverId);
        } else if (url.toLowerCase().contains("sqlite")) {
            Path script = rootDir.resolve("scripts/db/configure-db.sh");
            if (!Files.isRegularFile(script)) {
                row.put("ok", false);
                row.put("error", "missing " + script);
                return row;
            }
            cmd.add(script.toString());
            cmd.add("--engine");
            cmd.add("sqlite");
            cmd.add("--server-id");
            cmd.add(serverId);
        } else {
            Path script = rootDir.resolve("scripts/db/ensure-db.sh");
            if (!Files.isRegularFile(script)) {
                row.put("ok", false);
                row.put("error", "missing " + script);
                return row;
            }
            cmd.add(script.toString());
            cmd.add("--server-id");
            cmd.add(serverId);
            String host = extractJdbcHost(url);
            if (host != null) {
                cmd.add("--host");
                cmd.add(host);
            }
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(rootDir.toFile());
        pb.redirectErrorStream(true);
        if (!url.isBlank()) {
            pb.environment().put("YAP_JDBC_URL", url);
        }
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes());
        boolean finished = p.waitFor(180, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            row.put("ok", false);
            row.put("error", "ensure-db timed out");
            return row;
        }
        row.put("ok", p.exitValue() == 0);
        row.put("exit", p.exitValue());
        row.put("output", out.length() > 2_000 ? out.substring(0, 2_000) : out);
        return row;
    }

    static String extractJdbcHost(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return null;
        }
        // jdbc:mariadb://host:3306/db or jdbc:mysql://host/db
        int scheme = jdbcUrl.indexOf("://");
        if (scheme < 0) {
            return null;
        }
        String rest = jdbcUrl.substring(scheme + 3);
        int slash = rest.indexOf('/');
        String hostPort = slash >= 0 ? rest.substring(0, slash) : rest;
        int at = hostPort.lastIndexOf('@');
        if (at >= 0) {
            hostPort = hostPort.substring(at + 1);
        }
        int colon = hostPort.indexOf(':');
        String host = colon >= 0 ? hostPort.substring(0, colon) : hostPort;
        return host.isBlank() ? null : host;
    }

    private static Map<String, Object> step(String name, boolean ok, String note) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("step", name);
        m.put("ok", ok);
        m.put("note", note);
        return m;
    }

    @FunctionalInterface
    public interface InstanceStarter {
        void start(String instanceId) throws Exception;
    }
}
