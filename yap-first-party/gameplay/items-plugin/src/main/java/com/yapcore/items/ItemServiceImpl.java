package com.yapcore.items;

import com.yapcore.items.api.ItemService;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Optional;

public final class ItemServiceImpl implements ItemService {

    private final ItemsPlugin plugin;

    public ItemServiceImpl(ItemsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Optional<ItemStack> create(String itemId) {
        return plugin.factory().create(itemId);
    }

    @Override
    public Optional<ItemStack> create(String itemId, int amount) {
        return plugin.factory().create(itemId, amount);
    }

    @Override
    public Optional<String> idOf(ItemStack stack) {
        return plugin.factory().idOf(stack);
    }

    @Override
    public boolean isCustom(ItemStack stack) {
        return plugin.factory().isCustom(stack);
    }

    @Override
    public Collection<String> ids() {
        return plugin.registry().all().keySet();
    }

    @Override
    public boolean has(String itemId) {
        return plugin.registry().get(itemId).isPresent();
    }

    @Override
    public Optional<String> abilityCooldown(String itemId) {
        return plugin.registry().get(itemId).flatMap(def -> {
            if (def.abilities().isEmpty()) {
                return Optional.empty();
            }
            if (def.abilities().size() == 1) {
                return Optional.of(formatCooldown(def.abilities().getFirst().cooldownMs()));
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < def.abilities().size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                var ab = def.abilities().get(i);
                sb.append(ab.type().name().toLowerCase(java.util.Locale.ROOT))
                        .append('=')
                        .append(formatCooldown(ab.cooldownMs()));
            }
            return Optional.of(sb.toString());
        });
    }

    @Override
    public boolean setAbilityCooldown(String itemId, String duration) {
        if (itemId == null || itemId.isBlank() || !has(itemId)) {
            return false;
        }
        try {
            var written = plugin.writer().setAbilityCooldown(itemId, duration);
            if (written.isEmpty()) {
                return false;
            }
            plugin.reloadAll();
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("setAbilityCooldown failed for " + itemId + ": " + e.getMessage());
            return false;
        }
    }

    private static String formatCooldown(long ms) {
        if (ms <= 0L) {
            return "0s";
        }
        if (ms % 1000L == 0L) {
            return (ms / 1000L) + "s";
        }
        if (ms < 1000L) {
            return ms + "ms";
        }
        double sec = ms / 1000.0;
        String s = String.format(java.util.Locale.ROOT, "%.2f", sec);
        if (s.endsWith("0")) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "s";
    }
}
