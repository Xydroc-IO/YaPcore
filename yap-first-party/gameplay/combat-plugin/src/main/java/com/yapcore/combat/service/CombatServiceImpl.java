package com.yapcore.combat.service;

import com.yapcore.combat.CombatConfig;
import com.yapcore.combat.db.CombatRepository;
import com.yapcore.combat.formula.DamageCalculator;
import com.yapcore.combat.formula.PrayerPoints;
import com.yapcore.combat.gear.GearBonusLoader;
import com.yapcore.combat.model.PlayerCombatState;
import com.yapcore.combat.prayer.PrayerBookLoader;
import com.yapcore.combat.prayer.PrayerEffectResolver;
import com.yapcore.combat.prayer.PrayerModifiers;
import com.yapcore.mmo.CombatBuffs;
import com.yapcore.mmo.CombatService;
import com.yapcore.mmo.CombatStats;
import com.yapcore.mmo.CombatStyle;
import com.yapcore.mmo.GearBonus;
import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.SkillService;
import com.yapcore.mmo.SkillServices;
import com.yapcore.sched.YapSched;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class CombatServiceImpl implements CombatService {

    private static final SkillId ATTACK = SkillId.of("attack");
    private static final SkillId STRENGTH = SkillId.of("strength");
    private static final SkillId DEFENCE = SkillId.of("defence");
    private static final SkillId HITPOINTS = SkillId.of("hitpoints");
    private static final SkillId PRAYER = SkillId.of("prayer");
    private static final SkillId RANGED = SkillId.of("ranged");
    private static final SkillId MAGIC = SkillId.of("magic");

    private final JavaPlugin plugin;
    private final CombatConfig config;
    private final CombatRepository repository;
    private final GearBonusLoader gearLoader;
    private final PrayerBookLoader prayerLoader;
    private final java.util.Map<UUID, SkillSnapshot> skillSnapshots = new ConcurrentHashMap<>();

    private final java.util.Map<UUID, PlayerCombatState> cache = new ConcurrentHashMap<>();

    public CombatServiceImpl(
            JavaPlugin plugin,
            CombatConfig config,
            CombatRepository repository,
            GearBonusLoader gearLoader,
            PrayerBookLoader prayerLoader) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.gearLoader = gearLoader;
        this.prayerLoader = prayerLoader;
    }

    public CombatConfig config() {
        return config;
    }

    public DamageCalculator.Params formulaParams() {
        return new DamageCalculator.Params(
                config.levelFactor(),
                config.minDamageOnHit(),
                config.critChance(),
                config.critMultiplier());
    }

    public PlayerCombatState state(Player player) {
        return cache.computeIfAbsent(player.getUniqueId(), id -> loadOrCreate(player));
    }

    public void unload(UUID playerId) {
        cache.remove(playerId);
        skillSnapshots.remove(playerId);
    }

    public void warmSkillCache(UUID playerId) {
        YapSched.async(plugin, () -> {
            Optional<SkillService> skills = SkillServices.find();
            if (skills.isEmpty()) {
                return;
            }
            skillSnapshots.put(playerId, loadSkillSnapshot(skills.get(), playerId));
        });
    }

    public void invalidateSkillCache(UUID playerId) {
        skillSnapshots.remove(playerId);
        warmSkillCache(playerId);
    }

    public void persistAsync(PlayerCombatState state) {
        YapSched.async(plugin, () -> {
            try {
                repository.upsert(state);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "combat persist", e);
            }
        });
    }

    @Override
    public CombatStats stats(Player player) {
        int attack = skillLevel(player.getUniqueId(), ATTACK);
        int strength = skillLevel(player.getUniqueId(), STRENGTH);
        int defence = skillLevel(player.getUniqueId(), DEFENCE);
        int hitpoints = skillLevel(player.getUniqueId(), HITPOINTS);
        int prayer = skillLevel(player.getUniqueId(), PRAYER);
        int ranged = skillLevel(player.getUniqueId(), RANGED);
        int magic = skillLevel(player.getUniqueId(), MAGIC);
        GearBonus gear = gearLoader.aggregateEquipped(player);
        CombatBuffs buffs = activeBuffs(state(player));
        PlayerCombatState st = state(player);
        int maxHp = maxHp(hitpoints);
        int maxPrayer = PrayerPoints.maxPoints(prayer);
        int currentHp = Math.min(st.currentHp(), maxHp);
        int currentPrayer = PrayerPoints.clamp(st.currentPrayer(), maxPrayer);
        return new CombatStats(
                attack,
                strength,
                defence,
                hitpoints,
                prayer,
                ranged,
                magic,
                gear,
                buffs,
                currentHp,
                maxHp,
                currentPrayer,
                maxPrayer);
    }

    @Override
    public void recalculate(Player player) {
        int hitpoints = skillLevel(player.getUniqueId(), HITPOINTS);
        int prayer = skillLevel(player.getUniqueId(), PRAYER);
        int maxHp = maxHp(hitpoints);
        int maxPrayer = PrayerPoints.maxPoints(prayer);
        PlayerCombatState st = state(player);
        if (st.currentHp() > maxHp) {
            st.setCurrentHp(maxHp);
        }
        if (st.currentPrayer() > maxPrayer) {
            st.setCurrentPrayer(maxPrayer);
        }
        persistAsync(st);
        syncVanillaHealth(player, st.currentHp(), maxHp);
    }

    @Override
    public CompletableFuture<Integer> getHp(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerCombatState st = cache.get(playerId);
            if (st != null) {
                return st.currentHp();
            }
            try {
                return repository.get(playerId).map(PlayerCombatState::currentHp).orElse(config.baseHp());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> setHp(UUID playerId, int hp) {
        return CompletableFuture.runAsync(() -> {
            Player player = plugin.getServer().getPlayer(playerId);
            int hitpoints = skillLevel(playerId, HITPOINTS);
            int maxHp = maxHp(hitpoints);
            int clamped = Math.max(0, Math.min(maxHp, hp));
            PlayerCombatState st = player == null
                    ? loadOrCreateOffline(playerId, maxHp, PrayerPoints.maxPoints(skillLevel(playerId, PRAYER)))
                    : state(player);
            st.setCurrentHp(clamped);
            persistAsync(st);
            if (player != null && player.isOnline()) {
                YapSched.entity(plugin, player, () -> syncVanillaHealth(player, clamped, maxHp));
            }
        });
    }

    @Override
    public Optional<GearBonus> gearBonusFor(org.bukkit.inventory.ItemStack stack) {
        return gearLoader.bonusFor(stack);
    }

    public void drainPrayer(Player player, int amount) {
        if (amount <= 0) {
            return;
        }
        PlayerCombatState st = state(player);
        int maxPrayer = PrayerPoints.maxPoints(skillLevel(player.getUniqueId(), PRAYER));
        st.setCurrentPrayer(PrayerPoints.clamp(st.currentPrayer() - amount, maxPrayer));
        persistAsync(st);
    }

    public PrayerModifiers prayerModifiers(Player player) {
        return PrayerEffectResolver.resolve(
                state(player).activePrayers(),
                prayerLoader.prayers());
    }

    public java.util.Set<String> activePrayers(Player player) {
        return state(player).activePrayers();
    }

    public void restorePrayerPoints(Player player, int amount) {
        if (amount <= 0) {
            return;
        }
        PlayerCombatState st = state(player);
        int maxPrayer = PrayerPoints.maxPoints(skillLevel(player.getUniqueId(), PRAYER));
        st.setCurrentPrayer(PrayerPoints.clamp(st.currentPrayer() + amount, maxPrayer));
        persistAsync(st);
    }

    public int maxHp(int hitpointsLevel) {
        return config.baseHp() + hitpointsLevel * config.hpPerHitpointsLevel();
    }

    public void syncVanillaHealth(Player player, int currentHp, int maxHp) {
        if (!config.customHpEnabled()) {
            return;
        }
        double ratio = maxHp <= 0 ? 0 : (double) currentHp / maxHp;
        double display = Math.max(0.5, Math.min(20.0, ratio * 20.0));
        var maxAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxAttr != null) {
            maxAttr.setBaseValue(20.0);
        }
        player.setHealth(display);
    }

    public CombatBuffs activeBuffs(PlayerCombatState state) {
        long now = System.currentTimeMillis();
        int attack = state.buffAttackUntil() > now ? potionBoost("attack") : 0;
        int strength = state.buffStrengthUntil() > now ? potionBoost("strength") : 0;
        int defence = state.buffDefenceUntil() > now ? potionBoost("defence") : 0;
        return new CombatBuffs(attack, strength, defence);
    }

    public int potionBoost(String id) {
        CombatConfig.PotionDef def = config.potions().get(id);
        return def == null ? 0 : def.boost();
    }

    public boolean applyDamage(Player player, int damage) {
        return applyDamage(player, damage, CombatStyle.MELEE);
    }

    public boolean applyDamage(Player player, int damage, CombatStyle style) {
        if (damage <= 0 || player.isInvulnerable()) {
            return false;
        }
        PrayerModifiers prayers = prayerModifiers(player);
        double reduction = switch (style) {
            case RANGED -> prayers.protectMissiles();
            case MAGIC -> prayers.protectMagic();
            default -> prayers.protectMelee();
        };
        if (reduction > 0) {
            damage = (int) Math.floor(damage * (1.0 - Math.min(1.0, reduction)));
        }
        if (damage <= 0) {
            return false;
        }
        PlayerCombatState st = state(player);
        int maxHp = maxHp(skillLevel(player.getUniqueId(), HITPOINTS));
        int next = Math.max(0, st.currentHp() - damage);
        st.setCurrentHp(next);
        syncVanillaHealth(player, next, maxHp);
        persistAsync(st);
        return next <= 0;
    }

    public void heal(Player player, int amount) {
        if (amount <= 0) {
            return;
        }
        PlayerCombatState st = state(player);
        int maxHp = maxHp(skillLevel(player.getUniqueId(), HITPOINTS));
        int next = Math.min(maxHp, st.currentHp() + amount);
        st.setCurrentHp(next);
        syncVanillaHealth(player, next, maxHp);
        persistAsync(st);
    }

    public void restoreFull(Player player) {
        int maxHp = maxHp(skillLevel(player.getUniqueId(), HITPOINTS));
        int maxPrayer = PrayerPoints.maxPoints(skillLevel(player.getUniqueId(), PRAYER));
        PlayerCombatState st = state(player);
        st.setCurrentHp(maxHp);
        st.setCurrentPrayer(maxPrayer);
        syncVanillaHealth(player, maxHp, maxHp);
        persistAsync(st);
    }

    public void restorePrayer(Player player) {
        int maxPrayer = PrayerPoints.maxPoints(skillLevel(player.getUniqueId(), PRAYER));
        PlayerCombatState st = state(player);
        st.setCurrentPrayer(maxPrayer);
        persistAsync(st);
    }

    private PlayerCombatState loadOrCreate(Player player) {
        int maxHp = maxHp(skillLevel(player.getUniqueId(), HITPOINTS));
        int maxPrayer = PrayerPoints.maxPoints(skillLevel(player.getUniqueId(), PRAYER));
        return loadOrCreateOffline(player.getUniqueId(), maxHp, maxPrayer);
    }

    private PlayerCombatState loadOrCreateOffline(UUID playerId, int maxHp, int maxPrayer) {
        try {
            return repository.get(playerId).orElseGet(() -> {
                PlayerCombatState fresh = PlayerCombatState.fresh(playerId, maxHp, maxPrayer);
                try {
                    repository.upsert(fresh);
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.FINE, "combat init row", e);
                }
                return fresh;
            });
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "combat load", e);
            return PlayerCombatState.fresh(playerId, maxHp, maxPrayer);
        }
    }

    private int skillLevel(UUID playerId, SkillId skillId) {
        SkillSnapshot cached = skillSnapshots.get(playerId);
        if (cached != null) {
            if (!cached.fresh(config.skillCacheTtlMs())) {
                warmSkillCache(playerId);
            }
            return cached.level(skillId);
        }
        // Never block the region/entity thread on SkillService futures (DB).
        warmSkillCache(playerId);
        return 1;
    }

    private static SkillSnapshot loadSkillSnapshot(SkillService skills, UUID playerId) {
        long now = System.currentTimeMillis();
        return new SkillSnapshot(
                levelOrDefault(skills, playerId, ATTACK),
                levelOrDefault(skills, playerId, STRENGTH),
                levelOrDefault(skills, playerId, DEFENCE),
                levelOrDefault(skills, playerId, HITPOINTS),
                levelOrDefault(skills, playerId, PRAYER),
                levelOrDefault(skills, playerId, RANGED),
                levelOrDefault(skills, playerId, MAGIC),
                now);
    }

    private static int levelOrDefault(SkillService skills, UUID playerId, SkillId skillId) {
        try {
            return skills.get(playerId, skillId).join().level();
        } catch (Exception e) {
            return 1;
        }
    }

    public record SkillSnapshot(
            int attack,
            int strength,
            int defence,
            int hitpoints,
            int prayer,
            int ranged,
            int magic,
            long loadedAtMs) {

        boolean fresh(long ttlMs) {
            return System.currentTimeMillis() - loadedAtMs < ttlMs;
        }

        int level(SkillId skillId) {
            return switch (skillId.id()) {
                case "attack" -> attack;
                case "strength" -> strength;
                case "defence" -> defence;
                case "hitpoints" -> hitpoints;
                case "prayer" -> prayer;
                case "ranged" -> ranged;
                case "magic" -> magic;
                default -> 1;
            };
        }
    }
}
