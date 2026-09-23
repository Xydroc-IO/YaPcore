package com.yapcore.staff.screen;

import com.yapcore.staff.StaffCmds;
import com.yapcore.staff.YapStaffClient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * YaP420 admin — give seeds/buds/consumables, reload, remove look-at, status.
 * Item gives use {@code /yapitems give} (works whenever YaPItems + yap420.yml are loaded).
 * Ops that need the YaP420 plugin use {@code /yap420 …}.
 */
public final class Yap420HubScreen extends StaffPanelScreen {

    public Yap420HubScreen(Screen parent) {
        super(Component.literal("YaP420"), parent);
    }

    @Override
    protected void addContents() {
        addSubtitle("Gives via YaPItems · plant/cure needs yap-420.jar on this server");
        addTargetBar();

        String who = YapStaffClient.session().hasTarget()
                ? YapStaffClient.session().targetName()
                : "you";

        addSection("Give (to " + who + ")");
        addButtonGrid(
                action("Seeds ×16", "Sativa + indica seeds", () -> {
                    give("yap420_seed_sativa", 16);
                    give("yap420_seed_indica", 16);
                }),
                action("Cured buds ×8", "Sativa + indica cured", () -> {
                    give("yap420_bud_cured_sativa", 8);
                    give("yap420_bud_cured_indica", 8);
                }),
                action("Consumables", "Paper + joints + brownie", () -> {
                    give("yap420_rolling_paper", 16);
                    give("yap420_joint_sativa", 4);
                    give("yap420_joint_indica", 4);
                    give("yap420_blunt_sativa", 2);
                    give("yap420_blunt_indica", 2);
                    give("yap420_brownie", 4);
                }),
                action("Pack units", "Grams · ounces · pounds", () -> {
                    give("yap420_gram_sativa", 8);
                    give("yap420_gram_indica", 8);
                    give("yap420_ounce_sativa", 2);
                    give("yap420_ounce_indica", 2);
                    give("yap420_brick_sativa", 1);
                    give("yap420_brick_indica", 1);
                }),
                action("Drying rack", "One placeable rack", () -> give("yap420_drying_rack", 1)),
                action("Packaging press", "Press grams → ounces → pounds", () -> give("yap420_packaging_press", 1)),
                action("Starter kit", "Seeds · paper · rack · joints", () -> {
                    give("yap420_seed_sativa", 16);
                    give("yap420_seed_indica", 16);
                    give("yap420_rolling_paper", 16);
                    give("yap420_drying_rack", 1);
                    give("yap420_packaging_press", 1);
                    give("yap420_joint_sativa", 2);
                    give("yap420_joint_indica", 2);
                })
        );

        addSection("Ops (needs YaP420 plugin)");
        addButtonGrid(
                action("Status /yap420 info", "Plot and rack counts", () -> {
                    closeToGame();
                    StaffCmds.run("yap420 info");
                }),
                action("Open Blazed Boutique", "/yap420 sell", () -> {
                    closeToGame();
                    StaffCmds.run("yap420 sell");
                }),
                action("Reload", "Reload YaP420 config", () -> {
                    closeToGame();
                    StaffCmds.run("yap420 reload");
                }),
                action("Remove look-at", "Look at plant/rack then run", () -> {
                    closeToGame();
                    StaffCmds.run("yap420 remove");
                }),
                action("Chest YaP420 hub", "Full chest panel (browse every item)", () -> {
                    closeToGame();
                    StaffCmds.run("yapadmin chest");
                })
        );
    }

    private void give(String id, int amount) {
        // Always yapitems — registered on survival today; /yap420 is only after yap-420.jar loads.
        if (YapStaffClient.session().hasTarget()) {
            StaffCmds.runFmt("yapitems give %s %d %s", id, amount, YapStaffClient.session().targetName());
        } else {
            StaffCmds.runFmt("yapitems give %s %d", id, amount);
        }
    }
}
