package com.yapcore.staff;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import com.mojang.blaze3d.platform.InputConstants;
import com.yapcore.staff.screen.StaffHubScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class YapStaffClient implements ClientModInitializer {
    public static final String MOD_ID = "yap-staff";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static StaffConfig config = new StaffConfig();
    private static final StaffSession SESSION = new StaffSession();
    private static KeyMapping openKey;

    @Override
    public void onInitializeClient() {
        config = StaffConfig.load();
        openKey();
        LOGGER.info("YaP Staff {} ready — keybind + Esc pause open the full admin GUI",
                net.fabricmc.loader.api.FabricLoader.getInstance()
                        .getModContainer(MOD_ID)
                        .map(c -> c.getMetadata().getVersion().getFriendlyString())
                        .orElse("dev"));
    }

    public static StaffConfig config() {
        return config;
    }

    public static StaffSession session() {
        return SESSION;
    }

    public static KeyMapping openKey() {
        if (openKey == null) {
            openKey = new KeyMapping(
                    "key.yap-staff.open",
                    InputConstants.KEY_R,
                    KeyMapping.Category.MISC);
        }
        return openKey;
    }

    /** Opens the native staff hub (not the server chest shim). */
    public static void openHub() {
        if (!config.enabled) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.getConnection() == null) {
            return;
        }
        if (minecraft.gui.screen() instanceof ChatScreen) {
            return;
        }
        minecraft.gui.setScreen(new StaffHubScreen(minecraft.gui.screen()));
    }
}
