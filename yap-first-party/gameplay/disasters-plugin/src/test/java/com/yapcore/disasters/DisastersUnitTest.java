package com.yapcore.disasters;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisastersUnitTest {

    @Test
    void disasterTypeParseRoundTripAndAliases() {
        assertEquals(DisasterType.CLEAR, DisasterType.parse("sun"));
        assertEquals(DisasterType.RAIN, DisasterType.parse("STORM"));
        assertEquals(DisasterType.THUNDER, DisasterType.parse("thunderstorm"));
        assertEquals(DisasterType.HURRICANE, DisasterType.parse("typhoon"));
        assertEquals(DisasterType.TORNADO, DisasterType.parse("twister"));
        assertEquals(DisasterType.EARTHQUAKE, DisasterType.parse("quake"));
        assertEquals(DisasterType.VOLCANO, DisasterType.parse("eruption"));
        assertEquals(DisasterType.BLIZZARD, DisasterType.parse("snow"));
        assertEquals(DisasterType.DROUGHT, DisasterType.parse("heatwave"));
        assertEquals(DisasterType.METEOR, DisasterType.parse("shower"));
        assertEquals(DisasterType.TSUNAMI, DisasterType.parse("flood"));
        assertNull(DisasterType.parse(null));
        assertNull(DisasterType.parse(""));
        assertNull(DisasterType.parse("nope"));
        assertEquals("volcano", DisasterType.VOLCANO.configKey());
        assertEquals(11, DisasterType.values().length);
    }

    @Test
    void severityOrderingViaFxAndConfigDefaults() {
        assertFalse(DisasterType.CLEAR.hasFx());
        assertFalse(DisasterType.RAIN.hasFx());
        assertTrue(DisasterType.THUNDER.hasFx());
        assertTrue(DisasterType.TSUNAMI.hasFx());
        // Weather-only kinds come first; FX disasters follow in enum declaration order.
        assertTrue(DisasterType.CLEAR.ordinal() < DisasterType.RAIN.ordinal());
        assertTrue(DisasterType.RAIN.ordinal() < DisasterType.THUNDER.ordinal());
        assertTrue(DisasterType.THUNDER.ordinal() < DisasterType.METEOR.ordinal());
        assertTrue(DisasterType.METEOR.ordinal() < DisasterType.TSUNAMI.ordinal());

        EnumSet<DisasterType> fx = EnumSet.noneOf(DisasterType.class);
        for (DisasterType t : DisasterType.values()) {
            if (t.hasFx()) {
                fx.add(t);
            }
        }
        assertEquals(9, fx.size());

        DisastersConfig config = new DisastersConfig(null);
        assertTrue(config.enabled());
        assertTrue(config.broadcastStart());
        assertTrue(config.broadcastEnd());
        assertFalse(config.grief());
        assertEquals(120, config.defaultDurationSeconds());
        assertTrue(config.protectClaims());
        assertTrue(config.worldAllowed("world"));
        assertEquals(48.0, config.volcanoSiteSnapBlocks());
    }

    @Test
    void pluginYmlMainClass() throws Exception {
        assertEquals(DisastersPlugin.class, Class.forName("com.yapcore.disasters.DisastersPlugin"));
        try (InputStream in = DisastersPlugin.class.getResourceAsStream("/plugin.yml")) {
            assertNotNull(in);
            String yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(yml.contains("main: com.yapcore.disasters.DisastersPlugin"));
            assertTrue(yml.contains("folia-supported: true"));
        }
    }
}
