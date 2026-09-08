package com.yapcore.admin.gui;

import com.yapcore.admin.AdminPlugin;
import com.yapcore.admin.session.AdminSession;
import com.yapcore.admin.session.ItemCreateAbilitySlot;
import com.yapcore.admin.session.ItemCreateDraft;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

/** Custom item create wizard menus. */
final class AdminMenusCustomItemsWizard {

    private final AdminPlugin plugin;

    AdminMenusCustomItemsWizard(AdminPlugin plugin) {
        this.plugin = plugin;
    }

    void openCustomItemsCreate(Player player) {
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.CUSTOM_ITEMS_CREATE);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Create — category", NamedTextColor.GOLD));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuHolder.icon(Material.ANVIL, "Pick a category",
                "Then choose the exact base item"));
        inv.setItem(19, AdminMenuHolder.icon(Material.NETHERITE_SWORD, "Weapons",
                "Sword · axe · spear · mace · bow…"));
        inv.setItem(21, AdminMenuHolder.icon(Material.NETHERITE_PICKAXE, "Tools",
                "Pickaxe · shovel · hoe · shears · rod"));
        inv.setItem(23, AdminMenuHolder.icon(Material.AMETHYST_SHARD, "Gems / charms",
                "Amethyst · emerald · diamond · totem…"));
        inv.setItem(25, AdminMenuHolder.icon(Material.CHEST, "Props (placeable)",
                "Pick the look: oak · stone · lantern…"));
        inv.setItem(31, AdminMenuHolder.icon(Material.TRIPWIRE_HOOK, "Other", "Key"));
        inv.setItem(AdminMenuSlots.SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    void openCustomItemsCreateBase(Player player) {
        AdminSession session = plugin.session(player.getUniqueId());
        String group = session.createTemplateGroup();
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.CUSTOM_ITEMS_CREATE_BASE);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Base: " + ItemTemplateCatalog.groupTitle(group), NamedTextColor.GOLD));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuHolder.icon(Material.BOOK, ItemTemplateCatalog.groupTitle(group),
                "Click the exact base item",
                group.equals("prop") ? "These are placeable furniture looks" : "Then configure name / abilities"));
        int slot = 10;
        for (ItemTemplateCatalog.Entry e : ItemTemplateCatalog.byGroup(group)) {
            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            if (slot >= 44) {
                break;
            }
            inv.setItem(slot++, AdminMenuHolder.icon(e.icon(), e.label(),
                    e.furniture() ? "Placeable prop" : "Held / use item",
                    "id: " + e.id()));
        }
        inv.setItem(AdminMenuSlots.SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    void openCustomItemsCreateBuild(Player player) {
        AdminSession session = plugin.session(player.getUniqueId());
        ItemCreateDraft d = session.itemCreate();
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.CUSTOM_ITEMS_CREATE_BUILD);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Build item", NamedTextColor.GOLD));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuHolder.icon(Material.BOOK, "Draft",
                "Name: " + AdminMenuItemSupport.plain(d.displayName()),
                "Id: " + d.id(),
                "Type: " + ItemTemplateCatalog.label(d.template()) + " · Abilities: " + d.abilitiesLabel(),
                "Dmg " + d.damageLabel() + " · Range " + AdminMenuItemSupport.trim(d.range()) + " · CD " + d.cooldown(),
                "Gear ATK " + d.gearAttack() + " · STR " + d.gearStrength(),
                (d.glow() ? "Glow " : "No glow ") + "· " + (d.unbreakable() ? "Unbreakable" : "Breakable"),
                "Enchants: " + d.enchantsLabel()));
        inv.setItem(12, AdminMenuHolder.icon(
                d.glow() ? Material.GLOWSTONE_DUST : Material.GUNPOWDER,
                d.glow() ? "Glow: ON" : "Glow: OFF",
                "Enchantment shine on the item",
                "Click to toggle"));
        inv.setItem(13, AdminMenuHolder.icon(
                d.unbreakable() ? Material.BEDROCK : Material.IRON_INGOT,
                d.unbreakable() ? "Unbreakable: ON" : "Unbreakable: OFF",
                "Item never loses durability",
                "Click to toggle"));
        inv.setItem(14, AdminMenuHolder.icon(Material.ENCHANTED_BOOK,
                "Enchants: " + d.enchants().size(),
                d.enchantsLabel(),
                "Click to pick Sharpness, Unbreaking, …"));
        inv.setItem(19, AdminMenuHolder.icon(Material.NAME_TAG, "Set display name…",
                "Type in chat (supports & color codes)",
                "Current: " + AdminMenuItemSupport.plain(d.displayName())));
        inv.setItem(20, AdminMenuHolder.icon(Material.PAPER, "Set id…",
                "Internal id [a-z0-9_]",
                "Current: " + d.id()));
        inv.setItem(21, AdminMenuHolder.icon(Material.NETHER_STAR, "Abilities: " + d.abilities().size(),
                d.abilitiesLabel(),
                "Click to add/remove abilities"));
        inv.setItem(25, AdminMenuHolder.icon(Material.TRIPWIRE_HOOK, "Keybinds / Together",
                "Per-ability: Together (same key) or separate key",
                "Together = all fire at once on primary key"));
        if (d.usesDamage()) {
            inv.setItem(22, AdminMenuHolder.icon(Material.IRON_SWORD, "Damage: " + d.damageLabel(),
                    "Click to cycle — includes INSTAKILL"));
        }
        if (d.usesRange() && !d.usesBreakVolume()) {
            inv.setItem(23, AdminMenuHolder.icon(Material.ENDER_PEARL, "Range: " + AdminMenuItemSupport.trim(d.range()),
                    "Click to cycle (up to 100 blocks)"));
        }
        inv.setItem(24, AdminMenuHolder.icon(Material.CLOCK, "Cooldown: " + d.cooldown(),
                "Click to cycle"));
        if (d.usesBreakVolume()) {
            inv.setItem(23, AdminMenuHolder.icon(Material.ENDER_PEARL, "Break reach: " + AdminMenuItemSupport.trim(d.range()),
                    "How far you can look to break"));
            inv.setItem(28, AdminMenuHolder.icon(Material.COBBLESTONE, "Break blast: " + d.breakRadius(),
                    "0 = single block · 1–3 = cube around target"));
            inv.setItem(34, AdminMenuHolder.icon(Material.DIAMOND_PICKAXE, "Max blocks: " + d.breakCount(),
                    "Cap how many blocks one use breaks"));
        }
        if (d.usesRadius() && !d.usesBreakVolume()) {
            inv.setItem(28, AdminMenuHolder.icon(Material.TARGET, "AoE radius: " + AdminMenuItemSupport.trim(d.radius()),
                    "Enemy potion / stomp / pull / push"));
        }
        if (d.usesPotion()) {
            inv.setItem(31, AdminMenuHolder.icon(Material.POTION, "Potion: " + d.potionEffect(),
                    d.abilities().contains("effect") && !d.abilities().contains("area_effect")
                            ? "Self potion only"
                            : d.abilities().contains("area_effect") && !d.abilities().contains("effect")
                            ? "Enemy AoE potion"
                            : "Self and/or enemy AoE"));
        }
        if (d.usesPotionPower()) {
            inv.setItem(37, AdminMenuHolder.icon(Material.CLOCK, "Potion time: " + d.potionDurationSec() + "s",
                    "Duration of potion effects"));
            inv.setItem(38, AdminMenuHolder.icon(Material.GLOWSTONE_DUST, "Potion level: " + (d.potionAmplifier() + 1),
                    "Amplifier 0 = I · 1 = II · 2 = III"));
        }
        if (d.usesProjectile()) {
            inv.setItem(32, AdminMenuHolder.icon(Material.SNOWBALL, "Projectile: " + d.projectileKind(),
                    "snowball / arrow / egg / ender_pearl / fireball"));
        }
        if (d.usesHeal() && !d.usesBreakVolume()) {
            inv.setItem(39, AdminMenuHolder.icon(Material.GOLDEN_APPLE, "Heal HP: " + AdminMenuItemSupport.trim(d.healAmount()),
                    "Hearts restored by Heal self"));
        }
        inv.setItem(29, AdminMenuHolder.icon(Material.DIAMOND_SWORD, "Gear attack: +" + d.gearAttack(),
                "Click to cycle (melee bonus)"));
        inv.setItem(30, AdminMenuHolder.icon(Material.BLAZE_POWDER, "Gear strength: +" + d.gearStrength(),
                "Click to cycle"));
        inv.setItem(33, AdminMenuHolder.icon(Material.LIME_CONCRETE,
                d.replaceExisting() ? "SAVE CHANGES" : "CREATE ITEM",
                d.replaceExisting() ? "Overwrites items/custom/" + d.id() + ".yml" : "Writes the item and gives you one",
                AdminMenuItemSupport.plain(d.displayName()) + " (" + d.id() + ")"));
        inv.setItem(AdminMenuSlots.SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    void openCustomItemsCreateAbility(Player player) {
        AdminSession session = plugin.session(player.getUniqueId());
        ItemCreateDraft d = session.itemCreate();
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.CUSTOM_ITEMS_CREATE_ABILITY);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Abilities", NamedTextColor.LIGHT_PURPLE));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        String group = d.itemGroup();
        inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuHolder.icon(Material.NETHER_STAR,
                d.showAllAbilities() ? "All abilities" : ("Suited to " + ItemTemplateCatalog.groupTitle(group)),
                "Selected: " + d.abilitiesLabel(),
                "Click filter button to toggle"));
        inv.setItem(8, AdminMenuHolder.icon(
                d.showAllAbilities() ? Material.ENDER_EYE : Material.SPYGLASS,
                d.showAllAbilities() ? "Show suited only" : "Show all abilities",
                "Currently: " + (d.showAllAbilities() ? "ALL" : ItemTemplateCatalog.groupTitle(group))));
        inv.setItem(10, AdminMenuHolder.icon(Material.BARRIER,
                d.abilities().isEmpty() ? "▶ No ability" : "Clear abilities",
                "Remove all selected abilities"));
        int slot = 11;
        String lastCat = "";
        java.util.List<AbilityCatalog.Info> ordered = new java.util.ArrayList<>();
        for (String cat : AbilityCatalog.categoryOrder()) {
            for (AbilityCatalog.Info info : (d.showAllAbilities()
                    ? AbilityCatalog.all()
                    : AbilityCatalog.forItemGroup(group))) {
                if (info.category().equals(cat)) {
                    ordered.add(info);
                }
            }
        }
        for (AbilityCatalog.Info info : ordered) {
            if (!info.category().equals(lastCat)) {
                lastCat = info.category();
                while (slot % 9 == 0 || slot % 9 == 8) {
                    slot++;
                }
                if (slot >= 44) {
                    break;
                }
                inv.setItem(slot++, AdminMenuHolder.icon(Material.GRAY_STAINED_GLASS_PANE,
                        AbilityCatalog.categoryTitle(info.category()), "Category"));
            }
            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            if (slot >= 44) {
                break;
            }
            boolean sel = d.hasAbility(info.id());
            inv.setItem(slot++, AdminMenuHolder.icon(
                    sel ? Material.LIME_DYE : Material.LIGHT_GRAY_DYE,
                    (sel ? "▶ " : "") + info.label(),
                    info.description(),
                    sel ? "Selected — click to remove" : "Click to add"));
        }
        inv.setItem(AdminMenuSlots.SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    void openCustomItemsCreateEnchants(Player player) {
        AdminSession session = plugin.session(player.getUniqueId());
        ItemCreateDraft d = session.itemCreate();
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.CUSTOM_ITEMS_CREATE_ENCHANTS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Enchantments", NamedTextColor.AQUA));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuHolder.icon(Material.ENCHANTED_BOOK, "Enchantments",
                "Selected: " + d.enchantsLabel(),
                "Click to cycle level · suited to " + ItemTemplateCatalog.groupTitle(d.itemGroup())));
        inv.setItem(10, AdminMenuHolder.icon(Material.BARRIER,
                d.enchants().isEmpty() ? "▶ No enchants" : "Clear enchants",
                "Remove all enchantments"));
        int slot = 11;
        for (EnchantCatalog.Info info : EnchantCatalog.forItemGroup(d.itemGroup())) {
            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            if (slot >= 44) {
                break;
            }
            int lvl = d.enchantLevel(info.id());
            String title = lvl > 0
                    ? "▶ " + EnchantCatalog.labelWithLevel(info.id(), lvl)
                    : info.label();
            inv.setItem(slot++, AdminMenuHolder.icon(
                    lvl > 0 ? Material.ENCHANTED_BOOK : Material.BOOK,
                    title,
                    "Max " + EnchantCatalog.roman(info.maxLevel()),
                    "Click to cycle"));
        }
        inv.setItem(AdminMenuSlots.SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }

    void openCustomItemsCreateTriggers(Player player) {
        AdminSession session = plugin.session(player.getUniqueId());
        ItemCreateDraft d = session.itemCreate();
        AdminMenuHolder holder = new AdminMenuHolder(AdminMenuKind.CUSTOM_ITEMS_CREATE_TRIGGERS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Ability keybinds", NamedTextColor.YELLOW));
        holder.bind(inv);
        AdminMenuHolder.fillAll(inv);
        inv.setItem(AdminMenuSlots.SLOT_INFO, AdminMenuHolder.icon(Material.TRIPWIRE_HOOK, "Keybinds",
                "Click an ability to cycle its key",
                "Together = fires with the primary key at once"));
        List<ItemCreateAbilitySlot> slots = d.abilitySlots();
        int slot = 10;
        for (int i = 0; i < slots.size(); i++) {
            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            if (slot >= 44) {
                break;
            }
            ItemCreateAbilitySlot ab = slots.get(i);
            inv.setItem(slot++, AdminMenuHolder.icon(Material.NAME_TAG,
                    AbilityCatalog.label(ab.type()) + " → " + ab.triggerLabel(),
                    "Click to cycle keybind",
                    "Together / RMB / Sneak+RMB / LMB / Q / F / Attack"));
        }
        if (slots.isEmpty()) {
            inv.setItem(22, AdminMenuHolder.icon(Material.BARRIER, "No abilities yet",
                    "Add abilities first"));
        }
        inv.setItem(AdminMenuSlots.SLOT_BACK, AdminMenuHolder.icon(Material.ARROW, "Back"));
        inv.setItem(AdminMenuSlots.SLOT_CLOSE, AdminMenuHolder.icon(Material.DARK_OAK_DOOR, "Close"));
        player.openInventory(inv);
    }
}
