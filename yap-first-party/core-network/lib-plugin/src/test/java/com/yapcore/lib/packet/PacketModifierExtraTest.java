package com.yapcore.lib.packet;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

final class PacketModifierExtraTest {

    public record FakeData(int id, String serializer, Object value) {
    }

    public static final class FakeNbtPacket {
        public Object tag = "{Count:1b}";
        public List<FakeData> packed = List.of(new FakeData(2, "byte", (byte) 1));
    }

    public static final class FakeMetaPacket {
        public int entityId = 7;
        public List<FakeData> packed = List.of(
                new FakeData(2, "byte", (byte) 1),
                new FakeData(8, "chat", "old"));
    }

    @Test
    void wrappedEntityDataSetValue() {
        FakeMetaPacket handle = new FakeMetaPacket();
        PacketContainer box = PacketContainer.wrap(handle, PacketTypes.Play.Server.ENTITY_METADATA);
        WrappedEntityData meta = box.entityMetadata();
        assertEquals(7, meta.entityId());
        meta.setEntityId(9);
        assertEquals(9, handle.entityId);
        assertEquals("old", meta.getValue(8));
        meta.setValue(8, "new");
        assertEquals("new", meta.getValue(8));
        assertEquals("new", handle.packed.get(1).value());
        meta.setValue(40, true);
        assertEquals(true, meta.getValue(40));
    }

    @Test
    void nbtRoundTripWithoutNms() {
        PacketNbt nbt = PacketConverters.nbt().fromNms("{id:stone}");
        assertEquals("{id:stone}", nbt.snbt());
        assertEquals("{id:stone}", PacketConverters.nbt().toNms(nbt));
    }

    @Test
    void dataValuesUnpackRecord() {
        FakeNbtPacket handle = new FakeNbtPacket();
        PacketContainer box = PacketContainer.wrap(handle, PacketTypes.Play.Server.ENTITY_METADATA);
        List<PacketDataValue> values = box.dataValues();
        assertEquals(1, values.size());
        assertEquals(2, values.get(0).id());
        assertEquals("byte", values.get(0).serializer());
        assertEquals((byte) 1, values.get(0).value());
    }

    @Test
    void allocateAndCloneMutable() {
        PacketContainerTest.MutableChat chat = new PacketContainerTest.MutableChat();
        chat.text = "clone-me";
        PacketContainer box = PacketContainer.wrap(chat, PacketTypes.Play.Client.CHAT);
        Object copy = PacketInstances.allocate(PacketContainerTest.MutableChat.class);
        assertNotSame(chat, copy);
        PacketContainer clone = box.deepClone();
        clone.strings().write(0, "other");
        assertEquals("clone-me", chat.text);
        assertEquals("other", ((PacketContainerTest.MutableChat) clone.handle()).text);
        assertSame(PacketTypes.Play.Client.CHAT, clone.type());
    }
}
