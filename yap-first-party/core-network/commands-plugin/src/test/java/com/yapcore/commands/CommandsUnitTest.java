package com.yapcore.commands;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandsUnitTest {

    @Test
    void customCommandDefNormalizesAndDefaultsPermission() {
        CustomCommandDef def = new CustomCommandDef(
                "Discord",
                true,
                List.of("dc", "Disc"),
                "",
                "Join Discord",
                5,
                true,
                List.of("hi"),
                null,
                null,
                null);
        assertEquals("discord", def.name());
        assertEquals(List.of("dc", "Disc"), def.aliases());
        assertEquals("yapcommands.cmd.discord", def.effectivePermission());
        assertTrue(def.enabled());
        assertEquals(5, def.cooldownSeconds());
        assertTrue(def.messages().contains("hi"));
        assertTrue(def.playerCommands().isEmpty());
    }

    @Test
    void explicitPermissionWinsOverGenerated() {
        CustomCommandDef def = new CustomCommandDef(
                "shop", true, List.of(), "  shop.use  ", "", 0, false,
                List.of(), List.of(), List.of(), "");
        assertEquals("shop.use", def.effectivePermission());
        assertFalse(def.hideNoPermission());
    }

    @Test
    void pluginYmlExposesAdminAliasesAndPerms() throws Exception {
        try (InputStream in = CommandsPlugin.class.getResourceAsStream("/plugin.yml")) {
            assertNotNull(in);
            String yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(yml.contains("main: com.yapcore.commands.CommandsPlugin"));
            assertTrue(yml.contains("yapcommands.admin"));
            assertTrue(yml.contains("ycmd"));
            assertTrue(yml.contains("yapcommands.bypass.cooldown"));
        }
        assertEquals(CommandsPlugin.class, Class.forName("com.yapcore.commands.CommandsPlugin"));
    }
}
