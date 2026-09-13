package com.yapcore.link.bedrock.translator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yapcore.link.bedrock.downstream.JeBlockRegistry;
import org.junit.jupiter.api.Test;

final class JeToBedrockBlockMapperTest {

    @Test
    void mapsAirStoneGrassDirtToDistinctHashedRuntimes() {
        JeToBedrockBlockMapper mapper = new JeToBedrockBlockMapper(2169, new JeBlockRegistry());
        int air = mapper.mapJeGlobalId(0);
        int stone = mapper.mapJeGlobalId(1);
        int grass = mapper.mapJeGlobalId(9); // grass_block[snowy=false]
        int dirt = mapper.mapJeGlobalId(10);
        assertEquals(mapper.airRuntimeId(), air);
        assertEquals(mapper.stoneRuntimeId(), stone);
        assertNotEquals(air, stone);
        assertNotEquals(stone, grass);
        assertNotEquals(grass, dirt);
        assertNotEquals(air, grass);
        // Miss → air (not stone)
        int miss = mapper.mapJeGlobalId(2_000_000);
        assertEquals(mapper.airRuntimeId(), miss);
        assertTrue(mapper.mapMisses() >= 1);
    }

    @Test
    void mapsDeepslate30417NotAir() {
        JeToBedrockBlockMapper mapper = new JeToBedrockBlockMapper(2169, new JeBlockRegistry());
        assertEquals(32366, JeBlockRegistry.staticSize());
        String state = new JeBlockRegistry().stateName(30417);
        assertTrue(state != null && state.startsWith("minecraft:deepslate"), "30417=" + state);
        int deepslate = mapper.mapJeGlobalId(30417);
        assertNotEquals(mapper.airRuntimeId(), deepslate, "deepslate must not fall back to air");
        assertTrue(mapper.mapHits() >= 1);
    }
}
