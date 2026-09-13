package com.yapcore.link.bedrock.codec;

import com.yapcore.link.bedrock.cloudburst.LinkPaletteRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Comparator;
import java.util.List;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.v2168.Bedrock_v2168;
import org.cloudburstmc.protocol.bedrock.codec.v2169.Bedrock_v2169;
import org.cloudburstmc.protocol.bedrock.codec.v2192.Bedrock_v2192;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleNamedDefinition;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePacksInfoPacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerToClientHandshakePacket;
import org.cloudburstmc.protocol.common.NamedDefinition;
import org.cloudburstmc.protocol.common.SimpleDefinitionRegistry;
import org.cloudburstmc.protocol.common.util.VarInts;

/**
 * Cloudburst codec index + encode/decode for Link Bedrock (login + join).
 */
public final class LinkCloudburstCodecs {

    public static final int MIN_MODERN = 2168;

    private static final String[] GEYSER_CAMERA_PRESETS = {
            "minecraft:first_person",
            "minecraft:free",
            "minecraft:third_person",
            "minecraft:third_person_front",
            "geyser:free_audio",
            "geyser:free_effects",
            "geyser:free_audio_effects"
    };

    private static final List<BedrockCodec> CODECS = List.of(
            Bedrock_v2192.CODEC,
            Bedrock_v2169.CODEC,
            Bedrock_v2168.CODEC
    );

    private LinkCloudburstCodecs() {
    }

    public static boolean isModern(int clientProtocol) {
        return clientProtocol >= MIN_MODERN;
    }

    public static BedrockCodec codecFor(int clientProtocol) {
        return CODECS.stream()
                .filter(c -> c.getProtocolVersion() <= clientProtocol)
                .max(Comparator.comparingInt(BedrockCodec::getProtocolVersion))
                .orElse(CODECS.get(0));
    }

    public static String bandFor(int codecProtocol) {
        return codecProtocol >= 2192 ? "band_26_50" : "band_26_40";
    }

    public static Session open(int clientProtocol) {
        BedrockCodec codec = codecFor(clientProtocol);
        LinkPaletteRegistry.BandPalettes palettes = LinkPaletteRegistry.get().forProtocol(clientProtocol);
        BedrockCodecHelper helper = codec.createHelper();
        helper.setItemDefinitions(palettes.items());
        helper.setBlockDefinitions(palettes.blocks());
        helper.setCameraPresetDefinitions(geyserCameraPresetDefinitions());
        return new Session(clientProtocol, codec, helper, palettes);
    }

    public static SimpleDefinitionRegistry<NamedDefinition> geyserCameraPresetDefinitions() {
        SimpleDefinitionRegistry.Builder<NamedDefinition> builder = SimpleDefinitionRegistry.builder();
        for (int i = 0; i < GEYSER_CAMERA_PRESETS.length; i++) {
            builder.add(new SimpleNamedDefinition(GEYSER_CAMERA_PRESETS[i], i));
        }
        return builder.build();
    }

    public static NetworkSettingsPacket networkSettingsGeyser() {
        NetworkSettingsPacket packet = new NetworkSettingsPacket();
        packet.setCompressionThreshold(512);
        packet.setCompressionAlgorithm(PacketCompressionAlgorithm.ZLIB);
        packet.setClientThrottleEnabled(false);
        packet.setClientThrottleThreshold(0);
        packet.setClientThrottleScalar(0f);
        return packet;
    }

    public static ServerToClientHandshakePacket serverToClientHandshake(String jwt) {
        ServerToClientHandshakePacket packet = new ServerToClientHandshakePacket();
        packet.setJwt(jwt == null ? "" : jwt);
        return packet;
    }

    public static PlayStatusPacket playStatusLoginSuccess() {
        PlayStatusPacket packet = new PlayStatusPacket();
        packet.setStatus(PlayStatusPacket.Status.LOGIN_SUCCESS);
        return packet;
    }

    public static ResourcePacksInfoPacket resourcePacksInfoEmpty() {
        ResourcePacksInfoPacket packet = new ResourcePacksInfoPacket();
        packet.setForcedToAccept(false);
        packet.setHasAddonPacks(false);
        packet.setScriptingEnabled(false);
        packet.setVibrantVisualsForceDisabled(false);
        packet.setWorldTemplateId(new java.util.UUID(0L, 0L));
        packet.setWorldTemplateVersion("");
        return packet;
    }

    public static ResourcePackStackPacket resourcePackStackEmpty() {
        ResourcePackStackPacket packet = new ResourcePackStackPacket();
        packet.setForcedToAccept(false);
        packet.setGameVersion("*");
        packet.setExperimentsPreviouslyToggled(false);
        packet.setHasEditorPacks(false);
        return packet;
    }

    public static final class Session {
        private final int clientProtocol;
        private final BedrockCodec codec;
        private final BedrockCodecHelper helper;
        private final LinkPaletteRegistry.BandPalettes palettes;

        private Session(int clientProtocol, BedrockCodec codec, BedrockCodecHelper helper,
                        LinkPaletteRegistry.BandPalettes palettes) {
            this.clientProtocol = clientProtocol;
            this.codec = codec;
            this.helper = helper;
            this.palettes = palettes;
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

        public LinkPaletteRegistry.BandPalettes palettes() {
            return palettes;
        }

        public List<ItemDefinition> itemDefinitions() {
            return palettes.itemDefinitionList();
        }

        public int packetId(Class<? extends BedrockPacket> type) {
            return codec.getPacketDefinition(type).getId();
        }
    }
}
