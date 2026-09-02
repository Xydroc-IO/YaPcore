package com.yapcore.bag;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BagConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean enabled = true;
    public boolean inventoryTab = true;
    public boolean chestTabs = true;

    public static BagConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("yap-bag.json");
        BagConfig config = new BagConfig();
        if (Files.isRegularFile(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                BagConfig loaded = GSON.fromJson(reader, BagConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            } catch (IOException e) {
                YapBagClient.LOGGER.warn("Could not read yap-bag.json", e);
            }
        }
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            YapBagClient.LOGGER.warn("Could not write yap-bag.json", e);
        }
        return config;
    }
}
