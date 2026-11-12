"""Z3 (Simon 12.11.): the hearts in the kit HUD's design - pale gold rim, dark inside, blood red.

Writes 9x9 sprites for every vanilla heart state to the gui atlas
(assets/nightsiege/textures/gui/sprites/hud/heart/), KitHud.heartSprite picks them while the kit is worn.
Optional argument: a preview PNG (8x, on dark and on light ground).
Z3b: also the hunger symbols (hud/food_*). Pure Python (no PIL): python tools/hud/herzen.py [preview.png [food.png]]
"""
import os
import struct
import sys
import zlib

SHAPE = [
    ".OO...OO.",
    "OiiO.OiiO",
    "OiiiNiiiO",
    "OiiiiiiiO",
    "OiiiiiiiO",
    ".OiiiiiO.",
    "..OiiiO..",
    "...OiO...",
    "....O....",
]
# Chef 12.11. 18:10: empty hearts must read on the night - the rim pale gold, contrast >= 5.7:1 against night sky
# 0x2B3550 / 0x3C4A6E, ground 0x1E261C and blood moon 0x4A1A1A (WCAG luminance; the old dark iron was 1-2.4:1).
GOLD = 0xF6E3AE
GOLD_DARK = 0xE6CF94
IRON = 0xE6CF94
RIM = 0xE6CF94
EMPTY = 0x2A2118
EMPTY_TOP = 0x382C1E
LIGHT = 0xFFFFFF

# highlight, middle, shade
FILLS = {
    "normal": (0xFF7070, 0xD7262C, 0x8E1218),   # 3.2:1 against the empty inside
    "poisoned": (0xA6C44A, 0x5F7D18, 0x2E3F0A),
    "withered": (0x8A8178, 0x4E4842, 0x24201C),
    "absorbing": (0xF4DC8E, 0xC9A55A, 0x7A5A22),
    "frozen": (0xD2EEFF, 0x6E9ED0, 0x2E5480),
    "vehicle": (0xEAB060, 0xB0702A, 0x5E3A14),
}
HIGHLIGHT = {(1, 1), (2, 1), (1, 2), (6, 1)}
SHADE = {(7, 3), (7, 4), (6, 5), (3, 6), (4, 6), (5, 6), (4, 7)}


def blend(c, to, f):
    out = 0
    for s in (16, 8, 0):
        a = (c >> s) & 0xFF
        b = (to >> s) & 0xFF
        out |= round(a + (b - a) * f) << s
    return out


def container(blinking):
    px = [[None] * 9 for _ in range(9)]
    for y, row in enumerate(SHAPE):
        for x, ch in enumerate(row):
            if ch == "O":
                c = GOLD if y == 0 else GOLD_DARK if y == 1 else IRON
                px[y][x] = LIGHT if blinking else c
            elif ch == "N":
                px[y][x] = LIGHT if blinking else RIM
            elif ch == "i":
                px[y][x] = EMPTY_TOP if y <= 2 else EMPTY
    return px


def fill(kind, half, blinking):
    hl, mid, shade = FILLS[kind]
    px = [[None] * 9 for _ in range(9)]
    for y, row in enumerate(SHAPE):
        for x, ch in enumerate(row):
            if ch != "i" or (half and x > 4):
                continue
            c = hl if (x, y) in HIGHLIGHT else shade if (x, y) in SHADE else mid
            px[y][x] = blend(c, 0xFFFFFF, 0.45) if blinking else c
    return px


def png(path, px):
    h = len(px)
    w = len(px[0])
    raw = bytearray()
    for row in px:
        raw += b"\x00"
        for c in row:
            raw += b"\x00\x00\x00\x00" if c is None else struct.pack(">BBBB", (c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, 0xFF)

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(data)


def sprites():
    out = {"container": container(False), "container_blinking": container(True),
           "vehicle_container": container(False)}
    for kind in FILLS:
        if kind == "vehicle":
            out["vehicle_full"] = fill(kind, False, False)
            out["vehicle_half"] = fill(kind, True, False)
            continue
        for half in (False, True):
            for blinking in (False, True):
                name = kind + ("_half" if half else "_full") + ("_blinking" if blinking else "")
                out[name] = fill(kind, half, blinking)
    return out


# Z3b (Simon 12.11. 20:11): the hunger symbols in the same design - a leg of meat, meat top left, bone bottom right
# as vanilla's; pale gold rim, dark inside. Half: the right part stays (vanilla's row runs right to left).
FOOD = [
    "..OOO....",
    ".OmmmO...",
    "OmmmmmO..",
    "OmmmmmO..",
    ".OmmmmO..",
    "..OOmbO..",
    "....ObbO.",
    ".....ObbO",
    "......OO.",
]
FOOD_FILLS = {"": (0xF08A5A, 0xC2562A, 0x7A3018), "_hunger": (0xC8D880, 0x7E8A2E, 0x4A5418)}
FOOD_RIM = {"": 0xE6CF94, "_hunger": 0xC9D98E}
FOOD_HIGHLIGHT = {(2, 1), (2, 2), (1, 2)}
FOOD_SHADE = {(4, 4), (5, 4), (4, 5)}
BONE = (0xF4EAD0, 0xC8B890)


def food_empty(hunger):
    px = [[None] * 9 for _ in range(9)]
    for y, row in enumerate(FOOD):
        for x, ch in enumerate(row):
            if ch == "O":
                px[y][x] = FOOD_RIM[hunger]
            elif ch in "mb":
                px[y][x] = EMPTY_TOP if y <= 2 else EMPTY
    return px


def food_fill(hunger, half):
    hl, mid, shade = FOOD_FILLS[hunger]
    px = [[None] * 9 for _ in range(9)]
    for y, row in enumerate(FOOD):
        for x, ch in enumerate(row):
            if ch not in "mb" or (half and x < 4):
                continue
            if ch == "b":
                px[y][x] = BONE[0] if x + y < 13 else BONE[1]
            else:
                px[y][x] = hl if (x, y) in FOOD_HIGHLIGHT else shade if (x, y) in FOOD_SHADE else mid
    return px


def foods():
    out = {}
    for hunger in ("", "_hunger"):
        out["food_empty" + hunger] = food_empty(hunger)
        out["food_full" + hunger] = food_fill(hunger, False)
        out["food_half" + hunger] = food_fill(hunger, True)
    return out


def over(base, top):
    return [[t if t is not None else b for b, t in zip(rb, rt)] for rb, rt in zip(base, top)]


def preview(path, all_px):
    rows = []
    for ground in (0x2B3550, 0x9CB4D8):
        for kind in ("normal", "poisoned", "withered", "absorbing", "frozen", "vehicle"):
            names = [kind + "_full", kind + "_half"] if kind == "vehicle" else \
                [kind + "_full", kind + "_half", kind + "_full_blinking", kind + "_half_blinking"]
            line = [[ground] * 2 for _ in range(9)]
            empty = all_px["container"]
            for n in [None] + names:
                img = empty if n is None else over(empty, all_px[n])
                for y in range(9):
                    line[y] += [ground if c is None else c for c in img[y]] + [ground] * 2
            rows.append(line)
    width = max(len(r[0]) for r in rows)
    px = []
    for line in rows:
        for row in line:
            px.append(row + [row[0]] * (width - len(row)))
        px.append([px[-1][0]] * width)
    scaled = []
    for row in px:
        big = [c for c in row for _ in range(8)]
        scaled += [big] * 8
    png(path, scaled)


if __name__ == "__main__":
    here = os.path.dirname(os.path.abspath(__file__))
    target = os.path.join(here, "..", "..", "src", "main", "resources", "assets", "nightsiege", "textures", "gui",
                          "sprites", "hud", "heart")
    os.makedirs(target, exist_ok=True)
    all_px = sprites()
    for name, px in all_px.items():
        png(os.path.join(target, name + ".png"), px)
    print(len(all_px), "sprites ->", os.path.normpath(target))
    hud = os.path.normpath(os.path.join(target, ".."))
    food_px = foods()
    for name, px in food_px.items():
        png(os.path.join(hud, name + ".png"), px)
    print(len(food_px), "food sprites ->", hud)
    if len(sys.argv) > 2:
        rows = []
        for hunger in ("", "_hunger"):
            e = food_px["food_empty" + hunger]
            line = [e, over(e, food_px["food_full" + hunger]), over(e, food_px["food_half" + hunger])]
            rows.append(line)
        px = []
        for ground in (0x2B3550, 0x1E261C):
            for line in rows:
                for y in range(9):
                    r = [ground] * 2
                    for img in line:
                        r += [ground if c is None else c for c in img[y]] + [ground] * 2
                    px.append(r)
                px.append([ground] * len(px[-1]))
        png(sys.argv[2], [[c for c in row for _ in range(8)] for row in px for _ in range(8)])
        print("food preview ->", sys.argv[2])
    if len(sys.argv) > 1:
        preview(sys.argv[1], all_px)
        print("preview ->", sys.argv[1])
