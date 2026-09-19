# YaP-Folia patches

Ordered paperweight / post-apply deltas for the YaP-Folia product jar.

- **Authors:** YapLabs `<folia@yaplabs.com>` (all `*.patch` From headers)
- **Inventory + knobs:** [docs/folia/YAP_FOLIA_PATCHES.md](../../../docs/folia/YAP_FOLIA_PATCHES.md) — **62** patches (`0000`–`0069`)
- **Soak / ship profile:** [docs/folia/YAP_FOLIA_PATCHES.md](../../../docs/folia/YAP_FOLIA_PATCHES.md)
- **Upstream pin:** [../UPSTREAM.lock](../UPSTREAM.lock) — `14b7fee` / 2026-09-06. `0000`–`0033` are YaP behavior/repairs. `0034`–`0069` are Folia-itself improvements on that pin (through teleport-accept across the cut).
- **Domain:** YaP `*.java` helpers in this fork stay **≤500 lines** (split `YapCorridorCarver` and siblings in `0018`)

Do not put lab notebooks or agent scratch files in this directory — documentation lives under `docs/folia/`.
