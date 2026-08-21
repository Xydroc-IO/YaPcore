package com.yapcore.stacker;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collection;
import java.util.Locale;

/** Ground-item stacking via amount + PDC overflow display. */
public final class ItemStackService {

    private static final MiniMessage Mini = MiniMessage.miniMessage();

    private final StackerConfig config;
    private final StackKeys keys;
    private final StackerMetrics metrics;

    public ItemStackService(StackerConfig config, StackKeys keys, StackerMetrics metrics) {
        this.config = config;
        this.keys = keys;
        this.metrics = metrics;
    }

    public boolean enabled() {
        return config.enabled() && config.itemsEnabled();
    }

    public int getCount(Item item) {
        ItemStack stack = item.getItemStack();
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            Integer pdc = meta.getPersistentDataContainer().get(keys.itemStackSize, StackKeys.INT);
            if (pdc != null) {
                return Math.max(1, pdc);
            }
        }
        return Math.max(1, stack.getAmount());
    }

    public void setCount(Item item, int count) {
        int capped = Math.min(Math.max(1, count), config.itemMaxStack());
        ItemStack stack = item.getItemStack().clone();
        int vanillaMax = Math.max(1, stack.getMaxStackSize());
        if (capped <= vanillaMax) {
            stack.setAmount(capped);
            stack.editMeta(meta -> {
                meta.getPersistentDataContainer().remove(keys.itemStackSize);
                meta.displayName(null);
            });
        } else {
            stack.setAmount(vanillaMax);
            String type = prettyMaterial(stack);
            String template = config.itemNametag();
            stack.editMeta(meta -> {
                meta.getPersistentDataContainer().set(keys.itemStackSize, StackKeys.INT, capped);
                if (template != null && !template.isBlank()) {
                    String parsed = template
                            .replace("{type}", type)
                            .replace("{size}", Integer.toString(capped));
                    meta.displayName(Mini.deserialize(parsed));
                }
            });
        }
        item.setItemStack(stack);
    }

    private static String prettyMaterial(ItemStack stack) {
        String raw = stack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
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
        return sb.toString();
    }

    public boolean tryMergeAway(Item incoming) {
        if (!enabled() || !incoming.isValid()) {
            return false;
        }
        if (!config.worldEnabled(incoming.getWorld().getName())) {
            return false;
        }
        Item host = findHost(incoming);
        if (host == null) {
            return false;
        }
        int combined = getCount(host) + getCount(incoming);
        if (combined > config.itemMaxStack()) {
            int room = config.itemMaxStack() - getCount(host);
            if (room <= 0) {
                return false;
            }
            setCount(host, getCount(host) + room);
            int left = getCount(incoming) - room;
            if (left <= 0) {
                metrics.itemMerge();
                return true;
            }
            setCount(incoming, left);
            metrics.itemMerge();
            return false;
        }
        setCount(host, combined);
        metrics.itemMerge();
        return true;
    }

    public Item findHost(Item incoming) {
        Location loc = incoming.getLocation();
        double r = config.itemMergeRadius();
        ItemStack base = stripStackMeta(incoming.getItemStack());
        Collection<Entity> nearby = loc.getWorld().getNearbyEntities(loc, r, r, r,
                e -> e instanceof Item other
                        && other != incoming
                        && other.isValid()
                        && stripStackMeta(other.getItemStack()).isSimilar(base)
                        && getCount(other) < config.itemMaxStack());
        Item best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : nearby) {
            Item it = (Item) e;
            double d = it.getLocation().distanceSquared(loc);
            if (d < bestDist) {
                bestDist = d;
                best = it;
            }
        }
        return best;
    }

    public ItemStack stripStackMeta(ItemStack in) {
        ItemStack copy = in.clone();
        copy.editMeta(meta -> {
            meta.getPersistentDataContainer().remove(keys.itemStackSize);
            meta.displayName(null);
        });
        return copy;
    }
}
