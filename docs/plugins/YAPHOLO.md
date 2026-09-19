# YaPHolo — packet holograms

DecentHolograms / HolographicDisplays-class floating text. No world entities. Requires **`yap-lib.jar`** (`depend: [YaPLib]`) for packet send. Soft-depends **PlaceholderAPI** and **YaPNpcs**.

## Commands

```bash
/yapholo create spawn at world 0 66 0 &6Welcome %player_name%
/yapholo setlines spawn &6Welcome|&7online: %online%|#ICON:DIAMOND
/yapholo setpages spawn &6Page 1|&7punch me;;#ANIM:wave|&ePage 2
/yapholo attach spawn npc:shop:0.25
/yapholo click spawn LEFT:NEXT RIGHT:CONSOLE:say %player_name% clicked
/yapholo see spawn yapholo.see.vip
/yapholo move spawn at world 8 70 8
/yapholo list json
/yapholo status|reload
```

Aliases: `/holo`, `/hd`, `/hologram`. Permission: `yapholo.admin`.

## Line syntax

| Prefix | Effect |
|--------|--------|
| ordinary text | Color codes (`&6`) + tokens (`%player_name%`, `%online%`, `%world%`, `%player_x%`…) + PlaceholderAPI |
| `#ICON:DIAMOND` / `#ITEM:diamond_sword` | Floating item (Item Display, armor-stand name fallback) |
| `#ANIM:wave` | Named clip from `plugins/YaPHolo/animations.yml` |

Pages: separate with `;;` in commands, or a blank line in the dashboard textarea. Each viewer has their own page (click `LEFT:NEXT` / `RIGHT:PREV` / `LEFT:PAGE:2`).

## Attach

Follow an NPC, player, or entity. Offset is extra height above the target's hitbox.

- `npc:shop:0.25`
- `player:<uuid>:0.4`
- `entity:<uuid>:0.2`
- `none`

YaPNpcs hides the vanilla nametag and creates `npcntag_<id>` attached to the NPC when `hologram-nametags: true`.

## Clicks

Punch (left / attack) or interact (right). Cooldown: `click-cooldown-ticks`.

- `LEFT:NEXT` `RIGHT:PREV` `LEFT:PAGE:2`
- `RIGHT:PLAYER:warp spawn` (player runs the command)
- `RIGHT:CONSOLE:say hi %player_name%`
- `clear`

## API

```java
HologramService holos = HologramServices.require();
Hologram h = holos.create("spawn", player.getLocation().add(0, 2, 0),
        List.of("&6Welcome %player_name%", "#ICON:DIAMOND", "#ANIM:wave"));
h.attach(HologramAttach.npc("shop", 0.25));
h.setClicks(List.of(
        new HologramClick(HologramClick.Side.LEFT, HologramClick.Action.NEXT, ""),
        HologramClick.parse("RIGHT:CONSOLE:say clicked")));
h.setPages(List.of(List.of("&6Page 1"), List.of("&ePage 2")));
```

Compile against `yap-holo-api`. Soft-depend `YaPHolo`. Persistence: `plugins/YaPHolo/holograms.yml`.

## Dashboard

**Configure → Holograms** (`GET/POST /api/holo`) lists, creates, moves, edits lines/pages, attach, clicks, and see-permission. Plugin YAML is on **Plugin settings → YaPHolo**.

## Config

`plugins/YaPHolo/config.yml` — enabled, persist, line-spacing, view-distance, `entity: text_display`, `placeholders`, `click-cooldown-ticks`.

Animations: `plugins/YaPHolo/animations.yml` (`wave`, `blink`, `scroll` shipped).

## Related

- [YAPLIB.md](YAPLIB.md) — packet intercept YaPHolo sits on
- [PLUGIN_COMPAT.md](PLUGIN_COMPAT.md) — DecentHolograms / HolographicDisplays redirect here
