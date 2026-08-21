# Windows launch scripts (PowerShell)

Used by `gradle assembleRelease` → `build/dist/yapcore-release/windows/`.

| Script | Role |
|--------|------|
| `Lib.ps1` | Shared config / JVM / pid / junctions / Paperclip check |
| `Start.ps1` | Start server (`-Gui`, `-Fg`) |
| `Stop.ps1` | Stop (`-Force`) |
| `Status.ps1` | Running / config summary |
| `Gui.ps1` | Swing control panel |
| `Start-Prod.ps1` | Large pinned heap + ZGC |
| `Nginx-Setup.ps1` | Same nginx templates as Linux (`-DryRun`, `-Uninstall`) |
| `Vendor-Paper.ps1` | Clone/pin `vendor/paper` |
| `Build-Vendor-Paper.ps1` | Build YaP Paperclip → `lib\paper-*-yap.jar` |

Root wrappers: `start.cmd`, `nginx-setup.cmd`, `vendor-paper.cmd`, `build-vendor-paper.cmd`, …

See [docs/WINDOWS.md](../../docs/WINDOWS.md).
