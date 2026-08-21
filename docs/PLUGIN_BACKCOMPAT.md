# Plugin back-compat (1.20–1.21 → Paper 26.2)

YaPcore ships **Tier A + light B** support so many Paper plugins built for
**1.20–1.21** can run on product **Paper 26.2**.

This is **not** Via (clients). It is **not** a promise that every old jar works
(especially deep NMS). See also [PAPER_API_COVERAGE.md](PAPER_API_COVERAGE.md)
and [PLUGIN_COMPAT.md](PLUGIN_COMPAT.md).

## What we rewrite (before Paper loads)

On boot (`plugin-compat-enabled=true`, default), YaPcore scans `plugins/*.jar`
and ASM-rewrites:

| Kind | Examples |
|------|----------|
| Enchantment fields | `DAMAGE_ALL` → `SHARPNESS`, `DIG_SPEED` → `EFFICIENCY`, … |
| PotionEffectType fields | `SLOW` → `SLOWNESS`, `HEAL` → `INSTANT_HEALTH`, … |
| Particle fields | `REDSTONE` → `DUST`, `BLOCK_CRACK` → `BLOCK`, … |
| CraftBukkit packages | `org.bukkit.craftbukkit.v1_20_R3.*` → `org.bukkit.craftbukkit.*` |

Skipped: `yap-*.jar`, spatial tick, compat smoke, this compat plugin itself.

Backups (when `plugin-compat-backup=true`): `plugins/.yap-plugin-compat-backup/`.

## Config (`config/server.properties`)

```properties
plugin-compat-enabled=true
plugin-compat-rewrite=true
plugin-compat-backup=true
```

## Runtime plugin

`plugins/yap-plugin-compat.jar` (shipped by default) — `/yapcompat` status.
Load order: `STARTUP`.

## Limits

- Won’t fix Folia-only plugins.
- Won’t invent removed gameplay APIs (complex enchantment damage helpers, etc.).
- Version-pinned reflection into Mojang intermediary may still break — same as Paper.
- Re-copying an old jar over a rewritten one re-triggers rewrite on next boot.

## Verify

```bash
gradle test --tests com.yapcore.plugincompat.PluginCompatRewriterTest
./scripts/smoke-paper-plugins.sh
```
