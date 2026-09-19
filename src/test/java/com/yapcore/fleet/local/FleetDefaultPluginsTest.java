package com.yapcore.fleet.local;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class FleetDefaultPluginsTest {

    @Test
    void coreNetworkRecognizesProductJars() {
        assertTrue(FleetDefaultPlugins.isCoreNetwork("yap-holo.jar"));
        assertTrue(FleetDefaultPlugins.isCoreNetwork("yap-lib.jar"));
        assertTrue(FleetDefaultPlugins.isCoreNetwork("yap-db.jar"));
        assertTrue(FleetDefaultPlugins.isCoreDefault("yap-perms.jar"));
        assertTrue(FleetDefaultPlugins.isCoreNetwork("WorldEdit.jar"));
        assertTrue(FleetDefaultPlugins.isCoreNetwork("yap-tailor.jar"));
        assertTrue(FleetDefaultPlugins.isCoreNetwork("yap-bedrock-blocks.jar"));
        assertTrue(FleetDefaultPlugins.isCoreNetwork("yap-tebex.jar"));
        assertTrue(FleetDefaultPlugins.isCoreNetwork("yap-portals.jar"));
        assertTrue(FleetDefaultPlugins.isSeedDefault("yap-items.jar"));
        assertTrue(FleetDefaultPlugins.isSeedDefault("yap-qol.jar"));
        assertFalse(FleetDefaultPlugins.isCoreNetwork("yap-skills.jar"));
        assertFalse(FleetDefaultPlugins.isCoreNetwork("grim.jar"));
    }

    @Test
    void missingFromReportsAbsentDefaults() {
        List<String> missing = FleetDefaultPlugins.missingFrom(List.of("yap-db.jar"));
        assertTrue(missing.contains("yap-perms.jar"));
        assertTrue(missing.contains("yap-portals.jar"));
        assertTrue(missing.contains("yap-items.jar"));
        assertTrue(missing.contains("yap-qol.jar"));
        assertTrue(missing.contains("yap-tailor.jar"));
        assertTrue(missing.contains("yap-bedrock-blocks.jar"));
        assertFalse(missing.contains("yap-db.jar"));
    }
}
