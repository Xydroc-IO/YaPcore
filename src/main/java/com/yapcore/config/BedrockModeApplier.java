package com.yapcore.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Pre-start Bedrock path switch: Link-native, UDP forwarder (legacy first-party), or Geyser backup.
 *
 * <p>Writes both {@code link-data/link.properties} and {@code config/server.properties}
 * so Link and chassis agree before the stack starts.
 */
public final class BedrockModeApplier {

    /** Link owns Bedrock sessions ({@code BedrockSessionHost}); chassis Bedrock UDP off. */
    public static final String MODE_NATIVE = "native";
    /** Link UDP forwarder → chassis Bedrock (legacy {@code first-party}). */
    public static final String MODE_FORWARDER = "forwarder";
    /** @deprecated Prefer {@link #MODE_FORWARDER}; still accepted as alias. */
    @Deprecated
    public static final String MODE_FIRST_PARTY = "first-party";
    public static final String MODE_GEYSER_BACKUP = "geyser-backup";

    private BedrockModeApplier() {}

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return MODE_NATIVE;
        }
        String m = raw.trim().toLowerCase(Locale.ROOT);
        if (MODE_NATIVE.equals(m)) {
            return MODE_NATIVE;
        }
        if (MODE_GEYSER_BACKUP.equals(m) || "geyser".equals(m) || "backup".equals(m)) {
            return MODE_GEYSER_BACKUP;
        }
        if (MODE_FORWARDER.equals(m) || MODE_FIRST_PARTY.equals(m)) {
            return MODE_FORWARDER;
        }
        // Unknown → product default (Link-native)
        return MODE_NATIVE;
    }

    public static boolean isGeyserBackup(String mode) {
        return MODE_GEYSER_BACKUP.equals(normalize(mode));
    }

    public static boolean isNative(String mode) {
        return MODE_NATIVE.equals(normalize(mode));
    }

    public static Path geyserHome(Path rootDir, Path linkHome) {
        return linkHome.resolve("geyser");
    }

    public static Path geyserJar(Path rootDir, Path linkHome) {
        return geyserHome(rootDir, linkHome).resolve("Geyser-Standalone.jar");
    }

    /**
     * Apply mode to Link + chassis property files. Does not start/stop processes.
     *
     * @return summary map for dashboard/CLI
     */
    public static Map<String, Object> apply(Path rootDir, Path linkHome, Path serverPropsFile, String modeRaw)
            throws IOException {
        String mode = normalize(modeRaw);
        boolean backup = isGeyserBackup(mode);
        boolean nativeMode = isNative(mode);

        Files.createDirectories(linkHome);
        Path linkPropsFile = linkHome.resolve("link.properties");
        Properties link = loadProps(linkPropsFile);
        link.setProperty("bedrock-mode", mode);
        if (backup) {
            link.setProperty("bedrock-enabled", "false");
            link.setProperty("geyser-enabled", "true");
            link.setProperty("geyser-home", "geyser");
            link.setProperty("geyser-jar", "Geyser-Standalone.jar");
            link.setProperty("geyser-remote-host", "127.0.0.1");
            link.setProperty("geyser-remote-port", "25565");
            link.setProperty("geyser-bedrock-bind", "0.0.0.0:19132");
        } else {
            // native + forwarder both enable Link Bedrock edge
            link.setProperty("bedrock-enabled", "true");
            link.setProperty("geyser-enabled", "false");
            if (!link.containsKey("bedrock-bind") || link.getProperty("bedrock-bind", "").isBlank()) {
                link.setProperty("bedrock-bind", "0.0.0.0:25565,0.0.0.0:19132");
            }
            if (!link.containsKey("bedrock-backend") || link.getProperty("bedrock-backend", "").isBlank()) {
                link.setProperty("bedrock-backend", "127.0.0.1:25566");
            }
            link.setProperty("bedrock-shared-port", "true");
            link.setProperty("bedrock-also-19132", "true");
        }
        storeProps(linkPropsFile, link, "YaP Link — bedrock-mode applied");

        Files.createDirectories(serverPropsFile.getParent());
        Properties server = loadProps(serverPropsFile);
        server.setProperty("bedrock-mode", mode);
        if (backup) {
            server.setProperty("bedrock-enabled", "false");
            server.setProperty("crossplay-enabled", "false");
            server.setProperty("shared-listen-port", "false");
        } else if (nativeMode) {
            // Link owns Bedrock — chassis must not bind Bedrock UDP
            server.setProperty("bedrock-enabled", "false");
            server.setProperty("crossplay-enabled", "true");
            server.setProperty("shared-listen-port", "false");
            // Phone default / friends is :19132 (Link binds it); do not advertise JE :25565 as BE.
            if (!server.containsKey("public-bedrock-port")
                    || "25565".equals(server.getProperty("public-bedrock-port", "").trim())
                    || "25566".equals(server.getProperty("public-bedrock-port", "").trim())) {
                server.setProperty("public-bedrock-port", "19132");
            }
        } else {
            // forwarder: chassis Bedrock on (current first-party behavior)
            server.setProperty("bedrock-enabled", "true");
            server.setProperty("crossplay-enabled", "true");
            server.setProperty("shared-listen-port", "true");
        }
        storeProps(serverPropsFile, server, "YaPcore server configuration");

        Path jar = geyserJar(rootDir, linkHome);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("bedrockMode", mode);
        out.put("linkBedrockEnabled", !backup);
        out.put("linkBedrockNative", nativeMode);
        out.put("chassisBedrockEnabled", !backup && !nativeMode);
        out.put("geyserEnabled", backup);
        out.put("geyserJar", jar.toString());
        out.put("geyserJarPresent", Files.isRegularFile(jar));
        out.put("ok", true);
        if (backup && !Files.isRegularFile(jar)) {
            out.put("warning", "Geyser-Standalone.jar missing at " + jar
                    + " — stage the jar before starting in geyser-backup mode.");
        }
        return out;
    }

    public static String readMode(Path linkHome, Path serverPropsFile) {
        try {
            Properties link = loadProps(linkHome.resolve("link.properties"));
            String fromLink = link.getProperty("bedrock-mode");
            if (fromLink != null && !fromLink.isBlank()) {
                return normalize(fromLink);
            }
        } catch (Exception ignored) {
            // fall through
        }
        try {
            Properties server = loadProps(serverPropsFile);
            String fromServer = server.getProperty("bedrock-mode");
            if (fromServer != null && !fromServer.isBlank()) {
                return normalize(fromServer);
            }
        } catch (Exception ignored) {
            // fall through
        }
        return MODE_NATIVE;
    }

    private static Properties loadProps(Path file) throws IOException {
        Properties p = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                p.load(in);
            }
        }
        return p;
    }

    private static void storeProps(Path file, Properties p, String comment) throws IOException {
        try (OutputStream out = Files.newOutputStream(file)) {
            p.store(out, comment);
        }
    }
}
