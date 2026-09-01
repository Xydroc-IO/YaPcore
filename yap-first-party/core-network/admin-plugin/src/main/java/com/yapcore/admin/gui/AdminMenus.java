package com.yapcore.admin.gui;

import com.yapcore.admin.AdminConfig;
import com.yapcore.admin.AdminPlugin;
import com.yapcore.admin.action.AdminActions;
import com.yapcore.admin.session.AdminSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Builds admin super-menu inventories. */
public final class AdminMenus {

    public static final int SLOT_BACK = 45;
    public static final int SLOT_CLOSE = 49;
    public static final int SLOT_INFO = 4;

    // Hub
    public static final int HUB_PLAYERS = 10;
    public static final int HUB_SELF = 12;
    public static final int HUB_GIVE = 14;
    public static final int HUB_MOD = 16;
    public static final int HUB_SERVER = 28;
    public static final int HUB_ECONOMY = 30;
    public static final int HUB_LINKS = 32;
    public static final int HUB_COMBAT = 34;

    // Give hub
    public static final int GIVE_PRESETS = 20;
    public static final int GIVE_KITS = 22;
    public static final int GIVE_MATS = 24;
    public static final int GIVE_AMOUNT = 31;
    public static final int GIVE_TARGET = 40;

    // Materials nav
    public static final int MAT_PREV = 45;
    public static final int MAT_NEXT = 53;
    public static final int MAT_AMOUNT = 49;
    public static final int MAT_BACK = 48;
    public static final int CAT_ALL = 0;
    public static final int CAT_BLOCKS = 1;
    public static final int CAT_TOOLS = 2;
    public static final int CAT_COMBAT = 3;
    public static final int CAT_FOOD = 4;
    public static final int CAT_MISC = 5;

    private static final int PAGE_SIZE = 28;

    private final AdminPlugin plugin;

    public AdminMenus(AdminPlugin plugin) {
        this.plugin = plugin;
    }

    public void openHub(Player player) {
        if (!player.hasPermission("yapadmin.menu")) {
            player.sendMessage("§cNo permission.");
            return;
        }
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuHolder.Kind.HUB);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("YaP Admin", NamedTextColor.GOLD));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);

        AdminSession session = plugin.session(player.getUniqueId());
        String targetLore = session.hasTarget()
                ? "Target: " + session.targetName()
                : "No player selected";

        inv.setItem(SLOT_INFO, AdminMenuHolder.icon(Material.NETHER_STAR, NamedTextColor.AQUA, "Staff Hub",
                "Kitchen-sink admin controls",
                targetLore,
                "Online: " + Bukkit.getOnlinePlayers().size()));

        inv.setItem(HUB_PLAYERS, AdminMenuHolder.icon(Material.PLAYER_HEAD, "Players",
                "Pick an online player", "TP · moderate · inspect"));
        inv.setItem(HUB_SELF, AdminMenuHolder.icon(Material.FEATHER, "Self tools",
                "Fly · god · vanish · heal"));
        if (player.hasPermission("yapadmin.give")) {
            inv.setItem(HUB_GIVE, AdminMenuHolder.icon(Material.CHEST, "Give",
                    "Presets · kits · materials"));
        }
        inv.setItem(HUB_MOD, AdminMenuHolder.icon(Material.IRON_SWORD, NamedTextColor.RED, "Moderation",
                "Pick a player to kick / mute / ban"));
        if (player.hasPermission("yapadmin.server")) {
            inv.setItem(HUB_SERVER, AdminMenuHolder.icon(Material.COMMAND_BLOCK, "Server",
                    "Broadcast · status"));
        }
        if (player.hasPermission("yapadmin.economy") && plugin.actions().mmoContentEnabled()) {
            inv.setItem(HUB_ECONOMY, AdminMenuHolder.icon(Material.GOLD_INGOT, "Economy",
                    "Grant money to target / self"));
        }
        inv.setItem(HUB_LINKS, AdminMenuHolder.icon(Material.COMPASS, "Deep links",
                "Ranks · World · Stacker · Menu"));
        if (plugin.actions().pluginEnabled("YaPSkills") || plugin.actions().pluginEnabled("YaPCombat")) {
            inv.setItem(HUB_COMBAT, AdminMenuHolder.icon(Material.BLAZE_ROD, "Combat / Skills",
                    "Open skills · combat helpers"));
        }
        inv.setItem(SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    public void openPlayers(Player player) {
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuHolder.Kind.PLAYERS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Online players", NamedTextColor.YELLOW));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(SLOT_INFO, AdminMenuHolder.icon(Material.BOOK, "Online players",
                "Click a head to manage"));
        inv.setItem(SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));

        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.sort(Comparator.comparing(p -> p.getName().toLowerCase(Locale.ROOT)));
        int slot = 10;
        for (Player other : online) {
            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            if (slot >= 44) {
                break;
            }
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            head.editMeta(SkullMeta.class, meta -> {
                meta.setOwningPlayer(other);
                meta.displayName(Component.text(other.getName()).color(NamedTextColor.GREEN)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                meta.lore(List.of(
                        Component.text("World: " + other.getWorld().getName())
                                .color(NamedTextColor.GRAY)
                                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                        Component.text("Click to manage")
                                .color(NamedTextColor.DARK_GRAY)
                                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
            });
            inv.setItem(slot, head);
            slot++;
        }
        player.openInventory(inv);
    }

    public void openPlayerActions(Player player, Player target) {
        plugin.session(player.getUniqueId()).setTarget(target.getUniqueId(), target.getName());
        AdminMenuHolder holder = new AdminMenuHolder(
                AdminMenuHolder.Kind.PLAYER_ACTIONS, target.getUniqueId(), target.getName());
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Player: " + target.getName(), NamedTextColor.AQUA));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);

        inv.setItem(SLOT_INFO, playerHead(target, "Managing " + target.getName()));
        inv.setItem(10, AdminMenuHolder.icon(Material.ENDER_PEARL, "TP to player",
                "Teleport yourself to them"));
        inv.setItem(11, AdminMenuHolder.icon(Material.LEAD, "TP here",
                "Bring them to you"));
        inv.setItem(12, AdminMenuHolder.icon(Material.OAK_DOOR, "TP to spawn",
                "Send them to world spawn"));
        inv.setItem(14, AdminMenuHolder.icon(Material.PACKED_ICE, "Freeze",
                "Toggle freeze (Essentials)"));
        inv.setItem(15, AdminMenuHolder.icon(Material.CHEST, "Invsee",
                "Open their inventory"));
        inv.setItem(16, AdminMenuHolder.icon(Material.ENDER_CHEST, "Ender chest",
                "Open their ender chest"));

        inv.setItem(19, AdminMenuHolder.icon(Material.GOLDEN_APPLE, NamedTextColor.GREEN, "Heal",
                "Full health + hunger"));
        inv.setItem(20, AdminMenuHolder.icon(Material.COOKED_BEEF, "Feed",
                "Restore hunger"));
        inv.setItem(21, AdminMenuHolder.icon(Material.LAVA_BUCKET, NamedTextColor.RED, "Clear inv",
                "Requires confirm click twice"));
        inv.setItem(23, AdminMenuHolder.icon(Material.EMERALD, "Promote",
                "/promote " + target.getName()));
        inv.setItem(24, AdminMenuHolder.icon(Material.REDSTONE, "Demote",
                "/demote " + target.getName()));
        if (player.hasPermission("yapadmin.give")) {
            inv.setItem(25, AdminMenuHolder.icon(Material.SHULKER_BOX, "Give items",
                    "Open give menu for this player"));
        }

        inv.setItem(28, AdminMenuHolder.icon(Material.IRON_BOOTS, NamedTextColor.RED, "Kick",
                "Immediate kick"));
        inv.setItem(29, AdminMenuHolder.icon(Material.PAPER, "Warn",
                "Staff warning"));
        inv.setItem(30, AdminMenuHolder.icon(Material.WRITABLE_BOOK, "Mute 1h",
                "Temp mute one hour"));
        inv.setItem(31, AdminMenuHolder.icon(Material.BARRIER, NamedTextColor.DARK_RED, "Tempban 1d",
                "Ban for one day"));

        inv.setItem(SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    public void openSelfTools(Player player) {
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuHolder.Kind.SELF_TOOLS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Self tools", NamedTextColor.AQUA));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(SLOT_INFO, AdminMenuHolder.icon(Material.FEATHER, "Self tools",
                "Toggles apply to you"));
        inv.setItem(19, AdminMenuHolder.icon(Material.ELYTRA, "Fly", "/fly"));
        inv.setItem(20, AdminMenuHolder.icon(Material.TOTEM_OF_UNDYING, "God", "/god"));
        inv.setItem(21, AdminMenuHolder.icon(Material.GLASS, "Vanish", "/vanish"));
        inv.setItem(22, AdminMenuHolder.icon(Material.GOLDEN_APPLE, "Heal", "Full heal"));
        inv.setItem(23, AdminMenuHolder.icon(Material.COOKED_BEEF, "Feed", "Full hunger"));
        inv.setItem(24, AdminMenuHolder.icon(Material.SPYGLASS, "Social spy", "/socialspy"));
        inv.setItem(25, AdminMenuHolder.icon(Material.ENDER_EYE, "Night vision", "Toggle NV 5m"));
        inv.setItem(SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    public void openGiveHub(Player player) {
        if (!player.hasPermission("yapadmin.give")) {
            player.sendMessage("§cNo permission.");
            return;
        }
        AdminSession session = plugin.session(player.getUniqueId());
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuHolder.Kind.GIVE_HUB);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Give", NamedTextColor.GREEN));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);

        String target = session.hasTarget() ? session.targetName() : player.getName() + " (self)";
        inv.setItem(SLOT_INFO, AdminMenuHolder.icon(Material.CHEST, "Give hub",
                "Target: " + target,
                "Amount chip: " + session.giveAmount()));
        inv.setItem(GIVE_PRESETS, AdminMenuHolder.icon(Material.DIAMOND, "Curated presets",
                "Common admin items"));
        if (plugin.actions().pluginEnabled("YaPPlayerData")) {
            inv.setItem(GIVE_KITS, AdminMenuHolder.icon(Material.BUNDLE, "Kits",
                    "starter · adventurer · vip"));
        }
        inv.setItem(GIVE_MATS, AdminMenuHolder.icon(Material.COMPASS, "Material browser",
                "Paginated vanilla items"));
        inv.setItem(GIVE_AMOUNT, AdminMenuHolder.icon(Material.HOPPER, "Amount: " + session.giveAmount(),
                "Click to cycle 1 → 16 → 64"));
        inv.setItem(GIVE_TARGET, AdminMenuHolder.icon(Material.PLAYER_HEAD, "Change target",
                "Pick online player"));
        inv.setItem(SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    public void openGivePresets(Player player) {
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuHolder.Kind.GIVE_PRESETS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Give presets", NamedTextColor.GREEN));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(SLOT_INFO, AdminMenuHolder.icon(Material.DIAMOND, "Presets",
                "Click to give · shift = stack×4"));
        inv.setItem(SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));

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

    public void openGiveKits(Player player) {
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuHolder.Kind.GIVE_KITS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Give kits", NamedTextColor.GREEN));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(SLOT_INFO, AdminMenuHolder.icon(Material.BUNDLE, "Kits",
                "Dispatches /kit give"));
        inv.setItem(SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        int slot = 19;
        for (String kit : plugin.adminConfig().kits()) {
            inv.setItem(slot++, AdminMenuHolder.icon(Material.CHEST, kit,
                    "/kit give <player> " + kit));
        }
        player.openInventory(inv);
    }

    public void openGiveMaterials(Player player) {
        AdminSession session = plugin.session(player.getUniqueId());
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuHolder.Kind.GIVE_MATERIALS);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Materials p" + (session.materialPage() + 1), NamedTextColor.GREEN));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);

        inv.setItem(CAT_ALL, AdminMenuHolder.icon(Material.NETHER_STAR, catLabel(session, AdminSession.MaterialCategory.ALL)));
        inv.setItem(CAT_BLOCKS, AdminMenuHolder.icon(Material.BRICKS, catLabel(session, AdminSession.MaterialCategory.BLOCKS)));
        inv.setItem(CAT_TOOLS, AdminMenuHolder.icon(Material.IRON_PICKAXE, catLabel(session, AdminSession.MaterialCategory.TOOLS)));
        inv.setItem(CAT_COMBAT, AdminMenuHolder.icon(Material.IRON_SWORD, catLabel(session, AdminSession.MaterialCategory.COMBAT)));
        inv.setItem(CAT_FOOD, AdminMenuHolder.icon(Material.BREAD, catLabel(session, AdminSession.MaterialCategory.FOOD)));
        inv.setItem(CAT_MISC, AdminMenuHolder.icon(Material.CHEST, catLabel(session, AdminSession.MaterialCategory.MISC)));

        List<Material> mats = filteredMaterials(session.category());
        int page = session.materialPage();
        int maxPage = Math.max(0, (mats.size() - 1) / PAGE_SIZE);
        if (page > maxPage) {
            session.setMaterialPage(maxPage);
            page = maxPage;
        }
        int start = page * PAGE_SIZE;
        int end = Math.min(mats.size(), start + PAGE_SIZE);
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

        inv.setItem(MAT_PREV, AdminMenuHolder.icon(Material.ARROW, "Previous page",
                "Page " + (page + 1) + " / " + (maxPage + 1)));
        inv.setItem(MAT_BACK, AdminMenuHolder.icon(Material.OAK_DOOR, "Back to Give"));
        inv.setItem(MAT_AMOUNT, AdminMenuHolder.icon(Material.HOPPER, "Amount: " + session.giveAmount(),
                "Click to cycle 1 → 16 → 64"));
        inv.setItem(MAT_NEXT, AdminMenuHolder.icon(Material.ARROW, "Next page",
                "Page " + (page + 1) + " / " + (maxPage + 1)));
        player.openInventory(inv);
    }

    public void openServerOps(Player player) {
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuHolder.Kind.SERVER_OPS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Server ops", NamedTextColor.GOLD));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);

        double[] tps;
        try {
            tps = Bukkit.getTPS();
        } catch (Throwable t) {
            tps = new double[0];
        }
        String tpsLine = tps.length > 0 ? String.format(Locale.ROOT, "%.2f", tps[0]) : "n/a";
        inv.setItem(SLOT_INFO, AdminMenuHolder.icon(Material.COMMAND_BLOCK, "Server status",
                "Online: " + Bukkit.getOnlinePlayers().size(),
                "Worlds: " + Bukkit.getWorlds().size(),
                "TPS (1m): " + tpsLine));

        List<String> presets = plugin.adminConfig().broadcastPresets();
        int slot = 19;
        for (int i = 0; i < presets.size() && slot < 26; i++) {
            String msg = presets.get(i);
            String shortMsg = msg.length() > 40 ? msg.substring(0, 37) + "…" : msg;
            inv.setItem(slot++, AdminMenuHolder.icon(Material.NOTE_BLOCK, "Broadcast #" + (i + 1),
                    shortMsg));
        }
        inv.setItem(28, AdminMenuHolder.icon(Material.BOOKSHELF, "Open Ranks GUI",
                "YaPPerms reload lives there"));
        inv.setItem(SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    public void openEconomy(Player player) {
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuHolder.Kind.ECONOMY);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Economy", NamedTextColor.GOLD));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        AdminSession session = plugin.session(player.getUniqueId());
        String target = session.hasTarget() ? session.targetName() : player.getName() + " (self)";
        inv.setItem(SLOT_INFO, AdminMenuHolder.icon(Material.GOLD_INGOT, "Grant money",
                "Target: " + target,
                "Uses /yapmmo givemoney"));
        int slot = 19;
        for (int amount : plugin.adminConfig().moneyAmounts()) {
            inv.setItem(slot++, AdminMenuHolder.icon(Material.EMERALD, "+" + amount,
                    "Give " + amount + " to " + target));
        }
        inv.setItem(SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    public void openDeepLinks(Player player) {
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuHolder.Kind.DEEP_LINKS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Deep links", NamedTextColor.AQUA));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(SLOT_INFO, AdminMenuHolder.icon(Material.COMPASS, "Open other GUIs",
                "Closes this menu and runs command"));
        if (plugin.actions().pluginEnabled("YaPPerms")) {
            inv.setItem(19, AdminMenuHolder.icon(Material.NAME_TAG, "Ranks", "/yapperm gui"));
        }
        if (plugin.actions().pluginEnabled("YaPWorld")) {
            inv.setItem(20, AdminMenuHolder.icon(Material.WOODEN_AXE, "World edit", "/yapworld gui"));
        }
        if (plugin.actions().pluginEnabled("YaPStacker")) {
            inv.setItem(21, AdminMenuHolder.icon(Material.SPAWNER, "Stacker", "/yapstacker gui"));
        }
        if (plugin.actions().pluginEnabled("YaPPlayerData")) {
            inv.setItem(22, AdminMenuHolder.icon(Material.COMPASS, "Player menu", "/menu"));
        }
        inv.setItem(SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    public void openCombatSkills(Player player) {
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuHolder.Kind.COMBAT_SKILLS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Combat / Skills", NamedTextColor.LIGHT_PURPLE));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(SLOT_INFO, AdminMenuHolder.icon(Material.BLAZE_ROD, "Combat & skills",
                "Shortcuts into MMO plugins"));
        if (plugin.actions().pluginEnabled("YaPSkills")) {
            inv.setItem(20, AdminMenuHolder.icon(Material.EXPERIENCE_BOTTLE, "Skills menu", "/skills"));
        }
        if (plugin.actions().pluginEnabled("YaPCombat")) {
            inv.setItem(22, AdminMenuHolder.icon(Material.GOLDEN_APPLE, "Heal self", "Combat HP restore"));
            inv.setItem(24, AdminMenuHolder.icon(Material.ENCHANTED_BOOK, "Prayer list", "/prayer list"));
        }
        inv.setItem(SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    private static String catLabel(AdminSession session, AdminSession.MaterialCategory cat) {
        String base = cat.name().charAt(0) + cat.name().substring(1).toLowerCase(Locale.ROOT);
        return session.category() == cat ? "▶ " + base : base;
    }

    private static List<Material> filteredMaterials(AdminSession.MaterialCategory category) {
        return Arrays.stream(Material.values())
                .filter(Material::isItem)
                .filter(m -> !m.isAir())
                .filter(m -> !m.name().startsWith("LEGACY_"))
                .filter(m -> matchesCategory(m, category))
                .sorted(Comparator.comparing(Enum::name))
                .toList();
    }

    private static boolean matchesCategory(Material m, AdminSession.MaterialCategory category) {
        return switch (category) {
            case ALL -> true;
            case BLOCKS -> m.isBlock() && !AdminActions.isTool(m) && !AdminActions.isCombat(m);
            case TOOLS -> AdminActions.isTool(m);
            case COMBAT -> AdminActions.isCombat(m);
            case FOOD -> m.isEdible();
            case MISC -> !m.isBlock() && !AdminActions.isTool(m) && !AdminActions.isCombat(m) && !m.isEdible();
        };
    }

    private static ItemStack playerHead(Player player, String title) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> {
            meta.setOwningPlayer(player);
            meta.displayName(Component.text(title).color(NamedTextColor.AQUA)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        });
        return head;
    }
}
