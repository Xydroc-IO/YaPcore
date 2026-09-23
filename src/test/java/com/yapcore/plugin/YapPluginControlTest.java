package com.yapcore.plugin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class YapPluginControlTest {

    @Test
    void jarTokenStripsVersionAndDisabledSuffix() {
        assertEquals("yap-skills", YapPluginControl.jarToken("yap-skills-0.0.0.1.jar"));
        assertEquals("yap-skills", YapPluginControl.jarToken("yap-skills-0.0.0.1.jar.disabled"));
        assertEquals("placeholderapi", YapPluginControl.jarToken("PlaceholderAPI-2.11.5.jar"));
    }

    @Test
    void findEntryMatchesCatalogTokens() {
        assertNotNull(YapPluginControl.findEntry("yap-skills-0.0.0.1.jar"));
        assertEquals("yap-skills", YapPluginControl.findEntry("yap-skills-0.0.0.1.jar").id());
        assertEquals("yap-gameplay-knobs", YapPluginControl.findEntry("yap-gameplay-knobs-0.0.0.1.jar").id());
        assertNull(YapPluginControl.findEntry("random-unknown-plugin.jar"));
    }

    @Test
    void tierMarksCoreAndGameplay() {
        var skills = YapPluginControl.findEntry("yap-skills.jar");
        assertEquals(YapPluginControl.Tier.GAMEPLAY, YapPluginControl.tierFor("yap-skills.jar", skills));
        var db = YapPluginControl.findEntry("yap-db.jar");
        assertEquals(YapPluginControl.Tier.CORE, YapPluginControl.tierFor("yap-db.jar", db));
        var claims = YapPluginControl.findEntry("yap-claims.jar");
        assertEquals(YapPluginControl.Tier.CORE, YapPluginControl.tierFor("yap-claims.jar", claims));
        assertEquals(false, YapPluginControl.isOptIn("yap-claims.jar"));
        var mobs = YapPluginControl.findEntry("yap-mobs.jar");
        assertEquals(YapPluginControl.Tier.GAMEPLAY, YapPluginControl.tierFor("yap-mobs.jar", mobs));
        var dungeons = YapPluginControl.findEntry("yap-dungeons.jar");
        assertEquals(YapPluginControl.Tier.GAMEPLAY, YapPluginControl.tierFor("yap-dungeons.jar", dungeons));
    }

    @Test
    void optInMarksShippedSoftOffPlugins() {
        assertEquals(true, YapPluginControl.isOptIn("yap-disasters-0.0.0.1.jar"));
        assertEquals(true, YapPluginControl.isOptIn("yap-factions.jar"));
        assertEquals(true, YapPluginControl.isOptIn("yap-gameplay-knobs.jar"));
        assertEquals(false, YapPluginControl.isOptIn("yap-dungeons.jar"));
        assertEquals(false, YapPluginControl.isOptIn("yap-db.jar"));
        assertEquals(false, YapPluginControl.isOptIn("yap-chat.jar"));
        assertEquals(false, YapPluginControl.isOptIn("yap-portals.jar"));
    }
}
