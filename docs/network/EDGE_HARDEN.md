# Edge harden — operator playbook

Harden a **public** YaPcore edge without reading the whole wiki. Builds on
[EDGE_RATE_LIMIT.md](EDGE_RATE_LIMIT.md), [CLOUDFLARE_AND_NGINX.md](CLOUDFLARE_AND_NGINX.md),
and [LAGGUARD.md](../plugins/LAGGUARD.md).

## Target architecture

```text
Internet players
      │  TCP(+UDP) :25565
      ▼
DNS-only A/AAAA ──► nginx stream ──► YaP Link :25565-local OR YaPcore :25566
(grey cloud)         (optional)         dashboard :8080 → 127.0.0.1 only
                                        metrics  :9091 → 127.0.0.1 only
                                        YaP-Folia game (child JVM)
```

| Surface | Public? | Bind |
|---------|---------|------|
| Game JE/BE | Yes (`:25565`) | nginx → Link or YaPcore → **YaP-Folia** |
| Packs HTTP | Yes (`:80`/`:443`) via nginx | YaPcore `:8081` localhost |
| Web dashboard `:8080` | **No** | `web-dashboard-bind=127.0.0.1` |
| Link `/metrics` `:9091` | **No** | `metrics-http-bind=127.0.0.1` |

## Safe defaults

### Public internet

```properties
# link.properties
connect-rate-limit-enabled=true
connect-rate-per-ip=20
connect-rate-window-ms=10000
handshake-rate-limit-enabled=true
handshake-rate-per-ip=40
login-rate-limit-enabled=true
login-rate-per-ip=10
max-concurrent-per-ip-enabled=true
max-concurrent-per-ip=8
rate-limit-exempt-loopback=true
metrics-http-bind=127.0.0.1
```

```properties
# config/server.properties
web-dashboard-enabled=true
web-dashboard-bind=127.0.0.1
web-dashboard-localhost-only=true
internet-exposed=true
```

### LAN / lab

Raise limits or disable concurrent caps if many clients share a NAT:

```properties
connect-rate-per-ip=100
max-concurrent-per-ip=32
# or max-concurrent-per-ip-enabled=false for LAN only
```

Keep loopback exemption **on** so `./scripts/smoke-*.sh` keep working.

## Prove throttles fire

```bash
./scripts/smoke-link-rate-limit.sh              # unit soak (always)
LOOPBACK_SOAK=1 ./scripts/smoke-link-rate-limit.sh   # live TCP flood + /metrics
```

Watch:

```text
yap_link_connect_throttled_total
yap_link_handshake_dropped_total
yap_link_login_dropped_total
yap_link_connect_concurrent_dropped_total
```

Or `GET /api/status` → `observability.linkMetricsHint`.

## nginx stream

Templates: `deploy/nginx/yapcore-stream.conf.template`  
Hardened example (conn limits): `deploy/nginx/yapcore-stream-hardened.conf.example`

```bash
./scripts/nginx-setup.sh --dry-run
sudo ./scripts/nginx-setup.sh
```

Point **grey-cloud** DNS at the origin. Orange-cloud Minecraft TCP needs Spectrum.

## Cloudflare notes

1. Game hostname: **DNS only** (grey).
2. Packs/web: orange OK behind nginx `:80`/`:443`.
3. Do not publish `:8080`, `:8081`, or Link `:9091` to the world.
4. Optional Spectrum: TCP proxy to origin `:25565` if you must orange-cloud game traffic.

## Fail-closed checklist (under attack)

1. Confirm drops rising on Link `/metrics` (`*_throttled` / `*_dropped`).
2. Tighten: `connect-rate-per-ip=8`, `max-concurrent-per-ip=4`, shorter windows.
3. At nginx: enable `limit_conn` from the hardened stream example; reload nginx.
4. Host firewall: allow only `:25565` (and `:80`/`:443` for packs) from the internet.
5. Temporarily set `internet-exposed=false` / move DNS to maintenance if needed.
6. Do **not** bind dashboard or metrics to `0.0.0.0` to “debug” under fire.

Example host firewall / fail2ban snippets (docs only): see bottom of this file.

## Lag machines

Install LagGuard (`gradle installProductDefaults`). Survival defaults ship in
`plugins/YaPLagGuard/config.yml` — [LAGGUARD.md](../plugins/LAGGUARD.md).

```bash
./scripts/smoke-lagguard.sh
```

---

## Optional host snippets (examples only)

### nftables (allow game + packs)

```nft
table inet yap_edge {
  chain input {
    type filter hook input priority 0; policy drop;
    ct state established,related accept
    iif lo accept
    tcp dport { 25565, 80, 443 } accept
    udp dport 25565 accept
  }
}
```

### fail2ban (jail sketch — tune to your log path)

```ini
[yap-link-throttle]
enabled = true
filter = yap-link-throttle
logpath = /var/log/yap-link/link.log
maxretry = 1
findtime = 60
bantime = 3600
```

Filter would match `connect throttled ip=` lines when Link logging is raised to INFO for those events. Prefer Link rate limits + nginx `limit_conn` before fail2ban.
