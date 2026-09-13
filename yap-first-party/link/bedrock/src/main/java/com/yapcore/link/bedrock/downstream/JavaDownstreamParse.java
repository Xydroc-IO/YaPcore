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

/** JE packet parse / NBT skip helpers (split from {@link JavaDownstreamClient}). */
final class JavaDownstreamParse {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private JavaDownstreamParse() {}

    /**
     * Decode JE network chat component (NBT via {@code NbtIo.writeAnyTag} — classic UTF lengths).
     *
     * <p>Previously fell through to {@link McCodec#readString} which treated the NBT type byte
     * as a VarInt length — TAG_String (8) / TAG_End (0) became chat lines {@code "8"}/{@code "0"}.
     */
    static String tryPlainFromComponent(ByteBuf buf) {
        if (buf == null || !buf.isReadable()) {
            return null;
        }
        buf.markReaderIndex();
        try {
            byte type = buf.readByte();
            if (type == 0) { // TAG_End
                return null;
            }
            if (type == 8) { // TAG_String — DataOutput.writeUTF
                return readModifiedUtf(buf);
            }
            if (type == 10) { // TAG_Compound
                return readNbtCompoundText(buf);
            }
            if (type == 9) { // TAG_List — rare root; skim elements
                byte elemType = buf.readByte();
                int count = buf.readInt();
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < count && i < 32 && buf.isReadable(); i++) {
                    String part = readNbtValueText(buf, elemType);
                    if (part != null && !part.isBlank()) {
                        if (sb.length() > 0) {
                            sb.append(' ');
                        }
                        sb.append(part);
                    }
                }
                return sb.length() > 0 ? sb.toString() : null;
            }
            // Unsupported root tag — do NOT McCodec.readString (digit spam).
            buf.resetReaderIndex();
            JavaDownstreamNbt.skipNbtValue(buf, type);
            return null;
        } catch (Exception e) {
            try {
                buf.resetReaderIndex();
            } catch (Exception ignored) {
                // leave
            }
            return null;
        }
    }

    static String readNbtCompoundText(ByteBuf buf) {
        String text = null;
        String translate = null;
        StringBuilder extra = new StringBuilder();
        while (buf.isReadable()) {
            byte fieldType = buf.readByte();
            if (fieldType == 0) {
                break;
            }
            String name = readModifiedUtf(buf);
            if (name == null) {
                JavaDownstreamNbt.skipNbtValue(buf, fieldType);
                continue;
            }
            if ("text".equals(name) && fieldType == 8) {
                text = readModifiedUtf(buf);
            } else if ("translate".equals(name) && fieldType == 8) {
                translate = readModifiedUtf(buf);
            } else if (("extra".equals(name) || "with".equals(name)) && fieldType == 9) {
                byte elemType = buf.readByte();
                int count = buf.readInt();
                for (int i = 0; i < count && i < 32 && buf.isReadable(); i++) {
                    String part = readNbtValueText(buf, elemType);
                    if (part != null && !part.isBlank()) {
                        if (extra.length() > 0) {
                            extra.append(' ');
                        }
                        extra.append(part);
                    }
                }
            } else {
                JavaDownstreamNbt.skipNbtValue(buf, fieldType);
            }
        }
        if (text != null && !text.isBlank()) {
            return extra.length() > 0 ? text + " " + extra : text;
        }
        if (translate != null && !translate.isBlank()) {
            return formatTranslateKey(translate, extra.toString());
        }
        return extra.length() > 0 ? extra.toString() : null;
    }

    /**
     * Never dump raw translation keys like {@code death.attack.drown} or {@code entity.minecraft.zombie}
     * into Bedrock chat — that looked like "drowned while walking" / mob "code".
     */
    static String formatTranslateKey(String translate, String withArgs) {
        String key = translate.trim();
        String args = withArgs != null ? withArgs.trim() : "";
        String[] parts = args.isEmpty() ? new String[0] : args.split("\\s+");
        // Prefer human args; strip entity.* codes from with-list.
        java.util.List<String> clean = new java.util.ArrayList<>();
        for (String p : parts) {
            if (p == null || p.isBlank()) {
                continue;
            }
            if (p.startsWith("entity.") || p.startsWith("death.") || p.startsWith("minecraft:")) {
                // last path segment as weak fallback label
                int dot = p.lastIndexOf('.');
                String leaf = dot >= 0 ? p.substring(dot + 1) : p;
                if (!leaf.isBlank() && leaf.indexOf('_') >= 0) {
                    leaf = leaf.replace('_', ' ');
                }
                clean.add(capitalize(leaf));
                continue;
            }
            clean.add(p);
        }
        String a0 = clean.size() > 0 ? clean.get(0) : "";
        String a1 = clean.size() > 1 ? clean.get(1) : "";
        if (key.startsWith("death.attack.drown") || "death.attack.drown.player".equals(key)) {
            return a0.isEmpty() ? "Someone drowned" : a0 + " drowned";
        }
        if (key.contains("drown") && key.startsWith("death.")) {
            return a0.isEmpty() ? "Someone drowned" : a0 + " drowned";
        }
        if (key.startsWith("death.attack.mob") || key.startsWith("death.attack.player")
                || key.startsWith("death.attack.entity")) {
            if (!a0.isEmpty() && !a1.isEmpty()) {
                return a0 + " was slain by " + a1;
            }
            return !a0.isEmpty() ? a0 + " died" : "Someone died";
        }
        if (key.startsWith("death.attack.arrow") || key.startsWith("death.attack.trident")
                || key.startsWith("death.attack.thrown")) {
            if (!a0.isEmpty() && !a1.isEmpty()) {
                return a0 + " was shot by " + a1;
            }
            return !a0.isEmpty() ? a0 + " was shot" : "Someone was shot";
        }
        if (key.startsWith("death.attack.fall") || "death.fell.accident.generic".equals(key)) {
            return a0.isEmpty() ? "Someone fell from a high place" : a0 + " fell from a high place";
        }
        if (key.startsWith("death.")) {
            if (!a0.isEmpty() && !a1.isEmpty()) {
                return a0 + " was killed by " + a1;
            }
            return !a0.isEmpty() ? a0 + " died" : "Someone died";
        }
        // Non-death translate keys: prefer args only; never return the raw key.
        if (!args.isEmpty()) {
            return String.join(" ", clean);
        }
        return null;
    }

    static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }


    static String readNbtValueText(ByteBuf buf, byte type) {
        if (type == 8) {
            return readModifiedUtf(buf);
        }
        if (type == 10) {
            return readNbtCompoundText(buf);
        }
        JavaDownstreamNbt.skipNbtValue(buf, type);
        return null;
    }



    /** Java {@link java.io.DataOutput#writeUTF} / modified UTF-8. */
    static String readModifiedUtf(ByteBuf buf) {
        int utflen = buf.readUnsignedShort();
        if (utflen < 0 || utflen > buf.readableBytes()) {
            throw new IllegalArgumentException("Bad modified UTF length " + utflen);
        }
        if (utflen == 0) {
            return "";
        }
        byte[] bytes = new byte[utflen];
        buf.readBytes(bytes);
        // ASCII-fast path; modified UTF matches UTF-8 for BMP sans NUL.
        return new String(bytes, StandardCharsets.UTF_8);
    }



    static String safeString(ByteBuf buf) {
        try {
            if (buf.readableBytes() > 0) {
                return McCodec.readString(buf, 262144);
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "(disconnect)";
    }





}
