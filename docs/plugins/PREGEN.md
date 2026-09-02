# YaP Pregen — built-in chunk pre-generator

First-party **Chunky-class** world pregen for YaPcore / Paper 26.2.
Shipped as `plugins/yap-pregen.jar` (default product install).

**Not Folia.** Uses Paper `World.getChunkAtAsync` with MSPT throttling.

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

One job **per world** (parallel across worlds). Shared MSPT budget.

## Config (`plugins/YaPPregen/config.yml`)

```yaml
chunks-per-tick: 5
max-mspt: 40.0
broadcast-interval-sec: 30
auto-resume: true
max-worlds: 4
max-inflight: 32
```

Progress: `plugins/YaPPregen/progress/<world>.yml` (resume after restart when `auto-resume: true`).

## Web dashboard

Tab **Pregen** at `http://127.0.0.1:8080/` — or:

- `GET /api/pregen` — status text
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

```
/yappregen start world radius 32
/yappregen status
/yappregen pause world
/yappregen resume world
/yappregen cancel world
```
