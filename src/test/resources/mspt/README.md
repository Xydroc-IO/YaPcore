# MSPT fixtures

Tracked A/B JSON for `checkMsptRegressionFixtures` / `compare-folia.py`.

| File | Role |
|------|------|
| `stock-folia-heavypop.json` / `yap-folia-heavypop.json` | Soft pass — ship knobs (`20260904TshipOn`, **−7.55%**) |
| `yap-folia-heavypop-regress.json` | Must fail regression gate |
| `cite-fullcite-stock.json` / `cite-fullcite-yapcore.json` | Official cite — ship knobs (**−5.53%**, `20260904T040935Z`; peak **−12.4%** at `shipFc2`) |
| `cite-fullcite-verdict.txt` | Human verdict text |
| `peer-heavypop-canvas.json` | Canvas heavypop peer (YaP #1) |
| `cite-fullcite-canvas.json` | Canvas fullcite peer (ranking only) |

## Canvas

```bash
YAP_BENCH_COMPETITORS=folia,canvas,yapcore YAP_MSPT_REQUIRE_SHIP_KNOBS=1 \
  ./scripts/bench/run-vs-folia.sh heavypop 40
```

See [`docs/folia/CANVAS_PARITY.md`](../../../docs/folia/CANVAS_PARITY.md) ·
[`docs/folia/REAL_GAINS.md`](../../../docs/folia/REAL_GAINS.md).

## Ship knobs (fixture regen)

- async-chunk-save **on**, hopper-tick-budget **64**
- entity-tick-budget **400**, microtick **8**, subregion-partition **true**
- Result JSON must include `knob_*` fields
