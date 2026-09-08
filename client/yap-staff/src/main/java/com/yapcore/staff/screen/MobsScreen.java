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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Searchable mob browser — spawn via /yapadmin spawnmob at self or target. */
public final class MobsScreen extends StaffPanelScreen {

    private static final int PAGE_SIZE = 16;
    private static final String[] PRESETS = {
            "zombie", "skeleton", "creeper", "spider", "enderman", "witch",
            "blaze", "ghast", "wither_skeleton", "piglin_brute",
            "cow", "pig", "sheep", "chicken", "villager", "iron_golem",
            "wolf", "cat", "horse", "allay", "warden", "ender_dragon"
    };

    public MobsScreen(Screen parent) {
        super(Component.literal("Spawn mobs"), parent);
    }

    @Override
    protected void addContents() {
        var session = YapStaffClient.session();
        String where = session.hasTarget() ? "At: " + session.targetName() : "At you";

        addBody(new StringWidget(Component.literal(
                where + "  ·  Count ×" + session.mobAmount()), this.font));

        LinearLayout controls = LinearLayout.horizontal().spacing(6);
        controls.addChild(Button.builder(Component.literal("Count ×" + session.mobAmount()), b -> {
            session.cycleMobAmount();
            rebuildWidgets();
        }).width(110).build());
        controls.addChild(Button.builder(Component.literal("Select player…"), b -> {
            session.setPlayerFilter("");
            session.setPlayerPage(0);
            open(new PlayersScreen(this, true));
        }).width(120).build());
        controls.addChild(Button.builder(Component.literal("At me"), b -> {
            session.clearTarget();
            rebuildWidgets();
        }).width(70).build());
        addBody(controls);

        EditBox search = new EditBox(this.font, 240, 20, Component.literal("Search mobs"));
        search.setValue(session.mobFilter());
        search.setHint(Component.literal("Search entity…"));
        search.setResponder(session::setMobFilter);
        LinearLayout searchRow = LinearLayout.horizontal().spacing(6);
        searchRow.addChild(search);
        searchRow.addChild(Button.builder(Component.literal("Search"), b -> rebuildWidgets()).width(70).build());
        searchRow.addChild(Button.builder(Component.literal("Clear"), b -> {
            session.setMobFilter("");
            rebuildWidgets();
        }).width(60).build());
        addBody(searchRow);

        addSection("Quick picks");
        GridLayout presets = new GridLayout().columnSpacing(4).rowSpacing(3);
        GridLayout.RowHelper presetRows = presets.createRowHelper(gridColumns());
        for (String id : PRESETS) {
            presetRows.addChild(Button.builder(Component.literal(pretty(id)), b -> spawn(id))
                    .width(colWidth()).build());
        }
        addBody(presets);

        List<String> ids = filteredMobs(session.mobFilter());
        int maxPage = Math.max(0, (ids.size() - 1) / PAGE_SIZE);
        if (session.mobPage() > maxPage) {
            session.setMobPage(maxPage);
        }
        int page = session.mobPage();
        int start = page * PAGE_SIZE;
        int end = Math.min(ids.size(), start + PAGE_SIZE);

        addSection("All mobs — " + (page + 1) + "/" + (maxPage + 1));
        GridLayout grid = new GridLayout().columnSpacing(4).rowSpacing(3);
        GridLayout.RowHelper rows = grid.createRowHelper(gridColumns());
        for (int i = start; i < end; i++) {
            String id = ids.get(i);
            rows.addChild(Button.builder(Component.literal(pretty(id)), b -> spawn(id))
                    .width(colWidth()).build());
        }
        addBody(grid);

        LinearLayout nav = LinearLayout.horizontal().spacing(8);
        nav.addChild(Button.builder(Component.literal("◀ Prev"), b -> {
            session.setMobPage(page - 1);
            rebuildWidgets();
        }).width(80).build());
        nav.addChild(Button.builder(Component.literal("Next ▶"), b -> {
            session.setMobPage(page + 1);
            rebuildWidgets();
        }).width(80).build());
        addBody(nav);
    }

    private void spawn(String entityId) {
        var session = YapStaffClient.session();
        StaffCmds.spawnMob(entityId, session.mobAmount(),
                session.hasTarget() ? session.targetName() : null);
    }

    private static List<String> filteredMobs(String filter) {
        String needle = filter == null ? "" : filter.toLowerCase(Locale.ROOT).trim();
        List<String> out = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (!type.canSummon() || type == EntityTypes.PLAYER) {
                continue;
            }
            Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (key == null) {
                continue;
            }
            String path = key.getPath();
            if (!needle.isEmpty()
                    && !path.contains(needle)
                    && !path.replace('_', ' ').contains(needle)) {
                continue;
            }
            out.add(path);
        }
        out.sort(Comparator.naturalOrder());
        return out;
    }

    private static String pretty(String id) {
        String raw = id.replace('_', ' ');
        if (raw.length() <= 16) {
            return raw;
        }
        return raw.substring(0, 14) + "…";
    }
}
