"""Z3 (Simon 12.11.): the hearts in the kit HUD's design - dark iron, gold trim, blood red.

Writes 9x9 sprites for every vanilla heart state to the gui atlas
(assets/nightsiege/textures/gui/sprites/hud/heart/), KitHud.heartSprite picks them while the kit is worn.
Optional argument: a preview PNG (8x, on dark and on light ground).
Pure Python (no PIL): python tools/hud/herzen.py [preview.png]
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
GOLD = 0xC9A55A
GOLD_DARK = 0x8A6A2C
IRON = 0x0A0807
RIM = 0x4A3816
EMPTY = 0x1D1813
EMPTY_TOP = 0x2A2318
LIGHT = 0xF4E6C8

# highlight, middle, shade
FILLS = {
    "normal": (0xE5484A, 0xA8161C, 0x5E0A0E),
    "poisoned": (0xA6C44A, 0x5F7D18, 0x2E3F0A),
    "withered": (0x6A625A, 0x34302C, 0x141210),
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
    if len(sys.argv) > 1:
        preview(sys.argv[1], all_px)
        print("preview ->", sys.argv[1])
