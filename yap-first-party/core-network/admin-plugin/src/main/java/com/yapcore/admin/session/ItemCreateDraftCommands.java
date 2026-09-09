package com.yapcore.admin.session;

import com.yapcore.admin.gui.AbilityCatalog;

/** Command-building and ability-param usage helpers for {@link ItemCreateDraft}. */
final class ItemCreateDraftCommands {

    private ItemCreateDraftCommands() {
    }

    static String buildCreateCommand(ItemCreateDraft d) {
        StringBuilder sb = new StringBuilder();
        sb.append("yapitems create ").append(d.id())
                .append(" --template ").append(d.template())
                .append(" --name ").append(d.displayName())
                .append(" --cooldown ").append(d.cooldown());
        for (ItemCreateAbilitySlot slot : d.abilitySlots()) {
            sb.append(" --ability ").append(slot.type())
                    .append(" --trigger ").append(slot.trigger());
        }
        if (usesDamage(d)) {
            sb.append(" --damage ").append(trimNum(d.damage()));
        }
        if (usesRange(d)) {
            sb.append(" --range ").append(trimNum(d.range()));
        }
        if (usesRadius(d) && !usesBreakVolume(d)) {
            sb.append(" --radius ").append(trimNum(d.radius()));
        }
        if (usesBreakVolume(d)) {
            sb.append(" --radius ").append(d.breakRadius());
            sb.append(" --amount ").append(d.breakCount());
        }
        if (usesPotion(d)) {
            sb.append(" --effect ").append(d.potionEffectsCompact());
        }
        if (usesPotionPower(d)) {
            if (d.potionDurationSec() < 0) {
                sb.append(" --duration -1");
            } else {
                sb.append(" --duration ").append(d.potionDurationSec() * 20);
            }
            sb.append(" --amplifier ").append(d.potionAmplifier());
        }
        if (usesHeal(d) && !usesBreakVolume(d)) {
            sb.append(" --amount ").append(trimNum(d.healAmount()));
        }
        if (usesProjectile(d)) {
            sb.append(" --projectile ").append(d.projectileKind());
        }
        ItemCreateDraftFx fx = d.fx();
        if (!fx.enabled) {
            sb.append(" --no-fx");
        }
        if (fx.sound != null && !fx.sound.isBlank()) {
            sb.append(" --sound ").append(fx.sound);
        }
        if (fx.particle != null && !fx.particle.isBlank()) {
            sb.append(" --particle ").append(fx.particle);
        }
        if (fx.count >= 0) {
            sb.append(" --count ").append(fx.count);
        }
        if (d.gearAttack() > 0) {
            sb.append(" --gear-attack ").append(d.gearAttack());
        }
        if (d.gearStrength() > 0) {
            sb.append(" --gear-strength ").append(d.gearStrength());
        }
        if (d.glow()) {
            sb.append(" --glow");
        } else if (d.replaceExisting()) {
            sb.append(" --no-glow");
        }
        if (d.rainbow()) {
            sb.append(" --rainbow");
        } else if (d.replaceExisting()) {
            sb.append(" --no-rainbow");
        }
        if (d.unbreakable()) {
            sb.append(" --unbreakable");
        } else if (d.replaceExisting()) {
            sb.append(" --no-unbreakable");
        }
        if (!d.enchants().isEmpty()) {
            sb.append(" --enchants ").append(d.enchantsCompact());
        } else if (d.replaceExisting()) {
            sb.append(" --no-enchants");
        }
        if (d.furniture()) {
            sb.append(" --furniture");
        }
        if (d.replaceExisting()) {
            sb.append(" --replace");
        }
        return sb.toString();
    }

    static boolean usesDamage(ItemCreateDraft d) {
        return AbilityCatalog.anyNeeds(d.abilities(), AbilityCatalog.Info::needsDamage);
    }

    static boolean usesRange(ItemCreateDraft d) {
        return AbilityCatalog.anyNeeds(d.abilities(), AbilityCatalog.Info::needsRange);
    }

    static boolean usesRadius(ItemCreateDraft d) {
        return AbilityCatalog.anyNeeds(d.abilities(), AbilityCatalog.Info::needsRadius);
    }

    static boolean usesPotion(ItemCreateDraft d) {
        return AbilityCatalog.anyNeeds(d.abilities(), AbilityCatalog.Info::needsPotion);
    }

    static boolean usesPotionPower(ItemCreateDraft d) {
        return AbilityCatalog.anyNeeds(d.abilities(), AbilityCatalog.Info::needsPotionPower);
    }

    static boolean usesHeal(ItemCreateDraft d) {
        return AbilityCatalog.anyNeeds(d.abilities(), AbilityCatalog.Info::needsHeal);
    }

    static boolean usesProjectile(ItemCreateDraft d) {
        return AbilityCatalog.anyNeeds(d.abilities(), AbilityCatalog.Info::needsProjectile);
    }

    static boolean usesBreakVolume(ItemCreateDraft d) {
        return AbilityCatalog.anyNeeds(d.abilities(), AbilityCatalog.Info::needsBreakVolume);
    }

    static String trimNum(double v) {
        if (Math.rint(v) == v) {
            return Integer.toString((int) v);
        }
        return Double.toString(v);
    }
}
