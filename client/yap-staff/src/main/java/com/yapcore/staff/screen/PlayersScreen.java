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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Online player browser.
 * <ul>
 *   <li>Hub: click opens full player actions.</li>
 *   <li>Select mode: search + pages, tap name → set target and return to caller.</li>
 * </ul>
 */
public final class PlayersScreen extends StaffPanelScreen {

    private static final int PAGE_SIZE = 16;

    private final boolean selectOnly;
    private EditBox searchBox;

    public PlayersScreen(Screen parent) {
        this(parent, false);
    }

    public PlayersScreen(Screen parent, boolean selectOnly) {
        super(Component.literal(selectOnly ? "Select player" : "Online players"), parent);
        this.selectOnly = selectOnly;
    }

    @Override
    protected void addContents() {
        var session = YapStaffClient.session();

        if (selectOnly) {
            addSubtitle("Search, then tap a name — returns to your previous menu.");
        }

        LinearLayout searchRow = LinearLayout.horizontal().spacing(6);
        int searchW = Math.max(100, panelWidth() - 150);
        searchBox = editBox(searchW, 20, Component.literal("Search"));
        searchBox.setValue(session.playerFilter());
        searchBox.setHint(Component.literal("Type name…"));
        searchBox.setMaxLength(32);
        // Do not rebuild on every keystroke (kills focus). Apply via button / Enter.
        searchBox.setResponder(text -> session.setPlayerFilter(text));
        searchRow.addChild(searchBox);
        searchRow.addChild(Button.builder(Component.literal("Search"), b -> {
            session.setPlayerPage(0);
            rebuildWidgets();
        }).width(Math.min(70, Math.max(56, panelWidth() / 8))).build());
        searchRow.addChild(Button.builder(Component.literal("Clear"), b -> {
            session.setPlayerFilter("");
            session.setPlayerPage(0);
            rebuildWidgets();
        }).width(Math.min(60, Math.max(48, panelWidth() / 9))).build());
        addBody(searchRow);

        String selected = session.hasTarget() ? "Current target: " + session.targetName() : "Current target: (none)";
        addBody(new StringWidget(Component.literal(selected), this.font));

        List<String> allOnline = StaffCmds.onlineNames("");
        List<String> names = StaffCmds.onlineNames(session.playerFilter());
        addSubtitle(names.size() + " match · " + allOnline.size() + " online");

        // First-letter jumps (only letters that exist in the filtered set)
        Set<Character> letters = new LinkedHashSet<>();
        for (String name : names) {
            if (name.isEmpty()) {
                continue;
            }
            letters.add(Character.toUpperCase(name.charAt(0)));
        }
        if (!letters.isEmpty()) {
            int letterCols = this.width < 360 ? 8 : (this.width < 520 ? 10 : 13);
            GridLayout letterGrid = new GridLayout().columnSpacing(2).rowSpacing(2);
            GridLayout.RowHelper letterRows = letterGrid.createRowHelper(letterCols);
            int letterW = Math.max(16, (panelWidth() - (letterCols - 1) * 2) / letterCols);
            for (Character letter : letters) {
                char L = letter;
                letterRows.addChild(Button.builder(Component.literal(String.valueOf(L)), b -> {
                    // Jump to first page of names starting with this letter (within current filter)
                    int jump = 0;
                    for (int i = 0; i < names.size(); i++) {
                        if (Character.toUpperCase(names.get(i).charAt(0)) == L) {
                            jump = i / PAGE_SIZE;
                            break;
                        }
                    }
                    session.setPlayerPage(jump);
                    rebuildWidgets();
                }).width(letterW).build());
            }
            addBody(letterGrid);
        }

        int maxPage = Math.max(0, (names.size() - 1) / PAGE_SIZE);
        if (session.playerPage() > maxPage) {
            session.setPlayerPage(maxPage);
        }
        int page = session.playerPage();
        int start = page * PAGE_SIZE;
        int end = Math.min(names.size(), start + PAGE_SIZE);

        GridLayout grid = new GridLayout().columnSpacing(6).rowSpacing(4);
        GridLayout.RowHelper rows = grid.createRowHelper(gridColumns());
        for (int i = start; i < end; i++) {
            String name = names.get(i);
            boolean isTarget = session.hasTarget() && name.equals(session.targetName());
            String label = selectOnly
                    ? (isTarget ? "✓ " + name : "Select " + name)
                    : (isTarget ? "✓ " + name : name);
            rows.addChild(Button.builder(Component.literal(label), b -> onPick(name)).width(colWidth()).build());
        }
        if (names.isEmpty()) {
            addBody(new StringWidget(Component.literal("No matching players. Clear filter or check spelling."), this.font));
        } else {
            addBody(grid);
        }

        LinearLayout nav = LinearLayout.horizontal().spacing(8);
        nav.addChild(Button.builder(Component.literal("◀ Prev"), b -> {
            session.setPlayerPage(Math.max(0, page - 1));
            rebuildWidgets();
        }).width(Math.min(80, colWidth())).build());
        nav.addChild(new StringWidget(Component.literal(
                "Page " + (page + 1) + "/" + (maxPage + 1)
                        + "  ·  " + (names.isEmpty() ? "0" : (start + 1) + "–" + end)), this.font));
        nav.addChild(Button.builder(Component.literal("Next ▶"), b -> {
            session.setPlayerPage(Math.min(maxPage, page + 1));
            rebuildWidgets();
        }).width(Math.min(80, colWidth())).build());
        addBody(nav);

        if (selectOnly) {
            addBody(Button.builder(Component.literal("Cancel — back without changing"), b -> open(parent))
                    .width(wideWidth()).build());
        }
    }

    @Override
    protected void init() {
        super.init();
        if (searchBox != null) {
            setInitialFocus(searchBox);
        }
        // Enter in search applies filter
        if (searchBox != null) {
            searchBox.setResponder(text -> {
                YapStaffClient.session().setPlayerFilter(text);
            });
        }
    }

    private void onPick(String name) {
        YapStaffClient.session().setTarget(name);
        if (selectOnly) {
            // Fresh parent screen so target subtitle refreshes cleanly
            open(parent);
            return;
        }
        open(new PlayerActionsScreen(this, name));
    }
}
