"""
Stage-20 kit armour icons (design update, Simon 29 October 2026): netherite pieces with the violet
rim of the kit armour, so the kit's netherite is told apart from normal netherite. 16 x 16 like
vanilla armour icons; the kit sets the item model nightsiege:kit/netherite_<piece>.

    python3 tools/armor/kit20_icons.py
"""
import json
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources",
                    "assets", "nightsiege")
# o outline, a light, b mid, c dark, d darkest, v violet rim, V light violet
C = {"o": (22, 16, 20, 255), "a": (110, 96, 100, 255), "b": (78, 66, 70, 255), "c": (56, 46, 50, 255),
     "d": (38, 30, 34, 255), "v": (124, 64, 196, 255), "V": (178, 122, 240, 255)}
P = {
    "helmet": [
        "................",
        "................",
        "....oooooooo....",
        "...oVaaaabbbo...",
        "..oVabbbbbbcco..",
        "..ovbbbbbbbcco..",
        "..ovbccccccdco..",
        "..ovbooooooodo..",
        "..ovbo....odvo..",
        "..ovco....ocvo..",
        "..oVvo....ovVo..",
        "...oo......oo...",
        "................",
        "................",
        "................",
        "................",
    ],
    "chestplate": [
        "................",
        "..ooo......ooo..",
        ".oVabo....oabVo.",
        ".ovbbboooobbbvo.",
        ".ovbbbbbbbbbcvo.",
        ".oooobbbbbbcooo.",
        "....ovbbbbcvo...",
        "....ovbbbbcvo...",
        "....ovbccccvo...",
        "....ovbccccvo...",
        "....oVcccddVo...",
        "....ovvvvvvvo...",
        "....oooooooooo..",
        "................",
        "................",
        "................",
    ],
    "leggings": [
        "................",
        "...oooooooooo...",
        "...oVvvvvvvVo...",
        "...oabbbbbbco...",
        "...obbbccbbco...",
        "...obbco.obco...",
        "...obbco.obco...",
        "...ovbco.ovco...",
        "...ovbco.ovco...",
        "...ovcdo.ovdo...",
        "...oVcdo.oVdo...",
        "...oooo..oooo...",
        "................",
        "................",
        "................",
        "................",
    ],
    "boots": [
        "................",
        "................",
        "................",
        "................",
        "..oooo....oooo..",
        "..oVao....oaVo..",
        "..ovbo....obvo..",
        "..ovbo....obvo..",
        "..ovbo....obvo..",
        ".oobco....ocboo.",
        "ovbbco....ocbbvo",
        "oVccdo....odccVo",
        "oooooo....oooooo",
        "................",
        "................",
        "................",
    ],

}


def main():
    tex = os.path.join(ROOT, "textures", "item", "kit")
    models = os.path.join(ROOT, "models", "item", "kit")
    items = os.path.join(ROOT, "items", "kit")
    for d in (tex, models, items):
        os.makedirs(d, exist_ok=True)
    for piece, rows in P.items():
        im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        for y, r in enumerate(rows):
            for x, ch in enumerate(r):
                if ch in C:
                    im.putpixel((x, y), C[ch])
        name = "netherite_" + piece
        im.save(os.path.join(tex, name + ".png"))
        with open(os.path.join(models, name + ".json"), "w") as f:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "nightsiege:item/kit/" + name}}, f, indent=2)
        with open(os.path.join(items, name + ".json"), "w") as f:
            json.dump({"model": {"type": "minecraft:model", "model": "nightsiege:item/kit/" + name}}, f, indent=2)
    print("kit20 icons:", len(P))


if __name__ == "__main__":
    main()
