package com.yapcore.resourcepack;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Merges multiple active resource packs into one zip for Paper's single login-prompt slot.
 * Later packs overwrite earlier entries on path conflicts.
 */
public final class ResourcePackBundler {

    private static final Logger LOG = Logger.getLogger("YaPcore.ResourcePacks");
    public static final String BUNDLE_PREFIX = "yap-active-bundle-";

    private ResourcePackBundler() {
    }

    /**
     * @return pack file name under {@code packsDir} that clients should download
     *         (single active as-is, or a freshly built/cached merge when multiple).
     */
    public static String ensureOfferFile(Path packsDir, List<String> activeFiles) throws IOException {
        if (activeFiles == null || activeFiles.isEmpty()) {
            return "";
        }
        List<String> inputs = new ArrayList<>();
        for (String f : activeFiles) {
            if (f == null || f.isBlank()) {
                continue;
            }
            String n = f.trim();
            if (n.startsWith(BUNDLE_PREFIX)) {
                continue; // never nest bundles
            }
            Path p = packsDir.resolve(n);
            if (!Files.isRegularFile(p)) {
                throw new IOException("Active pack missing: " + n);
            }
            inputs.add(n);
        }
        if (inputs.isEmpty()) {
            return "";
        }
        if (inputs.size() == 1) {
            return inputs.get(0);
        }

        String fingerprint = fingerprint(packsDir, inputs);
        String outName = BUNDLE_PREFIX + fingerprint.substring(0, 8) + ".zip";
        Path out = packsDir.resolve(outName);
        if (Files.isRegularFile(out)) {
            LOG.info("Using cached multi-pack bundle " + outName + " (" + inputs.size() + " packs)");
            return outName;
        }

        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (String name : inputs) {
            mergeZip(packsDir.resolve(name), entries);
        }
        if (!entries.containsKey("pack.mcmeta")) {
            entries.put("pack.mcmeta", """
                    {"pack":{"pack_format":34,"supported_formats":{"min_inclusive":22,"max_inclusive":99},"description":"YaPcore active pack bundle"}}
                    """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        Path tmp = packsDir.resolve(outName + ".tmp");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tmp))) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        Files.move(tmp, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        LOG.info("Built multi-pack bundle " + outName + " from " + String.join(" + ", inputs)
                + " (" + entries.size() + " entries, sha1=" + sha1Hex(out) + ")");
        return outName;
    }

    private static void mergeZip(Path zip, Map<String, byte[]> into) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name == null || name.isBlank() || name.contains("..")) {
                    continue;
                }
                byte[] data = zis.readAllBytes();
                into.put(name, data); // later wins
            }
        }
    }

    private static String fingerprint(Path packsDir, List<String> inputs) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (Exception e) {
            throw new IOException(e);
        }
        for (String name : inputs) {
            md.update(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(sha1Hex(packsDir.resolve(name)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            md.update((byte) 0);
        }
        return HexFormat.of().formatHex(md.digest());
    }

    static String sha1Hex(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream in = Files.newInputStream(path);
                 DigestInputStream din = new DigestInputStream(in, digest)) {
                din.transferTo(OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new IOException("SHA-1 failed for " + path, e);
        }
    }

    public static boolean isBundleName(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).startsWith(BUNDLE_PREFIX);
    }
}
