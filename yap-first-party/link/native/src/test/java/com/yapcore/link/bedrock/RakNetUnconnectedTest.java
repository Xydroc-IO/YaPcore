package com.yapcore.link.bedrock;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RakNetUnconnectedTest {

    @Test
    void motdIncludesTrailingPorts() {
        String motd = RakNetUnconnected.buildMotd(
                "YaP Link", 2207, "26.45", 0, 500, 42L, "Hub", 19132, 19132);
        assertEquals(
                "MCPE;YaP Link;2207;26.45;0;500;42;Hub;Survival;1;19132;19132;",
                motd);
        String[] parts = motd.split(";", -1);
        assertTrue(parts.length >= 12);
        assertEquals("19132", parts[10]);
        assertEquals("19132", parts[11]);
    }

    @Test
    void pongRoundTrip() {
        String motd = RakNetUnconnected.buildMotd(
                "Test", 2207, "26.45", 1, 20, 99L, "Sub", 25565, 25565);
        ByteBuf pong = RakNetUnconnected.buildPong(12345L, 99L, motd);
        assertEquals(RakNetUnconnected.ID_UNCONNECTED_PONG, pong.getUnsignedByte(0));
        assertEquals(12345L, pong.getLong(1));
        assertEquals(99L, pong.getLong(9));

        ByteBuf ping = Unpooled.buffer();
        ping.writeByte(RakNetUnconnected.ID_UNCONNECTED_PING);
        ping.writeLong(777L);
        ping.writeBytes(RakNetUnconnected.MAGIC);
        ping.writeLong(1L);
        assertTrue(RakNetUnconnected.isUnconnectedPing(ping));
        assertEquals(777L, RakNetUnconnected.readPingTime(ping));
        ping.release();
        pong.release();
    }
}
