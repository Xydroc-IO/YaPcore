package com.yapcore.lib.packet;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;

/**
 * One packed entity-data entry ({@code SynchedEntityData.DataValue}).
 * Rewrite the NMS list via {@link PacketContainer#lists()}; this type is the read view.
 */
public final class PacketDataValue {

    private final Object handle;
    private final int id;
    private final Object serializer;
    private final Object value;

    public PacketDataValue(int id, Object serializer, Object value) {
        this(null, id, serializer, value);
    }

    private PacketDataValue(Object handle, int id, Object serializer, Object value) {
        this.handle = handle;
        this.id = id;
        this.serializer = serializer;
        this.value = value;
    }

    public Object handle() {
        return handle;
    }

    public int id() {
        return id;
    }

    public Object serializer() {
        return serializer;
    }

    public Object value() {
        return value;
    }

    /** Rebuild the NMS/record handle with a new value (same id + serializer). */
    public PacketDataValue withValue(Object newValue) {
        if (handle == null) {
            return new PacketDataValue(null, id, serializer, newValue);
        }
        Class<?> type = handle.getClass();
        RecordComponent[] components = type.getRecordComponents();
        if (components != null && components.length >= 3) {
            try {
                Object[] args = new Object[components.length];
                Class<?>[] types = new Class<?>[components.length];
                for (int i = 0; i < components.length; i++) {
                    types[i] = components[i].getType();
                    args[i] = i == 2 ? newValue : components[i].getAccessor().invoke(handle);
                }
                var ctor = type.getDeclaredConstructor(types);
                ctor.setAccessible(true);
                return fromNms(ctor.newInstance(args));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot rebuild data value " + type.getName(), e);
            }
        }
        Object copy = PacketInstances.allocate(type);
        copyFields(handle, copy);
        Field valueField = findValueField(type);
        if (valueField == null) {
            throw new IllegalStateException("Cannot find value field on " + type.getName());
        }
        try {
            valueField.set(copy, newValue);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(valueField.getName(), e);
        }
        return fromNms(copy);
    }

    public static List<PacketDataValue> unpack(Object list) {
        if (!(list instanceof List<?> items)) {
            return List.of();
        }
        List<PacketDataValue> out = new ArrayList<>(items.size());
        for (Object item : items) {
            out.add(fromNms(item));
        }
        return List.copyOf(out);
    }

    public static PacketDataValue fromNms(Object nms) {
        if (nms instanceof PacketDataValue packed) {
            return packed;
        }
        if (nms == null) {
            return new PacketDataValue(null, 0, null, null);
        }
        RecordComponent[] components = nms.getClass().getRecordComponents();
        if (components != null && components.length >= 3) {
            try {
                Object idObj = components[0].getAccessor().invoke(nms);
                int id = idObj instanceof Number number ? number.intValue() : 0;
                return new PacketDataValue(
                        nms,
                        id,
                        components[1].getAccessor().invoke(nms),
                        components[2].getAccessor().invoke(nms));
            } catch (ReflectiveOperationException ignored) {
                // fields
            }
        }
        Integer id = intField(nms, "id", "index");
        Object serializer = field(nms, "serializer", "accessor");
        Object value = field(nms, "value");
        return new PacketDataValue(nms, id == null ? 0 : id, serializer, value != null ? value : nms);
    }

    private static Integer intField(Object nms, String... names) {
        Object raw = field(nms, names);
        return raw instanceof Number number ? number.intValue() : null;
    }

    private static Object field(Object nms, String... names) {
        Class<?> type = nms.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                String fname = field.getName().toLowerCase();
                for (String name : names) {
                    if (fname.equals(name.toLowerCase())) {
                        field.setAccessible(true);
                        try {
                            return field.get(nms);
                        } catch (IllegalAccessException e) {
                            return null;
                        }
                    }
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static Field findValueField(Class<?> type) {
        Class<?> cursor = type;
        while (cursor != null && cursor != Object.class) {
            for (Field field : cursor.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if ("value".equalsIgnoreCase(field.getName())) {
                    field.setAccessible(true);
                    return field;
                }
            }
            cursor = cursor.getSuperclass();
        }
        return null;
    }

    private static void copyFields(Object from, Object to) {
        Class<?> cursor = from.getClass();
        while (cursor != null && cursor != Object.class) {
            for (Field field : cursor.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    field.set(to, field.get(from));
                } catch (IllegalAccessException ignored) {
                    // skip
                }
            }
            cursor = cursor.getSuperclass();
        }
    }
}
