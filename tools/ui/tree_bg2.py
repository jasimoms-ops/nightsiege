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
    cv.rect(x0, yb - h, x0 + w, yb, c)
    for x in range(x0, x0 + w + 1, crenel * 2):
        cv.rect(x, yb - h - crenel, x + crenel - 1, yb - h - 1, c)
    tw = max(5, w // 6)
    for i in range(towers):
        tx = x0 - 2 if i == 0 else x0 + w - tw + 2
        th = h + 10 + r.randrange(0, 6)
        cv.rect(tx, yb - th, tx + tw, yb, c)
        cv.poly([(tx - 1, yb - th), (tx + tw + 1, yb - th), (tx + tw // 2, yb - th - tw)], c)
        if lit:
            cv.put(tx + tw // 2, yb - th + 4, lit)
            cv.put(tx + tw // 2, yb - th + 5, lit)
    if lit:
        for _ in range(w // 8):
            wx, wy = r.randrange(x0 + 3, x0 + w - 3), r.randrange(yb - h + 3, yb - 4)
            cv.put(wx, wy, lit)
            cv.put(wx, wy + 1, lit)
            cv.glow(wx, wy, 4, lit, 0.25)


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
        moon(sky, 196, 24, 9, halo=(120, 100, 60))
        ridge(sky, 94, 8, 0.03, farther, 10)
        cx = 136   # a cathedral with a lit rose window
        mid.rect(cx - 40, gy - 50, cx + 40, gy, far)
        mid.poly([(cx - 44, gy - 50), (cx + 44, gy - 50), (cx, gy - 74)], far)
        for tx in (cx - 52, cx + 40):
            mid.rect(tx, gy - 66, tx + 12, gy, far)
            mid.poly([(tx - 1, gy - 66), (tx + 13, gy - 66), (tx + 6, gy - 84)], far)
        mid.disc(cx, gy - 44, 10, (40, 30, 20))
        cols = [(200, 60, 40), (60, 90, 200), (230, 190, 90), (60, 150, 80)]
        for k in range(8):
            a = k * math.pi / 4
            mid.disc(cx + math.cos(a) * 6, gy - 44 + math.sin(a) * 6, 2.5, shade(cols[k % 4], 0.75))
        mid.disc(cx, gy - 44, 3, (240, 210, 120))
        mid.glow(cx, gy - 44, 30, (240, 190, 90), 0.45)
        for k in range(-30, 31, 12):   # light from the door
            mid.line(cx, gy - 10, cx + k, gy + 30, shade((240, 200, 110), 0.25))
        mid.rect(cx - 5, gy - 16, cx + 5, gy, (230, 190, 100))
        banner(mid, 40, 40, crest, pole, cloth, glow=(160, 130, 60))
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
        moon(sky, 190, 26, 10, halo=(90, 100, 60))
        ridge(sky, 86, 14, 0.035, farther, 16)
        mid.rect(30, gy - 46, 62, gy, far)   # a stone watchtower with a heavy crossbow
        for x in range(28, 64, 6):
            mid.rect(x, gy - 50, x + 3, gy - 46, far)
        mid.rect(40, gy - 38, 44, gy - 34, (230, 170, 80))
        mid.glow(42, gy - 36, 8, (230, 160, 60), 0.4)
        mid.line(48, gy - 52, 78, gy - 60, (70, 50, 30), 2)
        mid.line(60, gy - 64, 66, gy - 48, (70, 50, 30), 2)
        oak(mid, 210, gy, 16, far, mix(far, (20, 50, 20), 0.4))
        ridge(mid, gy, 2, 0.06, far, 17)
        banner(mid, 116, 34, crest, pole, cloth)
        ground(front, fy, near, 18, grass=shade(near, 1.3))
        for x in range(0, FRONTW, 50):
            front.rect(x + 8, fy - 8, x + 16, fy, (58, 42, 28))   # crates of bolts
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
        moon(sky, 70, 26, 9, halo=(70, 120, 110))
        ridge(sky, 92, 10, 0.03, farther, 30)
        ridge(mid, gy, 3, 0.05, far, 31)
        mid.rect(170, gy - 28, 210, gy, far)   # a field hospital tent
        mid.poly([(166, gy - 28), (214, gy - 28), (190, gy - 46)], far)
        mid.rect(186, gy - 40, 194, gy - 32, shade((220, 60, 50), 0.55))
        mid.rect(188, gy - 42, 192, gy - 30, shade((220, 60, 50), 0.55))
        mid.rect(186, gy - 16, 194, gy, (210, 190, 120))
        mid.glow(190, gy - 8, 16, (220, 200, 120), 0.4)
        mid.disc(60, gy - 6, 8, shade(far, 1.4))   # a healing well
        mid.rect(53, gy - 6, 67, gy, shade(far, 1.4))
        mid.rect(56, gy - 7, 64, gy - 6, (90, 200, 160))
        mid.glow(60, gy - 8, 14, (80, 220, 160), 0.5)
        banner(mid, 110, 34, crest, pole, cloth)
        ground(front, fy, near, 32, grass=shade(near, 1.5))
        for x in range(0, FRONTW, 4):   # herbs and flowers
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
        moon(sky, 50, 24, 9, halo=(90, 80, 60))
        ridge(sky, 90, 10, 0.03, farther, 36)
        mid.rect(140, gy - 26, 220, gy, far)   # a workshop
        mid.poly([(136, gy - 26), (224, gy - 26), (200, gy - 38), (160, gy - 38)], far)
        mid.rect(206, gy - 50, 212, gy - 30, far)   # chimney
        for x in (150, 170, 190):
            mid.rect(x, gy - 18, x + 8, gy - 10, (210, 170, 90))
        mid.glow(180, gy - 14, 26, (220, 150, 60), 0.35)
        for x in (30, 90):   # turrets
            mid.rect(x, gy - 10, x + 12, gy, shade(far, 1.3))
            mid.rect(x + 2, gy - 18, x + 10, gy - 10, shade(far, 1.4))
            mid.line(x + 10, gy - 14, x + 24, gy - 19, shade(acc, 0.4), 2)
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
        moon(sky, 180, 24, 9, c=(200, 230, 170), halo=(80, 140, 60))
        ridge(sky, 92, 10, 0.03, farther, 45)
        mid.rect(20, gy - 40, 120, gy, far)   # a lab tower
        mid.poly([(16, gy - 40), (124, gy - 40), (70, gy - 62)], far)
        for x in (36, 64, 92):
            mid.rect(x, gy - 30, x + 8, gy - 20, (130, 220, 100))
            mid.glow(x + 4, gy - 25, 10, (100, 230, 80), 0.35)
        for k, x in enumerate((150, 172, 196)):
            flask(mid, x, gy - 1, (90, 70, 50), shade([(120, 240, 100), (200, 90, 220), (240, 180, 60)][k], 0.7),
                  shade((180, 210, 220), 0.5))
        cauldron(mid, 226, gy, shade(far, 1.4), (110, 230, 90))
        mid.glow(226, gy - 9, 14, (100, 230, 80), 0.5)
        banner(mid, 126, 32, crest, pole, cloth)
        ground(front, fy, near, 46, grass=shade(near, 1.3))
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
        mid.rect(130, gy - 30, 230, gy, far)   # a forge hall
        mid.rect(140, gy - 56, 150, gy - 30, far)
        mid.rect(150, gy - 20, 210, gy, (40, 22, 14))
        mid.glow(180, gy - 10, 34, (240, 120, 40), 0.6)
        gx = 180   # a golem frame on a scaffold
        mid.rect(gx - 8, gy - 26, gx + 8, gy - 8, shade((170, 160, 150), 0.45))
        mid.rect(gx - 4, gy - 32, gx + 4, gy - 26, shade((170, 160, 150), 0.45))
        mid.rect(gx - 13, gy - 24, gx - 9, gy - 10, shade((170, 160, 150), 0.42))
        mid.rect(gx + 9, gy - 24, gx + 13, gy - 10, shade((170, 160, 150), 0.42))
        mid.put(gx - 2, gy - 29, (240, 140, 60))
        mid.put(gx + 2, gy - 29, (240, 140, 60))
        for x in (40, 70):   # anvils
            mid.rect(x, gy - 6, x + 10, gy - 4, shade(far, 1.5))
            mid.rect(x + 3, gy - 4, x + 7, gy, shade(far, 1.5))
        banner(mid, 96, 34, crest, pole, cloth)
        ground(front, fy, near, 52)
    elif sub == "necromancer":
        moon(sky, 70, 26, 12, c=(200, 190, 220), halo=(110, 70, 160))
        ridge(sky, 92, 10, 0.03, farther, 54)
        ridge(mid, gy, 3, 0.05, far, 55)
        for k in range(10):
            grave(mid, 10 + k * 26 + r.randrange(-4, 5), gy, shade(far, 1.5), cross=k % 3 == 0)
        dead_tree(mid, 220, gy, 34, far, r)
        mid.rect(100, gy - 34, 124, gy, far)   # a crypt
        mid.poly([(96, gy - 34), (128, gy - 34), (112, gy - 46)], far)
        mid.rect(108, gy - 14, 116, gy, (120, 70, 180))
        mid.glow(112, gy - 8, 18, (150, 90, 220), 0.5)
        banner(mid, 150, 30, crest, pole, cloth)
        ground(front, fy, near, 56)
        for x in range(0, FRONTW, 34):   # bones
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
