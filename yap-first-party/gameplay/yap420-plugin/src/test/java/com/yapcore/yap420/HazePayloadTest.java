package com.yapcore.yap420;

import com.yapcore.yap420.channel.HazePayload;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HazePayloadTest {

    @Test
    void roundTripHaze() {
        byte[] raw = HazePayload.encodeHaze(0.65, 200);
        var parsed = HazePayload.parse(raw).orElseThrow();
        assertEquals(0.65, parsed.intensity(), 0.001);
        assertEquals(200, parsed.durationTicks());
    }

    @Test
    void helloDetect() {
        assertTrue(HazePayload.isHello("HELLO".getBytes(StandardCharsets.UTF_8)));
        assertTrue(HazePayload.isHello("hello".getBytes(StandardCharsets.UTF_8)));
    }
}
