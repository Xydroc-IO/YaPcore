package com.yapcore.stacker;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Tameable;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Core mob stack read/write + merge eligibility (no NMS). */
public final class StackService {

    private static final MiniMessage Mini = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final StackerPlugin plugin;
    private final StackerConfig config;
    private final StackKeys keys;
    private final HookService hooks;
    private final StackerMetrics metrics;

    private final ThreadLocal<Integer> pendingRemainder = ThreadLocal.withInitial(() -> 0);

    public StackService(
            StackerPlugin plugin,
            StackerConfig config,
            HookService hooks,
            StackerMetrics metrics) {
        this.plugin = plugin;
        this.config = config;
        this.keys = new StackKeys(plugin);
        this.hooks = hooks;
        this.metrics = metrics;
    }

    public void beginRemainderSpawn(int size) {
        pendingRemainder.set(Math.max(1, size));
    }

    public int takePendingRemainder() {
        int v = pendingRemainder.get();
        pendingRemainder.set(0);
        return v;
    }

    public boolean hasPendingRemainder() {
        return pendingRemainder.get() > 0;
    }

    public StackKeys keys() {
        return keys;
    }

    public StackerConfig config() {
        return config;
    }

    public HookService hooks() {
        return hooks;
    }

    public StackerMetrics metrics() {
        return metrics;
    }

    public int getStack(LivingEntity entity) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        Integer size = pdc.get(keys.stackSize, StackKeys.INT);
        return size == null ? 1 : Math.max(1, size);
    }

    public void setStack(LivingEntity entity, int size) {
        int cap = config.maxStackFor(entity.getType());
        int capped = Math.min(Math.max(1, size), cap);
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (capped <= 1) {
            pdc.remove(keys.stackSize);
            pdc.remove(keys.stacked);
            entity.customName(null);
            entity.setCustomNameVisible(false);
            return;
        }
        pdc.set(keys.stackSize, StackKeys.INT, capped);
        pdc.set(keys.stacked, StackKeys.BYTE, (byte) 1);
        applyNametag(entity, capped);
    }

    public boolean isOurStacked(LivingEntity entity) {
        return entity.getPersistentDataContainer().has(keys.stacked, StackKeys.BYTE);
    }

    public boolean canStack(LivingEntity entity) {
        if (!config.enabled() || !config.mobsEnabled()) {
            return false;
        }
        if (entity instanceof Player || entity instanceof ArmorStand) {
            return false;
        }
        if (!entity.isValid() || entity.isDead()) {
            return false;
        }
        if (!config.worldEnabled(entity.getWorld().getName())) {
            return false;
        }
        if (!config.mobTypeEnabled(entity.getType())) {
            return false;
        }
        if (config.blacklist().contains(entity.getType())) {
            return false;
        }
        if (!config.whitelist().isEmpty() && !config.whitelist().contains(entity.getType())) {
            return false;
        }
        if (hooks.shouldSkip(entity)) {
            return false;
        }
        if (config.skipTamed() && entity instanceof Tameable tameable && tameable.isTamed()) {
            return false;
        }
        if (config.skipLeashed() && entity.isLeashed()) {
            return false;
        }
        if (config.skipNamed() && hasForeignCustomName(entity)) {
            return false;
        }
        return true;
    }

    public boolean areCompatible(LivingEntity a, LivingEntity b) {
        if (a.getType() != b.getType()) {
            return false;
        }
        if (!canStack(a) || !canStack(b)) {
            return false;
        }
        if (config.requireSameAge() && a instanceof Ageable aa && b instanceof Ageable ba) {
            if (aa.isAdult() != ba.isAdult()) {
                return false;
            }
        }
        boolean sameColor = config.requireSameSheepColor();
        StackerConfig.MobRule rule = config.rule(a.getType());
        if (rule.requireSameColor() != null) {
            sameColor = rule.requireSameColor();
        }
        if (sameColor && a instanceof Sheep sa && b instanceof Sheep sb) {
            if (sa.getColor() != sb.getColor()) {
                return false;
            }
        }
        if (config.requireSameSlimeSize() && a instanceof Slime sa && b instanceof Slime sb) {
            if (sa.getSize() != sb.getSize()) {
                return false;
            }
        }
        return true;
    }

    public boolean tryMergeAway(LivingEntity incoming) {
        if (hasPendingRemainder()) {
            return false;
        }
        if (!canStack(incoming)) {
            return false;
        }
        LivingEntity host = findMergeHost(incoming);
        if (host == null) {
            return false;
        }
        int combined = getStack(host) + getStack(incoming);
        setStack(host, combined);
        metrics.mobMerge();
        return true;
    }

    public boolean tryMergeAfterRemainder(LivingEntity entity) {
        if (!canStack(entity)) {
            return false;
        }
        LivingEntity host = findMergeHost(entity);
        if (host == null) {
            return false;
        }
        int combined = getStack(host) + getStack(entity);
        setStack(host, combined);
        entity.remove();
        metrics.mobMerge();
        return true;
    }

    public LivingEntity findMergeHost(LivingEntity incoming) {
        Location loc = incoming.getLocation();
        double r = config.mergeRadius();
        int cap = config.maxStackFor(incoming.getType());
        Collection<Entity> nearby = loc.getWorld().getNearbyEntities(loc, r, r, r,
                e -> e instanceof LivingEntity living
                        && e != incoming
                        && areCompatible(incoming, living)
                        && getStack(living) < cap);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : nearby) {
            LivingEntity living = (LivingEntity) e;
            double d = living.getLocation().distanceSquared(loc);
            if (d < bestDist) {
                bestDist = d;
                best = living;
            }
        }
        return best;
    }

    /** Force-merge nearby compatible mobs into {@code host}; returns absorbed count. */
    public int forceMergeNearby(LivingEntity host) {
        if (!canStack(host)) {
            return 0;
        }
        Location loc = host.getLocation();
        double r = config.mergeRadius();
        int cap = config.maxStackFor(host.getType());
        List<LivingEntity> victims = new ArrayList<>();
        for (Entity e : loc.getWorld().getNearbyEntities(loc, r, r, r)) {
            if (e instanceof LivingEntity living && living != host && areCompatible(host, living)) {
                victims.add(living);
            }
        }
        int absorbed = 0;
        for (LivingEntity v : victims) {
            int room = cap - getStack(host);
            if (room <= 0) {
                break;
            }
            int take = Math.min(room, getStack(v));
            setStack(host, getStack(host) + take);
            int left = getStack(v) - take;
            if (left <= 0) {
                v.remove();
            } else {
                setStack(v, left);
            }
            absorbed += take;
            metrics.mobMerge();
        }
        return absorbed;
    }

    public void applyNametag(LivingEntity entity, int size) {
        String template = config.mobNametag();
        if (template == null || template.isBlank() || size <= 1) {
            entity.customName(null);
            entity.setCustomNameVisible(false);
            return;
        }
        String type = prettyType(entity);
        String parsed = template
                .replace("{type}", type)
                .replace("{size}", Integer.toString(size));
        Component name = Mini.deserialize(parsed);
        entity.customName(name);
        entity.setCustomNameVisible(true);
    }

    private boolean hasForeignCustomName(LivingEntity entity) {
        Component name = entity.customName();
        if (name == null) {
            return false;
        }
        if (isOurStacked(entity)) {
            return false;
        }
        String plain = PLAIN.serialize(name).trim();
        return !plain.isEmpty();
    }

    static String prettyType(LivingEntity entity) {
        String raw = entity.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder sb = new StringBuilder(raw.length());
        boolean cap = true;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ' ') {
                sb.append(c);
                cap = true;
            } else if (cap) {
                sb.append(Character.toUpperCase(c));
                cap = false;
            } else {
                sb.append(c);
            }
        }
        String base = sb.toString();
        if (entity instanceof Ageable ageable && !ageable.isAdult()) {
            return "Baby " + base;
        }
        if (entity instanceof Sheep sheep && sheep.getColor() != null) {
            String color = sheep.getColor().name().toLowerCase(Locale.ROOT).replace('_', ' ');
            return Character.toUpperCase(color.charAt(0)) + color.substring(1) + " " + base;
        }
        return base;
    }

    public StackerPlugin plugin() {
        return plugin;
    }
}
