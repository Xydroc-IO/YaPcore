## Summary

-

-

## Test plan

- [ ] `gradle checkDomainLineLimits` + `gradle checkDbBootstrapHygiene`
- [ ] Targeted suites when touching plugins: protect / factions / essentials / chat / world
- [ ] `gradle shadowJar` / relevant assemble task
- [ ] Manual join smoke (if networking / packs / Folia touched)
- [ ] Docs updated (Markdown under `docs/` — no PDFs)

## Notes

- Keep domain `.java` files ≤**500** lines (`./scripts/check-domain-line-limits.sh`)
- Prefer `YapDbBootstrap` for plugin SQL pools ([YAPDB.md](../docs/data/YAPDB.md))
- World / inventory mutations must stay on **SYNC**
- Production closeout phases: [PRODUCTION_READY.md](../docs/ops/PRODUCTION_READY.md)
- Contributions are **GPLv3** — [LICENSE](../LICENSE) · [LICENSING.md](../docs/start/LICENSING.md)
