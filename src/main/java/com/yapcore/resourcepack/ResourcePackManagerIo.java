package com.yapcore.resourcepack;

import com.yapcore.config.ServerConfig;
import com.yapcore.network.publicity.PublicEndpoint;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Pack IO / manifest / sync helpers for {@link ResourcePackManager}
 * (split for the ≤500-line domain gate).
 */
final class ResourcePackManagerIo {

    private static final Logger LOG = Logger.getLogger("YaPcore.ResourcePacks");

    private ResourcePackManagerIo() {
    }

    /**
     * When {@code resource-pack-url} is GitHub Releases, download the Bedrock
     * {@code .mcpack} into {@code resourcepacks/} so the zip HTTP CDN serves
     * the same bytes clients expect from the {@code 0.0.0.1} prerelease tag.
     */
    static void syncBedrockPackFromGitHub(ResourcePackManager mgr) {
        ServerConfig config = mgr.config();
        Path packsDir = mgr.getPacksDir();
        String override = config.getResourcePackUrl();
        String file = config.getResourcePackBedrockFile();
        if (override == null || override.isBlank() || file == null || file.isBlank()) {
            return;
        }
        if (!ResourcePackManagerOffers.isGithubAssetUrl(override)) {
            return;
        }
        String url = override.replace("{file}", file);
        Path dest = packsDir.resolve(file);
        Thread t = new Thread(() -> {
            try {
                java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                        .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                        .connectTimeout(java.time.Duration.ofSeconds(20))
                        .build();
                var req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                        .timeout(java.time.Duration.ofMinutes(3))
                        .GET()
                        .build();
                var res = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofInputStream());
                if (res.statusCode() < 200 || res.statusCode() >= 300) {
                    LOG.warning("GitHub pack sync HTTP " + res.statusCode() + " for " + url);
                    return;
                }
                Path tmp = dest.resolveSibling(file + ".download");
                try (InputStream in = res.body()) {
                    Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                }
                long size = Files.size(tmp);
                if (size < 1024) {
                    Files.deleteIfExists(tmp);
                    LOG.warning("GitHub pack sync too small (" + size + " B) — keeping existing " + file);
                    return;
                }
                normalizeBedrockMcpackManifest(tmp);
                try {
                    Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
                }
                LOG.info("Synced Bedrock pack from GitHub 0.0.0.1 prerelease → " + dest.getFileName()
                        + " (" + Files.size(dest) + " bytes); BE clients download via zip CDN");
            } catch (Exception e) {
                LOG.warning("GitHub pack sync failed: " + e.getMessage());
            }
        }, "yap-pack-github-sync");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Faithful upstream often ships {@code min_engine_version 1.26.x} + {@code capabilities:[pbr]},
     * which makes 1.21 / cracked Bedrock abort the pack handshake before any HTTP fetch.
     * Pin to product BE floor and drop PBR (same as {@code scripts/build-default-bedrock-pack.sh}).
     */
    static void normalizeBedrockMcpackManifest(Path mcpack) throws IOException {
        if (mcpack == null || !Files.isRegularFile(mcpack)) {
            return;
        }
        Path tmp = mcpack.resolveSibling(mcpack.getFileName() + ".norm");
        boolean rewritten = false;
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(mcpack.toFile());
             java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(
                     Files.newOutputStream(tmp))) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                out.putNextEntry(new java.util.zip.ZipEntry(name));
                if (!entry.isDirectory()) {
                    byte[] bytes;
                    try (InputStream in = zip.getInputStream(entry)) {
                        bytes = in.readAllBytes();
                    }
                    if ("manifest.json".equals(name) || name.endsWith("/manifest.json")) {
                        String json = new String(bytes, StandardCharsets.UTF_8);
                        String fixed = pinBedrockManifest(json);
                        if (!fixed.equals(json)) {
                            rewritten = true;
                            bytes = fixed.getBytes(StandardCharsets.UTF_8);
                        }
                    }
                    out.write(bytes);
                }
                out.closeEntry();
            }
        }
        if (rewritten) {
            try {
                Files.move(tmp, mcpack, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, mcpack, StandardCopyOption.REPLACE_EXISTING);
            }
            LOG.info("Normalized Bedrock pack manifest (min_engine=1.21.60, no PBR) in " + mcpack.getFileName());
        } else {
            Files.deleteIfExists(tmp);
        }
    }

    static String pinBedrockManifest(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        String out = json;
        // Drop capabilities array (PBR etc.)
        out = out.replaceAll("(?s),\\s*\"capabilities\"\\s*:\\s*\\[[^\\]]*\\]", "");
        out = out.replaceAll("(?s)\"capabilities\"\\s*:\\s*\\[[^\\]]*\\]\\s*,", "");
        // Pin min_engine_version to 1.21.60
        out = out.replaceAll(
                "\"min_engine_version\"\\s*:\\s*\\[[^\\]]*\\]",
                "\"min_engine_version\": [1, 21, 60]");
        return out;
    }

    static void probePackUrl(String url) {
        Thread t = new Thread(() -> {
            try {
                java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                        .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                        .connectTimeout(java.time.Duration.ofSeconds(5))
                        .build();
                var req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                        .method("HEAD", java.net.http.HttpRequest.BodyPublishers.noBody())
                        .timeout(java.time.Duration.ofSeconds(8))
                        .build();
                var res = client.send(req, java.net.http.HttpResponse.BodyHandlers.discarding());
                int code = res.statusCode();
                if (code >= 200 && code < 400) {
                    LOG.info("Pack URL probe OK (" + code + "): " + url);
                } else {
                    LOG.severe("Pack URL probe FAILED (" + code + "): " + url
                            + " — fix nginx/Cloudflare or resource-pack-url");
                }
            } catch (Exception e) {
                LOG.severe("Pack URL probe FAILED: " + url + " (" + e.getMessage() + ")");
            }
        }, "yap-pack-url-probe");
        t.setDaemon(true);
        t.start();
    }

    static Optional<UUID> readMcpackHeaderUuid(Path mcpack) {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(mcpack.toFile())) {
            java.util.zip.ZipEntry entry = zip.getEntry("manifest.json");
            if (entry == null) {
                return Optional.empty();
            }
            try (InputStream in = zip.getInputStream(entry)) {
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                int h = json.indexOf("\"header\"");
                if (h < 0) {
                    return Optional.empty();
                }
                int u = json.indexOf("\"uuid\"", h);
                if (u < 0) {
                    return Optional.empty();
                }
                int q1 = json.indexOf('"', u + 6);
                int q2 = json.indexOf('"', q1 + 1);
                if (q1 < 0 || q2 < 0) {
                    return Optional.empty();
                }
                return Optional.of(UUID.fromString(json.substring(q1 + 1, q2)));
            }
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    static void writePluginManifest(ResourcePackManager mgr) {
        try {
            Path packsDir = mgr.getPacksDir();
            ServerConfig config = mgr.config();
            Path root = packsDir.toAbsolutePath().normalize().getParent();
            if (root == null) {
                return;
            }
            Path dir = root.resolve("plugins").resolve("YaPPacks");
            Files.createDirectories(dir);
            Path file = dir.resolve("active.json");
            PublicEndpoint ep = new PublicEndpoint(config);
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"enabled\": ").append(config.isResourcePackEnabled()).append(",\n");
            json.append("  \"forced\": ").append(config.isResourcePackForced()).append(",\n");
            json.append("  \"prompt\": ").append(jsonString(config.getResourcePackPrompt())).append(",\n");
            json.append("  \"packs\": [\n");
            List<ResourcePackInfo> actives = mgr.getActivePacks();
            for (int i = 0; i < actives.size(); i++) {
                ResourcePackInfo p = actives.get(i);
                String url = ep.packUrl(p.getFileName());
                UUID id = ResourcePackManager.packUuid(p.getFileName(), p.getSha1Hex());
                json.append("    {\n");
                json.append("      \"file\": ").append(jsonString(p.getFileName())).append(",\n");
                json.append("      \"url\": ").append(jsonString(url)).append(",\n");
                json.append("      \"sha1\": ").append(jsonString(p.getSha1Hex())).append(",\n");
                json.append("      \"uuid\": ").append(jsonString(id.toString())).append("\n");
                json.append("    }").append(i + 1 < actives.size() ? "," : "").append('\n');
            }
            json.append("  ]\n");
            json.append("}\n");
            Files.writeString(file, json.toString(), StandardCharsets.UTF_8);
            // Mirror into Folia / Paper plugin trees when present
            for (String kernel : List.of("folia-kernel", "paper-kernel")) {
                Path kernelPlugins = root.resolve(kernel).resolve("plugins");
                if (Files.isDirectory(kernelPlugins) || Files.isSymbolicLink(kernelPlugins)) {
                    Path dest = kernelPlugins.resolve("YaPPacks");
                    Files.createDirectories(dest);
                    Files.writeString(dest.resolve("active.json"), json.toString(), StandardCharsets.UTF_8);
                }
            }
            LOG.info("Wrote YaPPacks manifest (" + actives.size() + " pack(s)) → " + file);
        } catch (Exception e) {
            LOG.warning("Could not write YaPPacks manifest: " + e.getMessage());
        }
    }

    static String jsonString(String s) {
        if (s == null) {
            return "\"\"";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "") + "\"";
    }

    static ResourcePackInfo fromPath(Path path, ServerConfig config) throws IOException {
        String sha1 = sha1Hex(path);
        String name = path.getFileName().toString();
        String id = name.replaceAll("\\.[^.]+$", "");
        boolean forced = config.isResourcePackForced();
        return new ResourcePackInfo(
                id,
                name,
                path,
                sha1,
                Files.size(path),
                config.getResourcePackPrompt(),
                forced
        );
    }

    static String sha1Hex(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream in = Files.newInputStream(path);
                 DigestInputStream din = new DigestInputStream(in, digest)) {
                din.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new IOException("SHA-1 failed for " + path, e);
        }
    }
}
