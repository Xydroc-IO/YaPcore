package com.yapcore.staff.screen;

import com.yapcore.staff.StaffCmds;
import com.yapcore.staff.YapStaffClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Searchable material browser + kits/presets — give via /yapadmin give. */
public final class GiveScreen extends StaffPanelScreen {

    private static final int PAGE_SIZE = 16;
    private static final String[] PRESETS = {
            "diamond", "diamond_sword", "diamond_pickaxe", "netherite_ingot",
            "golden_apple", "enchanted_golden_apple", "ender_pearl", "totem_of_undying",
            "elytra", "firework_rocket", "arrow", "bow", "shield", "oak_log",
            "cobblestone", "bread"
    };
    private static final String[] KITS = {"starter", "adventurer", "vip"};

    public GiveScreen(Screen parent) {
        super(Component.literal("Give / spawn"), parent);
    }

    @Override
    protected void addContents() {
        var session = YapStaffClient.session();
        String target = session.hasTarget() ? session.targetName() : "(self)";

        LinearLayout top = LinearLayout.vertical().spacing(4);
        top.addChild(new StringWidget(Component.literal(
                "Target: " + target + "  ·  Amount: " + session.giveAmount() + "  ·  yapadmin.give"), this.font));

        LinearLayout controls = LinearLayout.horizontal().spacing(6);
        controls.addChild(Button.builder(Component.literal("Amount ×" + session.giveAmount()), b -> {
            session.cycleGiveAmount();
            rebuildWidgets();
        }).width(110).build());
        controls.addChild(Button.builder(Component.literal("Pick target"), b ->
                open(new PlayersScreen(this, true))).width(100).build());
        controls.addChild(Button.builder(Component.literal("Clear target"), b -> {
            session.clearTarget();
            rebuildWidgets();
        }).width(100).build());
        top.addChild(controls);

        EditBox search = new EditBox(this.font, 240, 20, Component.literal("Search items"));
        search.setValue(session.giveFilter());
        search.setHint(Component.literal("Search materials…"));
        search.setResponder(text -> {
            session.setGiveFilter(text);
            rebuildWidgets();
        });
        top.addChild(search);
        addBody(top);

        // Presets
        addSubtitle("Curated presets");
        GridLayout presets = new GridLayout().columnSpacing(4).rowSpacing(3);
        GridLayout.RowHelper presetRows = presets.createRowHelper(4);
        for (String id : PRESETS) {
            presetRows.addChild(Button.builder(Component.literal(pretty(id)), b -> give(id))
                    .width(110).build());
        }
        addBody(presets);

        // Kits
        addSubtitle("Kits (/kit give)");
        LinearLayout kits = LinearLayout.horizontal().spacing(6);
        for (String kit : KITS) {
            kits.addChild(Button.builder(Component.literal(kit), b -> {
                String who = session.hasTarget() ? session.targetName() : null;
                if (who == null && minecraft != null && minecraft.player != null) {
                    who = minecraft.player.getGameProfile().name();
                }
                if (who != null) {
                    StaffCmds.runFmt("kit give %s %s", who, kit);
                }
            }).width(100).build());
        }
        addBody(kits);

        // Full browser
        List<String> ids = filteredItems(session.giveFilter());
        int maxPage = Math.max(0, (ids.size() - 1) / PAGE_SIZE);
        if (session.givePage() > maxPage) {
            session.setGivePage(maxPage);
        }
        int page = session.givePage();
        int start = page * PAGE_SIZE;
        int end = Math.min(ids.size(), start + PAGE_SIZE);

        addSubtitle("All items — page " + (page + 1) + "/" + (maxPage + 1) + " (" + ids.size() + ")");
        GridLayout grid = new GridLayout().columnSpacing(4).rowSpacing(3);
        GridLayout.RowHelper rows = grid.createRowHelper(4);
        for (int i = start; i < end; i++) {
            String id = ids.get(i);
            rows.addChild(Button.builder(Component.literal(pretty(id)), b -> give(id)).width(110).build());
        }
        addBody(grid);

        LinearLayout nav = LinearLayout.horizontal().spacing(8);
        nav.addChild(Button.builder(Component.literal("◀ Prev"), b -> {
            session.setGivePage(page - 1);
            rebuildWidgets();
        }).width(80).build());
        nav.addChild(Button.builder(Component.literal("Next ▶"), b -> {
            session.setGivePage(page + 1);
            rebuildWidgets();
        }).width(80).build());
        addBody(nav);
    }

    private void give(String materialId) {
        var session = YapStaffClient.session();
        int amount = session.giveAmount();
        if (session.hasTarget()) {
            StaffCmds.runFmt("yapadmin give %s %d %s", materialId, amount, session.targetName());
        } else {
            StaffCmds.runFmt("yapadmin give %s %d", materialId, amount);
        }
    }

    private static List<String> filteredItems(String filter) {
        String needle = filter == null ? "" : filter.toLowerCase(Locale.ROOT).trim();
        List<String> out = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) {
                continue;
            }
            Identifier key = BuiltInRegistries.ITEM.getKey(item);
            if (key == null) {
                continue;
            }
            String path = key.getPath();
            if (!needle.isEmpty() && !path.contains(needle) && !path.replace('_', ' ').contains(needle)) {
                continue;
            }
            out.add(path);
        }
        out.sort(Comparator.naturalOrder());
        return out;
    }

    private static String pretty(String id) {
        String raw = id.replace('_', ' ');
        if (raw.length() <= 18) {
            return raw;
        }
        return raw.substring(0, 16) + "…";
    }
}
