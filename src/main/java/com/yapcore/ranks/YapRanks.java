package com.yapcore.ranks;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * LuckPerms starter pack — groups, track, weights, chat meta, YaP permission nodes.
 * Canonical text: {@code examples/luckperms/apply-yap-ranks.txt} (also on classpath).
 */
public final class YapRanks {
    private static final Logger LOG = Logger.getLogger("YaPcore");
    private static final String CLASSPATH = "/ranks/apply-yap-ranks.txt";
    private static final String RELATIVE = "examples/luckperms/apply-yap-ranks.txt";
    private static final String MARKER = "config/yap-ranks-applied";

    private YapRanks() {
    }

    public static List<String> loadCommands(Path rootDir) throws IOException {
        Path file = rootDir.resolve(RELATIVE);
        String text;
        if (Files.isRegularFile(file)) {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } else {
            try (InputStream in = YapRanks.class.getResourceAsStream(CLASSPATH)) {
                if (in == null) {
                    throw new IOException("Rank pack not found (" + RELATIVE + " or classpath " + CLASSPATH + ")");
                }
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        List<String> out = new ArrayList<>();
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("/")) {
                line = line.substring(1).trim();
            }
            out.add(line);
        }
        return out;
    }

    public static boolean luckPermsInstalled(Path pluginsDir) {
        if (!Files.isDirectory(pluginsDir)) {
            return false;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsDir, "*.jar")) {
            for (Path jar : stream) {
                String name = jar.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.contains("luckperms")) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    public static boolean isApplied(Path rootDir) {
        return Files.isRegularFile(rootDir.resolve(MARKER));
    }

    public static void markApplied(Path rootDir) throws IOException {
        Path marker = rootDir.resolve(MARKER);
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "applied\n", StandardCharsets.UTF_8);
    }

    public static void clearApplied(Path rootDir) throws IOException {
        Files.deleteIfExists(rootDir.resolve(MARKER));
    }

    /**
     * @param dispatch returns command result (Paper/LP must be ready)
     */
    public static ApplyResult apply(Path rootDir, Function<String, String> dispatch, boolean force)
            throws IOException {
        if (!force && isApplied(rootDir)) {
            return new ApplyResult(false, 0, List.of(),
                    "Already applied (config/yap-ranks-applied). Use force to re-run.");
        }
        List<String> commands = loadCommands(rootDir);
        List<String> results = new ArrayList<>();
        int ok = 0;
        for (String cmd : commands) {
            String result = dispatch.apply(cmd);
            results.add("> " + cmd + (result == null || result.isBlank() ? "" : "\n" + result));
            ok++;
        }
        markApplied(rootDir);
        LOG.info("YaP ranks pack applied (" + ok + " LuckPerms commands)");
        return new ApplyResult(true, ok, results, "Applied " + ok + " commands. Assign with: lp user <name> parent set vip");
    }

    public record ApplyResult(boolean ran, int commandCount, List<String> lines, String summary) {
    }
}
