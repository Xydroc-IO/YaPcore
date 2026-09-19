package com.yapcore.lib.packet;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Packs Java/Bukkit values into NMS {@code SynchedEntityData.DataValue}
 * using {@code EntityDataSerializers} (no EntityDataAccessor required).
 */
public final class EntityDataCodec {

    private static Class<?> dataValueType;
    private static Constructor<?> dataValueCtor;
    private static final Map<String, Object> serializers = new HashMap<>();
    private static boolean loaded;

    private EntityDataCodec() {
    }

    /** NMS DataValue, or {@link PacketDataValue} when NMS is absent (tests). */
    public static Object pack(int watcherId, Object value) {
        ensure();
        Object nmsValue = toNms(value);
        Object serializer = serializerFor(value);
        Object nms = instantiate(watcherId, serializer, nmsValue);
        if (nms != null) {
            return nms;
        }
        return new PacketDataValue(watcherId, serializer, nmsValue);
    }

    public static Object toNms(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Optional<?> opt) {
            return opt.map(EntityDataCodec::toNms);
        }
        if (value instanceof Component component) {
            return PacketConverters.chat().toNms(component);
        }
        if (value instanceof ItemStack stack) {
            return PacketConverters.items().toNms(stack);
        }
        if (value instanceof PacketBlockPos pos) {
            return PacketConverters.blockPositions().toNms(pos);
        }
        if (value instanceof PacketNbt nbt) {
            return PacketConverters.nbt().toNms(nbt);
        }
        if (value instanceof PacketProfile profile) {
            return PacketConverters.profiles().toNms(profile);
        }
        if (value instanceof UUID uuid) {
            return Optional.of(uuid);
        }
        return value;
    }

    public static Object adapt(Object newValue, Object existing) {
        if (existing instanceof Optional) {
            Object inner = newValue instanceof Optional<?> opt ? opt.orElse(null) : newValue;
            return Optional.ofNullable(toNms(inner));
        }
        return toNms(newValue);
    }

    private static Object instantiate(int id, Object serializer, Object nmsValue) {
        if (dataValueCtor == null || serializer == null) {
            return null;
        }
        try {
            return dataValueCtor.newInstance(id, serializer, nmsValue);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Object serializerFor(Object value) {
        if (value instanceof Optional<?> opt) {
            if (opt.isEmpty()) {
                return ser("OPTIONAL_COMPONENT", "OPTIONAL_UUID", "OPTIONAL_BLOCK_POS");
            }
            Object inner = opt.orElse(null);
            if (inner instanceof Component || isNmsComponent(inner)) {
                return ser("OPTIONAL_COMPONENT");
            }
            if (inner instanceof UUID) {
                return ser("OPTIONAL_UUID", "OPTIONAL_LIVING_ENTITY_REFERENCE");
            }
            if (inner instanceof PacketBlockPos
                    || (inner != null && inner.getClass().getName().endsWith("BlockPos"))) {
                return ser("OPTIONAL_BLOCK_POS");
            }
            return ser("OPTIONAL_COMPONENT");
        }
        if (value instanceof Boolean) {
            return ser("BOOLEAN");
        }
        if (value instanceof Byte) {
            return ser("BYTE");
        }
        if (value instanceof Integer) {
            return ser("INT");
        }
        if (value instanceof Long) {
            return ser("LONG");
        }
        if (value instanceof Float) {
            return ser("FLOAT");
        }
        if (value instanceof String) {
            return ser("STRING");
        }
        if (value instanceof Component || isNmsComponent(value)) {
            return ser("COMPONENT");
        }
        if (value instanceof ItemStack || (value != null && value.getClass().getName().contains("ItemStack"))) {
            return ser("ITEM_STACK");
        }
        if (value instanceof PacketBlockPos || (value != null && value.getClass().getName().endsWith("BlockPos"))) {
            return ser("BLOCK_POS");
        }
        if (value instanceof PacketNbt || (value != null && value.getClass().getName().contains("CompoundTag"))) {
            return ser("COMPOUND_TAG");
        }
        if (value instanceof UUID) {
            return ser("OPTIONAL_UUID", "OPTIONAL_LIVING_ENTITY_REFERENCE");
        }
        return null;
    }

    private static boolean isNmsComponent(Object value) {
        return value != null && value.getClass().getName().startsWith("net.minecraft.network.chat.");
    }

    private static Object ser(String... names) {
        for (String name : names) {
            Object found = serializers.get(name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static synchronized void ensure() {
        if (loaded) {
            return;
        }
        loaded = true;
        ClassLoader loader = loader();
        Class<?> synched = clazz(loader, "net.minecraft.network.syncher.SynchedEntityData");
        if (synched != null) {
            for (Class<?> nested : synched.getDeclaredClasses()) {
                if ("DataValue".equals(nested.getSimpleName())) {
                    dataValueType = nested;
                    break;
                }
            }
        }
        if (dataValueType == null) {
            dataValueType = clazz(loader, "net.minecraft.network.syncher.SynchedEntityData$DataValue");
        }
        Class<?> serializerType = clazz(loader, "net.minecraft.network.syncher.EntityDataSerializer");
        if (dataValueType != null && serializerType != null) {
            dataValueCtor = ctor(dataValueType, int.class, serializerType, Object.class);
        }
        Class<?> table = clazz(loader, "net.minecraft.network.syncher.EntityDataSerializers");
        if (table != null) {
            for (Field field : table.getFields()) {
                if ((field.getModifiers() & java.lang.reflect.Modifier.STATIC) == 0) {
                    continue;
                }
                try {
                    Object value = field.get(null);
                    if (value != null) {
                        serializers.put(field.getName(), value);
                    }
                } catch (IllegalAccessException ignored) {
                    // skip
                }
            }
        }
    }

    private static ClassLoader loader() {
        try {
            return org.bukkit.Bukkit.getServer().getClass().getClassLoader();
        } catch (Throwable t) {
            return EntityDataCodec.class.getClassLoader();
        }
    }

    private static Class<?> clazz(ClassLoader loader, String name) {
        try {
            return Class.forName(name, true, loader);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static Constructor<?> ctor(Class<?> type, Class<?>... params) {
        try {
            Constructor<?> ctor = type.getDeclaredConstructor(params);
            ctor.setAccessible(true);
            return ctor;
        } catch (NoSuchMethodException e) {
            for (Constructor<?> ctor : type.getDeclaredConstructors()) {
                if (ctor.getParameterCount() == params.length) {
                    ctor.setAccessible(true);
                    return ctor;
                }
            }
            return null;
        }
    }
}
