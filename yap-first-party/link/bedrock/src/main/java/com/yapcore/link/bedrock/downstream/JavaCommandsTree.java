package com.yapcore.link.bedrock.downstream;

import com.yapcore.protocol.McCodec;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.cloudburstmc.protocol.bedrock.data.command.CommandData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumConstraint;
import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOverloadData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandParam;
import org.cloudburstmc.protocol.bedrock.data.command.CommandParamData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandPermission;
import org.cloudburstmc.protocol.bedrock.packet.AvailableCommandsPacket;

/**
 * Full JE {@code commands} (Brigadier) tree parse for protocol 776 / Folia 26.2.
 *
 * <p>Replaces best-effort UTF literal scraping with real node + ArgumentTypeInfo property skip,
 * then projects root literals into Bedrock {@link AvailableCommandsPacket} overloads.
 */
public final class JavaCommandsTree {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");
    private static final int MAX_NODES = 8192;
    private static final int MAX_OVERLOADS = 48;
    private static final int MAX_DEPTH = 6;
    private static final int MAX_PARAMS = 8;

    public static final int TYPE_ROOT = 0;
    public static final int TYPE_LITERAL = 1;
    public static final int TYPE_ARGUMENT = 2;

    private JavaCommandsTree() {
    }

    public record Node(
            int type,
            boolean executable,
            boolean restricted,
            int redirect,
            String name,
            int parserId,
            int[] children
    ) {
        public boolean hasRedirect() {
            return redirect >= 0;
        }
    }

    public record Parsed(Node[] nodes, int rootIndex) {
        public List<String> rootLiteralNames() {
            LinkedHashSet<String> out = new LinkedHashSet<>();
            if (nodes == null || rootIndex < 0 || rootIndex >= nodes.length) {
                return List.of();
            }
            Node root = nodes[rootIndex];
            if (root == null || root.children() == null) {
                return List.of();
            }
            for (int idx : root.children()) {
                if (idx < 0 || idx >= nodes.length || nodes[idx] == null) {
                    continue;
                }
                Node n = nodes[idx];
                if (n.type() == TYPE_LITERAL && n.name() != null && !n.name().isBlank()) {
                    out.add(n.name().toLowerCase(Locale.ROOT));
                }
            }
            return new ArrayList<>(out);
        }

        /** Build Bedrock AvailableCommands from the JE tree (not a name-only stub). */
        public AvailableCommandsPacket toAvailableCommands() {
            AvailableCommandsPacket packet = new AvailableCommandsPacket();
            if (nodes == null || rootIndex < 0 || rootIndex >= nodes.length || nodes[rootIndex] == null) {
                return packet;
            }
            Node root = nodes[rootIndex];
            Set<String> seen = new LinkedHashSet<>();
            boolean helpAdded = false;
            if (root.children() != null) {
                for (int idx : root.children()) {
                    if (idx < 0 || idx >= nodes.length || nodes[idx] == null) {
                        continue;
                    }
                    Node cmd = nodes[idx];
                    if (cmd.type() != TYPE_LITERAL || cmd.name() == null || cmd.name().isBlank()) {
                        continue;
                    }
                    String name = cmd.name().toLowerCase(Locale.ROOT);
                    if (!name.matches("[a-z][a-z0-9_]*") || name.length() > 32) {
                        continue;
                    }
                    if (!seen.add(name)) {
                        continue;
                    }
                    Node resolved = resolveRedirect(cmd);
                    CommandOverloadData[] overloads = buildOverloads(resolved, 0);
                    CommandPermission perm = cmd.restricted()
                            ? CommandPermission.GAME_DIRECTORS
                            : CommandPermission.ANY;
                    packet.getCommands().add(new CommandData(
                            name,
                            "Server command /" + name,
                            Set.of(CommandData.Flag.NOT_CHEAT),
                            perm,
                            null,
                            Collections.emptyList(),
                            overloads));
                    if ("help".equals(name)) {
                        helpAdded = true;
                    }
                }
            }
            if (!helpAdded) {
                packet.getCommands().add(0, new CommandData(
                        "help",
                        "Shows help / lists commands",
                        Set.of(CommandData.Flag.NOT_CHEAT),
                        CommandPermission.ANY,
                        null,
                        Collections.emptyList(),
                        new CommandOverloadData[]{new CommandOverloadData(false, new CommandParamData[0])}));
            }
            return packet;
        }

        private Node resolveRedirect(Node node) {
            if (node == null || !node.hasRedirect()) {
                return node;
            }
            int r = node.redirect();
            if (r < 0 || r >= nodes.length || nodes[r] == null) {
                return node;
            }
            return nodes[r];
        }

        private CommandOverloadData[] buildOverloads(Node commandNode, int depth) {
            if (commandNode == null) {
                return new CommandOverloadData[]{emptyOverload()};
            }
            List<List<CommandParamData>> paths = new ArrayList<>();
            walk(commandNode, new ArrayList<>(), paths, depth);
            if (paths.isEmpty()) {
                if (commandNode.executable()) {
                    return new CommandOverloadData[]{emptyOverload()};
                }
                // Non-executable leaf with no children — still expose bare command name.
                return new CommandOverloadData[]{emptyOverload()};
            }
            List<CommandOverloadData> out = new ArrayList<>(Math.min(paths.size(), MAX_OVERLOADS));
            for (List<CommandParamData> path : paths) {
                if (out.size() >= MAX_OVERLOADS) {
                    break;
                }
                out.add(new CommandOverloadData(false, path.toArray(new CommandParamData[0])));
            }
            return out.toArray(new CommandOverloadData[0]);
        }

        private void walk(Node node, List<CommandParamData> prefix,
                          List<List<CommandParamData>> paths, int depth) {
            if (paths.size() >= MAX_OVERLOADS) {
                return;
            }
            Node cur = resolveRedirect(node);
            if (cur == null) {
                return;
            }
            boolean leaf = cur.children() == null || cur.children().length == 0;
            if (cur.executable() || leaf) {
                paths.add(new ArrayList<>(prefix));
                if (leaf || depth >= MAX_DEPTH) {
                    return;
                }
            }
            if (depth >= MAX_DEPTH || cur.children() == null) {
                return;
            }

            List<Node> leafLiterals = new ArrayList<>();
            List<Node> branchLiterals = new ArrayList<>();
            List<Node> arguments = new ArrayList<>();
            for (int idx : cur.children()) {
                if (idx < 0 || idx >= nodes.length || nodes[idx] == null) {
                    continue;
                }
                Node child = nodes[idx];
                if (child.type() == TYPE_LITERAL) {
                    if (child.children() == null || child.children().length == 0) {
                        leafLiterals.add(child);
                    } else {
                        branchLiterals.add(child);
                    }
                } else if (child.type() == TYPE_ARGUMENT) {
                    arguments.add(child);
                }
            }

            if (!leafLiterals.isEmpty() && prefix.size() < MAX_PARAMS) {
                LinkedHashMap<String, Set<CommandEnumConstraint>> values = new LinkedHashMap<>();
                String first = "option";
                for (Node lit : leafLiterals) {
                    if (lit.name() != null) {
                        if (values.isEmpty()) {
                            first = lit.name();
                        }
                        values.putIfAbsent(lit.name(), Set.of());
                    }
                }
                if (!values.isEmpty()) {
                    CommandParamData p = new CommandParamData();
                    p.setName(first);
                    p.setOptional(cur.executable());
                    p.setEnumData(new CommandEnumData(first + "Options", values, false));
                    List<CommandParamData> next = new ArrayList<>(prefix);
                    next.add(p);
                    // Leaf literals terminate this branch (may still be executable themselves).
                    paths.add(next);
                }
            }

            for (Node lit : branchLiterals) {
                if (paths.size() >= MAX_OVERLOADS || prefix.size() >= MAX_PARAMS) {
                    return;
                }
                List<CommandParamData> next = new ArrayList<>(prefix);
                next.add(literalEnumParam(lit, cur.executable()));
                walk(lit, next, paths, depth + 1);
            }

            for (Node arg : arguments) {
                if (paths.size() >= MAX_OVERLOADS || prefix.size() >= MAX_PARAMS) {
                    return;
                }
                List<CommandParamData> next = new ArrayList<>(prefix);
                next.add(argumentParam(arg, cur.executable()));
                walk(arg, next, paths, depth + 1);
            }
        }

        private static CommandParamData literalEnumParam(Node lit, boolean parentExecutable) {
            LinkedHashMap<String, Set<CommandEnumConstraint>> values = new LinkedHashMap<>();
            String n = lit.name() != null ? lit.name() : "option";
            values.put(n, Set.of());
            CommandParamData p = new CommandParamData();
            p.setName(n);
            p.setOptional(parentExecutable);
            p.setEnumData(new CommandEnumData(n, values, false));
            return p;
        }

        private static CommandParamData argumentParam(Node arg, boolean parentExecutable) {
            CommandParamData p = new CommandParamData();
            String n = arg.name() != null ? arg.name() : "arg";
            p.setName(n);
            p.setOptional(parentExecutable);
            Object mapped = mapParser(arg.parserId());
            if (mapped instanceof String[] enums) {
                LinkedHashMap<String, Set<CommandEnumConstraint>> values = new LinkedHashMap<>();
                for (String v : enums) {
                    values.put(v, EnumSet.noneOf(CommandEnumConstraint.class));
                }
                p.setEnumData(new CommandEnumData(n + "Enum", values, false));
            } else {
                p.setType((CommandParam) mapped);
            }
            return p;
        }

        private static Object mapParser(int parserId) {
            return switch (parserId) {
                case 0 -> new String[]{"true", "false"}; // bool
                case 1, 2, 28, 29 -> CommandParam.FLOAT; // float/double/angle/rotation
                case 3, 4, 43 -> CommandParam.INT; // int/long/time
                case 6, 7 -> CommandParam.TARGET; // entity / game_profile
                case 8 -> CommandParam.BLOCK_POSITION;
                case 9, 10, 11 -> CommandParam.POSITION; // column / vec3 / vec2
                case 20 -> CommandParam.MESSAGE;
                case 21, 22, 23 -> CommandParam.JSON; // nbt*
                case 26 -> CommandParam.OPERATOR;
                case 36, 37 -> CommandParam.FILE_PATH; // resource_location / function
                case 42 -> new String[]{"survival", "creative", "adventure", "spectator"};
                default -> CommandParam.STRING;
            };
        }

        private static CommandOverloadData emptyOverload() {
            return new CommandOverloadData(false, new CommandParamData[0]);
        }
    }

    /**
     * Parse JE commands body. On failure returns {@code null} (caller may fall back to scrape).
     */
    public static Parsed parse(ByteBuf buf) {
        if (buf == null || !buf.isReadable()) {
            return null;
        }
        int start = buf.readerIndex();
        try {
            int count = McCodec.readVarInt(buf);
            if (count < 1 || count > MAX_NODES) {
                throw new IllegalArgumentException("bad node count " + count);
            }
            Node[] nodes = new Node[count];
            for (int i = 0; i < count; i++) {
                nodes[i] = readNode(buf);
            }
            int root = McCodec.readVarInt(buf);
            if (root < 0 || root >= count) {
                throw new IllegalArgumentException("bad root index " + root);
            }
            buf.readerIndex(buf.writerIndex());
            return new Parsed(nodes, root);
        } catch (Exception e) {
            buf.readerIndex(start);
            LOG.fine("JE commands tree parse failed: " + e.getMessage());
            return null;
        }
    }

    private static Node readNode(ByteBuf buf) {
        int flags = buf.readUnsignedByte();
        int type = flags & 0x03;
        boolean executable = (flags & 0x04) != 0;
        boolean hasRedirect = (flags & 0x08) != 0;
        boolean hasSuggest = (flags & 0x10) != 0;
        boolean restricted = (flags & 0x20) != 0;

        int childCount = McCodec.readVarInt(buf);
        if (childCount < 0 || childCount > MAX_NODES) {
            throw new IllegalArgumentException("bad child count " + childCount);
        }
        int[] children = new int[childCount];
        for (int i = 0; i < childCount; i++) {
            children[i] = McCodec.readVarInt(buf);
        }
        int redirect = hasRedirect ? McCodec.readVarInt(buf) : -1;

        String name = null;
        int parserId = -1;
        if (type == TYPE_LITERAL || type == TYPE_ARGUMENT) {
            name = McCodec.readString(buf, 32767);
        }
        if (type == TYPE_ARGUMENT) {
            parserId = McCodec.readVarInt(buf);
            skipParserProperties(buf, parserId);
        }
        if (hasSuggest) {
            McCodec.readString(buf, 32767); // suggestion identifier
        }
        return new Node(type, executable, restricted, redirect, name, parserId, children);
    }

    /** Skip ArgumentTypeInfo properties so the rest of the packet stays aligned. */
    static void skipParserProperties(ByteBuf buf, int parserId) {
        switch (parserId) {
            case 1 -> { // float
                int f = buf.readUnsignedByte();
                if ((f & 0x01) != 0) {
                    buf.readFloat();
                }
                if ((f & 0x02) != 0) {
                    buf.readFloat();
                }
            }
            case 2 -> { // double
                int f = buf.readUnsignedByte();
                if ((f & 0x01) != 0) {
                    buf.readDouble();
                }
                if ((f & 0x02) != 0) {
                    buf.readDouble();
                }
            }
            case 3 -> { // integer
                int f = buf.readUnsignedByte();
                if ((f & 0x01) != 0) {
                    buf.readInt();
                }
                if ((f & 0x02) != 0) {
                    buf.readInt();
                }
            }
            case 4 -> { // long
                int f = buf.readUnsignedByte();
                if ((f & 0x01) != 0) {
                    buf.readLong();
                }
                if ((f & 0x02) != 0) {
                    buf.readLong();
                }
            }
            case 5 -> McCodec.readVarInt(buf); // string behavior
            case 6, 31 -> buf.readByte(); // entity / score_holder flags
            case 43 -> buf.readInt(); // time min
            case 44, 45, 46, 47, 48 -> McCodec.readString(buf, 32767); // resource* registry
            case 0, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26,
                    27, 28, 29, 30, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 49, 50, 51, 52, 53,
                    54, 55, 56 -> {
                // no properties
            }
            default -> throw new IllegalArgumentException("unknown parser id " + parserId);
        }
    }
}
