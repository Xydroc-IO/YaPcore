package com.yapcore.chat;

import com.yapcore.chat.service.ChatServiceImpl;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatRelayOpsTest {

    @Test
    void constructorDefaultsMatchProduct() {
        ChatConfig config = new ChatConfig(null);
        assertEquals("global", config.defaultChannel());
        assertTrue(config.networkEnabled());
        assertTrue(config.networkRelayChannels().contains("global"));
        assertTrue(config.networkRelayChannels().contains("trade"));
        assertTrue(config.networkRelayChannels().contains("staff"));
        assertTrue(config.networkRelayChannels().contains("admin"));
        assertFalse(config.networkRelayChannels().contains("local"));
    }

    @Test
    void embeddedConfigYamlDeclaresGlobalNetworkRelay() throws Exception {
        try (InputStream in = ChatConfig.class.getClassLoader().getResourceAsStream("config.yml")) {
            assertNotNull(in, "plugin config.yml must be on test classpath");
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            assertEquals("global", yaml.getString("default-channel"));
            assertTrue(yaml.getBoolean("network.enabled"));
            assertTrue(yaml.getStringList("network.relay-channels").contains("global"));
            assertTrue(yaml.getStringList("network.relay-channels").contains("trade"));
            assertFalse(yaml.getStringList("network.relay-channels").contains("local"));
        }
    }

    @Test
    void typingInGlobalBuildsRelayPayloadForOtherServers() {
        ChatConfig config = typicalNetworkConfig();
        ChatServiceImpl service = new ChatServiceImpl(null, config);
        UUID alex = UUID.fromString("11111111-1111-1111-1111-111111111111");

        ChatRelayOps.Speak speak = ChatRelayOps.resolveSpeak(config, "global", "hello friends");
        assertEquals("global", speak.channel());
        assertEquals("hello friends", speak.message());
        assertTrue(ChatRelayOps.shouldRelay(config, speak.channel()));

        Optional<byte[]> payload = service.prepareRelayPayload(
                speak.channel(), alex, "Alex", speak.message());
        assertTrue(payload.isPresent());

        ChatRelayOps.RelayPacket packet = ChatRelayOps.parse(payload.get()).orElseThrow();
        assertEquals("global", packet.channelId());
        assertEquals("survival", packet.serverId());
        assertEquals(alex, packet.senderUuid());
        assertEquals("Alex", packet.senderName());
        assertEquals("hello friends", packet.message());
    }

    @Test
    void localPrefixDoesNotRelay() {
        ChatConfig config = typicalNetworkConfig();
        ChatServiceImpl service = new ChatServiceImpl(null, config);

        ChatRelayOps.Speak speak = ChatRelayOps.resolveSpeak(config, "global", "!anyone nearby?");
        assertEquals("local", speak.channel());
        assertEquals("anyone nearby?", speak.message());
        assertFalse(ChatRelayOps.shouldRelay(config, speak.channel()));
        assertTrue(service.prepareRelayPayload(
                speak.channel(), UUID.randomUUID(), "Alex", speak.message()).isEmpty());
    }

    @Test
    void networkDisabledSkipsRelay() {
        ChatConfig config = typicalNetworkConfig();
        config.applyNetworkForTest(false, "lobby", Set.of("global"));
        ChatServiceImpl service = new ChatServiceImpl(null, config);
        assertTrue(service.prepareRelayPayload(
                "global", UUID.randomUUID(), "Alex", "hi").isEmpty());
    }

    @Test
    void incomingRelayRendersServerTag() {
        ChatConfig config = typicalNetworkConfig();
        var component = ChatFormat.formatNetwork(config, "global", "lobby", "Alex", "cross-server hi");
        String plain = LegacyComponentSerializer.legacySection().serialize(component);
        assertTrue(plain.contains("lobby"));
        assertTrue(plain.contains("Alex"));
        assertTrue(plain.contains("cross-server hi"));
    }

    @Test
    void parseRejectsGarbage() {
        assertTrue(ChatRelayOps.parse((byte[]) null).isEmpty());
        assertTrue(ChatRelayOps.parse("NOPE|a|b|c|d|e".getBytes(StandardCharsets.UTF_8)).isEmpty());
        assertTrue(ChatRelayOps.parse("RELAY|global|lobby|not-a-uuid|Alex|hi").isEmpty());
    }

    private static ChatConfig typicalNetworkConfig() {
        ChatConfig config = new ChatConfig(null);
        config.applyChannelsForTest("global", Map.of(
                "global", new ChatConfig.ChannelDef("global", "{player}&7: {message}", -1, ""),
                "local", new ChatConfig.ChannelDef("local", "{player}&7: {message}", 100, ""),
                "trade", new ChatConfig.ChannelDef("trade", "{player}&7: {message}", -1, "")));
        config.applyNetworkForTest(true, "survival", Set.of("global", "trade", "staff", "admin"));
        return config;
    }
}
