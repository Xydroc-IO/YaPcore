package com.yapcore.bag.mixin;

import com.yapcore.bag.BagChannelPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

/**
 * Registers {@link BagChannelPayload} on the serverbound custom-payload codec list
 * (vanilla leaves this list open via an empty Util.make consumer — same hook Fabric API uses).
 */
@Mixin(ServerboundCustomPayloadPacket.class)
public abstract class ServerboundCustomPayloadPacketMixin {

    @Inject(method = "lambda$static$1", at = @At("HEAD"))
    private static void yap$registerBagChannel(ArrayList<CustomPacketPayload.TypeAndCodec<? super FriendlyByteBuf, ?>> codecs,
                                                 CallbackInfo ci) {
        codecs.add(new CustomPacketPayload.TypeAndCodec<>(BagChannelPayload.TYPE, BagChannelPayload.STREAM_CODEC));
    }
}
