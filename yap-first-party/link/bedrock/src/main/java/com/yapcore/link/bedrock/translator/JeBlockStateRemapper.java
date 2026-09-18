package com.yapcore.link.bedrock.translator;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remap JE block-state strings to Bedrock palette keys when property names differ.
 *
 * <p>Without this, stairs/signs miss the hashed palette and fall back to a single default
 * orientation (stairs look wrong; signs face the wrong way / blank text still needs actors).
 *
 * <p>Palette NBT stores bit fields as byte {@code 0}/{@code 1} (see {@link JeToBedrockBlockMapper}
 * {@code formatStateValue}), not {@code true}/{@code false}. Sign block ids also diverge:
 * JE {@code oak_sign} → BE {@code standing_sign}; JE {@code birch_sign} → {@code birch_standing_sign};
 * JE {@code dark_oak_*} → BE {@code darkoak_*}.
 */
final class JeBlockStateRemapper {

    private static final Pattern STAIRS = Pattern.compile(
            "^minecraft:([a-z0-9_]+_stairs)\\[(.*)]$", Pattern.CASE_INSENSITIVE);
    private static final Pattern WALL_SIGN = Pattern.compile(
            "^minecraft:([a-z0-9_]*wall_sign)\\[(.*)]$", Pattern.CASE_INSENSITIVE);
    private static final Pattern STANDING_SIGN = Pattern.compile(
            "^minecraft:([a-z0-9_]+_sign)\\[(.*)]$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROP = Pattern.compile("([a-z0-9_]+)=([a-z0-9_]+)");

    private JeBlockStateRemapper() {
    }

    /**
     * @return remapped Bedrock-style state string, or the original if no remap applies
     */
    static String remap(String jeState) {
        if (jeState == null || jeState.isBlank() || !jeState.contains("[")) {
            return jeState;
        }
        String s = jeState.trim();
        Matcher stairs = STAIRS.matcher(s);
        if (stairs.matches()) {
            return remapStairs(stairs.group(1).toLowerCase(Locale.ROOT), stairs.group(2));
        }
        Matcher wall = WALL_SIGN.matcher(s);
        if (wall.matches()) {
            return remapWallSign(wall.group(1).toLowerCase(Locale.ROOT), wall.group(2));
        }
        Matcher standing = STANDING_SIGN.matcher(s);
        if (standing.matches()) {
            String id = standing.group(1).toLowerCase(Locale.ROOT);
            if (id.contains("wall_sign") || id.contains("hanging_sign")) {
                return s;
            }
            return remapStandingSign(id, standing.group(2));
        }
        return s;
    }

    private static String remapStairs(String id, String props) {
        String facing = prop(props, "facing", "east");
        String half = prop(props, "half", "bottom");
        int weirdo = switch (facing) {
            case "west" -> 1;
            case "south" -> 2;
            case "north" -> 3;
            default -> 0; // east
        };
        // Palette keys use numeric bits ("0"/"1"), not boolean strings.
        int upside = "top".equals(half) ? 1 : 0;
        return "minecraft:" + id + "[upside_down_bit=" + upside + ",weirdo_direction=" + weirdo + "]";
    }

    private static String remapWallSign(String id, String props) {
        String facing = prop(props, "facing", "north");
        int dir = switch (facing) {
            case "east" -> 5;
            case "west" -> 4;
            case "south" -> 3;
            case "north" -> 2;
            default -> 2;
        };
        String beId = bedrockSignId(id, true);
        return "minecraft:" + beId + "[facing_direction=" + dir + "]";
    }

    private static String remapStandingSign(String id, String props) {
        int rotation = 0;
        try {
            rotation = Integer.parseInt(prop(props, "rotation", "0"));
        } catch (NumberFormatException ignored) {
            rotation = 0;
        }
        rotation = Math.floorMod(rotation, 16);
        String beId = bedrockSignId(id, false);
        return "minecraft:" + beId + "[ground_sign_direction=" + rotation + "]";
    }

    private static String bedrockSignId(String jeId, boolean wall) {
        if (wall) {
            if (jeId.equals("wall_sign") || jeId.equals("oak_wall_sign")) {
                return "wall_sign";
            }
            if (jeId.equals("dark_oak_wall_sign")) {
                return "darkoak_wall_sign";
            }
            if (jeId.endsWith("_wall_sign")) {
                return jeId;
            }
            return "wall_sign";
        }
        if (jeId.equals("sign") || jeId.equals("standing_sign") || jeId.equals("oak_sign")) {
            return "standing_sign";
        }
        if (jeId.equals("dark_oak_sign")) {
            return "darkoak_standing_sign";
        }
        if (jeId.endsWith("_sign") && !jeId.contains("wall") && !jeId.contains("hanging")) {
            String wood = jeId.substring(0, jeId.length() - "_sign".length());
            return wood + "_standing_sign";
        }
        return "standing_sign";
    }

    private static String prop(String props, String key, String def) {
        Matcher m = PROP.matcher(props);
        while (m.find()) {
            if (key.equalsIgnoreCase(m.group(1))) {
                return m.group(2).toLowerCase(Locale.ROOT);
            }
        }
        return def;
    }
}
