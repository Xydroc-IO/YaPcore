package com.yapcore.presence.mixin;

import com.yapcore.presence.PresenceChannelPayload;
import com.yapcore.presence.YapPresenceClient;
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
    private void yap$presenceHello(ClientboundLoginPacket packet, CallbackInfo ci) {
        YapPresenceClient.sendHello();
    }

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void yap$presencePayload(CustomPacketPayload payload, CallbackInfo ci) {
        if (payload instanceof PresenceChannelPayload presence) {
            YapPresenceClient.handleServerMessage(presence.message());
            ci.cancel();
        }
    }
}
