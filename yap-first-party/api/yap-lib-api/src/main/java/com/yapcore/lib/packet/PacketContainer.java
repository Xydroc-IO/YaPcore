package com.yapcore.lib.packet;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Wrapper around an NMS {@code Packet} (or any intercept object). Read/write by
 * Java type + index among matching record components / instance fields.
 * Record packets are rebuilt in place on write.
 */
public final class PacketContainer {

    private Object handle;
    private PacketType type;

    public PacketContainer(Object handle, PacketType type) {
        this.handle = Objects.requireNonNull(handle, "handle");
        this.type = Objects.requireNonNull(type, "type");
    }

    public static PacketContainer wrap(Object handle, PacketType type) {
        return new PacketContainer(handle, type);
    }

    public Object handle() {
        return handle;
    }

    public void setHandle(Object handle) {
        this.handle = Objects.requireNonNull(handle, "handle");
    }

    public PacketType type() {
        return type;
    }

    public void setType(PacketType type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    public Class<?> nmsClass() {
        return handle.getClass();
    }

    public String nmsName() {
        return handle.getClass().getName();
    }

    @SuppressWarnings("unchecked")
    public <T> T read(Class<T> token, int index) {
        return (T) slot(token, index).get(handle);
    }

    public <T> T readOrNull(Class<T> token, int index) {
        List<Slot> slots = slotsOfType(token);
        if (index < 0 || index >= slots.size()) {
            return null;
        }
        @SuppressWarnings("unchecked")
        T cast = (T) slots.get(index).get(handle);
        return cast;
    }

    public void write(Class<?> token, int index, Object value) {
        List<Slot> slots = slotsOfType(token);
        Slot slot = slots.get(index);
        if (slot instanceof RecordIndex ri) {
            this.handle = rebuildRecord(ri.index(), value);
            return;
        }
        slot.set(handle, value);
    }

    public int count(Class<?> token) {
        return slotsOfType(token).size();
    }

    public StructureModifier<Object> modifier() {
        return new StructureModifier<>(this, Object.class, null);
    }

    public <T> StructureModifier<T> modifier(Class<T> token) {
        return new StructureModifier<>(this, token, null);
    }

    public <T> StructureModifier<T> modifier(Class<?> token, EquivalentConverter<T> converter) {
        return new StructureModifier<>(this, token, converter);
    }

    public StructureModifier<Integer> integers() {
        return modifier(Integer.class);
    }

    public StructureModifier<Double> doubles() {
        return modifier(Double.class);
    }

    public StructureModifier<Float> floats() {
        return modifier(Float.class);
    }

    public StructureModifier<Long> longs() {
        return modifier(Long.class);
    }

    public StructureModifier<Short> shorts() {
        return modifier(Short.class);
    }

    public StructureModifier<Byte> bytes() {
        return modifier(Byte.class);
    }

    public StructureModifier<Boolean> booleans() {
        return modifier(Boolean.class);
    }

    public StructureModifier<String> strings() {
        return modifier(String.class);
    }

    public StructureModifier<java.util.UUID> uuids() {
        return modifier(java.util.UUID.class);
    }

    public StructureModifier<byte[]> byteArrays() {
        return modifier(byte[].class);
    }

    public StructureModifier<int[]> integerArrays() {
        return modifier(int[].class);
    }

    public StructureModifier<org.bukkit.inventory.ItemStack> items() {
        EquivalentConverter<org.bukkit.inventory.ItemStack> conv = PacketConverters.items();
        return modifier(conv.nmsHint(), conv);
    }

    public StructureModifier<net.kyori.adventure.text.Component> chatComponents() {
        EquivalentConverter<net.kyori.adventure.text.Component> conv = PacketConverters.chat();
        return modifier(conv.nmsHint(), conv);
    }

    public StructureModifier<PacketBlockPos> blockPositions() {
        EquivalentConverter<PacketBlockPos> conv = PacketConverters.blockPositions();
        return modifier(conv.nmsHint(), conv);
    }

    public StructureModifier<PacketProfile> gameProfiles() {
        EquivalentConverter<PacketProfile> conv = PacketConverters.profiles();
        return modifier(conv.nmsHint(), conv);
    }

    public StructureModifier<PacketNbt> nbtModifiers() {
        EquivalentConverter<PacketNbt> conv = PacketConverters.nbt();
        return modifier(conv.nmsHint(), conv);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public StructureModifier<java.util.List<?>> lists() {
        return modifier((Class) java.util.List.class);
    }

    /** First {@link List} field unpacked as packed entity data ({@code set_entity_data}). */
    public java.util.List<PacketDataValue> dataValues() {
        return PacketDataValue.unpack(lists().readOrNull(0));
    }

    public WrappedEntityData entityMetadata() {
        return WrappedEntityData.of(this);
    }

    /** Field-copy clone (records rebuilt via canonical constructor). */
    public PacketContainer deepClone() {
        return wrap(cloneHandle(), type);
    }

    private Object rebuildRecord(int componentIndex, Object value) {
        Class<?> recordType = handle.getClass();
        RecordComponent[] components = recordType.getRecordComponents();
        try {
            Object[] args = new Object[components.length];
            Class<?>[] types = new Class<?>[components.length];
            for (int i = 0; i < components.length; i++) {
                args[i] = i == componentIndex ? value : components[i].getAccessor().invoke(handle);
                types[i] = components[i].getType();
            }
            var ctor = recordType.getDeclaredConstructor(types);
            ctor.setAccessible(true);
            return ctor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot rebuild record packet " + recordType.getName(), e);
        }
    }

    private Slot slot(Class<?> token, int index) {
        List<Slot> slots = slotsOfType(token);
        if (index < 0 || index >= slots.size()) {
            throw new IndexOutOfBoundsException(token.getSimpleName() + "[" + index + "] size=" + slots.size());
        }
        return slots.get(index);
    }

    private List<Slot> slotsOfType(Class<?> token) {
        List<Slot> slots = new ArrayList<>();
        Class<?> type = handle.getClass();
        RecordComponent[] components = type.getRecordComponents();
        if (components != null && components.length > 0) {
            for (int i = 0; i < components.length; i++) {
                RecordComponent c = components[i];
                if (matches(token, c.getType())) {
                    slots.add(new RecordIndex(c, i));
                }
            }
            return slots;
        }
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (matches(token, field.getType())) {
                    field.setAccessible(true);
                    slots.add(new FieldSlot(field));
                }
            }
            type = type.getSuperclass();
        }
        return slots;
    }

    private static boolean matches(Class<?> token, Class<?> actual) {
        if (token.isAssignableFrom(actual)) {
            return true;
        }
        if (token.isPrimitive()) {
            return wrapper(token) == actual;
        }
        if (actual.isPrimitive()) {
            return wrapper(actual) == token;
        }
        return false;
    }

    private static Class<?> wrapper(Class<?> primitive) {
        if (primitive == int.class) {
            return Integer.class;
        }
        if (primitive == long.class) {
            return Long.class;
        }
        if (primitive == double.class) {
            return Double.class;
        }
        if (primitive == float.class) {
            return Float.class;
        }
        if (primitive == boolean.class) {
            return Boolean.class;
        }
        if (primitive == byte.class) {
            return Byte.class;
        }
        if (primitive == short.class) {
            return Short.class;
        }
        if (primitive == char.class) {
            return Character.class;
        }
        return primitive;
    }

    private Object cloneHandle() {
        Class<?> type = handle.getClass();
        RecordComponent[] components = type.getRecordComponents();
        if (components != null && components.length > 0) {
            try {
                Object[] args = new Object[components.length];
                Class<?>[] types = new Class<?>[components.length];
                for (int i = 0; i < components.length; i++) {
                    args[i] = components[i].getAccessor().invoke(handle);
                    types[i] = components[i].getType();
                }
                var ctor = type.getDeclaredConstructor(types);
                ctor.setAccessible(true);
                return ctor.newInstance(args);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot clone record packet " + type.getName(), e);
            }
        }
        try {
            Object copy = PacketInstances.allocate(type);
            Class<?> cursor = type;
            while (cursor != null && cursor != Object.class) {
                for (Field field : cursor.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    field.setAccessible(true);
                    field.set(copy, field.get(handle));
                }
                cursor = cursor.getSuperclass();
            }
            return copy;
        } catch (RuntimeException | ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot clone packet " + type.getName(), e);
        }
    }

    private interface Slot {
        Object get(Object target);

        void set(Object target, Object value);
    }

    private static final class FieldSlot implements Slot {
        private final Field field;

        private FieldSlot(Field field) {
            this.field = field;
        }

        @Override
        public Object get(Object target) {
            try {
                return field.get(target);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(field.getName(), e);
            }
        }

        @Override
        public void set(Object target, Object value) {
            try {
                field.set(target, value);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(field.getName(), e);
            }
        }
    }

    private static final class RecordIndex implements Slot {
        private final RecordComponent component;
        private final int index;

        private RecordIndex(RecordComponent component, int index) {
            this.component = component;
            this.index = index;
        }

        int index() {
            return index;
        }

        @Override
        public Object get(Object target) {
            try {
                return component.getAccessor().invoke(target);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(component.getName(), e);
            }
        }

        @Override
        public void set(Object target, Object value) {
            throw new UnsupportedOperationException("records rebuild via PacketContainer.write");
        }
    }
}
