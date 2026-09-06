package com.yapcore.admin;

import com.yapcore.admin.action.AdminActions;
import com.yapcore.admin.gui.AdminMenus;
import com.yapcore.admin.session.AdminSession;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminSmokeTest {

    @Test
    void configDefaultsAndItemPreset() {
        AdminConfig config = new AdminConfig();
        assertEquals(List.of("starter", "adventurer", "vip"), config.kits());
        assertEquals(List.of(100, 1000, 10000, 100000), config.moneyAmounts());
        assertTrue(config.broadcastPresets().isEmpty());
        assertTrue(config.presets().isEmpty());

        AdminConfig.ItemPreset preset = new AdminConfig.ItemPreset("sword", Material.DIAMOND_SWORD, 1, "Sword");
        assertEquals("sword", preset.id());
        assertEquals(Material.DIAMOND_SWORD, preset.material());
        assertEquals(1, preset.amount());
    }

    @Test
    void menuSlotConstantsAndPermissionsFromYml() throws Exception {
        assertEquals(45, AdminMenus.SLOT_BACK);
        assertEquals(49, AdminMenus.SLOT_CLOSE);
        assertEquals(10, AdminMenus.HUB_PLAYERS);
        assertEquals(14, AdminMenus.HUB_GIVE);

        try (InputStream in = AdminPlugin.class.getResourceAsStream("/plugin.yml")) {
            assertNotNull(in);
            String yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(yml.contains("main: com.yapcore.admin.AdminPlugin"));
            assertTrue(yml.contains("yapadmin.menu"));
            assertTrue(yml.contains("yapadmin.give"));
            assertTrue(yml.contains("yapadmin.plugins"));
        }
    }

    @Test
    void sessionGiveCycleAndMaterialHelpers() {
        AdminSession session = new AdminSession();
        assertFalse(session.hasTarget());
        session.setTarget(UUID.randomUUID(), "Alex");
        assertTrue(session.hasTarget());
        assertEquals(1, session.giveAmount());
        session.cycleGiveAmount();
        assertEquals(16, session.giveAmount());
        session.cycleGiveAmount();
        assertEquals(64, session.giveAmount());
        session.cycleGiveAmount();
        assertEquals(1, session.giveAmount());

        assertEquals("Diamond sword", AdminActions.pretty(Material.DIAMOND_SWORD));
        assertTrue(AdminActions.isTool(Material.IRON_PICKAXE));
        assertTrue(AdminActions.isCombat(Material.IRON_SWORD));
        assertFalse(AdminActions.isTool(Material.STONE));
    }
}
