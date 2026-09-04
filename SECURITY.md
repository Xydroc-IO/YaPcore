# Security Policy

## Supported versions

| Version | Supported |
|---------|-----------|
| **1.0.0.0** | Yes — current product line |
| Pre-1.0 / forks | Best-effort only |

## Reporting a vulnerability

**Do not** open a public GitHub issue for security flaws (RCE, auth bypass, path traversal on pack HTTP, secret leakage, etc.).

Contact maintainers privately with:

- YaPcore version / git commit / release tag
- Impact assessment (who can exploit, what is exposed)
- Reproduction steps
- Optional patch or mitigation

We aim to **acknowledge within 72 hours**.

## Operator hardening (baseline)

- Prefer intentional public edges ([EDGE_HARDEN.md](docs/network/EDGE_HARDEN.md)); do not expose pack HTTP or game ports casually.
- Keep dashboard tokens, DB passwords, Discord webhooks, and forwarding secrets out of git — [SECRETS.md](docs/start/SECRETS.md).
- Understand `online-mode` / offline UUID implications on public networks.
- Resource-pack URLs should serve trusted bytes; default CDN is GitHub Releases (`yapcore-default.zip`).
