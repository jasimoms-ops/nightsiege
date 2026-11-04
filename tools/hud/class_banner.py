#!/usr/bin/env python3
"""Klassenbanner (update 6, Simon 4 November 2026: "es gibt einfach nur Klassenbanner, so dass es
nur 5 Designs gibt"): one banner per class, an ordinary Minecraft banner with its own patterns.

A banner pattern is a single tinted layer, so every class banner is three layers on the class colour:
  * crest_<class>_dark  (tinted black)  outline of the emblem, the shadows, the castle wall below
  * crest_<class>_gold  (tinted yellow) the frame, the emblem, the band, the battlements, the fringe
  * the base colour itself (the class's dye)
Grey levels inside a layer give shades of the same dye (the game multiplies texel and dye colour),
alpha gives the strength. Shield textures are drawn at twice the vanilla density so the 15 x 15
emblem keeps its pixels on the 12 x 22 face.

Writes
  src/main/resources/assets/nightsiege/textures/entity/banner/crest_<class>_{dark,gold}.png  (64 x 64)
  src/main/resources/assets/nightsiege/textures/entity/shield/crest_<class>_{dark,gold}.png  (128 x 128)
  build/class_banners.png  (preview: standing banner, wall banner, shield, with the vanilla dyes)

    python3 tools/hud/class_banner.py
"""
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import crest as CR
import emblems as EM
import kit_hud as k
from PIL import Image, ImageDraw, ImageFont

ROOT = k.ROOT
TEX = os.path.join(ROOT, "src/main/resources/assets/nightsiege/textures/entity")

# The class -> its banner dye (the 16 Minecraft dyes; Techniker brown, because gold on orange vanishes).
CLASSES = ["warrior", "guardian", "archer", "engineer", "summoner"]
DYE = {"warrior": "red", "guardian": "blue", "archer": "green", "engineer": "brown", "summoner": "cyan"}
NAMES = {"warrior": "Krieger", "guardian": "Wächter", "archer": "Bogenschütze", "engineer": "Techniker",
         "summoner": "Beschwörer"}
# Minecraft's dye colours (DyeColor.getTextureDiffuseColor).
DYES = {"white": 0xF9FFFE, "orange": 0xF9801D, "magenta": 0xC74EBD, "light_blue": 0x3AB3DA, "yellow": 0xFED83D,
        "lime": 0x80C71F, "pink": 0xF38BAA, "gray": 0x474F52, "light_gray": 0x9D9D97, "cyan": 0x169C9C,
        "purple": 0x8932B8, "blue": 0x3C44AA, "brown": 0x835432, "green": 0x5E7C16, "red": 0xB02E26,
        "black": 0x1D1D21}

FW, FH = 20, 40          # the banner's cloth
# Emblem letters -> grey of the gold layer (bright, normal, dark); "o" goes to the dark layer.
BRIGHT = set("lSwWy")
DARKISH = set("dKERBCk")


def emblem(cls):
    rows, axis = CR.centred(cls, EM.E[cls])
    return rows, axis


def design(cls, w=FW, h=FH):
    """The two layers of one class banner on its w x h cloth: dict layer -> {(x, y): (grey, alpha)}."""
    dark, gold = {}, {}

    def g(x, y, grey=255, a=255):
        if 0 <= x < w and 0 <= y < h:
            gold[(x, y)] = (grey, a)

    def d(x, y, a=255):
        if 0 <= x < w and 0 <= y < h and (x, y) not in gold:
            old = dark.get((x, y), (0, 0))[1]
            dark[(x, y)] = (0, max(old, a))

    wall_top = h - 9                     # crenellations start here
    # Frame: gold on the sides and the top, a dark line inside it.
    for y in range(0, wall_top + 2):
        g(0, y, 215)
        g(w - 1, y, 175)
    for x in range(w):
        g(x, 0, 235)
    for y in range(1, wall_top + 2):
        d(1, y, 120)
        d(w - 2, y, 120)
    for x in range(1, w - 1):
        d(x, 1, 120)
    # Small gold corner studs.
    for (x, y) in ((2, 2), (w - 3, 2)):
        g(x, y, 255)

    # The emblem, centred on the cloth, its middle at a third of the height.
    rows, axis = emblem(cls)
    pts = [(x, y) for y, r in enumerate(rows) for x, ch in enumerate(r) if ch != "."]
    y0, y1 = min(p[1] for p in pts), max(p[1] for p in pts)
    ex = int(round((w - 1) / 2 - axis))
    ey = int(round(h * 0.34 - (y0 + y1) / 2))
    # A soft dark halo behind the emblem, then the emblem itself.
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch == ".":
                continue
            for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1), (0, 0)):
                d(ex + x + dx, ey + y + dy, 60)
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch == ".":
                continue
            if ch == "o":
                dark[(ex + x, ey + y)] = (0, 255)
            elif cls == "guardian" and ch == "K":
                dark[(ex + x, ey + y)] = (0, 215)   # the tower's gate stays an opening, not a gold block
            else:
                grey = 255 if ch in BRIGHT else 150 if ch in DARKISH else 210
                g(ex + x, ey + y, grey)

    # The band under the emblem: dark line, gold band with a lighter top, dark line.
    by = ey + y1 + 3
    for x in range(1, w - 1):
        d(x, by, 200)
        g(x, by + 1, 250)
        g(x, by + 2, 185)
        d(x, by + 3, 200)
    # The castle wall at the bottom: battlements with a gold top edge, the wall darker.
    merlon, gap = 3, 2
    x = 1
    tops = []
    while x < w - 1:
        tops.append((x, min(w - 2, x + merlon - 1)))
        x += merlon + gap
    for x in range(1, w - 1):
        is_merlon = any(a <= x <= b for a, b in tops)
        top = wall_top if is_merlon else wall_top + 2
        g(x, top, 235)
        for y in range(top + 1, h - 1):
            d(x, y, 115)
        # mortar of the wall: courses of 3, bricks of 4, every second course shifted by 2
        for y in range(top + 1, h - 1):
            course = (y - wall_top - 2) // 3
            if y > wall_top + 2 and ((y - wall_top - 2) % 3 == 0 or (x + course * 2) % 4 == 0):
                d(x, y, 170)
    for y in range(wall_top + 1, h):
        g(0, y, 175)
        g(w - 1, y, 150)
    # A gold fringe along the lower edge.
    for x in range(w):
        g(x, h - 1, 220 if x % 2 == 0 else 150)
    return {"dark": dark, "gold": gold}


def layer_png(pixels, size, front, back):
    """One pattern texture: the face drawn at the front and the back origin of the model's box."""
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for (x, y), (grey, a) in pixels.items():
        for ox, oy in (front, back):
            im.putpixel((ox + x, oy + y), (grey, grey, grey, a))
    return im


def tint(rgb, grey, a):
    r, g_, b = (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255
    return (r * grey // 255, g_ * grey // 255, b * grey // 255, a)


def cloth(cls, w, h, rnd):
    """The banner as the game draws it: the dyed base with a little cloth noise, then the layers."""
    base = DYES[DYE[cls]]
    im = Image.new("RGBA", (w, h))
    for y in range(h):
        for x in range(w):
            n = 235 + rnd.randint(-8, 8) - (6 if x in (0, w - 1) else 0)
            im.putpixel((x, y), tint(base, n, 255))
    lay = design(cls, w, h)
    for name, dye in (("dark", "black"), ("gold", "yellow")):
        top = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        for (x, y), (grey, a) in lay[name].items():
            top.putpixel((x, y), tint(DYES[dye], grey, a))
        im.alpha_composite(top)
    return im


def shield_face(cls, rnd):
    """The 12 x 22 shield face at twice the density (24 x 44), with the shield's iron rim."""
    w, h = 24, 44
    im = cloth(cls, w, h, rnd)
    # The vanilla shield draws its rim over the edge; a darker iron edge stands in for it here.
    for y in range(h):
        for x in range(w):
            if x < 2 or x >= w - 2 or y < 2 or y >= h - 2:
                im.putpixel((x, y), (92, 92, 96, 255) if (x + y) % 3 else (70, 70, 74, 255))
    return im


def main():
    rnd = random.Random(26)
    for sub in ("banner", "shield"):
        os.makedirs(os.path.join(TEX, sub), exist_ok=True)
    for cls in CLASSES:
        lay = design(cls)
        for name in ("dark", "gold"):
            # banner flag box 20 x 40 x 1 at (0, 0): front at (1, 1), back at (22, 1)
            layer_png(lay[name], 64, (1, 1), (22, 1)).save(os.path.join(TEX, "banner", "crest_%s_%s.png" % (cls, name)))
        # Shield: the same design on a 20 x 40 cloth, drawn into the 24 x 44 face (2 px rim kept free).
        sl = design(cls, 20, 40)
        for name in ("dark", "gold"):
            shifted = {(x + 2, y + 2): v for (x, y), v in sl[name].items()}
            # shield plate box 12 x 22 x 1 at (0, 0), here at double density: front at (2, 2), back at (28, 2)
            layer_png(shifted, 128, (2, 2), (28, 2)).save(os.path.join(TEX, "shield", "crest_%s_%s.png" % (cls, name)))

    # Preview: per class a standing banner (pole and crossbar) and the shield beside it, 8 x
    # (the shield's texels are half as big, so it is drawn at 4 x: the sizes match the game).
    S = 8
    col_w = 40
    sheet = Image.new("RGBA", (len(CLASSES) * col_w + 4, 78), (40, 38, 44, 255))
    for y in range(sheet.height):
        for x in range(sheet.width):
            c = 58 + ((x * 7 + y * 13) % 9) - (10 if y % 6 == 0 or (x + (y // 6) * 4) % 12 == 0 else 0)
            sheet.putpixel((x, y), (c, c - 2, c + 2, 255))
    pole, pole_d = (102, 76, 44, 255), (84, 62, 36, 255)
    for i, cls in enumerate(CLASSES):
        x0 = 4 + i * col_w
        for y in range(2, 72):
            for x in (x0 + 10, x0 + 11):
                sheet.putpixel((x, y), pole if y % 7 else pole_d)
        for x in range(x0, x0 + 22):
            for y in (2, 3):
                sheet.putpixel((x, y), pole if x % 5 else pole_d)
        sheet.alpha_composite(cloth(cls, FW, FH, rnd), (x0 + 1, 4))
    big = sheet.resize((sheet.width * S, sheet.height * S), Image.NEAREST)
    for i, cls in enumerate(CLASSES):
        x0 = (4 + i * col_w + 24) * S
        face = shield_face(cls, rnd).resize((24 * 4, 44 * 4), Image.NEAREST)
        big.alpha_composite(face, (x0, 12 * S))
    dr = ImageDraw.Draw(big)
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf", 30)
    except OSError:
        font = ImageFont.load_default()
    for i, cls in enumerate(CLASSES):
        x0 = (4 + i * col_w) * S
        dr.text((x0 + 4, big.height - 40), NAMES[cls], fill=(240, 230, 200, 255),
                font=font, stroke_width=3, stroke_fill=(20, 16, 14, 255))
    os.makedirs(os.path.join(ROOT, "build"), exist_ok=True)
    out = os.path.join(ROOT, "build", "class_banners.png")
    big.save(out)
    print(out)


if __name__ == "__main__":
    main()
