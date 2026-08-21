package com.yapcore.protocol.java;

import com.yapcore.protocol.java.codec.NbtWriter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Configuration-phase registry sync — vanilla-complete known-packs path.
 * Entry lists and tags come from {@link VanillaProtocolData} dumps for the
 * client's negotiated {@code minecraft:core} version.
 */
public final class RegistryBootstrap {

    private static final Logger LOG = Logger.getLogger("YaPcore.JE.Registry");

    /** Network registry id → key inside registryEntries.json */
    private static final String[][] REGISTRY_KEYS = {
            {"minecraft:dimension_type", "DIMENSION_TYPES"},
            {"minecraft:worldgen/biome", "BIOMES"},
            {"minecraft:damage_type", "DAMAGE_TYPES"},
            {"minecraft:chat_type", "CHAT_TYPES"},
            {"minecraft:world_clock", "WORLD_CLOCKS"},
            {"minecraft:timeline", "TIMELINES"},
            {"minecraft:painting_variant", "PAINTING_VARIANTS"},
            {"minecraft:cat_variant", "CAT_VARIANTS"},
            {"minecraft:cat_sound_variant", "CAT_SOUND_VARIANTS"},
            {"minecraft:chicken_variant", "CHICKEN_VARIANTS"},
            {"minecraft:chicken_sound_variant", "CHICKEN_SOUND_VARIANTS"},
            {"minecraft:cow_variant", "COW_VARIANTS"},
            {"minecraft:cow_sound_variant", "COW_SOUND_VARIANTS"},
            {"minecraft:frog_variant", "FROG_VARIANTS"},
            {"minecraft:pig_variant", "PIG_VARIANTS"},
            {"minecraft:pig_sound_variant", "PIG_SOUND_VARIANTS"},
            {"minecraft:wolf_variant", "WOLF_VARIANTS"},
            {"minecraft:wolf_sound_variant", "WOLF_SOUND_VARIANTS"},
            {"minecraft:zombie_nautilus_variant", "ZOMBIE_NAUTILUS_VARIANTS"},
            {"minecraft:sulfur_cube_archetype", "SULFUR_CUBE_ARCHETYPES"},
            {"minecraft:instrument", "INSTRUMENTS"},
            {"minecraft:jukebox_song", "JUKEBOX_SONGS"},
            {"minecraft:trim_material", "TRIM_MATERIALS"},
            {"minecraft:trim_pattern", "TRIM_PATTERNS"},
            {"minecraft:banner_pattern", "BANNER_PATTERNS"},
            {"minecraft:enchantment", "ENCHANTMENTS"},
            {"minecraft:dialog", "DIALOGS"},
    };

    private RegistryBootstrap() {
    }

    public static void sendVanillaComplete(Channel ch, boolean knownPacksMatched, String dumpVersion) {
        if (knownPacksMatched) {
            sendFullKnownPacks(ch, dumpVersion);
        } else {
            LOG.warning("Known packs did not match — sending network-NBT fallback (limited)");
            sendNbtFallback(ch);
        }
        NetworkTagBootstrap.sendFullVanilla(ch, dumpVersion);
    }

    private static void sendFullKnownPacks(Channel ch, String dumpVersion) {
        Map<String, String[]> catalogs = VanillaProtocolData.registriesFor(dumpVersion);
        int n = 0;
        int entries = 0;
        for (String[] pair : REGISTRY_KEYS) {
            String[] ids = catalogs.get(pair[1]);
            if (ids == null || ids.length == 0) {
                continue; // registry absent in this dump (older MC)
            }
            PacketFactory.send(ch, PacketFactory.registryData(pair[0], ids, null));
            n++;
            entries += ids.length;
        }
        LOG.info("Sent " + n + " Registry Data packets / " + entries
                + " entries (vanilla " + dumpVersion + " complete known-pack lists)");
    }

    private static void sendNbtFallback(Channel ch) {
        PacketFactory.send(ch, PacketFactory.registryData(
                "minecraft:dimension_type",
                new String[]{"minecraft:overworld"},
                new ByteBuf[]{buildOverworldDimensionType()}));
        PacketFactory.send(ch, PacketFactory.registryData(
                "minecraft:worldgen/biome",
                new String[]{"minecraft:plains"},
                new ByteBuf[]{buildPlainsBiome()}));
        ByteBuf d1 = buildGenericDamage("generic");
        ByteBuf d2 = buildGenericDamage("genericKill");
        ByteBuf d3 = buildGenericDamage("outOfWorld");
        PacketFactory.send(ch, PacketFactory.registryData(
                "minecraft:damage_type",
                new String[]{"minecraft:generic", "minecraft:generic_kill", "minecraft:out_of_world"},
                new ByteBuf[]{d1, d2, d3}));
    }

    private static ByteBuf buildGenericDamage(String messageId) {
        ByteBuf out = Unpooled.buffer();
        out.writeByte(10);
        NbtWriter.writeString(out, "message_id", messageId);
        NbtWriter.writeString(out, "scaling", "when_caused_by_living_non_player");
        NbtWriter.writeFloat(out, "exhaustion", 0.0f);
        out.writeByte(0);
        return out;
    }

    public static ByteBuf buildOverworldDimensionType() {
        ByteBuf out = Unpooled.buffer();
        out.writeByte(10);
        NbtWriter.writeByte(out, "piglin_safe", 0);
        NbtWriter.writeByte(out, "natural", 1);
        NbtWriter.writeFloat(out, "ambient_light", 0f);
        NbtWriter.writeInt(out, "monster_spawn_block_light_limit", 0);
        NbtWriter.writeString(out, "infiniburn", "#minecraft:infiniburn_overworld");
        NbtWriter.writeByte(out, "respawn_anchor_works", 0);
        NbtWriter.writeByte(out, "has_raids", 1);
        NbtWriter.writeInt(out, "min_y", -64);
        NbtWriter.writeInt(out, "height", 384);
        NbtWriter.writeInt(out, "logical_height", 384);
        NbtWriter.writeDouble(out, "coordinate_scale", 1.0);
        NbtWriter.writeByte(out, "ultrawarm", 0);
        NbtWriter.writeByte(out, "has_ceiling", 0);
        NbtWriter.writeByte(out, "has_skylight", 1);
        NbtWriter.writeByte(out, "bed_works", 1);
        NbtWriter.writeString(out, "effects", "minecraft:overworld");
        NbtWriter.writeCompound(out, "monster_spawn_light_level", () -> {
            NbtWriter.writeString(out, "type", "minecraft:constant");
            NbtWriter.writeInt(out, "value", 7);
        });
        out.writeByte(0);
        return out;
    }

    public static ByteBuf buildPlainsBiome() {
        ByteBuf out = Unpooled.buffer();
        out.writeByte(10);
        NbtWriter.writeByte(out, "has_precipitation", 1);
        NbtWriter.writeFloat(out, "temperature", 0.8f);
        NbtWriter.writeFloat(out, "downfall", 0.4f);
        NbtWriter.writeCompound(out, "effects", () -> {
            NbtWriter.writeInt(out, "sky_color", 7907327);
            NbtWriter.writeInt(out, "water_fog_color", 329011);
            NbtWriter.writeInt(out, "fog_color", 12638463);
            NbtWriter.writeInt(out, "water_color", 4159204);
        });
        out.writeByte(0);
        return out;
    }
}
