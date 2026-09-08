package com.yapcore.staff.mixin;

import com.yapcore.staff.YapStaffClient;
import com.yapcore.staff.screen.ItemsAdminScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Syncs YaPItems registry ids from server chat markers into the staff browse list. */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {

    @Inject(method = "addServerSystemMessage", at = @At("HEAD"), cancellable = true)
    private void yapStaff$captureServerMarkers(Component message, CallbackInfo ci) {
        if (captureItemMarkers(message)) {
            ci.cancel();
        }
    }

    @Inject(method = "addClientSystemMessage", at = @At("HEAD"), cancellable = true)
    private void yapStaff$captureClientMarkers(Component message, CallbackInfo ci) {
        if (captureItemMarkers(message)) {
            ci.cancel();
        }
    }

    /** @return true if the message was a machine marker and should be hidden from chat */
    private static boolean captureItemMarkers(Component message) {
        if (message == null) {
            return false;
        }
        String plain = message.getString();
        if (plain == null || plain.isBlank()) {
            return false;
        }
        String trimmed = plain.trim();
        if (trimmed.startsWith("yapitems:id=")) {
            String id = trimmed.substring("yapitems:id=".length()).trim().toLowerCase();
            YapStaffClient.session().rememberCustomItem(id);
            maybeRebuildItemsScreen();
            return true;
        }
        if (trimmed.startsWith("yapitems:created=")) {
            String id = trimmed.substring("yapitems:created=".length()).trim().toLowerCase();
            YapStaffClient.session().rememberCustomItem(id);
            maybeRebuildItemsScreen();
            return true;
        }
        if (trimmed.startsWith("yapitems:updated=")) {
            String id = trimmed.substring("yapitems:updated=".length()).trim().toLowerCase();
            YapStaffClient.session().rememberCustomItem(id);
            maybeRebuildItemsScreen();
            return true;
        }
        if (trimmed.startsWith("yapitems:deleted=")) {
            String id = trimmed.substring("yapitems:deleted=".length()).trim().toLowerCase();
            YapStaffClient.session().forgetCustomItem(id);
            maybeRebuildItemsScreen();
            return true;
        }
        // Human create line: "Created item god_killer"
        String lower = trimmed.toLowerCase();
        int idx = lower.indexOf("created item ");
        if (idx >= 0) {
            String rest = trimmed.substring(idx + "created item ".length()).trim();
            if (!rest.isEmpty()) {
                String id = rest.split("\\s+")[0].replaceAll("[^a-zA-Z0-9_]", "").toLowerCase();
                if (!id.isEmpty()) {
                    YapStaffClient.session().rememberCustomItem(id);
                    maybeRebuildItemsScreen();
                }
            }
        }
        return false;
    }

    private static void maybeRebuildItemsScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.gui != null && mc.gui.screen() instanceof ItemsAdminScreen screen) {
            screen.rebuildFromSync();
        }
    }
}
