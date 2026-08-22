package com.yapcore.npcs.quest;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.List;

public record QuestDefinition(
        String id,
        String name,
        String description,
        List<Objective> objectives,
        List<String> rewards
) {
    public enum ObjectiveType {
        BREAK_BLOCK,
        KILL_MOB
    }

    public record Objective(
            String id,
            ObjectiveType type,
            Material material,
            EntityType entityType,
            int amount
    ) {
    }
}
