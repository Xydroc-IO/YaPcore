package com.yapcore.haze.mixin;

import com.yapcore.haze.HazeChannelPayload;
import com.yapcore.haze.Yap420Client;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void yap$hazeHello(ClientboundLoginPacket packet, CallbackInfo ci) {
        Yap420Client.sendHello();
    }

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void yap$hazePayload(CustomPacketPayload payload, CallbackInfo ci) {
        if (payload instanceof HazeChannelPayload haze) {
            HazeChannelPayload.handleInbound(haze.message());
            ci.cancel();
        }
    }
}
