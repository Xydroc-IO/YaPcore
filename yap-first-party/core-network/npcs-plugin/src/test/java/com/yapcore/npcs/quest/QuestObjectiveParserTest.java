package com.yapcore.npcs.quest;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class QuestObjectiveParserTest {

    @Test
    void parsesSkillLevelObjective() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("id", "mining_lvl");
        raw.put("type", "SKILL_LEVEL");
        raw.put("skill", "mining");
        raw.put("level", 20);
        QuestDefinition.Objective obj = QuestPackLoader.parseObjective(raw);
        assertEquals(QuestDefinition.ObjectiveType.SKILL_LEVEL, obj.type());
        assertEquals("mining", obj.skillId());
        assertEquals(20, obj.minLevel());
    }

    @Test
    void parsesCraftAndBossObjectives() {
        Map<String, Object> craft = new LinkedHashMap<>();
        craft.put("id", "smith_dagger");
        craft.put("type", "CRAFT_ITEM");
        craft.put("recipe", "iron_dagger");
        craft.put("amount", 1);
        QuestDefinition.Objective c = QuestPackLoader.parseObjective(craft);
        assertEquals("iron_dagger", c.recipeId());

        Map<String, Object> boss = new LinkedHashMap<>();
        boss.put("id", "slay_king");
        boss.put("type", "KILL_BOSS");
        boss.put("boss-id", "goblin_king");
        QuestDefinition.Objective b = QuestPackLoader.parseObjective(boss);
        assertEquals("goblin_king", b.bossId());
    }

    @Test
    void gatherUsesMaterial() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("id", "mine_iron");
        raw.put("type", "GATHER");
        raw.put("material", "IRON_ORE");
        raw.put("amount", 5);
        QuestDefinition.Objective obj = QuestPackLoader.parseObjective(raw);
        assertEquals(QuestDefinition.ObjectiveType.GATHER, obj.type());
        assertNotNull(obj.material());
        assertEquals(5, obj.amount());
    }

    @Test
    void parsesWave3ObjectiveTypes() {
        Map<String, Object> play = new LinkedHashMap<>();
        play.put("id", "hours");
        play.put("type", "PLAYTIME");
        play.put("minutes", 30000);
        QuestDefinition.Objective p = QuestPackLoader.parseObjective(play);
        assertEquals(QuestDefinition.ObjectiveType.PLAYTIME, p.type());
        assertEquals(30000, p.minutes());

        Map<String, Object> bal = new LinkedHashMap<>();
        bal.put("id", "rich");
        bal.put("type", "ECONOMY_BALANCE");
        bal.put("min-balance", 10000);
        QuestDefinition.Objective b = QuestPackLoader.parseObjective(bal);
        assertEquals(QuestDefinition.ObjectiveType.ECONOMY_BALANCE, b.type());
        assertEquals(10000.0, b.minBalance());

        Map<String, Object> talk = new LinkedHashMap<>();
        talk.put("id", "hello");
        talk.put("type", "TALK");
        talk.put("npc-id", "quest_master");
        talk.put("amount", 1);
        QuestDefinition.Objective t = QuestPackLoader.parseObjective(talk);
        assertEquals(QuestDefinition.ObjectiveType.TALK, t.type());
        assertEquals("quest_master", t.npcId());

        Map<String, Object> place = new LinkedHashMap<>();
        place.put("id", "build");
        place.put("type", "PLACE_BLOCKS");
        place.put("material", "COBBLESTONE");
        place.put("amount", 100);
        QuestDefinition.Objective pl = QuestPackLoader.parseObjective(place);
        assertEquals(QuestDefinition.ObjectiveType.PLACE_BLOCKS, pl.type());
        assertEquals(100, pl.amount());
    }
}
