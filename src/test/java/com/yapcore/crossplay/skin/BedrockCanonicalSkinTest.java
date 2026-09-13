package com.yapcore.crossplay.skin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class BedrockCanonicalSkinTest {

    @Test
    void jsonRoundTripPreservesShaAndGeometry() {
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        BedrockCanonicalSkin a = BedrockCanonicalSkin.classicPng(uuid, png, null, true)
                .ensureGeometryData();
        assertFalse(a.geometryData().isBlank());
        assertTrue(a.geometryData().contains("minecraft:geometry"));
        String json = a.toJson();
        BedrockCanonicalSkin b = BedrockCanonicalSkin.fromJson(json);
        assertEquals(a.contentSha256(), b.contentSha256());
        assertEquals(a.geometryName(), b.geometryName());
        assertEquals(a.slim(), b.slim());
        assertEquals(a.skinId(), b.skinId());
        assertTrue(b.personaPieces().isEmpty());
    }

    @Test
    void putCanonicalFiresOnSkinChanged() {
        SkinService skins = new SkinService();
        AtomicReference<String> seen = new AtomicReference<>();
        skins.setOnSkinChanged(seen::set);
        UUID uuid = UUID.randomUUID();
        skins.putCanonical("Notifier", BedrockCanonicalSkin.classicPng(uuid, null, null, false));
        assertEquals("Notifier", seen.get());
    }
}
