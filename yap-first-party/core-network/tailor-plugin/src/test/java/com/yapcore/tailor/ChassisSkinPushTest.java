package com.yapcore.tailor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ChassisSkinPushTest {

    @Test
    void resolveSkinApplyBaseDefaultsToLocalhostSkin() {
        String base = ChassisSkinPush.resolveSkinApplyBase(null);
        assertTrue(base.startsWith("http://127.0.0.1:"));
        assertTrue(base.endsWith("/skin"));
    }

    @Test
    void buildApplyJsonIncludesUsernameAndNullCanonical() {
        UUID uuid = UUID.fromString("11111111-1111-1111-1111-111111111111");
        ActiveSkin skin = ActiveSkin.of(uuid, "https://example/skin.png", null, SkinModel.WIDE, null, 1L);
        String json = ChassisSkinPush.buildApplyJson("Steve", uuid, false, new byte[]{1, 2}, null, skin);
        assertTrue(json.contains("\"username\":\"Steve\""));
        assertTrue(json.contains("\"uuid\":\"11111111-1111-1111-1111-111111111111\""));
        assertTrue(json.contains("\"slim\":false"));
        assertTrue(json.contains("\"bedrockCanonicalJson\":null"));
        assertTrue(json.contains("\"skinPngBase64\":\"AQI=\""));
    }

    @Test
    void buildCompactCanonicalJsonHasClassicFields() {
        UUID uuid = UUID.fromString("22222222-2222-2222-2222-222222222222");
        ActiveSkin skin = ActiveSkin.of(uuid, null, null, SkinModel.SLIM, null, 1L);
        String json = ChassisSkinPush.buildCompactCanonicalJson(uuid, true, new byte[]{9}, null, skin);
        assertTrue(json.contains("\"slim\":true"));
        assertTrue(json.contains("\"skinId\":\"Standard_CustomSlim\""));
        assertTrue(json.contains("\"geometryName\":\"geometry.humanoid.customSlim\""));
        assertTrue(json.contains("\"geometryData\":\"\""));
        assertEquals(uuid.toString(), json.replaceAll("(?s).*\"uuid\":\"([^\"]+)\".*", "$1"));
    }
}
