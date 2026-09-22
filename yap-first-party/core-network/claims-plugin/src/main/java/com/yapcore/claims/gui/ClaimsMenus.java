package com.yapcore.claims.gui;

import com.yapcore.claims.Claim;
import com.yapcore.claims.ClaimService;
import com.yapcore.claims.ClaimsConfig;
import com.yapcore.claims.cmd.Perms;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Minimal chest GUI for owned claims. */
public final class ClaimsMenus {

    final JavaPlugin plugin;
    final ClaimsConfig config;
    final ClaimService claims;
    final Map<UUID, Map<Integer, String>> clickMeta = new HashMap<>();

    public ClaimsMenus(JavaPlugin plugin, ClaimsConfig config, ClaimService claims) {
        this.plugin = plugin;
        this.config = config;
        this.claims = claims;
    }

    public void openClaims(Player player) {
        if (!config.claimsEnabled() || claims == null) {
            player.sendMessage("§cClaims are disabled.");
            return;
        }
        if (!Perms.require(player, "yapdata.claim")) {
            return;
        }
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Claims", NamedTextColor.GREEN));
        holder.bind(inv);
        fillBorder(inv);
        Map<Integer, String> meta = new HashMap<>();
        try {
            int owned = 0;
            for (Claim c : claims.repo().listOwned(player.getUniqueId())) {
                if (!c.isSubdivision()
                        && c.serverId() != null
                        && c.serverId().equalsIgnoreCase(config.serverId())) {
                    owned++;
                }
            }
            String modeTip = config.claimsMode() == ClaimsConfig.ClaimMode.CHUNK
                    ? "Shovel: claim this " + config.claimsPlotSize() + "×" + config.claimsPlotSize() + " plot"
                    : "Shovel: two corners";
            String costTip = config.claimsCostEnabled()
                    ? "Cost: $" + String.format("%.0f", config.claimsCostAmount()) + " each"
                    : "Free to claim";
            String vertTip = "Y: −" + config.claimsPlotDepth() + " depth → max height";
            inv.setItem(4, icon(Material.GOLDEN_SHOVEL, "Your claims",
                    owned + " / " + config.claimsMaxClaims() + " slots",
                    modeTip,
                    costTip,
                    vertTip,
                    "Inspect: stick · /claim claim"));
            int slot = 10;
            for (Claim c : claims.repo().listOwned(player.getUniqueId())) {
                while (slot % 9 == 0 || slot % 9 == 8) {
                    slot++;
                }
                if (slot >= 44) {
                    break;
                }
                inv.setItem(slot, icon(
                        c.isSubdivision() ? Material.OAK_FENCE : Material.GRASS_BLOCK,
                        (c.isSubdivision() ? "Sub #" : "#") + c.id() + " " + c.name(),
                        "Server: " + c.serverId(),
                        "Area: " + c.area()
                                + (c.isSubdivision() ? " · parent #" + c.parentId() : ""),
                        c.isSubdivision() ? "Subdivision" : ("Tax: $" + String.format("%.2f", c.taxDue())
                                + (c.taxFrozen() ? " FROZEN" : "")),
                        "Click: visualize · Shift: abandon"));
                meta.put(slot, String.valueOf(c.id()));
                slot++;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "claims gui", e);
        }
        inv.setItem(49, icon(Material.BARRIER, NamedTextColor.RED, "Close"));
        clickMeta.put(player.getUniqueId(), meta);
        player.openInventory(inv);
    }

    static ItemStack icon(Material mat, String name, String... lore) {
        return icon(mat, NamedTextColor.YELLOW, name, lore);
    }

    static ItemStack icon(Material mat, NamedTextColor color, String name, String... lore) {
        ItemStack stack = new ItemStack(mat);
        stack.editMeta(meta -> {
            meta.displayName(Component.text(name).color(color).decoration(TextDecoration.ITALIC, false));
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(Component.text(line).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lines);
        });
        return stack;
    }

    static void fillBorder(Inventory inv) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        pane.editMeta(meta -> meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false)));
        int size = inv.getSize();
        for (int i = 0; i < size; i++) {
            int row = i / 9;
            int col = i % 9;
            if (row == 0 || row == size / 9 - 1 || col == 0 || col == 8) {
                inv.setItem(i, pane);
            }
        }
    }

    public static final class Holder implements InventoryHolder {
        private Inventory inventory;

        void bind(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
