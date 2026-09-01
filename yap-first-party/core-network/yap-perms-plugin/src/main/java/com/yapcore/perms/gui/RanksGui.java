package com.yapcore.perms.gui;

import com.yapcore.perms.EffectiveUser;
import com.yapcore.perms.PermsPlugin;
import com.yapcore.perms.db.PermsRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** In-game ranks / permissions control panel. */
public final class RanksGui {

    static final int SLOT_SELF = 4;
    static final int SLOT_GROUPS = 20;
    static final int SLOT_PLAYERS = 22;
    static final int SLOT_PROMOTE = 24;
    static final int SLOT_APPLY_PACK = 30;
    static final int SLOT_RELOAD = 32;
    static final int SLOT_CLOSE = 40;

    private final PermsPlugin plugin;

    public RanksGui(PermsPlugin plugin) {
        this.plugin = plugin;
    }

    public void openHub(Player player) {
        if (!player.hasPermission("yapperm.admin") && !player.hasPermission("yapperm.promote")
                && !player.hasPermission("yapperm.user")) {
            player.sendMessage("§cNo permission.");
            return;
        }
        RanksGuiHolder holder = new RanksGuiHolder(RanksGuiHolder.Kind.HUB);
        Inventory inv = Bukkit.createInventory(holder, 45, Component.text("YaP Ranks", NamedTextColor.GOLD));
        holder.bind(inv);
        RanksGuiHolder.fillAll(inv);

        EffectiveUser self = plugin.resolve(player.getUniqueId(), player.getName());
        inv.setItem(SLOT_SELF, RanksGuiHolder.icon(Material.NAME_TAG, NamedTextColor.AQUA, "You",
                "Group: " + self.primaryGroup(),
                "Weight: " + self.weight(),
                "Prefix: " + strip(self.prefix())));

        inv.setItem(SLOT_GROUPS, RanksGuiHolder.icon(Material.BOOKSHELF, "View groups",
                "Browse rank ladder and nodes"));
        inv.setItem(SLOT_PLAYERS, RanksGuiHolder.icon(Material.PLAYER_HEAD, "Online players",
                "Click a player to set their group",
                player.hasPermission("yapperm.admin") ? "Ready" : "Needs yapperm.admin"));
        inv.setItem(SLOT_PROMOTE, RanksGuiHolder.icon(Material.EMERALD, "Promote / demote",
                "/promote <player> · /demote <player>",
                "Or pick a player above"));
        if (player.hasPermission("yapperm.admin")) {
            inv.setItem(SLOT_APPLY_PACK, RanksGuiHolder.icon(Material.CHEST, "Apply starter pack",
                    "default → vip → mod → admin"));
            inv.setItem(SLOT_RELOAD, RanksGuiHolder.icon(Material.COMPARATOR, "Reload YaPPerms"));
        }
        inv.setItem(SLOT_CLOSE, RanksGuiHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    public void openGroups(Player player) {
        RanksGuiHolder holder = new RanksGuiHolder(RanksGuiHolder.Kind.GROUPS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Rank groups", NamedTextColor.YELLOW));
        holder.bind(inv);
        RanksGuiHolder.fillAll(inv);
        inv.setItem(4, RanksGuiHolder.icon(Material.BOOK, "Groups", "Click a group for details"));
        inv.setItem(45, RanksGuiHolder.icon(Material.ARROW, "Back"));

        List<PermsRepository.GroupRow> groups = new ArrayList<>(plugin.resolver().groups().values());
        groups.sort(Comparator.comparingInt(PermsRepository.GroupRow::weight));
        int slot = 9;
        for (PermsRepository.GroupRow group : groups) {
            if (slot >= 44) {
                break;
            }
            inv.setItem(slot++, RanksGuiHolder.icon(iconForGroup(group.name()), NamedTextColor.GREEN, group.name(),
                    "Weight: " + group.weight(),
                    "Prefix: " + strip(group.prefix()),
                    "Parents: " + (group.parents().isEmpty() ? "—" : String.join(", ", group.parents())),
                    "Nodes: " + group.nodes().size()));
        }
        player.openInventory(inv);
    }

    public void openOnlinePlayers(Player player) {
        if (!player.hasPermission("yapperm.admin")) {
            player.sendMessage("§cNo permission.");
            return;
        }
        RanksGuiHolder holder = new RanksGuiHolder(RanksGuiHolder.Kind.ONLINE_PLAYERS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Set player group", NamedTextColor.AQUA));
        holder.bind(inv);
        RanksGuiHolder.fillAll(inv);
        inv.setItem(4, RanksGuiHolder.icon(Material.PLAYER_HEAD, "Online players", "Click to choose a rank"));
        inv.setItem(45, RanksGuiHolder.icon(Material.ARROW, "Back"));

        int slot = 9;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (slot >= 44) {
                break;
            }
            EffectiveUser eff = plugin.resolve(online.getUniqueId(), online.getName());
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            head.editMeta(SkullMeta.class, meta -> {
                meta.setOwningPlayer(online);
                meta.displayName(Component.text(online.getName()).color(NamedTextColor.YELLOW));
                meta.lore(List.of(
                        Component.text("Group: " + eff.primaryGroup()).color(NamedTextColor.GRAY),
                        Component.text("Click to change group").color(NamedTextColor.DARK_GRAY)));
            });
            inv.setItem(slot++, head);
        }
        player.openInventory(inv);
    }

    public void openPickGroup(Player admin, UUID targetUuid, String targetName) {
        RanksGuiHolder holder = new RanksGuiHolder(RanksGuiHolder.Kind.PICK_GROUP, targetUuid, targetName);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Group → " + targetName, NamedTextColor.GREEN));
        holder.bind(inv);
        RanksGuiHolder.fillAll(inv);
        EffectiveUser eff = plugin.resolve(targetUuid, targetName);
        inv.setItem(4, RanksGuiHolder.icon(Material.NAME_TAG, targetName,
                "Current: " + eff.primaryGroup()));
        inv.setItem(45, RanksGuiHolder.icon(Material.ARROW, "Back"));

        List<PermsRepository.GroupRow> groups = new ArrayList<>(plugin.resolver().groups().values());
        groups.sort(Comparator.comparingInt(PermsRepository.GroupRow::weight));
        int slot = 9;
        for (PermsRepository.GroupRow group : groups) {
            if (slot >= 44) {
                break;
            }
            boolean current = group.name().equalsIgnoreCase(eff.primaryGroup());
            inv.setItem(slot++, RanksGuiHolder.icon(
                    iconForGroup(group.name()),
                    current ? NamedTextColor.GOLD : NamedTextColor.YELLOW,
                    group.name(),
                    "Weight: " + group.weight(),
                    current ? "Current group" : "Click to assign"));
        }
        playerOpen(admin, inv);
    }

    private static void playerOpen(Player player, Inventory inv) {
        player.openInventory(inv);
    }

    private static Material iconForGroup(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "admin", "owner" -> Material.NETHERITE_HELMET;
            case "mod", "moderator", "staff" -> Material.IRON_HELMET;
            case "vip", "mvp", "premium" -> Material.GOLDEN_HELMET;
            default -> Material.LEATHER_HELMET;
        };
    }

    private static String strip(String colored) {
        if (colored == null || colored.isBlank()) {
            return "—";
        }
        return colored.replaceAll("§[0-9a-fk-or]", "");
    }
}
