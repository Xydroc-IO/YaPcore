package com.yapcore.fleet.local;

import com.yapcore.fleet.model.FleetInstance;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Per-fleet-instance Minecraft world folders under the Folia tree
 * ({@code world}, {@code world_nether}, … or custom {@code level-name}).
 */
public final class InstanceWorldOps {

    private static final int MAX_ZIP_BYTES = 2 * 1024 * 1024 * 1024; // soft note; stream until done
    private static final long MAX_ENTRY_BYTES = 512L * 1024 * 1024;

    private InstanceWorldOps() {
    }

    public static Map<String, Object> status(Path rootDir, FleetInstance instance) throws IOException {
        Path dir = InstanceLayout.dir(rootDir, instance);
        String level = readLevelName(dir);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("instanceId", instance.id());
        out.put("instanceDir", dir.toString());
        out.put("levelName", level);
        out.put("levelSeed", readProp(dir, "level-seed", ""));
        out.put("levelType", readProp(dir, "level-type", "minecraft\\:normal"));
        out.put("worlds", listWorlds(dir));
        out.put("hint", "Stop the server before reset / create / import.");
        return out;
    }

    /**
     * Wipe overworld (+ optional dims) and set {@code level-type} so the next Start generates
     * a fresh world of that type (e.g. {@code minecraft:flat} for creative hubs).
     */
    public static Map<String, Object> swapLevelType(
            Path rootDir,
            FleetInstance instance,
            String levelType,
            boolean includeDims) throws IOException {
        String type = normalizeLevelType(levelType);
        Path dir = InstanceLayout.dir(rootDir, instance);
        String name = readLevelName(dir);
        deleteWorldSet(dir, name);
        if (includeDims) {
            deleteIfWorld(dir.resolve(name + "_nether"));
            deleteIfWorld(dir.resolve(name + "_the_end"));
        }
        Map<String, String> patch = new LinkedHashMap<>();
        patch.put("level-type", type);
        if (type.contains("flat")) {
            // Classic void-ish creative flat: one grass + dirt layers (Paper accepts empty {}).
            if (readProp(dir, "generator-settings", "{}").isBlank()
                    || "{}".equals(readProp(dir, "generator-settings", "{}"))) {
                patch.put("generator-settings", "{}");
            }
        }
        InstanceServerProps.patch(rootDir, instance, patch);
        Map<String, Object> out = status(rootDir, instance);
        out.put("action", "swap-level-type");
        out.put("levelType", type);
        out.put("deleted", name);
        out.put("note", "World data wiped. Start the server to generate " + type + ".");
        return out;
    }

    /** Accept {@code flat}, {@code FLAT}, {@code minecraft:flat}, etc. */
    public static String normalizeLevelType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "minecraft:normal";
        }
        String t = raw.trim().toLowerCase(Locale.ROOT).replace('\\', ':');
        return switch (t) {
            case "flat", "minecraft:flat", "superflat" -> "minecraft:flat";
            case "normal", "minecraft:normal", "default" -> "minecraft:normal";
            case "large_biomes", "largebiomes", "minecraft:large_biomes" -> "minecraft:large_biomes";
            case "amplified", "minecraft:amplified" -> "minecraft:amplified";
            case "single_biome", "minecraft:single_biome" -> "minecraft:single_biome";
            default -> t.contains(":") ? t : "minecraft:" + t;
        };
    }

    public static Map<String, Object> createNew(
            Path rootDir,
            FleetInstance instance,
            boolean wipeExisting,
            String levelName,
            String seed,
            String levelType) throws IOException {
        Path dir = InstanceLayout.dir(rootDir, instance);
        String name = sanitizeLevelName(levelName == null || levelName.isBlank()
                ? readLevelName(dir) : levelName);
        if (wipeExisting) {
            deleteWorldSet(dir, name);
        }
        Map<String, String> patch = new LinkedHashMap<>();
        patch.put("level-name", name);
        if (seed != null && !seed.isBlank()) {
            patch.put("level-seed", seed.trim());
        }
        if (levelType != null && !levelType.isBlank()) {
            patch.put("level-type", levelType.trim());
        }
        InstanceServerProps.patch(rootDir, instance, patch);
        Map<String, Object> out = status(rootDir, instance);
        out.put("action", "create-new");
        out.put("note", "World folders cleared (if wipe). Folia generates a fresh world on next Start.");
        return out;
    }

    public static Map<String, Object> reset(
            Path rootDir, FleetInstance instance, boolean includeDims) throws IOException {
        Path dir = InstanceLayout.dir(rootDir, instance);
        String name = readLevelName(dir);
        deleteWorldSet(dir, name);
        if (includeDims) {
            deleteIfWorld(dir.resolve(name + "_nether"));
            deleteIfWorld(dir.resolve(name + "_the_end"));
        }
        Map<String, Object> out = status(rootDir, instance);
        out.put("action", "reset");
        out.put("deleted", name);
        out.put("note", "Deleted world data. Start the server to generate a new one.");
        return out;
    }

    public static Map<String, Object> deleteNamed(
            Path rootDir, FleetInstance instance, String worldName) throws IOException {
        Path dir = InstanceLayout.dir(rootDir, instance);
        String name = sanitizeLevelName(worldName);
        Path target = dir.resolve(name).normalize();
        if (!target.startsWith(dir)) {
            throw new IOException("invalid world path");
        }
        if (!Files.isDirectory(target)) {
            throw new IOException("World not found: " + name);
        }
        deleteRecursive(target);
        Map<String, Object> out = status(rootDir, instance);
        out.put("action", "delete");
        out.put("deleted", name);
        return out;
    }

    /**
     * Import a world zip from a path on the chassis host (SCP the zip first, then point here).
     * Zip may be a folder with {@code level.dat} at root or one top-level directory.
     */
    public static Map<String, Object> importZipPath(
            Path rootDir, FleetInstance instance, String zipPath, String asLevelName)
            throws IOException {
        if (zipPath == null || zipPath.isBlank()) {
            throw new IOException("sourcePath required");
        }
        Path zip = Path.of(zipPath.trim()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(zip)) {
            throw new IOException("Zip not found: " + zip);
        }
        String lower = zip.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".zip")) {
            throw new IOException("Expected a .zip world archive");
        }
        Path dir = InstanceLayout.dir(rootDir, instance);
        String name = sanitizeLevelName(asLevelName == null || asLevelName.isBlank()
                ? readLevelName(dir) : asLevelName);
        Path dest = dir.resolve(name);
        if (Files.exists(dest)) {
            deleteRecursive(dest);
        }
        Files.createDirectories(dest);
        unzipWorld(zip, dest);
        InstanceServerProps.patch(rootDir, instance, Map.of("level-name", name));
        Map<String, Object> out = status(rootDir, instance);
        out.put("action", "import-zip");
        out.put("levelName", name);
        out.put("source", zip.toString());
        out.put("note", "Imported. Start the server to load this world.");
        return out;
    }

    /** Stream a zip upload into the instance world folder. */
    public static Map<String, Object> importZipStream(
            Path rootDir, FleetInstance instance, InputStream zipStream, String asLevelName)
            throws IOException {
        Path dir = InstanceLayout.dir(rootDir, instance);
        String name = sanitizeLevelName(asLevelName == null || asLevelName.isBlank()
                ? readLevelName(dir) : asLevelName);
        Path staging = Files.createTempFile("yap-world-", ".zip");
        try {
            try (OutputStream out = Files.newOutputStream(staging)) {
                zipStream.transferTo(out);
            }
            long size = Files.size(staging);
            if (size <= 0) {
                throw new IOException("Empty upload");
            }
            if (size > MAX_ZIP_BYTES) {
                throw new IOException("Zip too large");
            }
            Path dest = dir.resolve(name);
            if (Files.exists(dest)) {
                deleteRecursive(dest);
            }
            Files.createDirectories(dest);
            unzipWorld(staging, dest);
            InstanceServerProps.patch(rootDir, instance, Map.of("level-name", name));
            Map<String, Object> out = status(rootDir, instance);
            out.put("action", "upload-zip");
            out.put("levelName", name);
            out.put("bytes", size);
            out.put("note", "Uploaded. Start the server to load this world.");
            return out;
        } finally {
            Files.deleteIfExists(staging);
        }
    }

    static List<Map<String, Object>> listWorlds(Path instanceDir) throws IOException {
        List<Map<String, Object>> list = new ArrayList<>();
        if (!Files.isDirectory(instanceDir)) {
            return list;
        }
        try (var stream = Files.list(instanceDir)) {
            for (Path p : stream.toList()) {
                if (!Files.isDirectory(p)) {
                    continue;
                }
                if (!Files.isRegularFile(p.resolve("level.dat"))
                        && !Files.isRegularFile(p.resolve("level.dat_old"))) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", p.getFileName().toString());
                row.put("path", p.toString());
                row.put("bytes", dirSize(p));
                list.add(row);
            }
        }
        return list;
    }

    static String readLevelName(Path instanceDir) {
        return readProp(instanceDir, "level-name", "world");
    }

    private static String readProp(Path instanceDir, String key, String def) {
        Path file = instanceDir.resolve("server.properties");
        if (!Files.isRegularFile(file)) {
            return def;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        } catch (IOException e) {
            return def;
        }
        String v = p.getProperty(key, def);
        return v == null || v.isBlank() ? def : v.trim();
    }

    private static void deleteWorldSet(Path instanceDir, String levelName) throws IOException {
        deleteIfWorld(instanceDir.resolve(levelName));
        deleteIfWorld(instanceDir.resolve(levelName + "_nether"));
        deleteIfWorld(instanceDir.resolve(levelName + "_the_end"));
    }

    private static void deleteIfWorld(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            deleteRecursive(path);
        }
    }

    private static void deleteRecursive(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static long dirSize(Path root) {
        try {
            final long[] total = {0};
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    total[0] += attrs.size();
                    return FileVisitResult.CONTINUE;
                }
            });
            return total[0];
        } catch (IOException e) {
            return 0;
        }
    }

    private static void unzipWorld(Path zip, Path destDir) throws IOException {
        Path tmp = Files.createTempDirectory(destDir.getParent(), "yap-unz-");
        try {
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String raw = entry.getName().replace('\\', '/');
                    if (raw.contains("..") || raw.startsWith("/")) {
                        throw new IOException("Unsafe zip entry: " + raw);
                    }
                    Path out = tmp.resolve(raw).normalize();
                    if (!out.startsWith(tmp)) {
                        throw new IOException("Zip path escape: " + raw);
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(out);
                    } else {
                        Files.createDirectories(out.getParent());
                        long copied = 0;
                        try (OutputStream os = Files.newOutputStream(out)) {
                            byte[] buf = new byte[8192];
                            int n;
                            while ((n = zis.read(buf)) >= 0) {
                                copied += n;
                                if (copied > MAX_ENTRY_BYTES) {
                                    throw new IOException("Zip entry too large: " + raw);
                                }
                                os.write(buf, 0, n);
                            }
                        }
                    }
                    zis.closeEntry();
                }
            }
            Path levelRoot = findLevelRoot(tmp);
            if (levelRoot == null) {
                throw new IOException("No level.dat in zip — export a Minecraft world folder as zip");
            }
            copyTree(levelRoot, destDir);
        } finally {
            deleteRecursive(tmp);
        }
    }

    private static Path findLevelRoot(Path extracted) throws IOException {
        if (Files.isRegularFile(extracted.resolve("level.dat"))) {
            return extracted;
        }
        try (var stream = Files.list(extracted)) {
            List<Path> dirs = stream.filter(Files::isDirectory).toList();
            for (Path d : dirs) {
                if (Files.isRegularFile(d.resolve("level.dat"))) {
                    return d;
                }
            }
        }
        return null;
    }

    private static void copyTree(Path src, Path dest) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                Path rel = src.relativize(dir);
                Files.createDirectories(dest.resolve(rel.toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path rel = src.relativize(file);
                Files.copy(file, dest.resolve(rel.toString()), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static String sanitizeLevelName(String raw) throws IOException {
        if (raw == null || raw.isBlank()) {
            throw new IOException("level name required");
        }
        String s = raw.trim();
        if (!s.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,31}")) {
            throw new IOException("invalid level name: " + raw);
        }
        return s;
    }
}
