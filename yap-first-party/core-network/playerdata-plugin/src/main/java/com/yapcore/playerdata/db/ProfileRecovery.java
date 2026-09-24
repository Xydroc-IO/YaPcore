package com.yapcore.playerdata.db;

import com.yapcore.playerdata.sync.ItemSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Merges inventory / enderchest / bag pages from an orphan profile (e.g. {@code lobby}
 * after switching that backend to {@code global}) into the destination profile.
 * <p>
 * Never deletes the source row — source stays as a backup. Only fills empty dest slots
 * so existing global/survival gear is kept.
 */
public final class ProfileRecovery {

    public record Result(int players, int inventoriessMerged, int endersMerged, int bagPagesMerged, int itemsMoved) {
    }

    private final Database database;
    private final Logger log;

    public ProfileRecovery(Database database, Logger log) {
        this.database = database;
        this.log = log;
    }

    /** Merge {@code fromProfile} → {@code toProfile} for every UUID that has a source row. */
    public Result mergeAll(String fromProfile, String toProfile) throws SQLException {
        String from = norm(fromProfile);
        String to = norm(toProfile);
        if (from.equals(to)) {
            return new Result(0, 0, 0, 0, 0);
        }
        List<UUID> uuids = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT DISTINCT uuid FROM player_profiles WHERE profile = ?
                     UNION
                     SELECT DISTINCT uuid FROM player_backpack_pages WHERE profile = ?
                     """)) {
            ps.setString(1, from);
            ps.setString(2, from);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    uuids.add(UUID.fromString(rs.getString(1)));
                }
            }
        }
        int inv = 0;
        int ender = 0;
        int bags = 0;
        int items = 0;
        for (UUID uuid : uuids) {
            MergeOne one = mergeOne(uuid, from, to);
            if (one.inventoryTouched()) {
                inv++;
            }
            if (one.enderTouched()) {
                ender++;
            }
            bags += one.bagPages();
            items += one.itemsMoved();
        }
        if (log != null && (!uuids.isEmpty())) {
            log.info("Profile recovery " + from + " → " + to
                    + ": players=" + uuids.size()
                    + " inv=" + inv + " ender=" + ender
                    + " bagPages=" + bags + " items=" + items);
        }
        return new Result(uuids.size(), inv, ender, bags, items);
    }

    public MergeOne mergeOne(UUID uuid, String fromProfile, String toProfile) throws SQLException {
        String from = norm(fromProfile);
        String to = norm(toProfile);
        if (uuid == null || from.equals(to)) {
            return MergeOne.EMPTY;
        }
        ensureDestProfile(uuid, to);

        int items = 0;
        boolean invTouched = false;
        boolean enderTouched = false;

        PlayerSnapshot src = loadProfile(uuid, from);
        PlayerSnapshot dest = loadProfile(uuid, to);
        if (src != null) {
            if (dest == null) {
                dest = PlayerSnapshot.empty(to);
            }
            MergeStacks inv = mergeStacks(dest.inventory, src.inventory, 41);
            MergeStacks ender = mergeStacks(dest.enderchest, src.enderchest, 27);
            items += inv.moved() + ender.moved();
            invTouched = inv.moved() > 0;
            enderTouched = ender.moved() > 0;
            if (invTouched || enderTouched || dest.inventory == null) {
                saveProfile(uuid, to, dest.xp, dest.level, dest.health, dest.food, dest.saturation,
                        inv.result(), ender.result());
            }
            // Overflow from inventory/ender goes into bag pages below.
            List<ItemStack> overflow = new ArrayList<>();
            overflow.addAll(inv.overflow());
            overflow.addAll(ender.overflow());
            items += stashOverflowInBags(uuid, to, overflow);
        }

        int bagPages = mergeBagPages(uuid, from, to);
        return new MergeOne(invTouched, enderTouched, bagPages, items);
    }

    private int mergeBagPages(UUID uuid, String from, String to) throws SQLException {
        Map<Integer, byte[]> srcPages = loadBagPages(uuid, from);
        if (srcPages.isEmpty()) {
            return 0;
        }
        Map<Integer, byte[]> destPages = loadBagPages(uuid, to);
        int pagesTouched = 0;
        int maxPage = Math.max(
                srcPages.keySet().stream().mapToInt(Integer::intValue).max().orElse(0),
                destPages.keySet().stream().mapToInt(Integer::intValue).max().orElse(0));
        for (int page = 1; page <= Math.max(maxPage, 9); page++) {
            byte[] srcBlob = srcPages.get(page);
            if (isBlank(srcBlob, 45)) {
                continue;
            }
            byte[] destBlob = destPages.get(page);
            if (isBlank(destBlob, 45)) {
                saveBagPage(uuid, to, page, srcBlob);
                pagesTouched++;
                continue;
            }
            MergeStacks merged = mergeStacks(
                    ItemSerializer.deserialize(destBlob, 45),
                    ItemSerializer.deserialize(srcBlob, 45),
                    45);
            if (merged.moved() > 0) {
                saveBagPage(uuid, to, page, ItemSerializer.serialize(merged.result()));
                pagesTouched++;
                if (!merged.overflow().isEmpty()) {
                    stashOverflowInBags(uuid, to, merged.overflow());
                }
            }
        }
        return pagesTouched;
    }

    private int stashOverflowInBags(UUID uuid, String profile, List<ItemStack> overflow) throws SQLException {
        if (overflow == null || overflow.isEmpty()) {
            return 0;
        }
        int moved = 0;
        Map<Integer, byte[]> pages = loadBagPages(uuid, profile);
        for (int page = 1; page <= 9 && !overflow.isEmpty(); page++) {
            ItemStack[] slots = ItemSerializer.deserialize(pages.get(page), 45);
            boolean changed = false;
            for (int i = 0; i < slots.length && !overflow.isEmpty(); i++) {
                if (isEmpty(slots[i])) {
                    slots[i] = overflow.remove(0);
                    moved++;
                    changed = true;
                }
            }
            if (changed) {
                saveBagPage(uuid, profile, page, ItemSerializer.serialize(slots));
                pages.put(page, ItemSerializer.serialize(slots));
            }
        }
        if (!overflow.isEmpty() && log != null) {
            log.warning("Profile recovery: " + overflow.size()
                    + " item(s) could not fit for " + uuid + " (bag full)");
        }
        return moved;
    }

    private void ensureDestProfile(UUID uuid, String profile) throws SQLException {
        String sql = database.dialect().insertIgnore(
                "player_profiles",
                List.of("uuid", "profile", "xp", "level", "health", "food", "saturation",
                        "inventory", "enderchest"));
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, profile);
            ps.setInt(3, 0);
            ps.setInt(4, 0);
            ps.setDouble(5, 20.0);
            ps.setInt(6, 20);
            ps.setFloat(7, 5.0f);
            ps.setBytes(8, ItemSerializer.empty(41));
            ps.setBytes(9, ItemSerializer.empty(27));
            ps.executeUpdate();
        }
    }

    private PlayerSnapshot loadProfile(UUID uuid, String profile) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT xp, level, health, food, saturation, inventory, enderchest
                     FROM player_profiles WHERE uuid = ? AND profile = ?
                     """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, profile);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new PlayerSnapshot(
                        profile,
                        rs.getInt("xp"),
                        rs.getInt("level"),
                        rs.getDouble("health"),
                        rs.getInt("food"),
                        rs.getFloat("saturation"),
                        ItemSerializer.deserialize(rs.getBytes("inventory"), 41),
                        ItemSerializer.deserialize(rs.getBytes("enderchest"), 27));
            }
        }
    }

    private void saveProfile(
            UUID uuid,
            String profile,
            int xp,
            int level,
            double health,
            int food,
            float saturation,
            ItemStack[] inventory,
            ItemStack[] ender) throws SQLException {
        String sql = database.dialect().upsert(
                "player_profiles",
                List.of("uuid", "profile"),
                List.of("uuid", "profile", "xp", "level", "health", "food", "saturation",
                        "inventory", "enderchest"),
                Map.of(
                        "xp", "EXCLUDED.xp",
                        "level", "EXCLUDED.level",
                        "health", "EXCLUDED.health",
                        "food", "EXCLUDED.food",
                        "saturation", "EXCLUDED.saturation",
                        "inventory", "EXCLUDED.inventory",
                        "enderchest", "EXCLUDED.enderchest"));
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, profile);
            ps.setInt(3, xp);
            ps.setInt(4, level);
            ps.setDouble(5, health);
            ps.setInt(6, food);
            ps.setFloat(7, saturation);
            ps.setBytes(8, ItemSerializer.serialize(inventory));
            ps.setBytes(9, ItemSerializer.serialize(ender));
            ps.executeUpdate();
        }
    }

    private Map<Integer, byte[]> loadBagPages(UUID uuid, String profile) throws SQLException {
        Map<Integer, byte[]> out = new LinkedHashMap<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT page, contents FROM player_backpack_pages
                     WHERE uuid = ? AND profile = ?
                     """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, profile);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getInt("page"), rs.getBytes("contents"));
                }
            }
        }
        return out;
    }

    private void saveBagPage(UUID uuid, String profile, int page, byte[] contents) throws SQLException {
        String sql = database.dialect().upsert(
                "player_backpack_pages",
                List.of("uuid", "profile", "page"),
                List.of("uuid", "profile", "page", "contents"),
                Map.of("contents", "EXCLUDED.contents"));
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, profile);
            ps.setInt(3, page);
            ps.setBytes(4, contents != null ? contents : ItemSerializer.empty(45));
            ps.executeUpdate();
        }
    }

    static MergeStacks mergeStacks(ItemStack[] destIn, ItemStack[] srcIn, int size) {
        ItemStack[] dest = resize(destIn, size);
        ItemStack[] src = resize(srcIn, size);
        List<ItemStack> overflow = new ArrayList<>();
        int moved = 0;
        for (ItemStack stack : src) {
            if (isEmpty(stack)) {
                continue;
            }
            int slot = firstEmpty(dest);
            if (slot >= 0) {
                dest[slot] = stack.clone();
                moved++;
            } else {
                overflow.add(stack.clone());
            }
        }
        return new MergeStacks(dest, overflow, moved);
    }

    private static ItemStack[] resize(ItemStack[] in, int size) {
        ItemStack[] out = new ItemStack[size];
        if (in != null) {
            System.arraycopy(in, 0, out, 0, Math.min(in.length, size));
        }
        return out;
    }

    private static int firstEmpty(ItemStack[] items) {
        for (int i = 0; i < items.length; i++) {
            if (isEmpty(items[i])) {
                return i;
            }
        }
        return -1;
    }

    static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0;
    }

    static boolean isBlank(byte[] blob, int slots) {
        if (blob == null || blob.length == 0) {
            return true;
        }
        // Empty YAML is tiny ("size: N\n"); anything with items is much larger.
        if (blob.length <= 24) {
            return true;
        }
        ItemStack[] items = ItemSerializer.deserialize(blob, slots);
        for (ItemStack item : items) {
            if (!isEmpty(item)) {
                return false;
            }
        }
        return true;
    }

    private static String norm(String profile) {
        if (profile == null || profile.isBlank()) {
            return "global";
        }
        return profile.trim().toLowerCase(Locale.ROOT);
    }

    public record MergeOne(boolean inventoryTouched, boolean enderTouched, int bagPages, int itemsMoved) {
        static final MergeOne EMPTY = new MergeOne(false, false, 0, 0);
    }

    private record MergeStacks(ItemStack[] result, List<ItemStack> overflow, int moved) {
    }

    private record PlayerSnapshot(
            String profile,
            int xp,
            int level,
            double health,
            int food,
            float saturation,
            ItemStack[] inventory,
            ItemStack[] enderchest) {
        static PlayerSnapshot empty(String profile) {
            return new PlayerSnapshot(profile, 0, 0, 20.0, 20, 5.0f,
                    new ItemStack[41], new ItemStack[27]);
        }
    }
}
