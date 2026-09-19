package com.yapcore.skills.power;

import com.yapcore.mmo.SkillDefinition;
import com.yapcore.mmo.SkillId;
import org.bukkit.Material;

import java.util.Collection;

/** Which live skill owns a block or a hit. Mining never speeds wood, and wood never speeds ore. */
public final class SkillPowerBlocks {

    public static final SkillId MINING = SkillId.of("mining");
    public static final SkillId WOODCUTTING = SkillId.of("woodcutting");
    public static final SkillId STRENGTH = SkillId.of("strength");
    public static final SkillId MARATHON = SkillId.of("marathon");
    public static final SkillId BUILDER = SkillId.of("builder");

    private SkillPowerBlocks() {
    }

    public static SkillId breakSkill(Collection<SkillDefinition> definitions, Material block) {
        if (definitions == null || block == null) {
            return null;
        }
        if (owns(definitions, MINING, block)) {
            return MINING;
        }
        if (owns(definitions, WOODCUTTING, block)) {
            return WOODCUTTING;
        }
        return null;
    }

    private static boolean owns(Collection<SkillDefinition> definitions, SkillId id, Material block) {
        for (SkillDefinition def : definitions) {
            if (def == null || !def.enabled() || !id.equals(def.id())) {
                continue;
            }
            return def.breakActions() != null && def.breakActions().containsKey(block);
        }
        return false;
    }
}
