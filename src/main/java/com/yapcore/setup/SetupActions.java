package com.yapcore.setup;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Executes first-boot / ops setup checklist actions. */
public final class SetupActions {

    private SetupActions() {
    }

    public static Map<String, Object> run(Path rootDir, String action, Map<String, String> body)
            throws Exception {
        Path root = rootDir.toAbsolutePath().normalize();
        String act = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        return switch (act) {
            case "accept-eula", "eula" -> acceptEula(root);
            case "seed-defaults", "seed" -> seedDefaults(root);
            case "link-forwarding", "forwarding" -> linkForwarding(root, body);
            case "fetch-tebex", "tebex" -> fetchTebex(root);
            case "enable-grim", "grim-enable" -> grim(root, true);
            case "disable-grim", "grim-disable" -> grim(root, false);
            case "grim-status" -> grimStatus(root);
            case "production-profile", "production" -> productionProfile(root, body);
            case "nginx-dry-run" -> nginxDryRun(root);
            case "build-folia" -> buildFolia(root);
            case "fetch-grim" -> fetchGrim(root);
            default -> Map.of("ok", false, "error", "unknown action: " + act);
        };
    }

    private static Map<String, Object> acceptEula(Path root) throws Exception {
        SetupChecklist.writeEula(root);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("action", "accept-eula");
        out.put("result", "Wrote eula=true (root, folia-kernel, fleet instances when present)");
        out.put("ready", SetupChecklist.eulaReady(root));
        return out;
    }

    private static Map<String, Object> seedDefaults(Path root) throws Exception {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("action", "seed-defaults");
        if (Files.isRegularFile(root.resolve("scripts/setup/seed-defaults.sh"))) {
            Map<String, Object> ran = runBashOrWindows(
                    root, "scripts/setup/seed-defaults.sh", "Seed-Defaults",
                    List.of("--root", root.toString()), 180);
            if (Boolean.TRUE.equals(ran.get("ok")) || ran.containsKey("exit")) {
                out.putAll(ran);
                out.put("path", "script");
                return out;
            }
        }
        int seeded = SetupChecklist.seedDefaultsJava(root);
        out.put("ok", true);
        out.put("path", "java");
        out.put("seeded", seeded);
        out.put("result", seeded == 0
                ? "Nothing new — operator configs already present"
                : "Seeded " + seeded + " missing file(s)");
        out.put("output", out.get("result"));
        return out;
    }

    private static Map<String, Object> linkForwarding(Path root, Map<String, String> body)
            throws Exception {
        boolean enable = !"false".equalsIgnoreCase(body.getOrDefault("enable", "true"));
        String flag = enable ? "--enable" : "--disable";
        Map<String, Object> ran = runBashOrWindows(
                root, "scripts/setup/setup-velocity-forwarding.sh", "Forwarding", List.of(flag), 60);
        ran.put("action", "link-forwarding");
        return ran;
    }

    private static Map<String, Object> fetchTebex(Path root) throws Exception {
        Map<String, Object> ran = runBashOrWindows(
                root, "scripts/plugins/fetch-tebex.sh", "Fetch-Tebex", List.of(), 180);
        ran.put("action", "fetch-tebex");
        ran.put("ready", SetupChecklist.tebexPresent(root));
        return ran;
    }

    private static Map<String, Object> fetchGrim(Path root) throws Exception {
        Map<String, Object> ran = runBashOrWindows(
                root, "scripts/plugins/fetch-grim.sh", "Fetch-Grim",
                List.of("--disabled", "--root", root.toString()), 180);
        ran.put("action", "fetch-grim");
        ran.put("downloaded", SetupChecklist.grimDownloaded(root));
        return ran;
    }

    private static Map<String, Object> grim(Path root, boolean enable) throws Exception {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("action", enable ? "enable-grim" : "disable-grim");
        if (SetupOs.bashAvailable() && Files.isRegularFile(root.resolve("scripts/plugins/grim-ac.sh"))) {
            Map<String, Object> ran = SetupScriptRunner.runBash(
                    root, "scripts/plugins/grim-ac.sh",
                    List.of(enable ? "enable" : "disable", "--root", root.toString()), 60);
            out.putAll(ran);
            out.put("ready", SetupChecklist.grimEnabled(root));
            if (enable && Boolean.TRUE.equals(ran.get("ok"))) {
                out.put("hint", "Restart YaP-Folia so grim.jar loads.");
            }
            return out;
        }
        Path plugins = root.resolve("plugins");
        Path active = plugins.resolve("grim.jar");
        Path disabled = plugins.resolve("grim.jar.disabled");
        if (enable) {
            if (!Files.isRegularFile(active) && Files.isRegularFile(disabled)) {
                Files.move(disabled, active, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!Files.isRegularFile(active)) {
                out.put("ok", false);
                out.put("error", "grim.jar missing — run fetch-grim first");
                return out;
            }
            out.put("ok", true);
            out.put("result", "Enabled grim.jar — restart Folia");
            out.put("hint", "Restart YaP-Folia so grim.jar loads.");
        } else {
            if (Files.isRegularFile(active)) {
                Files.move(active, disabled, StandardCopyOption.REPLACE_EXISTING);
            }
            out.put("ok", true);
            out.put("result", "Disabled Grim (grim.jar.disabled)");
        }
        out.put("ready", SetupChecklist.grimEnabled(root));
        out.put("output", out.get("result"));
        return out;
    }

    private static Map<String, Object> grimStatus(Path root) throws Exception {
        if (SetupOs.bashAvailable() && Files.isRegularFile(root.resolve("scripts/plugins/grim-ac.sh"))) {
            Map<String, Object> ran = SetupScriptRunner.runBash(
                    root, "scripts/plugins/grim-ac.sh", List.of("status", "--root", root.toString()), 30);
            ran.put("action", "grim-status");
            ran.put("enabled", SetupChecklist.grimEnabled(root));
            ran.put("downloaded", SetupChecklist.grimDownloaded(root));
            return ran;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("action", "grim-status");
        out.put("enabled", SetupChecklist.grimEnabled(root));
        out.put("downloaded", SetupChecklist.grimDownloaded(root));
        out.put("result", SetupChecklist.grimEnabled(root)
                ? "enabled"
                : (SetupChecklist.grimDownloaded(root) ? "downloaded (disabled)" : "missing"));
        return out;
    }

    private static Map<String, Object> productionProfile(Path root, Map<String, String> body)
            throws Exception {
        List<String> args = new ArrayList<>();
        if ("true".equalsIgnoreCase(body.getOrDefault("withLink", "false"))) {
            args.add("--with-link");
        }
        Map<String, Object> ran = runBashOrWindows(
                root, "scripts/setup/apply-production-profile.sh", "Apply-Production", args, 60);
        ran.put("action", "production-profile");
        return ran;
    }

    private static Map<String, Object> nginxDryRun(Path root) throws Exception {
        Map<String, Object> ran = SetupScriptRunner.run(
                root,
                "scripts/setup/nginx-setup.sh",
                "scripts/windows/Nginx-Setup.ps1",
                List.of("--dry-run"),
                120);
        if (!Boolean.TRUE.equals(ran.get("ok")) && SetupOs.isWindows() && SetupOs.powershellAvailable()) {
            Map<String, Object> ps = SetupScriptRunner.run(
                    root,
                    "scripts/setup/nginx-setup.sh",
                    "scripts/windows/Nginx-Setup.ps1",
                    List.of("-DryRun"),
                    120);
            if (Boolean.TRUE.equals(ps.get("ok"))
                    || !String.valueOf(ps.getOrDefault("output", "")).isBlank()) {
                ran = ps;
            }
        }
        ran.put("action", "nginx-dry-run");
        ran.put("hint", "Full install: Swing → nginx tab (sudo) or scripts/setup/nginx-setup.sh");
        return ran;
    }

    private static Map<String, Object> buildFolia(Path root) throws Exception {
        if (SetupChecklist.foliaJarPresent(root)) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", true);
            out.put("action", "build-folia");
            out.put("result", "yap-folia jar already present under lib/");
            out.put("ready", true);
            return out;
        }
        Map<String, Object> ran = runBashOrWindows(
                root, "scripts/folia/build-yap-folia.sh", null, List.of(), 3_600);
        ran.put("action", "build-folia");
        ran.put("ready", SetupChecklist.foliaJarPresent(root));
        return ran;
    }

    /** Prefer bash script; on Windows without bash use Run-BashSetup.ps1 when mapped. */
    private static Map<String, Object> runBashOrWindows(
            Path root, String bashRel, String windowsAction, List<String> args, int timeoutSec)
            throws Exception {
        Map<String, Object> ran = SetupScriptRunner.runBash(root, bashRel, args, timeoutSec);
        if (Boolean.TRUE.equals(ran.get("ok"))) {
            return ran;
        }
        if (windowsAction != null && SetupOs.isWindows() && SetupOs.powershellAvailable()) {
            List<String> psArgs = new ArrayList<>();
            psArgs.add("-Action");
            psArgs.add(windowsAction);
            for (String a : args) {
                psArgs.add("-ExtraArgs");
                psArgs.add(a);
            }
            Map<String, Object> ps = SetupScriptRunner.run(
                    root, bashRel, "scripts/windows/Run-BashSetup.ps1", psArgs, timeoutSec);
            if (Boolean.TRUE.equals(ps.get("ok"))
                    || !String.valueOf(ps.getOrDefault("output", "")).isBlank()) {
                return ps;
            }
        }
        return ran;
    }
}
