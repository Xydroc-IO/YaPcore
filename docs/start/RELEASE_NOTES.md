# YaPcore release notes

Product version **0.0.0.1** · YaP Link **0.6.0-phase6** · YaP-Folia **26.2**

GitHub ships this tag as a **prerelease**. Packs and zips are
`https://github.com/Xydroc-IO/YaPcore/releases/download/0.0.0.1/{file}`.
`/releases/latest` ignores prereleases. The old stable **1.0.0.0** release was deleted, so that URL stays empty until a non-prerelease exists.

For build commands and zip layout see [RELEASES.md](RELEASES.md). For live status see
[YAPCORE_WHITEPAPER.md](../whitepaper/YAPCORE_WHITEPAPER.md).

YaP-Folia provenance polish + `UPSTREAM.lock` refresh — [YAP_FOLIA_PATCHES.md](../folia/YAP_FOLIA_PATCHES.md).

---

## After 0.0.0.1 — portals, ultrawide Panini, createWorld (2026-09-23)

Same ship version. No product bump. Client mods bumped (ultrawide **1.0.13**, visuals **1.0.18**, presence **1.0.12**). Rebuild packs + clients + `publishReleasesFolder -PyapGameplay=true` → stage `UPLOAD/`.

| Area | Change |
|------|--------|
| **YaPPortals** | End doors fill **black stained glass** (not `NETHER_PORTAL`); registry + walkable travel; cancel Folia nether hops at portal-ready. |
| **YaPDungeons** | Portal registry, air opening + lime pack swirl, Folia-safe instance world ops / teleports. |
| **YaP420** | Boutique packing premium on ounce/pound; dealer GUI + plant display polish. |
| **Folia** | `0080` portal-couple safe reschedule; `0081` enable Bukkit `createWorld` (unload stays stubbed). Jar md5 `ec017174`. |
| **Client** | Ultrawide Panini edge correct + Hor+; Iris pipeline / Sodium kick harden; presence skin refresh via SodiumWorldKick only. |
| **Fleet** | Local instance props / layout sync. |
| **Pack** | YaP420 plant generator + refreshed `yapcore-default.zip` / `.mcpack`. |

Upload: `gh release upload 0.0.0.1 UPLOAD/*.{zip,mcpack} --clobber -R Xydroc-IO/YaPcore`.

---

## After 0.0.0.1 — domain ≤500 + YaP420 ship (2026-09-22)

Same ship version. No version bump. `gradle checkDomainLineLimits` green. Rebuild clients + `publishReleasesFolder -PyapGameplay=true`.

| Area | Change |
|------|--------|
| **Domain ≤500** | Split oversize first-party classes: `AdminModerationActions`, `ClaimBorderOps`, `PortalLocalTransferOps`; Link Bedrock `LinkBedrockSessionSwitch` / `Pose` / `ConnectSpawn`, `BedrockSessionHostJoinListener`, `JavaDownstreamPlayEntities` / `ClientSend`. |
| **YaP420** | First-party plant → grow → harvest → dry/cure → craft → consume (`yap-420.jar`). |
| **YaPItems** | `yap420.yml` catalog (CMD 12200–12223); recipes accept custom item ids. |
| **Admin** | Super-menu **YaP420** hub (JE chest + Bedrock form + yap-staff). Give / reload / remove / status. |
| **Client** | Optional Fabric **yap-420** haze (`yap:420`); staff 1.0.34 ships YaP420 screen. `client_mods.zip` includes yap-420. |
| **Pack** | YaP420 item models/textures in `yap-items` overlay → rebuild `yapcore-default.zip`. |
| **Scripts** | Lifecycle / Folia / packs / check / plugins scripts moved under `scripts/{lifecycle,folia,packs,check,plugins,docs,setup,bench,windows}/`. |

Upload: `gh release upload 0.0.0.1 releases/0.0.0.1/{yapcore-release-linux.zip,yapcore-release-windows.zip,yap-network-suite.zip,yap-gameplay-suite.zip,yapcore-default.zip,yapcore-default.mcpack,client_mods.zip} --clobber -R Xydroc-IO/YaPcore`.

---

## After 0.0.0.1 — gameplay ops refresh (2026-09-20)

Same ship version. No version bump. Local `releases/0.0.0.1/` + `dist/client-mods/client_mods.zip` republished.

| Area | Change |
|------|--------|
| **Regions** | Admin `COMMAND` exempt so `/spawnmob` and staff tools work in protected spawn. Server-id via `yap-server-id.txt` for lobby/instance regions. |
| **LagGuard** | ClearLagg-style ground-item sweeper (warn + clear timers). |
| **Admin** | `/spawnmob` level picker via YaPLeveledMobs; Folia-safe teleport for troll squash. |
| **Swimming** | WaterWaves skips when swimming; YaPSkills swimming speed/breath/infinite at max. |
| **Client** | Fabric `client_mods.zip` rebuilt (visuals 1.0.13, staff 1.0.31, bag/presence/blocks/ultrawide). |

Upload: `gh release upload 0.0.0.1 releases/0.0.0.1/{yapcore-release-linux.zip,yapcore-release-windows.zip,yap-network-suite.zip,yap-gameplay-suite.zip,yapcore-default.zip,yapcore-default.mcpack,client_mods.zip} --clobber -R Xydroc-IO/YaPcore`.

---

## After 0.0.0.1 — 500 held on the packed spawn (2026-09-19)

Same ship version. No version bump. Product jar is `lib/yap-folia-26.2.jar` md5 `76aeaf3fefcf34bc9e80f441d0419df7`.

| Area | Change |
|------|--------|
| **0073** | Spawn search uses the registered cut, not a player standing in the chunk. |
| **0074** | Block spread and grow into a cut section return before snapshotting the missing chunk. |
| **0075** | Configuration stays on the global tick so a queued login still gets keepalives. |
| **0076** | Joins are not capped per tick. The play listener is installed before the connection is added. Spawn avoids the cut column. |
| **0077** | Spawn search returns null for chunk X=−1 before the cut is registered. Login acknowledgement does not park the global tick. |
| **0078** | Play packets are sent only after the protocol switch. |
| **0079** | No configuration keepalive after `finish_configuration`. The client is already in play. |
| **Proof** | `20260919T165559Z`: 500 at both ends, `into 2 shards`, TNT 2400, hoppers 770, 32 villagers, fuse drop 808.5, busiest region 37.24 ms. Stock `20260919T171015Z` started at 500 on that scene and ended at 119, busiest region 65.03 ms. Those 119 left because the encoder ran out of direct memory, not because a watchdog fired. |

---

## After 0.0.0.1 — owned floor 0071 / 0072 (2026-09-19)

Same ship version. No version bump. That cite was jar `398d2c6f6c93225a810abedf17ba1f9c`. The product file is now `76aeaf3fefcf34bc9e80f441d0419df7`.

| Area | Change |
|------|--------|
| **0071** | Relocate only an entity whose chunk key is in the corridor. A pad is a loaded chunk at least two chunks off the hole with a motion-blocking floor. |
| **0072** | A cut chunk contributes no collision box. An owned chunk keeps its blocks when the radius-4 miss is only the corridor. `fastClip` treats that chunk as empty air. |
| **Bench** | Villagers are penned on the four interior chunks. `villagers_start` counts planted villagers who are still alive. |
| **Proof** | Superseded as the product file. Jar `398d2c6f` held 100 (`20260919T134700Z`, 12.60 ms) beside stock `20260919T135026Z` (26.75 ms). See the 500 hold note above. |

---

## After 0.0.0.1 — teleport accept across the cut 0069 (2026-09-19)

Same ship version. Death respawn already committed on the shard that owns the destination chunk. The client accept was still rejected because Folia's 1-chunk halo includes the corridor, and that section still has a region pointer.

| Area | Change |
|------|--------|
| **0069** | A registered cut is not a foreign shard. The destination chunk must still be owned by this thread. |
| **Proof** | Superseded. Those cites were jar `b956d19a`. The product file is now `76aeaf3f` — see the 500 hold note above. |

---

## After 0.0.0.1 — teleport join on the destination chunk 0068 (2026-09-19)

Same ship version. Lab jar only. Home-leash commits were running on the shard whose center is chunk 10 while the position was chunk 0. That throw in `Player.aiStep` became `Internal server error`. The cut neighbor in the accept halo became `Invalid move player packet`.

| Area | Change |
|------|--------|
| **0068** | Join re-queues by the destination chunk until this thread owns it. `aiStep` returns instead of `getEntities` off-thread. `isInWall` skips a null chunk. Accept does not require the carved hole. |
| **Proof** | Superseded by `0069` cites `20260919T113134Z` and `20260919T113416Z`. |

---

## After 0.0.0.1 — cut is a status boundary 0067 (2026-09-19)

Same ship version. Lab jar only. Entity ticking needs a full radius-2 ring. The corridor unload cleared that on west piles one chunk off the hole. Leaving the loaded-bit set would claim the hole is still a chunk.

| Area | Change |
|------|--------|
| **0067** | `updatePendingStatus` treats a registered cut as a finished neighbor. `isNeighbourFullLoaded` stays false after the unload. |
| **Proof** | Landed in product cite `20260919T105329Z` (`into 2 shards`, fuse drop 802, `players_end=100`). |

---

## After 0.0.0.1 — grass skip cut neighbor 0066 (2026-09-19)

Same ship version. Lab jar only. 0065 made the west shard entity-tick; grass then `getChunkAt` the empty corridor (null) and `ReportedException` halted region #1 before fuse could drain.

| Area | Change |
|------|--------|
| **0066** | `SpreadingSnowyBlock.randomTick` uses `getChunkIfLoaded` and skips a missing neighbor. Does not sync-load the cut. |
| **Proof** | Landed in product cite `20260919T105329Z` (`into 2 shards`, fuse drop 802, `players_end=100`). |

---

## After 0.0.0.1 — kept SIM tickets after cut 0065 (2026-09-19)

Same ship version. Lab jar only. 0064 packed fullcite logged `into 2 shards` then fuse drop 401: east TNT ticked, west piles froze. The ticket wall zeroed flood-propagated PLAYER sim; PLUGIN/FORCED on the kept shards did not keep entity-ticking. Not bot herding.

| Area | Change |
|------|--------|
| **0065** | `processLevelUpdates` takes min(flood, remaining PLUGIN/FORCED ticket level) so a cut-wall decrease cannot leave kept piles at BLOCK_TICKING. `setChunkForceLoaded` on the owning region thread. `PLUGIN_TICKET`/`PLUGIN` get `FLAG_KEEP_DIMENSION_ACTIVE`. |
| **Proof** | Landed in product cite `20260919T105329Z` (`into 2 shards`, fuse drop 802, `players_end=100`). |

---

## After 0.0.0.1 — spawn finder skip cut 0064 (2026-09-19)

Same ship version. Lab jar only. 0063 packed fullcite emptied the hole then a bot death `syncLoadNonFull`’d the cut and watchdog-stalled the region — still no `into 2 shards`.

| Area | Change |
|------|--------|
| **0064** | `PlayerSpawnFinder.getLevelRespawnPos` returns null for cut keys; `syncLoadNonFull` does not `managedBlock` on the empty buffer. |
| **Proof** | Landed in product cite `20260919T105329Z` (`into 2 shards`, fuse drop 802, `players_end=100`). |

---

## After 0.0.0.1 — cut ticket wall 0062 (2026-09-19)

Same ship version. Incremental Folia → `lib/yap-folia-26.2-lab.jar`. Does **not** overwrite the GUI product jar. Does **not** force-unload occupied corridor chunks or delete TNT.

| Area | Change |
|------|--------|
| **0062** | Neighbor view-distance skip zeros the Moonrise propagator cell so the Folia empty buffer can unload. Ticket clamp is the bounded section AABB. Carve still aborts if corridor keys remain. |
| **Proof** | Live packed fullcite must log `YaP force-partition region #N into 2 shards`. Fuse/hoppers stay matched. Empty-strip bar must still PASS. |
| **Patches** | **56** files (`0000`–`0063`) |

---

## After 0.0.0.1 — partition region 0 (0063) (2026-09-19)

Same ship version. Lab jar only. 0062 packed fullcite emptied the corridor (`carve complete`) then never logged `into 2 shards` because `requestPartition` ignored region id 0.

| Area | Change |
|------|--------|
| **0063** | Spawn is region 0. Post-carve `requestPartition(0)` now enqueues `tryForcePartition`. Silent “not buffered” requeue now logs. |
| **Not a cite** | `20260919T090050Z` MSPT 16.1 / fuse 401 vs 800 — hole emptied, **no split**, do not publish. |

---

## After 0.0.0.1 — YaPChat / YaPGuard packet listeners (2026-09-19)

Same ship version. Rebuild `yap-chat.jar` + `yap-guard.jar` (needs `yap-lib.jar`).

| Area | Change |
|------|--------|
| **YaPChat** | Mute + block-filter cancel `Play.Client.CHAT` / `CHAT_COMMAND`. Unsigned rewrite uses YaPLib when present. |
| **YaPGuard** | Packet speed (flag), reach (cancel interact), scaffold (cancel place). Fly stays on the timer (needs blocks). Not Grim. |

---

## After 0.0.0.1 — partition along carved corridor 0061 (2026-09-19)

Same ship version. Lab jar only until a hot packed-spawn log shows `into 2 shards`.

| Area | Change |
|------|--------|
| **0061** | After a finished carve, force-partition splits left/right of that corridor. Median recut was why packed spawn stayed `force-partition deferred` with no `into 2 shards`. |
| **Patches** | **54** files (`0000`–`0061`) |

---

## After 0.0.0.1 — loaded kept-edge pads 0060 (2026-09-19)

Same ship version. Incremental Folia → `lib/yap-folia-26.2-lab.jar`.

| Area | Change |
|------|--------|
| **0060** | Corridor landing pads are the nearest already-loaded kept-edge chunks, not the view-distance rim. 0059 skipped unload dests (correct); pads at chunk 18 made relocate move 0 so packed-spawn carve aborted. |
| **Patches** | **53** files (`0000`–`0060`) |

---

## After 0.0.0.1 — player pad no sync-load 0059 (2026-09-19)

Same ship version. Incremental Folia → `lib/yap-folia-26.2-lab.jar`.

| Area | Change |
|------|--------|
| **0059** | Player pad relocate skips `getChunk(load)` / 7-arg `teleportTo`. After a packed live split those nested `PlayerSpawnFinder` sync-load and watchdog-stalled the region. |
| **Patches** | **52** files (`0000`–`0059`) |

---

## After 0.0.0.1 — YaPLib handshake hop + entity-data codecs (2026-09-19)

Same ship version. Rebuild `yap-lib.jar`.

| Area | Change |
|------|--------|
| **Handshake/login REGION** | No Bukkit player → Folia global scheduler + `event.address()`. Cancel still holds the packet. |
| **New metadata indexes** | `WrappedEntityData.setValue` packs via `EntityDataSerializers` (Component, ItemStack, Optional, …). |
| **Not a shim** | Still no `com.comphenix.protocol` — ProtocolLib plugins do not load. |

---

## After 0.0.0.1 — YaPLib region hold + entity data (2026-09-19)

Same ship version. Rebuild `yap-lib.jar`.

| Area | Change |
|------|--------|
| **REGION cancel** | Cancelable region listeners **hold** the packet before `packet_handler`. Folia hop, then drop or deliver. Netty is never blocked. `MONITOR` region stays observe-only. |
| **Entity metadata** | `WrappedEntityData` / `packet.entityMetadata()` — entity id + watcher-index get/set. No generated `WrapperPlay*` catalog. |

---

## After 0.0.0.1 — YaPLib ProtocolLib-class API (2026-09-19)

Same ship version. Rebuild `yap-lib.jar` + `yap-holo.jar`.

| Area | Change |
|------|--------|
| **Naming** | `Play.Client` = from the player (serverbound), `Play.Server` = to the player (clientbound) — ProtocolLib convention. |
| **Registry** | Full PLAY constants + ProtocolLib aliases (`SPAWN_ENTITY`, `USE_ENTITY`, `ENTITY_METADATA`, …). |
| **Modifiers** | `StructureModifier` + ItemStack / chat / BlockPos / GameProfile / NBT / packed entity-data views. |
| **createPacket** | Empty NMS instance from `PacketType` (no-arg ctor or Unsafe). |
| **Not a shim** | No `com.comphenix.protocol` drop-in; existing ProtocolLib plugins still do not load. |

---

## After 0.0.0.1 — YaPHolo DH extras (2026-09-19)

Same ship version. Rebuild `yap-holo.jar` + `yap-npcs.jar`.

| Area | Change |
|------|--------|
| **Attach** | Follow NPC / player / entity (`/yapholo attach`). |
| **Placeholders** | `%player_name%`, `%online%`, and PlaceholderAPI on the viewer’s entity thread. |
| **Clicks** | Left punch / right interact → console, player command, next/prev page. |
| **Items / anims** | `#ICON:DIAMOND`, `#ANIM:wave` (`animations.yml`). |
| **Pages** | Per-viewer pages (`;;` or dashboard blank line). |
| **NPC nametags** | YaPNpcs `hologram-nametags` uses `npcntag_<id>` instead of vanilla customName. |

---

## After 0.0.0.1 — YaPHolo split (2026-09-19)

Same ship version. Rebuild `yap-lib.jar` + `yap-holo.jar`.

| Area | Change |
|------|--------|
| **YaPLib** | Packet intercept only (`PacketService`). |
| **YaPHolo** | Packet holograms (`/yapholo`, `HologramService`) — `depend: [YaPLib]`. |

---

## After 0.0.0.1 — YaPLib packet intercept (2026-09-19)

Same ship version. Rebuild `yap-lib.jar` (`gradle :lib-plugin:shadowJar` or `installProductDefaults`).

| Area | Change |
|------|--------|
| **YaPLib** | Folia-safe ProtocolLib-class intercept (`PacketService`). Holograms moved to YaPHolo. |
| **Thread model** | Listen/cancel/rewrite on the Netty event loop; `REGION` listeners are observe-only. |
| **Compat** | ProtocolLib / PacketEvents → `yap-lib.jar`. DecentHolograms → `yap-holo.jar`. |

---

## After 0.0.0.1 — skills expansion (2026-09-18)

Same ship version. Rebuild `yap-skills.jar` (`gradle :skills-plugin:shadowJar` or `publishReleasesFolder -PyapGameplay=true`).

| Area | Change |
|------|--------|
| **Live skills** | Mining, woodcutting, strength, marathon, builder, herbalism, excavation, alchemy, health (cap 120, RS curve). `/skills` `/stats`. |
| **Power** | 3× mine/chop and hits at 120; Marathon 2× walk; Builder +1 place reach and 25% keep-block (no fast-place). |
| **Max abilities** | Super Breaker / Tree Feller (sneak + right-click air, 12s / 90s). Green Terra auto-replant. Excavation rare loot (diamond ~1/2500). Alchemy brew speed 1×→2×. Health +5 hearts and combat regen. |
| **Hub** | `/menu` Skills button when jobs are off. Level-up title/chat/sound. |

---

## After 0.0.0.1 — dashboard chest shops (2026-09-18)

Same ship version. Rebuild chassis + `yap-playerdata.jar`. Restart the GUI so the Shops sub-tabs load.

| Area | Change |
|------|--------|
| **Shops tab** | NPC catalogs and chest shops as two sub-tabs. |
| **API** | `GET/POST /api/shops` chest-list/create/set/remove/info (fleet `instance`). |
| **In-game** | `/shop create <price>` look-at-chest; admin `/shop list json all`. |
| **NPCs** | Honor `server-id` so lobby hub catalogs load. |

---

## After 0.0.0.1 — vanilla nether/end portals (2026-09-18)

Same ship version. Rebuild `yap-portals.jar` (no Folia product-jar overwrite).

| Area | Change |
|------|--------|
| **YaPPortals** | Vanilla nether, end, and end-gateway hop like Paper. Cancel a vanilla hop only inside an enabled YaP pad so Link Connect is not raced by local `world_nether`. |

---

## After 0.0.0.1 — contiguous-bar 0043 (2026-09-18)

Same ship version. Lab paperclip → `lib/yap-folia-26.2-lab.jar` (does **not** overwrite the GUI product jar).

| Area | Change |
|------|--------|
| **0043 relocate** | Same-world `Entity.teleportTo` so a live corridor can empty; no off-thread pad fallback. |
| **0043 probe** | `gap_bands` + `ticking_regions` — split bar is force+split+hold, not BLOCKS lockstep. |
| **Check** | `./scripts/folia/smoke-contiguous-bar.sh` — contiguous strip, VD/sim **10**, aligned microticks **off**, port **25575**. |

---

## After 0.0.0.1 — evacuate cut players 0058 (2026-09-19)

Same ship version. Incremental Folia → `lib/yap-folia-26.2-lab.jar`.

| Area | Change |
|------|--------|
| **0058** | Corridor evacuate includes `ServerPlayer`. Packed-spawn bots were pinning the cut section; force-partition aborted. |
| **Bench** | Home-leash does not teleport into an unloaded home chunk (would PLAYER-ticket the hole back). |
| **Patches** | **51** files (`0000`–`0058`) |

---

## After 0.0.0.1 — strip unpinned cut tickets 0057 (2026-09-19)

Same ship version. Incremental Folia → `lib/yap-folia-26.2-lab.jar`.

| Area | Change |
|------|--------|
| **0057** | Corridor strip no longer keeps neighbor view-distance PLAYER tickets on an empty cut chunk. Occupied chunks and portal chunks stay pinned. Lets packed-spawn force-partition finish under VD 8. |
| **Cite** | `cite-fullcite.sh` defaults `YAP_FOLIA_SUBREGION_CARVE=true` (ship profile). |
| **Patches** | **50** files (`0000`–`0057`) |

---

## After 0.0.0.1 — missing holder is a gap 0056 (2026-09-19)

Same ship version. Incremental Folia → `lib/yap-folia-26.2-lab.jar`.

| Area | Change |
|------|--------|
| **0056** | `checkNeighbour` / neighbour cache: a null holder is already-at-status (halo or mid-reap), not a crash. `CraftWorld.getChunkAt` does not NPE when `getChunk(load)` returns null. |
| **Bench** | Region-load snapshot skips unloaded chunks instead of `getChunkAt`+`load` into the corridor. |
| **Patches** | **49** files (`0000`–`0056`) |

---

## After 0.0.0.1 — player-loader cut load count 0055 (2026-09-19)

Same ship version. Incremental Folia → `lib/yap-folia-26.2-lab.jar`.

| Area | Change |
|------|--------|
| **0055** | After skipCutHole, scheduleChunkLoad walks the queued list size instead of maxLoadsThisTick (IOBE on region tick). |
| **Patches** | **48** files (`0000`–`0055`) |

---

## After 0.0.0.1 — cut FULL-neighbour holder 0054 (2026-09-19)

Same ship version. Incremental Folia → `lib/yap-folia-26.2-lab.jar`.

| Area | Change |
|------|--------|
| **0054** | Kept-edge FULL load no longer NPEs `setNeighbourFullLoaded` on a dropped corridor holder. Player send/unload skips missing holders and received-set misses. |
| **Patches** | **47** files (`0000`–`0054`) |

---

## After 0.0.0.1 — cut getChunk / player unload 0053 (2026-09-19)

Same ship version. Incremental Folia → `lib/yap-folia-26.2-lab.jar`.

| Area | Change |
|------|--------|
| **0053** | `getChunk(load)` on a registered cut returns loaded-or-null (getBlockState → air) instead of throwing. Player-loader unload sends forget without requiring a holder. |
| **Patches** | **46** files (`0000`–`0053`) |

---

## After 0.0.0.1 — cut missing chunkholder 0052 (2026-09-19)

Same ship version. Incremental Folia → `lib/yap-folia-26.2-lab.jar` (does **not** overwrite the GUI product jar). Bench home-leash no longer `getHighestBlockYAt` off-owner after a live carve.

| Area | Change |
|------|--------|
| **0052** | Ticket FULL-load beside an unloaded corridor threw `Missing chunkholder`. Treat a registered cut as an intentional gap. |
| **Bench** | Home-leash / bot send-home run on the entity thread; height sample only if this region owns the home chunk. |
| **Patches** | **45** files (`0000`–`0052`) |

---

## After 0.0.0.1 — fluid spread ownership 0051 (2026-09-19)

Same ship version. Incremental Folia → `lib/yap-folia-26.2-lab.jar` (does **not** overwrite the GUI product jar).

| Area | Change |
|------|--------|
| **0051** | After a live split, `FlowingFluid.spreadTo` can `setBlock` a neighbour shard. Defer onto the owning region (same pattern as 0015). Unloaded/cut chunks drop. Water still flows; carve stays on. |
| **Patches** | **44** files (`0000`–`0051`) |

---

## After 0.0.0.1 — unload/POI ownership 0050 (2026-09-19)

Same ship version. Incremental Folia → `lib/yap-folia-26.2-lab.jar` (does **not** overwrite the GUI product jar).

| Area | Change |
|------|--------|
| **0050** | `regioniser.removeChunk` no-ops if the section bit is already clear (holder-delete after corridor repair). POI search skips chunks the current TickThread does not own, so villager `AcquirePoi` cannot `ensureTickThread` a neighbour-region POI chunk after split. Carve/partition/async stay on. |
| **Patches** | **43** files (`0000`–`0050`) |

---

## After 0.0.0.1 — ticking-chunk null skip 0049 (2026-09-19)

Same ship version. Incremental Folia → `lib/yap-folia-26.2-lab.jar` (does **not** overwrite the GUI product jar).

| Area | Change |
|------|--------|
| **0049** | Mid-tick corridor unload can null entity-ticking list slots while `iterateTickingChunksFaster` still walks a captured size. Skip nulls; `tickChunk(null)` returns. |
| **Patches** | **42** files (`0000`–`0049`) |

---

Same ship version. Incremental Folia → `lib/yap-folia-26.2-lab.jar` (does **not** overwrite the GUI product jar).

| Area | Change |
|------|--------|
| **0048** | In-flight gap key is `~regionId`. Region 0 used `-regionId` (=0); register ignored it so `unloadIfCut` never dropped corridor holders (lvl=39, tickets empty). |
| **Check** | `./scripts/folia/smoke-contiguous-bar.sh` on the lab jar. |
| **Patches** | **41** files (`0000`–`0048`) |

---

Same ship version. Incremental Folia `:folia-server:jar` + `createPaperclipJar` → `lib/yap-folia-26.2-lab.jar` (does **not** overwrite the GUI product jar).

| Area | Change |
|------|--------|
| **0047** | Moonrise player chunk loader skips unpinned registered cut keys (load/gen/tick/send). Null FULL chunk no longer NPEs `updateQueues` after corridor evacuate. |
| **Check** | `./scripts/folia/smoke-contiguous-bar.sh` on the lab jar. Chassis fullcite previously died here (`RegionizedPlayerChunkLoader.updateQueues` chunk null). |
| **Patches** | **40** files (`0000`–`0047`) |

---

Same ship version. Incremental Folia `:folia-server:jar` only (does **not** overwrite the GUI product jar). Lab paperclip → `lib/yap-folia-26.2-lab.jar`.

| Area | Change |
|------|--------|
| **0046** | Packed-spawn cuts stay. Ticket/unload use this-world corridor **keys** (not the spawn AABB, not another world's cut). Pin loaded nether/end portal chunks; portal-travel tickets skip the clamp; `unloadIfCut` no longer MAX+1s a pinned holder. |
| **Patches** | **39** files (`0000`–`0046`) |

Swap the running Folia jar to pick this up; an already-cut spawn does not need a recarve for dest search.

---

## After 0.0.0.1 — contiguous-bar hold 0044/0045 (2026-09-18)

Same ship version. Lab paperclip still → `lib/yap-folia-26.2-lab.jar`.

| Area | Change |
|------|--------|
| **0044** | Cut AABB, on-thread gap maintain, RTQ requeue, portal couple by chunk, save wait. |
| **0045** | Ticket-level clamp + no sync-load into the cut so a live contiguous split holds under product view-distance. |
| **Check** | `./scripts/folia/smoke-contiguous-bar.sh` PASS: `splits=1`, `force_partitions=1`, `gap_bands=1`, `ticking_regions=2` (`pulses_ran=0`). |
| **Patches** | **38** files (`0000`–`0045`) |

---

## After 0.0.0.1 — GitHub prerelease CDN (2026-09-18)

Same ship version (no product bump). Tag **`0.0.0.1`** is a GitHub **prerelease**.

| Area | Change |
|------|--------|
| **Packs** | Default `resource-pack-url` is `/releases/download/0.0.0.1/{file}` because `/releases/latest` ignores prereleases. |
| **Install** | Linux/windows zips, suites, `yapcore-default.zip` / `.mcpack`, and `client_mods.zip` attach to that tag. |
| **Existing installs** | Seed will not overwrite `resource-pack-url`. Point it at the tag URL. The deleted **1.0.0.0** release is not a fallback. |

Rebuild: `gradle publishReleasesFolder -PyapGameplay=true` then `gh release upload 0.0.0.1 … --clobber`.

---

## After 0.0.0.1 — spawn ownership 0042 (2026-09-18)

Same ship version (no product bump). Incremental Folia paperclip → `lib/yap-folia-26.2.jar`. Pin remains **`14b7fee`**.

| Area | Change |
|------|--------|
| **0042 villager brain** | Folia #446: `updateActivityFromSchedule` is skipped while `EntityType.create(DIMENSION_TRAVEL)` runs on the origin region thread; retry on the owning tick. |
| **0042 end vehicle** | Folia #453: END→overworld for a vehicle uses the riding player's respawn, not world spawn. |
| **Patches** | Then **35** files (`0000`–`0042`); later **`0043`** on the same pin (see above). |

Does not change the regionizer. Relocate + `addChunk` refuse landed in **`0041`**. Rebuild: incremental `vendor/folia/work` `:folia-server:createPaperclipJar` → `lib/yap-folia-26.2.jar`.

---

## After 0.0.0.1 — packed-spawn regionizer cut 0041 (2026-09-18)

Same ship version (no product bump). Incremental Folia paperclip → `lib/yap-folia-26.2.jar`. Pin remains **`14b7fee`**.

| Area | Change |
|------|--------|
| **0041 cut** | Native regionizer cut (`YapRegionizerGap`): skip empty-section create, BFS will not jump the band, PLAYER/sim tickets will not refill the corridor. |
| **Thin gap** | 1-section hole is Folia-legal once the cut is registered (was 3 sections). |
| **Grid** | Ship `folia-grid-exponent=3` (8-chunk sections) so a VD=10 spawn blob has left / hole / right. |
| **Knobs** | `-Dyap.folia.regionizer-cut=true`, `-Dyap.folia.regionizer-thin-gap=true` (ship on) |
| **Patches** | **34** files (`0000`–`0041`) |

Stock Folia and Canvas still cannot split a packed spawn. YaP can, when the blob spans ≥3 sections and the corridor is emptied of non-player entities. Players standing in the strip still pin that chunk. Rebuild: incremental `vendor/folia/work` `:folia-server:createPaperclipJar` → `lib/yap-folia-26.2.jar`.

---

## After 0.0.0.1 — Folia-itself patches 0038–0040 (2026-09-18)

Same ship version (no product bump). Incremental Folia paperclip → `lib/yap-folia-26.2.jar`. Pin remains **`14b7fee`**.

| Area | Change |
|------|--------|
| **0038 teleport events** | Folia #490: `teleportAsync` fires `PlayerTeleportEvent` / `EntityTeleportEvent`; async nether/end portals fire `PlayerPortalEvent` / `EntityPortalEvent` before the entity is yanked. Move-event rewinds stay event-silent. |
| **0039 map autosave** | Folia #505/#506: maps live on `MinecraftServer#getDataStorage()`; autosave follows that storage once per interval on the global tick. |
| **0040 debug CME** | Folia #472 / PR 499: players cannot register debug subscriptions; `ServerDebugSubscribers.tick` does not mutate a shared HashMap from region threads. |
| **Patches** | **33** files (`0000`–`0040`) |

Build: incremental `vendor/folia/work` `:folia-server:createPaperclipJar` → `lib/yap-folia-26.2.jar`. Full rebuild still `./scripts/folia/build-yap-folia.sh`.

---

## After 0.0.0.1 — Folia-itself patches 0034–0037 (2026-09-18)

Same ship version (no product bump). Incremental Folia paperclip → `lib/yap-folia-26.2.jar`. Pin remains **`14b7fee`**.

| Area | Change |
|------|--------|
| **0034 tickets** | Last real ticket on the owning region thread drops immediately (no UNKNOWN 1-tick). Scheduler/RTQ/teleport holds are `FLAG_LOADING` only. `processTicketUpdates` after hold add/remove. |
| **0035 ownership** | Folia PRs 491/495/504: TickThread on AI sensors / delayed leash; `EnderDragon.syncPartPositions` before lookup registration and after dimension transform. |
| **0036 portal couple** | Folia #469: nether/overworld portal pairs; after origin `markNotTicking`, steal a due partner tick onto this OS thread. |
| **0037 split** | FoliaRegionScheduler / RTQ split requeue instead of NPE; entity split uses `YapRegionSplit.target`. |
| **Knobs** | `-Dyap.folia.ticket-hygiene=true`, `-Dyap.folia.portal-couple=true` (ship on) |
| **Patches** | Then **30** files (`0000`–`0037`); later **`0038`–`0040`** on the same pin (see above). |

Build: incremental `vendor/folia/work` `:folia-server:createPaperclipJar` → `lib/yap-folia-26.2.jar`. Full rebuild still `./scripts/folia/build-yap-folia.sh`.

---

## v0.0.0.1 — honest reset: real Folia splits, no second clock (2026-09-18)

Product version **0.0.0.1** (reset from the **1.0.0.0** line). Gradle `version`, every first-party `plugin.yml` / `link-plugin.json`, and release trees share this number.

| Area | Change |
|------|--------|
| **Bar** | Folia’s **regionizer holding under real splits** (contiguous hot region → empty-buffer cut → independent shards) **without** a YaP phase clock. Same-tick BLOCKS lockstep is not the bar. |
| **Status** | Ship path: partition + carve + native regionizer-cut (`0041`) + contiguous-bar relocate (`0043`) + ticket-gap hold (`0045`). Lab check: `./scripts/folia/smoke-contiguous-bar.sh`. |
| **Knobs** | Partition, carve, and aligned microticks stay **on** as intent. Microticks are optional coherence, not a second world clock. Lab gap/threshold/probe stay lab-only. |
| **Docs** | README, whitepaper §3.4/§4/§13, [YAP_FOLIA_PATCHES.md](../folia/YAP_FOLIA_PATCHES.md), [RELEASES.md](RELEASES.md), [SECURITY.md](../../SECURITY.md) |

Build: `gradle publishReleasesFolder -PyapGameplay=true` → `releases/0.0.0.1/`. Tag **`0.0.0.1`** as a **prerelease**. Packs use `/releases/download/0.0.0.1/{file}`. The previous GitHub release **`1.0.0.0`** was deleted, so `/releases/latest` stays empty until a non-prerelease exists.

---

## After 1.0.0.0 — Region cut lab (pre-gap) + ship-on partition/carve/waves (2026-09-18)

Same ship version on the old **1.0.0.0** line (no product bump then). Rebuild Folia incrementally, then `gradle publishReleasesFolder -PyapGameplay=true`.

| Area | Change |
|------|--------|
| **Region cut (lab)** | Pre-gapped force-partition + `RegionizedWorldData.split` (0032) + BLOCKS-tagged pulse; `wave_timeouts=0`. **Not** a live contiguous carve. |
| **0026 waves** | Per-world epoch keys; leave epoch at end of region tick; wait only when same-world peers arrived |
| **Ship defaults** | `folia-subregion-partition=true`, `folia-subregion-carve=true`, `folia-aligned-microticks=true` |
| **0018 carve** | Split to `YapCorridorCarver` / `YapCorridorEntities` / `YapCorridorPlanner` / `YapCorridorUnload` (≤500); Moonrise `processUnloads` |
| **Patches** | Then **26** YaP files on pin `14b7fee` (6 Sep). `0032` repairs YaP force-partition split; `0033` is the lab probe. **Not** a Folia regionizer backport. Later **`0034`–`0040`** landed on the same pin (see above). |
| **Domain ≤500** | Bedrock join/play/session extracts; Folia corridor helpers |
| **Docs** | [YAP_FOLIA_PATCHES.md](../folia/YAP_FOLIA_PATCHES.md) · [README.md](../../README.md) · [DEFAULTS.md](DEFAULTS.md) · whitepaper §3.4 / §4 |

Build: incremental `vendor/folia/work` `:folia-server:createPaperclipJar` → `lib/yap-folia-26.2.jar`, then `gradle publishReleasesFolder -PyapGameplay=true` → `releases/1.0.0.0/`. Upload with `--clobber` per [RELEASES.md](RELEASES.md).

---

## After 1.0.0.0 — Creative climate, NPC Folia spawn, reach, Bedrock, ultrawide (2026-09-17)

Same ship version (no product bump). Rebuild with `gradle publishReleasesFolder -PyapGameplay=true`.

| Area | Change |
|------|--------|
| **YaPNpcs** | Folia region-thread spawn (no global `spawnEntity` NPE); `server-id: default` falls back to fleet `yap-server-id.txt`; shop professions; no sync teleport |
| **YaPWorld** | Creative-hub climate: always noon, no weather cycle, no natural mobs (`climate.enabled`, auto-on for instance `creative`) |
| **YaPEssentials** | `block-reach` (survival 6.5 / creative 8); optional hub spawn-on-join |
| **YaPPortals** | Pad fill only replaces air / portal / glass — does not overwrite signs or builds |
| **Fleet** | Default **creative** instance; flat swap sets peaceful + no-spawn; per-instance PlayerData inventory profile; **factions** backend (`id=factions`) with YaPFactions enabled |
| **Dashboard** | Kit item fields + YaPItems catalog; fleet world-swap-flat; plugin hints for reach/climate |
| **YaP Link / Bedrock** | JE→BE block remapper, dimension/join, entity list, Bungee Connect pending |
| **yap-ultrawide** | Hor+ HUD shares world frustum; viewmodel + view-bob scaled so placement matches the crosshair |
| **Docs** | [YAPWORLD.md](../plugins/YAPWORLD.md) · [COMMANDS.md](../ops/COMMANDS.md) · [PORTALS.md](../network/PORTALS.md) · [CLIENTS_AND_PACKS.md](../network/CLIENTS_AND_PACKS.md) · [WEB_DASHBOARD.md](../ops/WEB_DASHBOARD.md) |

Build: `gradle publishReleasesFolder -PyapGameplay=true` → `releases/1.0.0.0/`. Upload with `--clobber` per [RELEASES.md](RELEASES.md).

---

## After 1.0.0.0 — NPC shops UX + dashboard Shops + fleet polish (2026-09-17)

Same ship version (no product bump). Rebuild with `gradle publishReleasesFolder -PyapGameplay=true`.

| Area | Change |
|------|--------|
| **YaPPlayerData / YaPNpcs** | Built-in shop presets (`weapons`/`armor`/`tools`/`food`/`blocks`/`redstone`/`crafting`/`enchants`/`farming` aka Tractor Supply / `fishing` aka Tackle Shack); unlimited stock; buyback ≈38%; enchanted buy-only |
| **In-game shop GUI** | One icon per item — left-click buy / right-click sell; quantity picker with running totals |
| **`/npc shop`** | `apply` / `presets` / `setitem` (buy+sell upsert) / `setoffer` / `clearoffers`; `/npc setname` / `move` |
| **Dashboard** | **Shops** tab — one row per item (Buy $ + Sell $); `/api/shops` (`setitem`, presets, list) |
| **YaPPortals** | Arrival lands at destination spawn; catalog mirror + wand polish |
| **YaPRegions** | `/region worldborder` fits vanilla border to region XZ AABB |
| **YaPItems** | Fleet catalog propagate + watch on create (network-wide custom items) |
| **Link / PlayerData** | Faster session unlock on soft-switch / hub transfers; lock release on quit |
| **Fleet** | Console command dispatch captures Folia JSON; heal broken lobby `plugins` symlink |
| **Domain ≤500** | Split SoftSwitch handlers, `NpcTraderTradeGui`, `NpcShopCatalogOps` |
| **Docs** | [PLAYERDATA.md](../data/PLAYERDATA.md) · [WEB_DASHBOARD.md](../ops/WEB_DASHBOARD.md) · [COMMANDS.md](../ops/COMMANDS.md) · [PORTALS.md](../network/PORTALS.md) · [YAPITEMS.md](../plugins/YAPITEMS.md) · [GAMEPLAY.md](../gameplay/GAMEPLAY.md) |

Build: `gradle publishReleasesFolder -PyapGameplay=true` → `releases/1.0.0.0/`. Upload with `--clobber` per [RELEASES.md](RELEASES.md).

---

## After 1.0.0.0 — Portals colors + hub region flags (2026-09-17)

Same ship version (no product bump). Rebuild with `gradle publishReleasesFolder -PyapGameplay=true`.

| Area | Change |
|------|--------|
| **YaPPortals** | Walk-through colored pads (LIGHT + dye particles) — no solid glass; `/portal setcolor` / create `[color]` |
| **YaPRegions** | Hub flags: `damage`, `use`, `hunger`, `farmland-trample`, `item-frame`, `armor-stand`, `leaf-decay`, `pistons`, `vehicle-place`/`destroy`; `/region gamemode`; immediate apply + OP-bypass tip |
| **Claims** | Softened doors/plates via `use` (parkour-friendly); shared `damage` flag |
| **YaPAdmin** | Give menu armor/weapon/tool gear kits |
| **YaPPerms** | VIP keep-inventory starter grant + backfill |
| **YaPNpcs** | `server:` / `/npc setserver` → YaPPortals Connect |
| **Docs** | [PORTALS.md](../network/PORTALS.md) · [GAMEPLAY.md](../gameplay/GAMEPLAY.md) regions |

Build: `gradle publishReleasesFolder -PyapGameplay=true` → `releases/1.0.0.0/`. Upload with `--clobber` per [RELEASES.md](RELEASES.md).

---

## After 1.0.0.0 — YaPPortals fleet transfers (2026-09-14)

| Area | Change |
|------|--------|
| **YaPPortals** | CORE+NETWORK default plugin, **on** — walk-through portals → Link `Connect` (`yap-portals.jar`) |
| **YaPNpcs** | `server:` / `/npc setserver` soft-dep on YaPPortals |
| **Link** | `/hub` still from `yaplink-server-selector`; portals are the Folia UX layer |
| **Docs** | [PORTALS.md](../network/PORTALS.md) |

---

## After 1.0.0.0 — Link /hub command intercept (2026-09-16)

| Area | Change |
|------|--------|
| **YaP Link** | Play relay now dispatches registered plugin commands (`/hub`, `/server`) from JE `chat_command` — previously registered but never intercepted (fell through to Folia) |
| **Transfer packet** | Play `transfer` id for 26.2/776 is `0x81` (was stale `0x7A` → client “failed to decode packet”); localhost clients Transfer to `127.0.0.1` |
| **system_chat** | 26.2 expects NBT text components (not JSON strings) — fixes `/server` DecoderException on `minecraft:system_chat` |

---

## After 1.0.0.0 — CI Folia smoke / ≤500 domain splits (2026-09-14)

| Area | Change |
|------|--------|
| **Folia CI** | Nightly/main `folia-fork.yml` runs boot smoke after jar build (`SKIP_SMOKE` removed) |
| **Domain ≤500** | Split FleetService, ControlPanel, DashboardLinkSnapshot, AdminMenusOps, ProtectServiceImpl, TailorServiceImpl |

---

## After 1.0.0.0 — plugin ship gaps / combat XP / defaults (2026-09-14)

| Area | Change |
|------|--------|
| **Release / dist** | `yap-tailor.jar` + `yap-bedrock-blocks.jar` in `assembleRelease` + `assemblePluginDist` |
| **YaPItems ↔ YaPSkills** | Gear-only `CombatService` no longer suppresses Skills combat XP (`ownsCombatXp()`) |
| **Defaults** | Seed `YaPTailor/` + `YaPTebex/`; Tailor jar default clears public skin-host URL |
| **YaPNpcs** | `unlock_recipe` / `teleport_unlock` warn honestly (no fake `yapmmo` dispatch) |

---

## After 1.0.0.0 — QoL defaults / Protect sessions / SNAPPY / map (2026-09-14)

| Area | Change |
|------|--------|
| **YaP-QoL** | `config/defaults/plugins/YaP-QoL/` seeded; dashboard blurb is product-default (not opt-in) |
| **YaPSkills** | Removed display-only combat level (PAPI/menu/calculator) |
| **YaPProtect ↔ YaPWorld** | WorldEdit apply batches log with `edit_op_id`; `/yapprotect lookup\|rollback session <uuid>` |
| **Bedrock SNAPPY** | Link + chassis inflate method=1 (snappy-java) instead of dropping batches |
| **YaPMap** | Config fallbacks aligned; [MAP.md](../ops/MAP.md) restored |
| **GameplayKnobs / Folia 0025** | Crop accelerate (`extraCropGrowthAttempts`) wired in patch + work tree |
| **Defaults docs** | Skills/LeveledMobs/QoL/Map tiers match shipped YAML |

---

## After 1.0.0.0 — YaP-QoL / fleet ensure preserve (2026-09-14)

Same ship version (no product bump). Rebuild **linux** + **windows** + suites with
`gradle publishReleasesFolder -PyapGameplay=true`.

| Area | Change |
|------|--------|
| **YaP-QoL** | Timber axe + area excavator (3×3/6×6/9×9); VIP kit; staff `/yapqol` + admin menu — [PLUGINS.md](../plugins/PLUGINS.md) |
| **Defaults** | `yap-items.jar` + `yap-qol.jar` seed on every fleet instance and ship in the release box (not slim-stripped) |
| **Fleet ensure** | Start/ensure **merges** `ops.json` (keeps in-game OPs); never deletes/overwrites installed plugin jars or `.jar.disabled` |
| **GUI** | Opening Control GUI does not rewrite instance trees — layout prep runs on create / Start / enable-fleet only |
| **Packaging** | `assembleRelease` / `assembleGameplaySuite` / `assemblePluginDist` include `yap-qol.jar` |

Build: `gradle publishReleasesFolder -PyapGameplay=true` → `releases/1.0.0.0/`. Upload GitHub assets with `--clobber` per [RELEASES.md](RELEASES.md).

---

## After 1.0.0.0 — Fleet GUI / dashboard / branding (2026-09-14)


Same ship version (no product bump). Rebuild **linux** + **windows** packages with
`gradle publishReleasesFolder -PyapGameplay=true` so operators get current chassis + web assets.

| Area | Change |
|------|--------|
| **Fleet defaults** | New instances seed **CORE+NETWORK** plugins; empty lobby/plugins heal on enable/list |
| **Fleet GUI** | Fleet-first rail: **YaP Link** + game servers; Plugins-first per server; Connect under Link |
| **Settings persist** | Instance MOTD / max-players / view-distance no longer clobbered on ensure/start |
| **Web branding** | Favicon / login / sidebar use `branding/yapcore-icon.png` + `yapcore-mark.png` |
| **Dashboard Start/Stop** | Badge and controls follow **fleet game servers**, not chassis DualStack alone |
| **Docs / DB copy** | YaPDB wording: MariaDB/MySQL · PostgreSQL · SQLite (not MariaDB-only) |

Build: `gradle publishReleasesFolder -PyapGameplay=true` → `releases/1.0.0.0/yapcore-release-{linux,windows}.zip`.

---

## After 1.0.0.0 — Packs / Link MOTD / GUI launch (2026-09-14)

Same ship version (no product bump). Ops-facing correctness:

| Area | Change |
|------|--------|
| **Default JE pack** | Faithful **64× held items restored** (no longer stripped to vanilla sprites) — rebuild `yapcore-default.zip` and upload GitHub release assets |
| **Pack CDN** | Product path is GitHub `releases/latest/download/{file}` — do not advertise a dead public-host `/pack/` URL |
| **Link MOTD max** | Aggregate max = **sum of UP backends only**; `max-players` in `link.properties` is a ceiling, not a floor |
| **`gui.sh`** | Fast launch by default (existing `yapcore.jar`); pass `--build` after chassis/GUI source changes |

Rebuild Link: `gradle :yap-link-native:shadowJar` → copy to root `yap-link.jar`. Publish packs from `releases/1.0.0.0/` or `resourcepacks/`.

---

## After 1.0.0.0 — Physics sub-steps (2026-09-13)

Same ship version (no product bump). Internal movement/combat integration rate:

| Area | Change |
|------|--------|
| **Physics sub-steps** | Patch `0031` — `YapTravelSubstep` + `YapMoveSubstep` (gravity/friction scaled; plugin tick unchanged) |
| **Ship defaults** | `folia-physics-substeps=true`, `folia-physics-substep-count=4`, `folia-physics-substep-min-move=0.02` |
| **Domain** | Helpers ≤500 lines (`YapPhysicsSubsteps` / move / travel) |
| **Docs** | YAP_FOLIA_PATCHES / SOAK / REAL_GAINS — feel vs capacity |

Build: `./scripts/folia/build-yap-folia.sh`. Rollback: `folia-physics-substeps=false`.

---

## After 1.0.0.0 — Aligned micro/sub-ticks (2026-09-13)

Same ship version (no product bump). Real YaP-Folia micro/sub-tick phases across regions:

| Area | Change |
|------|--------|
| **Aligned microticks** | Patches `0026`–`0030` — `YapMicroPhase` waves + soft epoch barriers + universal RTQ phase tagging |
| **Ship defaults** | `folia-aligned-microticks=true`, `folia-micro-phases=4`, `folia-tick-wave-max-wait-ms=2` |
| **Docs** | README / YAP_FOLIA_PATCHES — AI time-slice vs aligned phases |
| **Cite** | Re-run `./scripts/lifecycle/yapctl cite-fullcite` with `knob_aligned_microticks` disclosed |

Build: `./scripts/folia/build-yap-folia.sh` · smoke: `YAP_FOLIA_ALIGNED_MICROTICKS=true ./scripts/folia/smoke-folia.sh`.

---

## After 1.0.0.0 — Docs / AI transparency (2026-09-13)

Same ship version (no product bump). Documentation refresh for GitHub and operators:

| Area | Change |
|------|--------|
| **README** | Product surface includes Link-native Bedrock, Bedrock-feel, Tailor / presence / blocks |
| **AI disclosure** | [AI_TRANSPARENCY.md](AI_TRANSPARENCY.md) — AI-assisted development under human accountability |
| **Whitepaper** | **v0.5** — native join, parity, ≤500 domain gate, AI note |
| **Indexes** | Wiki / docs README / LICENSING / SECURITY / CONTRIBUTING / plugins lists updated |
| **PDFs** | `./scripts/docs/export-docs-pdf.sh` priority set expanded (local / gitignored) |

---

## After 1.0.0.0 — Bedrock-feel + native join + ≤500 domains (2026-09-13)

Same ship version (no product bump). Crossplay join path, Bedrock-feel pillars, and domain hygiene:

| Area | Change |
|------|--------|
| **Native join** | Link-native Bedrock join path — [YAP_LINK.md](../network/YAP_LINK.md) · [CROSSPLAY.md](../network/CROSSPLAY.md) |
| **Bedrock-feel** | Parity catalogs/matrix (phases 0–6), movement/emotes/skin glue; Tailor + **yap-presence** / **yap-blocks** clients — [BEDROCK_FEEL_PARITY.md](../product/BEDROCK_FEEL_PARITY.md) |
| **Domain ≤500** | Chassis + first-party oversize classes split to same-package helpers; `gradle checkDomainLineLimits` green |
| **client_mods** | Zip now includes **yap-presence** + **yap-blocks** alongside visuals/bag/staff/ultrawide |

Docs: [CROSSPLAY.md](../network/CROSSPLAY.md) · [CLIENTS_AND_PACKS.md](../network/CLIENTS_AND_PACKS.md) · [RELEASES.md](RELEASES.md).

Build: `./scripts/parity/smoke-bedrock-feel.sh` · `./scripts/packs/build-yap-client-render.sh` · `gradle publishReleasesFolder -PyapGameplay=true` · copy shadow Link jar → repo-root `yap-link.jar`.

---

## After 1.0.0.0 — World tools + Bedrock pack CDN (2026-09-08 evening)

Same ship version (no product bump). Operator build tools + crossplay pack path:

| Area | Change |
|------|--------|
| **YaPAdmin World tools** | Single hub tile for world edit + schematics + live paste preview (confirm / move / rotate / flip / undo); Bedrock form hub entry |
| **YaPWorld schem preview** | Interactive **Rotate 90°** (shift = CCW) and **Flip**; outline colour/yaw tip; `//schem rotate` / `flip`; `//rotate` prefers active preview |
| **Bedrock packs** | Offer GitHub `releases/latest/download/{file}` (same CDN as JE); pack handshake defers JOIN/forms until after StartGame; mcpack `min_engine_version` pinned to 1.21.60 |
| **yap-staff 1.0.30** | More… → World & build links for schematics + admin World tools |

Docs: [WEB_DASHBOARD.md](../ops/WEB_DASHBOARD.md) · [YAPWORLD.md](../plugins/YAPWORLD.md) · [CLIENTS_AND_PACKS.md](../network/CLIENTS_AND_PACKS.md).

Build: `./scripts/packs/build-yap-client-render.sh` · `gradle publishReleasesFolder -PyapGameplay=true` · restart Folia after jar/plugin swap.

---

## After 1.0.0.0 — YaPItems + staff create + shaders (2026-09-08)


Same ship version (no product bump). Custom items + client polish:

| Area | Change |
|------|--------|
| **YaPItems** | New gameplay plugin: YAML registry, multi-ability keybinds, furniture, recipes, kits `yap-item:`, create/delete/cooldown CLI — [YAPITEMS.md](../plugins/YAPITEMS.md) |
| **YaPAdmin** | Hub **Custom items** create wizard (categorized abilities, glow/unbreakable, enchant picker, break volume, potion options) |
| **yap-staff 1.0.27** | Custom items + enchant picker; `yap:staff` payload channel for long create/edit (avoids chat_command kick); `--nameb64` / `--abilities` |
| **YaP Shaders** | Glass ≠ water; waterfall cascade path; wind on leaves/grass/vines only (log builds stay still) |
| **Pack / kits** | `yap-items` overlay in `yapcore-default`; kit YAML `yap-item:` rows |

Docs: [YAPITEMS.md](../plugins/YAPITEMS.md) · [WEB_DASHBOARD.md](../ops/WEB_DASHBOARD.md) · [CLIENTS_AND_PACKS.md](../network/CLIENTS_AND_PACKS.md) · [yap-staff/README.md](../../client/yap-staff/README.md) · [yap-shaders/README.md](../../client/yap-shaders/README.md).

Build: `gradle installGameplayDefaults` · `./scripts/packs/build-yap-client-render.sh` · full Folia restart after jar swap.

---

## After 1.0.0.0 — staff client + pack/Via polish (2026-09-07)

Same ship version (no product bump). Operator / client staff UX:

| Area | Change |
|------|--------|
| **yap-staff 1.0.4** | Full Fabric staff GUI (players, give, trolls, mod, economy, **ranks/perms**, deep links); scrollable + window-scaled layout; searchable player picker returns to calling tool; **fix empty scroll body** (`arrangeElements` before `setMaxHeight`) |
| **YaPAdmin** | Trolls / give / money subcommands; economy deposits via `PlayerDataService` on the entity region thread (Folia-safe) |
| **yap-bag 1.0.2** | Mixins target `AbstractContainerScreen` for MC 26.2; page tabs above 6/5-row panel; **`yap:bag` HELLO** → server omits bottom item-nav (45-slot GUI) |
| **Via packs** | Forward optional Paper resource-pack prompts to modern JE (no longer auto-accept-only when unforced) |
| **YaPTab** | Sidebar/footer resolve `{balance}` and `${balance}` |
| **Packs / scripts** | GitHub Releases pack offer sync; Control Panel / `gui.sh` home resolution |

Docs: [WEB_DASHBOARD.md](../ops/WEB_DASHBOARD.md) · [CLIENTS_AND_PACKS.md](../network/CLIENTS_AND_PACKS.md) · [yap-staff/README.md](../../client/yap-staff/README.md) · [yap-bag/README.md](../../client/yap-bag/README.md).

Build clients: `./scripts/packs/build-yap-client-render.sh`. Rebuild admin/tab: `gradle :admin-plugin:jar :tab-plugin:shadowJar`. Republish trees: `gradle publishReleasesFolder -PyapGameplay=true`.

---

## v1.0.0.0 — product polish P0/P1 (2026-09-05)

Same ship version (no bump). Operator-facing consistency and ops reload parity:

| Area | Change |
|------|--------|
| **Shared messages** | `yap-messages-api` — Adventure text, permission errors with nodes, `YapConfigReload` / `YapHelp` — [PLUGINS.md](../plugins/PLUGINS.md) |
| **Defaults pack** | Full `config/defaults/plugins/` coverage for CORE+NETWORK + gameplay seeds (BedrockUI / FoliaBridge / WorldEdit shim N/A) — [DEFAULTS.md](DEFAULTS.md) |
| **Bedrock hubs** | `/menu`, kits/homes/warps, ranks, admin forms via YaPBedrockUI (JE keeps chests) — [CROSSPLAY.md](../network/CROSSPLAY.md) |
| **Ops / dashboard** | Catalog reload parity (admin, pregen, floodgate, dungeons, conquest, …); DB-not-ready / profile-loading UX; invalid YAML numbers → HTTP 400 |
| **Factions / Conquest** | Survival guild polish + opt-in YaPConquest chunk land (still `enabled: false` by default) — [GAMEPLAY.md](../gameplay/GAMEPLAY.md) · [GAMEPLAY.md](../gameplay/GAMEPLAY.md) |

Rebuild release trees: `./scripts/folia/build-yap-folia.sh && gradle publishReleasesFolder -PyapGameplay=true`.

---

## v1.0.0.0 — client visuals refresh (2026-09-05)

Optional Fabric / pack polish on the same ship version (no version bump):

| Area | Change |
|------|--------|
| **Release asset** | Upload **`client_mods.zip`** (yap-visuals + yap-bag + yap-staff + yap-ultrawide) |
| **YaP Shaders** | Multi-dir Gerstner water; weather-driven species foliage wind; softer distance fog; leaf cutout path |
| **Default pack** | Denser Faithful-based leaves (`strict_cutout`); water/weather overlay refresh |
| **yap-ultrawide** | Separate **21:9** and **32:9** Hor+ profiles (`match_16_9` / `match_21_9` / `fixed_hfov` + HFOV cap) |
| **yap-bag** | Screen mixins updated for MC 26.2 chest / inventory layout — use **1.0.2+** (HELLO → no item-nav row) |
| **yap-staff** | Esc / **R** full Staff GUI (scroll/scale, ranks, searchable pick) — **1.0.4+** |

Build: `./scripts/packs/build-yap-client-render.sh` · `./scripts/packs/build-default-resourcepack.sh`.
Docs: [CLIENTS_AND_PACKS.md](../network/CLIENTS_AND_PACKS.md), [RELEASES.md](RELEASES.md).

---

## v1.0.0.0 — 2026-09-02

First shippable **YaP-Folia** network product release. This is the starting version —
all first-party artifacts stay on **1.0.0.0**. Native plugin stack, first-party Java +
Bedrock crossplay, YaP Link proxy, web dashboard, and the operator SMP commands below.

### Highlights

| Area | What shipped |
|------|----------------|
| **Game authority** | YaP-Folia 26.2 (`folia-jar-source=build`) — regionized tick, not stock Paper/Folia |
| **Crossplay** | First-party ViaBackwards-class JE (1.20.2+) + Geyser-class Bedrock — **no Via\* / Geyser jars** |
| **Network** | YaP Link native proxy, dual-stack gateway, Floodgate-class identity; offline-mode Mojang skins |
| **Plugins** | Full CORE+NETWORK stack: perms, chat, moderation, essentials, claims, regions, protect, world, tab, discord, guard, map, NPCs, … |
| **Operator SMP** | `/bag` (3/5/7/9 pages), `/gm` + `/item`, `/eco`, per-rank name/chat colors, `/yapmod seen` |
| **Gameplay (opt-in)** | Thin skills (mining/woodcutting/strength), disasters, stacker, factions, **YaP Encyclopedia** (Purpur-inspired knobs). Gameplay jars default **off** until enabled. |
| **Ops** | Web dashboard (`:8080`) — ranks, kit builder, plugin YAML editors, Swing GUI, seed defaults, MariaDB/Postgres Docker packages, SQLite single-node |
| **Clients (optional)** | Fabric 26.2 under [`client/`](../../client/): **yap-visuals** (Sodium+Iris+shaders in one jar), **yap-bag**, **yap-staff**, **yap-ultrawide** — vanilla and Bedrock stay supported without them |
| **Packs** | `yapcore-default` (Faithful + skies) |
| **Integrations** | Optional fetch scripts for **Grim AC** and **Tebex** (GPLv3, not bundled by default) |

### Protocol & crossplay

- **JE matrix 4/4 spawn** under compression (1.20.4, 1.21.1, and pinned mid bands).
- **Optional packs on Via** — modern JE (≥1.20.2) get the Folia login Yes/No prompt; Via no longer auto-acks optional packs (that hid downloads). Mid-band clients still auto-ack.
- **Bedrock 1.21.50** — RakNet login, spawn, dig/place, chat, commands; play-depth smoke green.
- **Paper column stream** default for Bedrock terrain (flat chunks opt-in only).
- **G.33** placed-skull block-actor sync + item-in-hand SkullOwner Name NBT; full profile-hash textures remain Stretch.
- **Specialty Bedrock containers** — anvil, smithing, loom, stonecutter, cartography open via Paper-backed P4.6 bridge; **recipe pick** wired for stonecutter/loom/smithing/cartography (CRAFT_RECIPE_OPTIONAL); anvil rename via FILTER_TEXT (deploy chassis after soak).
- Limitations documented in [CROSSPLAY.md](../network/CROSSPLAY.md).

### Ops & configuration

- **`config/defaults/`** + `./scripts/setup/seed-defaults.sh` — LAN-friendly first boot.
- **[SECRETS.md](SECRETS.md)** — where owners set MariaDB passwords, dashboard token, Discord webhooks, forwarding secret.
- **Web dashboard** — ranks (name/chat colors), kit builder, players, regions, guard, map, Discord, Link, packs, plugin YAML editors.
- **Release zips** ship defaults/examples only — live operator tokens are never packaged.
- **Privacy / terms templates** for public server operators: [PRIVACY_POLICY.md](PRIVACY_POLICY.md), [TERMS_OF_USE.md](TERMS_OF_USE.md).

### Tier 4 & roadmap closure

Completion backlog **Tiers 1–4 Done** (with live-soak caveats):

- Tier 1: YaPTab sidebar, claim flags, admin menus.
- Tier 2: Dashboard polish, web map, Discord relay, tune docs.
- Tier 3: TAB cross-server sync, optional skills/disasters/stacker.
- Tier 4: Protocol phases 4A→4F — dual-stack join, Bedrock play depth, limitation docs.

Roadmap phases **8–17 Done** (dashboard, TAB, Discord, regions, map, guard, NPCs, Bedrock depth, release polish).

### Build & install

```bash
./scripts/folia/build-yap-folia.sh
gradle publishReleasesFolder
# → releases/1.0.0.0/yapcore-release-linux.zip (+ windows, suite zips)

cd releases/1.0.0.0/yapcore-release/linux
cp deploy/mariadb/.env.example deploy/mariadb/.env   # edit passwords
./configure-db.sh --server-id lobby
./start.sh --fg
```

Gameplay (skills, disasters, stacker, knobs) is included when `-PyapGameplay=true`. Slim CORE+NETWORK: `gradle assembleRelease -PyapGameplay=false`.

### Breaking / migration notes

| From | To |
|------|-----|
| Stock Folia / Paper as default | **YaP-Folia** — rebuild with `./scripts/folia/build-yap-folia.sh` |
| ViaVersion + Geyser + Floodgate jars | **Remove** — use built-in protocol stack |
| LuckPerms / EssentialsX / TAB / DiscordSRV | **Optional** — native YaP plugins cover typical SMP |
| Tracked `folia-kernel/server.properties` | **`server.properties.example`** — live file gitignored; seeded on first start |
| Bench JSON in repo | **Removed** — results are local/gitignored |

### Known limitations (honest)

Not blockers for release; documented for operators:

- **Retail Xbox / full inv UI** — validate with real clients before marketing “full play depth.”
- **ViaRewind 1.8 play depth** — out of product scope.
- **Bedrock specialty UI** — recipe pick wired (stonecutter/loom/smithing/cartography); anvil rename FILTER_TEXT pending chassis deploy after soak. Retest on Bedrock before marketing full play depth.
- **Sounds / particles** on older JE clients — same class of issues as ViaBackwards; see limitations doc.
- **YaPGuard** — lightweight movement heuristics only; **competitive / PvP requires Grim** (`./scripts/plugins/grim-ac.sh enable` + Folia restart) — [GRIM.md](../ops/GRIM.md)
- **Full Geyser feature matrix** — intentional Out; YaP ships depth, not a 1:1 Geyser clone.

### Contributors & license

YaPcore first-party code: **GNU GPLv3** — see [LICENSING.md](LICENSING.md).  
Not affiliated with Mojang, Microsoft, ViaVersion, or GeyserMC.

---

## Earlier milestones (pre-1.0.0.0)

Summarized from git history; not separate tagged releases.

| Period | Themes |
|--------|--------|
| **Link 0.6** | Native Velocity-class proxy phases 0–6; frame+zlib encoder fixes; link plugin suite |
| **YaP-Folia fork** | Managed YaP-Folia 26.2 build, sched compat agent, teleport transactions, region pool knobs |
| **World & admin** | YaPWorld in-game edit GUI, admin menu, kits, claim flags, regions plugin |
| **Docs regroup** | `docs/` topic folders, whitepaper v0.3 (Markdown source of truth; PDFs not tracked) |

---

## After 1.0.0.0 (same version — not a bump)

Product stays on **1.0.0.0**. Rebuild / republish artifacts with `gradle publishReleasesFolder`
when cutting a refreshed zip; do **not** change Gradle `version` until a real tag bump.

### Shipped on 1.0.0.0 line (post-tag refresh)

| Area | Change |
|------|--------|
| **YaPWorld** | FAWE-class phases 1–5: masks/patterns, brushes (+erode/raise/lower/melt/fill/forest), entity clipboard + paste `-a/-e/-b/-o/-s`, `//generate` + expression deform, `//fixlighting`, `//limit`, `.yschem` / `.schem` export + `.schematic`/`.litematic` import, WE shim clipboard surfaces — [YAPWORLD.md](../plugins/YAPWORLD.md) |
| **YaPTab** | Folia-safe Bukkit scoreboard sidebar (removed megavex packet path that kicked on join) |
| **YaPChat** | Secure-chat login rewrite fixed for YaP-Folia 26.2 (`ClientboundLoginPacket`) |
| **Economy** | Native `PlayerDataService` balance (deposit/withdraw/set) |
| **Dashboard** | Kit builder, player eco/perm actions, Tebex/plugin YAML editors, friendlier forms |
| **Packs / clients** | Skies/water texture refresh; optional **yap-visuals** / Iris / Sodium client stack docs |
| **Essentials** | Optional water-wave visuals (`features.water-waves`) |
| **Ops docs** | Public hostname `yapcoremc.yaplabs.us`, packs via nginx `:80`, grey-cloud game DNS |
| **Ops Waves 1–5** | Folia-safe pregen/protect/regions; Bedrock inventory fidelity; Discord event webhooks; map markers; dashboard Access context/temp + social/stacker panels; cite fixtures −5.53% fullcite (peak −12.4%) — [YAP_FOLIA_PATCHES.md](../folia/YAP_FOLIA_PATCHES.md) |
| **YaP Encyclopedia** | Purpur-**inspired** event-wired `knobs.yml` (original YaP code): attributes, ride perms, per-mob specials, gameplay/blocks; crop/fluid **NMS opt-in** only after YaP-Folia `0025` + soak — not full Purpur without that — [TUNE.md](../ops/TUNE.md) |
| **Bedrock specialty containers** | Anvil, smithing, loom, stonecutter, cartography — open + slot sync + **recipe pick**; anvil rename FILTER_TEXT pending deploy — [CROSSPLAY.md](../network/CROSSPLAY.md) |
| **Repo layout** | Optional Fabric client mods nested under [`client/`](../../client/) (`yap-visuals`, `yap-bag`, `yap-staff`, `yap-ultrawide`, Iris/Sodium/shaders) |
| **YaPCommands** | YAML custom `/commands` (`yap-commands.jar`) with dashboard **Custom commands** CRUD — messages, player/console runs, aliases, cooldowns — [COMMANDS.md](../ops/COMMANDS.md) · [WEB_DASHBOARD.md](../ops/WEB_DASHBOARD.md) |
| **Packs / CDN** | Default `resource-pack-url` → GitHub `releases/latest/download/{file}`; SHA-1 hashed from the remote bytes clients download; `public-pack-port` 80/443 honored for nginx edge — [CLIENTS_AND_PACKS.md](../network/CLIENTS_AND_PACKS.md) |
| **GitHub release assets** | Tag `1.0.0.0` ships OS zips, suites, `yapcore-default.zip`, and optional Fabric `client_mods.zip` (yap-visuals + yap-bag + yap-staff + yap-ultrawide) |
| **Docs hygiene** | Generated PDFs / office dumps gitignored — publish Markdown only |
| **Typical SMP defaults** | Chat slow-mode 3; claim tax off; map claim markers on; Disasters opt-in off; CHANGE_ME command links; generic Tab branding; dashboard **Opt-in** badge; seed↔jar drift CI — [DEFAULTS.md](DEFAULTS.md) · [WEB_DASHBOARD.md](../ops/WEB_DASHBOARD.md) |

### Still open (not a version bump)

- **Manual §E live checklist** — checklist lives in [CROSSPLAY.md](../network/CROSSPLAY.md); operator must tick join + specialty stations on a live box (cannot automate Xbox)
- Bedrock specialty recipe picks (stonecutter / loom / smithing / cartography) implemented via Paper CRAFT_RECIPE_OPTIONAL — deploy chassis after soak; JE already signed off
- Anvil rename FILTER_TEXT path started (codec + Paper hook — deploy after soak)
- Next-protocol dump when Mojang ships a new JE build ([CLIENTS_AND_PACKS.md](../network/CLIENTS_AND_PACKS.md))
- YaPWorld NMS section placement / FAWE CFI (intentionally out of scope)
- **12h soak-long PASS** (`logs/soak/soak-long-20260905T031507Z.log`) — zip may be marketed as **soak-proven**; heap/thread slope flat (folia heap median early≈1012MB late≈1082MB; threads 137→137) per [YAP_FOLIA_PATCHES.md](../folia/YAP_FOLIA_PATCHES.md)
- Rebuild YaP-Folia with `0025` encyclopedia NMS patch when enabling `crop-growth-nms` / `tick-fluids=false` in production (defaults stay **off**)
`releases/1.0.0.0/` republished 2026-09-13 with Bedrock-feel + native join, Link Bedrock module, presence/blocks clients, and domain ≤500 splits (`./scripts/packs/build-yap-client-render.sh` then `gradle publishReleasesFolder -PyapGameplay=true`). Prior: World tools + schem rotate/flip + Bedrock pack CDN (2026-09-08 evening); YaPItems/staff **1.0.27** same day; staff/bag polish (2026-09-07); typical SMP defaults (2026-09-06); pack CDN/SHA + client visuals (2026-09-04).

---

*0.0.0.1 is the ship version. The **1.0.0.0** GitHub release was deleted, so `/releases/latest` stays empty until a non-prerelease exists. Bump only when cutting a later tagged release.*
