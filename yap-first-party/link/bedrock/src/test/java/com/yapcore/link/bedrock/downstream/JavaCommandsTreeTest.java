package com.yapcore.link.bedrock.downstream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yapcore.protocol.McCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.cloudburstmc.protocol.bedrock.data.command.CommandData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOverloadData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandParam;
import org.cloudburstmc.protocol.bedrock.packet.AvailableCommandsPacket;
import org.junit.jupiter.api.Test;

final class JavaCommandsTreeTest {

    @Test
    void parsesRootLiteralsAndArgumentOverloads() {
        // nodes: 0=root → [1,2]; 1=literal hello (exec); 2=literal give → [3];
        // 3=argument player (game_profile=7)
        ByteBuf buf = Unpooled.buffer();
        McCodec.writeVarInt(buf, 4);

        writeNode(buf, /*flags*/ 0x00, new int[]{1, 2}, -1, null, -1); // root
        writeNode(buf, /*literal+exec*/ 0x01 | 0x04, new int[0], -1, "hello", -1);
        writeNode(buf, /*literal*/ 0x01, new int[]{3}, -1, "give", -1);
        writeNode(buf, /*argument*/ 0x02, new int[0], -1, "player", 7);

        McCodec.writeVarInt(buf, 0); // root index

        JavaCommandsTree.Parsed parsed = JavaCommandsTree.parse(buf);
        assertNotNull(parsed);
        assertTrue(parsed.rootLiteralNames().contains("hello"));
        assertTrue(parsed.rootLiteralNames().contains("give"));

        AvailableCommandsPacket packet = parsed.toAvailableCommands();
        assertTrue(packet.getCommands().size() >= 2);
        CommandData give = packet.getCommands().stream()
                .filter(c -> "give".equals(c.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(give.getOverloads().length >= 1);
        CommandOverloadData overload = give.getOverloads()[0];
        assertEquals(1, overload.getOverloads().length);
        assertEquals(CommandParam.TARGET, overload.getOverloads()[0].getType());
        assertFalse(packet.getCommands().isEmpty());
    }

    @Test
    void skipsIntegerParserProperties() {
        ByteBuf buf = Unpooled.buffer();
        McCodec.writeVarInt(buf, 3);
        writeNode(buf, 0x00, new int[]{1}, -1, null, -1);
        writeNode(buf, 0x01, new int[]{2}, -1, "xp", -1);
        // argument amount: integer parser id 3 with min+max flags
        int flags = 0x02; // argument
        buf.writeByte(flags);
        McCodec.writeVarInt(buf, 0); // no children
        McCodec.writeString(buf, "amount");
        McCodec.writeVarInt(buf, 3); // integer
        buf.writeByte(0x03); // has min + max
        buf.writeInt(0);
        buf.writeInt(100);
        McCodec.writeVarInt(buf, 0); // root

        JavaCommandsTree.Parsed parsed = JavaCommandsTree.parse(buf);
        assertNotNull(parsed);
        assertTrue(parsed.rootLiteralNames().contains("xp"));
        AvailableCommandsPacket packet = parsed.toAvailableCommands();
        CommandData xp = packet.getCommands().stream()
                .filter(c -> "xp".equals(c.getName()))
                .findFirst()
                .orElseThrow();
        assertEquals(CommandParam.INT, xp.getOverloads()[0].getOverloads()[0].getType());
    }

    private static void writeNode(ByteBuf buf, int flags, int[] children, int redirect,
                                  String name, int parserId) {
        buf.writeByte(flags);
        McCodec.writeVarInt(buf, children.length);
        for (int c : children) {
            McCodec.writeVarInt(buf, c);
        }
        if ((flags & 0x08) != 0) {
            McCodec.writeVarInt(buf, redirect);
        }
        int type = flags & 0x03;
        if (type == 1 || type == 2) {
            McCodec.writeString(buf, name != null ? name : "");
        }
        if (type == 2) {
            McCodec.writeVarInt(buf, parserId);
            // no properties for game_profile (7)
        }
        if ((flags & 0x10) != 0) {
            McCodec.writeString(buf, "minecraft:ask_server");
        }
    }
}
