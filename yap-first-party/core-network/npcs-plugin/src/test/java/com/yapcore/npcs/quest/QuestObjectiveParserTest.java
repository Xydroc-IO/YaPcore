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
}
