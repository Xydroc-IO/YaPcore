# Bedrock-feel parity — product contract

**Status:** Phases 0–6 Green (persona piece JE render remains Out under Phase 1)  
**Pinned band:** `band_26_50` (Cloudburst resources under `protocol/bedrock/cloudburst/`)  
**Config:** `parity.bedrock-feel` · `parity.bedrock-band`

## Rule (non-negotiable)

Catalog content is **ported from Bedrock**, not recreated.

| Allowed | Forbidden |
|---------|-----------|
| Extract Bedrock defs/assets for the pinned band | Hand-redrawing “close enough” skins/blocks/anims |
| Format **conversion** only (geo → JE runtime, etc.) | Inventing YaP-only IDs that claim Bedrock match |
| Runtime glue (Fabric/Folia/bridge) | Empty persona stubs, CMD/NoteBlock fakes for catalog blocks |
| Frozen catalog + explicit band bump | Auto-chasing every Mojang Bedrock update |

Green in [`BEDROCK_FEEL_MATRIX.md`](BEDROCK_FEEL_MATRIX.md) means: JE output is **convert-verified** against the Bedrock extract (golden hash), not “looks similar.”

## Match pipeline

```
Bedrock vanilla / Cloudburst palette (pinned band)
  → extract (fixtures + catalogs)
  → convert (deterministic)
  → provenance manifest (path, band, sha256)
  → JE Fabric / Folia / bridge (later phases)
```

Resources live under:

- Catalogs / fixtures / goldens: `src/main/resources/protocol/bedrock/parity/<band>/`
- Tooling: `com.yapcore.crossplay.bedrock.parity` + `scripts/parity/`

## Pillars (frozen v1)

Statuses live only in [`BEDROCK_FEEL_MATRIX.md`](BEDROCK_FEEL_MATRIX.md) as **Green** or **Out**. Never Partial.

| Pillar | Source | Phase |
|--------|--------|-------|
| Cosmetics (persona/skin geo+textures) | Bedrock skin packet + geometry extracts | 1 — classic skin+cube geo Green; persona JE render Out |
| Emotes / animations | Bedrock emote UUIDs + anim extracts | 2 — 4 free persona emotes Green |
| Movement | Bedrock constant extract | 3 — Green (incl. face_assist) |
| Blocks | Bedrock palette + resource extracts | 4 — Green |
| JE UX | Presence UI over same records | 5 — Green (Fabric wardrobe/emotes; BE forms) |
| Release | client_mods + matrix smoke | 6 — Green |

## Config

```properties
# Off until presence/blocks ship; Phase 0 loads catalogs either way.
parity.bedrock-feel=false
parity.bedrock-band=band_26_50
```

When `parity.bedrock-feel=true` (chassis) **and** Tailor `parity.bedrock-feel` / `require-presence-mod=true`: JE clients without `yap:presence` HELLO are kicked after the configured timeout.

## Tooling

```bash
# Re-extract catalogs from in-repo Cloudburst palette (+ optional vanilla RP)
./scripts/parity/extract.sh band_26_50

# Convert fixtures → converted/ and refresh provenance
./scripts/parity/convert.sh band_26_50

# Golden tests
./gradlew test --tests 'com.yapcore.crossplay.bedrock.parity.*'
```

Optional env: `YAP_BEDROCK_VANILLA_RP=/path/to/bedrock/resource_pack` — when set, extract also copies geometry/emote/anim files from that pack into fixtures (never hand-authored).

## Licensing / provenance

Every fixture and converted artifact is recorded in `provenance/manifest.v1.json` with source path, band, and SHA-256. Operators redistributing vanilla Bedrock extracts must comply with Mojang terms. YaP does not claim ownership of Mojang assets.

## Non-goals

- Marketplace / paid Character Creator catalogs  
- RenderDragon / PBR clone  
- Auto-expanding every exclusive each update  
- Silent unfinished work — use **Out** in the matrix (never Partial)  

## DoD — Phase 0

- [x] This contract
- [x] Config keys wired
- [x] Frozen Bedrock-keyed catalogs (blocks, emotes, animations, movement)
- [x] Extract → convert → provenance toolchain
- [x] Convert goldens for blocks + player anims + wide/slim geo
- [x] Convert goldens for free persona emote bones (4 from local Bedrock install)
- [x] Optional vanilla RP copy via `YAP_BEDROCK_VANILLA_RP` / `extract-emotes-from-bedrock.sh`

## DoD — Phase 1

- [x] `BedrockCanonicalSkin` + SkinService put/get/ingest (full geometry/persona fields when present in packet)
- [x] PlayerSkin codec writes non-empty geometry_data / pieces
- [x] CDN `POST /skin/apply` + Tailor `ChassisSkinPush` + persist canonical JSON
- [x] JE `yap-presence` renders ported **classic cube** geometry (`yap:presence` channel)
- [ ] JE persona piece render (Out)
- [x] Bedrock forms (wardrobe / URL / cape / model)

## DoD — Phase 2

- [x] `EmoteAuthorityService` + catalog UUID gate
- [x] Bedrock `EmotePacket` C2S → authority → outbound BE
- [x] JE `yap:presence` `EMOTE|<uuid>|<emoteId>` channel + bundled `yap.emote/1` bone playback (4 free)
- [x] Tailor `/emote` + Bedrock emote picker + `ChassisEmotePush`
- [x] Catalog authority tests (`EmoteAuthorityTest`)

## DoD — Phase 3

- [x] `MovementParityTable` + `MovementAuthorityService` (all 10 catalog IDs)
- [x] BE `UpdateAttributes` / adventure abilities from catalog
- [x] JE Paper attrs + walk/fly on join (`ParityMovementApplier`)
- [x] `yap:presence` `MOVEMENT|…` + client jump/gravity/drag/sprint-sneak predict
- [x] Guard sprint multiplier 1.3 + entity reach 3.0 baselines
- [x] `face_assist` placement (`yap-presence` mixin + MOVEMENT profile)
- [x] Golden tests (`MovementAuthorityTest`, `CloudburstMovementPacketsTest`)

## DoD — Phase 4

- [x] Convert-verified 24/24 yapblock + Bedrock textures in `yap-bedrock-blocks`
- [x] Folia `YaPBedrockBlocks`: place/break/give for all 24 (LIGHT / STONECUTTER / frames / BARRIER+ItemDisplay)
- [x] PDC identity `yapbedrock:port_id` — no NoteBlock / CMD-as-block fakes
- [x] Chassis bridge: PLACE from Bedrock item/runtime → Folia port; column sync returns palette runtime index
- [x] JE `yap-blocks` HELLO (`yap:blocks`) + face_assist via presence
- [x] Default resource pack merge + `ParityPhase4BlocksTest` / plugin catalog tests

## DoD — Phase 5

- [x] Fabric JE presence hub (keybind **P**) — wardrobe + emote picker
- [x] Same Tailor records as Bedrock forms (`WARDROBE|v1` / `EMOTE_CATALOG|v1` over `yap:presence`)
- [x] Apply/save/delete slots; URL skin; cape; slim/wide model
- [x] Play frozen catalog emotes from UI
- [x] Bedrock forms unchanged (`BedrockWardrobeForms`)
- [x] Codec tests (`PresenceUiCodecTest`)

## DoD — Phase 6

- [x] `client_mods.zip` includes `yap-presence` + `yap-blocks` (+ visuals/bag/staff/ultrawide)
- [x] `INSTALL.txt` documents parity mods / channels
- [x] `publishReleasesFolder` copies client_mods + pack + provenance pin when present
- [x] Dashboard status card (`bedrockFeel`) for band / mods / pack / provenance
- [x] `./scripts/parity/smoke-bedrock-feel.sh` matrix smoke
- [x] Docs: matrix Green; RELEASES / client README aligned
