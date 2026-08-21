#!/usr/bin/env python3
"""Generate YaPcore vanilla 26.2 config dumps (registry entry lists + full Update Tags NBT).

Requires:
  - Minecraft 26.2 server jar (bundler) at SERVER_JAR
  - registries.json from: java -DbundlerMainClass=net.minecraft.data.Main -jar SERVER_JAR --reports

Usage:
  SERVER_JAR=/path/to/server.jar REGISTRIES_JSON=/path/to/registries.json \\
    python3 scripts/generate-vanilla-protocol-26.2.py
"""
from __future__ import annotations

import gzip
import io
import json
import os
import struct
import zipfile
import collections
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources/protocol/vanilla/26.2"
JAVA_CATALOG = ROOT / "src/main/java/com/yapcore/protocol/java/VanillaRegistryCatalog.java"

SERVER_JAR = Path(os.environ.get("SERVER_JAR", "/tmp/server-26.2.jar"))
REGISTRIES_JSON = Path(os.environ.get("REGISTRIES_JSON", "/tmp/mc26reports/generated/reports/registries.json"))

SYNCED = {
    "banner_pattern": "BANNER_PATTERNS",
    "chat_type": "CHAT_TYPES",
    "damage_type": "DAMAGE_TYPES",
    "dimension_type": "DIMENSION_TYPES",
    "instrument": "INSTRUMENTS",
    "jukebox_song": "JUKEBOX_SONGS",
    "painting_variant": "PAINTING_VARIANTS",
    "sulfur_cube_archetype": "SULFUR_CUBE_ARCHETYPES",
    "timeline": "TIMELINES",
    "trim_material": "TRIM_MATERIALS",
    "trim_pattern": "TRIM_PATTERNS",
    "world_clock": "WORLD_CLOCKS",
    "cat_variant": "CAT_VARIANTS",
    "cat_sound_variant": "CAT_SOUND_VARIANTS",
    "chicken_variant": "CHICKEN_VARIANTS",
    "chicken_sound_variant": "CHICKEN_SOUND_VARIANTS",
    "cow_variant": "COW_VARIANTS",
    "cow_sound_variant": "COW_SOUND_VARIANTS",
    "frog_variant": "FROG_VARIANTS",
    "pig_variant": "PIG_VARIANTS",
    "pig_sound_variant": "PIG_SOUND_VARIANTS",
    "wolf_variant": "WOLF_VARIANTS",
    "wolf_sound_variant": "WOLF_SOUND_VARIANTS",
    "zombie_nautilus_variant": "ZOMBIE_NAUTILUS_VARIANTS",
    "enchantment": "ENCHANTMENTS",
    "dialog": "DIALOGS",
}

TAG_DIR_TO_REG = {
    "block": "minecraft:block",
    "item": "minecraft:item",
    "fluid": "minecraft:fluid",
    "entity_type": "minecraft:entity_type",
    "game_event": "minecraft:game_event",
    "potion": "minecraft:potion",
    "point_of_interest_type": "minecraft:point_of_interest_type",
    "damage_type": "minecraft:damage_type",
    "banner_pattern": "minecraft:banner_pattern",
    "enchantment": "minecraft:enchantment",
    "instrument": "minecraft:instrument",
    "painting_variant": "minecraft:painting_variant",
    "timeline": "minecraft:timeline",
    "dialog": "minecraft:dialog",
    "worldgen/biome": "minecraft:worldgen/biome",
}

ORDER = [
    "DIMENSION_TYPES", "BIOMES", "DAMAGE_TYPES", "CHAT_TYPES", "WORLD_CLOCKS", "TIMELINES",
    "PAINTING_VARIANTS", "CAT_VARIANTS", "CAT_SOUND_VARIANTS", "CHICKEN_VARIANTS", "CHICKEN_SOUND_VARIANTS",
    "COW_VARIANTS", "COW_SOUND_VARIANTS", "FROG_VARIANTS", "PIG_VARIANTS", "PIG_SOUND_VARIANTS",
    "WOLF_VARIANTS", "WOLF_SOUND_VARIANTS", "ZOMBIE_NAUTILUS_VARIANTS", "SULFUR_CUBE_ARCHETYPES",
    "INSTRUMENTS", "JUKEBOX_SONGS", "TRIM_MATERIALS", "TRIM_PATTERNS", "BANNER_PATTERNS",
    "ENCHANTMENTS", "DIALOGS",
]


def open_inner(server_jar: Path) -> zipfile.ZipFile:
    outer = zipfile.ZipFile(server_jar)
    inner = next(n for n in outer.namelist() if n.startswith("META-INF/versions/") and n.endswith(".jar"))
    return zipfile.ZipFile(io.BytesIO(outer.read(inner)))


def list_entries(z: zipfile.ZipFile, reg_path: str) -> list[str]:
    prefix = f"data/minecraft/{reg_path}/"
    depth = prefix.count("/")
    out = []
    for n in z.namelist():
        if n.startswith(prefix) and n.endswith(".json") and n.count("/") == depth:
            eid = n[len(prefix) : -5]
            if "/" not in eid:
                out.append(f"minecraft:{eid}")
    return sorted(set(out))


def enc_utf(s: str) -> bytes:
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def write_compound(entries: list[tuple[str, int, bytes]]) -> bytes:
    out = bytearray()
    for name, typ, payload in entries:
        out.append(typ)
        out += enc_utf(name)
        out += payload
    out.append(0)
    return bytes(out)


def int_array_payload(arr: list[int]) -> bytes:
    return struct.pack(">i", len(arr)) + b"".join(struct.pack(">i", x) for x in arr)


def main() -> None:
    if not SERVER_JAR.is_file():
        raise SystemExit(f"Missing SERVER_JAR: {SERVER_JAR}")
    if not REGISTRIES_JSON.is_file():
        raise SystemExit(f"Missing REGISTRIES_JSON: {REGISTRIES_JSON}")

    regs_report = json.loads(REGISTRIES_JSON.read_text())
    z = open_inner(SERVER_JAR)
    RES.mkdir(parents=True, exist_ok=True)

    catalog: dict[str, list[str]] = {}
    for reg, field in SYNCED.items():
        catalog[field] = list_entries(z, reg)
    catalog["BIOMES"] = list_entries(z, "worldgen/biome")

    lines = [
        "package com.yapcore.protocol.java;",
        "",
        "/**",
        " * Full synchronized-registry entry IDs for Minecraft 26.2 (from official server jar).",
        " * Regenerated by {@code scripts/generate-vanilla-protocol-26.2.py}.",
        " */",
        "public final class VanillaRegistryCatalog {",
        "",
        "    private VanillaRegistryCatalog() {}",
        "",
    ]
    for field in ORDER:
        arr = catalog[field]
        lines.append(f"    public static final String[] {field} = {{")
        for i in range(0, len(arr), 3):
            chunk = ", ".join(f'"{x}"' for x in arr[i : i + 3])
            lines.append(f"            {chunk},")
        lines.append("    };")
        lines.append("")
    lines.append("}")
    JAVA_CATALOG.write_text("\n".join(lines) + "\n")

    def protocol_ids(reg_key: str) -> dict[str, int]:
        return {k: v["protocol_id"] for k, v in regs_report[reg_key]["entries"].items()}

    id_maps: dict[str, dict[str, int]] = {
        "minecraft:block": protocol_ids("minecraft:block"),
        "minecraft:item": protocol_ids("minecraft:item"),
        "minecraft:fluid": protocol_ids("minecraft:fluid"),
        "minecraft:entity_type": protocol_ids("minecraft:entity_type"),
        "minecraft:game_event": protocol_ids("minecraft:game_event"),
        "minecraft:potion": protocol_ids("minecraft:potion"),
        "minecraft:point_of_interest_type": protocol_ids("minecraft:point_of_interest_type"),
        "minecraft:damage_type": {n: i for i, n in enumerate(catalog["DAMAGE_TYPES"])},
        "minecraft:banner_pattern": {n: i for i, n in enumerate(catalog["BANNER_PATTERNS"])},
        "minecraft:worldgen/biome": {n: i for i, n in enumerate(catalog["BIOMES"])},
        "minecraft:enchantment": {n: i for i, n in enumerate(catalog["ENCHANTMENTS"])},
        "minecraft:instrument": {n: i for i, n in enumerate(catalog["INSTRUMENTS"])},
        "minecraft:painting_variant": {n: i for i, n in enumerate(catalog["PAINTING_VARIANTS"])},
        "minecraft:timeline": {n: i for i, n in enumerate(catalog["TIMELINES"])},
        "minecraft:dialog": {n: i for i, n in enumerate(catalog["DIALOGS"])},
    }

    def tag_files():
        for n in z.namelist():
            if not (n.startswith("data/minecraft/tags/") and n.endswith(".json")):
                continue
            rest = n[len("data/minecraft/tags/") : -5]
            if rest.startswith("worldgen/"):
                parts = rest.split("/")
                if len(parts) < 3:
                    continue
                reg_dir = parts[0] + "/" + parts[1]
                tag_name = "/".join(parts[2:])
            else:
                parts = rest.split("/")
                reg_dir = parts[0]
                tag_name = "/".join(parts[1:])
            if reg_dir not in TAG_DIR_TO_REG:
                continue
            yield TAG_DIR_TO_REG[reg_dir], reg_dir, tag_name, n

    def flatten(path: str, reg_dir: str, idmap: dict[str, int], seen: set[str] | None = None) -> list[int]:
        if seen is None:
            seen = set()
        if path in seen:
            return []
        seen.add(path)
        try:
            data = json.loads(z.read(path))
        except KeyError:
            return []
        ids: list[int] = []
        for v in data.get("values", []):
            if isinstance(v, dict):
                v = v.get("id")
            if not isinstance(v, str):
                continue
            if v.startswith("#"):
                ref = v[1:]
                ns, name = (ref.split(":", 1) if ":" in ref else ("minecraft", ref))
                ids.extend(flatten(f"data/{ns}/tags/{reg_dir}/{name}.json", reg_dir, idmap, seen))
            else:
                if ":" not in v:
                    v = "minecraft:" + v
                if v in idmap:
                    ids.append(idmap[v])
        return ids

    all_tags: dict[str, collections.OrderedDict] = collections.OrderedDict()
    for reg_key, reg_dir, tag_name, path in sorted(tag_files()):
        ids = flatten(path, reg_dir, id_maps[reg_key])
        seen: set[int] = set()
        uniq = []
        for i in ids:
            if i not in seen:
                seen.add(i)
                uniq.append(i)
        all_tags.setdefault(reg_key, collections.OrderedDict())[f"minecraft:{tag_name}"] = uniq

    reg_entries = []
    for reg_key, tags in all_tags.items():
        tag_entries = [(tag_id, 11, int_array_payload(ids)) for tag_id, ids in tags.items()]
        reg_entries.append((reg_key, 10, write_compound(tag_entries)))

    root = bytearray()
    root.append(10)
    root += enc_utf("")
    root += write_compound(reg_entries)

    gz_path = RES / "networkTags.nbt"
    with gzip.open(gz_path, "wb", compresslevel=9) as g:
        g.write(root)

    (RES / "registryEntries.json").write_text(json.dumps({f: catalog[f] for f in ORDER}, indent=2))
    print(f"Wrote {JAVA_CATALOG}")
    print(f"Wrote {gz_path} ({gz_path.stat().st_size} bytes, {sum(len(v) for v in all_tags.values())} tags)")


if __name__ == "__main__":
    main()
