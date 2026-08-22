package com.yapcore.games.arena;

import com.yapcore.games.mode.GameModeLoader;
import com.yapcore.games.mode.GameModeType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArenaLoaderTest {

    @Test
    void parseArenaReadsCuboidAndSpawns() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new java.io.StringReader("""
                world: world
                min:
                  x: -10
                  y: 60
                  z: -10
                max:
                  x: 10
                  y: 80
                  z: 10
                spawns:
                  - x: 0.5
                    y: 65.0
                    z: 0.5
                    yaw: 90
                    pitch: 0
                lobby:
                  x: 0.5
                  y: 100.0
                  z: 0.5
                """));
        ArenaDefinition arena = ArenaLoader.parseArena("pit", yaml);
        assertNotNull(arena);
        assertEquals("pit", arena.id());
        assertEquals(-10, arena.minX());
        assertEquals(10, arena.maxX());
        assertEquals(1, arena.spawns().size());
        assertNotNull(arena.lobby());
    }

    @Test
    void parseModeReadsFfaSettings() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new java.io.StringReader("""
                id: ffa
                display-name: Free For All
                type: FFA
                arena: ffa_pit
                kit: ffa_standard
                min-players: 2
                max-players: 8
                win-kills: 10
                respawn-in-arena: true
                """));
        var mode = GameModeLoader.parseMode(yaml);
        assertNotNull(mode);
        assertEquals("ffa", mode.id().id());
        assertEquals(GameModeType.FFA, mode.type());
        assertEquals(10, mode.winKills());
        assertTrue(mode.respawnInArena());
    }
}
