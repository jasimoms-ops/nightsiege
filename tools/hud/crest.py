"""Crests v2 (Simon 28/29 October 2026): the class colour, a tone-on-tone division per subclass, the
emblem of the class or subclass and an ornament on top (plume, feathers, crown, cog, horns). 28 x 38.

    python3 tools/hud/crest.py
"""
import math
import os
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kit_hud as k
import emblems as EM
from PIL import Image

W, H = 28, 38
OUT = k.OUTLINE

SYM = {
"warrior": [   # crossed swords
"o...........o",
"lo.........ol",
".so.......os.",
"..so.....os..",
"...so...os...",
"....sooos....",
".....oSo.....",
"....sooos....",
"..ogo...ogo..",
".ogllo.ollgo.",
"..oo.....oo..",
".oo.......oo.",
"oo.........oo",
],
"archer": [    # bow with arrow
"....oo.......",
"...ogo...oo..",
"..ogo...oSo..",
".ogo...oSo...",
".go...oSo....",
"og.l.oSo.....",
"ogllsSo......",
"og..oSo......",
".go.so.......",
".ogo.........",
"..ogo........",
"...ogo.......",
"....oo.......",
],
"guardian": [  # tower
"oo.oo.oo.oo..",
"ogoogoogoog..",
"ogggggggggo..",
".ossssssso...",
".osSsssSso...",
".osoo.oosso..".replace("..","o."),
".oss.o.osso..",
".osssssssso..",
".osSsooosSo..",
".oss.ooo.so..",
".osssooosso..",
"ogggggggggo..",
"ooooooooooo..",
],
"engineer": [  # gear
".....ooo.....",
"..oo.ogo.oo..",
"..oggoggggo..",
"...ogglllgo..".replace("...o","..oo"),
".oogoo...ogoo",
"ogggo.....ggo",
"oglgo.....glo",
"ogggo.....ggo",
".oogoo...ogoo",
"..oggo...ggo.",
"..oggggggggo.",
"..oo.ogo.oo..",
".....ooo.....",
],
"summoner": [  # skull
"...ooooooo...",
"..oSSSSSSSo..",
".oSSsssssSSo.",
".oSsssssssso.",
".osoooosoooo.".replace("osoooosoooo","osooosooooo"),
".oso.osoo.so.",
".osoooosooso.",
".ossssosssso.",
"..osssssss o.".replace(" ","s"),
"...osoooso...",
"...os.s.so...",
"...ooooooo...",
".............",
],
}
COL = {"w": (240,236,226,255), "r": (196,40,34,255), "R": (120,20,18,255), "b": (120,170,230,255), "B": (40,80,160,255), "e": (90,170,70,255), "E": (40,100,40,255), "y": (250,220,80,255), "p": (130,70,170,255), "P": (190,140,230,255), "c": (90,230,220,255), "C": (30,140,140,255), "k": (140,96,52,255), "K": (96,62,30,255), "f": (250,140,40,255), "o": OUT, "g": k.GOLD, "l": k.GOLD_L, "s": (170, 176, 184, 255), "S": (226, 230, 236, 255), "d": k.BRONZE_D}

TOP = {   # ornament over the shield, 16 wide x 9, the last row rests on the shield's rim
"warrior": [".......oo.......", "......orro......", ".....orrrRo.....", "....orrrRRo.....", "...ooooooooo....", "..oSSSSSSSSSo...", "..oSsoooooSSo...", "..oSSSSoSSSSo...", "...ooooooooo....",],
"archer":  ["oo............oo", "oWo..........oWo", ".oWo........oWo.", ".oWWo......oWWo.", "..oWWo....oWWo..", "...oWWo..oWWo...", "....oWWooWWo....", ".....oWggWo.....", "......oooo......",],
"guardian":["...o...o...o....", "..olo.olo.olo...", "..ogo.ogo.ogo...", "..oggoggoggo....".replace("..oggoggoggo....","..ogggggggggo..."), "..oglllrllllgo..".replace("lllrllll","llrlllrl")[:16], "..oggggggggggo..", "..oooooooooooo..", "................", "................",],
"engineer":["................", "......oooo......", "...oo.oggo.oo...", "...oggoggoggo...", "....oggllggo....", "...ooggggggoo...", "...ogggoogggo...", "...oooo..oooo...", "................",],
"summoner":["o..............o", "oSo..........oSo", ".oSo........oSo.", ".oSSo......oSSo.", "..oSSo....oSSo..", "..oSSSooooSSSo..", "...oSSSSSSSSo...", "....oooooooo....", "................",],
}
TCOL = dict(COL); TCOL.update({"r": (178, 40, 34, 255), "W": (236, 232, 220, 255)})

# Emblems whose middle column is one too many when it would be doubled (wide ones lose it instead).
DOUBLE = set()
REMOVE = set()


def _mirror_score(rows, axis2):
    pts = [(x, y) for y, r in enumerate(rows) for x, ch in enumerate(r) if ch in COL]
    ok = 0
    for x, y in pts:
        mx = axis2 - x
        if 0 <= mx < len(rows[y]) and rows[y][mx] in COL:
            ok += 1
    return ok / len(pts)


def centred(name, rows):
    """The emblem and its middle (x). The shield's middle lies between two columns, so a mirror-
    symmetric emblem whose axis runs through a column gets that column twice (or, a wide one,
    loses it) and then lies exactly in the middle; a lopsided one goes by the middle of its weight
    (the bow, the axe), not of its box."""
    pts = [(x, y) for y, r in enumerate(rows) for x, ch in enumerate(r) if ch in COL]
    x0, x1 = min(p[0] for p in pts), max(p[0] for p in pts)
    best = None
    for a2 in range(2 * x0, 2 * x1 + 1):
        sc = _mirror_score(rows, a2)
        if best is None or sc > best[0] + 1e-9 or (abs(sc - best[0]) < 1e-9 and abs(a2 - (x0 + x1)) < abs(best[1] - (x0 + x1))):
            best = (sc, a2)
    sc, a2 = best
    if sc >= 0.9:
        if a2 % 2 == 0:
            a = a2 // 2
            if name in REMOVE or (x1 - x0 + 1 >= 15 and name not in DOUBLE):
                return [r[:a] + r[a + 1:] for r in rows], a - 0.5
            return [r[:a + 1] + r[a] + r[a + 1:] for r in rows], a + 0.5
        return rows, a2 / 2
    weight = sum(p[0] for p in pts) / len(pts)
    middle = ((x0 + x1) / 2 + weight) / 2
    return rows, math.floor(middle) + 0.5


def shield_mask(x, y, w=24, h=29):
    cx = (w - 1) / 2
    if y < 0 or y >= h: return None
    half = 11.5 if y < 17 else 11.5 * (1 - ((y - 17) / (h - 17)) ** 1.6)
    d = abs(x - cx)
    if d > half: return None
    return d, half

def crest(cls, division, colors, weapon=False, emblem=None):
    emblem = emblem or cls
    dark, light = colors
    im = k.img(W, H)
    ox, oy = 2, 8
    for y in range(29):
        for x in range(24):
            m = shield_mask(x, y)
            if not m: continue
            d, half = m
            if y == 0 or d > half - 1: c = OUT
            elif d > half - 3 or y < 3 or (y > 25 and d > half - 3.5):
                # bevelled gold rim: light top-left, dark bottom-right
                c = k.GOLD_L if (x < 12 and y < 15) else k.GOLD if y < 22 else k.BRONZE
                if (x + y) % 5 == 0 and y < 26: c = k.BRONZE
            elif d > half - 4 or y < 4: c = OUT
            else:
                v = (x - 4) - (y - 4) * 0.8
                if division == "bend": c = light if v > 0 else dark
                elif division == "quarterly": c = light if (x < 12) == (y < 14) else dark
                elif division == "chief": c = light if y < 9 else dark
                elif division == "chevron":
                    t = 11 + abs(x - 11.5) * 0.85
                    c = light if y < t + 2 else dark
                else: c = dark
                # soft shading: lighter band on the left
                if c in (dark, light) and x < 8 and (x + y) % 2 == 0:
                    c = tuple(min(255, int(v * 1.12)) for v in c[:3]) + (255,)
            im.putpixel((ox + x, oy + y), c)
    # charge
    if not weapon and emblem in EM.E:
        # Exactly in the middle of the field (Simon 30.10.: "bei vielen wappen ist das zeichen nicht
        # richtig in der mitte"): see centred().
        rows, axis = centred(emblem, EM.E[emblem])
        pts = [(x, y) for y, r in enumerate(rows) for x, ch in enumerate(r) if ch in COL]
        y0_, y1_ = min(p[1] for p in pts), max(p[1] for p in pts)
        sx = ox + int(round(11.5 - axis))
        sy = oy + round(13.5 - (y0_ + y1_) / 2)
        for y, r in enumerate(rows):
            for x, ch in enumerate(r):
                if ch in COL: im.putpixel((sx + x, sy + y), COL[ch])
    # ornament on top
    rows = TOP.get(cls, [])
    tx = 6
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch in TCOL and 0 <= tx + x < W: im.putpixel((tx + x, y), TCOL[ch])
    return im


SUBS = {"warrior": ["blademaster","berserker","axe","paladin"], "archer": ["swift","heavy","bolter","trapper"],
 "guardian": ["shieldfighter","bulwark","medic","warden"], "engineer": ["gunner","electrician","demolition","alchemist"],
 "summoner": ["tamer","golemsmith","necromancer","soulcatcher"]}

DIVS = ["bend", "quarterly", "chief", "chevron"]


def main():
    """Writes crest_<class>.png and crest_<subclass>.png (28 x 38) next to the kit HUD textures."""
    for cls, subs in SUBS.items():
        colors = k.CLASS_COLORS[cls]
        crest(cls, "plain", colors).save(os.path.join(k.OUT, "crest_" + cls + ".png"))
        for sub, div in zip(subs, DIVS):
            crest(cls, div, colors, emblem=sub).save(os.path.join(k.OUT, "crest_" + sub + ".png"))
    crest("none", "plain", k.CLASS_COLORS["none"], emblem="none").save(os.path.join(k.OUT, "crest_none.png"))
    sheet = Image.new("RGBA", (5 * 34 + 6, 5 * 44 + 6), (34, 30, 28, 255))
    for r, (cls, subs) in enumerate(SUBS.items()):
        for i, em in enumerate([cls] + subs):
            div = "plain" if i == 0 else DIVS[i - 1]
            sheet.alpha_composite(crest(cls, div, k.CLASS_COLORS[cls], emblem=em), (6 + i * 34, 6 + r * 44))
    os.makedirs(os.path.join(k.ROOT, "build"), exist_ok=True)
    sheet.resize((sheet.width * 4, sheet.height * 4), Image.NEAREST).save(os.path.join(k.ROOT, "build", "kit_crests.png"))


if __name__ == "__main__":
    main()
