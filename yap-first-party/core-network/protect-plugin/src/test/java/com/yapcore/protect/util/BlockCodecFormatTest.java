package com.yapcore.protect.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockCodecFormatTest {

    @Test
    void splitEncodedParsesMaterialDataAndTe() {
        BlockCodec.EncodedParts parts = BlockCodec.splitEncoded(
                "OAK_SIGN|minecraft:oak_sign[rotation=0]|#te;sf0=" + BlockCodec.b64("Hello"));
        assertEquals("OAK_SIGN", parts.material());
        assertEquals("minecraft:oak_sign[rotation=0]", parts.blockData());
        assertTrue(parts.tileExtras().startsWith("#te;"));
        Map<String, String> te = BlockCodec.parseTe(parts.tileExtras());
        assertEquals("Hello", BlockCodec.fromB64(te.get("sf0")));
    }

    @Test
    void splitEncodedHandlesAirAndLegacyWithoutTe() {
        assertEquals("AIR", BlockCodec.splitEncoded("AIR").material());
        BlockCodec.EncodedParts stone = BlockCodec.splitEncoded("STONE|minecraft:stone");
        assertEquals("STONE", stone.material());
        assertEquals("minecraft:stone", stone.blockData());
        assertNull(stone.tileExtras());
    }

    @Test
    void parseTeIgnoresBlank() {
        assertTrue(BlockCodec.parseTe("#te").isEmpty());
        assertTrue(BlockCodec.parseTe(null).isEmpty());
    }
}
