package com.yapcore.crossplay.skin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * PNG / base64 / JE textures helpers for {@link SkinService}
 * (split for the ≤500-line domain gate).
 */
final class SkinServiceTextures {

    private static final Logger LOG = Logger.getLogger("YaPcore.Skin");
    private static final byte[] PNG_MAGIC = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};

    private SkinServiceTextures() {
    }

    static byte[] tryDecodeBase64(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(s.trim());
        } catch (IllegalArgumentException e) {
            try {
                return Base64.getUrlDecoder().decode(s.trim());
            } catch (IllegalArgumentException e2) {
                return null;
            }
        }
    }

    static boolean looksLikePng(byte[] data) {
        if (data == null || data.length < 8) {
            return false;
        }
        for (int i = 0; i < PNG_MAGIC.length; i++) {
            if (data[i] != PNG_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    static boolean isLikelyRgba(int len) {
        return len == 64 * 64 * 4 || len == 64 * 32 * 4 || len == 128 * 128 * 4 || len == 128 * 64 * 4;
    }

    static byte[] rgbaToPng(byte[] rgba) {
        if (rgba == null || !isLikelyRgba(rgba.length)) {
            return null;
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
        } else {
            w = 128;
            h = 64;
        }
        try {
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            int i = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int r = rgba[i++] & 0xff;
                    int g = rgba[i++] & 0xff;
                    int b = rgba[i++] & 0xff;
                    int a = rgba[i++] & 0xff;
                    img.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                }
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", bos);
            return bos.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    static String javaTexturesPropertyValue(SkinService.SkinData skin, String username,
                                            String publicSkinBaseUrl) {
        if (skin == null) {
            return null;
        }
        StringBuilder json = new StringBuilder(256);
        json.append("{\"timestamp\":").append(System.currentTimeMillis())
                .append(",\"profileId\":\"").append(skin.uuid().toString().replace("-", ""))
                .append("\",\"profileName\":\"").append(escape(username))
                .append("\",\"textures\":{\"SKIN\":{");
        boolean hasSkin = (skin.skinPng() != null && skin.skinPng().length > 0)
                || (skin.canonical() != null && skin.canonical().skinPng() != null
                && skin.canonical().skinPng().length > 0)
                || (skin.skinDataBase64() != null && !skin.skinDataBase64().isBlank());
        json.append("\"url\":\"");
        if (hasSkin) {
            String base = publicSkinBaseUrl;
            if (base != null && !base.isBlank()) {
                String path = "/skin/" + skin.uuid() + ".png";
                if (base.endsWith("/skin") || base.contains("/skin/")) {
                    json.append(base);
                    if (!base.endsWith(".png")) {
                        if (!base.endsWith("/")) {
                            json.append('/');
                        }
                        json.append(skin.uuid()).append(".png");
                    }
                } else {
                    json.append(base).append(path);
                }
            } else {
                json.append("https://yapcore.local/skin/").append(skin.uuid());
            }
            if (skin.slim()) {
                json.append("\",\"metadata\":{\"model\":\"slim\"}");
            } else {
                json.append("\"");
            }
        } else {
            json.append("http://textures.minecraft.net/texture/yapcore-default\"");
        }
        json.append("}}}");
        return Base64.getEncoder().encodeToString(json.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Apply textures property onto an injected CraftPlayer via reflection (best-effort). */
    static boolean applyToPaperPlayer(String username, Object craftPlayer, String value) {
        if (craftPlayer == null || value == null) {
            return false;
        }
        try {
            Object profile = craftPlayer.getClass().getMethod("getPlayerProfile").invoke(craftPlayer);
            if (profile == null) {
                return false;
            }
            ClassLoader cl = craftPlayer.getClass().getClassLoader();
            boolean set = false;
            // Paper PlayerProfile#setProperty(String, String)
            try {
                profile.getClass().getMethod("setProperty", String.class, String.class)
                        .invoke(profile, "textures", value);
                set = true;
            } catch (NoSuchMethodException ignored) {
            }
            // destroystokyo ProfileProperty
            if (!set) {
                try {
                    Class<?> propCl = Class.forName("com.destroystokyo.paper.profile.ProfileProperty", true, cl);
                    Object prop = propCl.getConstructor(String.class, String.class)
                            .newInstance("textures", value);
                    profile.getClass().getMethod("setProperty", propCl).invoke(profile, prop);
                    set = true;
                } catch (ReflectiveOperationException ignored) {
                }
            }
            // Bukkit PlayerProfile / org.bukkit.profile.PlayerTextures path
            if (!set) {
                try {
                    Class<?> propCl = Class.forName("org.bukkit.profile.PlayerProfile", true, cl);
                    // getProperties + clear + set via setProperty(ProfileProperty) variants
                    Class<?> destProp = Class.forName("com.destroystokyo.paper.profile.ProfileProperty", true, cl);
                    Object prop = destProp.getConstructor(String.class, String.class)
                            .newInstance("textures", value);
                    for (var m : profile.getClass().getMethods()) {
                        if ("setProperty".equals(m.getName()) && m.getParameterCount() == 1) {
                            m.invoke(profile, prop);
                            set = true;
                            break;
                        }
                    }
                    if (!set && propCl.isInstance(profile)) {
                        // fall through
                    }
                } catch (ReflectiveOperationException ignored) {
                }
            }
            if (!set) {
                LOG.fine("applyToPaperPlayer: no setProperty variant for " + username);
                return false;
            }
            try {
                craftPlayer.getClass().getMethod("setPlayerProfile", profile.getClass())
                        .invoke(craftPlayer, profile);
            } catch (NoSuchMethodException e) {
                // Some CraftPlayer builds take the interface type
                Class<?> iface = Class.forName("com.destroystokyo.paper.profile.PlayerProfile", true, cl);
                craftPlayer.getClass().getMethod("setPlayerProfile", iface).invoke(craftPlayer, profile);
            }
            LOG.info("JE textures property applied for BE skin " + username);
            return true;
        } catch (Exception e) {
            LOG.fine("applyToPaperPlayer skin: " + e.getMessage());
            return false;
        }
    }

    static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
