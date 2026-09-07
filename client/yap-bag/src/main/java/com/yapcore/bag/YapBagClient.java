package com.yapcore.bag;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
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
        LOGGER.info("YaP Bag ready — HELLO on join; B / inventory tab / chest tabs use channel yap:bag");
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

    /** Tell YaPPlayerData this client has page tabs (omit chest-item nav row). */
    public static void sendHello() {
        if (!config.enabled) {
            return;
        }
        sendPayload(BagChannelPayload.hello());
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
        // Re-announce before OPEN so a fast B-press after join still skips item nav.
        sendPayload(BagChannelPayload.hello());
        int target = page > 0 ? page : 1;
        if (!sendPayload(BagChannelPayload.open(target))) {
            // Fallback if custom payload path fails mid-session
            String command = target > 1 ? "bag " + target : "bag";
            if (screen != null) {
                minecraft.player.connection.sendUnattendedCommand(command, screen);
            } else {
                minecraft.player.connection.sendCommand(command);
            }
        }
    }

    /** B key: open bag, or close if the YaP bag chest is already up. */
    public static void requestToggle() {
        if (!config.enabled) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        if (isBagScreen(minecraft.gui.screen())) {
            closeBag();
            return;
        }
        requestOpen(0);
    }

    public static boolean isBagScreen(Screen screen) {
        if (!(screen instanceof ContainerScreen container)) {
            return false;
        }
        return BagTitle.parse(container.getTitle().getString()).isPresent();
    }

    public static void closeBag() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        minecraft.player.closeContainer();
    }

    private static boolean sendPayload(BagChannelPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getConnection() == null) {
            return false;
        }
        try {
            minecraft.getConnection().send(new ServerboundCustomPayloadPacket(payload));
            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to send yap:bag payload {}", payload.message(), e);
            return false;
        }
    }
}
