package com.yapcore.mechanics.tool;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolRuleLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsRulesAndChecksTier() throws Exception {
        Files.writeString(tempDir.resolve("tools.yml"), """
                rules:
                  IRON_ORE:
                    tool: PICKAXE
                    min-tier: 2
                """);
        ToolRuleLoader loader = new ToolRuleLoader();
        loader.load(tempDir.resolve("tools.yml"));
        assertEquals(1, loader.ruleCount());
        ToolRuleLoader.ToolRule rule = loader.ruleFor(Material.IRON_ORE);
        assertTrue(ToolRuleLoader.matchesToolName("IRON_PICKAXE", rule.tool()));
        assertEquals(3, ToolRuleLoader.tierOfName("IRON_PICKAXE"));
        assertFalse(ToolRuleLoader.tierOfName("WOODEN_PICKAXE") >= rule.minTier());
    }
}
