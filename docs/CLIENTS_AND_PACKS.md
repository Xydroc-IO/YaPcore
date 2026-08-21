# Dual-stack clients & resource packs

## Game authority (Paper → YapEngine)

**Product path:** Paper game + Phase 3 tick on YapEngine cores 3–6 (**done**).  
Phase 4: polish dual-stack + YaP plugins on that world.  
See [PAPER_YAPENGINE_PORT.md](PAPER_YAPENGINE_PORT.md).

```properties
game-authority=paper
paper-embed=true
paper-phase3-tick-bridge=true
paper-phase3-nms-tick=true
paper-version=26.2
paper-dir=paper-kernel
```

| Value | Meaning |
|-------|---------|
| `paper` + `paper-embed=true` | Default — Paper owns JE; Phase 3 same-JVM when bridge on |
| `paper-phase3-nms-tick=true` | Interior NMS entity tick on cores 3–6 (**requires** `lib/paper-*-yap.jar`; boot fails if missing) |
| `native` | Experimental YapEngine flat world |
| `mojang` | Legacy Mojang wrap |

**Java 25+** for Paper 26.2. Prefer YaP Paperclip:

```bash
./scripts/vendor-paper.sh
./scripts/build-vendor-paper.sh   # → lib/paper-26.2-yap.jar
./scripts/start.sh --fg           # cds into paper-kernel
```

## Built-in multi-version (native authority only)

When `game-authority=native`, YaPcore speaks JE bands natively. With Paper,
the game process owns the protocol after join.

## Java + Bedrock

| Edition | Who binds | Notes |
|---------|-----------|--------|
| Java | Paper (when embed / Phase 3) | Public `port` |
| Bedrock | YaPcore gateway | UDP shared or separate |

Shared listen port (`shared-listen-port=true`) uses the same number for JE TCP and BE UDP. See [CROSSPLAY.md](CROSSPLAY.md).

## Resource packs

```properties
resource-pack-http-port=8081
```

Packs live under `resourcepacks/` and are served over the built-in HTTP host.
