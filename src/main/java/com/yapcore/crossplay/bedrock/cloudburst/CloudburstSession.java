package com.yapcore.crossplay.bedrock.cloudburst;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleNamedDefinition;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.common.NamedDefinition;
import org.cloudburstmc.protocol.common.SimpleDefinitionRegistry;
import org.cloudburstmc.protocol.common.util.VarInts;

/** Per-client Cloudburst codec helper bound to loaded palettes. */
public final class CloudburstSession {

    private static final String[] GEYSER_CAMERA_PRESETS = {
            "minecraft:first_person",
            "minecraft:free",
            "minecraft:third_person",
            "minecraft:third_person_front",
            "geyser:free_audio",
            "geyser:free_effects",
            "geyser:free_audio_effects"
    };

    private final int clientProtocol;
    private final BedrockCodec codec;
    private final BedrockCodecHelper helper;
    private final CloudburstPaletteRegistry.BandPalettes palettes;

    private CloudburstSession(int clientProtocol, BedrockCodec codec, BedrockCodecHelper helper,
                              CloudburstPaletteRegistry.BandPalettes palettes) {
        this.clientProtocol = clientProtocol;
        this.codec = codec;
        this.helper = helper;
        this.palettes = palettes;
    }

    public static CloudburstSession create(int clientProtocol) {
        BedrockCodec codec = CloudburstCodecIndex.codecFor(clientProtocol);
        CloudburstPaletteRegistry.BandPalettes palettes =
                CloudburstPaletteRegistry.get().forProtocol(clientProtocol);
        BedrockCodecHelper helper = codec.createHelper();
        helper.setItemDefinitions(palettes.items());
        helper.setBlockDefinitions(palettes.blocks());
        // Geyser startGame() binds CameraDefinitions before encode — required for CameraPresets.
        helper.setCameraPresetDefinitions(geyserCameraPresetDefinitions());
        return new CloudburstSession(clientProtocol, codec, helper, palettes);
    }

    /** Same identifiers as {@link CloudburstPackets#cameraPresetsVanilla()}. */
    public static SimpleDefinitionRegistry<NamedDefinition> geyserCameraPresetDefinitions() {
        SimpleDefinitionRegistry.Builder<NamedDefinition> builder = SimpleDefinitionRegistry.builder();
        for (int i = 0; i < GEYSER_CAMERA_PRESETS.length; i++) {
            builder.add(new SimpleNamedDefinition(GEYSER_CAMERA_PRESETS[i], i));
        }
        return builder.build();
    }

    /** @deprecated Prefer {@link #geyserCameraPresetDefinitions()}. */
    @Deprecated
    public static SimpleDefinitionRegistry<NamedDefinition> vanillaCameraPresetDefinitions() {
        return geyserCameraPresetDefinitions();
    }

    public ByteBuf encode(BedrockPacket packet) {
        ByteBuf buf = Unpooled.buffer(256);
        int packetId = codec.getPacketDefinition(packet.getClass()).getId();
        VarInts.writeUnsignedInt(buf, packetId & 0x3ff);
        codec.tryEncode(helper, buf, packet);
        return buf;
    }

    public BedrockPacket decode(int id, ByteBuf body) {
        return codec.tryDecode(helper, body, id);
    }

    public BedrockCodec codec() {
        return codec;
    }

    public BedrockCodecHelper helper() {
        return helper;
    }

    public int protocol() {
        return clientProtocol;
    }

    public List<ItemDefinition> itemDefinitions() {
        return palettes.itemDefinitionList();
    }

    public CloudburstPaletteRegistry.BandPalettes palettes() {
        return palettes;
    }
}
