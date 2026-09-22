package com.yapcore.haze.mixin;

import com.yapcore.haze.HazeChannelPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

@Mixin(ServerboundCustomPayloadPacket.class)
public abstract class ServerboundCustomPayloadPacketMixin {

    @Inject(method = "lambda$static$1", at = @At("HEAD"))
    private static void yap$registerHazeChannel(
            ArrayList<CustomPacketPayload.TypeAndCodec<? super FriendlyByteBuf, ?>> codecs,
            CallbackInfo ci) {
        codecs.add(new CustomPacketPayload.TypeAndCodec<>(
                HazeChannelPayload.TYPE, HazeChannelPayload.STREAM_CODEC));
    }
}
