"""
The season trophy "Krone der Belagerung" (design update, Simon 29 October 2026): a gold crown with
five points, red and blue gems and a dark red velvet cap, 32x32 like the kit weapons (kit_v3.finish).
Also a 3D model (crown on a cushion) for showing it on the core and when it is set up.

python3 tools/weapons/crown.py [--install]
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import kit_v3 as K  # noqa: E402

from PIL import Image

GOLD = (214, 168, 58, 255)
GOLD_D = (160, 116, 34, 255)
GOLD_L = (246, 214, 112, 255)
RED = (200, 30, 40, 255)
BLUE = (50, 100, 210, 255)
WHITE = (240, 236, 226, 255)
VELVET = (120, 20, 36, 255)
VELVET_D = (84, 12, 24, 255)


def draw():
    px = {}

    def rect(x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                px[(x, y)] = c

    # velvet cap behind the points
    for y in range(9, 19):
        half = int(9 - (18 - y) * 0.15)
        rect(16 - half, y, 15 + half, y, VELVET if y < 15 else VELVET_D)
    # five points with balls on top
    for cx, top in ((5, 8), (10, 6), (16, 4), (22, 6), (27, 8)):
        for y in range(top + 2, 20):
            w = max(0, (y - top - 2) // 3)
            rect(cx - 1 - w // 2, y, cx + w // 2, y, GOLD)
        rect(cx - 1, top, cx, top + 1, GOLD_L)
    # the band
    rect(3, 19, 28, 25, GOLD)
    rect(3, 19, 28, 19, GOLD_L)
    rect(3, 25, 28, 25, GOLD_D)
    # gems on the band
    for cx, c in ((8, BLUE), (16, RED), (24, BLUE)):
        rect(cx - 1, 21, cx + 1, 23, c)
        px[(cx - 1, 21)] = WHITE
    # pearls along the bottom edge
    for x in range(4, 28, 3):
        px[(x, 26)] = WHITE
    return px


def picture():
    out = K.finish(draw(), 77)
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    for (x, y), c in out.items():
        if 0 <= x < 32 and 0 <= y < 32:
            im.putpixel((x, y), c)
    return im


def model3d():
    """The crown on a cushion: a red cushion with gold tassels, the crown as a ring of plates."""
    def cube(f, t, tex, uv=(0, 0, 16, 16)):
        faces = {d: {"uv": list(uv), "texture": tex} for d in ("north", "south", "east", "west", "up", "down")}
        return {"from": f, "to": t, "faces": faces}

    el = [cube([2, 0, 2], [14, 3, 14], "#cushion"),
          cube([1, 0, 1], [2, 2, 2], "#gold"), cube([14, 0, 1], [15, 2, 2], "#gold"),
          cube([1, 0, 14], [2, 2, 15], "#gold"), cube([14, 0, 14], [15, 2, 15], "#gold"),
          # the band: four plates
          cube([4, 3, 4], [12, 6, 5], "#gold"), cube([4, 3, 11], [12, 6, 12], "#gold"),
          cube([4, 3, 5], [5, 6, 11], "#gold"), cube([11, 3, 5], [12, 6, 11], "#gold"),
          cube([5, 3, 5], [11, 7, 11], "#velvet")]
    for x, z in ((4, 4), (7.5, 4), (11, 4), (4, 11), (7.5, 11), (11, 11), (4, 7.5), (11, 7.5)):
        el.append(cube([x, 6, z], [x + 1, 8.5, z + 1], "#gold"))
    el.append(cube([7.5, 4, 3.6], [8.5, 5, 4], "#gem"))
    return {"textures": {"cushion": "minecraft:block/red_wool", "gold": "minecraft:block/gold_block",
                         "velvet": "minecraft:block/red_concrete", "gem": "minecraft:block/redstone_block",
                         "particle": "minecraft:block/gold_block"},
            "elements": el,
            "display": {"fixed": {"scale": [1, 1, 1]}, "ground": {"scale": [0.5, 0.5, 0.5]},
                        "gui": {"rotation": [30, 225, 0], "scale": [0.625, 0.625, 0.625]}}}


if __name__ == "__main__":
    root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
    im = picture()
    os.makedirs(os.path.join(root, "build"), exist_ok=True)
    im.resize((256, 256), Image.NEAREST).save(os.path.join(root, "build", "crown_preview.png"))
    if "--install" in sys.argv:
        a = os.path.join(root, "src", "main", "resources", "assets", "nightsiege")
        for d in ("textures/item/trophy", "models/item/trophy", "items/trophy"):
            os.makedirs(os.path.join(a, d), exist_ok=True)
        im.save(os.path.join(a, "textures/item/trophy/siege_crown.png"))
        with open(os.path.join(a, "models/item/trophy/siege_crown.json"), "w") as f:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "nightsiege:item/trophy/siege_crown"}}, f, indent=2)
        with open(os.path.join(a, "items/trophy/siege_crown.json"), "w") as f:
            json.dump({"model": {"type": "minecraft:model", "model": "nightsiege:item/trophy/siege_crown"}}, f, indent=2)
        with open(os.path.join(a, "models/item/trophy/siege_crown_3d.json"), "w") as f:
            json.dump(model3d(), f, indent=1)
        with open(os.path.join(a, "items/trophy/siege_crown_3d.json"), "w") as f:
            json.dump({"model": {"type": "minecraft:model", "model": "nightsiege:item/trophy/siege_crown_3d"}}, f, indent=2)
    print("crown ok")
