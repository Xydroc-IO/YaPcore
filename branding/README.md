# Branding

Official YaPcore visual identity — **Minecraft-inspired** (voxels / blocks / engine threads), not Mojang trademarks.

**Primary mark:** isometric grass-top slate cube with a recessed glowing amber **Y**.

Web dashboard serves these at `/branding/…` (live from this folder; jar also embeds icon+mark under `web/branding/`).

## Core marks

| File | Use |
|------|-----|
| `yapcore-icon.png` | App / window / taskbar icon · web favicon · sidebar |
| `yapcore-mark.png` | Logo mark (README, splash, web login) |
| `yapcore-avatar.png` | Square social / Discord avatar (tight crop) |
| `yapcore-wordmark.png` | Horizontal lockup on dark (cube + YaPcore) |
| `yapcore-wordmark-light.png` | Horizontal lockup on light docs backgrounds |
| `yapcore-mono-light.png` | Monochrome mark for dark UIs / watermarks |
| `yapcore-mono-dark.png` | Monochrome mark for light / print |
| `yapcore-badge.png` | Compact “Powered by YaPcore” footer badge |
| `yapcore-favicon.ico` | Multi-size favicon (browser / desktop) |
| `yapcore-favicon-{16,32,64,128,256}.png` | Discrete favicon PNGs derived from the icon |

## Banners & media

| File | Use |
|------|-----|
| `yapcore-banner.png` | GitHub / docs hero banner |
| `yapcore-og.png` | Open Graph / link-preview card (16:9) |
| `yapcore-discord-banner.png` | Discord / community server banner |
| `yapcore-thumbnail.png` | YouTube / video thumbnail base |
| `yapcore-splash.png` | App splash / boot screen (portrait) |
| `yapcore-poster.png` | Stories / mobile vertical (9:16) |
| `yapcore-wallpaper.png` | Desktop wallpaper |
| `yapcore-pattern.png` | Subtle UI / docs background motif |

## Legacy

| File | Notes |
|------|-------|
| `yapcore-banner-legacy.png` | Prior cinematic banner (Minecraft-style wordmark + creeper-in-A). Kept for reference; prefer `yapcore-banner.png`. |

## Palette

| Token | Hex | Role |
|-------|-----|------|
| Slate | `#1a1f28` / `#2c323c` | Stone faces, UI chrome |
| Grass | `#3d8c40` / `#5cb85c` | Cube top / accent green |
| Amber | `#f0a020` / `#ffc107` | Glowing **Y** |
| Circuit | `#3ecfcf` | Optional data-line accent |
| Ink | `#000000` | Dark lockup backgrounds |
| Snow | `#f5f6f8` | Light lockup backgrounds |

## Guidelines

- Prefer the cube + circuit motif; avoid Creeper faces or Minecraft wordmarks.
- Keep dark slate / grass / gold accents for consistency with the control GUI.
- Wordmarks use a clean geometric sans — not the classic Minecraft logo font.
- Do not commit operator-generated variants into `plugins/` or `resourcepacks/`.
- When regenerating media, keep `yapcore-icon.png` / `yapcore-mark.png` as the source of truth for the cube.

## License of assets

Branding images are © YapLabs / YaPcore contributors and may be used to identify this project.
Third-party forks should replace marks if rebranding.

YaPcore **source code** is **[GPLv3](../LICENSE)** — see [docs/start/LICENSING.md](../docs/start/LICENSING.md).
Branding artwork itself is not dual-licensed for commercial rebrand kits; ask before
shipping YaP marks on an unrelated product.
