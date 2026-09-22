package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.downstream.JeItemStackCodec;
import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.session.LinkBedrockSession;
import java.util.logging.Logger;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.MobArmorEquipmentPacket;
import org.cloudburstmc.protocol.bedrock.packet.MobEquipmentPacket;

/**
 * JE {@code set_equipment} → Bedrock {@link MobEquipmentPacket} / {@link MobArmorEquipmentPacket}.
 *
 * <p>JE equipment slot enum (proto 776): 0 mainhand, 1 offhand, 2 boots, 3 leggings,
 * 4 chestplate, 5 helmet, 6 body.
 */
public final class JavaEntityEquipmentTranslator {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private JavaEntityEquipmentTranslator() {
    }

    public static void onSetEquipment(LinkBedrockSession session, int entityId, int slot,
                                      JeItemStackCodec.Stack stack) {
        if (session == null || !session.isSentSpawnPacket()) {
            return;
        }
        // Do not equip actors that are not on Bedrock yet (buffered until 0x71). Fabricating
        // a runtime from the JE id spammed MobEquipment during the generating-world stall.
        if (session.joinPhase() != LinkBedrockSession.JoinPhase.SPAWNED) {
            return;
        }
        Long runtime = session.runtimeForJava(entityId);
        if (runtime == null) {
            return;
        }
        ItemData item = JavaInventoryTranslator.jeToBedrock(session, stack);
        if (slot == 0 || slot == 1) {
            MobEquipmentPacket packet = new MobEquipmentPacket();
            packet.setRuntimeEntityId(runtime);
            packet.setItem(item != null ? item : ItemData.AIR);
            packet.setInventorySlot(0);
            packet.setHotbarSlot(0);
            packet.setContainerId(slot == 0 ? ContainerId.INVENTORY : ContainerId.OFFHAND);
            session.sendUpstreamPacket(packet);
            BedrockJoinProbe.noteEvent(session.guid(),
                    "java_equip→be id=" + entityId + " hand=" + (slot == 0 ? "main" : "off"));
            return;
        }
        if (slot >= 2 && slot <= 5) {
            // Armor pieces arrive one-at-a-time; send a full MobArmorEquipment with this piece
            // and AIR for unknowns (client merges visually for tracked actors).
            MobArmorEquipmentPacket armor = new MobArmorEquipmentPacket();
            armor.setRuntimeEntityId(runtime);
            armor.setHelmet(slot == 5 ? item : ItemData.AIR);
            armor.setChestplate(slot == 4 ? item : ItemData.AIR);
            armor.setLeggings(slot == 3 ? item : ItemData.AIR);
            armor.setBoots(slot == 2 ? item : ItemData.AIR);
            armor.setBody(ItemData.AIR);
            session.sendUpstreamPacket(armor);
            BedrockJoinProbe.noteEvent(session.guid(),
                    "java_equip→be id=" + entityId + " armorSlot=" + slot);
            return;
        }
        if (slot == 6) {
            MobArmorEquipmentPacket body = new MobArmorEquipmentPacket();
            body.setRuntimeEntityId(runtime);
            body.setHelmet(ItemData.AIR);
            body.setChestplate(ItemData.AIR);
            body.setLeggings(ItemData.AIR);
            body.setBoots(ItemData.AIR);
            body.setBody(item != null ? item : ItemData.AIR);
            session.sendUpstreamPacket(body);
            return;
        }
        LOG.fine("BE equip skip unknown slot=" + slot + " id=" + entityId);
    }
}
