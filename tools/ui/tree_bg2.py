"""
Skill tree backgrounds v2 (Simon 29 October 2026, 01:25: pixel landscape with the subclass's crest,
parallax and a small animation, muted). Three layers per subclass, drawn at half size and doubled
(Minecraft pixels):

    sky   480 x 270  opaque   (sky, moon, far ridges)
    mid   544 x 270  alpha    (the scene and the crest banner)
    front 608 x 270  alpha    (ground and silhouettes in front)

CoreScreen moves the wider layers further when the tree is dragged; the particles (embers, mist,
souls ...) are drawn there in code (TreeScene.java).

    python3 tools/ui/tree_bg2.py [sub ...]   -> textures/gui/skills/sub2/<sub>_{sky,mid,front}.png
    python3 tools/ui/tree_bg2.py --sheet out.png   -> all 20 composed, for a look
"""
import math
import os
import random
import sys

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TEX = os.path.join(ROOT, "src/main/resources/assets/nightsiege/textures/gui")
OUT = os.path.join(TEX, "skills/sub2")
CRESTS = os.path.join(TEX, "kit_hud")
HW, HH = 240, 135          # half size; doubled on save
MIDW, FRONTW = 272, 304    # half widths of the wider layers

SUBS = ["blademaster", "berserker", "axe", "paladin", "swift", "heavy", "bolter", "trapper",
        "shieldfighter", "bulwark", "medic", "warden", "gunner", "electrician", "demolition", "alchemist",
        "tamer", "golemsmith", "necromancer", "soulcatcher"]

# sky top, sky horizon, accent
COL = {
    "blademaster": ((8, 8, 16), (64, 30, 40), (220, 220, 230)),
    "berserker": ((14, 4, 6), (110, 24, 20), (230, 60, 40)),
    "axe": ((8, 8, 10), (70, 40, 24), (200, 140, 70)),
    "paladin": ((10, 8, 18), (90, 60, 30), (240, 200, 110)),
    "swift": ((6, 12, 16), (40, 80, 70), (210, 235, 220)),
    "heavy": ((8, 10, 8), (50, 64, 34), (190, 140, 80)),
    "bolter": ((8, 10, 12), (44, 58, 56), (200, 170, 120)),
    "trapper": ((4, 10, 6), (30, 60, 34), (140, 180, 90)),
    "shieldfighter": ((6, 8, 20), (40, 56, 100), (200, 210, 230)),
    "bulwark": ((8, 8, 14), (60, 64, 84), (170, 170, 185)),
    "medic": ((4, 12, 14), (30, 76, 80), (140, 230, 140)),
    "warden": ((4, 6, 16), (24, 48, 96), (100, 240, 225)),
    "gunner": ((10, 8, 6), (80, 56, 30), (130, 190, 240)),
    "electrician": ((6, 6, 12), (50, 46, 70), (255, 230, 100)),
    "demolition": ((10, 6, 4), (100, 46, 20), (255, 160, 60)),
    "alchemist": ((6, 10, 4), (50, 70, 24), (120, 240, 100)),
    "tamer": ((4, 8, 12), (30, 60, 70), (200, 200, 210)),
    "golemsmith": ((8, 6, 6), (70, 44, 34), (230, 140, 70)),
    "necromancer": ((8, 4, 14), (54, 30, 80), (180, 120, 240)),
    "soulcatcher": ((2, 8, 12), (20, 70, 80), (100, 245, 235)),
}

# The subclass's crest file (kit HUD crests, 28 x 38).
CREST = {s: s for s in SUBS}

# Bayer 4x4 for dithered gradients.
BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def shade(c, f):
    return tuple(max(0, min(255, int(c[i] * f))) for i in range(3))


class Canvas:
    def __init__(self, w, h, opaque=False):
        self.w, self.h = w, h
        self.im = Image.new("RGBA", (w, h), (0, 0, 0, 255 if opaque else 0))
        self.px = self.im.load()

    def put(self, x, y, c, a=255):
        x, y = int(x), int(y)
        if 0 <= x < self.w and 0 <= y < self.h:
            if a >= 255 or len(c) == 4:
                self.px[x, y] = (c[0], c[1], c[2], 255)
            else:
                o = self.px[x, y]
                t = a / 255.0
                if o[3] == 0:
                    self.px[x, y] = (c[0], c[1], c[2], a)
                else:
                    self.px[x, y] = (int(o[0] + (c[0] - o[0]) * t), int(o[1] + (c[1] - o[1]) * t),
                                     int(o[2] + (c[2] - o[2]) * t), max(o[3], a))

    def rect(self, x0, y0, x1, y1, c):
        for y in range(int(y0), int(y1) + 1):
            for x in range(int(x0), int(x1) + 1):
                self.put(x, y, c)

    def line(self, x0, y0, x1, y1, c, wdt=1):
        n = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
        for i in range(n + 1):
            t = i / max(1, n)
            x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
            for k in range(wdt):
                self.put(round(x) + (k if abs(y1 - y0) > abs(x1 - x0) else 0),
                         round(y) + (k if abs(y1 - y0) <= abs(x1 - x0) else 0), c)

    def disc(self, cx, cy, r, c):
        for y in range(int(cy - r - 1), int(cy + r + 2)):
            for x in range(int(cx - r - 1), int(cx + r + 2)):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                    self.put(x, y, c)

    def glow(self, cx, cy, r, c, strength=0.6):
        """Additive soft light (only on what is drawn already, or as faint alpha on empty layers)."""
        for y in range(int(cy - r), int(cy + r + 1)):
            for x in range(int(cx - r), int(cx + r + 1)):
                if not (0 <= x < self.w and 0 <= y < self.h):
                    continue
                d = math.hypot(x - cx, y - cy) / r
                if d >= 1:
                    continue
                f = (1 - d) ** 2 * strength
                o = self.px[x, y]
                if o[3] == 0:
                    self.px[x, y] = (c[0], c[1], c[2], int(255 * f * 0.5))
                else:
                    self.px[x, y] = (min(255, int(o[0] + c[0] * f)), min(255, int(o[1] + c[1] * f)),
                                     min(255, int(o[2] + c[2] * f)), o[3])

    def poly(self, pts, c):
        d = ImageDraw.Draw(self.im)
        d.polygon([(int(x), int(y)) for x, y in pts], fill=c + (255,))


# ------------------------------------------------------------------ shared pieces

def sky_layer(sub, r):
    top, hor, acc = COL[sub]
    cv = Canvas(HW, HH, True)
    for y in range(HH):
        for x in range(HW):
            t = (y / (HH * 0.78)) ** 1.25
            band = t * 7 + BAYER[y & 3][x & 3] / 16.0
            cv.put(x, y, mix(top, hor, int(band) / 7.0))
    for _ in range(110):
        x, y = r.randrange(HW), r.randrange(int(HH * 0.55))
        b = r.choice([90, 120, 160, 210])
        cv.put(x, y, (b, b, b + 15))
        if b == 210 and r.random() < 0.3:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                cv.put(x + dx, y + dy, (90, 90, 110))
    return cv


def moon(cv, x, y, rad, c=(222, 216, 200), halo=None):
    if halo:
        cv.glow(x, y, rad * 3.2, halo, 0.35)
    cv.disc(x, y, rad, c)
    dark = shade(c, 0.82)
    for cx, cy, cr in ((-rad * 0.35, -rad * 0.2, rad * 0.28), (rad * 0.3, rad * 0.35, rad * 0.2), (rad * 0.1, -rad * 0.5, rad * 0.14)):
        cv.disc(x + cx, y + cy, cr, dark)


def ridge(cv, base, amp, freq, color, seed, rim=None, rough=1.0):
    r = random.Random(seed)
    ph = [r.random() * 10 for _ in range(4)]
    for x in range(cv.w):
        h = (math.sin(x * freq + ph[0]) * amp + math.sin(x * freq * 2.3 + ph[1]) * amp * 0.45 * rough
             + math.sin(x * freq * 5.1 + ph[2]) * amp * 0.18 * rough)
        top = int(base - h)
        for y in range(max(0, top), cv.h):
            cv.put(x, y, color)
        if rim:
            cv.put(x, top, rim)


def ground(cv, y0, color, seed, bumps=2, grass=None):
    r = random.Random(seed)
    for x in range(cv.w):
        top = y0 + int(math.sin(x * 0.07 + seed) * bumps + math.sin(x * 0.19 + seed * 2) * 1)
        for y in range(top, cv.h):
            cv.put(x, y, color)
        if grass and r.random() < 0.6:
            for k in range(r.randrange(1, 4)):
                cv.put(x, top - 1 - k, grass)


def pine(cv, x, yb, h, c):
    for i in range(h):
        wdt = int((h - i) * 0.38) + 1
        if i % 3 == 2:
            wdt -= 1
        for dx in range(-wdt, wdt + 1):
            cv.put(x + dx, yb - i, c)
    cv.rect(x, yb, x, yb + 2, c)


def oak(cv, x, yb, h, c, leaf):
    cv.rect(x - 1, yb - h, x + 1, yb, c)
    cv.disc(x, yb - h - 3, h * 0.55, leaf)
    cv.disc(x - h * 0.4, yb - h + 1, h * 0.38, leaf)
    cv.disc(x + h * 0.4, yb - h + 1, h * 0.4, leaf)


def dead_tree(cv, x, yb, h, c, r):
    cv.rect(x, yb - h, x + 1, yb, c)
    for k in range(4):
        y = yb - h + 3 + k * (h // 5)
        s = 1 if k % 2 else -1
        ln = r.randrange(4, 9)
        cv.line(x, y, x + s * ln, y - ln // 2, c)


def castle(cv, x0, yb, w, h, c, lit, r, towers=2, crenel=2):
    stone = shade(c, 1.9)
    stone_wall(cv, x0, yb - h, x0 + w, yb, stone, r)
    for x in range(x0, x0 + w + 1, crenel * 2):
        cv.rect(x, yb - h - crenel, x + crenel - 1, yb - h - 1, stone)
    tw = max(7, w // 6)
    for i in range(towers):
        tx = x0 - 3 if i == 0 else x0 + w - tw + 3
        th = h + 12 + r.randrange(0, 6)
        stone_wall(cv, tx, yb - th, tx + tw, yb, shade(stone, 0.95), r, light_left=i == 0)
        cone(cv, tx + tw // 2, yb - th - 1, tw // 2 + 2, tw + 4, shade(c, 1.5))
        if lit:
            window(cv, tx + tw // 2 - 1, yb - th + 5, 3, 5, lit, shade(stone, 0.6), cross=False)
    if lit:
        for k in range(w // 14):
            wx = x0 + 8 + k * 14
            window(cv, wx, yb - h + 6, 3, 5, lit, shade(stone, 0.6), cross=False)


def banner(cv, x, ytop, crest, pole, cloth, glow=None):
    """A banner on a pole with the crest (28 x 38) on its cloth."""
    cw, ch = crest.size
    cv.rect(x - 1, ytop - 4, x - 1, ytop + ch + 30, pole)
    cv.rect(x - 4, ytop - 3, x + cw + 3, ytop - 2, pole)
    cv.put(x - 1, ytop - 6, shade(pole, 1.4))
    bx0, by0 = x, ytop
    cv.rect(bx0, by0, bx0 + cw + 1, by0 + ch + 4, cloth)
    for k in range(cw + 2):   # swallow tail
        depth = 4 + abs((k - (cw + 1) / 2)) * 0.35
        for y in range(int(depth)):
            cv.put(bx0 + k, by0 + ch + 5 + y, cloth)
    if glow:
        cv.glow(bx0 + cw // 2, by0 + ch // 2, 34, glow, 0.25)
    cp = crest.load()
    for yy in range(ch):
        for xx in range(cw):
            p = cp[xx, yy]
            if p[3] > 100:
                cv.put(bx0 + 1 + xx, by0 + 2 + yy, shade(p[:3], 0.82))
    cv.rect(bx0, by0, bx0 + cw + 1, by0, shade(cloth, 0.6))


def wall_crest(cv, x, y, crest, frame):
    """The crest as a stone relief / plaque on a wall."""
    cw, ch = crest.size
    cv.rect(x - 2, y - 2, x + cw + 1, y + ch + 1, frame)
    cp = crest.load()
    for yy in range(ch):
        for xx in range(cw):
            p = cp[xx, yy]
            if p[3] > 100:
                cv.put(x + xx, y + yy, shade(p[:3], 0.78))


def sword(cv, x, yb, ln, blade, hilt, tilt=0):
    for i in range(ln):
        cv.put(x + int(i * tilt), yb - i, blade)
        if i > ln * 0.15:
            cv.put(x + 1 + int(i * tilt), yb - i, shade(blade, 0.7))
    gx = x + int(ln * tilt)
    cv.rect(gx - 3, yb - ln, gx + 4, yb - ln, hilt)
    cv.rect(gx, yb - ln - 5, gx + 1, yb - ln - 1, hilt)
    cv.put(gx, yb - ln - 6, shade(hilt, 1.4))


def grave(cv, x, yb, c, cross=False):
    if cross:
        cv.rect(x + 2, yb - 11, x + 3, yb, c)
        cv.rect(x, yb - 8, x + 5, yb - 7, c)
    else:
        cv.rect(x, yb - 8, x + 6, yb, c)
        cv.rect(x + 1, yb - 9, x + 5, yb - 9, c)


def flask(cv, x, yb, c, liquid, glass):
    cv.disc(x, yb - 6, 6, glass)
    cv.disc(x, yb - 6, 5, shade(glass, 0.6))
    for y in range(yb - 5, yb + 1):
        for xx in range(x - 5, x + 6):
            if (xx - x) ** 2 + (y - (yb - 6)) ** 2 <= 25:
                cv.put(xx, y, liquid)
    cv.rect(x - 1, yb - 18, x + 1, yb - 11, glass)
    cv.rect(x - 2, yb - 19, x + 2, yb - 19, c)


def cauldron(cv, x, yb, c, liquid):
    cv.rect(x - 7, yb - 8, x + 7, yb, c)
    cv.rect(x - 8, yb - 9, x + 8, yb - 8, shade(c, 1.2))
    cv.rect(x - 6, yb - 8, x + 6, yb - 7, liquid)
    cv.rect(x - 6, yb, x - 5, yb + 1, c)
    cv.rect(x + 5, yb, x + 6, yb + 1, c)


def gear(cv, cx, cy, rad, c, teeth=10):
    for k in range(teeth):
        a = k * 2 * math.pi / teeth
        for t in range(3):
            cv.disc(cx + math.cos(a) * (rad + t), cy + math.sin(a) * (rad + t), 1.4, c)
    cv.disc(cx, cy, rad, c)
    cv.disc(cx, cy, rad * 0.35, (0, 0, 0))


def wolf(cv, x, yb, c, howl=False):
    body = [(0, 0), (12, 0), (14, -6), (2, -7), (-2, -5)]
    cv.rect(x, yb - 7, x + 12, yb - 3, c)
    for lx in (1, 3, 10, 12):
        cv.rect(x + lx, yb - 3, x + lx, yb, c)
    if howl:
        cv.poly([(x + 11, yb - 7), (x + 14, yb - 15), (x + 16, yb - 14), (x + 14, yb - 6)], c)
        cv.put(x + 14, yb - 16, c)
    else:
        cv.rect(x + 12, yb - 10, x + 16, yb - 6, c)
        cv.put(x + 13, yb - 11, c)
    cv.line(x, yb - 6, x - 4, yb - 9, c)


def figure(cv, x, yb, c, h=14, weapon=None):
    cv.rect(x - 1, yb - h + 3, x + 1, yb - 4, c)          # body
    cv.rect(x - 1, yb - h, x + 1, yb - h + 2, c)          # head
    cv.rect(x - 1, yb - 3, x - 1, yb, c)
    cv.rect(x + 1, yb - 3, x + 1, yb, c)
    if weapon == "spear":
        cv.line(x + 3, yb, x + 3, yb - h - 6, c)
    elif weapon == "shield":
        cv.rect(x + 2, yb - h + 4, x + 5, yb - 4, c)


def stars_twinkle(cv, r, n, c):
    for _ in range(n):
        cv.put(r.randrange(cv.w), r.randrange(cv.h // 2), c)


def mute(cv, f):
    for y in range(cv.h):
        for x in range(cv.w):
            p = cv.px[x, y]
            if p[3]:
                cv.px[x, y] = (int(p[0] * f), int(p[1] * f), int(p[2] * f), p[3])



# ------------------------------------------------------------------ buildings (Simon 01:31: "die häuser designs
# sollten aber nochmal überarbeitet werden"): textured walls, shingled roofs, framed windows, lit from the moon side

def stone_wall(cv, x0, y0, x1, y1, base, r, light_left=True):
    """Ashlar: courses of 3 rows, bricks 4-7 wide, mortar lines, a few lighter and darker stones."""
    mortar = shade(base, 0.7)
    y = y1
    row = 0
    while y >= y0:
        x = x0 - (row % 2) * 3
        while x <= x1:
            bw = r.randrange(4, 8)
            tone = shade(base, r.choice([0.92, 1.0, 1.0, 1.08, 1.16]))
            for yy in range(max(y0, y - 2), y + 1):
                for xx in range(max(x0, x), min(x1, x + bw - 1) + 1):
                    cv.put(xx, yy, tone)
            for yy in range(max(y0, y - 2), y + 1):
                if x0 <= x + bw - 1 <= x1:
                    cv.put(x + bw - 1, yy, mortar)
            if y - 2 >= y0:
                for xx in range(max(x0, x), min(x1, x + bw - 1) + 1):
                    cv.put(xx, y - 2, shade(tone, 1.12))   # the lit top edge of each stone
            x += bw
        for xx in range(x0, x1 + 1):
            if y - 3 >= y0:
                cv.put(xx, y - 3, mortar)
        y -= 4
        row += 1
    edge = x0 if light_left else x1
    for yy in range(y0, y1 + 1):
        cv.put(edge, yy, shade(base, 1.3))


def timber_wall(cv, x0, y0, x1, y1, plaster, beam, r):
    """Half-timbering: plaster panels between dark posts, a rail in the middle, braces in the corners."""
    cv.rect(x0, y0, x1, y1, plaster)
    for yy in range(y0, y1 + 1):   # a little stain on the plaster
        for xx in range(x0, x1 + 1):
            if r.random() < 0.08:
                cv.put(xx, yy, shade(plaster, 0.9))
    step = 7
    xs = list(range(x0, x1 + 1, step))
    if xs[-1] != x1:
        xs.append(x1)
    for x in xs:
        cv.rect(x, y0, x + 1 if x < x1 else x, y1, beam)
    cv.rect(x0, y0, x1, y0 + 1, beam)
    cv.rect(x0, y1 - 1, x1, y1, beam)
    mid = (y0 + y1) // 2
    cv.rect(x0, mid, x1, mid, beam)
    for a, b in zip(xs, xs[1:]):
        if (a // step) % 2 == 0:
            cv.line(a + 1, y1 - 1, b - 1, mid + 1, beam)
        else:
            cv.line(a + 1, mid - 1, b - 1, y0 + 2, beam)


def roof(cv, x0, x1, yb, h, tile, r, overhang=3):
    """A gable roof seen from the front: shingle rows that alternate, a lighter ridge, dark eaves."""
    x0 -= overhang
    x1 += overhang
    cx = (x0 + x1) / 2.0
    for y in range(yb - h, yb + 1):
        t = (y - (yb - h)) / max(1, h)
        half = (x1 - x0) / 2.0 * t
        row = (y - (yb - h)) // 2
        for x in range(int(cx - half), int(cx + half) + 1):
            c = shade(tile, 1.0 if row % 2 else 0.86)
            if (x + row * 2) % 4 == 0 and y % 2 == 0:
                c = shade(tile, 0.72)
            if x < cx:
                c = shade(c, 1.12)   # the moon side
            cv.put(x, y, c)
    cv.rect(x0, yb, x1, yb, shade(tile, 0.55))
    cv.put(int(cx), yb - h - 1, shade(tile, 1.3))


def cone(cv, cx, yb, rad, h, tile):
    """A conical tower roof with shingle rings."""
    for y in range(yb - h, yb + 1):
        t = (y - (yb - h)) / max(1, h)
        half = rad * t + 1
        row = (y - (yb - h)) // 2
        for x in range(int(cx - half), int(cx + half) + 1):
            c = shade(tile, 1.0 if row % 2 else 0.85)
            if x < cx:
                c = shade(c, 1.12)
            cv.put(x, y, c)
    cv.rect(cx, yb - h - 4, cx, yb - h - 1, (110, 100, 80))


def window(cv, x, y, w, h, lit, frame, arch=False, cross=True):
    cv.rect(x - 1, y - 1, x + w, y + h, frame)
    cv.rect(x, y, x + w - 1, y + h - 1, lit)
    for yy in range(y, y + h):   # warmer at the bottom, as from a fire inside
        for xx in range(x, x + w):
            if yy < y + h // 3:
                cv.put(xx, yy, shade(lit, 0.85))
    if arch:
        for xx in range(x, x + w):
            if xx in (x, x + w - 1):
                cv.put(xx, y, frame)
    if cross:
        cv.rect(x + w // 2, y, x + w // 2, y + h - 1, frame)
        cv.rect(x, y + h // 2, x + w - 1, y + h // 2, frame)
    cv.rect(x - 1, y + h, x + w, y + h, shade(frame, 1.4))   # the sill
    cv.glow(x + w / 2, y + h / 2, max(w, h) * 1.8, lit, 0.28)


def door(cv, x, yb, w, h, wood, frame, lit=None):
    cv.rect(x - 1, yb - h - 1, x + w, yb, frame)
    cv.rect(x, yb - h, x + w - 1, yb, lit if lit else wood)
    cv.put(x, yb - h, frame)
    cv.put(x + w - 1, yb - h, frame)
    if not lit:
        for xx in range(x + 1, x + w - 1, 2):
            cv.rect(xx, yb - h + 1, xx, yb, shade(wood, 0.8))
        cv.put(x + w - 2, yb - h // 2, (200, 170, 90))
    else:
        cv.glow(x + w / 2, yb - h / 2, h * 1.4, lit, 0.35)


def chimney(cv, x, yb, h, base, r, smoke=None):
    cv.rect(x, yb - h, x + 4, yb, base)
    cv.rect(x - 1, yb - h - 1, x + 5, yb - h, shade(base, 1.2))
    if smoke:
        for k in range(6):
            cv.disc(x + 2 + k * 2 + r.randrange(-1, 2), yb - h - 4 - k * 4, 1.5 + k * 0.5, smoke)

# ------------------------------------------------------------------ the scenes

def scene(sub):
    r = random.Random(sum(map(ord, sub)) * 7919)
    top, hor, acc = COL[sub]
    sky = sky_layer(sub, r)
    mid = Canvas(MIDW, HH)
    front = Canvas(FRONTW, HH)
    crest = Image.open(os.path.join(CRESTS, "crest_" + CREST[sub] + ".png")).convert("RGBA")
    far = mix(hor, (6, 5, 8), 0.55)
    farther = mix(hor, (20, 18, 24), 0.35)
    near = mix(hor, (4, 3, 5), 0.8)
    darkest = mix(top, (0, 0, 0), 0.5)
    gy = 112   # ground line of the mid layer
    fy = 122   # ground line of the front layer
    acc_d = shade(acc, 0.55)
    pole = (60, 46, 34)
    cloth = shade(acc, 0.35)

    if sub == "blademaster":
        moon(sky, 70, 28, 11, halo=(120, 60, 80))
        ridge(sky, 92, 10, 0.025, farther, 1)
        ridge(mid, gy, 3, 0.05, far, 2)
        for x, t, ln in ((40, 0.08, 26), (62, -0.12, 30), (90, 0.05, 22), (170, -0.06, 28), (196, 0.1, 24), (230, -0.04, 30)):
            sword(mid, x, gy - 1, ln, shade(acc, 0.5), (90, 60, 40), t)
        banner(mid, 120, 34, crest, pole, cloth, glow=(80, 40, 50))
        for x in range(0, FRONTW, 31):
            sword(front, x + r.randrange(0, 10), fy + 2, r.randrange(14, 22), shade(acc, 0.32), (50, 34, 24), r.uniform(-0.2, 0.2))
        ground(front, fy, near, 3, grass=shade(near, 1.4))
    elif sub == "berserker":
        moon(sky, 180, 30, 14, c=(200, 50, 40), halo=(180, 30, 20))
        ridge(sky, 90, 12, 0.03, farther, 4)
        ridge(mid, gy, 4, 0.04, far, 5)
        for x in (20, 70, 200, 238):   # burning ruins
            h = r.randrange(16, 30)
            mid.rect(x, gy - h, x + 14, gy, far)
            for k in range(0, 14, 3):
                mid.rect(x + k, gy - h - r.randrange(1, 5), x + k + 1, gy - h, far)
            mid.glow(x + 7, gy - h // 2, 16, (200, 70, 20), 0.5)
            mid.put(x + 5, gy - 6, (255, 150, 40))
            mid.put(x + 9, gy - 9, (255, 120, 30))
        banner(mid, 118, 30, crest, pole, (70, 14, 12), glow=(180, 40, 20))
        ground(front, fy, near, 6, grass=shade(near, 1.3))
        for x in range(10, FRONTW, 46):
            front.line(x, fy + 1, x + 6, fy - 16, (40, 28, 20))   # spears in the ground
            front.rect(x + 5, fy - 19, x + 7, fy - 16, (70, 70, 74))
    elif sub == "axe":
        moon(sky, 60, 26, 10, halo=(100, 70, 40))
        ridge(sky, 86, 12, 0.03, farther, 7)
        for x in range(0, MIDW, 13):
            pine(mid, x + r.randrange(0, 6), gy - 2, r.randrange(18, 30), far)
        ridge(mid, gy, 2, 0.06, far, 8)
        for x in (80, 150, 190):
            mid.rect(x, gy - 6, x + 9, gy, (60, 40, 26))
            mid.rect(x, gy - 7, x + 9, gy - 6, (110, 80, 50))
        mid.line(155, gy - 6, 162, gy - 16, (70, 50, 30), 2)
        mid.rect(160, gy - 19, 166, gy - 14, (140, 140, 150))
        mid.glow(118, gy - 3, 14, (230, 120, 40), 0.6)
        mid.rect(116, gy - 3, 120, gy - 1, (255, 150, 50))
        banner(mid, 196, 30, crest, pole, cloth)
        ground(front, fy, near, 9, grass=shade(near, 1.3))
        for x in range(0, FRONTW, 60):
            front.rect(x + 10, fy - 4, x + 30, fy - 1, (40, 28, 18))   # logs
            front.rect(x + 30, fy - 4, x + 31, fy - 1, (90, 66, 40))
    elif sub == "paladin":
        moon(sky, 210, 24, 9, halo=(120, 100, 60))
        ridge(sky, 94, 8, 0.03, farther, 10)
        ridge(mid, gy, 2, 0.05, far, 12)
        stone = mix(hor, (110, 100, 86), 0.4)
        tile = mix(hor, (60, 56, 64), 0.5)
        gold = (240, 200, 110)
        cx = 140
        stone_wall(mid, cx - 38, gy - 52, cx + 38, gy, stone, r)        # the nave front
        mid.poly([(cx - 40, gy - 52), (cx + 40, gy - 52), (cx, gy - 76)], shade(stone, 0.9))
        for x in range(cx - 38, cx + 39, 6):                             # the gable edge
            mid.put(x, gy - 52 - int((38 - abs(x - cx)) * 0.63), shade(stone, 1.3))
        for tx in (cx - 54, cx + 38):                                    # two towers with spires
            stone_wall(mid, tx, gy - 70, tx + 16, gy, shade(stone, 0.95), r, light_left=tx < cx)
            cone(mid, tx + 8, gy - 71, 9, 26, tile)
            window(mid, tx + 6, gy - 60, 4, 10, gold, shade(stone, 0.6), cross=False)
            window(mid, tx + 6, gy - 34, 4, 8, shade(gold, 0.8), shade(stone, 0.6), cross=False)
        for bx in (cx - 39, cx + 37):                                    # buttresses
            mid.rect(bx, gy - 40, bx + 2, gy, shade(stone, 0.8))
        mid.disc(cx, gy - 44, 10, shade(stone, 0.6))                     # the rose window
        cols = [(200, 60, 40), (60, 90, 200), (230, 190, 90), (60, 150, 80)]
        for k in range(8):
            a = k * math.pi / 4
            mid.disc(cx + math.cos(a) * 6, gy - 44 + math.sin(a) * 6, 2.5, shade(cols[k % 4], 0.8))
        mid.disc(cx, gy - 44, 3, gold)
        mid.glow(cx, gy - 44, 30, gold, 0.4)
        for x in (cx - 26, cx + 20):                                     # pointed windows of the nave
            window(mid, x, gy - 30, 6, 12, shade(gold, 0.85), shade(stone, 0.6))
            mid.put(x + 2, gy - 32, shade(stone, 0.6))
            mid.put(x + 3, gy - 32, shade(stone, 0.6))
        door(mid, cx - 6, gy, 12, 18, (70, 48, 30), shade(stone, 0.6), lit=(235, 200, 110))
        for k in range(-30, 31, 10):                                     # light from the door
            mid.line(cx, gy, cx + k, gy + 30, shade(gold, 0.22))
        banner(mid, 36, 40, crest, pole, cloth, glow=(160, 130, 60))
        ground(front, fy, near, 11, grass=shade(near, 1.3))
        for x in range(6, FRONTW, 40):
            grave(front, x, fy, shade(near, 1.5), cross=True)
    elif sub == "swift":
        moon(sky, 50, 30, 8, halo=(80, 120, 110))
        for i in range(5):   # wind lines in the sky
            y = 30 + i * 9
            for x in range(r.randrange(0, 60), HW, 70):
                sky.line(x, y, x + 24, y - 2, shade(acc, 0.22))
        ridge(sky, 96, 6, 0.02, farther, 13)
        ridge(mid, gy, 6, 0.03, far, 14, rough=0.4)
        for x in range(0, MIDW, 3):   # tall grass bending
            h = r.randrange(4, 10)
            mid.line(x, gy, x + 3, gy - h, shade(far, 1.4))
        banner(mid, 150, 32, crest, pole, cloth)
        for k in range(6):   # birds
            bx, by = r.randrange(20, 250), r.randrange(10, 40)
            mid.put(bx, by, near)
            mid.put(bx - 1, by - 1, near)
            mid.put(bx + 1, by - 1, near)
        ground(front, fy, near, 15, grass=shade(near, 1.5))
        for x in range(0, FRONTW, 70):
            front.line(x + 20, fy, x + 26, fy - 18, (50, 40, 30))   # arrows stuck in the ground
            front.rect(x + 25, fy - 20, x + 27, fy - 18, (200, 200, 200))
    elif sub == "heavy":
        moon(sky, 200, 26, 10, halo=(90, 100, 60))
        ridge(sky, 86, 14, 0.035, farther, 16)
        ridge(mid, gy, 2, 0.06, far, 17)
        stone = mix(hor, (96, 96, 84), 0.45)
        tile = mix(hor, (70, 52, 40), 0.5)
        lamp = (230, 170, 80)
        # a stone watchtower with a heavy crossbow on its platform
        stone_wall(mid, 30, gy - 48, 62, gy, stone, r)
        for x in range(28, 64, 6):
            mid.rect(x, gy - 53, x + 3, gy - 49, stone)
        mid.rect(28, gy - 49, 64, gy - 48, shade(stone, 1.3))
        window(mid, 42, gy - 38, 5, 7, lamp, shade(stone, 0.6))
        window(mid, 42, gy - 22, 5, 6, shade(lamp, 0.8), shade(stone, 0.6))
        door(mid, 41, gy, 8, 11, (70, 48, 30), shade(stone, 0.6))
        mid.line(48, gy - 55, 78, gy - 62, (80, 58, 36), 2)
        mid.line(62, gy - 66, 68, gy - 50, (80, 58, 36), 2)
        mid.line(60, gy - 64, 76, gy - 60, (200, 200, 200))
        oak(mid, 212, gy, 16, far, mix(far, (20, 50, 20), 0.4))
        banner(mid, 116, 34, crest, pole, cloth)
        ground(front, fy, near, 18, grass=shade(near, 1.3))
        for x in range(0, FRONTW, 50):
            front.rect(x + 8, fy - 8, x + 16, fy, (58, 42, 28))
            front.rect(x + 8, fy - 8, x + 16, fy - 8, (90, 66, 40))
    elif sub == "bolter":
        moon(sky, 120, 22, 9, halo=(80, 90, 90))
        ridge(sky, 92, 10, 0.03, farther, 19)
        ridge(mid, gy, 3, 0.05, far, 20)
        for x in (30, 200):   # target stands
            mid.rect(x + 7, gy - 16, x + 8, gy, (60, 44, 30))
            for rad, c in ((8, (180, 170, 150)), (6, (160, 50, 40)), (4, (180, 170, 150)), (2, (160, 50, 40))):
                mid.disc(x + 8, gy - 22, rad, shade(c, 0.55))
            for k in range(3):
                mid.line(x + 4 + k * 3, gy - 26 + k * 2, x - 2 + k * 3, gy - 28 + k * 2, (120, 100, 70))
        mid.rect(90, gy - 28, 104, gy, far)   # a bolt rack
        for k in range(0, 14, 2):
            mid.line(91 + k, gy - 26, 91 + k, gy - 8, (150, 150, 160))
        banner(mid, 140, 32, crest, pole, cloth)
        ground(front, fy, near, 21, grass=shade(near, 1.3))
    elif sub == "trapper":
        moon(sky, 200, 24, 8, halo=(60, 90, 60))
        for x in range(0, HW, 9):
            pine(sky, x + r.randrange(0, 4), 100, r.randrange(20, 34), farther)
        for x in range(0, MIDW, 11):
            pine(mid, x + r.randrange(0, 5), gy, r.randrange(26, 44), far)
        banner(mid, 118, 40, crest, pole, cloth)
        for x in (40, 200):   # snares on the path
            mid.line(x, gy - 30, x + 6, gy - 2, (120, 100, 70))
        ground(front, fy, near, 22, grass=shade(near, 1.6))
        for x in range(20, FRONTW, 90):   # bear traps
            front.rect(x, fy - 1, x + 12, fy, (90, 90, 96))
            for k in range(0, 13, 2):
                front.put(x + k, fy - 3, (120, 120, 126))
    elif sub == "shieldfighter":
        moon(sky, 60, 22, 10, halo=(70, 90, 140))
        ridge(sky, 92, 8, 0.03, farther, 24)
        castle(mid, 150, gy, 90, 34, far, (230, 190, 100), r)
        mid.rect(186, gy - 18, 202, gy, (16, 14, 18))   # the gate
        ridge(mid, gy, 1, 0.05, far, 25)
        banner(mid, 40, 32, crest, pole, cloth)
        ground(front, fy, near, 26, grass=shade(near, 1.3))
        for x in range(4, FRONTW, 14):   # a shield wall
            front.rect(x, fy - 14, x + 10, fy, shade(acc, 0.28))
            front.rect(x + 4, fy - 9, x + 6, fy - 6, shade((200, 170, 90), 0.4))
            front.line(x + 5, fy - 14, x + 5, fy - 22, (60, 50, 40))
    elif sub == "bulwark":
        moon(sky, 190, 22, 11, halo=(90, 90, 110))
        ridge(sky, 90, 12, 0.03, farther, 27)
        for x in range(0, MIDW):   # a huge wall across the land
            for y in range(gy - 44, gy + 1):
                row = (y - gy) // 5
                c = shade(far, 1.25 if ((x + (row % 2) * 6) % 12 == 0 or (y - gy) % 5 == 0) else 1.0)
                mid.put(x, y, c)
        for x in range(0, MIDW, 10):
            mid.rect(x, gy - 50, x + 5, gy - 45, far)
        for x in range(30, MIDW, 70):
            mid.rect(x, gy - 30, x + 2, gy - 26, (230, 180, 90))
            mid.glow(x + 1, gy - 28, 8, (230, 160, 70), 0.35)
        wall_crest(mid, 122, gy - 42, crest, shade(far, 1.5))
        ground(front, fy, near, 28, grass=shade(near, 1.2))
    elif sub == "medic":
        moon(sky, 50, 26, 9, halo=(70, 120, 110))
        ridge(sky, 92, 10, 0.03, farther, 30)
        ridge(mid, gy, 2, 0.05, far, 31)
        plaster = mix(hor, (130, 130, 118), 0.45)
        beam = (44, 34, 26)
        stone = mix(hor, (96, 100, 96), 0.45)
        tile = mix(hor, (90, 54, 44), 0.5)
        warm = (230, 210, 140)
        # the infirmary: stone base, timbered walls, a red cross over the door
        stone_wall(mid, 150, gy - 8, 230, gy, stone, r)
        timber_wall(mid, 150, gy - 36, 230, gy - 9, plaster, beam, r)
        roof(mid, 150, 230, gy - 37, 22, tile, r)
        door(mid, 184, gy, 10, 16, (70, 48, 30), beam, lit=warm)
        mid.rect(186, gy - 31, 192, gy - 29, (190, 50, 44))
        mid.rect(188, gy - 33, 190, gy - 27, (190, 50, 44))
        for x in (158, 170, 204, 216):
            window(mid, x, gy - 26, 6, 8, warm, beam)
        window(mid, 188, gy - 50, 4, 5, shade(warm, 0.8), beam, cross=False)
        chimney(mid, 214, gy - 48, 12, shade(stone, 0.9), r, smoke=shade(far, 1.3))
        # the healing well with its little roof
        stone_wall(mid, 50, gy - 6, 70, gy, stone, r)
        mid.rect(51, gy - 7, 69, gy - 6, (90, 200, 160))
        mid.rect(52, gy - 22, 53, gy - 7, beam)
        mid.rect(67, gy - 22, 68, gy - 7, beam)
        roof(mid, 52, 68, gy - 22, 8, tile, r, overhang=2)
        mid.glow(60, gy - 8, 16, (80, 220, 160), 0.5)
        banner(mid, 104, 34, crest, pole, cloth)
        ground(front, fy, near, 32, grass=shade(near, 1.5))
        for x in range(0, FRONTW, 4):
            if r.random() < 0.5:
                h = r.randrange(2, 6)
                front.line(x, fy, x, fy - h, shade((70, 140, 60), 0.6))
                front.put(x, fy - h - 1, shade(r.choice([(220, 90, 90), (230, 220, 120), (230, 230, 230)]), 0.6))
    elif sub == "warden":
        for x in range(HW):   # aurora
            for k in range(3):
                y0 = 18 + k * 9 + int(math.sin(x * 0.05 + k) * 6)
                for y in range(y0, y0 + 10):
                    a = (1 - abs(y - y0 - 5) / 5.0) * 0.18
                    o = sky.px[x, y]
                    c = mix(o[:3], (60, 230, 200) if k != 1 else (120, 120, 240), a)
                    sky.px[x, y] = c + (255,)
        ridge(sky, 92, 10, 0.03, farther, 33)
        ridge(mid, gy, 3, 0.05, far, 34)
        cx = 136   # a circle of ward stones
        for k in range(7):
            x = cx - 60 + k * 20
            h = 16 + (6 if k in (0, 6) else 0) + r.randrange(-2, 3)
            mid.rect(x, gy - h, x + 6, gy, shade(far, 1.3))
            mid.rect(x + 2, gy - h + 3, x + 4, gy - h + 6, shade(acc, 0.5))
            mid.glow(x + 3, gy - h + 4, 6, acc, 0.35)
        banner(mid, cx - 14, 30, crest, pole, cloth, glow=shade(acc, 0.5))
        ground(front, fy, near, 35, grass=shade(near, 1.3))
    elif sub == "gunner":
        moon(sky, 44, 24, 9, halo=(90, 80, 60))
        ridge(sky, 90, 10, 0.03, farther, 36)
        ridge(mid, gy, 2, 0.05, far, 38)
        brick = mix(hor, (120, 70, 50), 0.45)
        tile = mix(hor, (64, 64, 70), 0.5)
        lamp = (230, 180, 100)
        # the workshop: brick, a saw-tooth roof with skylights, a big gear on the front
        stone_wall(mid, 132, gy - 30, 226, gy, brick, r)
        for k in range(3):
            x0 = 132 + k * 32
            mid.poly([(x0, gy - 30), (x0 + 32, gy - 30), (x0 + 32, gy - 46)], tile)
            mid.rect(x0 + 28, gy - 44, x0 + 31, gy - 32, shade(lamp, 0.7))
        mid.rect(130, gy - 31, 228, gy - 30, shade(brick, 0.6))
        door(mid, 166, gy, 24, 20, (60, 46, 34), shade(brick, 0.6), lit=lamp)   # the big gate, open
        for x in (140, 202, 214):
            window(mid, x, gy - 22, 6, 8, lamp, shade(brick, 0.6))
        gear(mid, 178, gy - 38, 6, shade(tile, 1.4), 9)
        chimney(mid, 216, gy - 46, 18, shade(brick, 0.85), r, smoke=shade(far, 1.25))
        for x in (34, 84):                                               # turrets on their posts
            mid.rect(x + 4, gy - 8, x + 8, gy, shade(far, 1.3))
            mid.rect(x, gy - 16, x + 12, gy - 8, shade(tile, 1.2))
            mid.rect(x + 2, gy - 14, x + 4, gy - 12, (230, 100, 60))
            mid.line(x + 12, gy - 13, x + 24, gy - 17, shade(acc, 0.45), 2)
        banner(mid, 100, 30, crest, pole, cloth)
        ground(front, fy, near, 37, grass=None)
        for x in range(10, FRONTW, 60):
            gear(front, x, fy - 6, 5, shade(near, 1.6), 8)
    elif sub == "electrician":
        for k in range(6):   # storm clouds
            cx, cy = r.randrange(0, HW), r.randrange(10, 40)
            for j in range(5):
                sky.disc(cx + j * 7 - 14, cy + r.randrange(-3, 4), 8, mix(top, hor, 0.5))
        ridge(sky, 94, 8, 0.03, farther, 39)
        ridge(mid, gy, 2, 0.05, far, 40)
        for cx in (40, 230):   # tesla coils
            for k in range(12):
                mid.rect(cx - 5, gy - 4 - k * 3, cx + 5, gy - 3 - k * 3, shade((200, 120, 60), 0.45 if k % 2 else 0.35))
            mid.disc(cx, gy - 44, 7, shade((200, 160, 90), 0.6))
            mid.glow(cx, gy - 44, 18, acc, 0.45)
        pts = [(46, gy - 46), (80, gy - 60), (100, gy - 40), (130, gy - 70), (160, gy - 44), (190, gy - 64), (224, gy - 46)]
        for a, b in zip(pts, pts[1:]):
            mid.line(a[0], a[1], b[0], b[1], shade(acc, 0.7))
        banner(mid, 122, 36, crest, pole, cloth)
        ground(front, fy, near, 41)
        for x in range(0, FRONTW, 2):   # cables
            front.put(x, fy - 3 + int(math.sin(x * 0.08) * 2), (30, 26, 22))
    elif sub == "demolition":
        moon(sky, 200, 30, 12, c=(220, 160, 100), halo=(160, 70, 20))
        ridge(sky, 88, 14, 0.03, farther, 42)
        for x in range(0, MIDW):   # a quarry edge with smoke
            h = 30 + int(math.sin(x * 0.04) * 8)
            if 90 < x < 170:
                h = 10 + int(abs(x - 130) * 0.4)
            for y in range(gy - h, gy + 1):
                mid.put(x, y, far if (y + x) % 7 else shade(far, 1.2))
        mid.glow(130, gy - 10, 30, (240, 120, 40), 0.55)
        for k in range(10):
            mid.disc(130 + r.randrange(-20, 21), gy - 20 - k * 4, 4 + k * 0.6, shade(far, 1.25))
        for x in (40, 52, 200, 212):   # tnt barrels
            mid.rect(x, gy - 8 - (x % 3), x + 8, gy - (x % 3), (120, 40, 30))
            mid.rect(x, gy - 5 - (x % 3), x + 8, gy - 4 - (x % 3), (200, 190, 170))
        banner(mid, 62, 26, crest, pole, cloth)
        ground(front, fy, near, 43)
        for x in range(0, FRONTW, 16):   # rubble
            front.rect(x, fy - r.randrange(1, 4), x + r.randrange(2, 6), fy, shade(near, 1.5))
    elif sub == "alchemist":
        moon(sky, 196, 24, 9, c=(200, 230, 170), halo=(80, 140, 60))
        ridge(sky, 92, 10, 0.03, farther, 45)
        ridge(mid, gy, 2, 0.05, far, 44)
        stone = mix(hor, (92, 96, 84), 0.45)
        plaster = mix(hor, (120, 116, 92), 0.45)
        beam = (40, 30, 22)
        tile = mix(hor, (62, 82, 46), 0.55)
        green = (130, 235, 100)
        # the house: stone below, half-timbered upper floor jutting out, a mossy gable roof
        stone_wall(mid, 26, gy - 20, 88, gy, stone, r)
        timber_wall(mid, 23, gy - 38, 91, gy - 21, plaster, beam, r)
        mid.rect(22, gy - 21, 92, gy - 20, beam)
        roof(mid, 23, 91, gy - 39, 22, tile, r)
        window(mid, 33, gy - 15, 6, 7, green, beam)
        window(mid, 71, gy - 15, 6, 7, green, beam)
        door(mid, 52, gy, 8, 12, (70, 48, 30), shade(stone, 0.7))
        mid.rect(48, gy - 16, 64, gy - 15, beam)                       # a sign above the door
        flask(mid, 56, gy - 18, (90, 70, 50), (120, 230, 90), (150, 170, 170))
        for x in (30, 52, 74):
            window(mid, x, gy - 33, 5, 6, shade(green, 0.85), beam)
        mid.disc(57, gy - 48, 4, beam)
        mid.disc(57, gy - 48, 3, shade(green, 0.9))
        mid.glow(57, gy - 48, 10, green, 0.35)
        chimney(mid, 76, gy - 50, 14, shade(stone, 0.9), r, smoke=mix(far, (90, 150, 80), 0.35))
        # the tower with its observatory cone and a brass star
        stone_wall(mid, 92, gy - 62, 108, gy, shade(stone, 0.95), r, light_left=False)
        window(mid, 98, gy - 50, 4, 8, green, beam, cross=False)
        window(mid, 98, gy - 28, 4, 6, shade(green, 0.8), beam, cross=False)
        cone(mid, 100, gy - 63, 11, 22, tile)
        mid.put(100, gy - 91, (240, 210, 120))
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            mid.put(100 + dx, gy - 91 + dy, (200, 170, 90))
        # outside: a table with flasks, a bubbling cauldron, herbs
        mid.rect(150, gy - 8, 186, gy - 7, (70, 50, 32))
        mid.rect(152, gy - 7, 153, gy, (50, 36, 24))
        mid.rect(183, gy - 7, 184, gy, (50, 36, 24))
        for k, x in enumerate((156, 166, 178)):
            flask(mid, x, gy - 9, (90, 70, 50), shade([(120, 240, 100), (200, 90, 220), (240, 180, 60)][k], 0.75),
                  shade((180, 210, 220), 0.55))
        cauldron(mid, 214, gy, shade(stone, 0.8), (110, 230, 90))
        mid.glow(214, gy - 9, 16, (100, 230, 80), 0.5)
        mid.glow(214, gy + 1, 8, (240, 120, 40), 0.5)                  # the fire under it
        banner(mid, 118, 30, crest, pole, cloth)
        ground(front, fy, near, 46, grass=shade(near, 1.3))
        for x in range(0, FRONTW, 5):                                  # herbs
            if r.random() < 0.45:
                h = r.randrange(2, 6)
                front.line(x, fy, x, fy - h, shade((70, 120, 50), 0.55))
    elif sub == "tamer":
        moon(sky, 130, 32, 16, halo=(110, 120, 130))
        ridge(sky, 90, 10, 0.03, farther, 48)
        ridge(mid, gy - 6, 14, 0.02, far, 49, rough=0.3)
        wolf(mid, 120, gy - 24, near, howl=True)
        wolf(mid, 150, gy - 18, near)
        wolf(mid, 92, gy - 14, near)
        for x in range(0, MIDW, 14):
            pine(mid, x + 200 if x + 200 < MIDW else x - 200, gy, r.randrange(16, 26), far)
        banner(mid, 30, 36, crest, pole, cloth)
        ground(front, fy, near, 50, grass=shade(near, 1.4))
    elif sub == "golemsmith":
        moon(sky, 40, 24, 9, halo=(100, 70, 50))
        ridge(sky, 90, 12, 0.03, farther, 51)
        ridge(mid, gy, 2, 0.05, far, 53)
        stone = mix(hor, (96, 88, 82), 0.45)
        tile = mix(hor, (58, 52, 50), 0.5)
        fire = (245, 130, 50)
        # the forge hall: a wide stone front, a great arch open to the fire, two chimneys
        stone_wall(mid, 128, gy - 40, 236, gy, stone, r)
        roof(mid, 128, 236, gy - 41, 18, tile, r)
        mid.rect(156, gy - 30, 208, gy, (34, 20, 12))
        for x in range(156, 209):                                        # the arch
            top = gy - 30 - int(math.sqrt(max(0, 26 ** 2 - (x - 182) ** 2)) * 0.25)
            mid.rect(x, top, x, gy - 30, (34, 20, 12))
        mid.glow(182, gy - 10, 40, fire, 0.6)
        gx = 182                                                         # a golem frame on the scaffold
        iron = shade((170, 160, 150), 0.55)
        mid.rect(gx - 8, gy - 26, gx + 8, gy - 8, iron)
        mid.rect(gx - 4, gy - 32, gx + 4, gy - 26, iron)
        mid.rect(gx - 13, gy - 24, gx - 9, gy - 10, shade(iron, 0.9))
        mid.rect(gx + 9, gy - 24, gx + 13, gy - 10, shade(iron, 0.9))
        mid.rect(gx - 6, gy - 8, gx - 3, gy, shade(iron, 0.9))
        mid.rect(gx + 3, gy - 8, gx + 6, gy, shade(iron, 0.9))
        mid.put(gx - 2, gy - 29, fire)
        mid.put(gx + 2, gy - 29, fire)
        for x in (gx - 18, gx + 17):                                     # scaffold poles
            mid.rect(x, gy - 34, x + 1, gy, (70, 50, 32))
        mid.rect(gx - 18, gy - 34, gx + 18, gy - 33, (70, 50, 32))
        for x in (136, 216):
            window(mid, x, gy - 26, 6, 8, fire, shade(stone, 0.6))
        chimney(mid, 140, gy - 56, 20, shade(stone, 0.85), r, smoke=shade(far, 1.3))
        chimney(mid, 222, gy - 56, 24, shade(stone, 0.85), r, smoke=shade(far, 1.3))
        for x in (40, 70):                                               # anvils
            mid.rect(x, gy - 6, x + 10, gy - 4, shade(far, 1.6))
            mid.rect(x + 3, gy - 4, x + 7, gy, shade(far, 1.6))
        banner(mid, 92, 34, crest, pole, cloth)
        ground(front, fy, near, 52)
    elif sub == "necromancer":
        moon(sky, 70, 26, 12, c=(200, 190, 220), halo=(110, 70, 160))
        ridge(sky, 92, 10, 0.03, farther, 54)
        ridge(mid, gy, 3, 0.05, far, 55)
        stone = mix(hor, (96, 90, 108), 0.45)
        purple = (170, 100, 240)
        for k in range(10):
            grave(mid, 10 + k * 26 + r.randrange(-4, 5), gy, shade(far, 1.6), cross=k % 3 == 0)
        dead_tree(mid, 226, gy, 34, far, r)
        # the mausoleum: steps, four columns, a pediment with a skull, the purple door
        stone_wall(mid, 92, gy - 34, 136, gy - 4, stone, r)
        mid.rect(88, gy - 3, 140, gy, shade(stone, 1.1))
        mid.rect(86, gy - 1, 142, gy, shade(stone, 1.2))
        for x in (94, 104, 122, 132):
            mid.rect(x, gy - 32, x + 2, gy - 4, shade(stone, 1.25))
            mid.rect(x - 1, gy - 33, x + 3, gy - 32, shade(stone, 1.35))
        mid.poly([(88, gy - 34), (140, gy - 34), (114, gy - 48)], shade(stone, 0.95))
        mid.rect(88, gy - 35, 140, gy - 34, shade(stone, 1.3))
        mid.rect(112, gy - 43, 116, gy - 39, (190, 186, 170))
        mid.put(113, gy - 41, (30, 20, 30))
        mid.put(115, gy - 41, (30, 20, 30))
        door(mid, 109, gy - 4, 10, 18, (40, 30, 40), shade(stone, 0.6), lit=purple)
        banner(mid, 156, 30, crest, pole, cloth)
        ground(front, fy, near, 56)
        for x in range(0, FRONTW, 34):
            front.rect(x, fy - 1, x + 5, fy - 1, (150, 146, 130))
            front.put(x, fy - 2, (150, 146, 130))
            front.put(x + 5, fy - 2, (150, 146, 130))
    elif sub == "soulcatcher":
        moon(sky, 180, 24, 10, c=(180, 240, 240), halo=(40, 140, 140))
        ridge(sky, 88, 14, 0.03, farther, 57)
        for x in range(0, MIDW):   # soul sand dunes
            h = 6 + int(math.sin(x * 0.05) * 4 + math.sin(x * 0.13) * 2)
            for y in range(gy - h, gy + 1):
                mid.put(x, y, mix(far, (60, 46, 36), 0.25))
        for x in (30, 90, 210):   # soul fires
            mid.rect(x - 2, gy - 9, x + 2, gy - 7, (90, 240, 240))
            mid.put(x, gy - 11, (160, 255, 255))
            mid.glow(x, gy - 9, 14, (60, 220, 220), 0.6)
        for x in (60, 236):   # bone spines
            for k in range(5):
                mid.line(x + k * 3, gy - 6, x + k * 4 - 2, gy - 22 + k, (150, 150, 136))
        banner(mid, 136, 32, crest, pole, cloth, glow=(40, 150, 150))
        ground(front, fy, near, 58)

    mute(mid, 0.8)
    mute(front, 0.7)
    return sky, mid, front


def doubled(cv):
    return cv.im.resize((cv.w * 2, cv.h * 2), Image.NEAREST)


def save(sub, layers):
    os.makedirs(OUT, exist_ok=True)
    for name, cv in zip(("sky", "mid", "front"), layers):
        doubled(cv).save(os.path.join(OUT, "%s_%s.png" % (sub, name)))


def compose(layers, drift=0.0):
    sky, mid, front = (doubled(c) for c in layers)
    out = sky.copy()
    for im in (mid, front):
        extra = im.width - out.width
        off = int(extra / 2 + drift * extra / 2)
        out.alpha_composite(im.crop((off, 0, off + out.width, out.height)))
    return out


def main(argv):
    if argv and argv[0] == "--sheet":
        sheet = Image.new("RGBA", (480 * 5, 270 * 4))
        for i, sub in enumerate(SUBS):
            sheet.paste(compose(scene(sub)), ((i % 5) * 480, (i // 5) * 270))
        sheet.convert("RGB").save(argv[1])
        return
    for sub in argv or SUBS:
        save(sub, scene(sub))


if __name__ == "__main__":
    main(sys.argv[1:])
