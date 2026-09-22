package com.yapcore.link.bedrock.translator;

import java.util.ArrayList;
import java.util.List;
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

    /**
     * Extra palette keys for blocks whose JE properties do not match Bedrock.
     * Tried after {@link #remap(String)}. A miss still falls back to the block id,
     * which is one default facing — that is the "everything faces south" look.
     */
    static List<String> extraKeys(String jeState) {
        List<String> out = new ArrayList<>(4);
        if (jeState == null || !jeState.contains("[")) {
            return out;
        }
        int bracket = jeState.indexOf('[');
        String id = jeState.substring(0, bracket);
        if (id.startsWith("minecraft:")) {
            id = id.substring("minecraft:".length());
        }
        id = id.toLowerCase(Locale.ROOT);
        String props = jeState.substring(bracket + 1, jeState.endsWith("]") ? jeState.length() - 1 : jeState.length());
        String beId = bedrockBlockId(id);
        String facing = propOpt(props, "facing");
        if (facing != null) {
            out.add("minecraft:" + beId + "[minecraft:cardinal_direction=" + facing + "]");
            out.add("minecraft:" + beId + "[facing_direction=" + facingDirection(facing) + "]");
            // Observers store facing as a word, not the 0–5 code.
            out.add("minecraft:" + beId + "[minecraft:facing_direction=" + facing + ",powered_bit="
                    + truthy(prop(props, "powered", "false")) + "]");
        }
        String axis = propOpt(props, "axis");
        if (axis != null) {
            out.add("minecraft:" + beId + "[pillar_axis=" + axis + "]");
        }
        if (id.endsWith("_door") || "wooden_door".equals(id)) {
            int hinge = "right".equals(prop(props, "hinge", "left")) ? 1 : 0;
            int open = truthy(prop(props, "open", "false"));
            int upper = "upper".equals(prop(props, "half", "lower")) ? 1 : 0;
            String face = facing != null ? facing : "south";
            out.add("minecraft:" + beId + "[door_hinge_bit=" + hinge
                    + ",minecraft:cardinal_direction=" + face
                    + ",open_bit=" + open
                    + ",upper_block_bit=" + upper + "]");
        }
        if (id.endsWith("trapdoor") || "trapdoor".equals(id)) {
            // 0 west, 1 east, 2 north, 3 south (Bedrock "side of the block").
            String face = facing != null ? facing : "north";
            int dir = switch (face) {
                case "west" -> 0;
                case "east" -> 1;
                case "south" -> 3;
                default -> 2;
            };
            int open = truthy(prop(props, "open", "false"));
            int up = "top".equals(prop(props, "half", "bottom")) ? 1 : 0;
            out.add("minecraft:" + beId + "[direction=" + dir
                    + ",open_bit=" + open
                    + ",upside_down_bit=" + up + "]");
        }
        if (id.endsWith("_slab")) {
            String type = prop(props, "type", "bottom");
            if ("double".equals(type) && beId.endsWith("_slab")) {
                String doubled = beId.substring(0, beId.length() - "_slab".length()) + "_double_slab";
                out.add("minecraft:" + doubled);
            } else {
                String half = "top".equals(type) ? "top" : "bottom";
                out.add("minecraft:" + beId + "[minecraft:vertical_half=" + half + "]");
            }
        }
        if ("lever".equals(id)) {
            String face = prop(props, "face", "wall");
            String point = facing != null ? facing : "north";
            boolean eastWest = "east".equals(point) || "west".equals(point);
            String dir = switch (face) {
                case "floor" -> eastWest ? "down_east_west" : "down_north_south";
                case "ceiling" -> eastWest ? "up_east_west" : "up_north_south";
                default -> point;
            };
            out.add("minecraft:lever[lever_direction=" + dir
                    + ",open_bit=" + truthy(prop(props, "powered", "false")) + "]");
        }
        if (id.endsWith("lantern")) {
            out.add("minecraft:" + beId + "[hanging=" + truthy(prop(props, "hanging", "false")) + "]");
        }
        if (id.endsWith("_button")) {
            out.add("minecraft:" + beId + "[button_pressed_bit="
                    + truthy(prop(props, "powered", "false"))
                    + ",facing_direction=" + facingDirection(facing != null ? facing : "down") + "]");
        }
        if (id.endsWith("fence_gate") || "fence_gate".equals(id)) {
            String face = facing != null ? facing : "south";
            out.add("minecraft:" + beId + "[in_wall_bit=" + truthy(prop(props, "in_wall", "false"))
                    + ",minecraft:cardinal_direction=" + face
                    + ",open_bit=" + truthy(prop(props, "open", "false")) + "]");
        }
        if (id.endsWith("rail")) {
            int dir = switch (prop(props, "shape", "north_south")) {
                case "east_west" -> 1;
                case "ascending_east" -> 2;
                case "ascending_west" -> 3;
                case "ascending_north" -> 4;
                case "ascending_south" -> 5;
                case "south_east" -> 6;
                case "south_west" -> 7;
                case "north_west" -> 8;
                case "north_east" -> 9;
                default -> 0;
            };
            out.add("minecraft:" + beId + "[rail_direction=" + dir + "]");
        }
        if ("repeater".equals(id)) {
            int delay = 1;
            try {
                delay = Integer.parseInt(prop(props, "delay", "1"));
            } catch (NumberFormatException ignored) {
                delay = 1;
            }
            delay = Math.max(1, Math.min(4, delay)) - 1;
            boolean powered = truthy(prop(props, "powered", "false")) == 1;
            String rid = powered ? "powered_repeater" : "unpowered_repeater";
            String face = facing != null ? facing : "north";
            out.add("minecraft:" + rid + "[minecraft:cardinal_direction=" + face
                    + ",repeater_delay=" + delay + "]");
        }
        return out;
    }

    /** Oak doors/trapdoors/gates use the old Bedrock names. */
    private static String bedrockBlockId(String jeId) {
        return switch (jeId) {
            case "oak_door", "wooden_door" -> "wooden_door";
            case "oak_trapdoor", "trapdoor" -> "trapdoor";
            case "oak_fence_gate", "fence_gate" -> "fence_gate";
            case "oak_button", "wooden_button" -> "wooden_button";
            case "dark_oak_door" -> "dark_oak_door";
            default -> jeId;
        };
    }

    /** Bedrock {@code facing_direction}: down=0 up=1 north=2 south=3 west=4 east=5. */
    private static int facingDirection(String facing) {
        return switch (facing) {
            case "up" -> 1;
            case "north" -> 2;
            case "south" -> 3;
            case "west" -> 4;
            case "east" -> 5;
            default -> 0;
        };
    }

    private static int truthy(String value) {
        return "true".equals(value) || "1".equals(value) ? 1 : 0;
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

    private static String propOpt(String props, String key) {
        Matcher m = PROP.matcher(props);
        while (m.find()) {
            if (key.equalsIgnoreCase(m.group(1))) {
                return m.group(2).toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }
}
