## Summary

-

-

## Test plan

- [ ] `gradle test` (or targeted module tests)
- [ ] `gradle shadowJar` / relevant assemble task
- [ ] Manual join smoke (if networking / packs / Folia touched)
- [ ] Docs updated (Markdown under `docs/` — no PDFs)

## Notes

- Keep domain `.java` files ≤**500** lines (`./scripts/check-domain-line-limits.sh`)
- World / inventory mutations must stay on **SYNC**
- Contributions are **GPLv3** — [LICENSE](../LICENSE) · [LICENSING.md](../docs/start/LICENSING.md)
