package com.yapcore.link.bedrock.translator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class JeBlockStateRemapperTest {

    @Test
    void stairsFacingProducesDistinctBedrockKeys() {
        String east = JeBlockStateRemapper.remap(
                "minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]");
        String west = JeBlockStateRemapper.remap(
                "minecraft:oak_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]");
        String top = JeBlockStateRemapper.remap(
                "minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]");
        assertEquals("minecraft:oak_stairs[upside_down_bit=0,weirdo_direction=0]", east);
        assertEquals("minecraft:oak_stairs[upside_down_bit=0,weirdo_direction=1]", west);
        assertEquals("minecraft:oak_stairs[upside_down_bit=1,weirdo_direction=0]", top);
        assertNotEquals(east, west);
        assertNotEquals(east, top);
    }

    @Test
    void standingSignUsesGroundSignDirection() {
        String oak = JeBlockStateRemapper.remap(
                "minecraft:oak_sign[rotation=4,waterlogged=false]");
        assertEquals("minecraft:standing_sign[ground_sign_direction=4]", oak);

        String birch = JeBlockStateRemapper.remap(
                "minecraft:birch_sign[rotation=8,waterlogged=false]");
        assertEquals("minecraft:birch_standing_sign[ground_sign_direction=8]", birch);

        String dark = JeBlockStateRemapper.remap(
                "minecraft:dark_oak_sign[rotation=0,waterlogged=false]");
        assertEquals("minecraft:darkoak_standing_sign[ground_sign_direction=0]", dark);
    }

    @Test
    void wallSignUsesFacingDirection() {
        String oak = JeBlockStateRemapper.remap(
                "minecraft:oak_wall_sign[facing=south,waterlogged=false]");
        assertEquals("minecraft:wall_sign[facing_direction=3]", oak);

        String birch = JeBlockStateRemapper.remap(
                "minecraft:birch_wall_sign[facing=east,waterlogged=false]");
        assertEquals("minecraft:birch_wall_sign[facing_direction=5]", birch);

        String dark = JeBlockStateRemapper.remap(
                "minecraft:dark_oak_wall_sign[facing=west,waterlogged=false]");
        assertEquals("minecraft:darkoak_wall_sign[facing_direction=4]", dark);
    }

    @Test
    void trapdoorSlabAndLeverUseBedrockProperties() {
        var trap = JeBlockStateRemapper.extraKeys(
                "minecraft:oak_trapdoor[facing=west,half=top,open=true,powered=false,waterlogged=false]");
        assertTrue(trap.contains("minecraft:trapdoor[direction=0,open_bit=1,upside_down_bit=1]"));

        var slab = JeBlockStateRemapper.extraKeys(
                "minecraft:oak_slab[type=top,waterlogged=false]");
        assertTrue(slab.contains("minecraft:oak_slab[minecraft:vertical_half=top]"));

        var lever = JeBlockStateRemapper.extraKeys(
                "minecraft:lever[face=floor,facing=east,powered=true]");
        assertTrue(lever.contains("minecraft:lever[lever_direction=down_east_west,open_bit=1]"));
    }
}
