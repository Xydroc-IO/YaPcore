package com.yapcore.fleet.ops;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Automates YaPDB setup for operators: pick MariaDB / Postgres / SQLite, start packaged
 * Docker when needed, write JDBC, optionally sync the catalog to fleet backends.
 */
public final class DatabaseSetup {

    private static final Logger LOG = Logger.getLogger("YaP.DatabaseSetup");
    private static final Pattern YAML_URL = Pattern.compile("(?m)^\\s*url:\\s*[\"']?([^\"'\\n#]+)");
    private static final Pattern YAML_ENGINE = Pattern.compile("(?m)^\\s*engine:\\s*[\"']?([^\"'\\n#]+)");
    private static final Pattern YAML_USER = Pattern.compile("(?m)^\\s*user:\\s*[\"']?([^\"'\\n#]+)");

    public enum Engine {
        MYSQL,
        POSTGRES,
        SQLITE;

        public static Engine parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return MYSQL;
            }
            String s = raw.trim().toLowerCase(Locale.ROOT);
            if (s.contains("postgres") || s.equals("pg") || s.equals("pgsql")) {
                return POSTGRES;
            }
            if (s.contains("sqlite")) {
                return SQLITE;
            }
            return MYSQL;
        }

        public static Engine fromJdbcUrl(String jdbcUrl) {
            if (jdbcUrl == null || jdbcUrl.isBlank()) {
                return MYSQL;
            }
            String u = jdbcUrl.toLowerCase(Locale.ROOT);
            if (u.contains("postgres")) {
                return POSTGRES;
            }
            if (u.contains("sqlite")) {
                return SQLITE;
            }
            return MYSQL;
        }

        /** Preset URL shown in wizards (SQLite path is finalized by configure-db.sh). */
        public String presetJdbcUrl(Path rootDir) {
            return switch (this) {
                case POSTGRES -> "jdbc:postgresql://127.0.0.1:5432/yap_playerdata";
                case SQLITE -> "jdbc:sqlite:" + rootDir.resolve("data/yap.db").toAbsolutePath().normalize();
                case MYSQL -> "jdbc:mysql://127.0.0.1:3306/yap_playerdata"
                        + "?useSSL=false&allowPublicKeyRetrieval=true";
            };
        }
    }

    private DatabaseSetup() {
    }

    public static Map<String, Object> status(Path rootDir) {
        Path root = rootDir.toAbsolutePath().normalize();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("bashAvailable", bashAvailable());
        out.put("dockerInstalled", commandOk(root, List.of("docker", "--version"), 5));
        out.put("dockerRunning", commandOk(root, List.of("docker", "info"), 8));
        out.put("scripts", scriptsPresent(root));
        out.put("mariadb", containerSnapshot(root, "mariadb", "yapcore-mariadb",
                "scripts/db/status-mariadb.sh"));
        out.put("postgres", containerSnapshot(root, "postgres", "yapcore-postgres",
                "scripts/db/status-postgres.sh"));
        out.put("yapdb", readYapdbSnapshot(root));
        out.put("engines", List.of(
                Map.of("id", "mysql", "label", "MariaDB / MySQL", "needsDocker", true,
                        "presetJdbc", Engine.MYSQL.presetJdbcUrl(root)),
                Map.of("id", "postgres", "label", "PostgreSQL", "needsDocker", true,
                        "presetJdbc", Engine.POSTGRES.presetJdbcUrl(root)),
                Map.of("id", "sqlite", "label", "SQLite (single-node)", "needsDocker", false,
                        "presetJdbc", Engine.SQLITE.presetJdbcUrl(root))));
        out.put("hint", "MariaDB/Postgres need Docker Desktop/Engine. SQLite needs no Docker.");
        return out;
    }

    /**
     * Start Docker (if needed), write JDBC for the chosen engine, optionally sync fleet catalog.
     */
    public static Map<String, Object> ensure(
            Path rootDir,
            Engine engine,
            String serverId,
            String host,
            boolean syncFleet) throws IOException, InterruptedException {
        Path root = rootDir.toAbsolutePath().normalize();
        String sid = (serverId == null || serverId.isBlank()) ? "lobby" : serverId.trim();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("action", "ensure");
        out.put("engine", engine.name().toLowerCase(Locale.ROOT));
        out.put("serverId", sid);

        if (!bashAvailable()) {
            out.put("ok", false);
            out.put("error", "bash not found — install Git Bash/WSL on Windows, or run scripts/db/*.sh manually");
            return out;
        }

        Map<String, Object> step = switch (engine) {
            case POSTGRES -> runScript(root, List.of(
                    root.resolve("scripts/db/ensure-postgres.sh").toString(),
                    "--server-id", sid), 180);
            case SQLITE -> runScript(root, List.of(
                    root.resolve("scripts/db/configure-db.sh").toString(),
                    "--engine", "sqlite",
                    "--server-id", sid,
                    "--root", root.toString()), 60);
            case MYSQL -> {
                List<String> cmd = new ArrayList<>();
                cmd.add(root.resolve("scripts/db/ensure-db.sh").toString());
                cmd.add("--server-id");
                cmd.add(sid);
                cmd.add("--root");
                cmd.add(root.toString());
                if (host != null && !host.isBlank()) {
                    cmd.add("--host");
                    cmd.add(host.trim());
                }
                yield runScript(root, cmd, 180);
            }
        };
        out.put("ensure", step);
        boolean ok = Boolean.TRUE.equals(step.get("ok"));

        int synced = 0;
        if (syncFleet) {
            synced = com.yapcore.fleet.local.InstanceLayout.syncSharedCatalogDataToLocalFleet(root);
            out.put("fleetFilesWritten", synced);
        }
        out.put("yapdb", readYapdbSnapshot(root));
        out.put("ok", ok);
        if (ok) {
            LOG.info("Database ensure " + engine + " server-id=" + sid + " fleetSync=" + synced);
        }
        return out;
    }

    /** Convenience: detect engine from a JDBC URL (network bootstrap path). */
    public static Map<String, Object> ensureFromJdbcUrl(
            Path rootDir, String jdbcUrl, String serverId)
            throws IOException, InterruptedException {
        Engine engine = Engine.fromJdbcUrl(jdbcUrl);
        String host = NetworkBootstrap.extractJdbcHost(jdbcUrl);
        return ensure(rootDir, engine, serverId, host, false);
    }

    public static Map<String, Object> startDocker(Path rootDir, Engine engine)
            throws IOException, InterruptedException {
        Path root = rootDir.toAbsolutePath().normalize();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("action", "start-docker");
        out.put("engine", engine.name().toLowerCase(Locale.ROOT));
        if (engine == Engine.SQLITE) {
            out.put("ok", true);
            out.put("note", "SQLite does not use Docker");
            return out;
        }
        if (!bashAvailable()) {
            out.put("ok", false);
            out.put("error", "bash not found");
            return out;
        }
        Path script = root.resolve(engine == Engine.POSTGRES
                ? "scripts/db/start-postgres.sh"
                : "scripts/db/start-mariadb.sh");
        Map<String, Object> step = runScript(root, List.of(script.toString()), 180);
        out.putAll(step);
        out.put("action", "start-docker");
        return out;
    }

    public static Map<String, Object> stopDocker(Path rootDir, Engine engine)
            throws IOException, InterruptedException {
        Path root = rootDir.toAbsolutePath().normalize();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("action", "stop-docker");
        out.put("engine", engine.name().toLowerCase(Locale.ROOT));
        if (engine == Engine.SQLITE) {
            out.put("ok", true);
            out.put("note", "SQLite does not use Docker");
            return out;
        }
        if (!bashAvailable()) {
            out.put("ok", false);
            out.put("error", "bash not found");
            return out;
        }
        if (engine == Engine.MYSQL) {
            Path script = root.resolve("scripts/db/stop-mariadb.sh");
            Map<String, Object> step = runScript(root, List.of(script.toString()), 60);
            out.putAll(step);
            out.put("action", "stop-docker");
            return out;
        }
        Map<String, Object> step = runScript(root, List.of(
                "bash", "-lc",
                "cd \"" + root.resolve("deploy/postgres") + "\" && "
                        + "(docker compose down || docker-compose down)"), 60);
        out.putAll(step);
        out.put("action", "stop-docker");
        return out;
    }

    private static Map<String, Object> scriptsPresent(Path root) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String rel : List.of(
                "scripts/db/ensure-db.sh",
                "scripts/db/ensure-postgres.sh",
                "scripts/db/configure-db.sh",
                "scripts/db/start-mariadb.sh",
                "scripts/db/start-postgres.sh",
                "scripts/db/stop-mariadb.sh")) {
            m.put(Path.of(rel).getFileName().toString(), Files.isRegularFile(root.resolve(rel)));
        }
        return m;
    }

    private static Map<String, Object> containerSnapshot(
            Path root, String id, String containerName, String statusScriptRel) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("container", containerName);
        boolean exists = commandOk(root, List.of(
                "docker", "inspect", "-f", "{{.State.Status}}", containerName), 5);
        m.put("present", exists);
        String health = capture(root, List.of(
                "docker", "inspect", "-f",
                "{{if .State.Health}}{{.State.Health.Status}}{{else}}n/a{{end}}",
                containerName), 5);
        m.put("health", health == null || health.isBlank() ? "unknown" : health.trim());
        Path statusScript = root.resolve(statusScriptRel);
        if (Files.isRegularFile(statusScript) && bashAvailable()) {
            try {
                Map<String, Object> st = runScript(root, List.of(statusScript.toString()), 15);
                m.put("statusOutput", st.get("output"));
            } catch (Exception e) {
                m.put("statusOutput", e.getMessage());
            }
        }
        return m;
    }

    private static Map<String, Object> readYapdbSnapshot(Path root) {
        Map<String, Object> m = new LinkedHashMap<>();
        Path cfg = root.resolve("plugins/YaPDB/config.yml");
        m.put("configPresent", Files.isRegularFile(cfg));
        if (!Files.isRegularFile(cfg)) {
            return m;
        }
        try {
            String text = Files.readString(cfg, StandardCharsets.UTF_8);
            String url = firstGroup(YAML_URL, text);
            String engine = firstGroup(YAML_ENGINE, text);
            String user = firstGroup(YAML_USER, text);
            m.put("engine", engine == null || engine.isBlank()
                    ? Engine.fromJdbcUrl(url).name().toLowerCase(Locale.ROOT)
                    : engine.trim());
            m.put("jdbcUrl", url == null ? "" : url.trim());
            m.put("user", user == null ? "" : user.trim());
            if (url != null && url.contains("sqlite:")) {
                String path = url.substring(url.indexOf("sqlite:") + 7).trim();
                m.put("sqliteFileExists", Files.isRegularFile(Path.of(path)));
            }
        } catch (IOException e) {
            m.put("error", e.getMessage());
        }
        return m;
    }

    private static String firstGroup(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private static Map<String, Object> runScript(Path root, List<String> scriptAndArgs, int timeoutSec)
            throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        if (!scriptAndArgs.isEmpty() && "bash".equals(scriptAndArgs.get(0))) {
            cmd.addAll(scriptAndArgs);
        } else {
            cmd.add("bash");
            cmd.addAll(scriptAndArgs);
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(root.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = p.waitFor(timeoutSec, TimeUnit.SECONDS);
        Map<String, Object> row = new LinkedHashMap<>();
        if (!finished) {
            p.destroyForcibly();
            row.put("ok", false);
            row.put("error", "timed out after " + timeoutSec + "s");
            row.put("output", truncate(out, 2_000));
            return row;
        }
        row.put("ok", p.exitValue() == 0);
        row.put("exit", p.exitValue());
        row.put("output", truncate(out, 4_000));
        return row;
    }

    private static boolean bashAvailable() {
        return commandOk(Path.of("."), List.of("bash", "-lc", "true"), 5)
                || Files.isExecutable(Path.of("/bin/bash"))
                || Files.isRegularFile(Path.of("/usr/bin/bash"));
    }

    private static boolean commandOk(Path root, List<String> cmd, int timeoutSec) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(root.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.getInputStream().readAllBytes();
            return p.waitFor(timeoutSec, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String capture(Path root, List<String> cmd, int timeoutSec) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(root.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return "";
            }
            return p.exitValue() == 0 ? out : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
