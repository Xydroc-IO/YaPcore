# How to use YaPItems

**Jar:** `yap-items.jar`  
Custom items: YAML catalog, abilities, furniture, recipes, CMD/resource-pack looks, create wizards.

## What it does

Defines items like YaP420 goods, VIP tools, rainbow gear, furniture props. Players use them; staff create/give via CLI, YaPAdmin, or Fabric yap-staff.

## Give & browse

```text
/yapitems list
/yapitems list sword
/yapitems info yap420_seed_sativa
/yapitems give yap420_joint_sativa 8
/yapitems give rainbow_blade 1 Steve
/yapitems gui
/yapitems reload
```

Alias: `/yitems`.

## Create a custom item

```text
/yapitems create my_blade --template sword --name &cMy Blade --glow --ability DAMAGE --trigger RIGHT_CLICK --damage 12 --cooldown 5s
```

Writes `items/custom/my_blade.yml` and **propagates** across fleet when `fleet.propagate-custom: true`.

Useful flags: `--nameb64`, `--abilities`, `--enchants sharpness:5`, `--furniture`, `--rainbow`, `--unbreakable`, `--gear-attack`.

Full flag list: [YAPITEMS.md](../../plugins/YAPITEMS.md).

## Resource pack

1. Add textures under `resourcepacks/yap-items/`  
2. `./scripts/packs/build-default-resourcepack.sh`  
3. Update fleet `resource-pack-sha1`  
4. Restart / reconnect and accept pack  

Without pack: purple/black missing textures.

## Fleet sync

- Definitions: catalog copy + watch reload  
- Stacks: PlayerData global inventory profile  
Minigame servers: use `inventory-profile: server` so survival gear is not shared.

## Related

[YAPITEMS.md](../../plugins/YAPITEMS.md) · [CLIENTS_AND_PACKS.md](../../network/CLIENTS_AND_PACKS.md) · [yap420.md](yap420.md) · [yapqol.md](yapqol.md)
