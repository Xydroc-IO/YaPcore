package com.yapcore.resourcepack;

import com.yapcore.client.ClientSession;
import com.yapcore.config.ServerConfig;
import com.yapcore.crossplay.skin.SkinService;
import com.yapcore.network.publicity.PublicEndpoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
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
 * Offer/URL helpers: {@link ResourcePackManagerOffers}; IO helpers: {@link ResourcePackManagerIo}.
 */
public final class ResourcePackManager {

    private static final Logger LOG = Logger.getLogger("YaPcore.ResourcePacks");

    private final Path packsDir;
    private final ServerConfig config;
    private final CopyOnWriteArrayList<Consumer<List<ResourcePackInfo>>> listeners = new CopyOnWriteArrayList<>();
    private volatile ResourcePackHttpServer httpServer;
    private volatile SkinService skinService;
    private volatile ResourcePackHttpServer.EmotePlayHandler emotePlayHandler;

    public ResourcePackManager(Path packsDir, ServerConfig config) {
        this.packsDir = Objects.requireNonNull(packsDir);
        this.config = Objects.requireNonNull(config);
    }

    /** Package-private for helpers in the same package. */
    ServerConfig config() {
        return config;
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

    private Path mapMeshesDir() {
        return gameRoot().resolve("plugins").resolve("YaPMap").resolve("map/meshes");
    }

    public Path skinsDir() {
        return packsDir.resolveSibling("skins");
    }

    public void addListener(Consumer<List<ResourcePackInfo>> listener) {
        listeners.add(listener);
    }

    public void ensureDirectory() throws IOException {
        Files.createDirectories(packsDir);
        Files.createDirectories(skinsDir());
    }

    public void setPublicHost(String host) {
        // retained for API compat; PublicEndpoint owns advertisement now
    }

    /** Wire SkinService for {@code POST /skin/apply} (Tailor). Safe before or after {@link #startHttp}. */
    public void setSkinService(SkinService skinService) {
        this.skinService = skinService;
        ResourcePackHttpServer http = httpServer;
        if (http != null) {
            http.setSkinService(skinService);
        }
    }

    /** Wire emote play for {@code POST /emote/play} (Tailor JE→chassis). */
    public void setEmotePlayHandler(ResourcePackHttpServer.EmotePlayHandler handler) {
        this.emotePlayHandler = handler;
        ResourcePackHttpServer http = httpServer;
        if (http != null) {
            http.setEmotePlayHandler(handler);
        }
    }

    public synchronized void startHttp() throws IOException {
        ensureDirectory();
        // Normalize on-disk Bedrock pack before serving (GitHub sync may reintroduce 1.26/PBR).
        String bedrockFile = config.getResourcePackBedrockFile();
        if (bedrockFile != null && !bedrockFile.isBlank()) {
            Path localBe = packsDir.resolve(bedrockFile);
            if (Files.isRegularFile(localBe)) {
                try {
                    normalizeBedrockMcpackManifest(localBe);
                } catch (Exception e) {
                    LOG.warning("Bedrock pack normalize skipped: " + e.getMessage());
                }
            }
        }
        // Pull Bedrock .mcpack from GitHub latest when resource-pack-url points there,
        // then serve it with application/zip (Bedrock cannot download GitHub Releases directly).
        syncBedrockPackFromGitHub();
        // Always host packs/map on :8081 so operators can curl/test even when the login offer is off.
        httpServer = new ResourcePackHttpServer(
                config.getBindHost(),
                config.getResourcePackHttpPort(),
                packsDir,
                mapWebDir(),
                mapTilesDir(),
                mapMeshesDir(),
                skinsDir(),
                skinService
        );
        if (emotePlayHandler != null) {
            httpServer.setEmotePlayHandler(emotePlayHandler);
        }
        httpServer.start();
        writePluginManifest();
        if (!config.isResourcePackEnabled()) {
            LOG.info("Resource pack login offer disabled — HTTP still serving on :"
                    + config.getResourcePackHttpPort());
            return;
        }
        List<ResourcePackInfo> actives = getActivePacks();
        LOG.info("Active resource packs (" + actives.size() + "): "
                + actives.stream().map(ResourcePackInfo::getFileName).collect(Collectors.joining(", ")));
        try {
            String offer = ResourcePackBundler.ensureOfferFile(packsDir, config.getResourcePackFiles());
            if (!offer.isBlank()) {
                ResourcePackManagerIo.probePackUrl(buildPublicUrl(offer));
            }
        } catch (IOException e) {
            LOG.warning("Offer pack prepare failed: " + e.getMessage());
            for (ResourcePackInfo pack : actives) {
                ResourcePackManagerIo.probePackUrl(buildPublicUrl(pack.getFileName()));
            }
        }
        String bedrock = config.getResourcePackBedrockFile();
        if (bedrock != null && !bedrock.isBlank() && Files.isRegularFile(packsDir.resolve(bedrock))) {
            ResourcePackManagerIo.probePackUrl(new PublicEndpoint(config).packUrlSelfHosted(bedrock));
        }
    }

    void syncBedrockPackFromGitHub() {
        ResourcePackManagerIo.syncBedrockPackFromGitHub(this);
    }

    static void normalizeBedrockMcpackManifest(Path mcpack) throws IOException {
        ResourcePackManagerIo.normalizeBedrockMcpackManifest(mcpack);
    }

    static String pinBedrockManifest(String json) {
        return ResourcePackManagerIo.pinBedrockManifest(json);
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
        return ResourcePackManagerOffers.createOffers(this, session);
    }

    public Optional<ResourcePackOffer> createOffer(ClientSession session) {
        return ResourcePackManagerOffers.createOffer(this, session);
    }

    /**
     * Bedrock login CDN offer ({@code .mcpack} only). Uses
     * {@code resource-pack-bedrock-file}; never the Java Edition zip.
     * <p>
     * Bedrock clients <strong>cannot</strong> download GitHub Releases directly —
     * GitHub serves {@code application/octet-stream} and modern BE requires
     * {@code application/zip} + {@code Content-Length}. JE still uses
     * {@code resource-pack-url} (GitHub latest). Bedrock gets the same
     * {@code .mcpack} bytes from {@link ResourcePackHttpServer}.
     *
     * @param clientAddress peer address string (e.g. {@code /127.0.0.1:34956}); may be blank
     */
    public Optional<ResourcePackOffer> createBedrockOffer(String clientAddress) {
        return ResourcePackManagerOffers.createBedrockOffer(clientAddress);
    }

    /** No-arg convenience. */
    public Optional<ResourcePackOffer> createBedrockOffer() {
        return createBedrockOffer("");
    }

    /**
     * Bedrock pack download URL — zip-typed YaP/nginx CDN (same bytes as GitHub latest).
     * Never raw github.com Releases URLs ({@code application/octet-stream} → client kick).
     */
    String bedrockPackUrl(String fileName, String clientAddress) {
        return ResourcePackManagerOffers.bedrockPackUrl(this, fileName, clientAddress);
    }

    static Optional<UUID> readMcpackHeaderUuid(Path mcpack) {
        return ResourcePackManagerIo.readMcpackHeaderUuid(mcpack);
    }

    public String buildPublicUrl(String fileName) {
        return ResourcePackManagerOffers.buildPublicUrl(this, fileName);
    }

    public String buildPublicUrl(String fileName, ClientSession session) {
        return ResourcePackManagerOffers.buildPublicUrl(this, fileName, session);
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
        ResourcePackManagerIo.writePluginManifest(this);
    }

    private ResourcePackInfo fromPath(Path path) throws IOException {
        return ResourcePackManagerIo.fromPath(path, config);
    }

    private void fireChanged() {
        List<ResourcePackInfo> snapshot = listPacks();
        for (Consumer<List<ResourcePackInfo>> listener : listeners) {
            listener.accept(snapshot);
        }
    }
}
