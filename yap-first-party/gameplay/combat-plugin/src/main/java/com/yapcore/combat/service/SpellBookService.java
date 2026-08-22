package com.yapcore.combat.service;

import com.yapcore.abilities.AbilityServices;
import com.yapcore.abilities.CastResult;
import com.yapcore.combat.formula.CombatHitResolver;
import com.yapcore.combat.formula.CombatPhysics;
import com.yapcore.combat.formula.DamageCalculator;
import com.yapcore.combat.integration.CombatPvpGate;
import com.yapcore.combat.prayer.PrayerModifiers;
import com.yapcore.combat.spell.SpellBookLoader;
import com.yapcore.combat.spell.SpellDefinition;
import com.yapcore.mmo.CombatStats;
import com.yapcore.mmo.CombatStyle;
import com.yapcore.mmo.GearBonus;
import com.yapcore.combat.status.StatusEffectService;
import com.yapcore.sched.YapSched;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SpellBookService {

    private static final Set<EntityType> UNDEAD_TYPES = Set.of(
            EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED,
            EntityType.SKELETON, EntityType.STRAY, EntityType.WITHER_SKELETON,
            EntityType.PHANTOM, EntityType.WITHER, EntityType.ZOMBIFIED_PIGLIN,
            EntityType.ZOGLIN, EntityType.SKELETON_HORSE, EntityType.ZOMBIE_HORSE,
            EntityType.ZOMBIE_VILLAGER, EntityType.GIANT);

    private final JavaPlugin plugin;
    private final CombatServiceImpl combat;
    private final CombatXpAwarder xp;
    private final SpellBookLoader spells;
    private final StatusEffectService status;

    public SpellBookService(
            JavaPlugin plugin,
            CombatServiceImpl combat,
            CombatXpAwarder xp,
            SpellBookLoader spells,
            StatusEffectService status) {
        this.plugin = plugin;
        this.combat = combat;
        this.xp = xp;
        this.spells = spells;
        this.status = status;
    }

    public List<SpellDefinition> knownSpells(Player player) {
        int magic = combat.stats(player).magic();
        List<SpellDefinition> out = new ArrayList<>();
        for (SpellDefinition spell : spells.spells().values()) {
            if (spell.minMagicLevel() <= magic) {
                out.add(spell);
            }
        }
        out.sort(Comparator.comparingInt(SpellDefinition::minMagicLevel));
        return out;
    }

    public boolean cast(Player player, String spellId) {
        var abilityResult = AbilityServices.find()
                .map(s -> s.cast(player, spellId))
                .orElse(null);
        if (abilityResult != null) {
            if (abilityResult == CastResult.UNKNOWN_ABILITY) {
                // fall through to legacy spell book
            } else {
                return abilityResult.ok();
            }
        }
        SpellDefinition spell = spells.get(spellId.toLowerCase(Locale.ROOT));
        if (spell == null) {
            player.sendMessage("§cUnknown spell.");
            return false;
        }
        CombatStats stats = combat.stats(player);
        if (stats.magic() < spell.minMagicLevel()) {
            player.sendMessage("§cRequires Magic level §e" + spell.minMagicLevel() + "§c.");
            return false;
        }
        if (stats.currentPrayer() < spell.prayerCost()) {
            player.sendMessage("§cNot enough prayer points (need §e" + spell.prayerCost() + "§c).");
            return false;
        }
        if (spell.requiresStaff() && !hasStaff(player, spell.requiredStaff())) {
            player.sendMessage("§cRequires §e" + spell.requiredStaff().name() + "§c in hand.");
            return false;
        }
        if (!hasRunes(player, spell.runes())) {
            player.sendMessage("§cMissing spell runes.");
            return false;
        }
        LivingEntity target = findTarget(player, 20);
        if (target == null) {
            player.sendMessage("§cNo target in range.");
            return false;
        }
        if (!matchesTargetFilter(target, spell.targetFilter())) {
            player.sendMessage("§cThis spell cannot target that creature.");
            return false;
        }
        if (target instanceof Player victim) {
            if (!CombatPvpGate.isPlayerVsPlayerAllowed(combat.config(), player, victim)) {
                player.sendMessage("§cPvP is not allowed here.");
                return false;
            }
        }

        consumeRunes(player, spell.runes());
        combat.drainPrayer(player, spell.prayerCost());
        xp.awardMagicCast(player.getUniqueId(), spell.castXp());

        PrayerModifiers prayers = combat.prayerModifiers(player);
        GearBonus gear = stats.gear();
        var attacker = new DamageCalculator.MagicAttacker(
                stats.magic(), gear.magicBonus(), prayers.magicBoost());
        var defender = target instanceof Player tp
                ? toDefender(combat.stats(tp), combat.prayerModifiers(tp))
                : mobDefender(target);
        DamageCalculator.Params params = combat.formulaParams();
        YapSched.entity(plugin, player, () -> CombatHitResolver.resolveAsync(
                plugin,
                random -> DamageCalculator.rollMagic(attacker, defender, spell.baseMaxHit(), params, random),
                result -> YapSched.entity(plugin, player, () -> {
                    if (!result.hit()) {
                        player.sendActionBar(net.kyori.adventure.text.Component.text("§7Your spell misses."));
                        return;
                    }
                    if (target instanceof Player tp) {
                        YapSched.entity(plugin, tp, () -> {
                            boolean dead = combat.applyDamage(tp, result.damage(), CombatStyle.MAGIC);
                            CombatPhysics.applyKnockback(tp, player, result, combat.config().physics());
                            applySpellEffect(player, tp, spell);
                            xp.awardMagicDamage(player.getUniqueId(), result.damage(), spell.damageXpMultiplier());
                            if (dead) {
                                tp.setHealth(0);
                            }
                        });
                    } else {
                        YapSched.entity(plugin, target, () -> {
                            double next = Math.max(0, target.getHealth() - result.damage());
                            target.setHealth(next);
                            CombatPhysics.applyKnockback(target, player, result, combat.config().physics());
                            applySpellEffect(player, target, spell);
                        });
                        xp.awardMagicDamage(player.getUniqueId(), result.damage(), spell.damageXpMultiplier());
                    }
                    String crit = result.critical() ? " §c§lCRIT" : "";
                    player.sendActionBar(net.kyori.adventure.text.Component.text(
                            "§d" + spell.displayName() + crit + " §7hit for §c" + result.damage()));
                })));
        return true;
    }

    private void applySpellEffect(Player caster, LivingEntity target, SpellDefinition spell) {
        if (spell.appliesEffect() == null || spell.appliesEffect().isBlank()) {
            return;
        }
        status.apply(target, spell.appliesEffect(), caster, spell.effectStacks());
    }

    private LivingEntity findTarget(Player player, double range) {
        RayTraceResult trace = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                range,
                entity -> entity instanceof LivingEntity && !entity.equals(player));
        if (trace == null || !(trace.getHitEntity() instanceof LivingEntity living)) {
            return null;
        }
        return living;
    }

    private static boolean hasStaff(Player player, Material staff) {
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        return main.getType() == staff || off.getType() == staff;
    }

    private static boolean hasRunes(Player player, Map<Material, Integer> runes) {
        if (runes.isEmpty()) {
            return true;
        }
        for (Map.Entry<Material, Integer> entry : runes.entrySet()) {
            if (countMaterial(player.getInventory(), entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private static void consumeRunes(Player player, Map<Material, Integer> runes) {
        if (runes.isEmpty()) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        for (Map.Entry<Material, Integer> entry : runes.entrySet()) {
            removeMaterial(inv, entry.getKey(), entry.getValue());
        }
    }

    private static int countMaterial(PlayerInventory inv, Material material) {
        int total = 0;
        for (ItemStack stack : inv.getContents()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private static void removeMaterial(PlayerInventory inv, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            if (stack.getAmount() <= 0) {
                contents[i] = null;
            }
            remaining -= take;
        }
        inv.setContents(contents);
    }

    private static boolean matchesTargetFilter(LivingEntity target, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        if ("undead".equalsIgnoreCase(filter.trim())) {
            return UNDEAD_TYPES.contains(target.getType());
        }
        return true;
    }

    private static DamageCalculator.Defender toDefender(CombatStats stats, PrayerModifiers prayers) {
        GearBonus gear = stats.gear();
        return new DamageCalculator.Defender(
                stats.defence(),
                gear.defenceBonus(),
                stats.buffs().defenceBoost() + prayers.defenceBoost());
    }

    private static DamageCalculator.Defender mobDefender(LivingEntity mob) {
        int defence = Math.max(1, (int) (mob.getMaxHealth() / 8));
        return new DamageCalculator.Defender(defence, 0, 0);
    }
}
