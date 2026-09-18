# Security Policy

## Supported versions

| Version | Supported |
|---------|-----------|
| **0.0.0.1** | Yes — current product line (GitHub **prerelease**) |
| **1.0.0.0** | Previous tagged line — security fixes land on **0.0.0.1** |
| Other pre-1.0 / forks | Best-effort only |

## Reporting a vulnerability

**Do not** open a public GitHub issue for security flaws (RCE, auth bypass, path traversal on pack HTTP, secret leakage, etc.).

Contact maintainers privately (do not use public issues for security):

- **Email:** [xydroc@yaplabs.us](mailto:xydroc@yaplabs.us)
- **Discord:** username **xydroc** (server: [discord.gg/BXbyQk88Da](https://discord.gg/BXbyQk88Da)) — prefer email for sensitive details

Include:

- YaPcore version / git commit / release tag
- Impact assessment (who can exploit, what is exposed)
- Reproduction steps
- Optional patch or mitigation

We aim to **acknowledge within 72 hours**.

For non-security bugs and operator help, see the README [Community & support](README.md#community--support) section.

## Operator hardening (baseline)

- Prefer intentional public edges ([NETWORKING.md](docs/network/NETWORKING.md)); do not expose pack HTTP or game ports casually.
- Keep dashboard tokens, DB passwords, Discord webhooks, and forwarding secrets out of git — [SECRETS.md](docs/start/SECRETS.md).
- Understand `online-mode` / offline UUID implications on public networks.
- Resource-pack URLs should serve trusted bytes; default CDN is GitHub Releases (`yapcore-default.zip`).
- Do not paste production secrets or private keys into AI assistants — [AI_TRANSPARENCY.md](docs/start/AI_TRANSPARENCY.md).

## AI-assisted development

YaPcore is built with AI coding tools under human review. That does **not** change vulnerability reporting: still use the private channel above. See [AI_TRANSPARENCY.md](docs/start/AI_TRANSPARENCY.md).