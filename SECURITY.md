# Security Policy

## Supported versions

| Version | Supported |
|---------|-----------|
| 0.1.x   | Yes |

## Reporting a vulnerability

Please **do not** open a public GitHub issue for security flaws (RCE, auth bypass, path traversal in pack HTTP, etc.).

Email or privately message the maintainers with:

- YaPcore version / commit
- Impact assessment
- Reproduction steps
- Optional patch

We aim to acknowledge within 72 hours.

## Operator notes

- Keep `online-mode` implications understood (offline UUID spoofing).
- Do not expose pack HTTP or game ports without firewall/nginx intent.
- Never commit secrets; use `.env` (gitignored) or your secret manager.
