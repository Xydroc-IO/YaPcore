package com.yapcore.presence.mixin;

import com.yapcore.presence.PresenceSkinApplier;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Wardrobe / Tailor skins must drive the live avatar, not only the menu preview.
 * Paper profile updates often do not refresh the local client's session skin.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void yap$overlayPresenceSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        PlayerSkin base = cir.getReturnValue();
        if (base == null) {
            return;
        }
        PlayerSkin overlaid = PresenceSkinApplier.overlay(self.getUUID(), base);
        if (overlaid != base) {
            cir.setReturnValue(overlaid);
        }
    }
}
