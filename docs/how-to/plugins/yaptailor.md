# How to use YaPTailor

**Jar:** `yap-tailor.jar`  
Skins, wardrobe, emotes, and `yap:presence` channel for Bedrock-feel parity.

## Players

```text
/skin
/wardrobe
/emote
```

## Other players see the wrong skin?

You see your own skin via **yap-presence** (local optimistic apply). Everyone else
downloads `{skin-host}/skin/{uuid}.png` from the chassis pack HTTP — or whatever
URL is stored in Tailor's DB.

### Checklist

1. `plugins/YaPTailor/config.yml` → `skin-host-public-base-url: "http://YOUR_PUBLIC_HOST:8081"`  
   (same host as `resource-pack-public-host`, no trailing `/skin`)
2. Restart **YaPcore GUI** so `POST /skin/apply` is wired (otherwise Tailor logs HTTP 503)
3. Restart the Folia instance so Tailor reloads config + jar
4. Re-apply the skin in wardrobe — watch for a red error; success updates DB away from
   `textures.minecraft.net`

If DB `tailor_active.skin_url` still points at Mojang, other players will keep seeing
your Mojang skin no matter what mods they run.

**Why others see a random/wrong skin (not Tailor, not your account):**  
On join, Velocity gives them your real Mojang-signed skin. ~1s later Tailor was
**overwriting** that with an unsigned `YaPTailor` property. Other clients reject it
and fall back to Steve/Alex (or a cached oddity) — so it looks like neither Tailor
nor your account. You still looked fine because yap-presence overlays locally.

Fixed: Tailor never applies unsigned YaPTailor properties; Mojang URLs get a
signed refresh or leave login textures alone.

## Related

[BEDROCK_FEEL_PARITY.md](../../product/BEDROCK_FEEL_PARITY.md)
