package com.yapcore.abilities.book;

import com.yapcore.abilities.AbilityCosts;
import com.yapcore.abilities.AbilityDefinition;
import com.yapcore.abilities.AbilityEffect;
import com.yapcore.abilities.EffectKind;
import com.yapcore.abilities.TargetMode;
import com.yapcore.mmo.SkillProgress;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Player-facing “what this spell does” text from YAML description or effects. */
public final class AbilityDescribe {

    private AbilityDescribe() {
    }

    public static String blurb(AbilityDefinition ability) {
        if (ability.description() != null && !ability.description().isBlank()) {
            return ability.description();
        }
        return autoBlurb(ability);
    }

    public static List<String> lorePlain(AbilityDefinition ability) {
        List<String> lines = new ArrayList<>();
        for (String part : wrap(blurb(ability), 40)) {
            lines.add("§f" + part);
        }
        String costs = costLine(ability.costs());
        if (!costs.isEmpty()) {
            lines.add("§7" + costs);
        }
        String how = howLine(ability);
        if (!how.isEmpty()) {
            lines.add("§7" + how);
        }
        return lines;
    }

    public static List<Component> lore(AbilityDefinition ability) {
        List<Component> out = new ArrayList<>();
        for (String line : lorePlain(ability)) {
            out.add(Component.text(line).decoration(TextDecoration.ITALIC, false));
        }
        return out;
    }

    public static void send(Player player, AbilityDefinition ability, Collection<SkillProgress> skills) {
        player.sendMessage("§d§l" + ability.displayName() + " §8(" + ability.id() + ")");
        for (String part : wrap(blurb(ability), 52)) {
            player.sendMessage("§f" + part);
        }
        String how = howLine(ability);
        if (!how.isEmpty()) {
            player.sendMessage("§7" + how);
        }
        String costs = costLine(ability.costs());
        if (!costs.isEmpty()) {
            player.sendMessage("§7" + costs);
        }
        player.sendMessage("§7" + AbilityUnlocks.requirementsText(ability, skills));
        player.sendMessage("§8Right-click in §e/abilities §8to read · click to add to keys §e4–9");
    }

    public static String inspectBody(AbilityDefinition ability, Collection<SkillProgress> skills) {
        StringBuilder sb = new StringBuilder();
        sb.append(blurb(ability)).append('\n');
        String how = howLine(ability);
        if (!how.isEmpty()) {
            sb.append(how).append('\n');
        }
        String costs = costLine(ability.costs());
        if (!costs.isEmpty()) {
            sb.append(costs).append('\n');
        }
        sb.append(plain(AbilityUnlocks.requirementsText(ability, skills)));
        return sb.toString();
    }

    static String autoBlurb(AbilityDefinition ability) {
        List<AbilityEffect> combat = new ArrayList<>();
        combat.addAll(ability.hitEffects());
        combat.addAll(ability.castEffects());

        int damage = 0;
        String style = "";
        boolean knockback = false;
        int heal = 0;
        String buff = "";
        String debuff = "";
        boolean teleport = false;
        boolean velocity = false;
        int chain = 0;
        double aoe = 0;
        int drain = 0;

        for (AbilityEffect effect : combat) {
            switch (effect.kind()) {
                case DAMAGE -> {
                    int hit = Math.max(effect.intParam("max-hit", 0), effect.intParam("amount", 0));
                    if (hit > damage) {
                        damage = hit;
                        style = effect.param("style", "");
                    }
                }
                case HEAL -> heal = Math.max(heal, effect.intParam("amount", effect.intParam("max-hit", 0)));
                case KNOCKBACK -> knockback = true;
                case BUFF -> {
                    if (buff.isEmpty()) {
                        buff = prettyId(effect.param("id", effect.param("effect", "a buff")));
                    }
                }
                case DEBUFF -> {
                    if (debuff.isEmpty()) {
                        debuff = prettyId(effect.param("id", effect.param("effect", "a curse")));
                    }
                }
                case TELEPORT -> teleport = true;
                case VELOCITY -> velocity = true;
                case CHAIN -> chain = Math.max(chain, effect.intParam("jumps", 0));
                case AOE -> aoe = Math.max(aoe, effect.doubleParam("radius", 0));
                case DRAIN_PRAYER -> drain = Math.max(drain, effect.intParam("amount", 0));
                default -> {
                }
            }
        }
        if (ability.hasProjectile() && ability.projectile().hasSplash()) {
            aoe = Math.max(aoe, ability.projectile().splashRadius());
        }

        List<String> bits = new ArrayList<>();
        if (damage > 0) {
            String hit = "up to " + damage + (style.isBlank() ? "" : " " + style) + " damage";
            if (ability.hasProjectile()) {
                String bolt = ability.projectile().isHoming() ? "a homing bolt" : "a projectile";
                bits.add("Fires " + bolt + " that hits for " + hit);
            } else if (ability.targetMode() == TargetMode.SELF) {
                bits.add("Damages nearby foes for " + hit);
            } else {
                bits.add("Hits the target for " + hit);
            }
        }
        if (knockback) {
            bits.add(bits.isEmpty() ? "Knocks the target back" : "knocks them back");
        }
        if (heal > 0) {
            bits.add("Heals you for " + heal);
        }
        if (!buff.isEmpty()) {
            bits.add("Grants " + buff);
        }
        if (!debuff.isEmpty()) {
            bits.add("Applies " + debuff);
        }
        if (chain > 0) {
            bits.add("Chains to " + chain + " more targets");
        }
        if (aoe > 0) {
            bits.add("Hits a " + trimNum(aoe) + "-block area");
        }
        if (teleport) {
            bits.add("Teleports you");
        }
        if (velocity) {
            bits.add("Launches you");
        }
        if (drain > 0) {
            bits.add("Drains " + drain + " prayer");
        }
        if (bits.isEmpty()) {
            return switch (ability.category()) {
                case MAGIC -> "A magic spell.";
                case RANGED -> "A ranged special attack.";
                case MELEE -> "A melee special attack.";
                case PRAYER -> "A prayer power.";
                case UTILITY -> "A utility ability.";
            };
        }
        return joinBits(bits) + ".";
    }

    static String howLine(AbilityDefinition ability) {
        List<String> parts = new ArrayList<>();
        parts.add(switch (ability.targetMode()) {
            case SELF -> "Cast on yourself";
            case AREA -> "Hits an area around you";
            case GROUND -> "Aim at the ground";
            case NONE -> "Instant cast";
            case RAYCAST -> "Aim at a target";
        });
        if (ability.targetMode() != TargetMode.SELF && ability.targetMode() != TargetMode.NONE) {
            parts.add("Range " + trimNum(ability.range()));
        }
        if (ability.cooldownTicks() > 0) {
            parts.add("Cooldown " + formatCooldown(ability.cooldownTicks()));
        }
        if (ability.hasTargetFilter() && !ability.targetFilter().isBlank()) {
            parts.add("vs " + prettyId(ability.targetFilter()));
        }
        return String.join(" · ", parts);
    }

    static String costLine(AbilityCosts costs) {
        if (costs == null) {
            return "";
        }
        var plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("YaPAbilities");
        boolean showPrayer = plugin == null || plugin.getConfig().getBoolean("costs.require-prayer", false);
        boolean showRunes = plugin == null || plugin.getConfig().getBoolean("costs.require-runes", false);
        boolean showStaff = plugin == null || plugin.getConfig().getBoolean("costs.require-staff", false);
        List<String> parts = new ArrayList<>();
        if (showPrayer && costs.prayer() > 0) {
            parts.add(costs.prayer() + " prayer");
        }
        if (showRunes) {
            for (Map.Entry<Material, Integer> rune : costs.runes().entrySet()) {
                parts.add(rune.getValue() + " " + prettyMaterial(rune.getKey()));
            }
        }
        if (showStaff && costs.requiredStaff() != null) {
            parts.add("hold " + prettyMaterial(costs.requiredStaff()));
        }
        return parts.isEmpty() ? "" : "Costs " + String.join(" · ", parts);
    }

    static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        String[] words = text.trim().split("\\s+");
        StringBuilder row = new StringBuilder();
        for (String word : words) {
            if (!row.isEmpty() && row.length() + 1 + word.length() > width) {
                lines.add(row.toString());
                row.setLength(0);
            }
            if (!row.isEmpty()) {
                row.append(' ');
            }
            row.append(word);
        }
        if (!row.isEmpty()) {
            lines.add(row.toString());
        }
        return lines;
    }

    private static String joinBits(List<String> bits) {
        if (bits.size() == 1) {
            return capitalize(bits.getFirst());
        }
        StringBuilder sb = new StringBuilder(capitalize(bits.getFirst()));
        for (int i = 1; i < bits.size(); i++) {
            boolean last = i == bits.size() - 1;
            String bit = bits.get(i);
            sb.append(last ? " and " : ", ");
            sb.append(Character.isLowerCase(bit.charAt(0)) ? bit : uncapitalize(bit));
        }
        return sb.toString();
    }

    private static String prettyId(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String[] words = raw.replace('_', ' ').replace('-', ' ').toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    private static String prettyMaterial(Material material) {
        return prettyId(material.name());
    }

    private static String capitalize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private static String uncapitalize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        return Character.toLowerCase(raw.charAt(0)) + raw.substring(1);
    }

    private static String trimNum(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((int) value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String formatCooldown(int ticks) {
        double sec = ticks / 20.0;
        return sec >= 10 ? String.format(Locale.ROOT, "%.0fs", sec) : String.format(Locale.ROOT, "%.1fs", sec);
    }

    private static String plain(String colored) {
        if (colored == null) {
            return "";
        }
        return colored.replaceAll("§[0-9a-fk-or]", "");
    }
}
