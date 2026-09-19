package com.yapcore.lib.packet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PacketContainerTest {

    public record FakeMove(double x, double y, double z, boolean onGround) {
    }

    public static final class MutableChat {
        public String text = "hi";
        public int flags = 3;
    }

    @Test
    void readsAndRebuildsRecord() {
        PacketContainer box = PacketContainer.wrap(
                new FakeMove(1.5, 64.0, -8.25, true),
                PacketTypes.Play.Client.MOVE_PLAYER_POS);
        assertEquals(1.5, box.read(Double.class, 0));
        assertEquals(64.0, box.read(Double.class, 1));
        assertTrue(box.read(Boolean.class, 0));
        box.write(Double.class, 1, 70.0);
        FakeMove rebuilt = (FakeMove) box.handle();
        assertEquals(70.0, rebuilt.y());
        assertEquals(1.5, rebuilt.x());
    }

    @Test
    void writesMutableFields() {
        MutableChat chat = new MutableChat();
        PacketContainer box = PacketContainer.wrap(chat, PacketTypes.Play.Client.CHAT);
        assertEquals("hi", box.read(String.class, 0));
        box.write(String.class, 0, "there");
        box.write(Integer.class, 0, 9);
        assertEquals("there", chat.text);
        assertEquals(9, chat.flags);
        assertEquals(1, box.count(String.class));
        assertEquals(1, box.count(Integer.class));
    }

    @Test
    void structureModifierAndClone() {
        PacketContainer box = PacketContainer.wrap(
                new FakeMove(1.5, 64.0, -8.25, true),
                PacketTypes.Play.Client.POSITION);
        assertEquals(64.0, box.doubles().read(1));
        box.doubles().write(1, 80.0);
        assertEquals(80.0, ((FakeMove) box.handle()).y());
        PacketContainer clone = box.deepClone();
        clone.doubles().write(1, 90.0);
        assertEquals(80.0, ((FakeMove) box.handle()).y());
        assertEquals(90.0, ((FakeMove) clone.handle()).y());
    }
}
