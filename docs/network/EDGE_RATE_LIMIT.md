# Edge rate limits & observability (Agent 0)

YaPcore ships **connection flood protection** on YaP Link, a **lag-machine governor**
plugin, and **Prometheus `/metrics`** scrape endpoints — without forking Folia.

**Operator playbook (public edge):** [EDGE_HARDEN.md](EDGE_HARDEN.md)

## YaP Link rate limits (default ON)

Keys in `link.properties` / `link.toml` (`examples/yap-link/`, `link-data/`):

| Key | Default | Meaning |
|-----|---------|---------|
| `connect-rate-limit-enabled` | `true` | Per-IP TCP accept throttle |
| `connect-rate-per-ip` | `20` | Max accepts / window |
| `connect-rate-window-ms` | `10000` | Window length |
| `handshake-rate-limit-enabled` | `true` | Handshake packet flood drop |
| `handshake-rate-per-ip` | `40` | |
| `handshake-rate-window-ms` | `10000` | |
| `login-rate-limit-enabled` | `true` | Login-intent flood drop |
| `login-rate-per-ip` | `10` | |
| `login-rate-window-ms` | `10000` | |
| `rate-limit-exempt-loopback` | `true` | Skip limits for `127.0.0.1` / `::1` |
| `max-concurrent-per-ip-enabled` | `true` | Cap simultaneous TCP sessions / IP |
| `max-concurrent-per-ip` | `8` | `0` disables the cap |

**Loopback is exempt by default** so smoke scripts and local ops are not tripped.
Soak with `rate-limit-exempt-loopback=false` (see script below).

### Public vs LAN

| Profile | connect/ip | concurrent/ip | Notes |
|---------|------------|---------------|-------|
| Public | `20` / 10s | `8` | Defaults |
| Under attack | `8` / 10s | `4` | Fail-closed — [EDGE_HARDEN.md](EDGE_HARDEN.md) |
| LAN / NAT | `100` / 10s | `32` | Many clients one IP |

Drops increment: `connect.throttled`, `handshake.dropped`, `login.dropped`, `connect.concurrent_dropped`.

Scrape (clear names):

```text
yap_link_connect_throttled_total
yap_link_handshake_dropped_total
yap_link_login_dropped_total
yap_link_connect_concurrent_dropped_total
yap_link_throttle_*_drops   # gauge mirrors for dashboards
```

Prove:

```bash
./scripts/smoke-link-rate-limit.sh
LOOPBACK_SOAK=1 ./scripts/smoke-link-rate-limit.sh
```

## YaPLagGuard

See [LAGGUARD.md](../plugins/LAGGUARD.md). Jar via `gradle installProductDefaults` / `assembleRelease`.

Survival-oriented defaults: entities/chunk `72`, primed TNT `8`, hopper `48`/20t, redstone `96`/20t.

```bash
./scripts/smoke-lagguard.sh
```

## Prometheus scrape

### Chassis

`GET http://127.0.0.1:8080/metrics` — **firewall**; no auth.

Exports: players, ticks, heap, ThreadMetrics, `yapcore_lagguard_*`.

### YaP Link

```properties
metrics-http-enabled=true
metrics-http-bind=127.0.0.1
metrics-http-port=9091
```

`GET http://127.0.0.1:9091/metrics` — throttle counters above.

### Example Prometheus scrape config

```yaml
scrape_configs:
  - job_name: yapcore
    static_configs:
      - targets: ["127.0.0.1:8080"]
    metrics_path: /metrics
  - job_name: yap-link
    static_configs:
      - targets: ["127.0.0.1:9091"]
    metrics_path: /metrics
```

## Dashboard

`GET /api/status` (authed) → `observability` (lagguard + `linkMetricsHint` for throttle metric names).
