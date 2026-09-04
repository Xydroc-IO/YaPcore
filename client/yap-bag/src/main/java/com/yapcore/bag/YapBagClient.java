package com.yapcore.bag;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import com.mojang.blaze3d.platform.InputConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class YapBagClient implements ClientModInitializer {
    public static final String MOD_ID = "yap-bag";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final long COOLDOWN_MS = 400L;

    private static BagConfig config = new BagConfig();
    private static KeyMapping openKey;
    private static long lastSendMs;

    @Override
    public void onInitializeClient() {
        config = BagConfig.load();
        openKey();
        LOGGER.info("YaP Bag ready — keybind B opens /bag (optional; vanilla still uses the command)");
    }

    public static BagConfig config() {
        return config;
    }

    public static KeyMapping openKey() {
        if (openKey == null) {
            openKey = new KeyMapping(
                    "key.yap-bag.open",
                    InputConstants.KEY_B,
                    KeyMapping.Category.INVENTORY);
        }
        return openKey;
    }

    public static void requestOpen(int page) {
        if (!config.enabled) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.getConnection() == null) {
            return;
        }
        Screen screen = minecraft.gui.screen();
        if (screen instanceof ChatScreen) {
            return;
        }
        if (page > 0 && screen instanceof ContainerScreen container) {
            var state = BagTitle.parse(container.getTitle().getString());
            if (state.isPresent() && state.get().page() == page) {
                return;
            }
        }
        long now = System.currentTimeMillis();
        if (now - lastSendMs < COOLDOWN_MS) {
            return;
        }
        lastSendMs = now;
        String command = page > 1 ? "bag " + page : "bag";
        if (screen != null) {
            minecraft.player.connection.sendUnattendedCommand(command, screen);
        } else {
            minecraft.player.connection.sendCommand(command);
        }
    }
}
