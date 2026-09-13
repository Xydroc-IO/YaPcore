package com.yapcore.crossplay.bedrock.parity.convert;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.yapcore.crossplay.bedrock.parity.ParityBand;
import com.yapcore.crossplay.bedrock.parity.ParityCatalogs;
import com.yapcore.crossplay.bedrock.parity.ProvenanceManifest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Runs all Phase 0 converters for a band and optionally writes converted/ + provenance. */
public final class ConvertPipeline {

    private static final Gson PRETTY = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    public record Artifact(String role, String relativePath, String json, String sourceRelative) {}

    private ConvertPipeline() {}

    /** Convert all fixtures available on the classpath (tests + tooling without a tree). */
    public static List<Artifact> convertClasspathFixtures(ParityBand band) throws IOException {
        return convert(band, null);
    }

    /** Convert preferring on-disk fixtures under {@code bandDir} when present. */
    public static List<Artifact> convertFromTree(ParityBand band, Path bandDir) throws IOException {
        return convert(band, bandDir);
    }

    /**
     * @deprecated Stub generation is forbidden — fixtures must come from Mojang bedrock-samples
     * or {@code YAP_BEDROCK_VANILLA_RP}. Always returns 0.
     */
    @Deprecated
    public static int ensureCatalogFixtureStubs(ParityBand band, Path bandDir) {
        return 0;
    }

    private static List<Artifact> convert(ParityBand band, Path bandDir) throws IOException {
        List<Artifact> out = new ArrayList<>();
        out.add(convertOne(band, bandDir, "skin", "geometry.humanoid.custom.json",
                "skin/geometry.humanoid.custom.yapgeo.json", "fixture-skin-geo",
                GeometryConverter::convert));
        out.add(convertOne(band, bandDir, "skin", "geometry.humanoid.customSlim.json",
                "skin/geometry.humanoid.customSlim.yapgeo.json", "fixture-skin-geo-slim",
                GeometryConverter::convert));

        for (String fixtureFile : listFixtureFiles(band, bandDir, "block", ".json", listExpectedBlockFixtures(band))) {
            String slug = fixtureFile.endsWith(".json")
                    ? fixtureFile.substring(0, fixtureFile.length() - ".json".length()) : fixtureFile;
            String role = "minecraft_allow".equals(slug) ? "fixture-block-allow" : "fixture-block-" + slug;
            out.add(convertOne(band, bandDir, "block", fixtureFile,
                    "block/" + slug + ".yapblock.json", role, BlockStateConverter::convert));
        }

        for (String fixtureFile : listFixtureFiles(band, bandDir, "emote", ".emote.json", listExpectedEmoteFixtures(band))) {
            String slug = fixtureFile.replace(".emote.json", "");
            String role = "wave".equals(slug) ? "fixture-emote-wave" : "fixture-emote-" + slug;
            out.add(convertOne(band, bandDir, "emote", fixtureFile,
                    "emote/" + slug + ".yapemote.json", role, EmoteConverter::convert));
        }

        for (String fixtureFile : listFixtureFiles(band, bandDir, "animation", ".json", listExpectedAnimationFixtures(band))) {
            String slug = fixtureFile.endsWith(".json")
                    ? fixtureFile.substring(0, fixtureFile.length() - ".json".length()) : fixtureFile;
            String role = "first_person_attack".equals(slug) ? "fixture-anim-fp-attack" : "fixture-anim-" + slug;
            out.add(convertOne(band, bandDir, "animation", fixtureFile,
                    "animation/" + slug + ".yapanim.json", role, AnimationConverter::convert));
        }
        return out;
    }

    /** @deprecated Prefer catalog-required emote fixtures via {@link #listExpectedEmoteFixtures}. */
    @Deprecated
    private static List<String> listFixtureFilesOptional(
            ParityBand band, Path bandDir, String fixtureDir, String suffix) throws IOException {
        try {
            return listFixtureFiles(band, bandDir, fixtureDir, suffix, List.of());
        } catch (IOException e) {
            return List.of();
        }
    }

    private static Artifact convertOne(
            ParityBand band,
            Path bandDir,
            String fixtureDir,
            String fixtureFile,
            String convertedRelative,
            String role,
            Converter converter) throws IOException {
        String fixtureRel = fixtureDir + "/" + fixtureFile;
        String src;
        if (bandDir != null) {
            Path onDisk = bandDir.resolve("fixtures").resolve(fixtureDir).resolve(fixtureFile);
            if (Files.isRegularFile(onDisk)) {
                src = Files.readString(onDisk, StandardCharsets.UTF_8);
            } else {
                src = readResource(band.fixturePath(fixtureRel));
            }
        } else {
            src = readResource(band.fixturePath(fixtureRel));
        }
        JsonObject converted = converter.convert(src);
        String json = PRETTY.toJson(converted) + "\n";
        return new Artifact(role, convertedRelative, json, fixtureRel);
    }

    public static ProvenanceManifest buildManifest(ParityBand band, List<Artifact> artifacts) throws IOException {
        return buildManifest(band, artifacts, null);
    }

    public static ProvenanceManifest buildManifest(ParityBand band, List<Artifact> artifacts, Path bandDir)
            throws IOException {
        List<ProvenanceManifest.Entry> entries = new ArrayList<>();
        // Fixtures referenced by artifacts (dedupe by role)
        Map<String, Artifact> byRole = new LinkedHashMap<>();
        for (Artifact a : artifacts) {
            byRole.putIfAbsent(a.role(), a);
        }
        for (Artifact a : byRole.values()) {
            String path = band.fixturePath(a.sourceRelative());
            byte[] bytes = readFixtureBytes(band, bandDir, a.sourceRelative());
            entries.add(new ProvenanceManifest.Entry(
                    a.role(),
                    path,
                    ProvenanceManifest.sha256Hex(bytes),
                    "bedrock-extract-fixture"));
        }
        // Converted (per artifact)
        List<Artifact> blockConverted = new ArrayList<>();
        for (Artifact a : artifacts) {
            String path = band.convertedPath(a.relativePath());
            String convertedRole = "converted-" + a.role().replace("fixture-", "");
            entries.add(new ProvenanceManifest.Entry(
                    convertedRole,
                    path,
                    ProvenanceManifest.sha256HexUtf8(a.json()),
                    band.fixturePath(a.sourceRelative())));
            if (a.relativePath().startsWith("block/") && a.relativePath().endsWith(".yapblock.json")) {
                blockConverted.add(a);
            }
        }
        // Aggregate role for all converted catalog blocks
        if (!blockConverted.isEmpty()) {
            blockConverted.sort(Comparator.comparing(Artifact::relativePath));
            StringBuilder agg = new StringBuilder();
            for (Artifact a : blockConverted) {
                agg.append(a.relativePath()).append('\n').append(a.json());
            }
            entries.add(new ProvenanceManifest.Entry(
                    "catalog-blocks-converted",
                    band.convertedPath("block/"),
                    ProvenanceManifest.sha256HexUtf8(agg.toString()),
                    "count=" + blockConverted.size()));
        }
        // Catalogs
        for (String cat : List.of("blocks.v1.json", "emotes.v1.json", "animations.v1.json", "movement.v1.json")) {
            String path = band.catalogPath(cat);
            entries.add(new ProvenanceManifest.Entry(
                    "catalog-" + cat.replace(".v1.json", ""),
                    path,
                    ProvenanceManifest.sha256Hex(readResourceBytes(path)),
                    null));
        }
        return new ProvenanceManifest(band.id(), 1, entries);
    }

    public static void writeToTree(Path parityBandDir, List<Artifact> artifacts, ProvenanceManifest manifest)
            throws IOException {
        // Drop stale converted emotes when fixtures were removed (no stub regen).
        Path emoteOut = parityBandDir.resolve("converted").resolve("emote");
        if (Files.isDirectory(emoteOut)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(emoteOut, "*.yapemote.json")) {
                for (Path p : stream) {
                    Files.deleteIfExists(p);
                }
            }
        }
        for (Artifact a : artifacts) {
            Path out = parityBandDir.resolve("converted").resolve(a.relativePath());
            Files.createDirectories(out.getParent());
            Files.writeString(out, a.json(), StandardCharsets.UTF_8);
        }
        manifest.write(parityBandDir.resolve("provenance").resolve("manifest.v1.json"));
    }

    public static String blockIdToFixtureFile(String bedrockId) {
        return bedrockId.replace("minecraft:", "minecraft_").replace(':', '_') + ".json";
    }

    public static String emoteFixtureSlug(ParityCatalogs.CatalogEntry e) {
        String name = e.string("name");
        if (name != null && !name.isBlank()) {
            return slugify(name);
        }
        return slugify(e.id());
    }

    public static String animationFixtureSlug(String animationId) {
        if ("animation.player.first_person.attack_rotation".equals(animationId)) {
            return "first_person_attack"; // preserve committed fixture name
        }
        String s = animationId;
        if (s.startsWith("controller.animation.")) {
            s = "ctrl_" + s.substring("controller.animation.".length());
        } else if (s.startsWith("animation.")) {
            s = s.substring("animation.".length());
        }
        return s.replace('.', '_');
    }

    static String slugify(String raw) {
        String s = raw.toLowerCase(Locale.ROOT)
                .replace('!', ' ')
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_|_$", "");
        return s.isBlank() ? "unnamed" : s;
    }

    private static List<String> listExpectedBlockFixtures(ParityBand band) {
        List<String> out = new ArrayList<>();
        for (ParityCatalogs.CatalogEntry e : ParityCatalogs.load(band).blocks().entries()) {
            out.add(blockIdToFixtureFile(e.id()));
        }
        return out;
    }

    private static List<String> listExpectedEmoteFixtures(ParityBand band) {
        List<String> out = new ArrayList<>();
        for (ParityCatalogs.CatalogEntry e : ParityCatalogs.load(band).emotes().entries()) {
            out.add(emoteFixtureSlug(e) + ".emote.json");
        }
        return out;
    }

    private static List<String> listExpectedAnimationFixtures(ParityBand band) {
        List<String> out = new ArrayList<>();
        for (ParityCatalogs.CatalogEntry e : ParityCatalogs.load(band).animations().entries()) {
            out.add(animationFixtureSlug(e.id()) + ".json");
        }
        return out;
    }

    private static List<String> listFixtureFiles(
            ParityBand band,
            Path bandDir,
            String fixtureDir,
            String suffix,
            List<String> expected) throws IOException {
        TreeMap<String, String> found = new TreeMap<>();
        if (bandDir != null) {
            Path dir = bandDir.resolve("fixtures").resolve(fixtureDir);
            if (Files.isDirectory(dir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*" + suffix)) {
                    for (Path p : stream) {
                        if (Files.isRegularFile(p)) {
                            found.put(p.getFileName().toString(), p.getFileName().toString());
                        }
                    }
                }
            }
        }
        for (String name : expected) {
            if (found.containsKey(name)) {
                continue;
            }
            String resourcePath = band.fixturePath(fixtureDir + "/" + name);
            if (resourceExists(resourcePath)) {
                found.put(name, name);
            }
        }
        if (found.isEmpty()) {
            throw new IOException("No fixtures found under fixtures/" + fixtureDir
                    + " — run scripts/parity/fetch-samples-and-extract.sh or set YAP_BEDROCK_VANILLA_RP");
        }
        return new ArrayList<>(found.values());
    }

    private static byte[] readFixtureBytes(ParityBand band, Path bandDir, String fixtureRel) throws IOException {
        if (bandDir != null) {
            Path onDisk = bandDir.resolve("fixtures").resolve(fixtureRel);
            if (Files.isRegularFile(onDisk)) {
                return Files.readAllBytes(onDisk);
            }
        }
        return readResourceBytes(band.fixturePath(fixtureRel));
    }

    private static boolean resourceExists(String path) {
        try (InputStream in = ConvertPipeline.class.getClassLoader().getResourceAsStream(path)) {
            return in != null;
        } catch (IOException e) {
            return false;
        }
    }

    private static String readResource(String path) throws IOException {
        return new String(readResourceBytes(path), StandardCharsets.UTF_8);
    }

    private static byte[] readResourceBytes(String path) throws IOException {
        try (InputStream in = ConvertPipeline.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Missing resource: " + path);
            }
            return in.readAllBytes();
        }
    }

    @FunctionalInterface
    private interface Converter {
        JsonObject convert(String input);
    }
}
