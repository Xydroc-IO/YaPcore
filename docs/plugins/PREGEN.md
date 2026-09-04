# YaP Pregen — built-in chunk pre-generator

First-party **Chunky-class** world pregen for YaPcore.
Shipped as `plugins/yap-pregen.jar` (default product install).

**Folia-supported.** Chunk loads run on the owning region via `YapSched`, hold plugin
chunk tickets while inflight, and use per-region inflight caps. Global MSPT throttling
applies on Paper only (Folia MSPT is region-local).

## Commands

`/yappregen` (aliases: `pregen`, `yapchunky`) — permission `yappregen.admin` (op):

```
/yappregen start <world> radius <chunks> [x z]
/yappregen start <world> circle <blockRadius> [x z]
/yappregen start <world> corners <x1> <z1> <x2> <z2>
/yappregen start <world> polygon <x1> <z1> <x2> <z2> <x3> <z3> ...
/yappregen start <world> worldborder
/yappregen start <world> selection          # WorldEdit //sel (soft-depend)
/yappregen pause [world|all]
/yappregen resume [world|all]
/yappregen cancel [world|all]
/yappregen status [world|all]
/yappregen reload
```

One job **per world** (parallel across worlds). Shared global inflight budget plus
per-region caps on Folia.

## Config (`plugins/YaPPregen/config.yml`)

```yaml
chunks-per-tick: 5
max-mspt: 40.0                 # Paper only
broadcast-interval-sec: 30
auto-resume: true
max-worlds: 4
max-inflight: 32
max-inflight-per-region: 8    # Folia region buckets
```

Progress: `plugins/YaPPregen/progress/<world>.yml` (resume after restart when `auto-resume: true`).

## Web dashboard

Tab **Pregen** at `http://127.0.0.1:8080/` — or:

- `GET /api/pregen` — status text / job map (`regionized`, `activeRegions`)
- `POST /api/pregen` — `{ "action":"start", "world":"world", "shape":"radius", "radius":"8" }`

## WorldEdit

Prefer **YaPWorld** on Folia: select with `/yapworld tool`, then:

```bash
/yappregen start <world> selection
# aliases: sel | we | yapworld
```

Falls back to stock WorldEdit `//sel` when YaPWorld is not installed (Paper benches only).
On the Folia product path prefer YaPWorld (`//sel`, wand) — stock WE/FAWE jars are not supported.

## Example

```bash
/yappregen start world radius 32
/yappregen status
/yappregen pause world
/yappregen resume world
```
