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
 * YaP rank pack — native {@code YaPPerms} ({@code yap-perms.jar}).
 * Reference: {@code src/main/resources/ranks/yap-ranks-reference.txt}
 */
public final class YapRanks {
    private static final Logger LOG = Logger.getLogger("YaPcore");
    private static final String CLASSPATH = "/ranks/yap-ranks-reference.txt";
    private static final String RELATIVE = "examples/yapperms/ranks-reference.txt";
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
                    throw new IOException("Rank reference not found (" + RELATIVE + " or classpath " + CLASSPATH + ")");
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

    public static boolean yapPermsInstalled(Path pluginsDir) {
        return jarPresent(pluginsDir, "yap-perms");
    }

    private static boolean jarPresent(Path pluginsDir, String token) {
        if (!Files.isDirectory(pluginsDir)) {
            return false;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsDir, "*.jar")) {
            for (Path jar : stream) {
                String name = jar.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.contains(token)) {
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
     * @param dispatch returns command result (Folia/Paper console must be ready)
     */
    public static ApplyResult apply(Path rootDir, Function<String, String> dispatch, boolean force)
            throws IOException {
        if (!force && isApplied(rootDir)) {
            return new ApplyResult(false, 0, List.of(),
                    "Already applied (config/yap-ranks-applied). Use force to re-run.");
        }
        Path pluginsDir = rootDir.resolve("plugins");
        if (!yapPermsInstalled(pluginsDir)) {
            return new ApplyResult(false, 0, List.of(),
                    "yap-perms.jar not found in plugins/. Run: gradle installProductDefaults");
        }
        String result = dispatch.apply("yapperm applypack");
        markApplied(rootDir);
        LOG.info("YaP ranks pack applied via native YaPPerms");
        return new ApplyResult(true, 1, List.of("> yapperm applypack\n" + nullToEmpty(result)),
                "Applied native YaPPerms starter pack. Assign with: /yapperm user <name> parent set vip");
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    public record ApplyResult(boolean ran, int commandCount, List<String> lines, String summary) {
    }
}
