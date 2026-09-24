package com.yapcore.fleet.local;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class InstanceLayoutInventoryProfileTest {

    @TempDir
    Path root;

    @Test
    void defaultProfileSharesHubSurvivalFactionsIsolatesCreativeSkyblock() {
        assertEquals("global", InstanceLayout.defaultInventoryProfile("lobby"));
        assertEquals("global", InstanceLayout.defaultInventoryProfile("hub"));
        assertEquals("global", InstanceLayout.defaultInventoryProfile("survival"));
        assertEquals("global", InstanceLayout.defaultInventoryProfile("factions"));
        assertEquals("server", InstanceLayout.defaultInventoryProfile("creative"));
        assertEquals("server", InstanceLayout.defaultInventoryProfile("creative-build"));
        assertEquals("server", InstanceLayout.defaultInventoryProfile("skyblock"));
        assertEquals("server", InstanceLayout.defaultInventoryProfile("eu-skyblock"));
    }

    @Test
    void writeServerIdHintStampsLobbyGlobalAndCreativeServer() throws Exception {
        Path lobby = root.resolve("lobby");
        Path creative = root.resolve("creative");
        for (Path dir : new Path[] {lobby, creative}) {
            Path pd = dir.resolve("plugins/YaPPlayerData");
            Files.createDirectories(pd);
            Files.writeString(pd.resolve("config.yml"), """
                    server-id: placeholder
                    inventory-profile: server
                    """);
        }

        InstanceLayout.writeServerIdHint(lobby, "lobby");
        InstanceLayout.writeServerIdHint(creative, "creative");

        assertEquals("global", profileOf(lobby));
        assertEquals("lobby", serverIdOf(lobby));
        assertEquals("server", profileOf(creative));
        assertEquals("creative", serverIdOf(creative));
    }

    private static String profileOf(Path instance) throws Exception {
        String text = Files.readString(instance.resolve("plugins/YaPPlayerData/config.yml"));
        for (String line : text.split("\n")) {
            if (line.trim().startsWith("inventory-profile:")) {
                return line.substring(line.indexOf(':') + 1).trim();
            }
        }
        throw new AssertionError("missing inventory-profile in " + text);
    }

    private static String serverIdOf(Path instance) throws Exception {
        String text = Files.readString(instance.resolve("plugins/YaPPlayerData/config.yml"));
        for (String line : text.split("\n")) {
            if (line.trim().startsWith("server-id:")) {
                return line.substring(line.indexOf(':') + 1).trim();
            }
        }
        throw new AssertionError("missing server-id in " + text);
    }
}
