package com.yapcore.staff.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Shared header/footer shell for staff screens.
 * Body scrolls and button/grid widths scale to the current window — works windowed or fullscreen.
 */
public abstract class StaffPanelScreen extends Screen {

    protected final Screen parent;
    protected HeaderAndFooterLayout layout;
    protected LinearLayout body;
    protected ScrollableLayout scroll;

    protected StaffPanelScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    @Override
    protected void init() {
        // Shorter chrome on low GUI-scale / windowed heights so the scroll body has room.
        int chrome = this.height < 280 ? 24 : (this.height < 360 ? 28 : 33);
        layout = new HeaderAndFooterLayout(this, chrome, chrome);
        body = LinearLayout.vertical().spacing(bodySpacing());
        layout.addTitleHeader(this.title, this.font);
        addContents();

        Minecraft mc = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
        int maxH = Math.max(48, layout.getContentHeight());
        scroll = new ScrollableLayout(mc, body, maxH);
        scroll.setMinWidth(panelWidth());
        layout.addToContents(scroll);

        LinearLayout footer = LinearLayout.horizontal().spacing(8);
        Button back = Button.builder(CommonComponents.GUI_BACK, b -> onClose()).width(footerBtnWidth()).build();
        Button done = Button.builder(CommonComponents.GUI_DONE, b -> closeToGame()).width(footerBtnWidth()).build();
        done.setTooltip(Tooltip.create(Component.literal("Close menu. The server still checks permissions.")));
        footer.addChild(back);
        footer.addChild(done);
        layout.addToFooter(footer);
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
    }

    protected abstract void addContents();

    @Override
    protected void repositionElements() {
        if (layout == null) {
            return;
        }
        if (scroll != null) {
            scroll.setMinWidth(panelWidth());
            // Arrange body first — setMaxHeight uses content.getHeight(); before arrange that
            // is 0 and collapses the scroll container (empty menu). Vanilla RestrictionsScreen
            // does the same: arrangeElements → setMaxHeight → parent arrange.
            scroll.arrangeElements();
            scroll.setMaxHeight(Math.max(48, layout.getContentHeight()));
        }
        layout.arrangeElements();
    }

    /** Usable content width that shrinks on small windows / grows (capped) on ultrawide. */
    protected int panelWidth() {
        int margin = this.width < 360 ? 16 : 32;
        return Math.min(560, Math.max(180, this.width - margin));
    }

    protected int colWidth() {
        return colWidthFor(gridColumns());
    }

    /** Column width for a specific column count within {@link #panelWidth()}. */
    protected int colWidthFor(int cols) {
        int c = Math.max(1, cols);
        int gaps = Math.max(0, c - 1) * 6;
        return Math.max(70, (panelWidth() - gaps) / c);
    }

    protected int wideWidth() {
        return panelWidth();
    }

    protected int footerBtnWidth() {
        return Math.min(120, Math.max(72, this.width / 5));
    }

    /**
     * How many side-by-side action buttons fit. Windowed / high GUI scale → fewer columns.
     * Scaled width ~427 is common on 854×480 at GUI scale 2.
     */
    protected int gridColumns() {
        if (this.width >= 780) {
            return 3;
        }
        if (this.width >= 420) {
            return 2;
        }
        return 1;
    }

    /** Cap a preferred column count to what actually fits this window. */
    protected int preferredColumns(int preferred) {
        return Math.max(1, Math.min(preferred, gridColumns()));
    }

    /** Dense chip rows (damage / cooldown values) — still respects narrow windows. */
    protected int chipColumns(int valueCount) {
        int want;
        if (this.width < 360) {
            want = 2;
        } else if (this.width < 520) {
            want = 3;
        } else {
            want = 4;
        }
        return Math.max(1, Math.min(want, valueCount));
    }

    protected int chipWidth(int cols) {
        int c = Math.max(1, cols);
        int gaps = Math.max(0, c - 1) * 4;
        return Math.max(36, (panelWidth() - gaps) / c);
    }

    protected int bodySpacing() {
        return this.height < 300 ? 2 : (this.height < 360 ? 3 : 6);
    }

    /** Full-width edit box that fits the panel (not a fixed 220px that overflows). */
    protected EditBox editBox(int height, Component message) {
        return new EditBox(this.font, panelWidth(), height, message);
    }

    protected EditBox editBox(int width, int height, Component message) {
        return new EditBox(this.font, Math.min(width, panelWidth()), height, message);
    }

    protected void rebuildWidgets() {
        double savedScroll = captureScrollAmount();
        this.clearWidgets();
        this.init();
        restoreScrollAmount(savedScroll);
    }

    /** Keep the body scrolled where you were after option clicks rebuild the panel. */
    private double captureScrollAmount() {
        for (GuiEventListener child : this.children()) {
            if (child instanceof AbstractScrollArea area) {
                return area.scrollAmount();
            }
        }
        return 0.0;
    }

    private void restoreScrollAmount(double amount) {
        if (amount <= 0.0) {
            return;
        }
        for (GuiEventListener child : this.children()) {
            if (child instanceof AbstractScrollArea area) {
                area.setScrollAmount(amount);
                return;
            }
        }
    }

    @Override
    public void onClose() {
        open(parent);
    }

    protected void open(Screen screen) {
        Minecraft mc = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
        if (mc != null) {
            mc.gui.setScreen(screen);
        }
    }

    protected void closeToGame() {
        open(null);
    }

    protected <T extends LayoutElement> T addBody(T child) {
        return body.addChild(child);
    }

    protected Button action(String label, String tip, Runnable run) {
        Button button = Button.builder(Component.literal(label), b -> run.run()).width(colWidth()).build();
        if (tip != null && !tip.isBlank()) {
            button.setTooltip(Tooltip.create(Component.literal(tip)));
        }
        return button;
    }

    protected Button actionWide(String label, String tip, Runnable run) {
        Button button = Button.builder(Component.literal(label), b -> run.run()).width(wideWidth()).build();
        if (tip != null && !tip.isBlank()) {
            button.setTooltip(Tooltip.create(Component.literal(tip)));
        }
        return button;
    }

    protected void addButtonGrid(Button... buttons) {
        int cols = gridColumns();
        GridLayout grid = new GridLayout().columnSpacing(6).rowSpacing(this.height < 360 ? 2 : 4);
        GridLayout.RowHelper rows = grid.createRowHelper(cols);
        int w = colWidthFor(cols);
        for (Button button : buttons) {
            button.setWidth(w);
            rows.addChild(button);
        }
        addBody(grid);
    }

    protected void addSubtitle(String text) {
        addBody(new StringWidget(Component.literal(text), this.font));
    }

    /** Section label for grouped hub / tool screens. */
    protected void addSection(String title) {
        addSubtitle(title);
    }

    /**
     * Compact target bar: current name + open searchable picker (scales to 200+ players).
     * Does not list every online name on this screen.
     */
    protected void addTargetBar() {
        var session = com.yapcore.staff.YapStaffClient.session();
        String label = session.hasTarget() ? "Player: " + session.targetName() : "No player selected";
        addSubtitle(label);
        LinearLayout row = LinearLayout.horizontal().spacing(6);
        int pickW = Math.min(160, Math.max(100, panelWidth() - 70));
        row.addChild(Button.builder(Component.literal("Select player…"), b -> {
            session.setPlayerFilter("");
            session.setPlayerPage(0);
            open(new PlayersScreen(this, true));
        }).width(pickW).build());
        row.addChild(Button.builder(Component.literal("Clear"), b -> {
            session.clearTarget();
            rebuildWidgets();
        }).width(Math.min(70, Math.max(48, panelWidth() / 6))).build());
        addBody(row);
    }

    /** @deprecated use {@link #addTargetBar()} — listing everyone inline does not scale. */
    @Deprecated
    protected void addTargetCycleRow() {
        addTargetBar();
    }
}
