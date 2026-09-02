# Folia fork CI note

## Why a separate path

Full `./scripts/build-yap-folia.sh` downloads Minecraft + Paperweight artifacts and
needs **JDK 25+**. Default CI (`ci.yml`) stays on a fast `shadowJar` + unit tests.

## Recommended PR checks for `vendor/folia/**`

When a PR touches `vendor/folia/patches/**`, `vendor/folia/UPSTREAM.lock`, or
`scripts/{vendor,build,folia-patch,verify}-yap-folia.sh`:

```bash
./scripts/vendor-folia.sh
./scripts/folia-patch.sh --check
# Optional on large runners:
# ./scripts/build-yap-folia.sh
# FOLIA_JAR_SOURCE=build ./scripts/smoke-folia.sh
```

Optional GitHub Actions job (JDK 25, long timeout) can be enabled later as
`folia-fork.yml`. Until then, Agent 1/2/3 must run `folia-patch.sh --check`
locally before merge.

## Packaging

Release scripts should include:

- `scripts/vendor-folia.sh`
- `scripts/folia-patch.sh`
- `scripts/build-yap-folia.sh`
- `scripts/verify-yap-folia.sh`
- `docs/folia/FOLIA_FORK.md`
- `docs/archive/folia/FOLIA_FORK_AGENT_HANDOFF.md` (archived)
