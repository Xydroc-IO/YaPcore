package com.yapcore.protocol.java.mod;

import com.yapcore.protocol.java.PacketFactory;
import com.yapcore.protocol.java.codec.McCodec;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Tolerates Fabric / Forge / NeoForge handshake noise so those clients can
 * complete vanilla-compatible joins when mods are client-side or soft-required.
 */
public final class ModLoaderCompat {

    private static final Logger LOG = Logger.getLogger("YaPcore.ModCompat");

    public enum LoaderHint {
        VANILLA, FABRIC, FORGE, NEOFORGE, QUILT, UNKNOWN
    }

    private final AtomicInteger queryId = new AtomicInteger(1);
    private LoaderHint hint = LoaderHint.VANILLA;

    public LoaderHint hint() {
        return hint;
    }

    public void noteBrand(String brand) {
        if (brand == null) {
            return;
        }
        String b = brand.toLowerCase(Locale.ROOT);
        if (b.contains("neoforge")) {
            hint = LoaderHint.NEOFORGE;
        } else if (b.contains("forge") || b.contains("fml")) {
            hint = LoaderHint.FORGE;
        } else if (b.contains("fabric")) {
            hint = LoaderHint.FABRIC;
        } else if (b.contains("quilt")) {
            hint = LoaderHint.QUILT;
        }
        LOG.info("Client brand/loader hint=" + hint + " raw=" + brand);
    }

    public void noteChannel(String channel) {
        if (channel == null) {
            return;
        }
        String c = channel.toLowerCase(Locale.ROOT);
        if (c.contains("neoforge")) {
            hint = LoaderHint.NEOFORGE;
        } else if (c.startsWith("fml:") || c.contains("forge")) {
            hint = LoaderHint.FORGE;
        } else if (c.startsWith("fabric:")) {
            hint = LoaderHint.FABRIC;
        }
    }

    /**
     * Answer Login Plugin Requests that Forge/NeoForge clients may expect.
     * Unknown channels: no-op (client already answered).
     */
    public void onLoginPluginResponse(Channel ch, int messageId, boolean understood, ByteBuf data) {
        LOG.fine("Login plugin response id=" + messageId + " understood=" + understood
                + " loader=" + hint);
    }

    /** Optional: probe brand early (ignored by vanilla). */
    public void sendBrandProbe(Channel ch) {
        // No-op for login; brand is play-phase. Kept for API completeness.
    }

    public static String detectFromHandshakeHost(String host) {
        if (host == null) {
            return null;
        }
        // Some launchers append \0forge or similar historically — strip for logging
        int nul = host.indexOf('\0');
        if (nul >= 0 && host.length() > nul + 1) {
            return host.substring(nul + 1);
        }
        return null;
    }

    public void logAccepted(String username) {
        LOG.info("Accepting " + username + " with loader-compat mode=" + hint
                + " (vanilla protocol path; server-side NeoForge/Forge mods need matching APIs)");
    }

    public ByteBuf brandPluginMessage(int protocolVersion, String brand) {
        byte[] bytes = brand.getBytes(StandardCharsets.UTF_8);
        ByteBuf payload = io.netty.buffer.Unpooled.buffer();
        McCodec.writeVarInt(payload, bytes.length);
        payload.writeBytes(bytes);
        byte[] data = new byte[payload.readableBytes()];
        payload.readBytes(data);
        payload.release();
        return PacketFactory.pluginMessage(protocolVersion, "minecraft:brand", data);
    }
}
