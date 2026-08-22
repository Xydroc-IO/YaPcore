# Folia / Paper API coverage

**Product path:** under `game-authority=folia` (default), Folia owns the game.
First-party plugins must declare `folia-supported: true` and schedule via
[`YapSched`](YAP_SCHED.md). Stock Paper jars are **unsupported** (no PaperCompat).

**Legacy Paper path:** under `game-authority=paper`, YaPcore still embeds Paperclip
for benches / Phase 3 opt-in. That is **not** the product default.

| Path | Who owns Bukkit/Paper API | Stock Paper jars |
|------|---------------------------|------------------|
| **Product** (`game-authority=folia`) | Folia 26.2 + first-party Folia-native | **No** |
| **Legacy** (`game-authority=paper`) | Real Paper (`paper-api` 26.2) | Yes (benches) |

## Product surface

| Area | Status |
|------|--------|
| Folia region / entity / global / async schedulers | Full (via Folia + YapSched) |
| YaP Link modern forwarding | Full |
| Chassis Via* / Geyser dual-stack | Partial → Phase 4 DoD |
| `yap-spatial-tick` | Unsupported on Folia |
| Arbitrary Spigot/Paper ecosystem jars | Unsupported |

## Legacy Paper notes

When `game-authority=paper`, plugins get complete Paper API from the embedded
Paperclip — same as stock Paper 26.2. Phase 3 spatial tick remains opt-in for
benches only.

Runtime matrix: `com.yapcore.api.ApiCoverage`.
Smoke: `./scripts/smoke-folia-plugins.sh` · `./scripts/smoke-yap-link-folia.sh`
