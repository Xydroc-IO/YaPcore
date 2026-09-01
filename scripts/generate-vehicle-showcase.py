#!/usr/bin/env python3
"""Render YaP Vehicles HD model previews for the showcase gallery."""
from __future__ import annotations

import json
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "resourcepacks" / "yap-vehicles"
MODELS = PACK / "assets" / "yapvehicles" / "models" / "vehicle"
TEXTURES = PACK / "assets" / "yapvehicles" / "textures" / "entity"
OUT = PACK / "showcase"

VEHICLES = [
    ("chassis", "YaP Chassis", "Developer frame · CMD 77200"),
    ("buggy", "Buggy", "Off-road · CMD 77201"),
    ("truck_4x4", "4×4 Truck", "Utility · CMD 77202"),
    ("monster_truck", "Monster Truck", "Oversized · CMD 77203"),
    ("sport_car", "Sport Car", "Track · CMD 77204"),
    ("hypercar", "Hypercar", "Top speed · CMD 77205"),
    ("lambo", "Lambo SV", "Exotic · CMD 77206"),
    ("ferrari", "Ferrari XX", "Exotic · CMD 77207"),
    ("mclaren", "McLaren GT", "Exotic · CMD 77208"),
    ("porsche", "Porsche Turbo", "Sports · CMD 77209"),
    ("hoverbike", "Hoverbike", "Hover · CMD 77210"),
]

CANVAS = 640
MARGIN = 48
BG = (18, 24, 32)
ACCENT = (90, 168, 255)
LABEL = (220, 228, 236)
MUTED = (140, 152, 168)


def load_font(size: int) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    for name in (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/TTF/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf",
    ):
        p = Path(name)
        if p.is_file():
            return ImageFont.truetype(str(p), size)
    return ImageFont.load_default()


def parse_texture_key(model: dict) -> str:
    textures = model.get("textures") or {}
    key = textures.get("0") or textures.get("particle") or ""
    if ":" in key:
        key = key.split(":", 1)[1]
    if key.startswith("entity/"):
        key = key[len("entity/") :]
    return key


def face_corners(direction: str, x1: float, y1: float, z1: float, x2: float, y2: float, z2: float):
    if direction == "down":
        y = y1
        return [(x1, y, z1), (x2, y, z1), (x2, y, z2), (x1, y, z2)]
    if direction == "up":
        y = y2
        return [(x1, y, z2), (x2, y, z2), (x2, y, z1), (x1, y, z1)]
    if direction == "north":
        z = z1
        return [(x2, y1, z), (x1, y1, z), (x1, y2, z), (x2, y2, z)]
    if direction == "south":
        z = z2
        return [(x1, y1, z), (x2, y1, z), (x2, y2, z), (x1, y2, z)]
    if direction == "west":
        x = x1
        return [(x, y1, z2), (x, y1, z1), (x, y2, z1), (x, y2, z2)]
    if direction == "east":
        x = x2
        return [(x, y1, z1), (x, y1, z2), (x, y2, z2), (x, y2, z1)]
    return []


def project(x: float, y: float, z: float, scale: float) -> tuple[float, float]:
    # Isometric-ish: rotate 45° around Y, tilt down slightly.
    rad = math.radians(35)
    xr = (x - 8) * math.cos(math.radians(45)) - (z - 8) * math.sin(math.radians(45))
    zr = (x - 8) * math.sin(math.radians(45)) + (z - 8) * math.cos(math.radians(45))
    sx = xr * scale
    sy = zr * scale * 0.55 - (y - 4) * scale * math.sin(rad)
    return sx, sy


def face_depth(corners: list[tuple[float, float, float]]) -> float:
    return sum(c[0] + c[2] - c[1] for c in corners) / len(corners)


def sample_face(tex: Image.Image, uv: list[float], u: int, v: int) -> Image.Image:
    tw, th = tex.size
    u1, v1, u2, v2 = uv
    # Minecraft UVs are in 0–16 pixel space on the atlas.
    px1 = int(min(u1, u2) / 16 * tw)
    py1 = int(min(v1, v2) / 16 * th)
    px2 = int(max(u1, u2) / 16 * tw)
    py2 = int(max(v1, v2) / 16 * th)
    if px2 <= px1:
        px2 = px1 + 1
    if py2 <= py1:
        py2 = py1 + 1
    patch = tex.crop((px1, py1, px2, py2)).resize((max(u, 1), max(v, 1)), Image.NEAREST)
    return patch


def draw_face(
    canvas: Image.Image,
    tex: Image.Image,
    corners3: list[tuple[float, float, float]],
    uv: list[float],
    scale: float,
    cx: float,
    cy: float,
) -> None:
    pts2 = [project(x, y, z, scale) for x, y, z in corners3]
    xs = [p[0] + cx for p in pts2]
    ys = [p[1] + cy for p in pts2]
    min_x, max_x = min(xs), max(xs)
    min_y, max_y = min(ys), max(ys)
    w = max(int(max_x - min_x), 1)
    h = max(int(max_y - min_y), 1)
    patch = sample_face(tex, uv, w, h)
    quad = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    p = Image.new("RGBA", (w, h))
    p.paste(patch, (0, 0))
    # Affine map unit square → quad (approximate with bbox blit for pixel look).
    overlay = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    overlay.paste(p, (int(min_x), int(min_y)))
    canvas.alpha_composite(overlay)


def render_model(model_path: Path, tex_path: Path) -> Image.Image:
    model = json.loads(model_path.read_text(encoding="utf-8"))
    tex = Image.open(tex_path).convert("RGBA")
    faces: list[tuple[float, str, list, list]] = []
    for el in model.get("elements") or []:
        x1, y1, z1 = el["from"]
        x2, y2, z2 = el["to"]
        for direction, face in (el.get("faces") or {}).items():
            if not face or face.get("texture") is None:
                continue
            corners = face_corners(direction, x1, y1, z1, x2, y2, z2)
            if not corners:
                continue
            uv = face.get("uv") or [0, 0, 16, 16]
            faces.append((face_depth(corners), direction, corners, uv))
    faces.sort(key=lambda f: f[0])

    img = Image.new("RGBA", (CANVAS, CANVAS), (*BG, 255))
    scale = 14.0
    cx, cy = CANVAS / 2, CANVAS * 0.58
    for _, _, corners, uv in faces:
        draw_face(img, tex, corners, uv, scale, cx, cy)
    return img


def compose_card(preview: Image.Image, title: str, subtitle: str) -> Image.Image:
    card = Image.new("RGBA", (CANVAS, CANVAS + 72), (*BG, 255))
    card.paste(preview, (0, 0))
    draw = ImageDraw.Draw(card)
    title_font = load_font(26)
    sub_font = load_font(15)
    draw.text((MARGIN, CANVAS + 14), title, fill=ACCENT, font=title_font)
    draw.text((MARGIN, CANVAS + 44), subtitle, fill=MUTED, font=sub_font)
    return card


def render_vehicle(vehicle_id: str) -> Image.Image:
    model_path = MODELS / f"{vehicle_id}.json"
    if not model_path.is_file():
        raise FileNotFoundError(model_path)
    model = json.loads(model_path.read_text(encoding="utf-8"))
    tex_name = parse_texture_key(model)
    tex_path = TEXTURES / f"{tex_name}.png"
    if not tex_path.is_file():
        tex_path = TEXTURES / f"{vehicle_id}.png"
    preview = render_model(model_path, tex_path)
    return preview


def write_readme() -> None:
    lines = [
        "# YaP Vehicles — model showcase",
        "",
        "Demo previews of the **high-res ItemDisplay bodies** shipped in the",
        "`yap-vehicles` resource pack (CustomModelData **77200–77210**).",
        "",
        "These PNGs are **marketing / docs previews** — not loaded by Minecraft.",
        "In-game art lives under `assets/yapvehicles/models/vehicle/` and",
        "`assets/yapvehicles/textures/entity/`.",
        "",
        "## Gallery",
        "",
        "| Preview | Id | In-game name |",
        "|---------|-----|--------------|",
    ]
    for vid, name, note in VEHICLES:
        cmd = note.split("CMD ")[-1].rstrip(")")
        lines.append(f"| ![{name}]({vid}.png) | `{vid}` | {name} · CMD {cmd} |")
    lines.extend(
        [
            "",
            "## Regenerate",
            "",
            "```bash",
            "python3 scripts/generate-vehicle-showcase.py",
            "```",
            "",
            "Requires `python3` + Pillow (`pip install pillow`).",
            "",
            "See [VEHICLES.md](../../docs/plugins/VEHICLES.md) for spawn commands and physics.",
        ]
    )
    (OUT / "README.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for vid, name, subtitle in VEHICLES:
        card = compose_card(render_vehicle(vid), name, subtitle)
        out = OUT / f"{vid}.png"
        card.save(out, optimize=True)
        print(f"  {out.relative_to(ROOT)}")
    write_readme()
    print(f"\nWrote {len(VEHICLES)} previews → {OUT.relative_to(ROOT)}/")


if __name__ == "__main__":
    main()
