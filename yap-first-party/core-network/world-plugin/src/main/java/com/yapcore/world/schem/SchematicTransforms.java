package com.yapcore.world.schem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Y-axis rotate / axis flip for {@link Schematic} snapshots (paste preview + clipboard).
 * Rotates relative positions, entity yaw, and common block-state properties
 * ({@code facing}, {@code axis}, sign {@code rotation}).
 */
public final class SchematicTransforms {

    private static final Pattern PROP = Pattern.compile("([a-z0-9_]+)=([a-z0-9_]+)");

    private SchematicTransforms() {
    }

    /** Clockwise Y rotation in 90° steps. Degrees may be negative (CCW). */
    public static Schematic rotateY(Schematic src, int degrees) {
        if (src == null) {
            return null;
        }
        int turns = normalizeTurns(degrees);
        if (turns == 0) {
            return src;
        }
        List<Schematic.BlockEntry> rotated = new ArrayList<>(src.blocks().size());
        int minDx = Integer.MAX_VALUE;
        int minDz = Integer.MAX_VALUE;
        for (Schematic.BlockEntry e : src.blocks()) {
            int dx = e.dx();
            int dy = e.dy();
            int dz = e.dz();
            for (int t = 0; t < turns; t++) {
                int ndx = dz;
                int ndz = -dx;
                dx = ndx;
                dz = ndz;
            }
            rotated.add(new Schematic.BlockEntry(dx, dy, dz, rotateEncodedY(e.encoded(), turns), e.tileNbt()));
            minDx = Math.min(minDx, dx);
            minDz = Math.min(minDz, dz);
        }
        if (minDx == Integer.MAX_VALUE) {
            minDx = 0;
            minDz = 0;
        }
        List<Schematic.BlockEntry> normalized = new ArrayList<>(rotated.size());
        for (Schematic.BlockEntry e : rotated) {
            normalized.add(new Schematic.BlockEntry(
                    e.dx() - minDx, e.dy(), e.dz() - minDz, e.encoded(), e.tileNbt()));
        }
        List<Schematic.EntityEntry> ents = rotateEntities(src.entities(), turns, minDx, minDz);
        return new Schematic(src.world(), src.anchorX(), src.anchorY(), src.anchorZ(), normalized, ents);
    }

    public static Schematic flip(Schematic src, char axis) {
        if (src == null) {
            return null;
        }
        Schematic.Bounds b = src.bounds();
        char a = Character.toLowerCase(axis);
        if (a != 'x' && a != 'y' && a != 'z') {
            return null;
        }
        List<Schematic.BlockEntry> flipped = new ArrayList<>(src.blocks().size());
        for (Schematic.BlockEntry e : src.blocks()) {
            int dx = e.dx();
            int dy = e.dy();
            int dz = e.dz();
            if (a == 'x') {
                dx = b.sizeX() - 1 - dx;
            } else if (a == 'z') {
                dz = b.sizeZ() - 1 - dz;
            } else {
                dy = b.sizeY() - 1 - dy;
            }
            flipped.add(new Schematic.BlockEntry(dx, dy, dz, flipEncoded(e.encoded(), a), e.tileNbt()));
        }
        List<Schematic.EntityEntry> ents = new ArrayList<>();
        for (Schematic.EntityEntry e : src.entities()) {
            int dx = e.dx();
            int dy = e.dy();
            int dz = e.dz();
            float yaw = e.yaw();
            if (a == 'x') {
                dx = b.sizeX() - 1 - dx;
                yaw = -yaw;
            } else if (a == 'z') {
                dz = b.sizeZ() - 1 - dz;
                yaw = 180f - yaw;
            } else {
                dy = b.sizeY() - 1 - dy;
            }
            ents.add(new Schematic.EntityEntry(dx, dy, dz, e.type(), yaw, e.pitch(), e.nbt()));
        }
        return new Schematic(src.world(), src.anchorX(), src.anchorY(), src.anchorZ(), flipped, ents);
    }

    public static int normalizeTurns(int degrees) {
        int turns = degrees / 90;
        // Non-multiples of 90 snap toward nearest quarter for UI/buttons.
        if (degrees % 90 != 0) {
            turns = Math.round(degrees / 90f);
        }
        return ((turns % 4) + 4) % 4;
    }

    public static String rotateEncodedY(String encoded, int turns) {
        if (encoded == null || encoded.isBlank() || turns == 0) {
            return encoded;
        }
        int t = ((turns % 4) + 4) % 4;
        if (t == 0) {
            return encoded;
        }
        int sep = encoded.indexOf('|');
        String mat = sep >= 0 ? encoded.substring(0, sep) : encoded;
        String data = sep >= 0 && sep + 1 < encoded.length() ? encoded.substring(sep + 1) : "";
        if (data.isBlank() || !data.contains("[")) {
            // Try minecraft:block[props] form without Material|
            if (encoded.contains("[") && encoded.contains("=")) {
                return rewriteProps(encoded, t);
            }
            return encoded;
        }
        return mat + "|" + rewriteProps(data, t);
    }

    static String flipEncoded(String encoded, char axis) {
        if (encoded == null || encoded.isBlank()) {
            return encoded;
        }
        char a = Character.toLowerCase(axis);
        int sep = encoded.indexOf('|');
        String mat = sep >= 0 ? encoded.substring(0, sep) : encoded;
        String data = sep >= 0 && sep + 1 < encoded.length() ? encoded.substring(sep + 1) : "";
        if (data.isBlank() || !data.contains("[")) {
            if (encoded.contains("[") && encoded.contains("=")) {
                return rewriteFlipProps(encoded, a);
            }
            return encoded;
        }
        return mat + "|" + rewriteFlipProps(data, a);
    }

    private static String rewriteProps(String data, int turnsCw) {
        Matcher m = PROP.matcher(data);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1).toLowerCase(Locale.ROOT);
            String val = m.group(2).toLowerCase(Locale.ROOT);
            String next = val;
            if ("facing".equals(key)) {
                next = rotateCardinal(val, turnsCw);
            } else if ("axis".equals(key) && turnsCw != 0) {
                next = rotateAxis(val, turnsCw);
            } else if ("rotation".equals(key) && turnsCw != 0) {
                try {
                    int r = Integer.parseInt(val);
                    next = Integer.toString((r + turnsCw * 4) & 15);
                } catch (NumberFormatException ignored) {
                    // keep
                }
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(key + "=" + next));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String rewriteFlipProps(String data, char axis) {
        Matcher m = PROP.matcher(data);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1).toLowerCase(Locale.ROOT);
            String val = m.group(2).toLowerCase(Locale.ROOT);
            String next = val;
            if ("facing".equals(key)) {
                next = flipCardinal(val, axis);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(key + "=" + next));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String flipCardinal(String facing, char axis) {
        if (axis == 'x') {
            return switch (facing) {
                case "east" -> "west";
                case "west" -> "east";
                default -> facing;
            };
        }
        if (axis == 'z') {
            return switch (facing) {
                case "north" -> "south";
                case "south" -> "north";
                default -> facing;
            };
        }
        return facing;
    }

    private static String rotateCardinal(String facing, int turns) {
        String[] order = {"north", "east", "south", "west"};
        int idx = indexOf(order, facing);
        if (idx < 0) {
            return facing; // up/down/etc.
        }
        return order[(idx + turns) % 4];
    }

    private static String rotateAxis(String axis, int turns) {
        if ("y".equals(axis)) {
            return "y";
        }
        if (!"x".equals(axis) && !"z".equals(axis)) {
            return axis;
        }
        // Odd turns swap x ↔ z
        if ((turns % 2) == 1) {
            return "x".equals(axis) ? "z" : "x";
        }
        return axis;
    }

    private static int indexOf(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].equals(v)) {
                return i;
            }
        }
        return -1;
    }

    private static List<Schematic.EntityEntry> rotateEntities(List<Schematic.EntityEntry> entities,
                                                              int turns, int minDx, int minDz) {
        List<Schematic.EntityEntry> out = new ArrayList<>(entities.size());
        for (Schematic.EntityEntry e : entities) {
            int dx = e.dx();
            int dz = e.dz();
            float yaw = e.yaw();
            for (int t = 0; t < turns; t++) {
                int ndx = dz;
                int ndz = -dx;
                dx = ndx;
                dz = ndz;
                yaw += 90f;
            }
            out.add(new Schematic.EntityEntry(dx - minDx, e.dy(), dz - minDz, e.type(), yaw, e.pitch(), e.nbt()));
        }
        return out;
    }
}
