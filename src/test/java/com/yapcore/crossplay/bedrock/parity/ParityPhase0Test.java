package com.yapcore.crossplay.bedrock.parity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yapcore.crossplay.bedrock.parity.convert.BlockStateConverter;
import com.yapcore.crossplay.bedrock.parity.convert.ConvertPipeline;
import com.yapcore.crossplay.bedrock.parity.convert.EmoteConverter;
import com.yapcore.crossplay.bedrock.parity.convert.GeometryConverter;
import com.yapcore.crossplay.bedrock.parity.extract.PaletteBlockExtractor;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ParityPhase0Test {

    private static final ParityBand BAND = ParityBand.of(ParityBand.DEFAULT);

    @Test
    void catalogsLoadFrozenSizes() {
        ParityCatalogs c = ParityCatalogs.load(BAND);
        assertEquals("band_26_50", c.band().id());
        assertEquals(24, c.blocks().size(), "frozen block catalog");
        assertEquals(4, c.emotes().size(), "frozen free persona emote catalog");
        assertEquals(12, c.animations().size(), "frozen animation catalog");
        assertEquals(10, c.movement().size(), "frozen movement constants");
        assertTrue(c.block("minecraft:allow").isPresent());
        assertTrue(c.emote("4c8ae710-df2e-47cd-814d-cc7bf21a3d67").isPresent());
        assertFalse(c.blocks().rule().isBlank());
    }

    @Test
    void everyCatalogBlockExistsInCloudburstPalette() throws Exception {
        Map<String, JsonObject> extracted = PaletteBlockExtractor.extractCatalogBlocks(BAND);
        assertEquals(24, extracted.size());
        JsonObject allow = extracted.get("minecraft:allow");
        assertTrue(allow.get("runtime_index").getAsInt() >= 0);
        assertEquals("minecraft:allow", allow.get("id").getAsString());
    }

    @Test
    void geometryConvertIsDeterministicAndMatchesGolden() throws Exception {
        String fixture = read(BAND.fixturePath("skin/geometry.humanoid.custom.json"));
        JsonObject a = GeometryConverter.convert(fixture);
        JsonObject b = GeometryConverter.convert(fixture);
        assertEquals(a, b, "converter must be deterministic");
        assertEquals("yap.geometry/1", a.get("format").getAsString());
        assertTrue(a.getAsJsonArray("geometries").size() >= 1);
        String identifier = a.getAsJsonArray("geometries").get(0).getAsJsonObject()
                .get("identifier").getAsString();
        assertEquals("geometry.humanoid.custom", identifier);

        String golden = read(BAND.convertedPath("skin/geometry.humanoid.custom.yapgeo.json")).trim();
        String actual = ParityCatalogs.toPretty(a).trim();
        assertEquals(ProvenanceManifest.sha256HexUtf8(golden + "\n"),
                ProvenanceManifest.sha256HexUtf8(actual + "\n"),
                "geometry golden sha mismatch — re-run parityConvert if fixture changed intentionally");
        assertEquals(normalize(golden), normalize(actual));
    }

    @Test
    void blockConvertPortsBedrockId() throws Exception {
        String fixture = read(BAND.fixturePath("block/minecraft_allow.json"));
        JsonObject out = BlockStateConverter.convert(fixture);
        assertEquals("yap.blockstate/1", out.get("format").getAsString());
        assertEquals("minecraft:allow", out.get("bedrock_id").getAsString());
        assertEquals("yapbedrock:allow", out.get("je_port_id").getAsString());

        String golden = read(BAND.convertedPath("block/minecraft_allow.yapblock.json")).trim();
        assertEquals(normalize(golden), normalize(ParityCatalogs.toPretty(out).trim()));
    }

    @Test
    void allCatalogBlocksHaveConvertedYapblock() throws Exception {
        ParityCatalogs c = ParityCatalogs.load(BAND);
        var artifacts = ConvertPipeline.convertClasspathFixtures(BAND);
        long blockConverted = artifacts.stream()
                .filter(a -> a.relativePath().startsWith("block/") && a.relativePath().endsWith(".yapblock.json"))
                .count();
        assertEquals(c.blocks().size(), blockConverted, "blocks.v1.json size == converted block yapblock files");
        for (ParityCatalogs.CatalogEntry e : c.blocks().entries()) {
            String file = ConvertPipeline.blockIdToFixtureFile(e.id()).replace(".json", ".yapblock.json");
            assertTrue(resourceExists(BAND.convertedPath("block/" + file)), "missing converted " + file);
        }
    }

    @Test
    void emoteConvertPortsWaveUuidAndBones() throws Exception {
        String fixture = read(BAND.fixturePath("emote/wave.emote.json"));
        JsonObject out = EmoteConverter.convert(fixture);
        assertEquals("yap.emote/1", out.get("format").getAsString());
        assertEquals("4c8ae710-df2e-47cd-814d-cc7bf21a3d67", out.get("bedrock_emote_id").getAsString());
        assertTrue(out.getAsJsonObject("clip").getAsJsonObject("bones").has("rightArm"));

        String golden = read(BAND.convertedPath("emote/wave.yapemote.json")).trim();
        assertEquals(normalize(golden), normalize(ParityCatalogs.toPretty(out).trim()));
    }

    @Test
    void allCatalogEmotesAndAnimationsConverted() throws Exception {
        ParityCatalogs c = ParityCatalogs.load(BAND);
        var artifacts = ConvertPipeline.convertClasspathFixtures(BAND);
        long emotes = artifacts.stream()
                .filter(a -> a.relativePath().startsWith("emote/") && a.relativePath().endsWith(".yapemote.json"))
                .count();
        long anims = artifacts.stream()
                .filter(a -> a.relativePath().startsWith("animation/") && a.relativePath().endsWith(".yapanim.json"))
                .count();
        assertEquals(c.emotes().size(), emotes);
        assertEquals(c.animations().size(), anims);
    }

    @Test
    void pipelineAndProvenanceCoverRequiredRoles() throws Exception {
        var artifacts = ConvertPipeline.convertClasspathFixtures(BAND);
        // 2 geos + 24 blocks + 4 emotes + 12 anims
        assertEquals(2 + 24 + 4 + 12, artifacts.size());
        ProvenanceManifest built = ConvertPipeline.buildManifest(BAND, artifacts);
        assertEquals(BAND.id(), built.band());
        built.require("fixture-skin-geo");
        built.require("fixture-block-allow");
        built.require("fixture-emote-wave");
        built.require("fixture-anim-fp-attack");
        built.require("converted-skin-geo");
        built.require("converted-block-allow");
        built.require("converted-emote-wave");
        built.require("converted-anim-fp-attack");
        built.require("catalog-blocks");
        ProvenanceManifest.Entry blocksConverted = built.require("catalog-blocks-converted");
        assertEquals("count=24", blocksConverted.source());

        ProvenanceManifest onClasspath = ProvenanceManifest.load(BAND);
        assertEquals(built.require("fixture-skin-geo").sha256(),
                onClasspath.require("fixture-skin-geo").sha256());
        assertEquals(built.require("converted-skin-geo").sha256(),
                onClasspath.require("converted-skin-geo").sha256());
        assertEquals(built.require("converted-block-allow").sha256(),
                onClasspath.require("converted-block-allow").sha256());
        assertEquals(built.require("converted-emote-wave").sha256(),
                onClasspath.require("converted-emote-wave").sha256());
        assertEquals(built.require("catalog-blocks-converted").sha256(),
                onClasspath.require("catalog-blocks-converted").sha256());
    }

    @Test
    void convertTwiceSameSha() throws Exception {
        List<ConvertPipeline.Artifact> a = ConvertPipeline.convertClasspathFixtures(BAND);
        List<ConvertPipeline.Artifact> b = ConvertPipeline.convertClasspathFixtures(BAND);
        for (int i = 0; i < a.size(); i++) {
            assertEquals(
                    ProvenanceManifest.sha256HexUtf8(a.get(i).json()),
                    ProvenanceManifest.sha256HexUtf8(b.get(i).json()),
                    a.get(i).role());
        }
    }

    private static String read(String path) throws Exception {
        try (InputStream in = ParityPhase0Test.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + path + " — run ./scripts/parity/convert.sh");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static boolean resourceExists(String path) {
        try (InputStream in = ParityPhase0Test.class.getClassLoader().getResourceAsStream(path)) {
            return in != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static String normalize(String json) {
        return JsonParser.parseString(json).toString();
    }
}
