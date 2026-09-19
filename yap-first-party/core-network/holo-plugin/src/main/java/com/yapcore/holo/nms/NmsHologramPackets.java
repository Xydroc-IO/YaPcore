package com.yapcore.holo.nms;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 26.2 packet constructors for TextDisplay holograms (ArmorStand fallback).
 */
public final class NmsHologramPackets {

    private final NmsLookup nms;
    private final EntityIdAllocator ids;
    private final boolean textDisplay;

    private final Object textDisplayType;
    private final Object armorStandType;
    private final Object itemDisplayType;
    private final Constructor<?> addEntityCtor;
    private final Constructor<?> setDataCtor;
    private final Constructor<?> removeCtor;
    private final Constructor<?> teleportCtor;
    private final Constructor<?> vec3Ctor;
    private final Constructor<?> posMoveCtor;
    private final Method dataValueCreate;
    private final Method componentLiteral;
    private final Method paperAsVanilla;
    private final Method asNmsCopy;
    private final Object dataText;
    private final Object dataBillboard;
    private final Object dataStyleFlags;
    private final Object dataSharedFlags;
    private final Object dataCustomName;
    private final Object dataCustomNameVisible;
    private final Object dataNoGravity;
    private final Object dataClientFlags;
    private final Object dataWidth;
    private final Object dataHeight;
    private final Object dataItem;
    private final Object vecZero;

    public NmsHologramPackets(NmsLookup nms, EntityIdAllocator ids, boolean preferTextDisplay) {
        this.nms = nms;
        this.ids = ids;
        Class<?> add = nms.clazz("net.minecraft.network.protocol.game.ClientboundAddEntityPacket");
        Class<?> data = nms.clazz("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket");
        Class<?> remove = nms.clazz("net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket");
        Class<?> teleport = nms.clazz("net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket");
        Class<?> vec3 = nms.clazz("net.minecraft.world.phys.Vec3");
        Class<?> posMove = nms.clazz("net.minecraft.world.entity.PositionMoveRotation");
        Class<?> entityTypeClass = nms.clazz("net.minecraft.world.entity.EntityType");
        Class<?> entityTypes = nms.clazz("net.minecraft.world.entity.EntityTypes");
        Class<?> entity = nms.clazz("net.minecraft.world.entity.Entity");
        Class<?> display = nms.clazz("net.minecraft.world.entity.Display");
        Class<?> textDisplayCl = inner(display, "TextDisplay");
        Class<?> itemDisplayCl = inner(display, "ItemDisplay");
        Class<?> armorStand = nms.clazz("net.minecraft.world.entity.decoration.ArmorStand");
        Class<?> synched = nms.clazz("net.minecraft.network.syncher.SynchedEntityData");
        Class<?> dataValue = inner(synched, "DataValue");
        Class<?> accessor = nms.clazz("net.minecraft.network.syncher.EntityDataAccessor");
        Class<?> componentCl = nms.clazz("net.minecraft.network.chat.Component");

        this.textDisplayType = nms.staticField(entityTypes != null ? entityTypes : entityTypeClass, "TEXT_DISPLAY");
        this.armorStandType = nms.staticField(entityTypes != null ? entityTypes : entityTypeClass, "ARMOR_STAND");
        this.itemDisplayType = nms.staticField(entityTypes != null ? entityTypes : entityTypeClass, "ITEM_DISPLAY");
        this.addEntityCtor = findAddCtor(add, entityTypeClass, vec3);
        this.setDataCtor = data == null ? null : nms.ctor(data, int.class, List.class);
        this.removeCtor = remove == null ? null : nms.ctor(remove, int[].class);
        this.teleportCtor = findTeleportCtor(teleport, posMove);
        this.vec3Ctor = vec3 == null ? null : nms.ctor(vec3, double.class, double.class, double.class);
        this.posMoveCtor = posMove == null || vec3 == null ? null
                : nms.ctor(posMove, vec3, vec3, float.class, float.class);
        this.dataValueCreate = dataValue == null || accessor == null ? null
                : nms.method(dataValue, "create", accessor, Object.class);
        this.componentLiteral = componentCl == null ? null : nms.method(componentCl, "literal", String.class);
        this.paperAsVanilla = paperAdventure();
        this.asNmsCopy = craftCopy();
        this.dataText = nms.staticField(textDisplayCl, "DATA_TEXT_ID");
        this.dataBillboard = nms.staticField(display, "DATA_BILLBOARD_RENDER_CONSTRAINTS_ID");
        this.dataStyleFlags = nms.staticField(textDisplayCl, "DATA_STYLE_FLAGS_ID");
        this.dataSharedFlags = nms.staticField(entity, "DATA_SHARED_FLAGS_ID");
        this.dataCustomName = nms.staticField(entity, "DATA_CUSTOM_NAME");
        this.dataCustomNameVisible = nms.staticField(entity, "DATA_CUSTOM_NAME_VISIBLE");
        this.dataNoGravity = nms.staticField(entity, "DATA_NO_GRAVITY");
        this.dataClientFlags = nms.staticField(armorStand, "DATA_CLIENT_FLAGS");
        this.dataWidth = firstStatic(nms, display, "DATA_WIDTH_ID", "DATA_WIDTH");
        this.dataHeight = firstStatic(nms, display, "DATA_HEIGHT_ID", "DATA_HEIGHT");
        this.dataItem = firstStatic(nms, itemDisplayCl, "DATA_ITEM_STACK_ID", "DATA_ITEM_ID", "DATA_ITEM");
        this.vecZero = vec3 == null ? null : nms.staticField(vec3, "ZERO");
        boolean textOk = preferTextDisplay && textDisplayType != null && dataText != null && addEntityCtor != null
                && setDataCtor != null && dataValueCreate != null;
        this.textDisplay = textOk;
        if (!textOk) {
            nms.warn("YaPHolo using armor_stand packets (text_display constructors unavailable)");
        }
    }

    public boolean ready() {
        return addEntityCtor != null && setDataCtor != null && removeCtor != null && dataValueCreate != null
                && (textDisplay ? textDisplayType != null : armorStandType != null);
    }

    public boolean textDisplay() {
        return textDisplay;
    }

    public int allocateId() {
        return ids.next();
    }

    public Object addEntity(int entityId, UUID uuid, Location loc, Object type) {
        try {
            Object movement = vecZero != null ? vecZero : vec3Ctor.newInstance(0.0, 0.0, 0.0);
            return addEntityCtor.newInstance(
                    entityId, uuid, loc.getX(), loc.getY(), loc.getZ(),
                    loc.getPitch(), loc.getYaw(), type, 0, movement, 0.0);
        } catch (ReflectiveOperationException e) {
            nms.warn("add_entity packet", e);
            return null;
        }
    }

    public Object spawnLine(int entityId, UUID uuid, Location loc, String line) {
        Object type = textDisplay ? textDisplayType : armorStandType;
        return addEntity(entityId, uuid, loc, type);
    }

    public Object spawnItem(int entityId, UUID uuid, Location loc) {
        Object type = itemDisplayType != null ? itemDisplayType : armorStandType;
        return addEntity(entityId, uuid, loc, type);
    }

    public Object itemMetadata(int entityId, org.bukkit.inventory.ItemStack stack) {
        try {
            List<Object> values = new ArrayList<>();
            Object nmsItem = nmsItem(stack);
            if (itemDisplayType != null && dataItem != null && nmsItem != null) {
                addValue(values, dataItem, nmsItem);
                addValue(values, dataBillboard, (byte) 3);
                addValue(values, dataWidth, 0.45f);
                addValue(values, dataHeight, 0.45f);
            } else {
                String name = stack == null ? "" : stack.getType().name();
                Object nmsText = vanilla("&f" + name);
                addValue(values, dataSharedFlags, (byte) (1 << 5));
                addValue(values, dataCustomName, Optional.ofNullable(nmsText));
                addValue(values, dataCustomNameVisible, Boolean.TRUE);
                addValue(values, dataNoGravity, Boolean.TRUE);
                addValue(values, dataClientFlags, (byte) (8 | 16));
            }
            if (values.isEmpty()) {
                return null;
            }
            return setDataCtor.newInstance(entityId, values);
        } catch (ReflectiveOperationException e) {
            nms.warn("item set_entity_data", e);
            return null;
        }
    }

    public Object metadata(int entityId, String line) {
        try {
            List<Object> values = textDisplay ? textValues(line) : armorValues(line);
            if (values.isEmpty()) {
                return null;
            }
            return setDataCtor.newInstance(entityId, values);
        } catch (ReflectiveOperationException e) {
            nms.warn("set_entity_data packet", e);
            return null;
        }
    }

    public Object remove(int... entityIds) {
        try {
            return removeCtor.newInstance((Object) entityIds);
        } catch (ReflectiveOperationException e) {
            nms.warn("remove_entities packet", e);
            return null;
        }
    }

    public Object teleport(int entityId, Location loc) {
        if (teleportCtor == null || posMoveCtor == null) {
            return addEntity(entityId, new UUID(0, entityId), loc,
                    textDisplay ? textDisplayType : armorStandType);
        }
        try {
            Object pos = vec3Ctor.newInstance(loc.getX(), loc.getY(), loc.getZ());
            Object delta = vecZero != null ? vecZero : vec3Ctor.newInstance(0.0, 0.0, 0.0);
            Object change = posMoveCtor.newInstance(pos, delta, loc.getYaw(), loc.getPitch());
            return teleportCtor.newInstance(entityId, change, java.util.Set.of(), false);
        } catch (ReflectiveOperationException e) {
            nms.warn("teleport_entity packet", e);
            return null;
        }
    }

    public Object typeHandle() {
        return textDisplay ? textDisplayType : armorStandType;
    }

    private List<Object> textValues(String line) {
        List<Object> values = new ArrayList<>();
        Object nmsText = vanilla(line);
        addValue(values, dataText, nmsText);
        addValue(values, dataBillboard, (byte) 3);
        addValue(values, dataStyleFlags, (byte) 2);
        addValue(values, dataWidth, 0.7f);
        addValue(values, dataHeight, 0.28f);
        return values;
    }

    private List<Object> armorValues(String line) {
        List<Object> values = new ArrayList<>();
        Object nmsText = vanilla(line);
        addValue(values, dataSharedFlags, (byte) (1 << 5));
        addValue(values, dataCustomName, Optional.ofNullable(nmsText));
        addValue(values, dataCustomNameVisible, Boolean.TRUE);
        addValue(values, dataNoGravity, Boolean.TRUE);
        addValue(values, dataClientFlags, (byte) (8 | 16));
        return values;
    }

    private void addValue(List<Object> values, Object accessor, Object value) {
        if (accessor == null || value == null || dataValueCreate == null) {
            return;
        }
        try {
            values.add(dataValueCreate.invoke(null, accessor, value));
        } catch (ReflectiveOperationException e) {
            nms.warn("DataValue.create", e);
        }
    }

    private Object vanilla(String line) {
        Component adventure = LegacyComponentSerializer.legacyAmpersand().deserialize(line == null ? "" : line);
        if (paperAsVanilla != null) {
            try {
                Object converted = paperAsVanilla.invoke(null, adventure);
                if (converted != null) {
                    return converted;
                }
            } catch (ReflectiveOperationException ignored) {
                // literal fallback
            }
        }
        if (componentLiteral != null) {
            String plain = PlainTextComponentSerializer.plainText().serialize(adventure);
            try {
                return componentLiteral.invoke(null, plain);
            } catch (ReflectiveOperationException e) {
                nms.warn("Component.literal", e);
            }
        }
        return line;
    }

    private Object nmsItem(org.bukkit.inventory.ItemStack stack) {
        if (stack == null || asNmsCopy == null) {
            return null;
        }
        try {
            return asNmsCopy.invoke(null, stack);
        } catch (ReflectiveOperationException e) {
            nms.warn("CraftItemStack.asNMSCopy", e);
            return null;
        }
    }

    private Method craftCopy() {
        String[] names = {
                "org.bukkit.craftbukkit.inventory.CraftItemStack",
                "org.bukkit.craftbukkit.inventory.CraftItemStack"
        };
        for (String name : names) {
            Class<?> type = nms.clazz(name);
            Method method = nms.method(type, "asNMSCopy", org.bukkit.inventory.ItemStack.class);
            if (method != null) {
                return method;
            }
        }
        return null;
    }

    private static Object firstStatic(NmsLookup nms, Class<?> type, String... names) {
        if (type == null) {
            return null;
        }
        for (String name : names) {
            Object value = nms.staticField(type, name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Method paperAdventure() {
        try {
            Class<?> paper = Class.forName("io.papermc.paper.adventure.PaperAdventure", true, nms.loader());
            return nms.method(paper, "asVanilla", Component.class);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private Constructor<?> findAddCtor(Class<?> add, Class<?> entityType, Class<?> vec3) {
        if (add == null || entityType == null || vec3 == null) {
            return null;
        }
        Constructor<?> ctor = nms.ctor(add, int.class, UUID.class, double.class, double.class, double.class,
                float.class, float.class, entityType, int.class, vec3, double.class);
        if (ctor != null) {
            return ctor;
        }
        for (Constructor<?> candidate : add.getDeclaredConstructors()) {
            if (candidate.getParameterCount() == 11) {
                candidate.setAccessible(true);
                return candidate;
            }
        }
        return null;
    }

    private Constructor<?> findTeleportCtor(Class<?> teleport, Class<?> posMove) {
        if (teleport == null || posMove == null) {
            return null;
        }
        Constructor<?> ctor = nms.ctor(teleport, int.class, posMove, java.util.Set.class, boolean.class);
        if (ctor != null) {
            return ctor;
        }
        for (Constructor<?> candidate : teleport.getDeclaredConstructors()) {
            if (candidate.getParameterCount() == 4) {
                candidate.setAccessible(true);
                return candidate;
            }
        }
        return null;
    }

    private static Class<?> inner(Class<?> outer, String simple) {
        if (outer == null) {
            return null;
        }
        for (Class<?> nested : outer.getDeclaredClasses()) {
            if (simple.equals(nested.getSimpleName())) {
                return nested;
            }
        }
        return null;
    }
}
