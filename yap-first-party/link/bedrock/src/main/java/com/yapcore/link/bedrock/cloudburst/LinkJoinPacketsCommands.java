package com.yapcore.link.bedrock.cloudburst;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.cloudburstmc.protocol.bedrock.data.command.CommandData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumConstraint;
import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOverloadData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandParam;
import org.cloudburstmc.protocol.bedrock.data.command.CommandParamData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandPermission;
import org.cloudburstmc.protocol.bedrock.packet.AvailableCommandsPacket;

/**
 * AvailableCommands catalog builders (split from {@link LinkJoinPackets}).
 */
final class LinkJoinPacketsCommands {

    private LinkJoinPacketsCommands() {}

    /** Empty AvailableCommands catalog — prefer {@link #availableCommandsVanilla()}. */
    static AvailableCommandsPacket availableCommandsEmpty() {
        return new AvailableCommandsPacket();
    }

    /**
     * Vanilla-essentials AvailableCommands so Bedrock chat shows real Minecraft commands
     * (empty stub = "0 commands"). Permission bits: ANY for social; GAME_DIRECTORS for OP tools.
     */
    static AvailableCommandsPacket availableCommandsVanilla() {
        AvailableCommandsPacket packet = new AvailableCommandsPacket();
        List<CommandData> cmds = new ArrayList<>();
        cmds.add(cmd("help", "Shows help / lists commands", CommandPermission.ANY,
                overload(),
                overload(param("command", CommandParam.STRING, true))));
        cmds.add(cmd("list", "Lists players on the server", CommandPermission.ANY, overload()));
        cmds.add(cmd("me", "Displays a message about yourself", CommandPermission.ANY,
                overload(param("message", CommandParam.MESSAGE, false))));
        cmds.add(cmd("tell", "Sends a private message", CommandPermission.ANY,
                overload(param("player", CommandParam.TARGET, false),
                        param("message", CommandParam.MESSAGE, false))));
        cmds.add(cmd("msg", "Sends a private message", CommandPermission.ANY,
                overload(param("player", CommandParam.TARGET, false),
                        param("message", CommandParam.MESSAGE, false))));
        cmds.add(cmd("w", "Sends a private message", CommandPermission.ANY,
                overload(param("player", CommandParam.TARGET, false),
                        param("message", CommandParam.MESSAGE, false))));

        CommandEnumData gamemodeEnum = hardEnum("Gamemode",
                "survival", "creative", "adventure", "spectator", "s", "c", "a", "sp", "0", "1", "2", "3");
        cmds.add(cmd("gamemode", "Sets a player's game mode", CommandPermission.GAME_DIRECTORS,
                overload(enumParam("gameMode", gamemodeEnum, false)),
                overload(enumParam("gameMode", gamemodeEnum, false),
                        param("player", CommandParam.TARGET, true))));
        cmds.add(cmd("tp", "Teleports entities", CommandPermission.GAME_DIRECTORS,
                overload(param("destination", CommandParam.TARGET, false)),
                overload(param("destination", CommandParam.POSITION, false)),
                overload(param("victim", CommandParam.TARGET, false),
                        param("destination", CommandParam.TARGET, false)),
                overload(param("victim", CommandParam.TARGET, false),
                        param("destination", CommandParam.POSITION, false))));
        cmds.add(cmd("teleport", "Teleports entities", CommandPermission.GAME_DIRECTORS,
                overload(param("destination", CommandParam.TARGET, false)),
                overload(param("destination", CommandParam.POSITION, false))));
        cmds.add(cmd("give", "Gives an item to a player", CommandPermission.GAME_DIRECTORS,
                overload(param("player", CommandParam.TARGET, false),
                        param("itemName", CommandParam.STRING, false),
                        param("amount", CommandParam.INT, true))));
        cmds.add(cmd("clear", "Clears items from player inventory", CommandPermission.GAME_DIRECTORS,
                overload(),
                overload(param("player", CommandParam.TARGET, true))));
        cmds.add(cmd("kill", "Kills entities", CommandPermission.GAME_DIRECTORS,
                overload(),
                overload(param("target", CommandParam.TARGET, true))));
        cmds.add(cmd("time", "Changes or queries the world's game time", CommandPermission.GAME_DIRECTORS,
                overload(param("amount", CommandParam.INT, false))));
        cmds.add(cmd("weather", "Sets the weather", CommandPermission.GAME_DIRECTORS,
                overload(param("type", CommandParam.STRING, false))));
        cmds.add(cmd("effect", "Adds or removes status effects", CommandPermission.GAME_DIRECTORS,
                overload(param("player", CommandParam.TARGET, false),
                        param("effect", CommandParam.STRING, false),
                        param("seconds", CommandParam.INT, true))));
        cmds.add(cmd("xp", "Adds or removes player experience", CommandPermission.GAME_DIRECTORS,
                overload(param("amount", CommandParam.INT, false),
                        param("player", CommandParam.TARGET, true))));
        cmds.add(cmd("enchant", "Enchants an item", CommandPermission.GAME_DIRECTORS,
                overload(param("player", CommandParam.TARGET, false),
                        param("enchantment", CommandParam.STRING, false),
                        param("level", CommandParam.INT, true))));
        cmds.add(cmd("summon", "Summons an entity", CommandPermission.GAME_DIRECTORS,
                overload(param("entityType", CommandParam.STRING, false),
                        param("spawnPos", CommandParam.POSITION, true))));
        cmds.add(cmd("setblock", "Places a block", CommandPermission.GAME_DIRECTORS,
                overload(param("position", CommandParam.BLOCK_POSITION, false),
                        param("tileName", CommandParam.STRING, false))));
        cmds.add(cmd("fill", "Fills a region with a block", CommandPermission.GAME_DIRECTORS,
                overload(param("from", CommandParam.BLOCK_POSITION, false),
                        param("to", CommandParam.BLOCK_POSITION, false),
                        param("tileName", CommandParam.STRING, false))));
        cmds.add(cmd("op", "Grants operator status", CommandPermission.GAME_DIRECTORS,
                overload(param("player", CommandParam.TARGET, false))));
        cmds.add(cmd("deop", "Revokes operator status", CommandPermission.GAME_DIRECTORS,
                overload(param("player", CommandParam.TARGET, false))));
        cmds.add(cmd("kick", "Kicks a player", CommandPermission.GAME_DIRECTORS,
                overload(param("player", CommandParam.TARGET, false),
                        param("reason", CommandParam.MESSAGE, true))));
        cmds.add(cmd("ban", "Bans a player", CommandPermission.GAME_DIRECTORS,
                overload(param("player", CommandParam.STRING, false),
                        param("reason", CommandParam.MESSAGE, true))));
        cmds.add(cmd("pardon", "Pardons a banned player", CommandPermission.GAME_DIRECTORS,
                overload(param("player", CommandParam.STRING, false))));
        cmds.add(cmd("whitelist", "Manages the server whitelist", CommandPermission.GAME_DIRECTORS,
                overload(param("action", CommandParam.STRING, false),
                        param("player", CommandParam.STRING, true))));
        cmds.add(cmd("seed", "Displays the world seed", CommandPermission.GAME_DIRECTORS, overload()));
        cmds.add(cmd("gamerule", "Sets or queries a game rule", CommandPermission.GAME_DIRECTORS,
                overload(param("rule", CommandParam.STRING, false),
                        param("value", CommandParam.STRING, true))));

        packet.getCommands().addAll(cmds);
        return packet;
    }

    /**
     * Vanilla essentials plus extra literal command names (plugin commands from JE tree /
     * plugin.yml). Each extra gets a single optional-message overload so Bedrock can tab-complete
     * {@code /dungeon} etc. Typed commands already reach Folia via CommandRequest → chat_command.
     */
    static AvailableCommandsPacket availableCommandsVanillaPlus(Iterable<String> extraNames) {
        AvailableCommandsPacket packet = availableCommandsVanilla();
        if (extraNames == null) {
            return packet;
        }
        java.util.HashSet<String> have = new java.util.HashSet<>();
        for (CommandData existing : packet.getCommands()) {
            if (existing != null && existing.getName() != null) {
                have.add(existing.getName().toLowerCase(java.util.Locale.ROOT));
            }
        }
        for (String raw : extraNames) {
            if (raw == null) {
                continue;
            }
            String name = raw.trim().toLowerCase(java.util.Locale.ROOT);
            if (name.startsWith("/")) {
                name = name.substring(1);
            }
            if (name.isEmpty() || name.length() > 32 || !name.matches("[a-z][a-z0-9_]*")) {
                continue;
            }
            if (!have.add(name)) {
                continue;
            }
            packet.getCommands().add(cmd(name, "Server command /" + name, CommandPermission.ANY,
                    overload(),
                    overload(param("args", CommandParam.MESSAGE, true))));
        }
        return packet;
    }

    private static CommandData cmd(String name, String desc, CommandPermission perm,
                                   CommandOverloadData... overloads) {
        return new CommandData(name, desc, Set.of(CommandData.Flag.NOT_CHEAT), perm,
                null, Collections.emptyList(), overloads);
    }

    private static CommandOverloadData overload(CommandParamData... params) {
        return new CommandOverloadData(false, params);
    }

    private static CommandParamData param(String name, CommandParam type, boolean optional) {
        CommandParamData p = new CommandParamData();
        p.setName(name);
        p.setType(type);
        p.setOptional(optional);
        return p;
    }

    private static CommandParamData enumParam(String name, CommandEnumData enumData, boolean optional) {
        CommandParamData p = new CommandParamData();
        p.setName(name);
        p.setEnumData(enumData);
        p.setOptional(optional);
        return p;
    }

    private static CommandEnumData hardEnum(String name, String... values) {
        Map<String, Set<CommandEnumConstraint>> map = new LinkedHashMap<>();
        for (String v : values) {
            map.put(v, EnumSet.noneOf(CommandEnumConstraint.class));
        }
        return new CommandEnumData(name, map, false);
    }

}
