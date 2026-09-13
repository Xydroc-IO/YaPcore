package com.yapcore.presence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Client toggles for the YaP Tailor hub (mirrors yap-staff config shape). */
public final class PresenceConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean enabled = true;
    /** Esc pause screen → open Tailor hub. */
    public boolean pauseButton = true;
    /** Keybind (default P) opens Tailor hub. */
    public boolean keybind = true;

    public static PresenceConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("yap-presence.json");
        PresenceConfig config = new PresenceConfig();
        if (Files.isRegularFile(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                PresenceConfig loaded = GSON.fromJson(reader, PresenceConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            } catch (IOException e) {
                YapPresenceClient.LOGGER.warn("Could not read yap-presence.json", e);
            }
        }
        config.save();
        return config;
    }

    public void save() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("yap-presence.json");
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            YapPresenceClient.LOGGER.warn("Could not write yap-presence.json", e);
        }
    }
}
