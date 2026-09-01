package com.yapcore.resourcepack;

import com.yapcore.client.ClientSession;
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
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Manages texture / resource packs. Multiple packs can be active at once;
 * Paper clients receive each via {@code Player.addResourcePack} (see YaPPacks plugin).
 */
public final class ResourcePackManager {

    private static final Logger LOG = Logger.getLogger("YaPcore.ResourcePacks");

    private final Path packsDir;
    private final ServerConfig config;
    private final CopyOnWriteArrayList<Consumer<List<ResourcePackInfo>>> listeners = new CopyOnWriteArrayList<>();
    private volatile ResourcePackHttpServer httpServer;

    public ResourcePackManager(Path packsDir, ServerConfig config) {
        this.packsDir = Objects.requireNonNull(packsDir);
        this.config = Objects.requireNonNull(config);
    }

    public Path getPacksDir() {
        return packsDir;
    }

    private Path gameRoot() {
        Path parent = packsDir.toAbsolutePath().normalize().getParent();
        return parent != null ? parent : packsDir;
    }

    private Path mapWebDir() {
        return gameRoot().resolve("plugins").resolve("YaPMap").resolve("web");
    }

    private Path mapTilesDir() {
        return gameRoot().resolve("plugins").resolve("YaPMap").resolve("map/tiles");
    }

    public void addListener(Consumer<List<ResourcePackInfo>> listener) {
        listeners.add(listener);
    }

    public void ensureDirectory() throws IOException {
        Files.createDirectories(packsDir);
    }

    public void setPublicHost(String host) {
        // retained for API compat; PublicEndpoint owns advertisement now
    }

    public synchronized void startHttp() throws IOException {
        ensureDirectory();
        if (!config.isResourcePackEnabled()) {
            LOG.info("Resource packs disabled in config");
            return;
        }
        httpServer = new ResourcePackHttpServer(
                config.getBindHost(),
                config.getResourcePackHttpPort(),
                packsDir,
                mapWebDir(),
                mapTilesDir()
        );
        httpServer.start();
        writePluginManifest();
        List<ResourcePackInfo> actives = getActivePacks();
        LOG.info("Active resource packs (" + actives.size() + "): "
                + actives.stream().map(ResourcePackInfo::getFileName).collect(Collectors.joining(", ")));
        try {
            String offer = ResourcePackBundler.ensureOfferFile(packsDir, config.getResourcePackFiles());
            if (!offer.isBlank()) {
                probePackUrl(buildPublicUrl(offer));
            }
        } catch (IOException e) {
            LOG.warning("Offer pack prepare failed: " + e.getMessage());
            for (ResourcePackInfo pack : actives) {
                probePackUrl(buildPublicUrl(pack.getFileName()));
            }
        }
    }

    private void probePackUrl(String url) {
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

    public synchronized void stopHttp() {
        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }
    }

    public List<ResourcePackInfo> listPacks() {
        List<ResourcePackInfo> list = new ArrayList<>();
        if (!Files.isDirectory(packsDir)) {
            return list;
        }
        try (Stream<Path> stream = Files.list(packsDir)) {
            stream.filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.endsWith(".zip") || n.endsWith(".mcpack");
                    })
                    .sorted()
                    .forEach(p -> {
                        try {
                            list.add(fromPath(p));
                        } catch (IOException e) {
                            LOG.warning("Could not read pack " + p + ": " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            LOG.warning("Could not list packs: " + e.getMessage());
        }
        return list;
    }

    public ResourcePackInfo addPack(Path source) throws IOException {
        ensureDirectory();
        if (!Files.isRegularFile(source)) {
            throw new IOException("Not a file: " + source);
        }
        String name = source.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".zip") && !lower.endsWith(".mcpack")) {
            throw new IOException("Pack must be .zip (Java) or .mcpack (Bedrock)");
        }
        Path dest = packsDir.resolve(name);
        Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
        ResourcePackInfo info = fromPath(dest);
        LOG.info("Installed resource pack " + name + " sha1=" + info.getSha1Hex());
        fireChanged();
        return info;
    }

    public boolean removePack(String fileName) throws IOException {
        if (fileName == null || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new IOException("Invalid pack name");
        }
        Path target = packsDir.toAbsolutePath().normalize().resolve(fileName).normalize();
        if (!target.startsWith(packsDir.toAbsolutePath().normalize())) {
            throw new IOException("Invalid pack path");
        }
        if (!Files.exists(target)) {
            return false;
        }
        List<String> actives = new ArrayList<>(config.getResourcePackFiles());
        if (actives.remove(fileName)) {
            config.setResourcePackFiles(actives);
            config.save();
        }
        Files.delete(target);
        LOG.info("Removed resource pack " + fileName);
        writePluginManifest();
        fireChanged();
        return true;
    }

    /** Replace the entire active set (ordered). Empty clears. */
    public void setActivePacks(List<String> fileNames) throws IOException {
        List<String> clean = new ArrayList<>();
        if (fileNames != null) {
            for (String name : fileNames) {
                if (name == null || name.isBlank()) {
                    continue;
                }
                String n = name.trim();
                Path target = packsDir.resolve(n);
                if (!Files.isRegularFile(target)) {
                    throw new IOException("Pack not found: " + n);
                }
                if (!clean.contains(n)) {
                    clean.add(n);
                }
            }
        }
        config.setResourcePackFiles(clean);
        config.save();
        LOG.info("Active resource packs → " + (clean.isEmpty() ? "(none)" : String.join(", ", clean)));
        try {
            String offer = ResourcePackBundler.ensureOfferFile(packsDir, clean);
            if (!offer.isBlank()) {
                LOG.info("Client offer pack → " + offer + " url=" + buildPublicUrl(offer));
            }
        } catch (IOException e) {
            LOG.warning("Could not build multi-pack offer: " + e.getMessage());
        }
        writePluginManifest();
        fireChanged();
    }

    /** Back-compat: set a single active pack (replaces the list). */
    public void setActivePack(String fileName) throws IOException {
        if (fileName == null || fileName.isBlank()) {
            setActivePacks(List.of());
            return;
        }
        setActivePacks(List.of(fileName.trim()));
    }

    public void addActivePack(String fileName) throws IOException {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        String n = fileName.trim();
        if (!Files.isRegularFile(packsDir.resolve(n))) {
            throw new IOException("Pack not found: " + n);
        }
        LinkedHashSet<String> set = new LinkedHashSet<>(config.getResourcePackFiles());
        set.add(n);
        setActivePacks(new ArrayList<>(set));
    }

    public void removeActivePack(String fileName) throws IOException {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        List<String> next = new ArrayList<>(config.getResourcePackFiles());
        next.remove(fileName.trim());
        setActivePacks(next);
    }

    public boolean isActive(String fileName) {
        return fileName != null && config.getResourcePackFiles().contains(fileName);
    }

    public List<ResourcePackInfo> getActivePacks() {
        List<ResourcePackInfo> out = new ArrayList<>();
        for (String file : config.getResourcePackFiles()) {
            Path path = packsDir.resolve(file);
            if (!Files.isRegularFile(path)) {
                LOG.warning("Active pack missing on disk: " + file);
                continue;
            }
            try {
                out.add(fromPath(path));
            } catch (IOException e) {
                LOG.warning("Could not read active pack " + file + ": " + e.getMessage());
            }
        }
        return out;
    }

    public Optional<ResourcePackInfo> getActivePack() {
        List<ResourcePackInfo> all = getActivePacks();
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    /** Offers for every active pack (native dual-stack path). */
    public List<ResourcePackOffer> createOffers(ClientSession session) {
        if (!config.isResourcePackEnabled()) {
            return List.of();
        }
        List<ResourcePackOffer> offers = new ArrayList<>();
        String prompt = config.getResourcePackPrompt();
        boolean forced = config.isResourcePackForced();
        for (ResourcePackInfo pack : getActivePacks()) {
            String url = buildPublicUrl(pack.getFileName(), session);
            boolean zip = pack.getFileName().toLowerCase(Locale.ROOT).endsWith(".zip")
                    || pack.getFileName().toLowerCase(Locale.ROOT).endsWith(".mcpack");
            ResourcePackOffer offer = new ResourcePackOffer(
                    packUuid(pack.getFileName(), pack.getSha1Hex()).toString(),
                    url,
                    pack.getSha1Hex(),
                    prompt == null || prompt.isBlank() ? pack.getPrompt() : prompt,
                    forced,
                    zip,
                    true
            );
            offers.add(offer);
        }
        if (!offers.isEmpty() && session != null) {
            session.offerResourcePack(offers.get(0));
            LOG.info("Offered " + offers.size() + " resource pack(s) to " + session.getUsername()
                    + " [" + session.getEdition() + "]");
        }
        return offers;
    }

    public Optional<ResourcePackOffer> createOffer(ClientSession session) {
        List<ResourcePackOffer> offers = createOffers(session);
        return offers.isEmpty() ? Optional.empty() : Optional.of(offers.get(0));
    }

    public String buildPublicUrl(String fileName) {
        return new PublicEndpoint(config).packUrl(fileName);
    }

    public String buildPublicUrl(String fileName, ClientSession session) {
        var ep = new PublicEndpoint(config);
        if (session != null && session.getAddress() != null) {
            return ep.packUrlForClient(fileName, session.getAddress());
        }
        return ep.packUrl(fileName);
    }

    public static UUID packUuid(String fileName, String sha1) {
        return UUID.nameUUIDFromBytes(("yapcore-pack:" + fileName + ":" + sha1)
                .getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Write {@code plugins/YaPPacks/active.json} for the Paper plugin that pushes
     * multiple packs via {@code Player.addResourcePack}.
     */
    public void writePluginManifest() {
        try {
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
            List<ResourcePackInfo> actives = getActivePacks();
            for (int i = 0; i < actives.size(); i++) {
                ResourcePackInfo p = actives.get(i);
                String url = ep.packUrl(p.getFileName());
                UUID id = packUuid(p.getFileName(), p.getSha1Hex());
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
            // Mirror into paper-kernel/plugins when present (Phase 3 cwd)
            Path paperPlugins = root.resolve("paper-kernel").resolve("plugins").resolve("YaPPacks");
            if (Files.isDirectory(root.resolve("paper-kernel").resolve("plugins"))
                    || Files.isSymbolicLink(root.resolve("paper-kernel").resolve("plugins"))) {
                Files.createDirectories(paperPlugins);
                Files.writeString(paperPlugins.resolve("active.json"), json.toString(), StandardCharsets.UTF_8);
            }
            LOG.info("Wrote YaPPacks manifest (" + actives.size() + " pack(s)) → " + file);
        } catch (Exception e) {
            LOG.warning("Could not write YaPPacks manifest: " + e.getMessage());
        }
    }

    private static String jsonString(String s) {
        if (s == null) {
            return "\"\"";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "") + "\"";
    }

    private ResourcePackInfo fromPath(Path path) throws IOException {
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

    private static String sha1Hex(Path path) throws IOException {
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

    private void fireChanged() {
        List<ResourcePackInfo> snapshot = listPacks();
        for (Consumer<List<ResourcePackInfo>> listener : listeners) {
            listener.accept(snapshot);
        }
    }
}
