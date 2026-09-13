package com.yapcore.blocks.mixin;

import com.yapcore.blocks.BlocksChannelPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

@Mixin(ClientboundCustomPayloadPacket.class)
public abstract class ClientboundCustomPayloadPacketMixin {

    @Inject(method = "lambda$static$1", at = @At("HEAD"))
    private static void yap$registerBlocksChannel(
            ArrayList<CustomPacketPayload.TypeAndCodec<? super FriendlyByteBuf, ?>> codecs,
            CallbackInfo ci) {
        codecs.add(new CustomPacketPayload.TypeAndCodec<>(
                BlocksChannelPayload.TYPE, BlocksChannelPayload.STREAM_CODEC));
    }
}
