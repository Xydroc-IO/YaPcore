package com.yapcore.crossplay.bedrock.parity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Provenance manifest: every fixture/converted artifact with band + sha256. */
public final class ProvenanceManifest {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private final String band;
    private final int version;
    private final List<Entry> entries;

    public ProvenanceManifest(String band, int version, List<Entry> entries) {
        this.band = Objects.requireNonNull(band);
        this.version = version;
        this.entries = List.copyOf(entries);
    }

    public static ProvenanceManifest load(ParityBand band) {
        String path = band.provenancePath();
        try (InputStream in = ProvenanceManifest.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing provenance manifest: " + path);
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                List<Entry> entries = new ArrayList<>();
                for (var el : root.getAsJsonArray("entries")) {
                    JsonObject o = el.getAsJsonObject();
                    entries.add(new Entry(
                            o.get("role").getAsString(),
                            o.get("path").getAsString(),
                            o.get("sha256").getAsString(),
                            o.has("source") ? o.get("source").getAsString() : null));
                }
                return new ProvenanceManifest(
                        root.get("band").getAsString(),
                        root.get("version").getAsInt(),
                        entries);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load provenance " + path, e);
        }
    }

    public static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String sha256Hex(Path file) throws IOException {
        return sha256Hex(Files.readAllBytes(file));
    }

    public static String sha256HexUtf8(String text) {
        return sha256Hex(text.getBytes(StandardCharsets.UTF_8));
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("band", band);
        root.addProperty("version", version);
        root.addProperty("rule", "sha256 of exact bytes used by convert golden tests; do not hand-edit converted outputs");
        JsonArray arr = new JsonArray();
        for (Entry e : entries) {
            JsonObject o = new JsonObject();
            o.addProperty("role", e.role());
            o.addProperty("path", e.path());
            o.addProperty("sha256", e.sha256());
            if (e.source() != null) {
                o.addProperty("source", e.source());
            }
            arr.add(o);
        }
        root.add("entries", arr);
        return root;
    }

    public String toPrettyJson() {
        return GSON.toJson(toJson());
    }

    public void write(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, toPrettyJson() + "\n", StandardCharsets.UTF_8);
    }

    public String band() {
        return band;
    }

    public int version() {
        return version;
    }

    public List<Entry> entries() {
        return entries;
    }

    public Entry require(String role) {
        for (Entry e : entries) {
            if (e.role().equals(role)) {
                return e;
            }
        }
        throw new IllegalStateException("Missing provenance role: " + role);
    }

    public record Entry(String role, String path, String sha256, String source) {}
}
