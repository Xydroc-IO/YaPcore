package com.yapcore.protocol.via.remap;

import com.yapcore.protocol.java.codec.McCodec;
import io.netty.buffer.ByteBuf;

/**
 * Network Holder / HolderSet skip helpers (JE 1.20.5+ component era).
 */
public final class RegistryHolderSkip {

    private RegistryHolderSkip() {
    }

    /**
     * registryEntryHolder: VarInt id; 0 → inline {@code otherwise} payload via callback;
     * else registry id = id-1 (no further bytes).
     */
    public static void skipHolder(ByteBuf in, Runnable skipInline) {
        int id = McCodec.readVarInt(in);
        if (id == 0) {
            skipInline.run();
        }
        // else id-1 is registry reference — done
    }

    /**
     * HolderSet / IDSet: VarInt n; n==0 → Identifier tag string; else n-1 registry ids.
     */
    public static void skipHolderSet(ByteBuf in) {
        int n = McCodec.readVarInt(in);
        if (n == 0) {
            McCodec.readString(in, 32767);
            return;
        }
        for (int i = 0; i < n - 1; i++) {
            McCodec.readVarInt(in);
        }
    }

    public static void skipItemBlockProperty(ByteBuf in) {
        McCodec.readString(in, 32767);
        boolean exact = in.readBoolean();
        if (exact) {
            McCodec.readString(in, 32767);
        } else {
            McCodec.readString(in, 32767); // min
            McCodec.readString(in, 32767); // max
        }
    }

    public static void skipItemBlockPredicate(ByteBuf in) {
        if (in.readBoolean()) {
            skipHolderSet(in);
        }
        if (in.readBoolean()) {
            int n = McCodec.readVarInt(in);
            for (int i = 0; i < n; i++) {
                skipItemBlockProperty(in);
            }
        }
        SlotComponentCodec.skipOptionalNbt(in);
    }

    public static void skipArmorTrimMaterial(ByteBuf in) {
        // 1.21.1: assetName, ingredientId, overrideArmorAssets[{k,v}], description nbt
        McCodec.readString(in, 32767);
        McCodec.readVarInt(in);
        int n = McCodec.readVarInt(in);
        for (int i = 0; i < n; i++) {
            McCodec.readString(in, 32767);
            McCodec.readString(in, 32767);
        }
        SlotComponentCodec.skipNbt(in);
    }

    public static void skipArmorTrimPattern(ByteBuf in) {
        McCodec.readString(in, 32767);
        McCodec.readVarInt(in);
        SlotComponentCodec.skipNbt(in);
        in.readBoolean();
    }

    public static void skipInstrumentData(ByteBuf in) {
        // soundEvent holder + f32 useDuration + f32 range + description nbt
        skipHolder(in, () -> skipItemSoundEvent(in));
        in.readFloat();
        in.readFloat();
        SlotComponentCodec.skipNbt(in);
    }

    private static void skipItemSoundEvent(ByteBuf in) {
        // sound name + optional fixed range
        McCodec.readString(in, 32767);
        if (in.readBoolean()) {
            in.readFloat();
        }
    }
}
