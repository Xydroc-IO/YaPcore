package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.downstream.JeItemRegistry;
import com.yapcore.link.bedrock.downstream.JeItemStackCodec;
import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.session.LinkBedrockSession;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.InventoryContentPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventorySlotPacket;

/**
 * JE container_set_content / container_set_slot → Bedrock inventory with real item ids.
 *
 * <p>JE player inv (window 0, 46 slots) maps to Bedrock inventory(36) + armor(4) + offhand(1).
 *
 * <p>Mapping is always JE item id → {@link JeItemRegistry} name → Bedrock
 * {@link ItemDefinition} by <b>identifier string</b> (never JE numeric id as Bedrock network id).
 * Block items also set {@link ItemData.Builder#blockDefinition} from the hashed block palette
 * so place/UpdateBlock matches StartGame {@code blockNetworkIdsHashed}.
 */
public final class JavaInventoryTranslator {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");
    private static final AtomicBoolean FORENSIC_ITEM_MAP_LOGGED = new AtomicBoolean(false);

    private JavaInventoryTranslator() {
    }

    public static void onContainerSetContent(LinkBedrockSession session, int windowId,
                                             JeItemStackCodec.Stack[] stacks) {
        if (session == null || !session.isSentSpawnPacket() || stacks == null) {
            return;
        }
        if (windowId == 0) {
            applyPlayerInventory(session, stacks);
            pushPlayerInventory(session);
            session.rememberJeInventorySlots(stacks.length);
            logForensicItemMapsOnce(session);
            BedrockJoinProbe.noteEvent(session.guid(),
                    "java_container_content→be win=0 slots=" + stacks.length
                            + " nonempty=" + countNonEmpty(stacks));
            return;
        }
        // Other windows: push as-is into matching container id (chests etc.).
        int size = Math.min(stacks.length, 128);
        List<ItemData> contents = new ArrayList<>(size);
        Map<String, ItemDefinition> byName = itemLookup(session);
        session.clearOpenContainerSlots();
        for (int i = 0; i < size; i++) {
            ItemData item = toBedrock(session, byName, stacks[i]);
            contents.add(item);
            session.setContainerSlot(i, item);
        }
        InventoryContentPacket packet = new InventoryContentPacket();
        packet.setContainerId(Math.max(0, windowId));
        packet.setContents(contents);
        session.sendUpstreamPacket(packet);
        BedrockJoinProbe.noteEvent(session.guid(),
                "java_container_content→be win=" + windowId + " slots=" + size);
    }

    public static void onContainerSetSlot(LinkBedrockSession session, int windowId, int slot,
                                          JeItemStackCodec.Stack stack) {
        if (session == null || !session.isSentSpawnPacket()) {
            return;
        }
        JeItemStackCodec.Stack s = stack != null ? stack : JeItemStackCodec.Stack.AIR;
        if (windowId == 0) {
            applyPlayerSlot(session, slot, s);
            // Push the affected Bedrock window slot.
            Map<String, ItemDefinition> byName = itemLookup(session);
            ItemData item = toBedrock(session, byName, s);
            if (slot >= 36 && slot <= 44) {
                sendSlot(session, ContainerId.INVENTORY, slot - 36, item);
            } else if (slot >= 9 && slot <= 35) {
                sendSlot(session, ContainerId.INVENTORY, slot, item);
            } else if (slot >= 5 && slot <= 8) {
                sendSlot(session, ContainerId.ARMOR, slot - 5, item);
            } else if (slot == 45) {
                sendSlot(session, ContainerId.OFFHAND, 0, item);
            }
            String jeName = s.isEmpty() ? "air" : JeItemRegistry.name(s.itemId());
            int beRt = item != null && item.getDefinition() != null ? item.getDefinition().getRuntimeId() : 0;
            BedrockJoinProbe.noteEvent(session.guid(),
                    "java_container_slot→be win=0 slot=" + slot
                            + " item=" + jeName + " beRt=" + beRt);
            return;
        }
        Map<String, ItemDefinition> byName = itemLookup(session);
        ItemData item = toBedrock(session, byName, s);
        session.setContainerSlot(Math.max(0, slot), item);
        sendSlot(session, Math.max(0, windowId), Math.max(0, slot), item);
        BedrockJoinProbe.noteEvent(session.guid(),
                "java_container_slot→be win=" + windowId + " slot=" + slot);
    }

    /** Push last known player inventory snapshot to Bedrock (open / refresh). */
    public static void pushPlayerInventory(LinkBedrockSession session) {
        if (session == null || !session.isSentSpawnPacket()) {
            return;
        }
        ItemData[] inv = session.bedrockInventorySlots();
        ItemData[] armor = session.bedrockArmorSlots();
        ItemData offhand = session.bedrockOffhand();
        sendContent(session, ContainerId.INVENTORY, inv);
        sendContent(session, ContainerId.ARMOR, armor);
        sendContent(session, ContainerId.OFFHAND, new ItemData[]{offhand != null ? offhand : ItemData.AIR});
    }

    private static void applyPlayerInventory(LinkBedrockSession session, JeItemStackCodec.Stack[] stacks) {
        Map<String, ItemDefinition> byName = itemLookup(session);
        ItemData[] inv = new ItemData[36];
        ItemData[] armor = new ItemData[4];
        for (int i = 0; i < 36; i++) {
            inv[i] = ItemData.AIR;
        }
        for (int i = 0; i < 4; i++) {
            armor[i] = ItemData.AIR;
        }
        ItemData offhand = ItemData.AIR;
        for (int je = 0; je < stacks.length; je++) {
            ItemData item = toBedrock(session, byName, stacks[je]);
            if (je >= 36 && je <= 44) {
                inv[je - 36] = item; // hotbar
            } else if (je >= 9 && je <= 35) {
                inv[je] = item; // main
            } else if (je >= 5 && je <= 8) {
                armor[je - 5] = item;
            } else if (je == 45) {
                offhand = item;
            }
        }
        session.storeBedrockInventory(inv, armor, offhand);
    }

    private static void applyPlayerSlot(LinkBedrockSession session, int jeSlot, JeItemStackCodec.Stack stack) {
        Map<String, ItemDefinition> byName = itemLookup(session);
        ItemData item = toBedrock(session, byName, stack);
        ItemData[] inv = session.bedrockInventorySlots();
        ItemData[] armor = session.bedrockArmorSlots();
        ItemData offhand = session.bedrockOffhand();
        if (inv == null || inv.length != 36) {
            inv = airArray(36);
        } else {
            inv = inv.clone();
        }
        if (armor == null || armor.length != 4) {
            armor = airArray(4);
        } else {
            armor = armor.clone();
        }
        if (jeSlot >= 36 && jeSlot <= 44) {
            inv[jeSlot - 36] = item;
        } else if (jeSlot >= 9 && jeSlot <= 35) {
            inv[jeSlot] = item;
        } else if (jeSlot >= 5 && jeSlot <= 8) {
            armor[jeSlot - 5] = item;
        } else if (jeSlot == 45) {
            offhand = item;
        }
        session.storeBedrockInventory(inv, armor, offhand != null ? offhand : ItemData.AIR);
    }

    private static ItemData toBedrock(LinkBedrockSession session,
                                     Map<String, ItemDefinition> byName,
                                     JeItemStackCodec.Stack stack) {
        return jeToBedrock(session, byName, stack);
    }

    /** Public JE→BE item remap for equipment / inventory bridges. */
    public static ItemData jeToBedrock(LinkBedrockSession session, JeItemStackCodec.Stack stack) {
        return jeToBedrock(session, itemLookup(session), stack);
    }

    static ItemData jeToBedrock(LinkBedrockSession session,
                                Map<String, ItemDefinition> byName,
                                JeItemStackCodec.Stack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemData.AIR;
        }
        String name = JeItemRegistry.name(stack.itemId());
        if (name == null || "minecraft:air".equals(name)) {
            return ItemData.AIR;
        }
        ItemDefinition def = byName.get(name);
        if (def == null) {
            // Bedrock sometimes omits minecraft: prefix variants — try raw.
            def = byName.get(name.startsWith("minecraft:") ? name.substring(10) : name);
        }
        if (def == null) {
            LOG.fine("BE item miss jeId=" + stack.itemId() + " name=" + name);
            return ItemData.AIR;
        }
        int count = Math.max(1, Math.min(64, stack.count()));
        int damage = Math.max(0, stack.damage());
        ItemData.Builder builder = ItemData.builder().definition(def).count(count).damage(damage);
        // Placeable blocks need hashed block network_id, not item network id.
        BlockDefinition blockDef = blockDefinitionForItem(session, name);
        if (blockDef != null) {
            builder.blockDefinition(blockDef);
        }
        return builder.build();
    }

    private static BlockDefinition blockDefinitionForItem(LinkBedrockSession session, String itemName) {
        if (session == null || itemName == null || itemName.isBlank()) {
            return null;
        }
        JeToBedrockBlockMapper mapper = session.blockMapper();
        if (mapper == null) {
            return null;
        }
        int runtime = mapper.mapBlockName(itemName);
        if (runtime == mapper.airRuntimeId() && !"minecraft:air".equals(itemName)) {
            // Non-block items (sticks, etc.) correctly resolve to air — omit blockDefinition.
            return null;
        }
        return session.blockDefinitionOrAir(runtime);
    }

    /** Once per JVM: log first 20 JE name → Bedrock runtimeId maps for join forensics. */
    private static void logForensicItemMapsOnce(LinkBedrockSession session) {
        if (!FORENSIC_ITEM_MAP_LOGGED.compareAndSet(false, true) || session == null) {
            return;
        }
        Map<String, ItemDefinition> byName = itemLookup(session);
        String[] samples = {
                "minecraft:dirt", "minecraft:oak_log", "minecraft:oak_planks", "minecraft:cobblestone",
                "minecraft:stone", "minecraft:grass_block", "minecraft:oak_leaves", "minecraft:sand",
                "minecraft:gravel", "minecraft:oak_sapling", "minecraft:stick", "minecraft:deepslate",
                "minecraft:oak_slab", "minecraft:torch", "minecraft:crafting_table", "minecraft:furnace",
                "minecraft:chest", "minecraft:glass", "minecraft:water_bucket", "minecraft:diamond"
        };
        StringBuilder sb = new StringBuilder("BE item forensic maps (jeName→beRuntimeId):");
        for (String name : samples) {
            ItemDefinition def = byName.get(name);
            int rt = def != null ? def.getRuntimeId() : -1;
            sb.append(" ").append(name).append("→").append(rt);
        }
        LOG.info(sb.toString());
        BedrockJoinProbe.noteEvent(session.guid(), "item_forensic_maps logged samples=20");
    }

    private static Map<String, ItemDefinition> itemLookup(LinkBedrockSession session) {
        Map<String, ItemDefinition> byName = new HashMap<>();
        if (session == null || session.itemDefinitions() == null) {
            return byName;
        }
        for (ItemDefinition def : session.itemDefinitions()) {
            if (def == null || def.getIdentifier() == null) {
                continue;
            }
            byName.put(def.getIdentifier(), def);
        }
        return byName;
    }

    private static void sendContent(LinkBedrockSession session, int containerId, ItemData[] slots) {
        if (slots == null) {
            return;
        }
        InventoryContentPacket packet = new InventoryContentPacket();
        packet.setContainerId(containerId);
        List<ItemData> list = new ArrayList<>(slots.length);
        for (ItemData slot : slots) {
            list.add(slot != null ? slot : ItemData.AIR);
        }
        packet.setContents(list);
        session.sendUpstreamPacket(packet);
    }

    private static void sendSlot(LinkBedrockSession session, int containerId, int slot, ItemData item) {
        InventorySlotPacket packet = new InventorySlotPacket();
        packet.setContainerId(containerId);
        packet.setSlot(Math.max(0, slot));
        packet.setItem(item != null ? item : ItemData.AIR);
        session.sendUpstreamPacket(packet);
    }

    private static ItemData[] airArray(int n) {
        ItemData[] a = new ItemData[n];
        for (int i = 0; i < n; i++) {
            a[i] = ItemData.AIR;
        }
        return a;
    }

    private static int countNonEmpty(JeItemStackCodec.Stack[] stacks) {
        int n = 0;
        for (JeItemStackCodec.Stack s : stacks) {
            if (s != null && !s.isEmpty()) {
                n++;
            }
        }
        return n;
    }
}
