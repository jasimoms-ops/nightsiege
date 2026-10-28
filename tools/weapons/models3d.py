"""
3D kit weapons (design update, Simon 29 October 2026, direction A): every melee kit weapon and
staff becomes a voxel model built from its 32x32 picture. The blade and shaft are one texel deep
like a vanilla item; guards, pommels, gems and glowing parts (gold, dark and glowing texels in the
picture) stand out three times as deep, so they have real volume in the hand. In the hand the
weapon is shown 15 % larger; in the inventory it stays a flat picture.

python3 tools/weapons/models3d.py
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import kit_v3 as K  # noqa: E402

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources",
                    "assets", "nightsiege")
KEYS = ["blademaster_saber", "berserker_bloodblade", "axe_bearded", "paladin_sunaxe", "shieldfighter_crestsword",
        "bulwark_wallbreaker", "medic_lancet", "warden_wardblade", "gunner_wrenchblade", "electrician_coilblade",
        "demolition_fusemachete", "alchemist_vialknife", "tamer_packstaff", "golemsmith_forgestaff",
        "necromancer_bonestaff", "soulcatcher_lanternstaff"]
PX = 16.0 / 32.0   # one texel in model units


def depth_of(c):
    k = K.kind(c)
    if k in ("gold", "glow", "dark"):
        return 1.5    # three texels deep: guards, gems, pommels, runes
    return 0.5        # one texel like a vanilla item


def model(key):
    im = Image.open(os.path.join(ROOT, "textures", "item", "kit", key + ".png")).convert("RGBA")
    w, h = im.size
    deep = {}
    for y in range(h):
        for x in range(w):
            c = im.getpixel((x, y))
            if c[3] > 0:
                deep[(x, y)] = depth_of(c)
    elements = []
    for y in range(h):
        x = 0
        while x < w:
            if (x, y) not in deep:
                x += 1
                continue
            d = deep[(x, y)]
            x0 = x
            while x < w and deep.get((x, y)) == d:
                x += 1
            x1 = x
            u0, u1 = x0 * PX, x1 * PX
            v0, v1 = y * PX, (y + 1) * PX
            z0, z1 = 8 - d / 2, 8 + d / 2
            faces = {
                "north": {"uv": [u1, v0, u0, v1], "texture": "#layer0"},
                "south": {"uv": [u0, v0, u1, v1], "texture": "#layer0"},
                "up": {"uv": [u0, v0, u1, v0 + PX], "texture": "#layer0"},
                "down": {"uv": [u0, v1 - PX, u1, v1], "texture": "#layer0"},
                "west": {"uv": [u0, v0, u0 + PX, v1], "texture": "#layer0"},
                "east": {"uv": [u1 - PX, v0, u1, v1], "texture": "#layer0"},
            }
            elements.append({"from": [x0 * PX, 16 - (y + 1) * PX, z0], "to": [x1 * PX, 16 - y * PX, z1], "faces": faces})
    return {
        "parent": "minecraft:item/handheld",
        "gui_light": "front",
        "textures": {"layer0": "nightsiege:item/kit/" + key, "particle": "nightsiege:item/kit/" + key},
        "elements": elements,
        "display": {
            "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.5, 0.5], "scale": [0.98, 0.98, 0.98]},
            "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4.5, 0.5], "scale": [0.98, 0.98, 0.98]},
            "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.4, 1.13], "scale": [0.78, 0.78, 0.78]},
            "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.4, 1.13], "scale": [0.78, 0.78, 0.78]},
            "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
            "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
            "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]},
            "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
        },
    }


def main():
    for key in KEYS:
        m = model(key)
        with open(os.path.join(ROOT, "models", "item", "kit", key + ".json"), "w") as f:
            json.dump(m, f, separators=(",", ":"))
        print(key, len(m["elements"]), "elements")


if __name__ == "__main__":
    main()
