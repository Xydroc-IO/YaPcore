package com.yapcore.crossplay.emote;

import com.yapcore.crossplay.bedrock.parity.ParityBand;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class EmoteAuthorityTest {

    private static EmoteAuthorityService service;

    @BeforeAll
    static void load() {
        service = new EmoteAuthorityService(ParityBand.of(ParityBand.DEFAULT), 0L);
    }

    @Test
    void loadsAllCatalogClips() {
        assertEquals(4, service.catalogs().emotes().size());
        assertEquals(4, service.clips().size());
        assertEquals(0, service.clips().missingClips());
    }

    @Test
    void waveClipHasRealBones() {
        EmoteClip wave = service.clips().byId("4c8ae710-df2e-47cd-814d-cc7bf21a3d67").orElseThrow();
        assertEquals("Wave", wave.name());
        assertTrue(wave.lengthSeconds() > 0.0);
        assertFalse(wave.boneTracks().isEmpty());
        assertTrue(wave.boneTracks().containsKey("rightArm"));
    }

    @Test
    void rejectsUnknownEmote() {
        assertTrue(service.tryPlay(UUID.randomUUID(), "Steve", "00000000-0000-0000-0000-000000000000", "TEST")
                .isEmpty());
    }

    @Test
    void acceptsCatalogIdAndName() {
        UUID u = UUID.randomUUID();
        assertTrue(service.tryPlay(u, "Steve", "4c8ae710-df2e-47cd-814d-cc7bf21a3d67", "TEST").isPresent());
        assertTrue(service.tryPlayByName(UUID.randomUUID(), "Alex", "wave", "TEST").isPresent());
        assertTrue(service.tryPlayByName(UUID.randomUUID(), "Alex", "Simple Clap", "TEST").isPresent());
    }

    @Test
    void allClipsHavePositiveLengthWithinTolerance() {
        for (EmoteClip clip : service.clips().allById().values()) {
            assertTrue(clip.lengthSeconds() > 0.0, clip.bedrockEmoteId());
            assertTrue(clip.lengthSeconds() <= 30.0, "unreasonable length " + clip.bedrockEmoteId());
            assertFalse(clip.boneTracks().isEmpty(), "bones required " + clip.bedrockEmoteId());
        }
    }
}
