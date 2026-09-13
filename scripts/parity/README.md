# Bedrock parity extract / convert (Phase 0)

```bash
# From repo root (uses system `gradle` or ./gradlew)
./scripts/parity/extract.sh band_26_50
./scripts/parity/convert.sh band_26_50

# Or
gradle parityAll -PparityBand=band_26_50 -PparityRoot="$PWD"

# Tests
gradle :test --tests 'com.yapcore.crossplay.bedrock.parity.*' \
  --tests 'com.yapcore.config.protocol.ParityConfigTest'
```

Optional: `YAP_BEDROCK_VANILLA_RP=/path/to/bedrock/resource_pack` during extract copies geometry from vanilla (port path). Without it, committed fixtures under `src/main/resources/protocol/bedrock/parity/<band>/fixtures/` are used.

Contract: [`docs/product/BEDROCK_FEEL_PARITY.md`](../../docs/product/BEDROCK_FEEL_PARITY.md)
