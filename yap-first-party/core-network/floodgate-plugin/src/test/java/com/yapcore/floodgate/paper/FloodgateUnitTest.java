package com.yapcore.floodgate.paper;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloodgateUnitTest {

    @Test
    void bedrockUuidHeuristicUsesMsbZero() {
        FloodgateRuntime runtime = new FloodgateRuntime(Logger.getAnonymousLogger(), null);
        long xuid = 0xABCDEFL;
        UUID bedrock = new UUID(0L, xuid);
        UUID javaLike = UUID.randomUUID();

        assertTrue(runtime.isBedrock(bedrock));
        assertFalse(runtime.isBedrock(javaLike));

        FloodgateRuntime.PlayerInfo info = runtime.remember(bedrock, ".Steve", null);
        assertNotNull(info);
        assertEquals(xuid, info.xuid());
        assertEquals("Steve", info.bedrockUsername());
        assertTrue(runtime.get(bedrock).isPresent());
    }

    @Test
    void bedrockDataParseAndJavaUuid() {
        String linked = "Alex;" + UUID.fromString("11111111-1111-1111-1111-111111111111")
                + ";" + UUID.fromString("22222222-2222-2222-2222-222222222222");
        String[] fields = {
                "1.21", "BedrockSteve", "999", "7", "en_US", "", "", "10.0.0.1", linked, "1", "", ""
        };
        FloodgateRuntime.BedrockData data =
                FloodgateRuntime.BedrockData.parse(String.join("\0", fields));
        assertEquals("BedrockSteve", data.username());
        assertEquals(999L, data.xuid());
        assertTrue(data.linked().isPresent());
        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), data.javaUuid());

        String[] unlinked = {
                "1.21", "OnlyBe", "42", "1", "en_US", "", "", "127.0.0.1", "null", "0", "", ""
        };
        FloodgateRuntime.BedrockData bare =
                FloodgateRuntime.BedrockData.parse(String.join("\0", unlinked));
        assertEquals(new UUID(0L, 42L), bare.javaUuid());
        assertThrows(IllegalArgumentException.class,
                () -> FloodgateRuntime.BedrockData.parse("too\0few"));
    }

    @Test
    void identifierAndPluginYml() throws Exception {
        assertEquals("^Floodgate^", FloodgateRuntime.IDENTIFIER);
        try (InputStream in = FloodgatePlugin.class.getResourceAsStream("/plugin.yml")) {
            assertNotNull(in);
            String yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(yml.contains("main: com.yapcore.floodgate.paper.FloodgatePlugin"));
            assertTrue(yml.contains("name: YaPFloodgate"));
            assertTrue(yml.contains("yapfloodgate.admin"));
        }
    }
}
