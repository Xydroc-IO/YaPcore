package com.yapcore.link.bedrock.session;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleBlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

/** Split from {@link LinkBedrockSession} for the ≤500-line domain gate. */
final class LinkBedrockSessionPlay {

    private final LinkBedrockSession s;

    LinkBedrockSessionPlay(LinkBedrockSession session) {
        this.s = session;
    }

   void armPostInitPositionConfirm(double feetX, double feetY, double feetZ) {
      s.lastSyncX = feetX;
      s.lastSyncY = feetY;
      s.lastSyncZ = feetZ;
      s.pendingTeleportId = Math.max(1, s.lastAcceptedTeleportId + 1);
      s.unconfirmedAuthMoves = 0;
   }

   boolean markTeleportAccepted(int teleportId, double x, double y, double z) {
      if (teleportId >= 0 && teleportId == s.lastAcceptedTeleportId) {
         return false;
      }

      boolean echo = !isNearLastSync(x, y, z);
      s.lastAcceptedTeleportId = teleportId;
      s.lastSyncX = x;
      s.lastSyncY = y;
      s.lastSyncZ = z;
      s.pendingTeleportId = teleportId;
      s.unconfirmedAuthMoves = 0;
      return echo;
   }

   boolean isNearLastSync(double x, double y, double z) {
      if (Double.isNaN(s.lastSyncX)) {
         return false;
      }

      double dx = x - s.lastSyncX;
      double dy = y - s.lastSyncY;
      double dz = z - s.lastSyncZ;
      return dx * dx + dy * dy + dz * dz < 0.001;
   }

   boolean confirmOrHoldAuthMove(double feetX, double feetY, double feetZ) {
      if (s.pendingTeleportId < 0) {
         s.unconfirmedAuthMoves = 0;
         return true;
      } else if (canConfirmTeleport(feetX, feetY, feetZ)) {
         s.pendingTeleportId = -1;
         s.unconfirmedAuthMoves = 0;
         return false;
      } else {
         s.unconfirmedAuthMoves++;
         return false;
      }
   }

   boolean canConfirmTeleport(double feetX, double feetY, double feetZ) {
      return Double.isNaN(s.lastSyncX)
         ? false
         : Math.abs(feetX - s.lastSyncX) < LinkBedrockSession.TELEPORT_CONFIRM_XZ
            && Math.abs(feetY - s.lastSyncY) < LinkBedrockSession.TELEPORT_CONFIRM_Y
            && Math.abs(feetZ - s.lastSyncZ) < LinkBedrockSession.TELEPORT_CONFIRM_XZ;
   }

   boolean shouldResendTeleport() {
      return s.pendingTeleportId >= 0 && s.unconfirmedAuthMoves >= LinkBedrockSession.TELEPORT_RESEND_THRESHOLD;
   }

   void resetUnconfirmedAuthMoves() {
      s.unconfirmedAuthMoves = 0;
   }

   int beginOrContinueDig(int x, int y, int z) {
      if (s.digSequence >= 0 && s.digX == x && s.digY == y && s.digZ == z) {
         return -1;
      }

      int seq = s.nextBlockSequence();
      s.digX = x;
      s.digY = y;
      s.digZ = z;
      s.digSequence = seq;
      return seq;
   }

   void clearDig() {
      s.digX = Integer.MIN_VALUE;
      s.digY = Integer.MIN_VALUE;
      s.digZ = Integer.MIN_VALUE;
      s.digSequence = -1;
   }

   void setLastAttackTarget(int entityId) {
      s.lastAttackTarget = entityId;
   }

   void noteAttackTarget(int entityId) {
      setLastAttackTarget(entityId);
   }

   boolean notePlayerInputFlags(int flags) {
      int f = flags & 127;
      if (f == s.lastPlayerInputFlags) {
         return false;
      }

      s.lastPlayerInputFlags = f;
      return true;
   }

   void trackEntity(int javaEntityId, long runtimeId) {
      s.entityRuntimeByJava.put(javaEntityId, runtimeId);
   }

   Long runtimeForJava(int javaEntityId) {
      return s.entityRuntimeByJava.get(javaEntityId);
   }

   int javaEntityForRuntime(long runtimeId) {
      if (runtimeId <= 0L) {
         return 0;
      }

      int asInt = (int)runtimeId;
      Long mapped = s.entityRuntimeByJava.get(asInt);
      if (mapped != null && mapped == runtimeId) {
         return asInt;
      }

      for (Map.Entry<Integer, Long> e : s.entityRuntimeByJava.entrySet()) {
         if (e.getValue() != null && e.getValue() == runtimeId) {
            return e.getKey();
         }
      }

      if (runtimeId == s.runtimeId()) {
         return 0;
      } else {
         return asInt > 0 ? asInt : 0;
      }
   }

   void setEntityPos(int javaEntityId, float x, float y, float z, float yaw, float pitch) {
      s.entityPosByJava.put(javaEntityId, new float[]{x, y, z, yaw, pitch});
   }

   float[] entityPos(int javaEntityId) {
      return s.entityPosByJava.get(javaEntityId);
   }

   Long untrackEntity(int javaEntityId) {
      s.entityPosByJava.remove(javaEntityId);
      s.entityHealthByJava.remove(javaEntityId);
      s.entityMoveTicks.remove(javaEntityId);
      s.playerJavaEntityIds.remove(javaEntityId);
      return s.entityRuntimeByJava.remove(javaEntityId);
   }

   void rememberPlayerName(UUID id, String name) {
      if (id != null && name != null && !name.isBlank()) {
         s.playerNamesByUuid.put(id, name);
      }
   }

   void forgetPlayerName(UUID id) {
      if (id != null) {
         s.playerNamesByUuid.remove(id);
      }
   }

   String playerName(UUID id) {
      return id == null ? null : s.playerNamesByUuid.get(id);
   }

   Map<UUID, String> playerNameSnapshot() {
      return Map.copyOf(s.playerNamesByUuid);
   }

   void rememberPluginCommands(Iterable<String> names) {
      if (names != null) {
         for (String n : names) {
            if (n != null && !n.isBlank()) {
               s.pluginCommandNames.add(n.trim().toLowerCase(Locale.ROOT));
            }
         }
      }
   }

   Set<String> pluginCommandNames() {
      return Set.copyOf(s.pluginCommandNames);
   }

   void rememberJeInventorySlots(int slots) {
      if (slots > 0) {
         s.lastJeInventorySlots = slots;
      }
   }

   void rememberJeWindow(int windowId, int menuType) {
      s.lastJeWindowId = windowId;
      s.lastJeMenuType = menuType;
   }

   void storeBedrockInventory(ItemData[] inv, ItemData[] armor, ItemData offhand) {
      s.bedrockInventorySlots = inv;
      s.bedrockArmorSlots = armor;
      s.bedrockOffhand = offhand != null ? offhand : ItemData.AIR;
      s.hasBedrockInventorySnapshot = true;
   }

   void rememberHealth(float health, int food, float saturation) {
      s.lastHealth = health;
      s.lastFood = food;
      s.lastSaturation = saturation;
   }

   ItemData cursorItem() {
      return s.cursorItem != null ? s.cursorItem : ItemData.AIR;
   }

   void setCursorItem(ItemData item) {
      s.cursorItem = item != null ? item : ItemData.AIR;
   }

   int jeContainerStateId() {
      return s.jeContainerStateId;
   }

   void rememberJeContainerState(int stateId) {
      s.jeContainerStateId = Math.max(0, stateId);
   }

   int nextStackNetworkId() {
      int id = s.stackNetworkIdSeq++;
      if (s.stackNetworkIdSeq <= 0) {
         s.stackNetworkIdSeq = 1;
      }
      return id;
   }

   ItemData containerSlot(int slot) {
      return s.openContainerSlots.getOrDefault(slot, ItemData.AIR);
   }

   void setContainerSlot(int slot, ItemData item) {
      if (item == null || item == ItemData.AIR) {
         s.openContainerSlots.remove(slot);
      } else {
         s.openContainerSlots.put(slot, item);
      }
   }

   void clearOpenContainerSlots() {
      s.openContainerSlots.clear();
   }

   void rememberBlockRuntime(int x, int y, int z, int runtimeId) {
      s.blockRuntimeAtPos.put(packBlock(x, y, z), runtimeId);
   }

   int blockRuntimeAt(int x, int y, int z) {
      Integer rt = s.blockRuntimeAtPos.get(packBlock(x, y, z));
      return rt != null ? rt : 0;
   }

   void rememberEntityHealth(int javaEntityId, float health) {
      if (javaEntityId > 0) {
         s.entityHealthByJava.put(javaEntityId, health);
      }
   }

   Float entityHealth(int javaEntityId) {
      return s.entityHealthByJava.get(javaEntityId);
   }

   private static long packBlock(int x, int y, int z) {
      return (((long) x & 0x3FFFFFFL) << 38) | (((long) z & 0x3FFFFFFL) << 12) | ((long) y & 0xFFFL);
   }

   boolean bumpEntityMoveForceAbsolute(int javaEntityId) {
      int n = s.entityMoveTicks.merge(javaEntityId, 1, Integer::sum);
      return n % 20 == 0;
   }

   BlockDefinition blockDefinitionOrAir(int runtimeIdHint) {
      if (s.codec != null) {
         try {
            BlockDefinition def = (BlockDefinition)s.codec.palettes().blocks().getDefinition(runtimeIdHint);
            if (def != null) {
               return def;
            }
         } catch (Exception ignored) {
         }
      }

      return new SimpleBlockDefinition("minecraft:air", runtimeIdHint, NbtMap.EMPTY);
   }

   int airRuntimeId() {
      if (s.codec != null && s.codec.palettes() != null) {
         return s.codec.palettes().airRuntimeId();
      } else {
         return s.blockMapper != null ? s.blockMapper.airRuntimeId() : 0;
      }
   }

   int stoneRuntimeId() {
      if (s.codec != null && s.codec.palettes() != null) {
         return s.codec.palettes().stoneRuntimeId();
      } else {
         return s.blockMapper != null ? s.blockMapper.stoneRuntimeId() : 1;
      }
   }

}
