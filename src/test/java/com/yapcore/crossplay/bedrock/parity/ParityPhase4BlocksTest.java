package com.yapcore.crossplay.bedrock.parity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Phase 4 DoD: 24 convert-verified ports, placement contract, textures present, no Partial.
 */
final class ParityPhase4BlocksTest {

    private static BedrockPortBlockRegistry registry;
    private static Path packRoot;

    @BeforeAll
    static void load() {
        registry = BedrockPortBlockRegistry.loadDefault();
        packRoot = Path.of("resourcepacks/yap-bedrock-blocks");
        if (!Files.isDirectory(packRoot)) {
            packRoot = Path.of("../resourcepacks/yap-bedrock-blocks");
        }
    }

    @Test
    void twentyFourPortsWithRuntimeIndex() {
        assertEquals(24, registry.size());
        for (BedrockPortBlockRegistry.PortBlock pb : registry.all()) {
            assertTrue(pb.bedrockRuntimeIndex() >= 0, pb.bedrockId() + " missing runtime index");
            assertTrue(pb.jePortId().startsWith("yapbedrock:"), pb.jePortId());
            assertFalse(pb.modelPath().isBlank(), pb.bedrockId());
        }
    }

    @Test
    void resourcePackHasModelsAndTextures() throws Exception {
        assertTrue(Files.isDirectory(packRoot), "missing " + packRoot.toAbsolutePath());
        JsonObject report = JsonParser.parseString(
                Files.readString(packRoot.resolve("EXTRACT_REPORT.json"))).getAsJsonObject();
        assertEquals(24, report.get("catalog_blocks").getAsInt());
        assertEquals(24, report.get("models_written").getAsInt());
        assertTrue(report.get("textures_copied").getAsInt() >= 24);
        assertFalse(report.has("missing") && report.getAsJsonArray("missing").size() > 0,
                "EXTRACT_REPORT still lists missing textures");

        for (BedrockPortBlockRegistry.PortBlock pb : registry.all()) {
            String shortName = pb.shortName();
            Path model = packRoot.resolve("assets/yapbedrock/models/block/" + shortName + ".json");
            Path item = packRoot.resolve("assets/yapbedrock/models/item/" + shortName + ".json");
            assertTrue(Files.isRegularFile(model), "missing block model " + model);
            assertTrue(Files.isRegularFile(item), "missing item model " + item);
        }
        assertTrue(Files.isRegularFile(packRoot.resolve("assets/minecraft/models/item/paper.json")));
        assertTrue(Files.isRegularFile(packRoot.resolve("assets/minecraft/models/item/brick.json")));
    }

    @Test
    void cmdMapCoversCatalog() throws Exception {
        JsonObject cmd = JsonParser.parseString(Files.readString(packRoot.resolve("CMD_MAP.json")))
                .getAsJsonObject();
        assertEquals(7001, cmd.get("cmd_start").getAsInt());
        assertEquals(7024, cmd.get("cmd_end").getAsInt());
        assertEquals(24, cmd.getAsJsonArray("entries").size());
    }

    @Test
    void movementFaceAssistEnabledInCatalog() {
        MovementParityTable table = MovementParityTable.loadDefault();
        assertTrue(table.faceAssist());
        assertEquals(1.0, table.get(MovementParityTable.ID_FACE_ASSIST), 1e-9);
    }

    @Test
    void yapblockFormatMatchesCatalog() {
        for (ParityCatalogs.CatalogEntry e : ParityCatalogs.loadDefault().blocks().entries()) {
            String slug = com.yapcore.crossplay.bedrock.parity.convert.ConvertPipeline
                    .blockIdToFixtureFile(e.id()).replace(".json", "");
            String path = ParityBand.of(ParityBand.DEFAULT)
                    .convertedPath("block/" + slug + ".yapblock.json");
            try (InputStream in = ParityPhase4BlocksTest.class.getClassLoader().getResourceAsStream(path)) {
                assertTrue(in != null, "missing " + path);
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                assertEquals("yap.blockstate/1", root.get("format").getAsString());
                assertEquals(e.id(), root.get("bedrock_id").getAsString());
            } catch (Exception ex) {
                throw new AssertionError(path, ex);
            }
        }
    }
}
