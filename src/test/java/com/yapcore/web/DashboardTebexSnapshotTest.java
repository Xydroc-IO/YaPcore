package com.yapcore.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardTebexSnapshotTest {

    @TempDir
    Path root;

    @Test
    void masksSecretAndReadsBuyCommand() throws Exception {
        Path cfg = root.resolve("plugins").resolve("Tebex").resolve("config.yml");
        Files.createDirectories(cfg.getParent());
        Files.writeString(cfg, """
                buy-command:
                  enabled: true
                  name: shop
                check-for-updates: false
                auto-report-enabled: false
                gui:
                  menu:
                    home:
                      title: "My Shop"
                      rows: 4
                server:
                  proxy: false
                  secret-key: abcd1234wxyz5678
                config-version: 2
                """);
        Files.writeString(root.resolve("plugins").resolve("tebex.jar"), "stub");
        seedDefaultsRecipes();

        Map<String, Object> snap = DashboardNetworkSnapshots.tebex(root);
        assertTrue((Boolean) snap.get("installed"));
        assertTrue((Boolean) snap.get("secretConfigured"));
        assertEquals("abcd…5678", snap.get("secretMasked"));
        assertEquals("shop", snap.get("buyCommandName"));
        assertTrue((Boolean) snap.get("buyCommandEnabled"));
        assertFalse((Boolean) snap.get("proxyMode"));
        assertFalse((Boolean) snap.get("checkForUpdates"));
        assertFalse((Boolean) snap.get("autoReportEnabled"));
        assertEquals("My Shop", snap.get("guiHomeTitle"));
        assertEquals(4, snap.get("guiHomeRows"));
        assertTrue(snap.get("setupHint").toString().contains("Secret set"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> recipes = (List<Map<String, Object>>) snap.get("packageRecipes");
        assertFalse(recipes.isEmpty());
        assertEquals("VIP rank", recipes.getFirst().get("name"));
    }

    @Test
    void saveSecretAndExpandedSettings() throws Exception {
        DashboardTebexWriters.saveSecret(root, "mysecretkey99");
        DashboardTebexWriters.saveSettings(root, Map.of(
                "buyCommandEnabled", "true",
                "buyCommandName", "store",
                "proxyMode", "true",
                "verbose", "true",
                "checkForUpdates", "false",
                "autoReportEnabled", "false",
                "guiHomeTitle", "Donator Shop",
                "guiHomeRows", "5"));
        Map<String, Object> snap = DashboardNetworkSnapshots.tebex(root);
        assertTrue((Boolean) snap.get("secretConfigured"));
        assertEquals("myse…ey99", snap.get("secretMasked"));
        assertEquals("store", snap.get("buyCommandName"));
        assertTrue((Boolean) snap.get("proxyMode"));
        assertTrue((Boolean) snap.get("verbose"));
        assertFalse((Boolean) snap.get("checkForUpdates"));
        assertFalse((Boolean) snap.get("autoReportEnabled"));
        assertEquals("Donator Shop", snap.get("guiHomeTitle"));
        assertEquals(5, snap.get("guiHomeRows"));
    }

    @Test
    void maskSecretShortKeysFullyHidden() {
        assertEquals("••••••••", DashboardNetworkSnapshots.maskSecret("short"));
        assertEquals("", DashboardNetworkSnapshots.maskSecret(""));
    }

    @Test
    void recipesAreConfigDriven() throws Exception {
        seedDefaultsRecipes();
        DashboardTebexRecipes.ensureFile(root);
        DashboardTebexRecipes.upsertRecipe(root, "Coins", "yapeco give {username} 1000");
        List<Map<String, Object>> recipes = DashboardTebexRecipes.load(root);
        assertTrue(recipes.stream().anyMatch(r -> "Coins".equals(r.get("name"))));
        DashboardTebexRecipes.deleteRecipe(root, "Coins");
        assertTrue(DashboardTebexRecipes.load(root).stream().noneMatch(r -> "Coins".equals(r.get("name"))));
        assertThrows(IllegalArgumentException.class,
                () -> DashboardTebexRecipes.saveYamlText(root, "not-a-mapping"));
    }

    @Test
    void parsesTebexInfoAndCachesForceCheck() {
        String info = """
                Information for this server:
                Lobby for webstore AcmeSMP
                Server prices are in USD
                Webstore URL: https://store.example.com
                """;
        Map<String, Object> parsed = DashboardTebexStatus.rememberInfo(info);
        assertEquals("AcmeSMP", parsed.get("storeName"));
        assertEquals("Lobby", parsed.get("serverName"));
        assertEquals("USD", parsed.get("currency"));
        assertEquals("https://store.example.com", parsed.get("webstoreUrl"));
        assertTrue((Boolean) parsed.get("connected"));

        Map<String, Object> check = DashboardTebexStatus.rememberForceCheck("Check completed. See console for details.");
        assertTrue((Boolean) check.get("lastCheckOk"));
        assertFalse(DashboardNetworkSnapshots.str(check.get("lastCheckAt"), "").isBlank());
        assertEquals("AcmeSMP", DashboardTebexStatus.cached().get("storeName"));
    }

    @Test
    void hubOnlyPlacementDetectsNonHubInstall() throws Exception {
        Files.createDirectories(root.resolve("fleet/instances/lobby/plugins"));
        Files.createDirectories(root.resolve("fleet/instances/survival/plugins"));
        Files.writeString(root.resolve("fleet/fleet.json"), """
                {"schemaVersion":1,"primaryId":"lobby","instances":[]}
                """);
        Files.writeString(root.resolve("fleet/instances/lobby/plugins/tebex.jar"), "hub");
        Files.writeString(root.resolve("fleet/instances/survival/plugins/tebex.jar"), "wrong");

        Map<String, Object> place = DashboardTebexStatus.hubPlacement(root);
        assertFalse((Boolean) place.get("hubOnlyOk"));
        assertTrue(place.get("hubPlacementDetail").toString().contains("survival"));

        Files.delete(root.resolve("fleet/instances/survival/plugins/tebex.jar"));
        place = DashboardTebexStatus.hubPlacement(root);
        assertTrue((Boolean) place.get("hubOnlyOk"));
        assertTrue(place.get("hubPlacementDetail").toString().contains("hub only"));
    }

    @Test
    void opsSummaryReportsHubPlacement() throws Exception {
        Files.createDirectories(root.resolve("fleet/instances/lobby/plugins"));
        Files.createDirectories(root.resolve("fleet/instances/survival/plugins"));
        Files.writeString(root.resolve("fleet/fleet.json"), "{\"primaryId\":\"lobby\"}");
        Files.writeString(root.resolve("fleet/instances/lobby/plugins/tebex.jar"), "hub");
        Files.writeString(root.resolve("fleet/instances/survival/plugins/tebex.jar"), "wrong");
        Path cfg = root.resolve("plugins").resolve("Tebex").resolve("config.yml");
        Files.createDirectories(cfg.getParent());
        Files.writeString(cfg, "server:\n  secret-key: abcd1234wxyz5678\n");
        seedDefaultsRecipes();

        Map<String, Object> ops = DashboardOpsSnapshots.opsPhase8Summary(root);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> plugins = (List<Map<String, Object>>) ops.get("plugins");
        Map<String, Object> tebex = plugins.stream()
                .filter(p -> "Tebex".equals(p.get("label")))
                .findFirst()
                .orElseThrow();
        assertTrue(tebex.get("detail").toString().contains("survival"));
    }

    @Test
    void kitGrantQueueListsPendingAndStuck() throws Exception {
        Path db = root.resolve("data/yap-test.db");
        Files.createDirectories(db.getParent());
        String jdbc = "jdbc:sqlite:" + db.toAbsolutePath();
        try (Connection c = DriverManager.getConnection(jdbc); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE kit_grants (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      uuid CHAR(36) NOT NULL,
                      kit VARCHAR(32) NOT NULL,
                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      delivered_at TIMESTAMP NULL
                    )
                    """);
            st.execute("""
                    CREATE TABLE players (
                      uuid CHAR(36) PRIMARY KEY,
                      name VARCHAR(16) NOT NULL,
                      balance DECIMAL(20,2) NOT NULL DEFAULT 0,
                      xp INT NOT NULL DEFAULT 0,
                      level INT NOT NULL DEFAULT 0,
                      health DOUBLE NOT NULL DEFAULT 20,
                      food INT NOT NULL DEFAULT 20,
                      saturation FLOAT NOT NULL DEFAULT 5,
                      inventory BLOB NOT NULL,
                      enderchest BLOB NOT NULL
                    )
                    """);
            st.execute("INSERT INTO players (uuid, name, inventory, enderchest) VALUES "
                    + "('11111111-1111-1111-1111-111111111111', 'Steve', X'00', X'00')");
            st.execute("INSERT INTO kit_grants (uuid, kit) VALUES "
                    + "('11111111-1111-1111-1111-111111111111', 'vip')");
            st.execute("INSERT INTO kit_grants (uuid, kit) VALUES "
                    + "('22222222-2222-2222-2222-222222222222', 'missing_pack')");
        }
        Path yapdb = root.resolve("plugins").resolve("YaPDB").resolve("config.yml");
        Files.createDirectories(yapdb.getParent());
        Files.writeString(yapdb, """
                jdbc:
                  url: %s
                  user: yap
                  password: ""
                """.formatted(jdbc));
        Path kits = root.resolve("plugins").resolve("YaPPlayerData").resolve("kits.yml");
        Files.createDirectories(kits.getParent());
        Files.writeString(kits, """
                kits:
                  vip:
                    delay-seconds: 1
                    items: []
                """);

        Map<String, Object> grants = DashboardTebexKitGrants.snapshot(root);
        assertTrue((Boolean) grants.get("dbOk"));
        assertEquals(1, grants.get("pendingCount"));
        assertEquals(1, grants.get("stuckCount"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pending = (List<Map<String, Object>>) grants.get("pendingGrants");
        assertEquals("Steve", pending.getFirst().get("username"));
        assertEquals("vip", pending.getFirst().get("kit"));

        long stuckId = ((Number) ((List<Map<String, Object>>) grants.get("stuckGrants")).getFirst().get("id")).longValue();
        assertTrue(DashboardTebexKitGrants.cancelGrant(root, stuckId));
        Map<String, Object> after = DashboardTebexKitGrants.snapshot(root);
        assertEquals(0, after.get("stuckCount"));
    }

    @Test
    void postBodyActionsCoveredViaWritersAndParsers() throws Exception {
        // Mirrors /api/tebex POST set-secret | save-settings | save-recipes paths without HTTP.
        Map<String, String> secretBody = TinyJson.parseFlatObject(
                "{\"action\":\"set-secret\",\"secret\":\"postsecretkey01\"}");
        assertEquals("set-secret", secretBody.get("action"));
        DashboardTebexWriters.saveSecret(root, secretBody.get("secret"));

        Map<String, String> settingsBody = TinyJson.parseFlatObject("""
                {"action":"save-settings","buyCommandEnabled":"false","buyCommandName":"buy","proxyMode":"false","verbose":"true","checkForUpdates":"true","autoReportEnabled":"true","guiHomeTitle":"Shop","guiHomeRows":"3"}
                """);
        DashboardTebexWriters.saveSettings(root, settingsBody);

        seedDefaultsRecipes();
        Map<String, String> recipeBody = TinyJson.parseFlatObject(
                "{\"action\":\"upsert-recipe\",\"name\":\"Test Pack\",\"commands\":\"kit grant {username} vip\"}");
        DashboardTebexRecipes.upsertRecipe(root, recipeBody.get("name"), recipeBody.get("commands"));

        Map<String, Object> snap = DashboardNetworkSnapshots.tebex(root);
        assertTrue((Boolean) snap.get("secretConfigured"));
        assertFalse((Boolean) snap.get("buyCommandEnabled"));
        assertTrue((Boolean) snap.get("verbose"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> recipes = (List<Map<String, Object>>) snap.get("packageRecipes");
        assertTrue(recipes.stream().anyMatch(r -> "Test Pack".equals(r.get("name"))));
    }

    @Test
    void webhookInboundSnapshotAndSave() throws Exception {
        DashboardNetworkSnapshotWriters.saveTebexWebhook(root, true, 8766, "webhook-secret-xyz", false);
        Files.writeString(root.resolve("plugins").resolve("yap-tebex.jar"), "stub");

        Map<String, Object> snap = DashboardNetworkSnapshots.tebex(root);
        assertTrue((Boolean) snap.get("webhookInstalled"));
        assertTrue((Boolean) snap.get("webhookEnabled"));
        assertTrue((Boolean) snap.get("webhookSecretConfigured"));
        assertFalse((Boolean) snap.get("webhookEnforceIps"));
        assertEquals(8766, snap.get("webhookPort"));
        assertEquals("https://<public>/tebex/webhook", snap.get("webhookUrlHint"));
        assertTrue(snap.get("webhookListenHint").toString().contains("127.0.0.1:8766"));
    }

    private void seedDefaultsRecipes() throws Exception {
        Path defaults = root.resolve("config/defaults/tebex-recipes.yml");
        Files.createDirectories(defaults.getParent());
        Files.writeString(defaults, """
                recipes:
                  - name: VIP rank
                    commands: |
                      yapperm user {username} parent set vip
                      kit grant {username} vip
                  - name: Adventurer kit unlock
                    commands: |
                      yapperm user {username} permission set yapdata.kit.adventurer true
                      kit grant {username} adventurer
                  - name: VIP kit unlock only
                    commands: |
                      yapperm user {username} permission set yapdata.kit.vip true
                """);
    }
}
