package com.yapcore.link.chat;

import com.yapcore.link.api.LinkMetrics;
import com.yapcore.link.api.LinkPlugin;
import com.yapcore.link.api.LinkPluginDescription;
import com.yapcore.link.api.LinkPlayer;
import com.yapcore.link.api.LinkProxy;
import com.yapcore.link.api.RegisteredServer;
import com.yapcore.link.api.event.PluginMessageEvent;
import com.yapcore.link.plugin.LinkEventBus;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ChatBridgePluginTest {

    @Test
    void relaysBackendYapChatToOtherServers() {
        ChatBridgePlugin plugin = new ChatBridgePlugin();
        RecordingProxy proxy = new RecordingProxy();
        plugin.onLoad(new StubContext(proxy));
        plugin.onEnable();

        LinkEventBus bus = new LinkEventBus();
        bus.register(plugin);

        byte[] data = "test-payload".getBytes();
        bus.fire(new PluginMessageEvent(
                PluginMessageEvent.SourceKind.BACKEND,
                Optional.empty(),
                Optional.of(new StubServer("lobby")),
                ChatBridgePlugin.CHANNEL,
                data
        ));

        assertEquals(1, proxy.broadcasts.size());
        assertEquals("survival", proxy.broadcasts.get(0).target().name());
        assertEquals("lobby", proxy.broadcasts.get(0).exclude().name());
        assertArrayEquals(data, proxy.broadcasts.get(0).data());
    }

    private record Broadcast(RegisteredServer target, RegisteredServer exclude, byte[] data) {
    }

    private static final class RecordingProxy implements LinkProxy {

        private final List<Broadcast> broadcasts = new ArrayList<>();
        private final List<RegisteredServer> servers = List.of(
                new StubServer("lobby"),
                new StubServer("survival")
        );

        @Override
        public Logger logger() {
            return Logger.getLogger("test");
        }

        @Override
        public Path home() {
            return Path.of(".");
        }

        @Override
        public Collection<LinkPlayer> players() {
            return List.of();
        }

        @Override
        public Optional<LinkPlayer> player(String username) {
            return Optional.empty();
        }

        @Override
        public Collection<RegisteredServer> servers() {
            return servers;
        }

        @Override
        public Optional<RegisteredServer> server(String name) {
            return servers.stream().filter(s -> s.name().equalsIgnoreCase(name)).findFirst();
        }

        @Override
        public void registerChannel(com.yapcore.link.api.ChannelIdentifier channel) {
        }

        @Override
        public void fireEvent(com.yapcore.link.api.event.LinkEvent event) {
        }

        @Override
        public void registerCommand(String name, com.yapcore.link.api.SimpleCommand command) {
        }

        @Override
        public void registerCommand(String name, String permission, com.yapcore.link.api.SimpleCommand command) {
        }

        @Override
        public void broadcastPluginMessage(
                RegisteredServer target,
                com.yapcore.link.api.ChannelIdentifier channel,
                byte[] data,
                RegisteredServer excludeSource
        ) {
            broadcasts.add(new Broadcast(target, excludeSource, data));
        }

        @Override
        public LinkMetrics metrics() {
            return new LinkMetrics() {
                @Override
                public void counter(String name, long delta) {
                }

                @Override
                public void gauge(String name, long value) {
                }

                @Override
                public long counter(String name) {
                    return 0;
                }
            };
        }
    }

    private record StubContext(LinkProxy proxy) implements LinkPlugin.LinkPluginContext {
        @Override
        public LinkPluginDescription description() {
            return new LinkPluginDescription("t", "t", "1", "x", List.of());
        }

        @Override
        public Path dataDirectory() {
            return Path.of(".");
        }

        @Override
        public Logger logger() {
            return Logger.getLogger("test");
        }
    }

    private record StubServer(String name) implements RegisteredServer {
        @Override
        public String host() {
            return "127.0.0.1";
        }

        @Override
        public int port() {
            return 25565;
        }

        @Override
        public void sendPluginMessage(com.yapcore.link.api.ChannelIdentifier channel, byte[] data) {
        }
    }
}
