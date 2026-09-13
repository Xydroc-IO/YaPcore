# Bedrock-feel parity matrix

Pinned band: **`band_26_50`**. Status is only **Green** or **Out**.  
**Green** = convert-verified against Bedrock extract **and** wired end-to-end for that row’s DoD.  
**Out** = unfinished. No Partial.

| Row | Status | Notes |
|-----|--------|-------|
| Phase 0 contract + catalogs | **Green** | Frozen catalogs + config keys |
| Phase 0 extract→convert for blocks | **Green** | 24/24 yapblock + provenance |
| Phase 0 extract→convert for humanoid geo | **Green** | wide+slim from Mojang bedrock-samples |
| Phase 0 extract→convert for player anims | **Green** | 12/12 from bedrock-samples (incl. controllers) |
| Phase 0 extract→convert for emote bones | **Green** | 4/4 free persona emotes from local Bedrock `persona/pieces` |
| Cosmetics: classic skin PNG + cube geo JE | **Green** | Canonical ingest, Tailor, `yap-presence` cube render |
| Cosmetics: persona piece JE render | **Out** | Pieces stored on canonical; not drawn on JE |
| Emotes: UUID catalog + BE↔JE relay | **Green** | Authority, EmotePacket, EmoteList, Tailor `/emote`, presence |
| Emotes: JE bone playback from convert | **Green** | 4 free `yap.emote/1` clips |
| Movement: catalog → BE + JE + presence + Guard | **Green** | jump/gravity/drag/sprint-sneak; Guard sprint×1.3 / reach 3.0 |
| Movement: `face_assist` placement | **Green** | `FaceAssistPlacementMixin` from MOVEMENT profile |
| `parity.bedrock-feel` JE HELLO gate | **Green** | Tailor kicks JE without `yap:presence` HELLO; BE exempt |
| Block ports: Folia + pack + bridge + HELLO | **Green** | Phase 4 complete |
| JE presence UX (wardrobe screens) | **Green** | Fabric hub (**P**) — wardrobe + emote picker over Tailor records; BE forms stay |
| Release packaging | **Green** | `client_mods.zip` (6 mods) + provenance pin + dashboard card + `smoke-bedrock-feel.sh` |

### Operator
```bash
./scripts/parity/extract-emotes-from-bedrock.sh
./scripts/parity/extract-block-textures.sh
./scripts/parity/convert.sh
./scripts/build-default-resourcepack.sh
./scripts/build-yap-client-render.sh
./scripts/parity/smoke-bedrock-feel.sh
```

Folia cannot register true custom `Block` types — exclusives use BARRIER + ItemDisplay with Bedrock textures (item CMD). Not NoteBlock/CMD **block** fakes.
