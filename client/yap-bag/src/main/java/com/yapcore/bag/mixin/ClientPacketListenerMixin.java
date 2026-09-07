package com.yapcore.bag.mixin;

import com.yapcore.bag.YapBagClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Announce yap-bag tabs as soon as play-phase login completes. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void yap$bagHello(ClientboundLoginPacket packet, CallbackInfo ci) {
        YapBagClient.sendHello();
    }
}
