package com.yapcore.link.selector;

import com.yapcore.link.api.ChannelIdentifier;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** BungeeCord Connect payload shape used by portal plugins on Folia backends. */
final class BungeeConnectPayloadTest {

    @Test
    void connectPayloadRoundTrip() throws Exception {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(raw)) {
            out.writeUTF("Connect");
            out.writeUTF("survival");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw.toByteArray()))) {
            assertEquals("Connect", in.readUTF());
            assertEquals("survival", in.readUTF());
        }
    }

    @Test
    void channelIdsMatchVelocityPaper() {
        assertEquals("bungeecord:main", ServerSelectorPlugin.BUNGEE_MAIN.id());
        assertEquals("minecraft:BungeeCord", ServerSelectorPlugin.BUNGEE_LEGACY.id());
    }

    @Test
    void isBungeeChannelAcceptsMainAndLegacy() throws Exception {
        Method m = ServerSelectorPlugin.class.getDeclaredMethod("isBungeeChannel", ChannelIdentifier.class);
        m.setAccessible(true);
        assertTrue((Boolean) m.invoke(null, ServerSelectorPlugin.BUNGEE_MAIN));
        assertTrue((Boolean) m.invoke(null, ServerSelectorPlugin.BUNGEE_LEGACY));
        assertTrue((Boolean) m.invoke(null, ChannelIdentifier.fromMcChannel("BungeeCord")));
    }
}
