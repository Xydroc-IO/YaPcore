# Capture retail Xbox / Floodgate chain (optional soak)

CI already runs a **retail-shaped** multi-hop fixture (P4.9). Capturing a live
Mojang-rooted JWT is optional — use it when you want to harden G.42 against a
real Xbox login.

## Capture

```bash
JAVA_TOOL_OPTIONS='-Dyap.floodgate.dumpChain=true' ./scripts/start.sh --nogui
# optional: -Dyap.floodgate.dumpChainPath=/abs/path.json
```

1. Join once from an Xbox-signed Bedrock / retail client.
2. Stop the server.
3. Fixture lands at `build/xbox-chain-capture.json` (gitignored).

## Soak

```bash
# JWT unit + optional retail fixture (+ offline connectivity if server up)
./scripts/protocol-matrix/run-bedrock-xbox-soak.sh

# Live Microsoft/Xbox client join (device-code / cached token via bedrock-protocol)
BEDROCK_ONLINE=1 HOST=127.0.0.1 PORT=25566 ./scripts/protocol-matrix/run-bedrock-xbox-soak.sh

# Fail CI if live join required
REQUIRE_XBOX_LIVE=1 BEDROCK_ONLINE=1 ./scripts/protocol-matrix/run-bedrock-xbox-soak.sh
```

Or copy into `src/test/resources/xbox/retail-chain.json` (gitignored) and re-run
`xbox-chain-soak.sh` / `run-bedrock-xbox-soak.sh` with no env.

Without a live capture, JWT soak still passes on the shaped CI gate
and prints that no retail fixture is present (gated — not a failure unless
`REQUIRE_XBOX_LIVE=1`).
