package com.yapcore.tailor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

final class PresenceUiCodecTest {

    @Test
    void wardrobeV2RoundTrip() {
        var view = new PresenceUiCodec.WardrobeView(
                true,
                "https://cdn.example/skin.png",
                "",
                8L,
                List.of(
                        new PresenceUiCodec.SlotView(7L, "Cool|Skin", false, "https://a/b.png", null),
                        new PresenceUiCodec.SlotView(8L, "Wave", true, "https://c/d.png", "https://cape")));
        String enc = PresenceUiCodec.encodeWardrobe(view);
        assertTrue(enc.startsWith("WARDROBE|v2|"));
        var decoded = PresenceUiCodec.decodeWardrobe(enc).orElseThrow();
        assertTrue(decoded.slim());
        assertEquals("https://cdn.example/skin.png", decoded.activeUrl());
        assertEquals(8L, decoded.activeSlotId());
        assertEquals(2, decoded.slots().size());
        assertEquals("Cool|Skin", decoded.slots().get(0).name());
        assertEquals(8L, decoded.slots().get(1).id());
        assertTrue(decoded.slots().get(1).slim());
    }

    @Test
    void wardrobeV1StillDecodes() {
        String enc = "WARDROBE|v1|0|"
                + PresenceUiCodec.b64("https://a.png")
                + "||"
                + "1,0," + PresenceUiCodec.b64("Old") + "," + PresenceUiCodec.b64("https://slot.png") + ",";
        var decoded = PresenceUiCodec.decodeWardrobe(enc).orElseThrow();
        assertEquals(-1L, decoded.activeSlotId());
        assertEquals(1, decoded.slots().size());
        assertEquals("Old", decoded.slots().get(0).name());
    }

    @Test
    void emoteCatalogRoundTrip() {
        String enc = PresenceUiCodec.encodeEmoteCatalog(List.of(
                new PresenceUiCodec.EmoteView("4c8ae710-df2e-47cd-814d-cc7bf21a3d67", "Wave"),
                new PresenceUiCodec.EmoteView("9a469a61-c83b-4ba9-b507-bdbe64430582", "Simple Clap")));
        var decoded = PresenceUiCodec.decodeEmoteCatalog(enc).orElseThrow();
        assertEquals(2, decoded.size());
        assertEquals("Wave", decoded.get(0).name());
    }
}
