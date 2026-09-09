package com.yapcore.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockStateAliasesTest {

    @Test
    void normalizesGrass() {
        assertEquals("minecraft:short_grass", BlockStateAliases.normalize("minecraft:grass"));
        assertEquals("minecraft:short_grass", BlockStateAliases.normalize("grass"));
    }

    @Test
    void stripsPlayerHeadNbtAndMapsFacingToWallHead() {
        assertEquals("minecraft:player_wall_head[facing=north]",
                BlockStateAliases.normalize("minecraft:player_head{facing=north}"));
        assertEquals("minecraft:player_head",
                BlockStateAliases.normalize("minecraft:player_head{facing"));
        assertEquals("minecraft:player_head",
                BlockStateAliases.normalize("minecraft:player_head{SkullOwner:Steve}"));
    }

    @Test
    void keepsBracketFacingAsWallHead() {
        assertEquals("minecraft:player_wall_head[facing=east]",
                BlockStateAliases.normalize("minecraft:player_head[facing=east]"));
    }
}
