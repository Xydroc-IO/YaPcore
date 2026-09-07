package com.yapcore.staff;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class StaffConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean enabled = true;
    /** Esc pause screen → open staff hub. */
    public boolean pauseButton = true;
    /** Keybind (default R) opens staff hub. */
    public boolean keybind = true;

    public static StaffConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("yap-staff.json");
        StaffConfig config = new StaffConfig();
        if (Files.isRegularFile(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                StaffConfig loaded = GSON.fromJson(reader, StaffConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            } catch (IOException e) {
                YapStaffClient.LOGGER.warn("Could not read yap-staff.json", e);
            }
        }
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            YapStaffClient.LOGGER.warn("Could not write yap-staff.json", e);
        }
        return config;
    }
}
