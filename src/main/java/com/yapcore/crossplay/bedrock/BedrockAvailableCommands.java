package com.yapcore.crossplay.bedrock;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bedrock 1.21.50 {@code available_commands} builder — vanilla-style catalog for
 * autocomplete / chat UX (first-party, not PMMP/Geyser source).
 *
 * <p>Parameter encoding matches minecraft-data split fields:
 * {@code value_type:lu16} + {@code enum_type:lu16} (valid=16, enum=48, soft_enum=1040).
 */
public final class BedrockAvailableCommands {

    /** Standard arg types (low 16 bits when enum_type=valid). */
    public static final int TYPE_INT = 1;
    public static final int TYPE_FLOAT = 3;
    public static final int TYPE_VALUE = 4;
    public static final int TYPE_OPERATOR = 6;
    public static final int TYPE_TARGET = 8;
    public static final int TYPE_WILDCARD_TARGET = 10;
    public static final int TYPE_STRING = 44;
    public static final int TYPE_BLOCK_POSITION = 52;
    public static final int TYPE_POSITION = 53;
    public static final int TYPE_MESSAGE = 55;
    public static final int TYPE_RAW_TEXT = 58;
    public static final int TYPE_JSON = 62;
    public static final int TYPE_COMMAND = 74;

    public static final int ENUM_VALID = 16;
    public static final int ENUM_HARD = 48;
    public static final int ENUM_SOFT = 1040;

    public static final int PERM_NORMAL = 0;
    public static final int PERM_OPERATOR = 1;

    private final List<String> enumValues = new ArrayList<>();
    private final Map<String, Integer> enumValueIndex = new LinkedHashMap<>();
    private final List<HardEnum> hardEnums = new ArrayList<>();
    private final List<SoftEnum> softEnums = new ArrayList<>();
    private final List<CommandDef> commands = new ArrayList<>();

    private BedrockAvailableCommands() {
    }

    /** Default YaPcore / vanilla-adjacent command set. */
    public static ByteBuf encodeDefault() {
        BedrockAvailableCommands b = new BedrockAvailableCommands();
        b.buildDefault();
        return b.encodePacket();
    }

    private void buildDefault() {
        int gamemode = hardEnum("Gamemode", "survival", "creative", "adventure", "spectator",
                "s", "c", "a", "sp", "0", "1", "2", "3");
        int difficulty = hardEnum("Difficulty", "peaceful", "easy", "normal", "hard", "p", "e", "n", "h",
                "0", "1", "2", "3");
        int bool = hardEnum("Boolean", "true", "false");
        int weather = hardEnum("Weather", "clear", "rain", "thunder");
        int timeSpec = hardEnum("TimeSpec", "day", "night", "noon", "midnight", "sunrise", "sunset");
        int timeMode = hardEnum("TimeMode", "add", "set", "query");
        int timeQuery = hardEnum("TimeQuery", "daytime", "gametime", "day");
        int gamerules = softEnum("BoolGameRules",
                "commandblocksenabled", "commandblockoutput", "dodaylightcycle", "doentitydrops",
                "dofiretick", "doimmediaterespawn", "domobloot", "domobspawning", "dotiledrops",
                "doweathercycle", "drowningdamage", "falldamage", "firedamage", "freezedamage",
                "keepinventory", "mobgriefing", "naturalregeneration", "pvp", "respawnblocksexplode",
                "sendcommandfeedback", "showbordereffect", "showcoordinates", "showdeathmessages",
                "showtags");

        cmd("help", "Shows help / lists commands", PERM_NORMAL,
                overload(),
                overload(std("command", TYPE_STRING, true)),
                overload(std("page", TYPE_INT, true)));
        cmd("list", "Lists players on the server", PERM_NORMAL, overload());
        cmd("me", "Displays a message about yourself", PERM_NORMAL,
                overload(std("message", TYPE_MESSAGE, false)));
        cmd("say", "Sends a message in the name of the server", PERM_OPERATOR,
                overload(std("message", TYPE_MESSAGE, false)));
        cmd("tell", "Sends a private message", PERM_NORMAL,
                overload(std("player", TYPE_TARGET, false), std("message", TYPE_MESSAGE, false)));
        cmd("msg", "Sends a private message", PERM_NORMAL,
                overload(std("player", TYPE_TARGET, false), std("message", TYPE_MESSAGE, false)));
        cmd("w", "Sends a private message", PERM_NORMAL,
                overload(std("player", TYPE_TARGET, false), std("message", TYPE_MESSAGE, false)));

        cmd("gamemode", "Sets a player's game mode", PERM_OPERATOR,
                overload(enumParam("gameMode", gamemode, false)),
                overload(enumParam("gameMode", gamemode, false), std("player", TYPE_TARGET, true)));
        cmd("difficulty", "Sets the difficulty level", PERM_OPERATOR,
                overload(enumParam("difficulty", difficulty, false)));
        cmd("time", "Changes or queries the world's game time", PERM_OPERATOR,
                overload(enumParam("mode", timeMode, false), enumParam("amount", timeSpec, false)),
                overload(enumParam("mode", timeMode, false), std("amount", TYPE_INT, false)),
                overload(enumParam("mode", timeMode, false), enumParam("query", timeQuery, false)));
        cmd("weather", "Sets the weather", PERM_OPERATOR,
                overload(enumParam("type", weather, false)),
                overload(enumParam("type", weather, false), std("duration", TYPE_INT, true)));

        cmd("tp", "Teleports entities", PERM_OPERATOR,
                overload(std("destination", TYPE_TARGET, false)),
                overload(std("destination", TYPE_POSITION, false)),
                overload(std("victim", TYPE_TARGET, false), std("destination", TYPE_TARGET, false)),
                overload(std("victim", TYPE_TARGET, false), std("destination", TYPE_POSITION, false)));
        cmd("teleport", "Teleports entities", PERM_OPERATOR,
                overload(std("destination", TYPE_TARGET, false)),
                overload(std("destination", TYPE_POSITION, false)),
                overload(std("victim", TYPE_TARGET, false), std("destination", TYPE_TARGET, false)),
                overload(std("victim", TYPE_TARGET, false), std("destination", TYPE_POSITION, false)));

        cmd("give", "Gives an item to a player", PERM_OPERATOR,
                overload(std("player", TYPE_TARGET, false), std("itemName", TYPE_STRING, false),
                        std("amount", TYPE_INT, true), std("data", TYPE_INT, true)));
        cmd("clear", "Clears items from player inventory", PERM_OPERATOR,
                overload(),
                overload(std("player", TYPE_TARGET, true)),
                overload(std("player", TYPE_TARGET, false), std("itemName", TYPE_STRING, true),
                        std("data", TYPE_INT, true), std("maxCount", TYPE_INT, true)));
        cmd("kill", "Kills entities", PERM_OPERATOR,
                overload(),
                overload(std("target", TYPE_TARGET, true)));
        cmd("kick", "Kicks a player from the server", PERM_OPERATOR,
                overload(std("player", TYPE_TARGET, false), std("reason", TYPE_MESSAGE, true)));
        cmd("op", "Grants operator status", PERM_OPERATOR,
                overload(std("player", TYPE_TARGET, false)));
        cmd("deop", "Revokes operator status", PERM_OPERATOR,
                overload(std("player", TYPE_TARGET, false)));

        cmd("enchant", "Adds an enchantment to a player's selected item", PERM_OPERATOR,
                overload(std("player", TYPE_TARGET, false), std("enchantmentName", TYPE_STRING, false),
                        std("level", TYPE_INT, true)));
        cmd("effect", "Add/remove status effects", PERM_OPERATOR,
                overload(std("player", TYPE_TARGET, false), std("effect", TYPE_STRING, false),
                        std("seconds", TYPE_INT, true), std("amplifier", TYPE_INT, true),
                        enumParam("hideParticles", bool, true)),
                overload(std("player", TYPE_TARGET, false), std("clear", TYPE_STRING, false)));
        cmd("xp", "Adds or removes player experience", PERM_OPERATOR,
                overload(std("amount", TYPE_STRING, false), std("player", TYPE_TARGET, true)));
        cmd("experience", "Adds or removes player experience", PERM_OPERATOR,
                overload(std("amount", TYPE_STRING, false), std("player", TYPE_TARGET, true)));

        cmd("summon", "Summons an entity", PERM_OPERATOR,
                overload(std("entityType", TYPE_STRING, false), std("spawnPos", TYPE_POSITION, true)));
        cmd("setblock", "Changes a block to another block type", PERM_OPERATOR,
                overload(std("position", TYPE_BLOCK_POSITION, false), std("tileName", TYPE_STRING, false),
                        std("tileData", TYPE_INT, true)));
        cmd("fill", "Fills a region with a specific block", PERM_OPERATOR,
                overload(std("from", TYPE_BLOCK_POSITION, false), std("to", TYPE_BLOCK_POSITION, false),
                        std("tileName", TYPE_STRING, false), std("tileData", TYPE_INT, true)));
        cmd("clone", "Clones blocks from one region to another", PERM_OPERATOR,
                overload(std("begin", TYPE_BLOCK_POSITION, false), std("end", TYPE_BLOCK_POSITION, false),
                        std("destination", TYPE_BLOCK_POSITION, false)));
        cmd("locate", "Finds the nearest structure or biome", PERM_OPERATOR,
                overload(std("feature", TYPE_STRING, false)));
        cmd("spawnpoint", "Sets players' spawn point", PERM_OPERATOR,
                overload(),
                overload(std("player", TYPE_TARGET, true)),
                overload(std("player", TYPE_TARGET, false), std("spawnPos", TYPE_POSITION, true)));
        cmd("setworldspawn", "Sets the world spawn", PERM_OPERATOR,
                overload(),
                overload(std("spawnPoint", TYPE_POSITION, true)));

        cmd("gamerule", "Sets or queries a game rule value", PERM_OPERATOR,
                overload(softParam("rule", gamerules, false)),
                overload(softParam("rule", gamerules, false), enumParam("value", bool, false)),
                overload(softParam("rule", gamerules, false), std("value", TYPE_INT, false)));

        cmd("playsound", "Plays a sound", PERM_OPERATOR,
                overload(std("sound", TYPE_STRING, false), std("player", TYPE_TARGET, true),
                        std("position", TYPE_POSITION, true), std("volume", TYPE_FLOAT, true),
                        std("pitch", TYPE_FLOAT, true)));
        cmd("stopsound", "Stops a sound", PERM_OPERATOR,
                overload(std("player", TYPE_TARGET, false), std("sound", TYPE_STRING, true)));
        cmd("title", "Controls screen titles", PERM_OPERATOR,
                overload(std("player", TYPE_TARGET, false), std("titleName", TYPE_STRING, false),
                        std("titleText", TYPE_MESSAGE, true)));
        cmd("particle", "Creates a particle emitter", PERM_OPERATOR,
                overload(std("effect", TYPE_STRING, false), std("position", TYPE_POSITION, false)));
        cmd("tag", "Manages scoreboard tags", PERM_OPERATOR,
                overload(std("targets", TYPE_TARGET, false), std("action", TYPE_STRING, false),
                        std("tagName", TYPE_STRING, true)));
        cmd("scoreboard", "Tracks and displays scores", PERM_OPERATOR,
                overload(std("action", TYPE_STRING, false), std("args", TYPE_RAW_TEXT, true)));
        cmd("function", "Runs commands from a function file", PERM_OPERATOR,
                overload(std("name", TYPE_STRING, false)));
        cmd("reload", "Reloads functions / packs", PERM_OPERATOR, overload());
        cmd("stop", "Stops the server", PERM_OPERATOR, overload());
        cmd("whitelist", "Manages the server whitelist", PERM_OPERATOR,
                overload(std("action", TYPE_STRING, false), std("name", TYPE_STRING, true)));
    }

    private int intern(String value) {
        Integer existing = enumValueIndex.get(value);
        if (existing != null) {
            return existing;
        }
        int idx = enumValues.size();
        enumValues.add(value);
        enumValueIndex.put(value, idx);
        return idx;
    }

    private int hardEnum(String name, String... values) {
        int[] idxs = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            idxs[i] = intern(values[i]);
        }
        hardEnums.add(new HardEnum(name, idxs));
        return hardEnums.size() - 1;
    }

    private int softEnum(String name, String... values) {
        softEnums.add(new SoftEnum(name, List.of(values)));
        return softEnums.size() - 1;
    }

    private void cmd(String name, String description, int permission, Overload... overloads) {
        commands.add(new CommandDef(name, description, permission, List.of(overloads)));
    }

    private static Overload overload(Param... params) {
        return new Overload(List.of(params));
    }

    private static Param std(String name, int type, boolean optional) {
        return new Param(name, type, ENUM_VALID, optional);
    }

    private static Param enumParam(String name, int enumIndex, boolean optional) {
        return new Param(name, enumIndex, ENUM_HARD, optional);
    }

    private static Param softParam(String name, int softIndex, boolean optional) {
        return new Param(name, softIndex, ENUM_SOFT, optional);
    }

    private ByteBuf encodePacket() {
        ByteBuf out = Unpooled.buffer(4096);
        BedrockPacketCodec.writeUnsignedVarInt(out, BedrockPacketIds.AVAILABLE_COMMANDS.id);

        int valuesLen = enumValues.size();
        BedrockPacketCodec.writeUnsignedVarInt(out, valuesLen);
        // _enum_type is contextual (byte/short/int) — no bytes written
        for (String v : enumValues) {
            BedrockPacketCodec.writeString(out, v);
        }

        BedrockPacketCodec.writeUnsignedVarInt(out, 0); // chained_subcommand_values
        BedrockPacketCodec.writeUnsignedVarInt(out, 0); // suffixes

        BedrockPacketCodec.writeUnsignedVarInt(out, hardEnums.size());
        for (HardEnum e : hardEnums) {
            BedrockPacketCodec.writeString(out, e.name);
            BedrockPacketCodec.writeUnsignedVarInt(out, e.valueIndexes.length);
            for (int idx : e.valueIndexes) {
                writeEnumValueIndex(out, idx, valuesLen);
            }
        }

        BedrockPacketCodec.writeUnsignedVarInt(out, 0); // chained_subcommands

        BedrockPacketCodec.writeUnsignedVarInt(out, commands.size());
        for (CommandDef c : commands) {
            BedrockPacketCodec.writeString(out, c.name);
            BedrockPacketCodec.writeString(out, c.description);
            out.writeShortLE(0); // flags
            out.writeByte(c.permission);
            out.writeIntLE(-1); // alias none
            BedrockPacketCodec.writeUnsignedVarInt(out, 0); // chained_subcommand_offsets
            BedrockPacketCodec.writeUnsignedVarInt(out, c.overloads.size());
            for (Overload ol : c.overloads) {
                out.writeBoolean(false); // chaining
                BedrockPacketCodec.writeUnsignedVarInt(out, ol.params.size());
                for (Param p : ol.params) {
                    BedrockPacketCodec.writeString(out, p.name);
                    out.writeShortLE(p.valueType);
                    out.writeShortLE(p.enumType);
                    out.writeBoolean(p.optional);
                    out.writeByte(0); // CommandFlags options
                }
            }
        }

        BedrockPacketCodec.writeUnsignedVarInt(out, softEnums.size()); // dynamic_enums
        for (SoftEnum se : softEnums) {
            BedrockPacketCodec.writeString(out, se.name);
            BedrockPacketCodec.writeUnsignedVarInt(out, se.values.size());
            for (String v : se.values) {
                BedrockPacketCodec.writeString(out, v);
            }
        }

        BedrockPacketCodec.writeUnsignedVarInt(out, 0); // enum_constraints
        return out;
    }

    private static void writeEnumValueIndex(ByteBuf out, int index, int valuesLen) {
        if (valuesLen <= 0xff) {
            out.writeByte(index);
        } else if (valuesLen <= 0xffff) {
            out.writeShortLE(index);
        } else {
            out.writeIntLE(index);
        }
    }

    private record HardEnum(String name, int[] valueIndexes) {
    }

    private record SoftEnum(String name, List<String> values) {
    }

    private record CommandDef(String name, String description, int permission, List<Overload> overloads) {
    }

    private record Overload(List<Param> params) {
    }

    private record Param(String name, int valueType, int enumType, boolean optional) {
    }
}
