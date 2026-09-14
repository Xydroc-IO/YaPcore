package com.yapcore.presence;

import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;

/**
 * Builds {@link ClientAsset.Texture} values for DynamicTextures registered by
 * {@link PresenceTextureCache}.
 *
 * <p>MC 26.2 {@link ClientAsset.ResourceTexture} rewrites {@code ns:path} →
 * {@code ns:textures/path.png} and looks that up in the resource pack. Our skins are
 * {@link net.minecraft.client.renderer.texture.DynamicTexture}s, so we must use
 * {@link ClientAsset.DownloadedTexture}, which keeps {@code texturePath} as-is.
 */
public final class PresencePlayerSkins {

    private PresencePlayerSkins() {
    }

    public static ClientAsset.Texture body(Identifier registeredDynamicId) {
        if (registeredDynamicId == null) {
            return null;
        }
        return new ClientAsset.DownloadedTexture(
                registeredDynamicId, "yap-presence://" + registeredDynamicId);
    }

    public static ClientAsset.Texture optional(Identifier registeredDynamicId) {
        return registeredDynamicId == null ? null : body(registeredDynamicId);
    }
}
