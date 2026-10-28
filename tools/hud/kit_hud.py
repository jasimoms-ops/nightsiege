#!/usr/bin/env python3
"""Pixel textures of the kit HUD (Simon, 28 October 2026): dark iron, bronze and gold filigree.

One texel is one GUI pixel, so at every GUI scale the HUD looks like Minecraft's own pixel art.
Writes into src/main/resources/assets/nightsiege/textures/gui/kit_hud/ and a preview into
build/kit_hud_preview.png (four times larger, on a night-coloured background).

    python3 tools/hud/kit_hud.py
"""
import os
import random
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "src/main/resources/assets/nightsiege/textures/gui/kit_hud")

H = 46  # panel height

OUTLINE = (10, 8, 7, 255)
IRON0 = (20, 17, 15, 255)
IRON1 = (29, 25, 22, 255)
IRON2 = (36, 31, 27, 255)
IRON3 = (46, 40, 35, 255)
BRONZE = (107, 82, 34, 255)
BRONZE_D = (74, 56, 22, 255)
GOLD = (201, 165, 90, 255)
GOLD_L = (232, 196, 106, 255)
CLEAR = (0, 0, 0, 0)


def img(w, h, fill=CLEAR):
    return Image.new("RGBA", (w, h), fill)


def plate(im, x0, y0, x1, y1, seed):
    """Dark iron with a faint hammered grain."""
    r = random.Random(seed)
    for y in range(y0, y1):
        for x in range(x0, x1):
            v = r.random()
            c = IRON1 if v < 0.70 else IRON0 if v < 0.85 else IRON2
            im.putpixel((x, y), c)


def border_top(im, x0, x1):
    """The top edge: outline, bronze, gold, dark seam."""
    for x in range(x0, x1):
        im.putpixel((x, 0), OUTLINE)
        im.putpixel((x, 1), BRONZE)
        im.putpixel((x, 2), GOLD)
        im.putpixel((x, 3), BRONZE_D)
        im.putpixel((x, 4), IRON0)


def scroll(im, cx, y):
    """A small gold filigree scroll (7 x 4) under the top edge."""
    pts = [(-3, 1), (-2, 0), (-1, 0), (0, 1), (1, 2), (2, 2), (3, 1),
           (-3, 2), (3, 0), (0, 0)]
    for dx, dy in pts:
        x, yy = cx + dx, y + dy
        if 0 <= x < im.width and 0 <= yy < im.height:
            im.putpixel((x, yy), GOLD if (dx + dy) % 2 == 0 else BRONZE)


def rivet(im, x, y):
    im.putpixel((x, y), GOLD_L)
    im.putpixel((x + 1, y), GOLD)
    im.putpixel((x, y + 1), GOLD)
    im.putpixel((x + 1, y + 1), BRONZE_D)


def panel_mid():
    w = 32
    im = img(w, H)
    plate(im, 0, 5, w, H, 1)
    border_top(im, 0, w)
    scroll(im, 8, 5)
    scroll(im, 24, 5)
    # a fine bronze line along the foot
    for x in range(w):
        im.putpixel((x, H - 2), BRONZE_D)
        im.putpixel((x, H - 1), OUTLINE)
    return im


def panel_cap(left=True):
    w = 12
    im = img(w, H)
    plate(im, 0, 5, w, H, 2 if left else 3)
    border_top(im, 0, w)
    # rounded outer corner and a side edge
    for y in range(H):
        im.putpixel((0, y), OUTLINE)
        im.putpixel((1, y), BRONZE)
        im.putpixel((2, y), GOLD if y > 3 else im.getpixel((2, y)))
        im.putpixel((3, y), BRONZE_D if y > 3 else im.getpixel((3, y)))
    for (x, y) in [(0, 0), (1, 0), (0, 1), (2, 0), (0, 2)]:
        im.putpixel((x, y), CLEAR)
    im.putpixel((1, 1), OUTLINE)
    im.putpixel((2, 1), BRONZE)
    im.putpixel((1, 2), BRONZE)
    # a curl on the side and rivets
    for y, x in [(10, 5), (11, 6), (12, 6), (13, 5), (14, 6), (15, 7)]:
        im.putpixel((x, y), GOLD)
    rivet(im, 6, 20)
    rivet(im, 6, H - 7)
    for x in range(w):
        im.putpixel((x, H - 2), BRONZE_D)
        im.putpixel((x, H - 1), OUTLINE)
    im.putpixel((2, H - 2), GOLD)
    if not left:
        im = im.transpose(Image.FLIP_LEFT_RIGHT)
    return im


def arch():
    """The crown over the middle: a raised arch with a spike."""
    w, h = 56, 10
    im = img(w, h)
    cx = w // 2
    for x in range(w):
        d = abs(x - cx + 0.5) / (w / 2)
        top = int(round(2 + 7 * d * d))  # the arch's top edge, lower to the sides
        for y in range(top, h):
            if y == top:
                c = OUTLINE
            elif y == top + 1:
                c = GOLD
            elif y == top + 2:
                c = BRONZE
            else:
                c = IRON1 if (x + y) % 5 else IRON0
            im.putpixel((x, y), c)
    # the spike
    for y, half in [(0, 0), (1, 0), (2, 1)]:
        for x in range(cx - half - 1, cx + half + 1):
            im.putpixel((x, y), GOLD_L if y < 2 else GOLD)
    for x in (cx - 6, cx + 5):
        im.putpixel((x, 6), GOLD)
    return im


def slot(size, edge=BRONZE, inner_edge=BRONZE_D, gold=False, round_=False):
    im = img(size, size)
    for y in range(size):
        for x in range(size):
            if x in (0, size - 1) or y in (0, size - 1):
                c = OUTLINE
            elif x in (1, size - 2) or y in (1, size - 2):
                c = (GOLD_L if gold and (x + y) % 2 == 0 else GOLD) if gold else edge
            elif x in (2, size - 3) or y in (2, size - 3):
                c = inner_edge if not gold else BRONZE
            else:
                c = IRON0 if (x * 3 + y) % 7 else IRON1
            im.putpixel((x, y), c)
    if round_:
        for (x, y) in [(0, 0), (1, 0), (0, 1), (2, 0), (0, 2), (1, 1)]:
            for (xx, yy) in [(x, y), (size - 1 - x, y), (x, size - 1 - y), (size - 1 - x, size - 1 - y)]:
                im.putpixel((xx, yy), CLEAR)
        for (x, y) in [(2, 1), (1, 2)]:
            for (xx, yy) in [(x, y), (size - 1 - x, y), (x, size - 1 - y), (size - 1 - x, size - 1 - y)]:
                im.putpixel((xx, yy), OUTLINE)
    if size >= 18 and not gold:
        im.putpixel((2, 2), GOLD)
        im.putpixel((size - 3, 2), GOLD)
    return im


def bar(w, h):
    """Frame of a bar: outline, bronze, a dark inside (the fill is drawn over it)."""
    im = img(w, h)
    for y in range(h):
        for x in range(w):
            if x in (0, w - 1) or y in (0, h - 1):
                c = OUTLINE
            elif x in (1, w - 2) or y in (1, h - 2):
                c = BRONZE
            else:
                c = (13, 11, 10, 255)
            im.putpixel((x, y), c)
    for (x, y) in [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)]:
        im.putpixel((x, y), CLEAR)
    return im


# 7 x 7 symbols left of the bars: o outline, a-d colours light to dark, w white shine.
HEART = [
    ".oo.oo.",
    "owaoaao",
    "oaabbbo",
    "obbbbco",
    ".obbco.",
    "..oco..",
    "...o...",
]
ARMOR = [
    "oo...oo",
    "owaoaao",
    "oabbbbo",
    ".obbbo.",
    ".obbco.",
    ".obcco.",
    ".ooooo.",
]
FOOD = [
    "...ooo.",
    "..owaao",
    ".oaabbo",
    ".obbbo.",
    "o.ooo..",
    "wo.....",
    "ow.....",
]
AIR = [
    "..ooo..",
    ".oaaao.",
    "oawwbao",
    "oaw.bao",
    "oab.cao",
    ".oacao.",
    "..ooo..",
]
def icon(rows):
    palettes = {
        id(HEART): [(232, 84, 72, 255), (196, 44, 36, 255), (130, 24, 20, 255)],
        id(ARMOR): [(206, 214, 222, 255), (150, 160, 172, 255), (96, 104, 116, 255)],
        id(FOOD): [(226, 160, 84, 255), (184, 110, 46, 255), (120, 68, 28, 255)],
        id(AIR): [(200, 232, 250, 255), (120, 184, 226, 255), (66, 120, 176, 255)],
    }
    a, b, c = palettes[id(rows)]
    im = img(7, 7)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            col = {"o": OUTLINE, "w": (250, 246, 236, 255), "a": a, "b": b, "c": c}.get(ch)
            if col:
                im.putpixel((x, y), col)
    return im


CLASS_COLORS = {
    "warrior": ((143, 38, 32, 255), (184, 53, 44, 255)),
    "archer": ((47, 96, 38, 255), (84, 140, 64, 255)),
    "guardian": ((34, 70, 128, 255), (66, 110, 180, 255)),
    "engineer": ((150, 88, 24, 255), (196, 124, 42, 255)),
    "summoner": ((28, 104, 92, 255), (60, 160, 140, 255)),
    "none": ((60, 52, 46, 255), (90, 80, 70, 255)),
}


def crest(colors):
    """A shield in the class colour with a gold rim (the class icon is drawn over its middle)."""
    w, h = 26, 30
    dark, light = colors
    im = img(w, h)
    cx = (w - 1) / 2
    for y in range(h):
        if y < 18:
            half = 12.5
        else:
            t = (y - 18) / (h - 18)
            half = 12.5 * (1 - t * t)
        for x in range(w):
            d = abs(x - cx)
            if d <= half - 0.5:
                edge = d > half - 2.5 or y < 2 or (y > 17 and d > half - 3.0)
                inner = d > half - 3.5 or y < 3
                if y == 0 or d > half - 1.5:
                    c = OUTLINE
                elif edge:
                    c = GOLD if (x + y) % 3 else GOLD_L
                elif inner:
                    c = BRONZE_D
                else:
                    c = light if (x < cx and y < 12) else dark
                im.putpixel((x, y), c)
    return im


def main():
    os.makedirs(OUT, exist_ok=True)
    parts = {
        "panel_mid": panel_mid(),
        "panel_left": panel_cap(True),
        "panel_right": panel_cap(False),
        "panel_arch": arch(),
        "slot_ability": slot(22),
        "slot_ability_ready": slot(22, gold=True),
        "slot_choice": slot(14),
        "slot_choice_current": slot(14, gold=True),
        "slot_item": slot(18),
        "slot_item_selected": slot(18, gold=True),
        "slot_offhand": slot(18, round_=True),
        "bar_health": bar(62, 10),
        "bar_small": bar(62, 8),
        "icon_heart": icon(HEART),
        "icon_armor": icon(ARMOR),
        "icon_food": icon(FOOD),
        "icon_air": icon(AIR),
    }
    for name, colors in CLASS_COLORS.items():
        parts["crest_" + name] = crest(colors)
    for name, im in parts.items():
        im.save(os.path.join(OUT, name + ".png"))
    preview(parts)
    print("kit hud textures:", len(parts))


def preview(p):
    """The panel put together like KitHud draws it, four times larger."""
    W, Hs = 480, 120
    bg = img(W, Hs, (52, 67, 46, 255))
    for y in range(0, 60):
        for x in range(W):
            bg.putpixel((x, y), (59, 79, 97, 255))
    abil_w = 3 * 24 - 2
    crest_x, abil_x = 8, 38
    bars_x = abil_x + abil_w + 7
    hot_x = bars_x + 71 + 5
    pw = hot_x + 90 + 5
    x0 = (W - pw) // 2
    y0 = Hs - H
    bg.alpha_composite(p["panel_left"], (x0, y0))
    x = x0 + 12
    while x < x0 + pw - 12:
        bg.alpha_composite(p["panel_mid"].crop((0, 0, min(32, x0 + pw - 12 - x), H)), (x, y0))
        x += 32
    bg.alpha_composite(p["panel_right"], (x0 + pw - 12, y0))
    bg.alpha_composite(p["panel_arch"], (x0 + pw // 2 - 28, y0 - 9))
    bg.alpha_composite(p["crest_warrior"], (x0 + crest_x, y0 + 9))
    for i in range(3):
        bg.alpha_composite(p["slot_ability_ready" if i == 0 else "slot_ability"], (x0 + abil_x + i * 24, y0 + 7))
    for i in range(4):
        bg.alpha_composite(p["slot_choice_current" if i == 1 else "slot_choice"], (x0 + abil_x + 6 + i * 15, y0 + 30))
    for name, bar, y in (("icon_heart", "bar_health", 9), ("icon_armor", "bar_small", 22), ("icon_food", "bar_small", 33)):
        h = p[bar].height
        bg.alpha_composite(p[name], (x0 + bars_x, y0 + y + (h - 7) // 2))
        bg.alpha_composite(p[bar], (x0 + bars_x + 9, y0 + y))
    for i in range(5):
        bg.alpha_composite(p["slot_item_selected" if i == 0 else "slot_item"], (x0 + hot_x + i * 18, y0 + 6))
    for i in range(4):
        bg.alpha_composite(p["slot_item"], (x0 + hot_x + i * 18, y0 + 24))
    bg.alpha_composite(p["slot_offhand"], (x0 + hot_x + 4 * 18, y0 + 24))
    os.makedirs(os.path.join(ROOT, "build"), exist_ok=True)
    bg.resize((W * 4, Hs * 4), Image.NEAREST).save(os.path.join(ROOT, "build", "kit_hud_preview.png"))


if __name__ == "__main__":
    main()
