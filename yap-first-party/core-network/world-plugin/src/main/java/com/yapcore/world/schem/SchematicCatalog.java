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

/** Lists and inspects schematic files on disk. */
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
        String name = filename.replace(".yschem", "").replace(".schem", "");
        String format = filename.toLowerCase(Locale.ROOT).endsWith(".schem") ? "schem" : "yschem";
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
        Path yschem = dir.resolve(safe + ".yschem");
        if (Files.isRegularFile(yschem)) {
            return yschem;
        }
        Path schem = dir.resolve(safe + ".schem");
        if (Files.isRegularFile(schem)) {
            return schem;
        }
        return null;
    }

    public static Schematic load(Path file) throws IOException {
        if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".schem")) {
            return SpongeSchematicImporter.importFile(file);
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
        String ext = source.getFileName().toString().endsWith(".schem") ? ".schem" : ".yschem";
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
        String ext = source.getFileName().toString().endsWith(".schem") ? ".schem" : ".yschem";
        Path target = dir.resolve(sanitize(to) + ext);
        if (Files.exists(target)) {
            throw new IOException("Target name already exists");
        }
        Files.copy(source, target);
    }

    public static Path importBytes(Path dir, String filename, byte[] data) throws IOException {
        Files.createDirectories(dir);
        String lower = filename.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".schem") && !lower.endsWith(".yschem")) {
            filename = sanitize(filename) + ".schem";
        } else {
            filename = sanitize(filename.replace(".schem", "").replace(".yschem", ""))
                    + (lower.endsWith(".yschem") ? ".yschem" : ".schem");
        }
        Path dest = dir.resolve(filename);
        Files.write(dest, data);
        if (filename.endsWith(".schem")) {
            Schematic imported = SpongeSchematicImporter.importFile(dest);
            Path yschem = dir.resolve(filename.replace(".schem", ".yschem"));
            SchematicIO.save(yschem, imported);
        }
        return dest;
    }

    static boolean isSchematicFile(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return Files.isRegularFile(p) && (n.endsWith(".yschem") || n.endsWith(".schem"));
    }

    public static String sanitize(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "-");
    }
}
