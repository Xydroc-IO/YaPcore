package com.yapcore.world.schem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/** Lists and inspects schematic files on disk (all supported formats). */
public final class SchematicCatalog {

    private SchematicCatalog() {
    }

    public static List<Map<String, Object>> list(Path dir) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(SchematicCatalog::isSchematicFile)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .forEach(p -> out.add(inspect(p)));
        } catch (IOException ignored) {
        }
        return out;
    }

    public static Map<String, Object> inspect(Path file) {
        Map<String, Object> info = new LinkedHashMap<>();
        String filename = file.getFileName().toString();
        String lower = filename.toLowerCase(Locale.ROOT);
        String format = formatOf(lower);
        String name = stripExtension(filename);
        info.put("name", name);
        info.put("format", format);
        info.put("filename", filename);
        try {
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
            info.put("bytes", attrs.size());
            info.put("modified", attrs.lastModifiedTime().toMillis());
        } catch (IOException e) {
            info.put("bytes", 0L);
            info.put("modified", 0L);
        }
        try {
            Schematic schem = load(file);
            Schematic.Bounds b = schem.bounds();
            info.put("blocks", schem.blocks().size());
            info.put("sizeX", b.sizeX());
            info.put("sizeY", b.sizeY());
            info.put("sizeZ", b.sizeZ());
            info.put("world", schem.world());
        } catch (Exception e) {
            info.put("blocks", 0);
            info.put("sizeX", 0);
            info.put("sizeY", 0);
            info.put("sizeZ", 0);
            info.put("error", e.getMessage());
        }
        return info;
    }

    public static Path resolve(Path dir, String name) {
        String safe = sanitize(name);
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".yschem") || lower.endsWith(".schem")
                || lower.endsWith(".schematic") || lower.endsWith(".litematic")) {
            Path raw = dir.resolve(name);
            if (Files.isRegularFile(raw)) {
                return raw;
            }
            raw = dir.resolve(safe + extensionOf(lower));
            return Files.isRegularFile(raw) ? raw : null;
        }
        Path yschem = dir.resolve(safe + ".yschem");
        if (Files.isRegularFile(yschem)) {
            return yschem;
        }
        Path schem = dir.resolve(safe + ".schem");
        if (Files.isRegularFile(schem)) {
            return schem;
        }
        Path schematic = dir.resolve(safe + ".schematic");
        if (Files.isRegularFile(schematic)) {
            return schematic;
        }
        Path litematic = dir.resolve(safe + ".litematic");
        if (Files.isRegularFile(litematic)) {
            return litematic;
        }
        return null;
    }

    /**
     * Load any supported schematic format into a YaP {@link Schematic}.
     * Used by //schem, GUI, web API, and the WorldEdit clipboard loader.
     */
    public static Schematic load(Path file) throws IOException {
        String n = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (n.endsWith(".schem")) {
            return SpongeSchematicImporter.importFile(file);
        }
        if (n.endsWith(".schematic")) {
            return LegacySchematicImporter.importFile(file);
        }
        if (n.endsWith(".litematic")) {
            return LitematicImporter.importFile(file);
        }
        return SchematicIO.load(file);
    }

    public static void delete(Path dir, String name) throws IOException {
        Path file = resolve(dir, name);
        if (file == null) {
            throw new IOException("Schematic not found");
        }
        Files.delete(file);
    }

    public static void rename(Path dir, String from, String to) throws IOException {
        Path source = resolve(dir, from);
        if (source == null) {
            throw new IOException("Schematic not found");
        }
        String ext = extensionOf(source.getFileName().toString().toLowerCase(Locale.ROOT));
        Path target = dir.resolve(sanitize(to) + ext);
        if (Files.exists(target)) {
            throw new IOException("Target name already exists");
        }
        Files.move(source, target);
    }

    public static void duplicate(Path dir, String from, String to) throws IOException {
        Path source = resolve(dir, from);
        if (source == null) {
            throw new IOException("Schematic not found");
        }
        String ext = extensionOf(source.getFileName().toString().toLowerCase(Locale.ROOT));
        Path target = dir.resolve(sanitize(to) + ext);
        if (Files.exists(target)) {
            throw new IOException("Target name already exists");
        }
        Files.copy(source, target);
    }

    public static Path importBytes(Path dir, String filename, byte[] data) throws IOException {
        Files.createDirectories(dir);
        String lower = filename.toLowerCase(Locale.ROOT);
        String ext;
        if (lower.endsWith(".litematic")) {
            ext = ".litematic";
        } else if (lower.endsWith(".schematic")) {
            ext = ".schematic";
        } else if (lower.endsWith(".yschem")) {
            ext = ".yschem";
        } else {
            ext = ".schem";
        }
        String base = sanitize(stripExtension(filename));
        Path dest = dir.resolve(base + ext);
        Files.write(dest, data);
        // Validate + mirror foreign formats into native .yschem for tooling that only reads yschem
        if (!ext.equals(".yschem")) {
            Schematic imported = load(dest);
            Path yschem = dir.resolve(base + ".yschem");
            SchematicIO.save(yschem, imported);
        }
        return dest;
    }

    static boolean isSchematicFile(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return Files.isRegularFile(p) && (n.endsWith(".yschem") || n.endsWith(".schem")
                || n.endsWith(".schematic") || n.endsWith(".litematic"));
    }

    public static String sanitize(String name) {
        String base = stripExtension(name);
        return base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "-");
    }

    static String stripExtension(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        for (String ext : List.of(".litematic", ".schematic", ".yschem", ".schem")) {
            if (lower.endsWith(ext)) {
                return filename.substring(0, filename.length() - ext.length());
            }
        }
        return filename;
    }

    static String formatOf(String lowerFilename) {
        if (lowerFilename.endsWith(".litematic")) {
            return "litematic";
        }
        if (lowerFilename.endsWith(".schematic")) {
            return "schematic";
        }
        if (lowerFilename.endsWith(".schem")) {
            return "schem";
        }
        return "yschem";
    }

    static String extensionOf(String lowerFilename) {
        return switch (formatOf(lowerFilename)) {
            case "litematic" -> ".litematic";
            case "schematic" -> ".schematic";
            case "schem" -> ".schem";
            default -> ".yschem";
        };
    }
}
