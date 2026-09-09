package com.yapcore.world;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remaps legacy / renamed / mangled block ids so schematics and WorldEdit pastes
 * work on modern Paper.
 * <p>
 * Handles common paste killers:
 * <ul>
 *   <li>{@code minecraft:grass} → {@code short_grass}</li>
 *   <li>{@code minecraft:player_head{facing=north}} (NBT braces) → wall head / bare head</li>
 *   <li>Truncated strings like {@code minecraft:player_head{facing}</li>
 * </ul>
 */
public final class BlockStateAliases {

    private static final Pattern KV = Pattern.compile("([a-zA-Z0-9_]+)\\s*=\\s*([^,}\\]]+)");

    private static final Map<String, String> ID_ALIASES = Map.ofEntries(
            Map.entry("grass", "short_grass"),
            Map.entry("minecraft:grass", "minecraft:short_grass"),
            Map.entry("tallgrass", "short_grass"),
            Map.entry("minecraft:tallgrass", "minecraft:short_grass"),
            Map.entry("double_plant", "tall_grass"),
            Map.entry("minecraft:double_plant", "minecraft:tall_grass"),
            Map.entry("grass_path", "dirt_path"),
            Map.entry("minecraft:grass_path", "minecraft:dirt_path"),
            Map.entry("snow_layer", "snow"),
            Map.entry("minecraft:snow_layer", "minecraft:snow"),
            Map.entry("portal", "nether_portal"),
            Map.entry("minecraft:portal", "minecraft:nether_portal"),
            Map.entry("stationary_water", "water"),
            Map.entry("minecraft:stationary_water", "minecraft:water"),
            Map.entry("stationary_lava", "lava"),
            Map.entry("minecraft:stationary_lava", "minecraft:lava"),
            Map.entry("mob_spawner", "spawner"),
            Map.entry("minecraft:mob_spawner", "minecraft:spawner"),
            Map.entry("skull", "player_head"),
            Map.entry("minecraft:skull", "minecraft:player_head")
    );

    private BlockStateAliases() {
    }

    /** Rewrite legacy / brace-mangled block id into a Bukkit-parseable form. */
    public static String normalize(String state) {
        if (state == null || state.isBlank()) {
            return "minecraft:air";
        }
        String raw = state.trim();

        // Split id from either [block states] or {nbt / mangled props}
        String id;
        String suffix = "";
        int square = raw.indexOf('[');
        int brace = raw.indexOf('{');
        if (square >= 0 && (brace < 0 || square < brace)) {
            id = raw.substring(0, square);
            suffix = raw.substring(square);
        } else if (brace >= 0) {
            id = raw.substring(0, brace);
            suffix = raw.substring(brace);
        } else {
            id = raw;
        }

        id = remapId(id.trim());
        String idKey = id.toLowerCase(Locale.ROOT);
        boolean isHead = idKey.endsWith(":player_head") || idKey.equals("player_head")
                || idKey.endsWith(":skull") || idKey.equals("skull")
                || idKey.endsWith(":player_wall_head") || idKey.equals("player_wall_head")
                || idKey.endsWith("_head") || idKey.endsWith("_wall_head");

        if (suffix.startsWith("{")) {
            String inner = stripBraces(suffix);
            String facing = findProp(inner, "facing");
            String rotation = findProp(inner, "rotation");

            // Item/skull NBT (SkullOwner, profile, …) — drop; keep facing/rotation if present
            boolean looksLikeBlockProps = facing != null || rotation != null
                    || findProp(inner, "waterlogged") != null
                    || findProp(inner, "powered") != null;
            boolean looksLikeItemNbt = inner.toLowerCase(Locale.ROOT).contains("skullowner")
                    || inner.toLowerCase(Locale.ROOT).contains("profile")
                    || inner.toLowerCase(Locale.ROOT).contains("custom_name");

            if (isHead && facing != null) {
                // Floor player_head doesn't use facing — wall variant does
                String wallId = toWallHead(id);
                return wallId + "[facing=" + facing.toLowerCase(Locale.ROOT) + "]";
            }
            if (isHead && rotation != null) {
                return ensureNamespaced(id) + "[rotation=" + rotation + "]";
            }
            if (looksLikeBlockProps && !looksLikeItemNbt) {
                String bracket = toBracketProps(inner);
                if (!bracket.isEmpty()) {
                    return ensureNamespaced(id) + bracket;
                }
            }
            // Bare block id (ignore NBT / truncated braces)
            return ensureNamespaced(id);
        }

        if (suffix.startsWith("[")) {
            // Remap id but keep bracket props; fix player_head[facing=…] → wall head
            if (isHead && suffix.toLowerCase(Locale.ROOT).contains("facing=")) {
                return toWallHead(id) + suffix;
            }
            return ensureNamespaced(id) + suffix;
        }

        return ensureNamespaced(id);
    }

    /** Parse block data with legacy aliases; never throws for recoverable ids. */
    public static BlockData create(String state) {
        String normalized = normalize(state);
        try {
            return Bukkit.createBlockData(normalized);
        } catch (IllegalArgumentException first) {
            // Try without properties
            int cut = indexOfProp(normalized);
            String bare = cut > 0 ? normalized.substring(0, cut) : normalized;
            try {
                BlockData data = Bukkit.createBlockData(bare);
                return applyKnownProps(data, normalized);
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
            Material mat = matchMaterial(bare);
            if (mat != null && mat.isBlock()) {
                BlockData data = mat.createBlockData();
                return applyKnownProps(data, normalized);
            }
            // Last resort: player/skull → PLAYER_HEAD
            String lower = bare.toLowerCase(Locale.ROOT);
            if (lower.contains("player_head") || lower.endsWith(":skull") || lower.equals("skull")) {
                if (lower.contains("wall") || normalized.toLowerCase(Locale.ROOT).contains("facing=")) {
                    return applyKnownProps(Material.PLAYER_WALL_HEAD.createBlockData(), normalized);
                }
                return Material.PLAYER_HEAD.createBlockData();
            }
            throw first;
        }
    }

    public static BlockData createOrAir(String state) {
        try {
            return create(state);
        } catch (IllegalArgumentException e) {
            return Material.AIR.createBlockData();
        }
    }

    private static String remapId(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        String mapped = ID_ALIASES.get(key);
        if (mapped == null && !key.contains(":")) {
            mapped = ID_ALIASES.get("minecraft:" + key);
            if (mapped != null && mapped.startsWith("minecraft:")) {
                mapped = mapped.substring("minecraft:".length());
            }
        }
        return mapped != null ? mapped : id;
    }

    private static String ensureNamespaced(String id) {
        if (id.contains(":")) {
            return id.toLowerCase(Locale.ROOT);
        }
        return "minecraft:" + id.toLowerCase(Locale.ROOT);
    }

    private static String toWallHead(String id) {
        String lower = id.toLowerCase(Locale.ROOT);
        if (lower.contains("wall_head")) {
            return ensureNamespaced(id);
        }
        if (lower.endsWith("player_head") || lower.equals("player_head") || lower.endsWith(":skull")) {
            return "minecraft:player_wall_head";
        }
        // zombie_head → zombie_wall_head etc.
        if (lower.endsWith("_head") && !lower.contains("wall")) {
            int colon = lower.indexOf(':');
            String name = colon >= 0 ? lower.substring(colon + 1) : lower;
            if (name.endsWith("_head")) {
                name = name.substring(0, name.length() - "_head".length()) + "_wall_head";
                return "minecraft:" + name;
            }
        }
        return ensureNamespaced(id);
    }

    private static String stripBraces(String suffix) {
        String s = suffix.trim();
        if (s.startsWith("{")) {
            s = s.substring(1);
        }
        if (s.endsWith("}")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.trim();
    }

    private static String findProp(String inner, String key) {
        if (inner == null || inner.isBlank()) {
            return null;
        }
        Matcher m = KV.matcher(inner);
        while (m.find()) {
            if (m.group(1).equalsIgnoreCase(key)) {
                String v = m.group(2).trim();
                // Truncated / junk
                if (v.isEmpty() || v.equalsIgnoreCase(key)) {
                    return null;
                }
                return v.replace("\"", "").replace("'", "");
            }
        }
        // Truncated: "{facing" with no value — treat as missing
        return null;
    }

    private static String toBracketProps(String inner) {
        Matcher m = KV.matcher(inner);
        StringBuilder sb = new StringBuilder("[");
        boolean any = false;
        while (m.find()) {
            String k = m.group(1).toLowerCase(Locale.ROOT);
            String v = m.group(2).trim().replace("\"", "").replace("'", "");
            if (v.isEmpty()) {
                continue;
            }
            // Skip item NBT keys
            if (k.contains("skull") || k.equals("profile") || k.equals("custom_name")
                    || k.equals("display") || k.equals("tag")) {
                continue;
            }
            if (any) {
                sb.append(',');
            }
            sb.append(k).append('=').append(v.toLowerCase(Locale.ROOT));
            any = true;
        }
        if (!any) {
            return "";
        }
        sb.append(']');
        return sb.toString();
    }

    private static int indexOfProp(String s) {
        int a = s.indexOf('[');
        int b = s.indexOf('{');
        if (a < 0) {
            return b;
        }
        if (b < 0) {
            return a;
        }
        return Math.min(a, b);
    }

    private static Material matchMaterial(String bare) {
        String name = bare;
        if (name.contains(":")) {
            name = name.substring(name.indexOf(':') + 1);
        }
        Material mat = Material.matchMaterial(name);
        if (mat != null) {
            return mat;
        }
        return Material.matchMaterial(bare);
    }

    private static BlockData applyKnownProps(BlockData data, String normalized) {
        String lower = normalized.toLowerCase(Locale.ROOT);
        try {
            if (data instanceof Directional dir) {
                String facing = extractBracketProp(lower, "facing");
                if (facing != null) {
                    try {
                        dir.setFacing(BlockFace.valueOf(facing.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            if (data instanceof Rotatable rot) {
                String rotation = extractBracketProp(lower, "rotation");
                if (rotation != null) {
                    try {
                        // rotation 0-15 maps to BlockFace via Bukkit rotatable — try parse as face name first
                        rot.setRotation(BlockFace.valueOf(rotation.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException ignored) {
                        // numeric rotation left as default
                    }
                }
            }
        } catch (Throwable ignored) {
            // immutable clones etc.
        }
        return data;
    }

    private static String extractBracketProp(String normalizedLower, String key) {
        int start = normalizedLower.indexOf('[');
        if (start < 0) {
            return findProp(normalizedLower, key);
        }
        int end = normalizedLower.indexOf(']', start);
        String inner = end > start
                ? normalizedLower.substring(start + 1, end)
                : normalizedLower.substring(start + 1);
        return findProp(inner, key);
    }
}
