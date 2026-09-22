package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.session.LinkBedrockSession;
import com.yapcore.protocol.McCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.nbt.NbtUtils;
import org.cloudburstmc.protocol.bedrock.packet.BlockEntityDataPacket;

/**
 * JE sign block-entity text → Bedrock {@link BlockEntityDataPacket}.
 * Block state alone has no lines, so Bedrock signs were blank.
 */
public final class JavaSignTranslator {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");
    private static final int MAX_BLOCK_ENTITIES = 512;
    /** Bedrock drops block-entity data before 0x71 — hold sign text until then. */
    private static final ConcurrentHashMap<Long, List<PendingSign>> PENDING = new ConcurrentHashMap<>();

    private record PendingSign(int x, int y, int z, String front, String back) {}

    private JavaSignTranslator() {
    }

    /** After a REAL column is on the wire, push sign text that lived in the JE chunk. */
    public static void sendChunkSigns(LinkBedrockSession session, int chunkX, int chunkZ, ByteBuf jePayload) {
        if (session == null || jePayload == null || !jePayload.isReadable()) {
            return;
        }
        ByteBuf body = jePayload.duplicate();
        if (!JavaChunkDecoder776.seekBlockEntities(body)) {
            return;
        }
        int count;
        try {
            count = McCodec.readVarInt(body);
        } catch (RuntimeException e) {
            return;
        }
        count = Math.min(Math.max(count, 0), MAX_BLOCK_ENTITIES);
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        int sent = 0;
        for (int i = 0; i < count && body.isReadable(); i++) {
            int xz = body.readUnsignedByte();
            int y = body.readShort();
            try {
                McCodec.readVarInt(body); // type id
            } catch (RuntimeException e) {
                return;
            }
            NbtMap nbt = readNbt(body);
            if (nbt == null) {
                return;
            }
            String front = lines(nbt.getCompound("front_text"));
            String back = lines(nbt.getCompound("back_text"));
            if (front == null && back == null) {
                front = legacyLines(nbt, "Text1", "Text2", "Text3", "Text4");
            }
            if (front == null && back == null) {
                continue;
            }
            int x = baseX + ((xz >> 4) & 15);
            int z = baseZ + (xz & 15);
            send(session, x, y, z, front, back);
            sent++;
        }
        if (sent > 0) {
            BedrockJoinProbe.noteEvent(session.guid(),
                    "java_sign_text cx=" + chunkX + " cz=" + chunkZ + " n=" + sent);
            LOG.fine("BE sign text cx=" + chunkX + " cz=" + chunkZ + " n=" + sent);
        }
    }

    /** Push sign text that arrived before SetLocalPlayerAsInitialized. */
    public static void flushPending(LinkBedrockSession session) {
        if (session == null || !session.isUpstreamInitialized()) {
            return;
        }
        List<PendingSign> batch = PENDING.remove(session.guid());
        if (batch == null || batch.isEmpty()) {
            return;
        }
        for (PendingSign sign : batch) {
            write(session, sign.x(), sign.y(), sign.z(), sign.front(), sign.back());
        }
        BedrockJoinProbe.noteEvent(session.guid(), "java_sign_text FLUSH n=" + batch.size());
    }

    /** JE {@code block_entity_data} (edited sign). Position is the 1.14+ packed long. */
    public static void fromPacket(LinkBedrockSession session, ByteBuf buf) {
        if (session == null || buf == null || buf.readableBytes() < 8) {
            return;
        }
        long packed = buf.readLong();
        int x = (int) (packed >> 38);
        int y = (int) (packed << 52 >> 52);
        int z = (int) (packed << 26 >> 38);
        try {
            McCodec.readVarInt(buf);
        } catch (RuntimeException e) {
            return;
        }
        NbtMap nbt = readNbt(buf);
        if (nbt == null) {
            return;
        }
        String front = lines(nbt.getCompound("front_text"));
        String back = lines(nbt.getCompound("back_text"));
        if (front == null && back == null) {
            return;
        }
        send(session, x, y, z, front, back);
    }

    public static void send(LinkBedrockSession session, int x, int y, int z, String front, String back) {
        if (session == null) {
            return;
        }
        if ((front == null || front.isBlank()) && (back == null || back.isBlank())) {
            return;
        }
        if (!session.isUpstreamInitialized()) {
            PENDING.computeIfAbsent(session.guid(), k -> new ArrayList<>())
                    .add(new PendingSign(x, y, z, front, back));
            return;
        }
        write(session, x, y, z, front, back);
    }

    private static void write(LinkBedrockSession session, int x, int y, int z, String front, String back) {
        String frontText = front == null ? "" : front;
        NbtMap data = NbtMap.builder()
                .putString("id", "Sign")
                .putInt("x", x)
                .putInt("y", y)
                .putInt("z", z)
                // Legacy clients read Text; 1.19.80+ read FrontText/BackText.
                .putString("Text", frontText)
                .putCompound("FrontText", textCompound(front))
                .putCompound("BackText", textCompound(back))
                .putByte("IsWaxed", (byte) 0)
                .putByte("TextIgnoreLegacyBugResolved", (byte) 1)
                .build();
        BlockEntityDataPacket packet = new BlockEntityDataPacket();
        packet.setBlockPosition(Vector3i.from(x, y, z));
        packet.setData(data);
        session.sendUpstreamPacket(packet);
    }

    private static NbtMap textCompound(String lines) {
        String text = lines == null ? "" : lines;
        return NbtMap.builder()
                .putString("Text", text)
                .putInt("SignTextColor", 0xFF000000)
                .putByte("IgnoreLighting", (byte) 0)
                .putByte("PersistFormatting", (byte) 1)
                .build();
    }

    private static String lines(NbtMap side) {
        if (side == null) {
            return null;
        }
        List<String> parts = messageLines(side);
        if (parts.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size() && i < 4; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(parts.get(i));
        }
        String out = sb.toString();
        return out.isBlank() ? null : out;
    }

    /** 26.2 stores sign lines as text-component compounds, not JSON strings. */
    private static List<String> messageLines(NbtMap side) {
        List<String> out = new ArrayList<>();
        var compounds = side.getList("messages", NbtType.COMPOUND);
        if (compounds != null && !compounds.isEmpty()) {
            for (NbtMap line : compounds) {
                out.add(componentPlain(line));
            }
            return out;
        }
        var strings = side.getList("messages", NbtType.STRING);
        if (strings != null) {
            for (String line : strings) {
                out.add(plain(line));
            }
        }
        return out;
    }

    private static String componentPlain(NbtMap component) {
        if (component == null || component.isEmpty()) {
            return "";
        }
        String text = component.getString("text", "");
        StringBuilder sb = new StringBuilder(plain(text));
        var extra = component.getList("extra", NbtType.COMPOUND);
        if (extra != null) {
            for (NbtMap part : extra) {
                sb.append(componentPlain(part));
            }
        }
        var extraStrings = component.getList("extra", NbtType.STRING);
        if (extraStrings != null) {
            for (String part : extraStrings) {
                sb.append(plain(part));
            }
        }
        if (sb.length() == 0) {
            String translate = component.getString("translate", "");
            if (!translate.isBlank()) {
                sb.append(translate);
            }
        }
        return sb.toString();
    }

    private static String legacyLines(NbtMap nbt, String... keys) {
        StringBuilder sb = new StringBuilder();
        boolean any = false;
        for (String key : keys) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            String value = nbt.getString(key, "");
            if (!value.isBlank()) {
                any = true;
            }
            sb.append(plain(value));
        }
        return any ? sb.toString() : null;
    }

    /** Strip a JSON text component down to the visible line. */
    static String plain(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            s = s.substring(1, s.length() - 1);
        }
        int key = s.indexOf("\"text\"");
        if (key >= 0) {
            int colon = s.indexOf(':', key + 6);
            int open = colon >= 0 ? s.indexOf('"', colon + 1) : -1;
            int close = open >= 0 ? s.indexOf('"', open + 1) : -1;
            if (open >= 0 && close > open) {
                s = s.substring(open + 1, close);
            } else {
                return "";
            }
        } else if (s.startsWith("{") || s.startsWith("[")) {
            return "";
        }
        return s.replace("\\n", "").replace("\\\"", "\"");
    }

    private static NbtMap readNbt(ByteBuf buf) {
        if (buf == null || !buf.isReadable()) {
            return null;
        }
        // JE writeNbt is big-endian nameless network NBT. createReader is Bedrock LE.
        try (var in = NbtUtils.createNetworkReader(new ByteBufInputStream(buf))) {
            Object tag = in.readTag();
            return tag instanceof NbtMap map ? map : NbtMap.EMPTY;
        } catch (IOException | RuntimeException e) {
            LOG.fine("sign nbt: " + e.getMessage());
            return null;
        }
    }
}
