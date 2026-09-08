package com.yapcore.staff.screen;

import com.yapcore.staff.StaffCmds;
import com.yapcore.staff.YapStaffClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Browse / give / create YaPItems via /yapitems. */
public final class ItemsAdminScreen extends StaffPanelScreen {

    private static final String[] BUILTIN = {
            "stormblade", "frostbane", "emberpick", "prism_charm",
            "vault_key", "candy_cleaver", "ward_totem", "stone_pedestal"
    };
    private static final int PAGE_SIZE = 12;

    public ItemsAdminScreen(Screen parent) {
        super(Component.literal("Custom items"), parent);
        // Pull full registry into the browse list whenever this screen opens.
        StaffCmds.run("yapitems list --ids");
    }

    /** Called when the chat mixin receives synced item ids. */
    public void rebuildFromSync() {
        if (this.minecraft != null && this.minecraft.gui.screen() == this) {
            rebuildWidgets();
        }
    }

    @Override
    protected void addContents() {
        var session = YapStaffClient.session();
        String target = session.hasTarget() ? "Player: " + session.targetName() : "Giving to yourself";

        LinearLayout top = LinearLayout.vertical().spacing(4);
        top.addChild(new StringWidget(Component.literal(
                target + "  ·  Amount ×" + session.giveAmount()), this.font));

        // Wrap via grid — a single row of 5 fixed buttons overflows windowed GUI scale.
        int cCols = preferredColumns(3);
        int cw = colWidthFor(cCols);
        GridLayout controlGrid = new GridLayout().columnSpacing(4).rowSpacing(3);
        GridLayout.RowHelper cRows = controlGrid.createRowHelper(cCols);
        cRows.addChild(Button.builder(Component.literal("Amount ×" + session.giveAmount()), b -> {
            session.cycleGiveAmount();
            rebuildWidgets();
        }).width(cw).build());
        cRows.addChild(Button.builder(Component.literal("Select player…"), b ->
                open(new PlayersScreen(this, true))).width(cw).build());
        cRows.addChild(Button.builder(Component.literal("Reload"), b ->
                StaffCmds.run("yapitems reload")).width(cw).build());
        cRows.addChild(Button.builder(Component.literal("Sync list"), b ->
                StaffCmds.run("yapitems list --ids")).width(cw).build());
        cRows.addChild(Button.builder(Component.literal("Server GUI"), b -> {
            closeToGame();
            StaffCmds.run("yapitems gui");
        }).width(cw).build());
        top.addChild(controlGrid);
        top.addChild(new StringWidget(Component.literal(
                "Created items appear here after Sync list (or automatically on create)"), this.font));

        EditBox search = editBox(20, Component.literal("Filter"));
        search.setValue(session.giveFilter());
        search.setHint(Component.literal("Filter item ids…"));
        search.setResponder(text -> {
            session.setGiveFilter(text);
            rebuildWidgets();
        });
        top.addChild(search);

        LinearLayout giveRow = LinearLayout.horizontal().spacing(6);
        EditBox idBox = editBox(Math.max(100, panelWidth() - 100), 20, Component.literal("Item id"));
        idBox.setValue(session.itemsIdDraft());
        idBox.setHint(Component.literal("Give by id…"));
        idBox.setResponder(session::setItemsIdDraft);
        giveRow.addChild(idBox);
        giveRow.addChild(Button.builder(Component.literal("Give id"), b -> {
            String id = session.itemsIdDraft();
            if (!id.isBlank()) {
                session.rememberCustomItem(id);
                give(id);
            }
        }).width(Math.min(80, Math.max(56, panelWidth() / 6))).build());
        top.addChild(giveRow);

        top.addChild(Button.builder(Component.literal("Set ability cooldown…"), b -> {
            String id = session.itemsIdDraft();
            open(new ItemsCooldownScreen(this, id));
        }).width(wideWidth()).build());
        top.addChild(Button.builder(Component.literal("Create custom item…"), b ->
                open(new ItemsCreateScreen(this))).width(wideWidth()).build());
        top.addChild(new StringWidget(Component.literal(
                "Builder: name · ability · damage · range · cooldown · gear"), this.font));
        addBody(top);

        addSection("Quick bases (opens builder)");
        int qCols = preferredColumns(3);
        int qw = colWidthFor(qCols);
        GridLayout create = new GridLayout().columnSpacing(4).rowSpacing(3);
        GridLayout.RowHelper createRows = create.createRowHelper(qCols);
        addQuick(createRows, session, "Sword…", "sword", qw);
        addQuick(createRows, session, "Axe…", "axe", qw);
        addQuick(createRows, session, "Pickaxe…", "pickaxe", qw);
        addQuick(createRows, session, "Shovel…", "shovel", qw);
        addQuick(createRows, session, "Bow…", "bow", qw);
        addQuick(createRows, session, "Amethyst…", "amethyst", qw);
        addQuick(createRows, session, "Emerald…", "emerald", qw);
        addQuick(createRows, session, "Nether star…", "nether_star", qw);
        addQuick(createRows, session, "Prop oak…", "prop_oak", qw);
        addQuick(createRows, session, "Prop chest…", "prop_chest", qw);
        addQuick(createRows, session, "Prop lantern…", "prop_lantern", qw);
        addQuick(createRows, session, "Key…", "key", qw);
        addBody(create);

        List<String> ids = filtered(session);
        int maxPage = Math.max(0, (ids.size() - 1) / PAGE_SIZE);
        if (session.givePage() > maxPage) {
            session.setGivePage(maxPage);
        }
        int page = session.givePage();
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, ids.size());

        addSection("Items (" + ids.size() + ")");
        GridLayout grid = new GridLayout().columnSpacing(4).rowSpacing(3);
        GridLayout.RowHelper rows = grid.createRowHelper(1);
        for (int i = start; i < end; i++) {
            String id = ids.get(i);
            LinearLayout row = LinearLayout.horizontal().spacing(4);
            int idW = Math.max(64, panelWidth() - 140);
            row.addChild(Button.builder(Component.literal(id), b -> {
                session.setItemsIdDraft(id);
                give(id);
            }).width(idW).build());
            row.addChild(Button.builder(Component.literal("Edit"), b -> {
                session.setItemsIdDraft(id);
                session.setCreateIdDraft(id);
                session.setCreateDisplayName(id);
                open(new ItemsCreateScreen(this, true));
            }).width(44).build());
            row.addChild(Button.builder(Component.literal("Del"), b -> {
                session.forgetCustomItem(id);
                StaffCmds.customItemDelete(id);
                rebuildWidgets();
            }).width(36).build());
            row.addChild(Button.builder(Component.literal("CD"), b -> {
                session.setItemsIdDraft(id);
                open(new ItemsCooldownScreen(this, id));
            }).width(32).build());
            rows.addChild(row);
        }
        addBody(grid);

        LinearLayout nav = LinearLayout.horizontal().spacing(8);
        nav.addChild(Button.builder(Component.literal("Prev"), b -> {
            session.setGivePage(Math.max(0, page - 1));
            rebuildWidgets();
        }).width(80).build());
        nav.addChild(new StringWidget(Component.literal("Page " + (page + 1) + "/" + (maxPage + 1)), this.font));
        nav.addChild(Button.builder(Component.literal("Next"), b -> {
            session.setGivePage(Math.min(maxPage, page + 1));
            rebuildWidgets();
        }).width(80).build());
        addBody(nav);

        addSubtitle("Tip: Sync list pulls every registered id from the server. Create also auto-adds.");
    }

    private void give(String id) {
        var session = YapStaffClient.session();
        String who = session.hasTarget() ? session.targetName() : null;
        StaffCmds.customItemGive(id, session.giveAmount(), who);
    }

    private void addQuick(
            GridLayout.RowHelper rows,
            com.yapcore.staff.StaffSession session,
            String label,
            String template,
            int width) {
        rows.addChild(Button.builder(Component.literal(label), b -> {
            session.setCreateTemplate(template);
            var entry = com.yapcore.staff.ItemTemplateCatalog.get(template);
            if (entry != null) {
                String ability = entry.defaultAbility();
                session.setCreateAbility(ability == null || ability.isBlank() ? "none" : ability);
            }
            open(new ItemsCreateScreen(this));
        }).width(width).build());
    }

    private static List<String> filtered(com.yapcore.staff.StaffSession session) {
        String needle = session.giveFilter() == null ? "" : session.giveFilter().toLowerCase(Locale.ROOT).trim();
        Set<String> all = new LinkedHashSet<>();
        for (String id : BUILTIN) {
            all.add(id);
        }
        all.addAll(session.rememberedCustomItems());
        List<String> out = new ArrayList<>();
        for (String id : all) {
            if (needle.isEmpty() || id.contains(needle)) {
                out.add(id);
            }
        }
        return out;
    }
}
