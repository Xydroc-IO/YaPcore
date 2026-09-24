package com.yapcore.fleet.local;

import com.yapcore.fleet.model.FleetInstance;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/** Read / patch Folia {@code server.properties} for a fleet instance tree. */
public final class InstanceServerProps {

    private static final String[] KEYS = {
            "server-port", "server-ip", "max-players", "motd", "gamemode", "difficulty",
            "view-distance", "simulation-distance", "online-mode", "pvp",
            "spawn-protection", "spawn-monsters", "spawn-animals", "force-gamemode",
            "white-list", "enforce-whitelist", "enable-command-block",
            "level-name", "level-seed", "level-type", "generator-settings"
    };

    private InstanceServerProps() {
    }

    public static Path propsFile(Path rootDir, FleetInstance instance) {
        return InstanceLayout.dir(rootDir, instance).resolve("server.properties");
    }

    public static Map<String, String> read(Path rootDir, FleetInstance instance) throws IOException {
        Path file = propsFile(rootDir, instance);
        Properties p = load(file);
        Map<String, String> out = new LinkedHashMap<>();
        for (String key : KEYS) {
            String v = p.getProperty(key);
            if (v != null) {
                out.put(key, v);
            }
        }
        out.putIfAbsent("server-port", Integer.toString(instance.port()));
        out.putIfAbsent("server-ip", instance.bind());
        return out;
    }

    /**
     * Patch known keys into {@code server.properties}. Returns the effective property map after write.
     * Does not update {@link FleetInstance} registry — caller must sync port/bind into fleet.json.
     */
    public static Map<String, String> patch(
            Path rootDir, FleetInstance instance, Map<String, String> updates) throws IOException {
        Path file = propsFile(rootDir, instance);
        Files.createDirectories(file.getParent());
        patchFile(file, updates);
        return read(rootDir, instance);
    }

    /** Patch known keys into an existing {@code server.properties} file. */
    public static void patchFile(Path file, Map<String, String> updates) throws IOException {
        if (file == null || updates == null || updates.isEmpty()) {
            return;
        }
        Files.createDirectories(file.getParent());
        Properties p = load(file);
        for (Map.Entry<String, String> e : updates.entrySet()) {
            String key = e.getKey() == null ? "" : e.getKey().trim();
            if (key.isEmpty() || !isAllowed(key)) {
                continue;
            }
            String val = e.getValue() == null ? "" : e.getValue().trim();
            p.setProperty(key, val);
        }
        try (OutputStream out = Files.newOutputStream(file)) {
            p.store(out, "YaP fleet instance");
        }
    }

    /**
     * Point an existing fleet {@code server.properties} at the GitHub pack clients download.
     * A local-zip SHA-1 against that URL makes Minecraft fail the download.
     *
     * @return true when the file was updated
     */
    public static boolean syncGithubPackOffer(Path propsFile, String urlTemplate, String sha1, String fileName)
            throws IOException {
        if (propsFile == null || !Files.isRegularFile(propsFile)) {
            return false;
        }
        if (urlTemplate == null || urlTemplate.isBlank() || !isGithubPackUrl(urlTemplate)) {
            return false;
        }
        if (!isSha1(sha1)) {
            return false;
        }
        String file = (fileName == null || fileName.isBlank()) ? "yapcore-default.zip" : fileName.trim();
        String url = urlTemplate.replace("{file}", file);
        String hex = sha1.toLowerCase(Locale.ROOT);
        String id = UUID.nameUUIDFromBytes(("yapcore-pack:" + file + ":" + hex)
                .getBytes(StandardCharsets.UTF_8)).toString();
        Properties p = load(propsFile);
        if (url.equals(p.getProperty("resource-pack"))
                && hex.equals(p.getProperty("resource-pack-sha1"))
                && id.equals(p.getProperty("resource-pack-id"))) {
            return false;
        }
        p.setProperty("resource-pack", url);
        p.setProperty("resource-pack-sha1", hex);
        p.setProperty("resource-pack-id", id);
        try (OutputStream out = Files.newOutputStream(propsFile)) {
            p.store(out, "YaP fleet instance");
        }
        return true;
    }

    /**
     * Stamp the on-disk pack SHA-1 onto a fleet properties file whose
     * {@code resource-pack} URL already points at that zip. Clients reject the
     * download when the advertised hash and the file disagree, and keep the old pack.
     *
     * @return true when the file was updated
     */
    public static boolean syncLocalZipSha(Path propsFile, Path zipFile) throws IOException {
        if (propsFile == null || !Files.isRegularFile(propsFile) || zipFile == null || !Files.isRegularFile(zipFile)) {
            return false;
        }
        Properties p = load(propsFile);
        String url = p.getProperty("resource-pack", "");
        if (url.isBlank() || isGithubPackUrl(url) || !url.contains(zipFile.getFileName().toString())) {
            return false;
        }
        String hex = sha1(zipFile);
        String id = UUID.nameUUIDFromBytes(("yapcore-pack:" + zipFile.getFileName() + ":" + hex)
                .getBytes(StandardCharsets.UTF_8)).toString();
        if (hex.equalsIgnoreCase(p.getProperty("resource-pack-sha1", ""))
                && id.equals(p.getProperty("resource-pack-id", ""))) {
            return false;
        }
        p.setProperty("resource-pack-sha1", hex);
        p.setProperty("resource-pack-id", id);
        try (OutputStream out = Files.newOutputStream(propsFile)) {
            p.store(out, "YaP fleet instance");
        }
        return true;
    }

    static boolean isGithubPackUrl(String url) {
        String u = url.toLowerCase(Locale.ROOT);
        return u.contains("github.com/") || u.contains("githubusercontent.com/");
    }

    private static String sha1(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(Files.readAllBytes(file));
            byte[] hash = digest.digest();
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 unavailable", e);
        }
    }

    private static boolean isSha1(String s) {
        return s != null && s.matches("(?i)[a-f0-9]{40}");
    }

    static boolean isAllowed(String key) {
        for (String k : KEYS) {
            if (k.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static Properties load(Path file) throws IOException {
        Properties p = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                p.load(in);
            }
        }
        return p;
    }
}
