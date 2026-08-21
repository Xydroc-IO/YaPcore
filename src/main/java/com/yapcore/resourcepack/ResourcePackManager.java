package com.yapcore.resourcepack;

import com.yapcore.client.ClientEdition;
import com.yapcore.client.ClientSession;
import com.yapcore.config.ServerConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Manages texture / resource packs and builds seamless download offers for clients.
 */
public final class ResourcePackManager {

    private static final Logger LOG = Logger.getLogger("YaPcore.ResourcePacks");

    private final Path packsDir;
    private final ServerConfig config;
    private final CopyOnWriteArrayList<Consumer<List<ResourcePackInfo>>> listeners = new CopyOnWriteArrayList<>();
    private volatile ResourcePackHttpServer httpServer;
    private volatile String publicHost = "127.0.0.1";

    public ResourcePackManager(Path packsDir, ServerConfig config) {
        this.packsDir = Objects.requireNonNull(packsDir);
        this.config = Objects.requireNonNull(config);
    }

    public Path getPacksDir() {
        return packsDir;
    }

    public void addListener(Consumer<List<ResourcePackInfo>> listener) {
        listeners.add(listener);
    }

    public void ensureDirectory() throws IOException {
        Files.createDirectories(packsDir);
    }

    public void setPublicHost(String host) {
        if (host != null && !host.isBlank() && !"0.0.0.0".equals(host)) {
            this.publicHost = host;
        }
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
                packsDir
        );
        httpServer.start();
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
        if (fileName.equals(config.getResourcePackFile())) {
            config.setResourcePackFile("");
            config.save();
        }
        Files.delete(target);
        LOG.info("Removed resource pack " + fileName);
        fireChanged();
        return true;
    }

    public void setActivePack(String fileName) throws IOException {
        if (fileName == null || fileName.isBlank()) {
            config.setResourcePackFile("");
            config.save();
            fireChanged();
            return;
        }
        Path target = packsDir.resolve(fileName);
        if (!Files.isRegularFile(target)) {
            throw new IOException("Pack not found: " + fileName);
        }
        config.setResourcePackFile(fileName);
        config.save();
        LOG.info("Active resource pack set to " + fileName);
        fireChanged();
    }

    public Optional<ResourcePackInfo> getActivePack() {
        String file = config.getResourcePackFile();
        if (file == null || file.isBlank()) {
            return Optional.empty();
        }
        Path path = packsDir.resolve(file);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(fromPath(path));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Build a download offer for a connecting client. Same pack URL works for both
     * editions when a zip/mcpack is hosted; clients pull it automatically.
     */
    public Optional<ResourcePackOffer> createOffer(ClientSession session) {
        if (!config.isResourcePackEnabled()) {
            return Optional.empty();
        }
        Optional<ResourcePackInfo> active = getActivePack();
        if (active.isEmpty()) {
            return Optional.empty();
        }
        ResourcePackInfo pack = active.get();
        String url = buildPublicUrl(pack.getFileName(), session);
        boolean javaOk = pack.getFileName().toLowerCase(Locale.ROOT).endsWith(".zip")
                || pack.getFileName().toLowerCase(Locale.ROOT).endsWith(".mcpack");
        boolean bedrockOk = true;
        ResourcePackOffer offer = new ResourcePackOffer(
                pack.getId(),
                url,
                pack.getSha1Hex(),
                config.getResourcePackPrompt().isBlank() ? pack.getPrompt() : config.getResourcePackPrompt(),
                config.isResourcePackForced(),
                javaOk,
                bedrockOk
        );
        session.offerResourcePack(offer);
        LOG.info("Offered resource pack to " + session.getUsername()
                + " [" + session.getEdition() + "] url=" + url);
        return Optional.of(offer);
    }

    public String buildPublicUrl(String fileName) {
        return new com.yapcore.network.publicity.PublicEndpoint(config).packUrl(fileName);
    }

    public String buildPublicUrl(String fileName, ClientSession session) {
        var ep = new com.yapcore.network.publicity.PublicEndpoint(config);
        if (session != null && session.getAddress() != null) {
            return ep.packUrlForClient(fileName, session.getAddress());
        }
        return ep.packUrl(fileName);
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
