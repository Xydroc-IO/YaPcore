# IC-90 FAIL checklist — Geyser vs YaP (NetworkSettings → SetLocalPlayerAsInitialized)

**Status:** IC-90 still open. JOIN_PROBE (`logs/bedrock-join/`) is the source of truth for wire order — do not treat any current YaP path as a proven-good join.

**Historical note (2026-09-09 ~22:18, guid=bafa3dc814c538ff):** one session logged real `SetLocalPlayerAsInitialized` and stayed up minutes (`movement=CLIENT`, empties after ChunkRadiusUpdated, publisher only after init). That session’s logs are gone; replaying “ChunkRadius → empties → wait 0x71” today still fails IC-90. Do not call the current code “gold.”

## FAIL only (pre-fix)

| # | Item | Geyser | YaP (broken) | Fix |
|---|------|--------|--------------|-----|
| F1 | Soft-init from PlayerAuthInput/MovePlayer | Waits for 0x71 only | Soft-init → fake JOIN_OK | **REMOVED** — wait for real 0x71 |
| F2 | ChunkRadiusUpdated(13) without filling radius | After connect: setServerRenderDistance then **Java streams chunks** | Radius promised, only 1 LevelChunk → client never inits | **Fill empties immediately after ChunkRadiusUpdated (before init)** — Java chunk stand-in |
| F3 | Empties after soft-init | N/A | 288 after fake init then die | Empties moved to post-radius; init path publisher-only |
| F4 | Init-fallback JOIN_OK @3s | None | scheduleClientInitFallback | **REMOVED** |
| F5 | Misread StartConfiguration timing | First join: `serverRenderDistance=-1` → **0** empties from StartConfiguration; connect() sends radius=0 → **1** empty; Java Login then ChunkRadiusUpdated + real chunks | Treated StartConfiguration as “288 after init” | Match JavaLoginTranslator order, not the -1 no-op |

## Not FAIL (wire / proven)

| Item | Notes |
|------|-------|
| AuthoritativeMovementMode | Geyser `buildStartGamePacket` **never calls** `setAuthoritativeMovementMode`. v818+ serializer **omits** mode on wire (only rewind/blockBreaking; v944: blockBreaking only). CLIENT log is cosmetic for proto 2169. |
| Encryption order | NetworkSettings → compress header → enableEncryption → compress→encrypt→0xfe. Geyser does **not** skip enc for offline Floodgate. |
| connect() packet count | 14 vanilla-height packets; 1 LevelChunk (radius 0). |
| RequestChunkRadius | Store-only. |
| SetLocalPlayerAsInitialized | Mark ready + publisher only. |

## Hypothesis proof

**(a) Outbound encrypt:** log `BE encrypt applied outbound` after enableEncryption (compress→encrypt→0xfe).  
**(b) Empty timing:** Geyser connect empties **before** init (1 col). Full-radius fill is **Java chunk stream after ChunkRadiusUpdated**, still typically before Bedrock 0x71. YaP matches that with post-radius empties.  
**(c) Movement enum in bytecode:** Geyser never sets it; default null; **not on wire** ≥v818.  
**(d) Headers:** compression method byte (after NetworkSettings) then AES trailer encrypt, then 0xfe frame.

## Try-now markers

Look for:
1. `BE encrypt applied outbound` (after handshake)
2. `BE connect()+ChunkRadiusUpdated+postRadiusEmpties` … `waiting REAL SetLocalPlayerAsInitialized`
3. `BE SetLocalPlayerAsInitialized XYDROC4550` (**real**, not soft-init)
4. `BE JOIN_OK` lasting **>10s** without IC-90 / client disconnect

If still dying: still mismatched vs Geyser `JavaLoginTranslator` chunk delivery quality (empty vs real blocks) — next is Paper→Bedrock column push, not more soft-init.
