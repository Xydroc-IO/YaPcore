package com.yapcore.dungeons.loot;

import com.yapcore.dungeons.DungeonsConfig;
import com.yapcore.playerdata.PlayerDataService;
import com.yapcore.playerdata.PlayerDataServiceProvider;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public final class LootService {

    private final JavaPlugin plugin;
    private final DungeonsConfig config;
    private final LootTable table;
    private final NamespacedKey tierKey;
    private final NamespacedKey levelKey;

    public LootService(JavaPlugin plugin, DungeonsConfig config, LootTable table) {
        this.plugin = plugin;
        this.config = config;
        this.table = table;
        this.tierKey = new NamespacedKey(plugin, "yap_dungeon_tier");
        this.levelKey = new NamespacedKey(plugin, "yap_dungeon_level");
    }

    public List<ItemStack> rollClearRewards(int level, long seed, int clearsToday) {
        LootTable.LevelLoot loot = table.get(level);
        Random rng = new Random(seed ^ 0x1007L);
        List<ItemStack> out = new ArrayList<>();
        for (LootTable.Entry e : loot.guaranteed()) {
            out.add(stack(e, level));
        }
        // One weighted rare roll; prestige gets a second
        out.add(weighted(loot.rare(), level, rng));
        if (level >= 51) {
            out.add(weighted(loot.rare(), level, rng));
        }
        return out;
    }

    public double economyPayout(int level, int clearsToday) {
        double base = table.get(level).economy();
        if (clearsToday >= config.dailyClearSoftCap()) {
            base *= config.dailyEconomyScale();
        }
        return base;
    }

    public void grant(Player player, List<ItemStack> items, double economy) {
        for (ItemStack item : items) {
            var leftover = player.getInventory().addItem(item);
            leftover.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
        }
        if (economy > 0) {
            Optional<PlayerDataService> pdata = PlayerDataServiceProvider.find();
            pdata.ifPresent(svc -> svc.deposit(player.getUniqueId(), economy));
            player.sendMessage("§a+" + (int) economy + " §7dungeon reward");
        }
        player.sendMessage("§6Dungeon loot granted (" + items.size() + " stacks)");
    }

    public void fillChest(org.bukkit.block.Chest chest, int level, long seed) {
        LootTable.LevelLoot loot = table.get(level);
        Random rng = new Random(seed ^ chest.getX() * 31L ^ chest.getZ());
        List<ItemStack> stacks = new ArrayList<>();
        if (!loot.guaranteed().isEmpty()) {
            stacks.add(stack(loot.guaranteed().get(rng.nextInt(loot.guaranteed().size())), level));
        }
        if (rng.nextDouble() < 0.55) {
            stacks.add(weighted(loot.rare(), level, rng));
        }
        chest.getInventory().clear();
        for (ItemStack s : stacks) {
            chest.getInventory().addItem(s);
        }
        chest.update();
    }

    private ItemStack weighted(List<LootTable.Entry> entries, int level, Random rng) {
        if (entries.isEmpty()) {
            return new ItemStack(MaterialSafe.IRON_INGOT, 1);
        }
        int total = entries.stream().mapToInt(LootTable.Entry::weight).sum();
        int roll = rng.nextInt(Math.max(1, total));
        int acc = 0;
        for (LootTable.Entry e : entries) {
            acc += e.weight();
            if (roll < acc) {
                return stack(e, level);
            }
        }
        return stack(entries.getLast(), level);
    }

    private ItemStack stack(LootTable.Entry e, int level) {
        ItemStack stack = new ItemStack(e.material(), e.amount());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING,
                    level >= 51 ? "prestige" : "core");
            meta.getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER, level);
                if (e.named()) {
                String title = (level >= 51 ? "Prestige " : "Dungeon ") + level + " Trophy";
                meta.displayName(Component.text(title, NamedTextColor.GOLD, TextDecoration.BOLD));
                String typeName = stack.getType().name();
                meta.addEnchant(Enchantment.UNBREAKING, Math.min(5, 1 + level / 20), true);
                if (typeName.contains("SWORD") && level >= 20) {
                    meta.addEnchant(Enchantment.SHARPNESS, Math.min(5, 1 + level / 20), true);
                }
                if (typeName.contains("CHESTPLATE") && level >= 30) {
                    meta.addEnchant(Enchantment.PROTECTION, Math.min(4, 1 + level / 25), true);
                }
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** Avoid hard Material refs in helpers when missing. */
    private static final class MaterialSafe {
        static final org.bukkit.Material IRON_INGOT = org.bukkit.Material.IRON_INGOT;
    }
}
