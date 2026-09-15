package com.yapcore.setup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Runs operator setup scripts with Linux bash or Windows PowerShell when mapped.
 */
public final class SetupScriptRunner {

    private SetupScriptRunner() {
    }

    /**
     * Resolve and run a setup script.
     *
     * @param bashRelative path under root, e.g. {@code scripts/fetch-tebex.sh}
     * @param psRelative   optional Windows script, e.g. {@code scripts/windows/Nginx-Setup.ps1}
     * @param args         extra args (same for both when possible)
     */
    public static Map<String, Object> run(
            Path rootDir, String bashRelative, String psRelative, List<String> args, int timeoutSec)
            throws IOException, InterruptedException {
        Path root = rootDir.toAbsolutePath().normalize();
        List<String> extra = args == null ? List.of() : args;

        if (SetupOs.bashAvailable()) {
            Path script = root.resolve(bashRelative);
            if (Files.isRegularFile(script)) {
                List<String> cmd = new ArrayList<>();
                cmd.add("bash");
                cmd.add(script.toString());
                cmd.addAll(extra);
                return exec(root, cmd, timeoutSec, bashRelative);
            }
        }

        if (psRelative != null && !psRelative.isBlank() && SetupOs.powershellAvailable()) {
            Path script = root.resolve(psRelative);
            if (Files.isRegularFile(script)) {
                List<String> cmd = new ArrayList<>();
                cmd.add(SetupOs.powershellExe());
                cmd.add("-NoProfile");
                cmd.add("-ExecutionPolicy");
                cmd.add("Bypass");
                cmd.add("-File");
                cmd.add(script.toString());
                cmd.addAll(extra);
                return exec(root, cmd, timeoutSec, psRelative);
            }
        }

        Map<String, Object> fail = new LinkedHashMap<>();
        fail.put("ok", false);
        fail.put("error", missingMessage(root, bashRelative, psRelative));
        fail.put("output", "");
        fail.put("script", bashRelative);
        return fail;
    }

    public static Map<String, Object> runBash(
            Path rootDir, String bashRelative, List<String> args, int timeoutSec)
            throws IOException, InterruptedException {
        return run(rootDir, bashRelative, null, args, timeoutSec);
    }

    private static String missingMessage(Path root, String bashRelative, String psRelative) {
        boolean bashFile = Files.isRegularFile(root.resolve(bashRelative));
        if (!SetupOs.bashAvailable() && bashFile) {
            return "bash not found — install Git Bash/WSL, or run: " + bashRelative;
        }
        if (!bashFile && (psRelative == null || !Files.isRegularFile(root.resolve(psRelative)))) {
            return "missing script: " + bashRelative;
        }
        if (psRelative != null && !SetupOs.powershellAvailable()) {
            return "PowerShell not found for " + psRelative;
        }
        return "cannot run " + bashRelative;
    }

    private static Map<String, Object> exec(Path root, List<String> cmd, int timeoutSec, String label)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(root.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = p.waitFor(timeoutSec, TimeUnit.SECONDS);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("script", label);
        row.put("command", String.join(" ", cmd));
        if (!finished) {
            p.destroyForcibly();
            row.put("ok", false);
            row.put("error", "timed out after " + timeoutSec + "s");
            row.put("output", truncate(out, 4_000));
            return row;
        }
        row.put("ok", p.exitValue() == 0);
        row.put("exit", p.exitValue());
        row.put("output", truncate(out, 8_000));
        return row;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
