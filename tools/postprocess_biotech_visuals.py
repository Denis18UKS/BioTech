#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/biotech"
MULTI = ASSETS / "textures/entity/multiblock"
HAND_MODEL = ASSETS / "models/item/dnk_injector_hand.json"

PALETTES = {
    "dna_synthesizer": ((19, 25, 26), (47, 58, 58), (69, 224, 132), (172, 255, 204)),
    "dna_mixer": ((16, 24, 27), (43, 61, 65), (43, 207, 200), (157, 255, 247)),
    "dna_hybridizer": ((24, 18, 29), (64, 47, 71), (186, 72, 219), (241, 166, 255)),
    "dna_integrator": ((17, 25, 25), (51, 65, 62), (52, 228, 163), (174, 255, 220)),
    "bioreactor": ((17, 26, 21), (51, 67, 57), (75, 226, 106), (178, 255, 188)),
}


def shade(rgb: tuple[int, int, int], factor: float) -> tuple[int, int, int, int]:
    return tuple(max(0, min(255, int(c * factor))) for c in rgb) + (255,)


def stable_seed(text: str) -> int:
    return int(hashlib.sha256(text.encode("utf-8")).hexdigest()[:8], 16)


def panel_tile(name: str, index: int, palette: tuple[tuple[int, int, int], ...]) -> Image.Image:
    base, metal, accent, shine = palette
    img = Image.new("RGBA", (32, 32), metal + (255,))
    d = ImageDraw.Draw(img)
    s = stable_seed(f"{name}:{index}")

    d.rectangle((0, 0, 31, 31), fill=base + (255,))
    d.rectangle((1, 1, 30, 30), fill=shade(metal, 0.70), outline=shade(metal, 1.65), width=1)
    d.rectangle((4, 4, 27, 27), fill=metal + (255,), outline=shade(metal, 1.25), width=1)
    d.line((5, 26, 26, 26), fill=shade(base, 0.55), width=2)

    for x, y in ((3, 3), (28, 3), (3, 28), (28, 28)):
        d.rectangle((x - 1, y - 1, x + 1, y + 1), fill=shade(metal, 1.75))
        d.point((x, y), fill=shade(base, 0.55))

    mode = index % 6
    if mode == 0:
        d.rectangle((7, 9, 24, 22), fill=shade(base, 0.62), outline=shade(accent, 0.62))
        d.rectangle((9, 12, 22, 15), fill=shade(accent, 0.35))
        d.line((10, 13, 21, 13), fill=shine, width=1)
        d.rectangle((10, 18, 13, 20), fill=accent + (255,))
        d.rectangle((17, 18, 20, 20), fill=shade(accent, 0.55))
    elif mode == 1:
        d.rectangle((7, 7, 24, 24), fill=shade(base, 0.70), outline=shade(metal, 1.35))
        for y in range(9, 24, 3):
            d.line((9, y, 22, y), fill=shade(metal, 1.65))
            d.line((9, y + 1, 22, y + 1), fill=shade(base, 0.45))
        d.rectangle((6, 25, 25, 27), fill=accent + (255,))
    elif mode == 2:
        d.polygon([(6, 7), (18, 7), (26, 15), (26, 25), (14, 25), (6, 17)],
                  fill=shade(metal, 0.78), outline=shade(metal, 1.45))
        d.line((7, 23, 23, 7), fill=accent + (255,), width=2)
        d.line((10, 25, 26, 9), fill=shade(accent, 0.45), width=1)
    elif mode == 3:
        d.rectangle((6, 8, 25, 23), fill=shade(base, 0.58), outline=shade(metal, 1.35))
        for x in range(8, 24, 4):
            level = 10 + ((s >> x) & 0x7)
            d.rectangle((x, level, x + 2, 21), fill=shade(accent, 0.65))
            d.line((x, level, x + 2, level), fill=shine)
        d.rectangle((7, 25, 24, 27), fill=shade(metal, 1.35))
    elif mode == 4:
        d.rectangle((7, 7, 24, 24), fill=shade(base, 0.60), outline=shade(accent, 0.50))
        points = [(10, 17), (13, 11), (17, 20), (21, 12)]
        for a, b in zip(points, points[1:]):
            d.line((*a, *b), fill=accent + (255,), width=2)
        for x, y in points:
            d.rectangle((x - 1, y - 1, x + 1, y + 1), fill=shine)
    else:
        d.rectangle((6, 7, 25, 24), fill=shade(base, 0.62), outline=shade(metal, 1.30))
        for offset in range(-8, 32, 7):
            d.line((6 + offset, 24, 17 + offset, 7), fill=accent + (255,), width=3)
        d.rectangle((6, 18, 25, 24), fill=shade(base, 0.45))
        d.rectangle((9, 20, 22, 22), fill=shade(accent, 0.42))

    return img


def make_machine_texture(name: str, palette: tuple[tuple[int, int, int], ...]) -> Image.Image:
    atlas = Image.new("RGBA", (256, 256), palette[0] + (255,))
    for row in range(8):
        for col in range(8):
            atlas.alpha_composite(panel_tile(name, row * 8 + col, palette), (col * 32, row * 32))
    return atlas


def make_window() -> Image.Image:
    img = Image.new("RGBA", (64, 64), (9, 38, 35, 255))
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, 63, 63), fill=(8, 24, 24, 255), outline=(106, 128, 124, 255), width=3)
    d.rectangle((5, 5, 58, 58), fill=(8, 70, 63, 255), outline=(101, 236, 193, 255), width=2)
    d.rectangle((9, 9, 54, 54), fill=(10, 91, 79, 255), outline=(29, 146, 121, 255), width=1)
    for x in range(-24, 80, 12):
        d.line((x, 54, x + 32, 9), fill=(66, 192, 157, 255), width=1)
    d.line((12, 13, 39, 13), fill=(193, 255, 231, 255), width=2)
    d.rectangle((22, 24, 41, 41), outline=(67, 255, 166, 255), width=2)
    d.line((25, 36, 37, 27), fill=(127, 255, 190, 255), width=2)
    return img


def make_port() -> Image.Image:
    img = Image.new("RGBA", (64, 64), (13, 18, 19, 255))
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, 63, 63), fill=(15, 22, 23, 255), outline=(133, 146, 143, 255), width=3)
    d.rectangle((6, 6, 57, 57), fill=(26, 36, 36, 255), outline=(70, 84, 82, 255), width=2)
    d.rectangle((12, 12, 51, 51), fill=(7, 30, 23, 255), outline=(62, 234, 133, 255), width=3)
    d.rectangle((18, 18, 45, 45), fill=(12, 89, 49, 255), outline=(155, 255, 191, 255), width=2)
    d.rectangle((24, 24, 39, 39), fill=(63, 230, 117, 255), outline=(202, 255, 216, 255), width=2)
    d.rectangle((28, 17, 35, 28), fill=(13, 27, 25, 255), outline=(208, 226, 221, 255))
    d.line((31, 28, 31, 45), fill=(219, 235, 230, 255), width=3)
    d.line((35, 28, 35, 45), fill=(83, 255, 139, 255), width=3)
    return img


def make_glow() -> Image.Image:
    img = Image.new("RGBA", (64, 64), (5, 28, 18, 255))
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, 63, 63), fill=(6, 31, 21, 255))
    for inset, col in ((3, (28, 92, 52, 255)), (8, (39, 151, 75, 255)), (14, (64, 225, 109, 255))):
        d.rectangle((inset, inset, 63 - inset, 63 - inset), outline=col, width=3)
    d.line((13, 32, 51, 32), fill=(194, 255, 209, 255), width=3)
    d.line((32, 13, 32, 51), fill=(194, 255, 209, 255), width=3)
    d.ellipse((25, 25, 39, 39), fill=(86, 255, 137, 255), outline=(223, 255, 230, 255), width=2)
    return img


def fix_injector_pose() -> None:
    model = json.loads(HAND_MODEL.read_text(encoding="utf-8"))
    display = model.setdefault("display", {})
    display["thirdperson_righthand"] = {
        "rotation": [0, 0, 0],
        "translation": [0, 1.8, 1.0],
        "scale": [0.70, 0.70, 0.70],
    }
    display["thirdperson_lefthand"] = {
        "rotation": [0, 0, 0],
        "translation": [0, 1.8, 1.0],
        "scale": [0.70, 0.70, 0.70],
    }
    HAND_MODEL.write_text(json.dumps(model, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    MULTI.mkdir(parents=True, exist_ok=True)
    for name, palette in PALETTES.items():
        make_machine_texture(name, palette).save(MULTI / f"{name}.png", optimize=True)
    make_window().save(MULTI / "window.png", optimize=True)
    make_port().save(MULTI / "port.png", optimize=True)
    make_glow().save(MULTI / "glow.png", optimize=True)
    fix_injector_pose()
    print("Applied solid multiblock redesign and third-person injector alignment.")


if __name__ == "__main__":
    main()
