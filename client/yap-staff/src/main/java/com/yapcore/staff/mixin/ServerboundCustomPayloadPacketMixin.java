package com.yapcore.staff.mixin;

import com.yapcore.staff.StaffChannelPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

/**
 * Registers {@link StaffChannelPayload} on the serverbound custom-payload codec list
 * (vanilla leaves this list open via an empty Util.make consumer — same hook Fabric API / yap-bag use).
 */
@Mixin(ServerboundCustomPayloadPacket.class)
public abstract class ServerboundCustomPayloadPacketMixin {

    @Inject(method = "lambda$static$1", at = @At("HEAD"))
    private static void yap$registerStaffChannel(ArrayList<CustomPacketPayload.TypeAndCodec<? super FriendlyByteBuf, ?>> codecs,
                                                 CallbackInfo ci) {
        codecs.add(new CustomPacketPayload.TypeAndCodec<>(StaffChannelPayload.TYPE, StaffChannelPayload.STREAM_CODEC));
    }
}
