package com.yapcore.dungeons.loot;

import com.yapcore.dungeons.DungeonsConfig;
import com.yapcore.playerdata.PlayerDataService;
import com.yapcore.playerdata.PlayerDataServiceProvider;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
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
    private final NamespacedKey chestKey;

    public LootService(JavaPlugin plugin, DungeonsConfig config, LootTable table) {
        this.plugin = plugin;
        this.config = config;
        this.table = table;
        this.tierKey = new NamespacedKey(plugin, "yap_dungeon_tier");
        this.levelKey = new NamespacedKey(plugin, "yap_dungeon_level");
        this.chestKey = new NamespacedKey(plugin, "yap_dungeon_chest");
    }

    public NamespacedKey chestKey() {
        return chestKey;
    }

    public List<ItemStack> rollClearRewards(int level, long seed, int clearsToday) {
        LootTable.LevelLoot loot = table.get(level);
        Random rng = new Random(seed ^ 0x1007L ^ (long) clearsToday * 31L);
        List<ItemStack> out = new ArrayList<>();
        for (LootTable.Entry e : loot.guaranteed()) {
            out.add(stack(e, level));
        }
        out.add(weighted(loot.rare(), level, rng));
        if (level >= 51) {
            out.add(weighted(loot.rare(), level, rng));
        }
        // Always at least one tangible stack
        if (out.isEmpty()) {
            out.add(new ItemStack(Material.IRON_INGOT, 4));
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
        int given = 0;
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                continue;
            }
            var leftover = player.getInventory().addItem(item);
            leftover.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
            given++;
        }
        if (economy > 0) {
            Optional<PlayerDataService> pdata = PlayerDataServiceProvider.find();
            pdata.ifPresent(svc -> svc.deposit(player.getUniqueId(), economy));
            player.sendMessage("§a+" + (int) economy + " §7dungeon reward");
        }
        player.sendMessage("§6Dungeon loot granted (§e" + given + "§6 stacks)");
    }

    /**
     * Fill a dungeon chest using the <em>live</em> block inventory (Folia-safe).
     * Snapshot {@code Chest#getInventory()} + {@code update()} was wiping stacks.
     *
     * @return number of item stacks placed
     */
    public int fillChest(Block block, int level, long seed) {
        if (block == null) {
            return 0;
        }
        if (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST) {
            block.setType(Material.CHEST, false);
        }
        if (!(block.getState() instanceof Chest chest)) {
            return 0;
        }
        // Tag for open-time refill safety net
        chest.getPersistentDataContainer().set(chestKey, PersistentDataType.STRING, "filled");
        chest.getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER, level);
        chest.update(true, false);

        // Re-fetch live inventory after tag write
        if (!(block.getState() instanceof Chest live)) {
            return 0;
        }
        Inventory inv = live.getBlockInventory();
        inv.clear();

        LootTable.LevelLoot loot = table.get(level);
        Random rng = new Random(seed ^ block.getX() * 31L ^ block.getZ() * 997L ^ (long) level * 13L);
        List<ItemStack> stacks = new ArrayList<>();
        if (!loot.guaranteed().isEmpty()) {
            for (LootTable.Entry e : loot.guaranteed()) {
                stacks.add(stack(e, level));
            }
        } else {
            stacks.add(new ItemStack(Material.IRON_INGOT, 2 + rng.nextInt(4)));
            stacks.add(new ItemStack(Material.GOLDEN_APPLE, 1));
        }
        stacks.add(weighted(loot.rare(), level, rng));
        if (level >= 10 || rng.nextDouble() < 0.55) {
            stacks.add(weighted(loot.rare(), level, rng));
        }

        int placed = 0;
        int slot = 0;
        for (ItemStack s : stacks) {
            if (s == null || s.getType().isAir() || s.getAmount() <= 0) {
                continue;
            }
            if (slot < inv.getSize()) {
                inv.setItem(slot++, s);
            } else {
                inv.addItem(s);
            }
            placed++;
        }
        return placed;
    }

    /** If a tagged dungeon chest is empty when opened, refill it once. */
    public int refillIfEmpty(Block block, int level, long seed) {
        if (!(block.getState() instanceof Chest chest)) {
            return 0;
        }
        Inventory inv = chest.getBlockInventory();
        for (ItemStack s : inv.getContents()) {
            if (s != null && !s.getType().isAir()) {
                return 0;
            }
        }
        Integer stored = chest.getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER);
        int lvl = stored != null ? stored : level;
        return fillChest(block, lvl, seed);
    }

    private ItemStack weighted(List<LootTable.Entry> entries, int level, Random rng) {
        if (entries.isEmpty()) {
            return new ItemStack(Material.IRON_INGOT, 1 + rng.nextInt(3));
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
        ItemStack stack = new ItemStack(e.material(), Math.max(1, e.amount()));
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
}
