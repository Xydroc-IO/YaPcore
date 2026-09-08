package com.yapcore.staff;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class StaffConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean enabled = true;
    /** Esc pause screen → open staff hub. */
    public boolean pauseButton = true;
    /** Keybind (default R) opens staff hub. */
    public boolean keybind = true;
    /** Remembered YaPItems ids (created / synced from server). */
    public LinkedHashSet<String> customItemIds = new LinkedHashSet<>();

    public static StaffConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("yap-staff.json");
        StaffConfig config = new StaffConfig();
        if (Files.isRegularFile(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                StaffConfig loaded = GSON.fromJson(reader, StaffConfig.class);
                if (loaded != null) {
                    config = loaded;
                    if (config.customItemIds == null) {
                        config.customItemIds = new LinkedHashSet<>();
                    }
                }
            } catch (IOException e) {
                YapStaffClient.LOGGER.warn("Could not read yap-staff.json", e);
            }
        }
        config.save();
        return config;
    }

    public void save() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("yap-staff.json");
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            YapStaffClient.LOGGER.warn("Could not write yap-staff.json", e);
        }
    }

    public void rememberItemId(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        if (customItemIds == null) {
            customItemIds = new LinkedHashSet<>();
        }
        if (customItemIds.add(id.trim().toLowerCase(Locale.ROOT))) {
            save();
        }
    }

    public void forgetItemId(String id) {
        if (id == null || id.isBlank() || customItemIds == null) {
            return;
        }
        if (customItemIds.remove(id.trim().toLowerCase(Locale.ROOT))) {
            save();
        }
    }

    public void rememberItemIds(Set<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        if (customItemIds == null) {
            customItemIds = new LinkedHashSet<>();
        }
        boolean changed = false;
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                changed |= customItemIds.add(id.trim().toLowerCase(Locale.ROOT));
            }
        }
        if (changed) {
            save();
        }
    }
}
