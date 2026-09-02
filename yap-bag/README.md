# yap-bag

Fabric **client** mod for Minecraft **26.2**. It does not go on YaPcore or Folia.

YaPPlayerData `/bag` already works for vanilla Java and Bedrock. This mod only
adds a nicer way to open the **same** extra storage:

- **B** (Controls → Inventory → Open YaP bag)
- **Bag** button on the survival inventory
- Page tabs on the bag chest (`YaP Bag · 1/3`)

Players without the mod keep `/bag`, `/backpack`, `/bp`, and the `/menu` Bag icon.

## Install

1. Fabric Loader **0.19.3+** for Minecraft **26.2**
2. Drop `yap-bag-1.0.0.jar` into `.minecraft/mods/`
3. Join YaPcore / Folia as usual (vanilla protocol)

## Build

```bash
cd yap-bag
./gradlew build
```

Jar: `build/libs/yap-bag-1.0.0.jar`

## Config

Written on first launch: `.minecraft/config/yap-bag.json`

| Key | Default | Meaning |
|-----|---------|---------|
| `enabled` | `true` | Master switch |
| `inventoryTab` | `true` | Bag button on the E inventory |
| `chestTabs` | `true` | Page tabs on the bag chest |
