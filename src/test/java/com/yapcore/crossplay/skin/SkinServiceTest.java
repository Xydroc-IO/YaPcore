package com.yapcore.crossplay.skin;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P4.8 JE textures property from BE skin registry. */
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
    }
}
