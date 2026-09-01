name: Pull request
about: Template for YaPcore PRs
title: ""
labels: []
assignees: []
---

## Summary
-
-

## Test plan
- [ ] `./test-unit.sh`
- [ ] `gradle shadowJar`
- [ ] Manual join smoke (if networking touched)
- [ ] Docs updated (if API/behavior changed)

## Notes
- Keep domain folders ≤500 lines where practical
- World/inventory mutations must stay on SYNC
- Contributions are **GPLv3** (see [LICENSE](../LICENSE) · [LICENSING.md](../docs/start/LICENSING.md))
