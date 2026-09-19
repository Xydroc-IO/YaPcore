package com.yapcore.lib.packet;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/** CraftBukkit / NMS converters used by {@link StructureModifier}. */
public final class PacketConverters {

    private static Method asBukkitCopy;
    private static Method asNmsCopy;
    private static Method paperAsAdventure;
    private static Method paperAsVanilla;
    private static Method componentLiteral;
    private static Class<?> nmsComponentType;
    private static Class<?> nmsTagType;
    private static Method tagParse;
    private static Constructor<?> blockPosCtor;
    private static Method blockPosX;
    private static Method blockPosY;
    private static Method blockPosZ;
    private static Constructor<?> profileCtor;
    private static Method profileId;
    private static Method profileName;
    private static boolean loaded;

    private PacketConverters() {
    }

    public static EquivalentConverter<Integer> integers() {
        return identity(Integer.class);
    }

    public static EquivalentConverter<String> strings() {
        return identity(String.class);
    }

    public static EquivalentConverter<UUID> uuids() {
        return identity(UUID.class);
    }

    public static EquivalentConverter<ItemStack> items() {
        ensure();
        return new EquivalentConverter<>() {
            @Override
            public Object toNms(ItemStack value) {
                if (value == null || asNmsCopy == null) {
                    return null;
                }
                try {
                    return asNmsCopy.invoke(null, value);
                } catch (ReflectiveOperationException e) {
                    return null;
                }
            }

            @Override
            public ItemStack fromNms(Object nms) {
                if (nms == null || asBukkitCopy == null) {
                    return null;
                }
                try {
                    Object copy = asBukkitCopy.invoke(null, nms);
                    return copy instanceof ItemStack stack ? stack : null;
                } catch (ReflectiveOperationException e) {
                    return null;
                }
            }

            @Override
            public Class<?> nmsHint() {
                return asNmsCopy == null ? ItemStack.class : asNmsCopy.getReturnType();
            }
        };
    }

    public static EquivalentConverter<Component> chat() {
        ensure();
        return new EquivalentConverter<>() {
            @Override
            public Object toNms(Component value) {
                Component adventure = value == null ? Component.empty() : value;
                if (paperAsVanilla != null) {
                    try {
                        Object converted = paperAsVanilla.invoke(null, adventure);
                        if (converted != null) {
                            return converted;
                        }
                    } catch (ReflectiveOperationException ignored) {
                        // literal
                    }
                }
                if (componentLiteral != null) {
                    try {
                        return componentLiteral.invoke(null,
                                PlainTextComponentSerializer.plainText().serialize(adventure));
                    } catch (ReflectiveOperationException ignored) {
                        return adventure;
                    }
                }
                return adventure;
            }

            @Override
            public Component fromNms(Object nms) {
                if (nms == null) {
                    return Component.empty();
                }
                if (nms instanceof Component component) {
                    return component;
                }
                if (nms instanceof Optional<?> opt) {
                    return fromNms(opt.orElse(null));
                }
                if (paperAsAdventure != null) {
                    try {
                        Object converted = paperAsAdventure.invoke(null, nms);
                        if (converted instanceof Component component) {
                            return component;
                        }
                    } catch (ReflectiveOperationException ignored) {
                        // string
                    }
                }
                return LegacyComponentSerializer.legacySection().deserialize(String.valueOf(nms));
            }

            @Override
            public Class<?> nmsHint() {
                return nmsComponentType != null ? nmsComponentType : Component.class;
            }
        };
    }

    public static EquivalentConverter<PacketBlockPos> blockPositions() {
        ensure();
        return new EquivalentConverter<>() {
            @Override
            public Object toNms(PacketBlockPos value) {
                if (value == null || blockPosCtor == null) {
                    return value;
                }
                try {
                    return blockPosCtor.newInstance(value.x(), value.y(), value.z());
                } catch (ReflectiveOperationException e) {
                    return value;
                }
            }

            @Override
            public PacketBlockPos fromNms(Object nms) {
                if (nms instanceof PacketBlockPos pos) {
                    return pos;
                }
                if (nms == null || blockPosX == null) {
                    return null;
                }
                try {
                    return new PacketBlockPos(
                            ((Number) blockPosX.invoke(nms)).intValue(),
                            ((Number) blockPosY.invoke(nms)).intValue(),
                            ((Number) blockPosZ.invoke(nms)).intValue());
                } catch (ReflectiveOperationException e) {
                    return null;
                }
            }

            @Override
            public Class<?> nmsHint() {
                return blockPosCtor == null ? PacketBlockPos.class : blockPosCtor.getDeclaringClass();
            }
        };
    }

    public static EquivalentConverter<PacketProfile> profiles() {
        ensure();
        return new EquivalentConverter<>() {
            @Override
            public Object toNms(PacketProfile value) {
                if (value == null || profileCtor == null) {
                    return value;
                }
                try {
                    return profileCtor.newInstance(value.uuid(), value.name());
                } catch (ReflectiveOperationException e) {
                    return value;
                }
            }

            @Override
            public PacketProfile fromNms(Object nms) {
                if (nms instanceof PacketProfile profile) {
                    return profile;
                }
                if (nms == null || profileId == null) {
                    return null;
                }
                try {
                    UUID id = (UUID) profileId.invoke(nms);
                    String name = String.valueOf(profileName.invoke(nms));
                    return new PacketProfile(id, name);
                } catch (ReflectiveOperationException e) {
                    return null;
                }
            }

            @Override
            public Class<?> nmsHint() {
                return profileCtor == null ? PacketProfile.class : profileCtor.getDeclaringClass();
            }
        };
    }

    public static EquivalentConverter<PacketNbt> nbt() {
        ensure();
        return new EquivalentConverter<>() {
            @Override
            public Object toNms(PacketNbt value) {
                if (value == null) {
                    return null;
                }
                Object handle = value.handle();
                if (handle instanceof String snbt && tagParse != null) {
                    try {
                        return tagParse.invoke(null, snbt);
                    } catch (ReflectiveOperationException e) {
                        return handle;
                    }
                }
                return handle;
            }

            @Override
            public PacketNbt fromNms(Object nms) {
                return PacketNbt.wrap(nms);
            }

            @Override
            public Class<?> nmsHint() {
                return nmsTagType != null ? nmsTagType : PacketNbt.class;
            }
        };
    }

    private static <T> EquivalentConverter<T> identity(Class<T> type) {
        return new EquivalentConverter<>() {
            @Override
            public Object toNms(T value) {
                return value;
            }

            @Override
            @SuppressWarnings("unchecked")
            public T fromNms(Object nms) {
                return (T) nms;
            }

            @Override
            public Class<?> nmsHint() {
                return type;
            }
        };
    }

    private static synchronized void ensure() {
        if (loaded) {
            return;
        }
        loaded = true;
        ClassLoader loader = loader();
        Class<?> craft = clazz(loader, "org.bukkit.craftbukkit.inventory.CraftItemStack");
        if (craft != null) {
            asNmsCopy = method(craft, "asNMSCopy", ItemStack.class);
            asBukkitCopy = method(craft, "asBukkitCopy", Object.class);
            if (asBukkitCopy == null && asNmsCopy != null) {
                asBukkitCopy = method(craft, "asBukkitCopy", asNmsCopy.getReturnType());
            }
        }
        Class<?> paper = clazz(loader, "io.papermc.paper.adventure.PaperAdventure");
        if (paper != null) {
            paperAsVanilla = method(paper, "asVanilla", Component.class);
            paperAsAdventure = method(paper, "asAdventure", Object.class);
            if (paperAsAdventure == null && paperAsVanilla != null) {
                paperAsAdventure = method(paper, "asAdventure", paperAsVanilla.getReturnType());
            }
        }
        Class<?> nmsComponent = clazz(loader, "net.minecraft.network.chat.Component");
        nmsComponentType = nmsComponent;
        if (nmsComponent != null) {
            componentLiteral = method(nmsComponent, "literal", String.class);
        }
        Class<?> blockPos = clazz(loader, "net.minecraft.core.BlockPos");
        if (blockPos != null) {
            blockPosCtor = ctor(blockPos, int.class, int.class, int.class);
            blockPosX = method(blockPos, "getX");
            blockPosY = method(blockPos, "getY");
            blockPosZ = method(blockPos, "getZ");
        }
        Class<?> profile = clazz(loader, "com.mojang.authlib.GameProfile");
        if (profile != null) {
            profileCtor = ctor(profile, UUID.class, String.class);
            profileId = method(profile, "getId");
            profileName = method(profile, "getName");
        }
        nmsTagType = clazz(loader, "net.minecraft.nbt.Tag");
        if (nmsTagType == null) {
            nmsTagType = clazz(loader, "net.minecraft.nbt.CompoundTag");
        }
        Class<?> parser = clazz(loader, "net.minecraft.nbt.TagParser");
        if (parser != null) {
            tagParse = method(parser, "parseCompoundFully", String.class);
            if (tagParse == null) {
                tagParse = method(parser, "parseCompound", String.class);
            }
        }
    }

    private static ClassLoader loader() {
        try {
            return org.bukkit.Bukkit.getServer().getClass().getClassLoader();
        } catch (Throwable t) {
            return PacketConverters.class.getClassLoader();
        }
    }

    private static Class<?> clazz(ClassLoader loader, String name) {
        try {
            return Class.forName(name, true, loader);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static Method method(Class<?> type, String name, Class<?>... params) {
        try {
            Method method = type.getMethod(name, params);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Constructor<?> ctor(Class<?> type, Class<?>... params) {
        try {
            Constructor<?> ctor = type.getConstructor(params);
            ctor.setAccessible(true);
            return ctor;
        } catch (NoSuchMethodException e) {
            try {
                Constructor<?> ctor = type.getDeclaredConstructor(params);
                ctor.setAccessible(true);
                return ctor;
            } catch (NoSuchMethodException ex) {
                return null;
            }
        }
    }
}
