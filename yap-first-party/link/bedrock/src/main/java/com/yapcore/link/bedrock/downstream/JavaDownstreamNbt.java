package com.yapcore.link.bedrock.downstream;

import com.yapcore.protocol.McCodec;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** NBT skip / login-play / forwarding helpers (split from {@link JavaDownstreamClient}). */
final class JavaDownstreamNbt {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private JavaDownstreamNbt() {}

    static void skipEntityMetaValue(ByteBuf buf, int serializer) {
        switch (serializer) {
            case 0 -> buf.readByte(); // BYTE
            case 1 -> McCodec.readVarInt(buf); // INT
            case 2 -> { // VAR_LONG
                int n = 0;
                byte b;
                do {
                    b = buf.readByte();
                    n++;
                } while ((b & 0x80) != 0 && n < 10 && buf.isReadable());
            }
            case 3 -> buf.readFloat();
            case 4 -> McCodec.readString(buf, 32767);
            case 5 -> JavaDownstreamParse.tryPlainFromComponent(buf); // COMPONENT
            case 6 -> { // OPTIONAL_COMPONENT
                if (buf.readBoolean()) {
                    JavaDownstreamParse.tryPlainFromComponent(buf);
                }
            }
            case 7 -> JeItemStackCodec.readSlot(buf); // ITEM_STACK
            case 8 -> buf.readBoolean(); // BOOLEAN
            case 9 -> { // ROTATIONS x3 floats
                buf.readFloat();
                buf.readFloat();
                buf.readFloat();
            }
            case 10 -> buf.readLong(); // BLOCK_POS
            case 11 -> { // OPTIONAL_BLOCK_POS
                if (buf.readBoolean()) {
                    buf.readLong();
                }
            }
            case 12 -> McCodec.readVarInt(buf); // DIRECTION
            default -> McCodec.readVarInt(buf); // best-effort holders / enums
        }
    }

    static void skipNbtValue(ByteBuf buf, byte type) {
        switch (type) {
            case 1 -> buf.readByte();
            case 2 -> buf.readShort();
            case 3 -> buf.readInt();
            case 4 -> buf.readLong();
            case 5 -> buf.readFloat();
            case 6 -> buf.readDouble();
            case 7 -> { // byte array
                int n = buf.readInt();
                buf.skipBytes(Math.max(0, Math.min(n, buf.readableBytes())));
            }
            case 8 -> JavaDownstreamParse.readModifiedUtf(buf);
            case 9 -> {
                byte elem = buf.readByte();
                int count = buf.readInt();
                for (int i = 0; i < count && i < 4096 && buf.isReadable(); i++) {
                    skipNbtValue(buf, elem);
                }
            }
            case 10 -> {
                while (buf.isReadable()) {
                    byte ft = buf.readByte();
                    if (ft == 0) {
                        break;
                    }
                    JavaDownstreamParse.readModifiedUtf(buf);
                    skipNbtValue(buf, ft);
                }
            }
            case 11 -> {
                int n = buf.readInt();
                buf.skipBytes(Math.max(0, Math.min(n, buf.readableBytes() / 4)) * 4);
            }
            case 12 -> {
                int n = buf.readInt();
                buf.skipBytes(Math.max(0, Math.min(n, buf.readableBytes() / 8)) * 8);
            }
            default -> {
                // unknown
            }
        }
    }

    static void skipNbtValue(ByteBuf in, int type) {
        // NBT tag ids: 1=byte 2=short 3=int 4=long 5=float 6=double 7=byte[] 8=string
        // 9=list 10=compound 11=int[] 12=long[]
        switch (type) {
            case 1 -> in.skipBytes(1);
            case 2 -> in.skipBytes(2);
            case 3, 5 -> in.skipBytes(4); // int / float
            case 4, 6 -> in.skipBytes(8); // long / double (was wrongly 4 for double → Bad string length)
            case 7 -> {
                int len = in.readInt();
                in.skipBytes(Math.max(0, Math.min(len, in.readableBytes())));
            }
            case 8 -> skipNbtUtf(in); // DataInput.readUTF — unsigned short, not MC VarInt
            case 9 -> {
                int elemType = in.readUnsignedByte();
                int len = in.readInt();
                for (int i = 0; i < len && in.isReadable(); i++) {
                    skipNbtValue(in, elemType);
                }
            }
            case 10 -> {
                while (in.isReadable()) {
                    byte fieldType = in.readByte();
                    if (fieldType == 0) {
                        break;
                    }
                    skipNbtUtf(in); // compound key
                    skipNbtValue(in, fieldType & 0xFF);
                }
            }
            case 11 -> {
                int len = in.readInt();
                in.skipBytes(Math.max(0, Math.min(len * 4, in.readableBytes())));
            }
            case 12 -> {
                int len = in.readInt();
                in.skipBytes(Math.max(0, Math.min(len * 8, in.readableBytes())));
            }
            default -> {
                // unknown tag
            }
        }
    }

    /**
     * JE player_info / player_info_update (1.19.3+): actions bitmask + entries.
     * Bit 0 = ADD_PLAYER (name + properties).
     */
    static void parsePlayerInfo(JavaDownstreamClient client, ByteBuf buf) {
        if (client.listener == null || !buf.isReadable()) {
            return;
        }
        try {
            int actions = McCodec.readVarInt(buf);
            int count = McCodec.readVarInt(buf);
            boolean add = (actions & 0x01) != 0;
            for (int i = 0; i < count && buf.isReadable(); i++) {
                UUID id = McCodec.readUuid(buf);
                if (add) {
                    String name = McCodec.readString(buf, 16);
                    int props = McCodec.readVarInt(buf);
                    for (int p = 0; p < props && buf.isReadable(); p++) {
                        McCodec.readString(buf, 32767);
                        McCodec.readString(buf, 32767);
                        if (buf.readBoolean()) {
                            McCodec.readString(buf, 32767);
                        }
                    }
                    client.listener.onPlayerInfoAdd(id, name);
                    // Remaining action payloads for this entry — best-effort skip by stopping
                    // further structured reads; leftover bytes discarded by caller release.
                    if ((actions & ~0x01) != 0) {
                        // Consume remaining fields we know for common bits when present.
                        if ((actions & 0x02) != 0 && buf.isReadable()) { // chat session
                            if (buf.readBoolean()) {
                                McCodec.readUuid(buf);
                                // skip two key byte arrays if present — length-prefixed
                                skipPrefixedBytes(buf);
                                skipPrefixedBytes(buf);
                            }
                        }
                        if ((actions & 0x04) != 0 && buf.isReadable()) {
                            McCodec.readVarInt(buf); // gamemode
                        }
                        if ((actions & 0x08) != 0 && buf.isReadable()) {
                            buf.readBoolean(); // listed
                        }
                        if ((actions & 0x10) != 0 && buf.isReadable()) {
                            McCodec.readVarInt(buf); // latency
                        }
                        if ((actions & 0x20) != 0 && buf.isReadable()) {
                            if (buf.readBoolean()) {
                                JavaDownstreamParse.tryPlainFromComponent(buf);
                            }
                        }
                    }
                } else {
                    // Non-ADD actions still start with UUID; skip known payloads.
                    if ((actions & 0x02) != 0 && buf.isReadable()) {
                        if (buf.readBoolean()) {
                            McCodec.readUuid(buf);
                            skipPrefixedBytes(buf);
                            skipPrefixedBytes(buf);
                        }
                    }
                    if ((actions & 0x04) != 0 && buf.isReadable()) {
                        McCodec.readVarInt(buf);
                    }
                    if ((actions & 0x08) != 0 && buf.isReadable()) {
                        buf.readBoolean();
                    }
                    if ((actions & 0x10) != 0 && buf.isReadable()) {
                        McCodec.readVarInt(buf);
                    }
                    if ((actions & 0x20) != 0 && buf.isReadable()) {
                        if (buf.readBoolean()) {
                            JavaDownstreamParse.tryPlainFromComponent(buf);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.fine("JE player_info parse skip: " + e.getMessage());
        }
    }

    static void skipPrefixedBytes(ByteBuf buf) {
        if (!buf.isReadable()) {
            return;
        }
        int len = McCodec.readVarInt(buf);
        if (len > 0) {
            buf.skipBytes(Math.min(len, buf.readableBytes()));
        }
    }

    static void skipNbtPayload(ByteBuf in) {
        if (!in.isReadable()) {
            return;
        }
        byte type = in.readByte();
        if (type == 0) {
            return;
        }
        skipNbtValue(in, type & 0xFF);
    }

    /** Classic binary NBT string / name: unsigned short length + Modified UTF-8 bytes. */
    static void skipNbtUtf(ByteBuf in) {
        if (in.readableBytes() < 2) {
            return;
        }
        int len = in.readUnsignedShort();
        in.skipBytes(Math.max(0, Math.min(len, in.readableBytes())));
    }

    static byte[] loadForwardingSecret(Path linkHome) {
        if (linkHome == null) {
            return null;
        }
        Path secret = linkHome.resolve("forwarding.secret");
        try {
            if (!Files.isRegularFile(secret)) {
                LOG.info("JE downstream: no forwarding.secret at " + secret
                        + " — Velocity inject disabled");
                return null;
            }
            String text = Files.readString(secret, StandardCharsets.UTF_8).trim();
            if (text.isBlank()) {
                return null;
            }
            return text.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOG.warning("JE downstream: failed reading forwarding.secret: " + e.getMessage());
            return null;
        }
    }

    /**
     * Best-effort harvest of Brigadier literal-looking UTF strings from {@code commands}.
     * Prefer {@link JavaCommandsTree#parse} — this is the fallback when tree decode fails.
     */
    static java.util.List<String> extractCommandLiterals(ByteBuf buf) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        if (buf == null || !buf.isReadable()) {
            return java.util.List.of();
        }
        int start = buf.readerIndex();
        int end = buf.writerIndex();
        java.util.Set<String> deny = java.util.Set.of(
                "player", "players", "target", "targets", "victim", "destination", "amount",
                "message", "reason", "item", "itemname", "enchantment", "effect", "rule",
                "value", "gamemode", "seconds", "level", "entity", "entitytype", "position",
                "spawnpos", "from", "to", "tilename", "action", "type", "command", "page",
                "objective", "team", "score", "scale", "name", "uuid", "x", "y", "z");
        for (int i = start; i < end; i++) {
            buf.readerIndex(i);
            try {
                int len = McCodec.readVarInt(buf);
                if (len < 2 || len > 32 || buf.readableBytes() < len) {
                    continue;
                }
                String s = buf.toString(buf.readerIndex(), len, StandardCharsets.UTF_8);
                if (!s.matches("[a-z][a-z0-9_]*")) {
                    continue;
                }
                if (deny.contains(s)) {
                    continue;
                }
                out.add(s);
            } catch (Exception ignored) {
                // try next offset
            }
        }
        buf.readerIndex(end);
        return new java.util.ArrayList<>(out);
    }

    static JavaDownstreamClient.LoginPlayInfo parseLoginPlay(JavaDownstreamClient client, ByteBuf buf) {
        int entityId = buf.readInt();
        buf.readBoolean(); // hardcore
        int worldCount = McCodec.readVarInt(buf);
        for (int i = 0; i < worldCount && buf.isReadable(); i++) {
            McCodec.readString(buf, 32767);
        }
        McCodec.readVarInt(buf); // max players
        int viewDistance = McCodec.readVarInt(buf);
        McCodec.readVarInt(buf); // simulation distance
        buf.readBoolean(); // reduced debug
        buf.readBoolean(); // enable respawn screen
        if (buf.isReadable()) {
            buf.readBoolean(); // limited crafting (764+)
        }
        int dimensionType = buf.isReadable() ? McCodec.readVarInt(buf) : 0;
        String dimensionName = buf.isReadable() ? McCodec.readString(buf, 32767) : "minecraft:overworld";
        if (buf.isReadable()) {
            buf.readLong(); // hashed seed
        }
        if (buf.isReadable()) {
            buf.readByte(); // game mode
        }
        if (buf.isReadable()) {
            buf.readByte(); // previous game mode
        }
        if (buf.isReadable()) {
            buf.readBoolean(); // is debug
        }
        if (buf.isReadable()) {
            buf.readBoolean(); // is flat
        }
        if (buf.isReadable()) {
            McCodec.readVarInt(buf); // portal cooldown (759+)
        }
        if (buf.isReadable()) {
            McCodec.readVarInt(buf); // sea level (766+)
        }
        if (buf.isReadable()) {
            buf.readBoolean(); // enforces secure chat (767+)
        }
        return new JavaDownstreamClient.LoginPlayInfo(entityId, viewDistance, dimensionType, dimensionName);
    }
}
