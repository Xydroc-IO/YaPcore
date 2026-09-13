package com.yapcore.crossplay.skin;

import com.yapcore.crossplay.bedrock.cloudburst.CloudburstSession;
import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.data.skin.ImageData;
import org.cloudburstmc.protocol.bedrock.data.skin.PersonaPieceData;
import org.cloudburstmc.protocol.bedrock.data.skin.PersonaPieceType;
import org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin;
import org.cloudburstmc.protocol.bedrock.packet.PlayerSkinPacket;
import org.cloudburstmc.protocol.common.util.VarInts;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P4.8 JE textures property from BE skin registry + Phase 1 canonical skins. */
class SkinServiceTest {

    @Test
    void javaTexturesPropertyIsBase64Json() {
        SkinService skins = new SkinService();
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000099");
        skins.registerDefault("BedrockAlex", uuid);
        String prop = skins.javaTexturesPropertyValue("BedrockAlex");
        assertNotNull(prop);
        String json = new String(Base64.getDecoder().decode(prop));
        assertTrue(json.contains("textures"));
        assertTrue(json.contains("SKIN"));
        assertTrue(json.contains("profileId"));
        BedrockCanonicalSkin canonical = skins.getCanonical("BedrockAlex");
        assertNotNull(canonical);
        assertFalse(canonical.geometryData().isBlank());
    }

    @Test
    void hostedSkinUrlUsesPublicBase() {
        SkinService skins = new SkinService();
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        skins.setPublicSkinBaseUrl("https://cdn.example/yap");
        skins.putJavaSkin("Alex", uuid, png, null, true);
        String prop = skins.javaTexturesPropertyValue("Alex");
        assertNotNull(prop);
        String json = new String(Base64.getDecoder().decode(prop));
        assertTrue(json.contains("https://cdn.example/yap/skin/" + uuid + ".png"));
        assertTrue(json.contains("slim"));
        assertNotNull(skins.pngBytes("Alex"));
        assertNotNull(skins.getCanonical("Alex"));
    }

    @Test
    void putCanonicalRoundTripShaAndClientboundGeometry() {
        SkinService skins = new SkinService();
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        BedrockCanonicalSkin original = BedrockCanonicalSkin.classicPng(uuid, png, null, false)
                .ensureGeometryData();
        assertFalse(original.geometryData().isBlank());
        String expectedSha = original.contentSha256();
        assertFalse(expectedSha.isBlank());

        skins.putCanonical("Steve", original);
        BedrockCanonicalSkin stored = skins.getCanonical("Steve");
        assertNotNull(stored);
        assertEquals(expectedSha, stored.contentSha256());
        assertFalse(stored.geometryData().isBlank());

        ByteBuf pkt = skins.clientboundSkinPacket("Steve", 2207);
        assertNotNull(pkt);
        try {
            // Minimal empty player-list skin is ~100–200 bytes; canonical with geometry is much larger
            assertTrue(pkt.readableBytes() > 256,
                    "clientbound packet should include non-empty geometry_data, got "
                            + pkt.readableBytes());
            byte[] raw = new byte[pkt.readableBytes()];
            pkt.getBytes(pkt.readerIndex(), raw);
            String asText = new String(raw, StandardCharsets.ISO_8859_1);
            assertTrue(asText.contains("minecraft:geometry") || asText.contains("format_version"),
                    "packet bytes should embed geometry JSON");
        } finally {
            pkt.release();
        }
    }

    @Test
    void ingestCloudburstSerializedSkinPreservesGeometryAndPersonaPiece() {
        SkinService skins = new SkinService();
        AtomicReference<String> notified = new AtomicReference<>();
        skins.setOnSkinChanged(notified::set);

        UUID uuid = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        String geoData = "{\"format_version\":\"1.12.0\",\"minecraft:geometry\":[{"
                + "\"description\":{\"identifier\":\"geometry.yap.ingest_test\",\"texture_width\":64,"
                + "\"texture_height\":64},\"bones\":[]}]}";
        String patch = "{\"geometry\":{\"default\":\"geometry.yap.ingest_test\"}}";
        byte[] rgba = new byte[64 * 64 * 4];
        for (int i = 0; i < rgba.length; i += 4) {
            rgba[i] = (byte) 0x10;
            rgba[i + 1] = (byte) 0x20;
            rgba[i + 2] = (byte) 0x30;
            rgba[i + 3] = (byte) 0xFF;
        }
        ImageData skinImg = ImageData.of(64, 64, rgba);
        PersonaPieceData piece = new PersonaPieceData(
                "piece-yap-1",
                PersonaPieceType.HAIR,
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                false,
                "product-yap");
        SerializedSkin serialized = SerializedSkin.builder()
                .skinId("YapIngestSkin")
                .playFabId("playfab-yap")
                .skinResourcePatch(patch)
                .skinData(skinImg)
                .capeData(ImageData.EMPTY)
                .geometryData(geoData)
                .geometryDataEngineVersion("1.14.0")
                .animationData("anim-yap")
                .animations(List.of())
                .capeId("")
                .fullSkinId("YapIngestSkin")
                .armSize("wide")
                .skinColor("#0")
                .personaPieces(List.of(piece))
                .tintColors(List.of())
                .premium(false)
                .persona(true)
                .capeOnClassic(false)
                .primaryUser(true)
                .overridingPlayerAppearance(false)
                .trusted(true)
                .profileHash("")
                .build();

        skins.ingestClientSkin("IngestPlayer", uuid, serialized);

        assertEquals("IngestPlayer", notified.get());
        BedrockCanonicalSkin canonical = skins.getCanonical("IngestPlayer");
        assertNotNull(canonical);
        assertEquals(geoData, canonical.geometryData());
        assertEquals("geometry.yap.ingest_test", canonical.geometryName());
        assertEquals("playfab-yap", canonical.playFabId());
        assertEquals("anim-yap", canonical.animationData());
        assertEquals(1, canonical.personaPieces().size());
        assertEquals("piece-yap-1", canonical.personaPieces().get(0).pieceId());
        assertEquals("hair", canonical.personaPieces().get(0).pieceType());
        assertTrue(canonical.persona());
        String sha1 = canonical.contentSha256();
        assertFalse(sha1.isBlank());
        assertEquals(sha1, skins.getCanonical("IngestPlayer").contentSha256());
    }

    @Test
    void ingestPlayerSkinByteBufViaCloudburstEncodePreservesFields() {
        SkinService skins = new SkinService();
        AtomicReference<String> notified = new AtomicReference<>();
        skins.setOnSkinChanged(notified::set);

        UUID uuid = UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff");
        String geoData = "{\"format_version\":\"1.12.0\",\"minecraft:geometry\":[{"
                + "\"description\":{\"identifier\":\"geometry.yap.buf_test\"},\"bones\":[]}]}";
        PersonaPieceData piece = new PersonaPieceData(
                "buf-piece", PersonaPieceType.TOP,
                UUID.fromString("22222222-2222-2222-2222-222222222222"), true, "");
        SerializedSkin serialized = SerializedSkin.builder()
                .skinId("BufSkin")
                .playFabId("pf")
                .skinResourcePatch("{\"geometry\":{\"default\":\"geometry.yap.buf_test\"}}")
                .skinData(ImageData.of(64, 64, new byte[64 * 64 * 4]))
                .capeData(ImageData.EMPTY)
                .geometryData(geoData)
                .geometryDataEngineVersion("1.14.0")
                .animationData("")
                .animations(List.of())
                .capeId("")
                .fullSkinId("BufSkin")
                .armSize("slim")
                .skinColor("#0")
                .personaPieces(List.of(piece))
                .tintColors(List.of())
                .premium(true)
                .persona(true)
                .capeOnClassic(false)
                .primaryUser(true)
                .overridingPlayerAppearance(false)
                .trusted(true)
                .profileHash("hash")
                .build();

        PlayerSkinPacket pkt = new PlayerSkinPacket();
        pkt.setUuid(uuid);
        pkt.setSkin(serialized);
        pkt.setNewSkinName("BufSkin");
        pkt.setOldSkinName("");
        pkt.setTrustedSkin(true);

        CloudburstSession cb = CloudburstSession.create(2207);
        ByteBuf encoded = cb.encode(pkt);
        try {
            VarInts.readUnsignedInt(encoded);
            skins.ingestClientSkin("BufPlayer", encoded, 2207);
        } finally {
            encoded.release();
        }

        assertEquals("BufPlayer", notified.get());
        BedrockCanonicalSkin canonical = skins.getCanonical("BufPlayer");
        assertNotNull(canonical);
        assertEquals(geoData, canonical.geometryData());
        assertFalse(canonical.personaPieces().isEmpty());
        assertEquals("buf-piece", canonical.personaPieces().get(0).pieceId());
        assertTrue(canonical.slim());
        assertTrue(canonical.premium());
        String sha = canonical.contentSha256();
        assertFalse(sha.isBlank());
        assertEquals(sha, skins.getCanonical("BufPlayer").contentSha256());
    }

    @Test
    void ingestLegacyCanonicalByteBufRoundTrip() {
        SkinService skins = new SkinService();
        UUID uuid = UUID.fromString("cccccccc-dddd-eeee-ffff-000000000001");
        String geoData = "{\"format_version\":\"1.12.0\",\"minecraft:geometry\":[{"
                + "\"description\":{\"identifier\":\"geometry.yap.legacy\"},\"bones\":[]}]}";
        BedrockCanonicalSkin original = new BedrockCanonicalSkin(
                uuid,
                "LegacySkin",
                "pf-legacy",
                "{\"geometry\":{\"default\":\"geometry.yap.legacy\"}}",
                null,
                null,
                "geometry.yap.legacy",
                geoData,
                "1.14.0",
                "legacy-anim",
                "",
                "LegacySkin",
                false,
                "#0",
                List.of(new BedrockCanonicalSkin.PersonaPiece(
                        "legacy-piece", "body",
                        "33333333-3333-3333-3333-333333333333", false, "prod")),
                List.of(),
                false,
                true,
                false,
                true,
                false,
                "true",
                "",
                List.of());

        // Craft body via playerSkin then strip packet id — exact writeCanonicalSkin layout
        ByteBuf full = com.yapcore.crossplay.bedrock.codec.BedrockLoginCodec.playerSkin(original, 776);
        try {
            com.yapcore.crossplay.bedrock.BedrockPacketCodec.readUnsignedVarInt(full);
            skins.ingestClientSkin("LegacyPlayer", full, 776);
            BedrockCanonicalSkin got = skins.getCanonical("LegacyPlayer");
            assertNotNull(got);
            assertEquals(geoData, got.geometryData());
            assertEquals(1, got.personaPieces().size());
            assertEquals("legacy-piece", got.personaPieces().get(0).pieceId());
            assertEquals("body", got.personaPieces().get(0).pieceType());
            assertEquals("legacy-anim", got.animationData());
            assertEquals(original.contentSha256(), got.contentSha256());
        } finally {
            full.release();
        }
    }
}
