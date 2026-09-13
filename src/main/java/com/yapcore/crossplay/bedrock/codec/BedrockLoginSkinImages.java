package com.yapcore.crossplay.bedrock.codec;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import static com.yapcore.crossplay.bedrock.codec.BedrockCodecBinary.*;

/**
 * SkinImage / PNG helpers (split from {@link BedrockLoginCodec} for the ≤500-line domain gate).
 */
final class BedrockLoginSkinImages {
    private BedrockLoginSkinImages() {}

    record SkinImage(int width, int height, byte[] data) {
        static SkinImage empty() {
            return new SkinImage(0, 0, new byte[0]);
        }
    }

    static SkinImage toSkinImage(byte[] rgbaOrPng) {
        if (rgbaOrPng == null || rgbaOrPng.length == 0) {
            return SkinImage.empty();
        }
        byte[] rgba = rgbaOrPng;
        if (isPng(rgbaOrPng)) {
            byte[] decoded = pngToRgba(rgbaOrPng);
            if (decoded == null) {
                return SkinImage.empty();
            }
            rgba = decoded;
        }
        int pixels = rgba.length / 4;
        int w;
        int h;
        if (pixels == 64 * 64) {
            w = 64;
            h = 64;
        } else if (pixels == 64 * 32) {
            w = 64;
            h = 32;
        } else if (pixels == 128 * 128) {
            w = 128;
            h = 128;
        } else if (pixels == 128 * 64) {
            w = 128;
            h = 64;
        } else if (rgba.length >= 4) {
            // Unknown size — treat as 64x64 truncated / padded
            w = 64;
            h = 64;
            byte[] padded = new byte[w * h * 4];
            System.arraycopy(rgba, 0, padded, 0, Math.min(rgba.length, padded.length));
            rgba = padded;
        } else {
            return SkinImage.empty();
        }
        return new SkinImage(w, h, rgba);
    }

    static boolean isPng(byte[] data) {
        return data != null && data.length >= 8
                && (data[0] & 0xff) == 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47;
    }

    static byte[] pngToRgba(byte[] png) {
        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
            if (img == null) {
                return null;
            }
            int w = img.getWidth();
            int h = img.getHeight();
            byte[] rgba = new byte[w * h * 4];
            int i = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = img.getRGB(x, y);
                    rgba[i++] = (byte) ((argb >> 16) & 0xff);
                    rgba[i++] = (byte) ((argb >> 8) & 0xff);
                    rgba[i++] = (byte) (argb & 0xff);
                    rgba[i++] = (byte) ((argb >> 24) & 0xff);
                }
            }
            return rgba;
        } catch (Exception e) {
            return null;
        }
    }

    static void writeSkinImage(ByteBuf out, SkinImage img) {
        out.writeIntLE(img.width);
        out.writeIntLE(img.height);
        writeUnsignedVarInt(out, img.data.length);
        if (img.data.length > 0) {
            out.writeBytes(img.data);
        }
    }

    static void writeEmptySkinImage(ByteBuf out) {
        out.writeIntLE(0); // width
        out.writeIntLE(0); // height
        writeUnsignedVarInt(out, 0); // ByteArray data
    }

    static SkinImage readSkinImage(ByteBuf in) {
        int w = in.readIntLE();
        int h = in.readIntLE();
        int len = readUnsignedVarInt(in);
        byte[] data = new byte[Math.max(0, len)];
        if (len > 0) {
            in.readBytes(data);
        }
        return new SkinImage(w, h, data);
    }

    static byte[] skinImageToPng(SkinImage img) {
        if (img == null || img.data == null || img.data.length == 0) {
            return null;
        }
        if (com.yapcore.crossplay.skin.SkinService.looksLikePng(img.data)) {
            return img.data.clone();
        }
        if (com.yapcore.crossplay.skin.SkinService.isLikelyRgba(img.data.length)) {
            return com.yapcore.crossplay.skin.SkinService.rgbaToPng(img.data);
        }
        return img.data.clone();
    }

    static byte[] decodeMaybeBase64(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return java.util.Base64.getDecoder().decode(s.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static String personaPieceTypeName(int ordinal) {
        org.cloudburstmc.protocol.bedrock.data.skin.PersonaPieceType[] vals =
                org.cloudburstmc.protocol.bedrock.data.skin.PersonaPieceType.values();
        if (ordinal >= 0 && ordinal < vals.length) {
            return vals[ordinal].getSerializeName();
        }
        return "unknown";
    }

    static void writeUuidLe(ByteBuf out, UUID uuid) {
        out.writeLongLE(uuid.getMostSignificantBits());
        out.writeLongLE(uuid.getLeastSignificantBits());
    }

    static UUID parseUuidOrNil(String s) {
        if (s == null || s.isBlank()) {
            return new UUID(0L, 0L);
        }
        try {
            return UUID.fromString(s.trim());
        } catch (IllegalArgumentException e) {
            return new UUID(0L, 0L);
        }
    }

    static int personaPieceTypeOrdinal(String pieceType) {
        if (pieceType == null || pieceType.isBlank()) {
            return org.cloudburstmc.protocol.bedrock.data.skin.PersonaPieceType.UNKNOWN.ordinal();
        }
        String raw = pieceType.trim();
        try {
            return org.cloudburstmc.protocol.bedrock.data.skin.PersonaPieceType.fromName(raw).ordinal();
        } catch (IllegalArgumentException ignored) {
            // fall through to aliases
        }
        // Aliases used by Tailor / older YaP strings → Cloudburst serialize names
        String normalized = switch (raw.toLowerCase()) {
            case "facial_hair", "persona_facial_hair" -> "facialhair";
            case "face_accessory", "persona_face_accessory" -> "faceaccessory";
            case "high_hips", "persona_high_hips" -> "high_pants";
            case "left_leg", "persona_left_leg" -> "leftleg";
            case "right_leg", "persona_right_leg" -> "rightleg";
            case "left_arm", "persona_left_arm" -> "leftarm";
            case "right_arm", "persona_right_arm" -> "rightarm";
            case "classic_skin", "persona_classic_skin" -> "classicskin";
            case "cape" -> "capes";
            default -> raw.toLowerCase().replace("persona_", "").replace("_", "");
        };
        try {
            return org.cloudburstmc.protocol.bedrock.data.skin.PersonaPieceType.fromName(normalized).ordinal();
        } catch (IllegalArgumentException e) {
            try {
                return Integer.parseInt(raw);
            } catch (NumberFormatException nfe) {
                return org.cloudburstmc.protocol.bedrock.data.skin.PersonaPieceType.UNKNOWN.ordinal();
            }
        }
    }

    static int parseSkinColorArgb(String color) {
        if (color == null || color.isBlank() || "#0".equals(color)) {
            return 0;
        }
        String c = color.trim();
        if (c.startsWith("#")) {
            c = c.substring(1);
        }
        try {
            if (c.length() <= 6) {
                int rgb = Integer.parseInt(c, 16);
                return 0xFF000000 | rgb;
            }
            return (int) Long.parseLong(c, 16);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
