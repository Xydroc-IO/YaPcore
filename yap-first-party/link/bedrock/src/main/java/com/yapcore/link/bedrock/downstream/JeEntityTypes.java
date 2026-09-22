package com.yapcore.link.bedrock.downstream;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Folia/JE entity-type registry ids → Bedrock AddEntity identifiers.
 *
 * <p>Built from Folia {@code EntityTypes} registration order (includes {@code sulfur_cube},
 * which shifts every id after tadpole vs stock minecraft-data 26.2). Types with no Bedrock
 * actor (displays/markers) return null so callers skip AddEntity instead of spawning an
 * armor stand.
 */
public final class JeEntityTypes {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    /** JE ids with no sensible Bedrock actor (block/item/text display, interaction, marker). */
    private static final boolean[] SKIP = new boolean[160];

    private static final String[] BY_ID = new String[160];

    static {
        // Folia network ids: block_display=15, interaction=69, item_display=72, marker=84, text_display=132
        for (int id : new int[]{15, 69, 72, 84, 132}) {
            if (id >= 0 && id < SKIP.length) {
                SKIP[id] = true;
            }
        }
        Map<Integer, String> m = new HashMap<>();
                m.put(0, "minecraft:boat"); // acacia_boat
                m.put(1, "minecraft:chest_boat"); // acacia_chest_boat
                m.put(2, "minecraft:allay");
                m.put(3, "minecraft:area_effect_cloud");
                m.put(4, "minecraft:armadillo");
                m.put(5, "minecraft:armor_stand");
                m.put(6, "minecraft:arrow");
                m.put(7, "minecraft:axolotl");
                m.put(8, "minecraft:chest_boat"); // bamboo_chest_raft
                m.put(9, "minecraft:boat"); // bamboo_raft
                m.put(10, "minecraft:bat");
                m.put(11, "minecraft:bee");
                m.put(12, "minecraft:boat"); // birch_boat
                m.put(13, "minecraft:chest_boat"); // birch_chest_boat
                m.put(14, "minecraft:blaze");
                m.put(16, "minecraft:bogged");
                m.put(17, "minecraft:breeze");
                m.put(18, "minecraft:breeze_wind_charge_projectile");
                m.put(19, "minecraft:camel");
                m.put(20, "minecraft:camel_husk");
                m.put(21, "minecraft:cat");
                m.put(22, "minecraft:cave_spider");
                m.put(23, "minecraft:boat"); // cherry_boat
                m.put(24, "minecraft:chest_boat"); // cherry_chest_boat
                m.put(25, "minecraft:chest_minecart");
                m.put(26, "minecraft:chicken");
                m.put(27, "minecraft:cod");
                m.put(28, "minecraft:copper_golem");
                m.put(29, "minecraft:command_block_minecart");
                m.put(30, "minecraft:cow");
                m.put(31, "minecraft:creaking");
                m.put(32, "minecraft:creeper");
                m.put(33, "minecraft:boat"); // dark_oak_boat
                m.put(34, "minecraft:chest_boat"); // dark_oak_chest_boat
                m.put(35, "minecraft:dolphin");
                m.put(36, "minecraft:donkey");
                m.put(37, "minecraft:dragon_fireball");
                m.put(38, "minecraft:drowned");
                m.put(39, "minecraft:egg");
                m.put(40, "minecraft:elder_guardian");
                m.put(41, "minecraft:enderman");
                m.put(42, "minecraft:endermite");
                m.put(43, "minecraft:ender_dragon");
                m.put(44, "minecraft:ender_pearl");
                m.put(45, "minecraft:ender_crystal");
                m.put(46, "minecraft:evocation_illager");
                m.put(47, "minecraft:evocation_fang");
                m.put(48, "minecraft:xp_bottle");
                m.put(49, "minecraft:xp_orb");
                m.put(50, "minecraft:eye_of_ender_signal");
                m.put(51, "minecraft:falling_block");
                m.put(52, "minecraft:fireball");
                m.put(53, "minecraft:fireworks_rocket");
                m.put(54, "minecraft:fox");
                m.put(55, "minecraft:frog");
                m.put(56, "minecraft:furnace_minecart");
                m.put(57, "minecraft:ghast");
                m.put(58, "minecraft:happy_ghast");
                m.put(59, "minecraft:zombie"); // giant
                m.put(60, "minecraft:item_frame"); // glow_item_frame
                m.put(61, "minecraft:glow_squid");
                m.put(62, "minecraft:goat");
                m.put(63, "minecraft:guardian");
                m.put(64, "minecraft:hoglin");
                m.put(65, "minecraft:hopper_minecart");
                m.put(66, "minecraft:horse");
                m.put(67, "minecraft:husk");
                m.put(68, "minecraft:evocation_illager"); // illusioner
                m.put(70, "minecraft:iron_golem");
                m.put(71, "minecraft:item");
                m.put(73, "minecraft:item_frame");
                m.put(74, "minecraft:boat"); // jungle_boat
                m.put(75, "minecraft:chest_boat"); // jungle_chest_boat
                m.put(76, "minecraft:leash_knot");
                m.put(77, "minecraft:lightning_bolt");
                m.put(78, "minecraft:llama");
                m.put(79, "minecraft:llama_spit");
                m.put(80, "minecraft:magma_cube");
                m.put(81, "minecraft:boat"); // mangrove_boat
                m.put(82, "minecraft:chest_boat"); // mangrove_chest_boat
                // Bedrock npc actor, not AddPlayer — AddPlayer without PlayerList is invisible.
                m.put(83, "minecraft:npc"); // mannequin
                m.put(85, "minecraft:minecart");
                m.put(86, "minecraft:mooshroom");
                m.put(87, "minecraft:mule");
                m.put(88, "minecraft:nautilus");
                m.put(89, "minecraft:boat"); // oak_boat
                m.put(90, "minecraft:chest_boat"); // oak_chest_boat
                m.put(91, "minecraft:ocelot");
                m.put(92, "minecraft:ominous_item_spawner");
                m.put(93, "minecraft:painting");
                m.put(94, "minecraft:boat"); // pale_oak_boat
                m.put(95, "minecraft:chest_boat"); // pale_oak_chest_boat
                m.put(96, "minecraft:panda");
                m.put(97, "minecraft:parched");
                m.put(98, "minecraft:parrot");
                m.put(99, "minecraft:phantom");
                m.put(100, "minecraft:pig");
                m.put(101, "minecraft:piglin");
                m.put(102, "minecraft:piglin_brute");
                m.put(103, "minecraft:pillager");
                m.put(104, "minecraft:polar_bear");
                m.put(105, "minecraft:splash_potion");
                m.put(106, "minecraft:lingering_potion");
                m.put(107, "minecraft:pufferfish");
                m.put(108, "minecraft:rabbit");
                m.put(109, "minecraft:ravager");
                m.put(110, "minecraft:salmon");
                m.put(111, "minecraft:sheep");
                m.put(112, "minecraft:shulker");
                m.put(113, "minecraft:shulker_bullet");
                m.put(114, "minecraft:silverfish");
                m.put(115, "minecraft:skeleton");
                m.put(116, "minecraft:skeleton_horse");
                m.put(117, "minecraft:slime");
                m.put(118, "minecraft:small_fireball");
                m.put(119, "minecraft:sniffer");
                m.put(120, "minecraft:snowball");
                m.put(121, "minecraft:snow_golem");
                m.put(122, "minecraft:spawner_minecart");
                m.put(123, "minecraft:arrow"); // spectral_arrow
                m.put(124, "minecraft:spider");
                m.put(125, "minecraft:boat"); // spruce_boat
                m.put(126, "minecraft:chest_boat"); // spruce_chest_boat
                m.put(127, "minecraft:squid");
                m.put(128, "minecraft:stray");
                m.put(129, "minecraft:strider");
                m.put(130, "minecraft:magma_cube"); // sulfur_cube (Folia-only; shifts subsequent ids)
                m.put(131, "minecraft:tadpole");
                m.put(133, "minecraft:tnt");
                m.put(134, "minecraft:tnt_minecart");
                m.put(135, "minecraft:trader_llama");
                m.put(136, "minecraft:thrown_trident");
                m.put(137, "minecraft:tropicalfish");
                m.put(138, "minecraft:turtle");
                m.put(139, "minecraft:vex");
                m.put(140, "minecraft:villager");
                m.put(141, "minecraft:vindicator");
                m.put(142, "minecraft:wandering_trader");
                m.put(143, "minecraft:warden");
                m.put(144, "minecraft:wind_charge_projectile");
                m.put(145, "minecraft:witch");
                m.put(146, "minecraft:wither");
                m.put(147, "minecraft:wither_skeleton");
                m.put(148, "minecraft:wither_skull");
                m.put(149, "minecraft:wolf");
                m.put(150, "minecraft:zoglin");
                m.put(151, "minecraft:zombie");
                m.put(152, "minecraft:zombie_horse");
                m.put(153, "minecraft:zombie_nautilus");
                m.put(154, "minecraft:zombie_villager");
                m.put(155, "minecraft:zombie_piglin");
                m.put(156, "minecraft:player");
                m.put(157, "minecraft:fishing_hook");
        for (Map.Entry<Integer, String> e : m.entrySet()) {
            int id = e.getKey();
            if (id >= 0 && id < BY_ID.length) {
                BY_ID[id] = e.getValue();
            }
        }
    }

    private JeEntityTypes() {
    }

    /**
     * @return Bedrock identifier such as {@code minecraft:cow}, or {@code null} to skip spawn
     */
    public static String bedrockIdentifier(int jeTypeId) {
        if (jeTypeId >= 0 && jeTypeId < SKIP.length && SKIP[jeTypeId]) {
            return null;
        }
        if (jeTypeId >= 0 && jeTypeId < BY_ID.length && BY_ID[jeTypeId] != null) {
            return BY_ID[jeTypeId];
        }
        LOG.info("JE entity type id=" + jeTypeId + " unmapped — skipping Bedrock AddEntity");
        return null;
    }
}
