package com.yapcore.setup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/** Read-only first-boot checklist status for dashboard + Swing Setup panel. */
public final class SetupChecklist {

    private SetupChecklist() {
    }

    public static Map<String, Object> snapshot(Path rootDir) {
        Path root = rootDir.toAbsolutePath().normalize();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.putAll(SetupOs.snapshot());
        out.put("steps", steps(root));
        out.put("platformCommands", platformCommands());
        out.put("docs", List.of(
                Map.of("label", "Quick start", "path", "docs/start/QUICK_START.md"),
                Map.of("label", "Windows", "path", "docs/start/WINDOWS.md"),
                Map.of("label", "Tebex", "path", "docs/ops/TEBEX.md"),
                Map.of("label", "Integrations", "path", "docs/ops/INTEGRATIONS.md")));
        long ready = ((List<?>) out.get("steps")).stream()
                .filter(s -> s instanceof Map<?, ?> m && Boolean.TRUE.equals(m.get("ready")))
                .count();
        out.put("readyCount", ready);
        out.put("totalCount", ((List<?>) out.get("steps")).size());
        out.put("summary", ready + " / " + out.get("totalCount") + " setup steps ready");
        return out;
    }

    private static List<Map<String, Object>> steps(Path root) {
        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(step("eula", "Accept EULA", eulaReady(root),
                "Writes eula=true for Folia / fleet instances", true));
        steps.add(step("seed-defaults", "Seed defaults",
                Files.isRegularFile(root.resolve("config/server.properties")),
                "Copy config/defaults when missing (idempotent)", true));
        steps.add(step("database", "Database (YaPDB)", dbReady(root),
                "Open Fleet → Database or run configure-db", false,
                "fleet", "Use Fleet → Database card / Database… dialog"));
        steps.add(step("bootstrap", "Bootstrap network", fleetPresent(root),
                "Lobby + survival fleet layout", false,
                "fleet", "Use Fleet → Bootstrap wizard"));
        steps.add(step("link-forwarding", "Link forwarding secret",
                Files.isRegularFile(root.resolve("forwarding.secret"))
                        || Files.isRegularFile(root.resolve("link-data/forwarding.secret")),
                "Modern player-info forwarding for YaP Link", true));
        steps.add(step("fetch-tebex", "Fetch Tebex (Hub)", tebexPresent(root),
                "Official Folia store plugin — Hub / lobby only", true));
        steps.add(step("enable-grim", "Enable Grim AC", grimEnabled(root),
                "Optional top-tier AC; restart Folia after enable", true));
        steps.add(step("production-profile", "Production profile", productionHint(root),
                "Apply public production keys from defaults", true));
        steps.add(step("nginx-dry-run", "nginx dry-run",
                Files.isRegularFile(root.resolve("scripts/setup/nginx-setup.sh"))
                        || Files.isRegularFile(root.resolve("scripts/windows/Nginx-Setup.ps1")),
                "Preview nginx edge config (install needs sudo / Swing)", true));
        steps.add(step("build-folia", "YaP-Folia jar", foliaJarPresent(root),
                "Needed for source installs when lib jar is missing", true));
        return steps;
    }

    private static Map<String, Object> step(String id, String label, boolean ready, String detail, boolean runnable) {
        return step(id, label, ready, detail, runnable, null, null);
    }

    private static Map<String, Object> step(
            String id, String label, boolean ready, String detail, boolean runnable,
            String deeplink, String deeplinkHint) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("label", label);
        row.put("ready", ready);
        row.put("detail", detail);
        row.put("runnable", runnable);
        row.put("status", ready ? "ready" : "todo");
        if (deeplink != null) {
            row.put("deeplink", deeplink);
            row.put("deeplinkHint", deeplinkHint);
        }
        return row;
    }

    private static Map<String, Object> platformCommands() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("linux", List.of(
                "./start.sh --gui",
                "./scripts/setup/seed-defaults.sh",
                "./configure-db.sh --server-id lobby",
                "./scripts/plugins/fetch-tebex.sh",
                "./scripts/plugins/grim-ac.sh enable"));
        out.put("windows", List.of(
                ".\\start.cmd",
                ".\\gui.cmd",
                ".\\scripts\\windows\\Start-MariaDB.ps1",
                ".\\scripts\\windows\\Configure-Db.ps1",
                ".\\scripts\\windows\\Nginx-Setup.ps1 -DryRun"));
        return out;
    }

    static boolean eulaReady(Path root) {
        return eulaAccepted(root.resolve("eula.txt"))
                || eulaAccepted(root.resolve("folia-kernel/eula.txt"))
                || fleetEulaAny(root);
    }

    private static boolean fleetEulaAny(Path root) {
        Path instances = root.resolve("fleet/instances");
        if (!Files.isDirectory(instances)) {
            return false;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(instances)) {
            for (Path inst : stream) {
                if (Files.isDirectory(inst) && eulaAccepted(inst.resolve("eula.txt"))) {
                    return true;
                }
            }
        } catch (IOException ignored) {
        }
        return false;
    }

    static boolean eulaAccepted(Path file) {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try {
            String text = Files.readString(file).toLowerCase(Locale.ROOT);
            return text.contains("eula=true");
        } catch (IOException e) {
            return false;
        }
    }

    static boolean dbReady(Path root) {
        Path yapdb = root.resolve("plugins/YaPDB/config.yml");
        if (!Files.isRegularFile(yapdb)) {
            return false;
        }
        try {
            String text = Files.readString(yapdb);
            return text.contains("jdbc:") && text.contains("url:")
                    && !text.toLowerCase(Locale.ROOT).contains("change-me");
        } catch (IOException e) {
            return false;
        }
    }

    static boolean fleetPresent(Path root) {
        return Files.isRegularFile(root.resolve("fleet/fleet.json"))
                && Files.isDirectory(root.resolve("fleet/instances"));
    }

    static boolean tebexPresent(Path root) {
        if (Files.isRegularFile(root.resolve("plugins/tebex.jar"))) {
            return true;
        }
        Path lobby = root.resolve("fleet/instances/lobby/plugins/tebex.jar");
        return Files.isRegularFile(lobby);
    }

    static boolean grimEnabled(Path root) {
        return Files.isRegularFile(root.resolve("plugins/grim.jar"));
    }

    static boolean grimDownloaded(Path root) {
        return grimEnabled(root) || Files.isRegularFile(root.resolve("plugins/grim.jar.disabled"));
    }

    static boolean productionHint(Path root) {
        Path cfg = root.resolve("config/server.properties");
        if (!Files.isRegularFile(cfg)) {
            return false;
        }
        try {
            String text = Files.readString(cfg);
            return text.contains("expose-server=true") || text.contains("production");
        } catch (IOException e) {
            return false;
        }
    }

    static boolean foliaJarPresent(Path root) {
        Path lib = root.resolve("lib");
        if (!Files.isDirectory(lib)) {
            return false;
        }
        try (Stream<Path> stream = Files.list(lib)) {
            return stream.anyMatch(p -> {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return n.startsWith("yap-folia") && n.endsWith(".jar");
            });
        } catch (IOException e) {
            return false;
        }
    }

    /** Copy missing defaults tree (Java path when bash unavailable). */
    static int seedDefaultsJava(Path root) throws IOException {
        Path defaults = root.resolve("config/defaults");
        if (!Files.isDirectory(defaults)) {
            return 0;
        }
        int seeded = 0;
        Path serverProps = defaults.resolve("server.properties");
        Path destProps = root.resolve("config/server.properties");
        if (Files.isRegularFile(serverProps) && !Files.exists(destProps)) {
            Files.createDirectories(destProps.getParent());
            Files.copy(serverProps, destProps, StandardCopyOption.COPY_ATTRIBUTES);
            seeded++;
        }
        Path pluginsDefaults = defaults.resolve("plugins");
        if (Files.isDirectory(pluginsDefaults)) {
            try (Stream<Path> walk = Files.walk(pluginsDefaults)) {
                for (Path src : walk.filter(Files::isRegularFile).toList()) {
                    String name = src.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (!(name.endsWith(".yml") || name.endsWith(".properties") || name.endsWith(".yaml"))) {
                        continue;
                    }
                    Path rel = pluginsDefaults.relativize(src);
                    Path dest = root.resolve("plugins").resolve(rel);
                    if (Files.exists(dest)) {
                        continue;
                    }
                    Files.createDirectories(dest.getParent());
                    Files.copy(src, dest, StandardCopyOption.COPY_ATTRIBUTES);
                    seeded++;
                }
            }
        }
        Path linkDefault = defaults.resolve("link.properties");
        Path linkDest = root.resolve("link-data/link.properties");
        if (Files.isRegularFile(linkDefault) && !Files.exists(linkDest)) {
            Files.createDirectories(linkDest.getParent());
            Files.copy(linkDefault, linkDest, StandardCopyOption.COPY_ATTRIBUTES);
            seeded++;
        }
        return seeded;
    }

    static void writeEula(Path root) throws IOException {
        byte[] bytes = "eula=true\n".getBytes(StandardCharsets.UTF_8);
        writeEulaFile(root.resolve("eula.txt"), bytes);
        writeEulaFile(root.resolve("folia-kernel/eula.txt"), bytes);
        Path instances = root.resolve("fleet/instances");
        if (!Files.isDirectory(instances)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(instances)) {
            for (Path inst : stream) {
                if (Files.isDirectory(inst)) {
                    writeEulaFile(inst.resolve("eula.txt"), bytes);
                }
            }
        }
    }

    private static void writeEulaFile(Path file, byte[] bytes) throws IOException {
        Files.createDirectories(file.getParent() == null ? Path.of(".") : file.getParent());
        Files.write(file, bytes);
    }
}
