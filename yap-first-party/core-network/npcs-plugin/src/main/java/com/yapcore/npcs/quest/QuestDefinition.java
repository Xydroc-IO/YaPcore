package com.yapcore.npcs.quest;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.List;

public record QuestDefinition(
        String id,
        String name,
        String description,
        String requiresQuest,
        List<Objective> objectives,
        List<String> rewards
) {
    public enum ObjectiveType {
        BREAK_BLOCK,
        GATHER,
        KILL_MOB,
        SKILL_LEVEL,
        CRAFT_ITEM,
        KILL_BOSS
    }

    public record Objective(
            String id,
            ObjectiveType type,
            Material material,
            EntityType entityType,
            int amount,
            String skillId,
            int minLevel,
            String recipeId,
            String bossId
    ) {
        /** Legacy constructor for break/kill objectives. */
        public Objective(String id, ObjectiveType type, Material material, EntityType entityType, int amount) {
            this(id, type, material, entityType, amount, "", 0, "", "");
        }
    }
}
