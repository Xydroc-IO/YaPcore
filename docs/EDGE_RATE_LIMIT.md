# Edge rate limits & observability (Agent 0 / Phase 0)

YaPcore ships **connection flood protection** on YaP Link, a **lag-machine governor**
plugin, and **Prometheus `/metrics`** scrape endpoints — without forking Folia.

## 0.1 — YaP Link rate limits (default ON)

Keys in `link.properties` / `link.toml` (see `server/link-data/link.properties.example`):

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

**Loopback (`127.0.0.1` / `::1`) is exempt** so smoke scripts and local ops are not tripped.

Drops increment Link metrics: `connect.throttled`, `handshake.dropped`, `login.dropped`.

Disable (not recommended on public edges):

```properties
connect-rate-limit-enabled=false
handshake-rate-limit-enabled=false
login-rate-limit-enabled=false
```

## 0.2 — YaPLagGuard

Plugin jar: `yap-lagguard.jar` (installed by `gradle installProductDefaults`).

Config: `plugins/YaPLagGuard/config.yml`

| Budget | Default |
|--------|---------|
| `max-entities-per-chunk` | 80 |
| `max-primed-tnt-per-chunk` | 12 |
| `max-hopper-transfers-per-window` | 64 / 20 ticks |
| `max-redstone-events-per-window` | 128 / 20 ticks |

Folia-safe (`folia-supported: true`, YapSched for stats writer). Commands: `/yaplagguard status|reload`.

Stats file for dashboard/Prometheus: `plugins/YaPLagGuard/stats.json`.

## 0.3 — Prometheus scrape

### Chassis (YaPcore web dashboard)

When `web-dashboard-enabled=true` (default):

```text
GET http://127.0.0.1:8080/metrics
```

No auth (same class as `/health`) — **firewall or bind privately**.

Exports: players, ticks, heap, ThreadMetrics counters, lagguard trips.

### YaP Link

```properties
metrics-http-enabled=true
metrics-http-bind=127.0.0.1
metrics-http-port=9091
```

```text
GET http://127.0.0.1:9091/metrics
```

Exports: online players, connect/handshake/login drops, plugin messages.

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

## 0.4 — Dashboard

`GET /api/status` (authed) includes an `observability` object with lagguard stats + tick/player gauges.
