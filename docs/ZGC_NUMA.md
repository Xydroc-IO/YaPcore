# Generational ZGC + NUMA for YapEngine

YapEngine’s 16-thread layout (Traffic Cop + spatial cores + sandboxes) allocates
huge numbers of **short-lived** packets/events each tick and a smaller set of
**long-lived** player/session objects. Generational ZGC matches that shape:
young garbage is reclaimed without scanning the whole heap, while STW pauses
stay sub-millisecond even on large heaps.

## Why ZGC (not G1) here

| Property | YapEngine benefit |
|----------|-------------------|
| Concurrent mark + relocate | Game loops (Threads 3–6) avoid multi-ms GC stutters |
| Generational young/old split | Tick debris dies fast; player profiles stay in old gen |
| NUMA-aware allocation (`-XX:+UseNUMA`) | Keeps Game Core object pages on the bound socket |
| Pinned heap (`-Xms == -Xmx`) | No OS resize storms mid-tick |

No Java source changes required — flags live in deployment scripts only.

## Production command (canonical)

```bash
numactl --cpunodebind=0 --membind=0 java \
  -Xms12G -Xmx12G \
  -XX:+UseZGC \
  -XX:+UnlockDiagnosticVMOptions \
  -XX:+UnlockExperimentalVMOptions \
  -XX:ThreadPriorityPolicy=1 \
  -XX:+UseNUMA \
  -jar yapcore.jar --nogui
```

YapLabs wrapper (same semantics, config-aware):

```bash
./scripts/start-prod.sh              # 12G pinned, node 0
./scripts/start-prod.sh --heap-gb=16 --fg
```

Daily / GUI starts also enable ZGC via `./scripts/start.sh` using
`config/server.properties`.

## Config knobs (`config/server.properties`)

| Key | Default | Meaning |
|-----|---------|---------|
| `jvm-gc` | `zgc` | `zgc` or `g1` |
| `jvm-numa` | `true` | `-XX:+UseNUMA` + optional `numactl` |
| `jvm-numa-node` | `0` | `numactl --cpunodebind` / `--membind` |
| `jvm-heap-pin` | `true` | Force `-Xms == -Xmx` (from `ram-mb`) |
| `jvm-thread-priority` | `true` | `-XX:ThreadPriorityPolicy=1` |
| `ram-mb` | `2048` | Heap size (prod script overrides to 12G) |

Extra HotSpot flags: `YAPCORE_JAVA_OPTS="…" ./scripts/start.sh`

## Flag notes

- **`-XX:+UseZGC`** — concurrent ultra-low-pause collector (generational by
  default on modern JDKs; scripts probe and drop obsolete `+ZGenerational`).
- **`-Xms` / `-Xmx` equal** — pins committed heap; avoids resize CPU during play.
- **`-XX:+UseNUMA`** — ZGC allocates in the nearest NUMA node, aligning with
  Threads 3–6 memory isolation.
- **`-XX:ThreadPriorityPolicy=1`** — allows OS-level priority mapping for
  YapEngine’s thread roles (requires diagnostic unlock on some JDKs).
- **`numactl --cpunodebind=0 --membind=0`** — hard-pins the process to socket 0
  so Traffic Cop + Game Cores stay on one topology node when hardware has NUMA.

Requires **JDK 21+** (YapLabs targets current OpenJDK; Generational ZGC is
production-ready on JDK 23+ / current LTS line).
