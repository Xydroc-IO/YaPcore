package com.yapcore.tab;

import com.yapcore.tab.util.LegacyColors;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabUnitTest {

    @Test
    void configFieldDefaultsBeforeReload() {
        TabConfig config = new TabConfig(null);
        assertTrue(config.sidebarEnabled());
        assertTrue(config.nametagTeams());
        assertEquals(3, config.refreshSeconds());
        assertTrue(config.networkSyncEnabled());
        assertEquals("default", config.serverId());
        assertEquals(30, config.networkSyncHeartbeatSeconds());
        assertFalse(config.bossBarEnabled());
        assertTrue(config.bossBarWelcomeOnJoin());
        assertEquals("&6&lWelcome", config.bossBarTitle());
        assertEquals(BossBar.Color.YELLOW, config.bossBarColor());
        assertEquals(8, config.bossBarDurationSeconds());
    }

    @Test
    void parseColorFallsBackToYellow() {
        assertEquals(BossBar.Color.RED, TabConfig.parseColor("red"));
        assertEquals(BossBar.Color.BLUE, TabConfig.parseColor(" BLUE "));
        assertEquals(BossBar.Color.YELLOW, TabConfig.parseColor(null));
        assertEquals(BossBar.Color.YELLOW, TabConfig.parseColor(""));
        assertEquals(BossBar.Color.YELLOW, TabConfig.parseColor("not-a-color"));
    }

    @Test
    void legacyColorsAndPluginYml() throws Exception {
        assertEquals("", LegacyColors.plain(null));
        assertEquals("Hi", LegacyColors.plain("&aHi"));
        assertEquals("Hi", LegacyColors.plain("§cHi"));
        String plain = PlainTextComponentSerializer.plainText().serialize(LegacyColors.component("&aHello"));
        assertEquals("Hello", plain);
        assertTrue(LegacyColors.component(null).equals(net.kyori.adventure.text.Component.empty()));

        try (InputStream in = TabPlugin.class.getResourceAsStream("/plugin.yml")) {
            assertNotNull(in);
            String yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(yml.contains("main: com.yapcore.tab.TabPlugin"));
            assertTrue(yml.contains("yaptab"));
        }
    }
}
