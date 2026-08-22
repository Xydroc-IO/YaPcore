#!/usr/bin/env bash
# Phase 4 play-soak polish — automated gates + printable live checklist.
#
# Runs unit/smoke gates that do not need a retail client, then prints the
# §E live soak checklist from VIA_GEYSER_PARITY for operators to tick.
#
# Usage (from repo root):
#   ./scripts/protocol-matrix/play-soak.sh
#   HOST=127.0.0.1 PORT=25566 ./scripts/protocol-matrix/play-soak.sh --matrix
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

RUN_MATRIX=0
RUN_BE=0
RUN_XBOX=0
for arg in "$@"; do
  case "$arg" in
    --matrix) RUN_MATRIX=1 ;;
    --bedrock|--be) RUN_BE=1 ;;
    --xbox) RUN_XBOX=1 ;;
    --all) RUN_MATRIX=1; RUN_BE=1; RUN_XBOX=1 ;;
  esac
done

echo "== Phase 4 automated soak gates =="

echo "-- Packet dumps / mid band --"
gradle :test --tests 'com.yapcore.protocol.via.id.PacketIdDumpIndexTest' \
  --tests 'com.yapcore.protocol.via.BandPathCompletenessTest' \
  --tests 'com.yapcore.protocol.via.remap.SlotCodecClickTest' \
  --tests 'com.yapcore.protocol.via.remap.MetadataAndLightTest' -q

echo "-- Bedrock column stream (P4.5) + containers/forms/skins --"
gradle :test --tests 'com.yapcore.crossplay.bedrock.BedrockColumnStreamerTest' \
  --tests 'com.yapcore.crossplay.bedrock.BedrockContainerBridgeTest' \
  --tests 'com.yapcore.crossplay.bedrock.BedrockPacketCodecGameplayTest' \
  --tests 'com.yapcore.protocol.via.CatalogStoreTest' \
  --tests 'com.yapcore.crossplay.form.FormServiceTest' \
  --tests 'com.yapcore.crossplay.skin.SkinServiceTest' -q

echo "-- Xbox shaped CI (P4.9) --"
./scripts/protocol-matrix/xbox-chain-soak.sh

if [[ "$RUN_MATRIX" == "1" ]]; then
  echo "-- JE protocol matrix (needs live Via front) --"
  HOST="${HOST:-127.0.0.1}" PORT="${PORT:-25566}" ./scripts/protocol-matrix/run-matrix.sh
fi

if [[ "$RUN_BE" == "1" ]]; then
  echo "-- Bedrock smoke (needs live BE listener) --"
  ./scripts/protocol-matrix/run-bedrock-smoke.sh
fi

if [[ "$RUN_XBOX" == "1" ]]; then
  echo "-- Xbox / Mojang soak (JWT + optional online join) --"
  ./scripts/protocol-matrix/run-bedrock-xbox-soak.sh
fi

echo
echo "== Live soak checklist (tick on a running server) =="
echo "See docs/VIA_GEYSER_PARITY.md §E — mark Done only when passed."
echo
cat <<'EOF'
JE mid (1.20.4 and 1.21.1 minimum):
  [ ] Join under compression + optional forced pack
  [ ] Walk 200+ blocks across chunk borders
  [ ] Open chest / furnace / crafting; shift-click stack
  [ ] Hotbar select + place/break 32 blocks
  [ ] Attack mob; see other players move
  [ ] Run /help and one plugin command
  [ ] No disconnect for 10 minutes

Bedrock 1.21.50 (Paper columns default — P4.5):
  [ ] RakNet ping + login + spawn (geyserParitySmoke)
  [ ] Move across chunk borders — terrain matches Paper (not flat void)
  [ ] Move, jump, sprint; chat visible to JE
  [ ] Break/place mirrored on Paper; column refreshes
  [ ] Inventory open; take/place; /give appears (G.27)
  [ ] Armor equip + crafting grid mutate (G.26)
  [ ] Chest TAKE/PLACE; villager UPDATE_TRADE; enchant options (G.30)
  [ ] Command from BE → Paper executes
  [ ] Form opens and returns (if UI used)
  [ ] Offline smoke + Xbox soak (./scripts/protocol-matrix/run-bedrock-xbox-soak.sh)
  [ ] Retail fixture or BEDROCK_ONLINE=1 live join (G.42)

Optional flags:
  -Dyapcore.bedrock.flat-chunks=true          # force flat (opt-in only)
  -Dyapcore.bedrock.paper-chunks-fallback-flat=false  # empty marker if Paper miss
  -Dyap.floodgate.dumpChain=true              # capture retail Xbox JWT
EOF

echo
echo "Automated soak gates: PASS"
echo "Live ticks remain operator-owned until checked above."
echo
echo "Real-load pop ladder (bots 50→200 + denser heavypop MSPT):"
echo "  ./scripts/bench/run-vs-folia.sh"
echo "  ./scripts/bench/run-vs-folia.sh --tiers mid,heavy"
echo "See docs/BENCH_VS_PAPER.md § Pop ladder."
