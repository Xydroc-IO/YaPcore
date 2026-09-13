# Bedrock parity resources (`band_26_50`)

Frozen catalogs, Bedrock fixtures, converted JE intermediates, and provenance.

| Path | Role |
|------|------|
| `catalogs/` | Bedrock-keyed freeze lists (blocks, emotes, animations, movement) |
| `fixtures/` | Extracted Bedrock source docs (do not hand-recreate) |
| `converted/` | Deterministic converter output (golden) |
| `provenance/manifest.v1.json` | sha256 + source for every artifact |

Rebuild: `./scripts/parity/convert.sh band_26_50`
