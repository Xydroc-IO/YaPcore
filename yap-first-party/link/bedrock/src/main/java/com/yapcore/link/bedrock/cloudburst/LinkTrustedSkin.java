package com.yapcore.link.bedrock.cloudburst;

import java.awt.Color;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.logging.Logger;
import org.cloudburstmc.protocol.bedrock.data.skin.ImageData;
import org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket;

/**
 * Geyser-valid PlayerList skin: 64×64 Steve RGBA + humanoid geometry JSON.
 *
 * <p>Probe 051738: flat-fill skin with empty {@code geometryData} encoded as ~16KB PlayerList and
 * immediately preceded post-JOIN_OK disconnect (IC-90). Chassis/docs require non-empty geometry
 * for {@code geometry.humanoid.custom} (see {@code BedrockCanonicalSkin}).
 */
public final class LinkTrustedSkin {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    /** Hard skip — runaway encode / persona bloat (64×64 + geo is typically ~24KB). */
    public static final int MAX_SAFE_ENCODED_BYTES = 48_000;
    /**
     * Soft preference from ops notes. Impossible for raw 64×64 RGBA alone (16KB); used only as
     * a log hint. Valid Geyser skins are expected in the 16–28KB encoded range.
     */
    public static final int PREFERRED_ENCODED_BYTES = 4_096;

    private static final String GEO_NAME = "geometry.humanoid.custom";
    /** Cloudburst legacy patch spacing — matches {@code SerializedSkin.convertLegacyGeometryName}. */
    private static final String RESOURCE_PATCH =
            "{\"geometry\" : {\"default\" : \"" + GEO_NAME + "\"}}";

    private static volatile byte[] steveRgba;
    private static volatile String geometryJson;
    private static volatile SerializedSkin cachedSkin;

    private LinkTrustedSkin() {
    }

    /** Shared trusted Steve skin (immutable payload; safe to reuse across entries). */
    public static SerializedSkin steveWide() {
        SerializedSkin local = cachedSkin;
        if (local != null) {
            return local;
        }
        synchronized (LinkTrustedSkin.class) {
            if (cachedSkin != null) {
                return cachedSkin;
            }
            byte[] rgba = loadSteveRgba();
            String geo = loadGeometryJson();
            if (rgba == null || rgba.length < ImageData.DOUBLE_SKIN_SIZE || geo == null || geo.isBlank()) {
                throw new IllegalStateException(
                        "LinkTrustedSkin missing steve RGBA or geometry fixture");
            }
            cachedSkin = SerializedSkin.builder()
                    .skinId("Standard_Custom")
                    .playFabId("")
                    .geometryName(GEO_NAME)
                    .skinResourcePatch(RESOURCE_PATCH)
                    .skinData(ImageData.of(64, 64, rgba))
                    .animations(Collections.emptyList())
                    .capeData(ImageData.EMPTY)
                    .geometryData(geo)
                    .geometryDataEngineVersion("1.14.0")
                    .animationData("")
                    .premium(false)
                    .persona(false)
                    .capeOnClassic(false)
                    .primaryUser(true)
                    .capeId("")
                    .fullSkinId("Standard_Custom")
                    .armSize("wide")
                    .skinColor("#0")
                    .color(new Color(0, true))
                    .personaPieces(Collections.emptyList())
                    .tintColors(Collections.emptyList())
                    .overridingPlayerAppearance(false)
                    .trusted(true)
                    .profileHash("")
                    .build();
            if (!cachedSkin.isValid()) {
                throw new IllegalStateException("LinkTrustedSkin Steve skin failed SerializedSkin.isValid()");
            }
            LOG.info("BE LinkTrustedSkin Steve 64x64 geometryBytes=" + geo.length()
                    + " rgbaBytes=" + rgba.length + " valid=true");
            return cachedSkin;
        }
    }

    /**
     * Sanity for PlayerList ADD: valid 64×64 skin + non-empty geometry + encoded size under
     * {@link #MAX_SAFE_ENCODED_BYTES}. Returns reject reason, or null if OK to send.
     */
    public static String rejectReason(PlayerListPacket packet, int encodedBytes) {
        if (packet == null || packet.getEntries() == null || packet.getEntries().isEmpty()) {
            return "empty_entries";
        }
        SerializedSkin skin = packet.getEntries().get(0).getSkin();
        if (skin == null) {
            return "null_skin";
        }
        if (!skin.isValid()) {
            return "skin_not_valid";
        }
        ImageData data = skin.getSkinData();
        if (data == null || data.getWidth() < 64 || data.getHeight() < 32
                || data.getImage() == null || data.getImage().length < ImageData.DOUBLE_SKIN_SIZE) {
            return "skin_image_too_small";
        }
        String geo = skin.getGeometryData();
        if (geo == null || geo.isBlank()) {
            return "geometry_empty";
        }
        if (encodedBytes <= 0) {
            return "encode_failed";
        }
        if (encodedBytes > MAX_SAFE_ENCODED_BYTES) {
            return "encoded_too_large=" + encodedBytes;
        }
        return null;
    }

    private static byte[] loadSteveRgba() {
        byte[] local = steveRgba;
        if (local != null) {
            return local;
        }
        synchronized (LinkTrustedSkin.class) {
            if (steveRgba != null) {
                return steveRgba;
            }
            String path = "protocol/bedrock/skin/steve_wide.rgba";
            try (InputStream in = LinkTrustedSkin.class.getClassLoader().getResourceAsStream(path)) {
                if (in == null) {
                    LOG.severe("Missing resource " + path);
                    return null;
                }
                steveRgba = in.readAllBytes();
                return steveRgba;
            } catch (Exception e) {
                LOG.severe("Failed loading " + path + ": " + e.getMessage());
                return null;
            }
        }
    }

    private static String loadGeometryJson() {
        String local = geometryJson;
        if (local != null) {
            return local;
        }
        synchronized (LinkTrustedSkin.class) {
            if (geometryJson != null) {
                return geometryJson;
            }
            String path = "protocol/bedrock/skin/geometry.humanoid.custom.json";
            try (InputStream in = LinkTrustedSkin.class.getClassLoader().getResourceAsStream(path)) {
                if (in == null) {
                    LOG.severe("Missing resource " + path);
                    return null;
                }
                geometryJson = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                return geometryJson;
            } catch (Exception e) {
                LOG.severe("Failed loading " + path + ": " + e.getMessage());
                return null;
            }
        }
    }
}
