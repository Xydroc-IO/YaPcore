package com.yapcore.staff.screen;

import com.yapcore.staff.StaffCmds;
import com.yapcore.staff.YapStaffClient;
import com.yapcore.staff.ranks.PermCatalog;
import com.yapcore.staff.ranks.PermCmds;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Searchable permission catalog → allow / deny / unset for a user or group. */
public final class PermEditorScreen extends StaffPanelScreen {

    public enum Scope {
        USER, GROUP
    }

    private static final int PAGE_SIZE = 10;

    private final Scope scope;
    private final String groupId;

    public PermEditorScreen(Screen parent, Scope scope, String groupId) {
        super(Component.literal(scope == Scope.USER ? "Player permissions" : "Group permissions: " + groupId), parent);
        this.scope = scope;
        this.groupId = groupId;
    }

    @Override
    protected void addContents() {
        var session = YapStaffClient.session();
        String subject = scope == Scope.USER
                ? (session.hasTarget() ? session.targetName() : "(pick player)")
                : groupId;
        String modeLabel = switch (session.permModeIndex()) {
            case 1 -> "Deny";
            case 2 -> "Unset";
            default -> "Allow";
        };
        String durLabel = PermCmds.durationLabel(session.permDurationIndex());

        LinearLayout top = LinearLayout.vertical().spacing(4);
        top.addChild(new StringWidget(Component.literal(
                "Subject: " + subject + "  ·  Click = " + modeLabel + "  ·  " + durLabel), this.font));

        LinearLayout controls = LinearLayout.horizontal().spacing(4);
        if (scope == Scope.USER) {
            controls.addChild(Button.builder(Component.literal("Pick player…"), b ->
                    open(new PlayersScreen(this, true))).width(100).build());
        }
        controls.addChild(Button.builder(Component.literal("Mode: " + modeLabel), b -> {
            session.cyclePermMode();
            rebuildWidgets();
        }).width(100).build());
        controls.addChild(Button.builder(Component.literal("Dur: " + durLabel), b -> {
            session.cyclePermDuration();
            rebuildWidgets();
        }).width(100).build());
        if (scope == Scope.USER) {
            controls.addChild(Button.builder(Component.literal("Info"), b -> {
                String p = StaffCmds.requireTarget();
                if (p != null) {
                    PermCmds.userInfo(p);
                }
            }).width(50).build());
        } else {
            controls.addChild(Button.builder(Component.literal("Info"), b ->
                    PermCmds.groupInfo(groupId)).width(50).build());
        }
        top.addChild(controls);

        EditBox search = new EditBox(this.font, 240, 20, Component.literal("Search"));
        search.setValue(session.permFilter());
        search.setHint(Component.literal("Search nodes…"));
        search.setResponder(text -> {
            session.setPermFilter(text);
            rebuildWidgets();
        });
        top.addChild(search);
        addBody(top);

        GridLayout cats = new GridLayout().columnSpacing(3).rowSpacing(2);
        GridLayout.RowHelper catRows = cats.createRowHelper(4);
        catRows.addChild(catChip("all", "All"));
        for (PermCatalog.Category cat : PermCatalog.categories()) {
            catRows.addChild(catChip(cat.id(), cat.title()));
        }
        addBody(cats);

        List<PermCatalog.Node> nodes = PermCatalog.search(session.permFilter(), session.permCategory());
        int maxPage = Math.max(0, (nodes.size() - 1) / PAGE_SIZE);
        if (session.permPage() > maxPage) {
            session.setPermPage(maxPage);
        }
        int page = session.permPage();
        int start = page * PAGE_SIZE;
        int end = Math.min(nodes.size(), start + PAGE_SIZE);

        GridLayout grid = new GridLayout().columnSpacing(4).rowSpacing(3);
        GridLayout.RowHelper rows = grid.createRowHelper(1);
        for (int i = start; i < end; i++) {
            PermCatalog.Node node = nodes.get(i);
            String label = (node.danger() ? "⚠ " : "") + node.label();
            String tip = node.node() + " — " + node.desc();
            rows.addChild(actionWide(label, tip, () -> applyNode(node.node())));
        }
        if (nodes.isEmpty()) {
            addBody(new StringWidget(Component.literal("No matching nodes."), this.font));
        } else {
            addBody(grid);
        }

        LinearLayout nav = LinearLayout.horizontal().spacing(8);
        nav.addChild(Button.builder(Component.literal("◀ Prev"), b -> {
            session.setPermPage(page - 1);
            rebuildWidgets();
        }).width(80).build());
        nav.addChild(new StringWidget(Component.literal(
                "Page " + (page + 1) + "/" + (maxPage + 1) + " · " + nodes.size() + " nodes"), this.font));
        nav.addChild(Button.builder(Component.literal("Next ▶"), b -> {
            session.setPermPage(page + 1);
            rebuildWidgets();
        }).width(80).build());
        addBody(nav);

        addSubtitle("Custom node");
        LinearLayout custom = LinearLayout.horizontal().spacing(6);
        EditBox customBox = new EditBox(this.font, 200, 20, Component.literal("node"));
        customBox.setValue(session.customNode());
        customBox.setHint(Component.literal("e.g. myplugin.use.*"));
        customBox.setResponder(session::setCustomNode);
        custom.addChild(customBox);
        custom.addChild(Button.builder(Component.literal("Apply"), b -> {
            if (!session.customNode().isBlank()) {
                applyNode(session.customNode());
            }
        }).width(70).build());
        if (scope == Scope.USER) {
            custom.addChild(Button.builder(Component.literal("Check"), b -> {
                String p = StaffCmds.requireTarget();
                if (p != null && !session.customNode().isBlank()) {
                    PermCmds.check(p, session.customNode());
                }
            }).width(60).build());
        }
        addBody(custom);
    }

    private Button catChip(String id, String label) {
        var session = YapStaffClient.session();
        boolean active = session.permCategory().equals(id);
        String text = active ? "[" + label + "]" : label;
        return Button.builder(Component.literal(text), b -> {
            session.setPermCategory(id);
            rebuildWidgets();
        }).width(Math.min(90, 18 + label.length() * 6)).build();
    }

    private void applyNode(String node) {
        var session = YapStaffClient.session();
        PermCmds.Mode mode = switch (session.permModeIndex()) {
            case 1 -> PermCmds.Mode.DENY;
            case 2 -> PermCmds.Mode.UNSET;
            default -> PermCmds.Mode.ALLOW;
        };
        if (scope == Scope.USER) {
            String p = StaffCmds.requireTarget();
            if (p == null) {
                return;
            }
            PermCmds.userPerm(p, node, mode, session.permDurationIndex());
        } else {
            PermCmds.groupPerm(groupId, node, mode, session.permDurationIndex());
        }
    }
}
