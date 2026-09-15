package com.yapcore.setup;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Detect OS + available script hosts (bash / PowerShell). */
public final class SetupOs {

    private SetupOs() {
    }

    public static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win");
    }

    public static boolean bashAvailable() {
        return commandOk(List.of("bash", "-lc", "true"), 5)
                || Files.isExecutable(Path.of("/bin/bash"))
                || Files.isRegularFile(Path.of("/usr/bin/bash"));
    }

    public static boolean powershellAvailable() {
        if (!isWindows()) {
            return false;
        }
        return commandOk(List.of("powershell", "-NoProfile", "-Command", "exit 0"), 8)
                || commandOk(List.of("pwsh", "-NoProfile", "-Command", "exit 0"), 8);
    }

    public static String powershellExe() {
        if (commandOk(List.of("pwsh", "-NoProfile", "-Command", "exit 0"), 5)) {
            return "pwsh";
        }
        return "powershell";
    }

    public static Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("os", isWindows() ? "windows" : "linux");
        out.put("bashAvailable", bashAvailable());
        out.put("powershellAvailable", powershellAvailable());
        out.put("hint", isWindows() && !bashAvailable()
                ? "Install Git Bash or WSL for full setup scripts; PowerShell covers DB/nginx/start."
                : "Bash available for setup scripts.");
        return out;
    }

    static boolean commandOk(List<String> cmd, int timeoutSec) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.getInputStream().readAllBytes();
            return p.waitFor(timeoutSec, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
