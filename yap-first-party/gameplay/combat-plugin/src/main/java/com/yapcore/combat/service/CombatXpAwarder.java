package com.yapcore.combat.service;

import com.yapcore.combat.CombatConfig;
import com.yapcore.mmo.CombatStyle;
import com.yapcore.mmo.SkillDefinition;
import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.SkillService;
import com.yapcore.mmo.SkillServices;
import com.yapcore.mmo.XpSource;
import org.bukkit.plugin.java.JavaPlugin;

public final class CombatXpAwarder {

    private final JavaPlugin plugin;
    private final CombatConfig config;

    public CombatXpAwarder(JavaPlugin plugin, CombatConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void awardDamageDealt(java.util.UUID playerId, int damage, CombatStyle style) {
        if (damage <= 0) {
            return;
        }
        SkillServices.find().ifPresent(skills -> {
            switch (style) {
                case RANGED -> {
                    grant(skills, playerId, SkillId.of("ranged"), dealtXp(skills, SkillId.of("ranged"), damage));
                    grant(skills, playerId, SkillId.of("hitpoints"), hitpointsXp(skills, damage, style));
                }
                case MAGIC -> grant(skills, playerId, SkillId.of("magic"), dealtXp(skills, SkillId.of("magic"), damage));
                default -> {
                    grant(skills, playerId, SkillId.of("attack"), dealtXp(skills, SkillId.of("attack"), damage));
                    grant(skills, playerId, SkillId.of("strength"), dealtXp(skills, SkillId.of("strength"), damage));
                    grant(skills, playerId, SkillId.of("hitpoints"), hitpointsXp(skills, damage, style));
                }
            }
        });
    }

    public void awardMagicCast(java.util.UUID playerId, double castXp) {
        if (castXp <= 0) {
            return;
        }
        SkillServices.find().ifPresent(skills ->
                grant(skills, playerId, SkillId.of("magic"), castXp));
    }

    public void awardMagicDamage(java.util.UUID playerId, int damage, double multiplier) {
        if (damage <= 0) {
            return;
        }
        SkillServices.find().ifPresent(skills -> {
            double amount = dealtXp(skills, SkillId.of("magic"), damage);
            if (amount <= 0) {
                amount = damage * multiplier;
            }
            grant(skills, playerId, SkillId.of("magic"), amount);
            grant(skills, playerId, SkillId.of("hitpoints"), hitpointsXp(skills, damage, CombatStyle.MAGIC));
        });
    }

    public void awardDamageTaken(java.util.UUID playerId, int damage) {
        if (damage <= 0) {
            return;
        }
        SkillServices.find().ifPresent(skills -> {
            SkillDefinition defDef = skills.definition(SkillId.of("defence")).orElse(null);
            double amount;
            if (defDef != null && defDef.combatTaken() != null) {
                amount = damage * defDef.combatTaken().xpPerDamage();
            } else {
                amount = damage * config.xpDamageMultiplier() * config.xpDefenceShare();
            }
            grant(skills, playerId, SkillId.of("defence"), amount);
            grant(skills, playerId, SkillId.of("hitpoints"), hitpointsXp(skills, damage, CombatStyle.MELEE));
        });
    }

    public void awardKill(java.util.UUID playerId, CombatStyle style) {
        SkillServices.find().ifPresent(skills -> {
            int pseudoDamage = (int) Math.max(1, config.xpKillBase());
            switch (style) {
                case RANGED -> {
                    grant(skills, playerId, SkillId.of("ranged"), dealtXp(skills, SkillId.of("ranged"), pseudoDamage));
                    grant(skills, playerId, SkillId.of("hitpoints"), hitpointsXp(skills, pseudoDamage, style));
                }
                case MAGIC -> grant(skills, playerId, SkillId.of("magic"), dealtXp(skills, SkillId.of("magic"), pseudoDamage));
                default -> {
                    grant(skills, playerId, SkillId.of("attack"), dealtXp(skills, SkillId.of("attack"), pseudoDamage));
                    grant(skills, playerId, SkillId.of("strength"), dealtXp(skills, SkillId.of("strength"), pseudoDamage));
                    grant(skills, playerId, SkillId.of("defence"), dealtXp(skills, SkillId.of("defence"), pseudoDamage));
                    grant(skills, playerId, SkillId.of("hitpoints"), hitpointsXp(skills, pseudoDamage, style));
                }
            }
        });
    }

    private double dealtXp(SkillService skills, SkillId skillId, int damage) {
        SkillDefinition def = skills.definition(skillId).orElse(null);
        if (def != null) {
            SkillDefinition.CombatDealtAction action = switch (skillId.id()) {
                case "ranged" -> def.rangedDealt();
                case "magic" -> def.magicDealt();
                default -> def.combatDealt();
            };
            if (action != null) {
                return damage * action.xpPerDamage() * action.share();
            }
        }
        return damage * config.xpDamageMultiplier() * configShare(skillId);
    }

    private double configShare(SkillId skillId) {
        return switch (skillId.id()) {
            case "ranged" -> config.xpRangedShare();
            case "magic" -> config.xpMagicShare();
            case "attack" -> config.xpAttackShare();
            case "strength" -> config.xpStrengthShare();
            case "defence" -> config.xpDefenceShare();
            default -> 1.0;
        };
    }

    private double hitpointsXp(SkillService skills, int damage, CombatStyle style) {
        double combatXp = switch (style) {
            case RANGED -> dealtXp(skills, SkillId.of("ranged"), damage);
            case MAGIC -> dealtXp(skills, SkillId.of("magic"), damage);
            default -> dealtXp(skills, SkillId.of("attack"), damage)
                    + dealtXp(skills, SkillId.of("strength"), damage);
        };
        SkillDefinition hpDef = skills.definition(SkillId.of("hitpoints")).orElse(null);
        if (hpDef != null && hpDef.hitpointsRatio() != null) {
            return combatXp * hpDef.hitpointsRatio().ratio();
        }
        return damage * config.xpDamageMultiplier() * config.xpHitpointsShare();
    }

    private void grant(SkillService skills, java.util.UUID playerId, SkillId skill, double amount) {
        if (amount <= 0) {
            return;
        }
        skills.addXp(playerId, skill, amount, XpSource.ACTION)
                .exceptionally(ex -> {
                    plugin.getLogger().fine("combat xp " + skill + ": " + ex.getMessage());
                    return null;
                });
    }
}
