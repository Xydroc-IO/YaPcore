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

/** Pick an ability cooldown duration for one YaPItems id, then apply. */
public final class ItemsCooldownScreen extends StaffPanelScreen {

    private static final String[] PRESETS = {
            "0s", "1s", "2s", "3s", "5s", "8s", "10s", "12s", "15s", "20s", "30s", "60s"
    };

    private final String itemId;
    private EditBox durationBox;

    public ItemsCooldownScreen(Screen parent, String itemId) {
        super(Component.literal("Ability cooldown"), parent);
        this.itemId = itemId == null ? "" : itemId.trim().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    protected void addContents() {
        var session = YapStaffClient.session();
        if (!itemId.isBlank()) {
            session.setItemsIdDraft(itemId);
            session.rememberCustomItem(itemId);
        }

        addSubtitle("Item: " + (itemId.isBlank() ? "(none — set id below)" : itemId));
        addSubtitle("Type a duration or click a preset. Applies with /yapitems cooldown.");

        LinearLayout idRow = LinearLayout.horizontal().spacing(6);
        EditBox idBox = new EditBox(this.font, 200, 20, Component.literal("Item id"));
        idBox.setValue(itemId.isBlank() ? session.itemsIdDraft() : itemId);
        idBox.setHint(Component.literal("item id…"));
        idBox.setResponder(session::setItemsIdDraft);
        idRow.addChild(idBox);
        addBody(idRow);

        LinearLayout durRow = LinearLayout.horizontal().spacing(6);
        durationBox = new EditBox(this.font, 120, 20, Component.literal("Duration"));
        durationBox.setValue(session.itemsCooldown());
        durationBox.setHint(Component.literal("e.g. 3s"));
        durationBox.setResponder(text -> {
            if (text != null && !text.isBlank()) {
                session.setItemsCooldown(text.trim());
            }
        });
        durationBox.setMaxLength(16);
        durRow.addChild(durationBox);
        durRow.addChild(Button.builder(Component.literal("Apply"), b -> apply(durationBox.getValue())).width(80).build());
        addBody(durRow);
        addBody(new StringWidget(Component.literal("Examples: 0s  ·  3s  ·  2.5s  ·  500ms"), this.font));

        addSection("Presets (click = set now)");
        GridLayout grid = new GridLayout().columnSpacing(4).rowSpacing(3);
        GridLayout.RowHelper rows = grid.createRowHelper(4);
        for (String preset : PRESETS) {
            String p = preset;
            rows.addChild(Button.builder(Component.literal(p), b -> {
                session.setItemsCooldown(p);
                if (durationBox != null) {
                    durationBox.setValue(p);
                }
                apply(p);
            }).width(Math.max(56, (colWidth() - 12) / 2)).build());
        }
        addBody(grid);
    }

    private void apply(String rawDuration) {
        var session = YapStaffClient.session();
        String id = session.itemsIdDraft();
        if (id == null || id.isBlank()) {
            id = itemId;
        }
        if (id == null || id.isBlank()) {
            return;
        }
        String duration = rawDuration == null || rawDuration.isBlank() ? session.itemsCooldown() : rawDuration.trim();
        session.setItemsCooldown(duration);
        session.rememberCustomItem(id);
        StaffCmds.customItemCooldown(id, duration);
    }
}
