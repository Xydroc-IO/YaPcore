# YaPLib — packet intercept

Folia-safe ProtocolLib-class hooks. Drop `yap-lib.jar` (CORE+NETWORK). Do not install ProtocolLib or PacketEvents.

This is the **replacement product**, not an API shim. Plugins with `depend: [ProtocolLib]` and `com.comphenix.protocol` / `WrapperPlay*` classes will not load. First-party listeners: YaPHolo clicks, YaPChat mute/filter, YaPGuard speed/reach/scaffold. Use `com.yapcore.lib`.

Holograms are a **separate** plugin: [`yap-holo.jar`](YAPHOLO.md).

## Naming (ProtocolLib convention)

`Client` = **from the player** (serverbound). `Server` = **to the player** (clientbound).

That matches ProtocolLib, not Mojang’s `Clientbound*` / `Serverbound*` class names.

| Listen for | Constant | Direction |
|------------|----------|-----------|
| Player move / chat / interact | `PacketTypes.Play.Client.MOVE_PLAYER_POS` / `CHAT` / `INTERACT` | serverbound |
| Add entity / metadata / system chat | `PacketTypes.Play.Server.ADD_ENTITY` / `ENTITY_METADATA` / `SYSTEM_CHAT` | clientbound |

ProtocolLib aliases (`SPAWN_ENTITY`, `USE_ENTITY`, `POSITION`, `ENTITY_METADATA`) are the same instances as the Mojang snake_case names (`add_entity`, `interact`, `move_player_pos`, `set_entity_data`). Anything else: `PacketTypes.of(state, dir, "name")`.

```java
PacketService packets = PacketServices.requirePackets();
packets.addListener(this, new PacketAdapter(this, PacketPriority.NORMAL, PacketThreadMode.REGION,
        PacketTypes.Play.Client.CHAT) {
    @Override
    public void onPacketReceiving(PacketEvent event) {
        // Player entity thread — world reads OK. Packet is held until you return.
        if (event.player() != null && event.player().hasPermission("yap.mute")) {
            event.setCancelled(true);
        }
    }
});
packets.addListener(this, new PacketAdapter(this, PacketPriority.NORMAL, PacketThreadMode.REGION,
        PacketTypes.Login.Client.HELLO) {
    @Override
    public void onPacketReceiving(PacketEvent event) {
        // Global region — no Player yet. Cancel/rewrite OK. Do not touch worlds.
        if (event.address() != null && blocked(event.address().getAddress())) {
            event.setCancelled(true);
        }
    }
});
```

`onPacketReceiving` is inbound (from the player). `onPacketSending` is outbound (to the player).

| Rule | Why |
|------|-----|
| Default thread is the connection event loop | Fast path. Cancel/rewrite here; do not touch blocks/entities |
| No world mutation on Netty | Folia region ownership |
| `PacketThreadMode.REGION` + cancelable priority | Packet is **held** before `packet_handler`. Entity scheduler if a player is bound; otherwise global region (handshake/login). Netty is never blocked |
| `event.address()` | Connection IP during handshake/login when `event.player()` is null |
| `PacketThreadMode.REGION` + `MONITOR` | Observe-only after accept — does not delay the packet |
| `PacketPriority.MONITOR` | Sees the final cancelled flag; cannot cancel |

Handshake/login (no Bukkit player yet) still hops: Folia **global** region scheduler, with `event.address()` for the remote IP. Do not mutate worlds there.

## Read / write / create

`PacketContainer` is the NMS handle plus typed field views (ProtocolLib `StructureModifier`):

```java
PacketContainer box = packets.createPacket(PacketTypes.Play.Server.SYSTEM_CHAT);
box.chatComponents().write(0, Component.text("hello"));
packets.send(player, box, true);

double y = event.packet().doubles().read(1);
ItemStack item = event.packet().items().readOrNull(0);
PacketBlockPos pos = event.packet().blockPositions().readOrNull(0);
PacketNbt nbt = event.packet().nbtModifiers().readOrNull(0);

WrappedEntityData meta = event.packet().entityMetadata();
int entityId = meta.entityId();
Object customName = meta.getValue(2);
meta.setValue(2, Optional.of(Component.text("rewritten"))); // existing or new watcher index
meta.setValue(23, true); // new index — serializer inferred (BOOLEAN, COMPONENT, ItemStack, …)
```

`createPacket` allocates an empty NMS instance (no-arg constructor, then `Unsafe`). Fill fields before send.

There is no generated `WrapperPlayServer*` catalog and no `com.comphenix.protocol` drop-in — existing ProtocolLib jars will not load. Use `depend: [YaPLib]` and `com.yapcore.lib`. Entity metadata get/set uses `WrappedEntityData`; new watcher indexes pack through `EntityDataSerializers` (pass `Optional` for optional custom-name slots).

Send/receive:

```java
packets.send(player, nmsPacket, true);   // run outbound listeners
packets.send(player, nmsPacket, false);  // skip YaPLib listeners
packets.receive(player, nmsPacket, true);
```

## Commands

| Command | Permission |
|---------|------------|
| `/yaplib status\|reload` (aliases `/protocolib`) | `yaplib.admin` |

## Config

`plugins/YaPLib/config.yml` — `intercept`, `debug-listeners`.

## Related

- [YAPHOLO.md](YAPHOLO.md) — packet holograms on this intercept
- [PLUGIN_COMPAT.md](PLUGIN_COMPAT.md) — ProtocolLib / PacketEvents redirect here
- [PLUGINS.md](PLUGINS.md) — CORE jar list
