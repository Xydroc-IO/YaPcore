package com.yapcore.playerdata;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards product defaults for chest shops + auction house. */
final class EconomyFeaturesConfigTest {

    @Test
    void defaultConfigEnablesShopsAndAuctionsKeepsJobsOff() throws Exception {
        String yaml;
        try (var in = Objects.requireNonNull(
                EconomyFeaturesConfigTest.class.getResourceAsStream("/config.yml"),
                "config.yml missing from playerdata resources")) {
            yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(yaml.contains("shops: true"), "features.shops should default on");
        assertTrue(yaml.contains("auctions: true"), "features.auctions should default on");
        assertTrue(yaml.contains("jobs: false"), "features.jobs should stay off with YaPSkills");
        assertTrue(yaml.contains("traders: true"), "features.traders default on for YaPNpcs shop hubs");
        assertFalse(yaml.contains("shops: false"), "stale shops: false still in default config");
        assertFalse(yaml.contains("auctions: false"), "stale auctions: false still in default config");
    }
}
