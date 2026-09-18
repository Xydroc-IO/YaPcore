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

    @Test
    void stairsFacingMapsToDistinctRuntimes() {
        JeToBedrockBlockMapper mapper = new JeToBedrockBlockMapper(2169, new JeBlockRegistry());
        int east = mapper.mapBlockName(
                "minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]");
        int west = mapper.mapBlockName(
                "minecraft:oak_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]");
        int north = mapper.mapBlockName(
                "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]");
        int top = mapper.mapBlockName(
                "minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]");
        assertNotEquals(mapper.airRuntimeId(), east);
        assertNotEquals(east, west);
        assertNotEquals(east, north);
        assertNotEquals(east, top);
    }

    @Test
    void signsMapToOrientedPaletteEntries() {
        JeToBedrockBlockMapper mapper = new JeToBedrockBlockMapper(2169, new JeBlockRegistry());
        int stand0 = mapper.mapBlockName("minecraft:oak_sign[rotation=0,waterlogged=false]");
        int stand4 = mapper.mapBlockName("minecraft:oak_sign[rotation=4,waterlogged=false]");
        int wallN = mapper.mapBlockName("minecraft:oak_wall_sign[facing=north,waterlogged=false]");
        int wallS = mapper.mapBlockName("minecraft:oak_wall_sign[facing=south,waterlogged=false]");
        int birch = mapper.mapBlockName("minecraft:birch_sign[rotation=2,waterlogged=false]");
        assertNotEquals(mapper.airRuntimeId(), stand0);
        assertNotEquals(stand0, stand4);
        assertNotEquals(wallN, wallS);
        assertNotEquals(mapper.airRuntimeId(), birch);
        assertNotEquals(stand0, birch);
    }
}
