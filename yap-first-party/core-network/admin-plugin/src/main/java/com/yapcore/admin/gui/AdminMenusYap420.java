package com.yapcore.admin.gui;

import com.yapcore.admin.AdminPlugin;
import com.yapcore.admin.session.AdminSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;

/** YaP420 plant / cure / give admin panels. */
final class AdminMenusYap420 {

    private static final List<GiveEntry> GIVE_ENTRIES = List.of(
            new GiveEntry("yap420_seed_sativa", "Sativa seeds", Material.WHEAT_SEEDS),
            new GiveEntry("yap420_seed_indica", "Indica seeds", Material.WHEAT_SEEDS),
            new GiveEntry("yap420_bud_wet_sativa", "Wet sativa bud", Material.GREEN_DYE),
            new GiveEntry("yap420_bud_wet_indica", "Wet indica bud", Material.GREEN_DYE),
            new GiveEntry("yap420_bud_cured_sativa", "Cured sativa bud", Material.LIME_DYE),
            new GiveEntry("yap420_bud_cured_indica", "Cured indica bud", Material.LIME_DYE),
            new GiveEntry("yap420_gram_sativa", "Sativa gram", Material.DRIED_KELP),
            new GiveEntry("yap420_gram_indica", "Indica gram", Material.DRIED_KELP),
            new GiveEntry("yap420_ounce_sativa", "Sativa ounce", Material.HAY_BLOCK),
            new GiveEntry("yap420_ounce_indica", "Indica ounce", Material.HAY_BLOCK),
            new GiveEntry("yap420_brick_sativa", "Sativa pound", Material.HAY_BLOCK),
            new GiveEntry("yap420_brick_indica", "Indica pound", Material.HAY_BLOCK),
            new GiveEntry("yap420_rolling_paper", "Rolling paper", Material.PAPER),
            new GiveEntry("yap420_joint_sativa", "Sativa joint", Material.COOKIE),
            new GiveEntry("yap420_joint_indica", "Indica joint", Material.COOKIE),
            new GiveEntry("yap420_blunt_sativa", "Sativa blunt", Material.COOKIE),
            new GiveEntry("yap420_blunt_indica", "Indica blunt", Material.COOKIE),
            new GiveEntry("yap420_brownie", "Medicated brownie", Material.COOKIE),
            new GiveEntry("yap420_drying_rack", "Drying rack", Material.TRIPWIRE_HOOK),
            new GiveEntry("yap420_packaging_press", "Packaging press", Material.PISTON),
            new GiveEntry("yap420_hemp_fiber", "Hemp fiber", Material.PAPER)
    );

    private final AdminPlugin plugin;

    AdminMenusYap420(AdminPlugin plugin) {
        this.plugin = plugin;
    }

    void openHub(Player player) {
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.YAP420_HUB);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("YaP420", NamedTextColor.GREEN));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);

        boolean on = plugin.actions().pluginEnabled("YaP420");
        AdminSession session = plugin.session(player.getUniqueId());
        String target = giveTargetLabel(player, session);
        String stats = statusLine();

        inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuHolder.icon(Material.KELP, NamedTextColor.GREEN, "YaP420",
                on ? "Plugin online" : "Plugin offline on this server",
                stats,
                "Give target: " + target,
                "Select a player under Players (optional)"));

        if (!on) {
            inv.setItem(22, AdminMenuHolder.icon(Material.BARRIER, NamedTextColor.RED, "YaP420 offline",
                    "Install yap-420.jar / enable YaP420"));
        } else {
            inv.setItem(AdminMenuSlots.Y420_GIVE_SEEDS, AdminMenuHolder.icon(Material.WHEAT_SEEDS, NamedTextColor.GREEN,
                    "Give seeds ×" + session.giveAmount(),
                    "Sativa + indica seeds",
                    "To: " + target));
            inv.setItem(AdminMenuSlots.Y420_GIVE_BUDS, AdminMenuHolder.icon(Material.DRIED_KELP, NamedTextColor.GREEN,
                    "Give cured buds ×" + session.giveAmount(),
                    "Sativa + indica cured",
                    "To: " + target));
            inv.setItem(AdminMenuSlots.Y420_GIVE_CONSUME, AdminMenuHolder.icon(Material.COOKIE, NamedTextColor.GOLD,
                    "Give consumables ×" + session.giveAmount(),
                    "Joints + brownie + paper",
                    "To: " + target));
            inv.setItem(AdminMenuSlots.Y420_GIVE_RACK, AdminMenuHolder.icon(Material.TRIPWIRE_HOOK, NamedTextColor.YELLOW,
                    "Give drying rack",
                    "Placeable rack item",
                    "To: " + target));
            inv.setItem(AdminMenuSlots.Y420_GIVE_ALL, AdminMenuHolder.icon(Material.CHEST, NamedTextColor.AQUA,
                    "Give full set ×" + session.giveAmount(),
                    "Seeds, paper, cured, joints, rack",
                    "To: " + target));
            inv.setItem(AdminMenuSlots.Y420_BROWSE, AdminMenuHolder.icon(Material.BOOKSHELF, "Browse & give…",
                    "Pick any YaP420 item",
                    "Click amount tile to cycle stack size"));
            inv.setItem(AdminMenuSlots.Y420_AMOUNT, AdminMenuHolder.icon(Material.GOLD_NUGGET, "Amount: ×" + session.giveAmount(),
                    "Click to cycle 1 · 8 · 16 · 32 · 64"));
            inv.setItem(AdminMenuSlots.Y420_STARTER, AdminMenuHolder.icon(Material.BUNDLE, NamedTextColor.LIGHT_PURPLE,
                    "Starter kit",
                    "16 seeds each · 16 paper · 1 rack · 2 joints",
                    "To: " + target));
            inv.setItem(AdminMenuSlots.Y420_RELOAD, AdminMenuHolder.icon(Material.COMPARATOR, "Reload",
                    "/yap420 reload"));
            inv.setItem(AdminMenuSlots.Y420_REMOVE, AdminMenuHolder.icon(Material.BARRIER, NamedTextColor.RED, "Remove look-at",
                    "Look at a plant or rack · /yap420 remove"));
            inv.setItem(AdminMenuSlots.Y420_INFO, AdminMenuHolder.icon(Material.SPYGLASS, "Status",
                    "/yap420 info — plots & racks count"));
        }

        inv.setItem(AdminMenuSlots.SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    void openGive(Player player) {
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.YAP420_GIVE);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("YaP420 give", NamedTextColor.GREEN));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);

        AdminSession session = plugin.session(player.getUniqueId());
        String target = giveTargetLabel(player, session);
        inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuHolder.icon(Material.BOOK, "Give YaP420 item",
                "Click an item · amount ×" + session.giveAmount(),
                "To: " + target));
        inv.setItem(AdminMenuSlots.Y420_AMOUNT, AdminMenuHolder.icon(Material.GOLD_NUGGET, "Amount: ×" + session.giveAmount(),
                "Click to cycle 1 · 8 · 16 · 32 · 64"));

        int slot = 10;
        for (GiveEntry entry : GIVE_ENTRIES) {
            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            if (slot >= 44) {
                break;
            }
            ItemStack icon = AdminMenuHolder.icon(entry.material(), NamedTextColor.GREEN, entry.label(),
                    "id:" + entry.id(),
                    "Give ×" + session.giveAmount() + " to " + target);
            inv.setItem(slot, icon);
            slot++;
        }

        inv.setItem(AdminMenuSlots.SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    static List<GiveEntry> giveEntries() {
        return GIVE_ENTRIES;
    }

    static String giveTargetLabel(Player admin, AdminSession session) {
        if (session.hasTarget()) {
            return session.targetName();
        }
        return admin.getName() + " (self)";
    }

    static String giveTargetName(Player admin, AdminSession session) {
        if (session.hasTarget()) {
            return session.targetName();
        }
        return admin.getName();
    }

    private static String statusLine() {
        Plugin pl = Bukkit.getPluginManager().getPlugin("YaP420");
        if (pl == null || !pl.isEnabled()) {
            return "plots/racks: n/a";
        }
        try {
            Object plots = pl.getClass().getMethod("plots").invoke(pl);
            Object racks = pl.getClass().getMethod("racks").invoke(pl);
            int p = (Integer) plots.getClass().getMethod("size").invoke(plots);
            int r = (Integer) racks.getClass().getMethod("size").invoke(racks);
            return "plots=" + p + " · racks=" + r;
        } catch (ReflectiveOperationException e) {
            return "plots/racks: unknown";
        }
    }

    record GiveEntry(String id, String label, Material material) {
    }
}
