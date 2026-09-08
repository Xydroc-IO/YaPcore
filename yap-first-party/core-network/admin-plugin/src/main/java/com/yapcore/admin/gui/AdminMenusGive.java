package com.yapcore.admin.gui;

import com.yapcore.admin.AdminConfig;
import com.yapcore.admin.AdminPlugin;
import com.yapcore.admin.action.AdminActions;
import com.yapcore.admin.session.AdminSession;
import com.yapcore.messages.YapMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Give hub, presets, kits, and material browser menus. */
final class AdminMenusGive {

    private final AdminPlugin plugin;

    AdminMenusGive(AdminPlugin plugin) {
        this.plugin = plugin;
    }

    void openGiveHub(Player player) {
        if (!player.hasPermission("yapadmin.give")) {
            YapMessages.noPermission(player, "yapadmin.give");
            return;
        }
        AdminSession session = plugin.session(player.getUniqueId());
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.GIVE_HUB);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Give", NamedTextColor.GREEN));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);

        String target = session.hasTarget() ? session.targetName() : player.getName() + " (self)";
        inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuHolder.icon(Material.CHEST, "Give hub",
                "Target: " + target,
                "Amount chip: " + session.giveAmount()));
        inv.setItem(AdminMenuSlots.GIVE_PRESETS, AdminMenuHolder.icon(Material.DIAMOND, "Curated presets",
                "Common admin items"));
        if (plugin.actions().pluginEnabled("YaPPlayerData")) {
            inv.setItem(AdminMenuSlots.GIVE_KITS, AdminMenuHolder.icon(Material.BUNDLE, "Kits",
                    "starter · adventurer · vip"));
        }
        inv.setItem(AdminMenuSlots.GIVE_MATS, AdminMenuHolder.icon(Material.COMPASS, "Material browser",
                "Paginated vanilla items"));
        inv.setItem(AdminMenuSlots.GIVE_AMOUNT, AdminMenuHolder.icon(Material.HOPPER, "Amount: " + session.giveAmount(),
                "Click to cycle 1 → 16 → 64"));
        inv.setItem(AdminMenuSlots.GIVE_TARGET, AdminMenuHolder.icon(Material.PLAYER_HEAD, "Change target",
                "Pick online player"));
        inv.setItem(AdminMenuSlots.SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    void openGivePresets(Player player) {
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.GIVE_PRESETS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Give presets", NamedTextColor.GREEN));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuHolder.icon(Material.DIAMOND, "Presets",
                "Click to give · shift = stack×4"));
        inv.setItem(AdminMenuSlots.SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));

        int slot = 9;
        for (AdminConfig.ItemPreset preset : plugin.adminConfig().presets()) {
            if (slot >= 44) {
                break;
            }
            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            ItemStack icon = AdminMenuHolder.icon(preset.material(), preset.displayName(),
                    "Default ×" + preset.amount(),
                    "Id: " + preset.id());
            icon.setAmount(Math.min(64, Math.max(1, preset.amount())));
            inv.setItem(slot, icon);
            slot++;
        }
        player.openInventory(inv);
    }

    void openGiveKits(Player player) {
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.GIVE_KITS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Give kits", NamedTextColor.GREEN));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuHolder.icon(Material.BUNDLE, "Kits",
                "Dispatches /kit give"));
        inv.setItem(AdminMenuSlots.SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        int slot = 19;
        for (String kit : plugin.adminConfig().kits()) {
            inv.setItem(slot++, AdminMenuHolder.icon(Material.CHEST, kit,
                    "/kit give <player> " + kit));
        }
        player.openInventory(inv);
    }

    void openGiveMaterials(Player player) {
        AdminSession session = plugin.session(player.getUniqueId());
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.GIVE_MATERIALS);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Materials p" + (session.materialPage() + 1), NamedTextColor.GREEN));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);

        inv.setItem(AdminMenuSlots.CAT_ALL, AdminMenuHolder.icon(Material.NETHER_STAR, catLabel(session, AdminSession.MaterialCategory.ALL)));
        inv.setItem(AdminMenuSlots.CAT_BLOCKS, AdminMenuHolder.icon(Material.BRICKS, catLabel(session, AdminSession.MaterialCategory.BLOCKS)));
        inv.setItem(AdminMenuSlots.CAT_TOOLS, AdminMenuHolder.icon(Material.IRON_PICKAXE, catLabel(session, AdminSession.MaterialCategory.TOOLS)));
        inv.setItem(AdminMenuSlots.CAT_COMBAT, AdminMenuHolder.icon(Material.IRON_SWORD, catLabel(session, AdminSession.MaterialCategory.COMBAT)));
        inv.setItem(AdminMenuSlots.CAT_FOOD, AdminMenuHolder.icon(Material.BREAD, catLabel(session, AdminSession.MaterialCategory.FOOD)));
        inv.setItem(AdminMenuSlots.CAT_MISC, AdminMenuHolder.icon(Material.CHEST, catLabel(session, AdminSession.MaterialCategory.MISC)));

        List<Material> mats = filteredMaterials(session.category());
        int page = session.materialPage();
        int maxPage = Math.max(0, (mats.size() - 1) / AdminMenuSlots.PAGE_SIZE);
        if (page > maxPage) {
            session.setMaterialPage(maxPage);
            page = maxPage;
        }
        int start = page * AdminMenuSlots.PAGE_SIZE;
        int end = Math.min(mats.size(), start + AdminMenuSlots.PAGE_SIZE);
        int slot = 9;
        for (int i = start; i < end; i++) {
            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            if (slot >= 44) {
                break;
            }
            Material mat = mats.get(i);
            inv.setItem(slot, AdminMenuHolder.icon(mat, AdminActions.pretty(mat),
                    "Give ×" + session.giveAmount(),
                    mat.name()));
            slot++;
        }

        inv.setItem(AdminMenuSlots.MAT_PREV, AdminMenuHolder.icon(Material.ARROW, "Previous page",
                "Page " + (page + 1) + " / " + (maxPage + 1)));
        inv.setItem(AdminMenuSlots.MAT_BACK, AdminMenuHolder.icon(Material.OAK_DOOR, "Back to Give"));
        inv.setItem(AdminMenuSlots.MAT_AMOUNT, AdminMenuHolder.icon(Material.HOPPER, "Amount: " + session.giveAmount(),
                "Click to cycle 1 → 16 → 64"));
        inv.setItem(AdminMenuSlots.MAT_NEXT, AdminMenuHolder.icon(Material.ARROW, "Next page",
                "Page " + (page + 1) + " / " + (maxPage + 1)));
        player.openInventory(inv);
    }

    static String catLabel(AdminSession session, AdminSession.MaterialCategory cat) {
        String base = cat.name().charAt(0) + cat.name().substring(1).toLowerCase(Locale.ROOT);
        return session.category() == cat ? "▶ " + base : base;
    }

    static List<Material> filteredMaterials(AdminSession.MaterialCategory category) {
        return Arrays.stream(Material.values())
                .filter(Material::isItem)
                .filter(m -> !m.isAir())
                .filter(m -> !m.name().startsWith("LEGACY_"))
                .filter(m -> matchesCategory(m, category))
                .sorted(Comparator.comparing(Enum::name))
                .toList();
    }

    static boolean matchesCategory(Material m, AdminSession.MaterialCategory category) {
        return switch (category) {
            case ALL -> true;
            case BLOCKS -> m.isBlock() && !AdminActions.isTool(m) && !AdminActions.isCombat(m);
            case TOOLS -> AdminActions.isTool(m);
            case COMBAT -> AdminActions.isCombat(m);
            case FOOD -> m.isEdible();
            case MISC -> !m.isBlock() && !AdminActions.isTool(m) && !AdminActions.isCombat(m) && !m.isEdible();
        };
    }
}
