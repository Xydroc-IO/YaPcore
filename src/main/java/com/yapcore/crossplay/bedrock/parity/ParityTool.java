package com.yapcore.crossplay.bedrock.parity;

import com.yapcore.crossplay.bedrock.parity.convert.ConvertPipeline;
import com.yapcore.crossplay.bedrock.parity.extract.PaletteBlockExtractor;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * CLI for Phase 0 extract/convert.
 * <pre>
 *   ParityTool extract [band] [repoRoot]
 *   ParityTool convert [band] [repoRoot]
 *   ParityTool all [band] [repoRoot]
 * </pre>
 */
public final class ParityTool {

    private ParityTool() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            usage();
            System.exit(2);
        }
        String cmd = args[0];
        String bandId = args.length > 1 ? args[1] : ParityBand.DEFAULT;
        Path root = Path.of(args.length > 2 ? args[2] : ".").toAbsolutePath().normalize();
        ParityBand band = ParityBand.of(bandId);
        Path bandDir = root.resolve("src/main/resources/protocol/bedrock/parity").resolve(band.id());

        switch (cmd) {
            case "extract" -> extract(band, bandDir);
            case "convert" -> convert(band, bandDir);
            case "all" -> {
                extract(band, bandDir);
                convert(band, bandDir);
            }
            case "summary" -> System.out.println(PaletteBlockExtractor.pretty(
                    PaletteBlockExtractor.catalogSummary(band)));
            default -> {
                usage();
                System.exit(2);
            }
        }
    }

    private static void extract(ParityBand band, Path bandDir) throws Exception {
        System.out.println("Extracting catalog blocks from Cloudburst palette for " + band);
        Map<String, com.google.gson.JsonObject> fixtures = PaletteBlockExtractor.extractCatalogBlocks(band);
        PaletteBlockExtractor.writeBlockFixtures(bandDir, fixtures);
        System.out.println("Wrote " + fixtures.size() + " block fixtures under " + bandDir.resolve("fixtures/block"));

        String vanilla = System.getenv("YAP_BEDROCK_VANILLA_RP");
        if (vanilla != null && !vanilla.isBlank()) {
            Path rp = Path.of(vanilla);
            int n = PaletteBlockExtractor.copyFromVanillaRp(rp, bandDir, List.of(
                    new PaletteBlockExtractor.CopySpec(
                            "models/entity/humanoid.geo.json",
                            "skin/geometry.humanoid.custom.json"),
                    new PaletteBlockExtractor.CopySpec(
                            "models/entity/player.geo.json",
                            "skin/geometry.player.from_rp.json")
            ));
            System.out.println("Copied " + n + " files from YAP_BEDROCK_VANILLA_RP=" + rp);
        } else {
            System.out.println("YAP_BEDROCK_VANILLA_RP unset — keeping committed geometry/emote fixtures");
        }
        System.out.println(PaletteBlockExtractor.pretty(PaletteBlockExtractor.catalogSummary(band)));
    }

    private static void convert(ParityBand band, Path bandDir) throws Exception {
        System.out.println("Converting fixtures for " + band);
        // Stubs forbidden — fixtures must come from Mojang bedrock-samples / vanilla RP extract.
        var artifacts = ConvertPipeline.convertFromTree(band, bandDir);
        ProvenanceManifest manifest = ConvertPipeline.buildManifest(band, artifacts, bandDir);
        ConvertPipeline.writeToTree(bandDir, artifacts, manifest);
        long blockConverted = artifacts.stream()
                .filter(a -> a.relativePath().startsWith("block/") && a.relativePath().endsWith(".yapblock.json"))
                .count();
        long animConverted = artifacts.stream()
                .filter(a -> a.relativePath().startsWith("animation/") && a.relativePath().endsWith(".yapanim.json"))
                .count();
        long emoteConverted = artifacts.stream()
                .filter(a -> a.relativePath().startsWith("emote/") && a.relativePath().endsWith(".yapemote.json"))
                .count();
        System.out.println("Wrote converted/ + provenance for " + artifacts.size() + " artifacts"
                + " (blocks=" + blockConverted + " anims=" + animConverted + " emotes=" + emoteConverted + ")");
        if (emoteConverted == 0) {
            System.out.println("NOTE: no emote fixtures — set YAP_BEDROCK_VANILLA_RP for free emote bone data "
                    + "(not published in Mojang bedrock-samples). UUID catalog still authoritative.");
        }
        for (var a : artifacts) {
            System.out.println("  " + a.relativePath() + " sha256="
                    + ProvenanceManifest.sha256HexUtf8(a.json()).substring(0, 16) + "…");
        }
    }

    private static void usage() {
        System.err.println("Usage: ParityTool <extract|convert|all|summary> [band] [repoRoot]");
        System.err.println("  YAP_BEDROCK_SAMPLES_RP  Mojang bedrock-samples resource_pack root");
        System.err.println("  YAP_BEDROCK_VANILLA_RP  full game RP (required for emote bone extracts)");
    }
}
