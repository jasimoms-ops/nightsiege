"""
Skill tree backgrounds per subclass (design update, Simon 28/29 October 2026): a dark night scene
480 x 270 like gui/skills/<class>.png, tinted in the subclass's colour, with its own motif.

    python3 tools/ui/tree_bg.py            -> src/main/resources/assets/nightsiege/textures/gui/skills/sub/<sub>.png
"""
import math
import os
import random

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "src/main/resources/assets/nightsiege/textures/gui/skills/sub")
W, H = 480, 270

# subclass -> (sky tint, accent) ; the class colours from the crests, each subclass a little apart
COL = {
    "blademaster": ((60, 18, 22), (210, 210, 220)), "berserker": ((70, 10, 12), (200, 40, 34)),
    "axe": ((50, 24, 16), (170, 110, 60)), "paladin": ((56, 30, 14), (236, 196, 96)),
    "swift": ((16, 40, 30), (200, 230, 210)), "heavy": ((22, 36, 18), (140, 100, 56)),
    "bolter": ((20, 34, 30), (170, 150, 110)), "trapper": ((14, 32, 18), (110, 150, 70)),
    "shieldfighter": ((16, 26, 56), (190, 200, 220)), "bulwark": ((22, 26, 40), (150, 150, 160)),
    "medic": ((14, 34, 40), (120, 210, 120)), "warden": ((14, 24, 52), (90, 230, 220)),
    "gunner": ((40, 26, 12), (110, 170, 230)), "electrician": ((36, 26, 12), (250, 220, 80)),
    "demolition": ((44, 20, 10), (250, 140, 40)), "alchemist": ((30, 34, 14), (100, 230, 90)),
    "tamer": ((14, 34, 32), (180, 180, 190)), "golemsmith": ((24, 30, 30), (210, 120, 60)),
    "necromancer": ((26, 14, 40), (170, 110, 230)), "soulcatcher": ((10, 34, 38), (90, 240, 230)),
}


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def base(sub, seed):
    sky, acc = COL[sub]
    r = random.Random(seed)
    im = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(im)
    top = (6, 5, 10)
    for y in range(H):
        t = y / H
        d.line([(0, y), (W, y)], fill=mix(top, sky, min(1, t * 1.4)))
    for _ in range(180):
        x, y = r.randrange(W), r.randrange(int(H * 0.6))
        c = r.choice([(120, 120, 140), (200, 200, 220), (90, 90, 110)])
        d.point((x, y), fill=c)
    # moon with a soft halo
    mx, my = r.randrange(60, 420), r.randrange(30, 60)
    for rad, c in ((30, mix(sky, (40, 36, 50), 0.5)), (22, mix(sky, (60, 56, 70), 0.5)), (12, (216, 210, 200))):
        d.ellipse([mx - rad, my - rad, mx + rad, my + rad], fill=c)
    d.ellipse([mx - 3, my - 4, mx + 1, my], fill=(190, 184, 176))
    # far hills
    pts = [(0, H)]
    for x in range(0, W + 20, 20):
        pts.append((x, int(H * 0.68 + 14 * math.sin(x / 47.0 + seed) + 8 * math.sin(x / 13.0))))
    pts.append((W, H))
    d.polygon(pts, fill=mix(sky, (8, 6, 8), 0.55))
    return im, d, r, sky, acc


def ground(d, sky, y0=226):
    d.rectangle([0, y0, W, H], fill=mix(sky, (4, 3, 4), 0.75))


def dim(acc, t=0.55):
    return mix(acc, (10, 8, 10), t)


def sword(d, x, y, length, angle, blade, hilt):
    a = math.radians(angle)
    ex, ey = x + math.cos(a) * length, y - math.sin(a) * length
    d.line([(x, y), (ex, ey)], fill=blade, width=5)
    gx, gy = x + math.cos(a) * 8, y - math.sin(a) * 8
    px, py = math.cos(a + math.pi / 2) * 10, -math.sin(a + math.pi / 2) * 10
    d.line([(gx - px, gy - py), (gx + px, gy + py)], fill=hilt, width=4)
    d.line([(x, y), (x - math.cos(a) * 10, y + math.sin(a) * 10)], fill=hilt, width=4)


def motif(sub, im, d, r, sky, acc):
    a1, a2 = dim(acc, 0.45), dim(acc, 0.7)
    if sub == "blademaster":
        sword(d, 170, 210, 140, 55, a1, a2)
        sword(d, 310, 210, 140, 125, a1, a2)
        ground(d, sky)
    elif sub == "berserker":
        for _ in range(40):
            x, y, s = r.randrange(W), r.randrange(40, 220), r.randrange(2, 7)
            d.ellipse([x, y, x + s, y + s * 1.4], fill=mix(acc, (30, 0, 0), 0.4))
        for i, x in enumerate(range(80, 420, 70)):
            d.line([(x, 150), (x + 10, 120), (x + 20, 150)], fill=a1, width=3)
            d.line([(x + 5, 136), (x + 15, 136)], fill=a1, width=2)
        ground(d, sky)
    elif sub == "axe":
        ground(d, sky, 220)
        for x in (90, 150, 360, 410):   # stumps and logs
            d.rectangle([x, 206, x + 26, 224], fill=a2)
            d.ellipse([x, 200, x + 26, 212], fill=a1)
        d.polygon([(220, 224), (260, 160), (300, 224)], fill=a2)   # a tent
        d.polygon([(236, 226), (250, 214), (264, 226)], fill=(250, 150, 40))
        d.line([(320, 170), (330, 224)], fill=a1, width=4)
        d.polygon([(312, 168), (334, 158), (334, 184)], fill=dim((200, 200, 210), 0.5))
    elif sub == "paladin":
        cx = 240
        d.rectangle([cx - 50, 70, cx + 50, 230], fill=dim(acc, 0.82))
        d.ellipse([cx - 50, 30, cx + 50, 130], fill=dim(acc, 0.82))
        for i, c in enumerate([(200, 60, 40), (60, 90, 190), acc, (60, 150, 80)]):
            x0 = cx - 40 + (i % 2) * 40
            y0 = 80 + (i // 2) * 60
            d.rectangle([x0 + 2, y0, x0 + 38, y0 + 56], fill=dim(c, 0.55))
        d.line([(cx, 70), (cx, 196)], fill=dim(acc, 0.3), width=4)
        d.line([(cx - 40, 140), (cx + 40, 140)], fill=dim(acc, 0.3), width=4)
        ground(d, sky)
    elif sub == "swift":
        for i in range(9):
            y = 40 + i * 20
            d.arc([-100 + i * 30, y, 560 - i * 20, y + 90], 200, 330, fill=dim(acc, 0.65), width=2)
        for _ in range(14):
            x, y = r.randrange(40, 440), r.randrange(30, 200)
            d.line([(x, y), (x + 18, y - 8)], fill=a1, width=2)
            d.line([(x + 4, y - 1), (x + 8, y + 4)], fill=a1, width=1)
        ground(d, sky)
    elif sub == "heavy":
        d.rectangle([200, 60, 260, 224], fill=a2)   # a big oak
        d.ellipse([110, 10, 350, 150], fill=mix(sky, (20, 40, 16), 0.5))
        d.ellipse([140, 30, 320, 130], fill=mix(sky, (30, 56, 24), 0.5))
        d.arc([300, 60, 420, 220], 270, 90, fill=a1, width=4)   # a longbow leaning
        d.line([(360, 60), (360, 220)], fill=dim((220, 220, 220), 0.6), width=1)
        ground(d, sky)
    elif sub == "bolter":
        for cx, cy, rad in ((120, 150, 50), (210, 110, 34), (360, 160, 60)):
            for k in range(12):
                a = k * math.pi / 6
                d.line([(cx, cy), (cx + math.cos(a) * (rad + 8), cy + math.sin(a) * (rad + 8))], fill=a2, width=6)
            d.ellipse([cx - rad, cy - rad, cx + rad, cy + rad], fill=a2)
            d.ellipse([cx - rad // 3, cy - rad // 3, cx + rad // 3, cy + rad // 3], fill=mix(sky, (6, 6, 8), 0.4))
        ground(d, sky)
    elif sub == "trapper":
        ground(d, sky, 214)
        for _ in range(60):
            x = r.randrange(W)
            d.line([(x, 214), (x + r.randrange(-3, 4), 214 - r.randrange(4, 14))], fill=dim((80, 140, 60), 0.5), width=1)
        for cx in (130, 340):
            d.ellipse([cx - 40, 190, cx + 40, 214], outline=a1, width=2)
            for k in range(-36, 40, 8):
                d.line([(cx + k, 214), (cx + k, 196)], fill=a2, width=1)
        d.line([(240, 214), (250, 120)], fill=a2, width=2)
        d.arc([220, 110, 280, 170], 180, 360, fill=a1, width=2)
    elif sub == "shieldfighter":
        for i, x in enumerate(range(30, 470, 54)):
            y = 150 + (i % 2) * 6
            d.ellipse([x, y, x + 50, y + 64], fill=dim(acc, 0.7))
            d.ellipse([x + 18, y + 24, x + 32, y + 40], fill=dim((200, 170, 90), 0.5))
        ground(d, sky, 228)
    elif sub == "bulwark":
        for row in range(6):
            y = 120 + row * 18
            off = (row % 2) * 20
            for x in range(-20 + off, W, 40):
                d.rectangle([x + 1, y + 1, x + 38, y + 16], fill=dim(acc, 0.72 - (row % 3) * 0.04))
        for x in range(0, W, 40):
            d.rectangle([x, 96, x + 22, 120], fill=dim(acc, 0.7))
    elif sub == "medic":
        ground(d, sky, 214)
        for _ in range(40):
            x = r.randrange(20, 460)
            h = r.randrange(12, 40)
            d.line([(x, 214), (x, 214 - h)], fill=dim((80, 160, 70), 0.45), width=2)
            d.ellipse([x - 4, 210 - h, x + 4, 216 - h], fill=dim(r.choice([(200, 80, 80), (230, 220, 120), (230, 230, 230)]), 0.45))
        d.rectangle([228, 60, 252, 140], fill=dim((220, 60, 50), 0.5))
        d.rectangle([200, 88, 280, 112], fill=dim((220, 60, 50), 0.5))
    elif sub == "warden":
        cx, cy = 240, 130
        for rad in (100, 80, 56):
            d.ellipse([cx - rad, cy - rad, cx + rad, cy + rad], outline=dim(acc, 0.5), width=2)
        for k in range(8):
            a = k * math.pi / 4
            x, y = cx + math.cos(a) * 90, cy + math.sin(a) * 90
            d.line([(x - 4, y - 6), (x + 4, y + 6)], fill=dim(acc, 0.35), width=2)
            d.line([(x + 4, y - 6), (x - 2, y)], fill=dim(acc, 0.35), width=2)
        d.polygon([(cx, cy - 40), (cx + 35, cy + 22), (cx - 35, cy + 22)], outline=dim(acc, 0.4))
        ground(d, sky)
    elif sub == "gunner":
        for x in range(0, W, 24):
            d.line([(x, 0), (x, H)], fill=mix(sky, (40, 60, 90), 0.4))
        for y in range(0, H, 24):
            d.line([(0, y), (W, y)], fill=mix(sky, (40, 60, 90), 0.4))
        d.rectangle([180, 120, 300, 220], outline=a1, width=2)
        d.rectangle([200, 90, 280, 120], outline=a1, width=2)
        d.line([(240, 90), (330, 60)], fill=a1, width=3)
        d.line([(160, 230), (320, 230)], fill=a1, width=1)
    elif sub == "electrician":
        for cx in (110, 370):
            for k in range(9):
                d.ellipse([cx - 24, 110 + k * 10, cx + 24, 122 + k * 10], outline=dim((200, 120, 60), 0.4), width=3)
            d.ellipse([cx - 16, 86, cx + 16, 112], fill=dim((200, 120, 60), 0.5))
        pts = [(126, 90), (180, 70), (210, 110), (260, 60), (300, 104), (356, 88)]
        d.line(pts, fill=dim(acc, 0.2), width=3)
        ground(d, sky)
    elif sub == "demolition":
        for _ in range(20):
            x, y, rad = r.randrange(W), r.randrange(40, 180), r.randrange(14, 40)
            d.ellipse([x - rad, y - rad, x + rad, y + rad], fill=mix(sky, (70, 64, 60), 0.35))
        for x in (110, 160, 320, 370):
            d.rectangle([x, 176, x + 40, 224], fill=dim((140, 90, 50), 0.6))
            d.line([(x, 188), (x + 40, 188)], fill=dim((60, 60, 60), 0.4), width=2)
            d.line([(x, 212), (x + 40, 212)], fill=dim((60, 60, 60), 0.4), width=2)
        ground(d, sky)
    elif sub == "alchemist":
        for cx, h in ((130, 70), (240, 100), (360, 60)):
            d.ellipse([cx - 40, 220 - 70, cx + 40, 230], outline=dim((200, 220, 230), 0.45), width=2)
            d.rectangle([cx - 8, 220 - 70 - h // 2, cx + 8, 222 - 70], outline=dim((200, 220, 230), 0.45), width=2)
            d.chord([cx - 38, 175, cx + 38, 228], 0, 180, fill=dim(acc, 0.55))
            for k in range(5):
                d.ellipse([cx - 10 + k * 5, 120 - k * 18, cx + 6 + k * 5, 136 - k * 18], fill=mix(sky, acc, 0.25))
        ground(d, sky, 230)
    elif sub == "tamer":
        ground(d, sky, 200)
        for i in range(10):
            x, y = 40 + i * 44, 210 + (i % 2) * 22
            d.ellipse([x, y, x + 12, y + 10], fill=a2)
            for k in range(4):
                d.ellipse([x - 4 + k * 6, y - 8, x + 1 + k * 6, y - 3], fill=a2)
        rock = mix(sky, (16, 16, 18), 0.6)
        d.polygon([(250, 200), (290, 150), (360, 140), (420, 200)], fill=rock)
        wolf = mix(sky, (6, 6, 8), 0.75)
        # a sitting wolf howling at the moon
        d.polygon([(318, 146), (322, 112), (330, 96), (338, 82), (346, 70), (352, 74), (348, 86), (356, 92),
                   (350, 98), (342, 100), (344, 120), (356, 146)], fill=wolf)
        d.polygon([(340, 84), (344, 72), (348, 80)], fill=wolf)
        d.polygon([(356, 146), (372, 140), (366, 148)], fill=wolf)
    elif sub == "golemsmith":
        d.rectangle([150, 180, 330, 196], fill=a2)
        d.polygon([(130, 160), (350, 160), (330, 180), (150, 180)], fill=a1)
        d.rectangle([210, 196, 270, 230], fill=a2)
        for _ in range(30):
            x, y = 240 + r.randrange(-60, 60), 150 - r.randrange(0, 70)
            d.point((x, y), fill=(250, 180, 80))
        ground(d, sky, 230)
    elif sub == "necromancer":
        ground(d, sky, 214)
        for x in (70, 150, 250, 340, 420):
            h = r.randrange(30, 50)
            d.rectangle([x, 214 - h, x + 26, 214], fill=mix(sky, (90, 86, 100), 0.45))
            d.ellipse([x, 204 - h, x + 26, 224 - h], fill=mix(sky, (90, 86, 100), 0.45))
        for _ in range(12):
            y = r.randrange(190, 230)
            x = r.randrange(-40, W)
            d.ellipse([x, y, x + r.randrange(60, 140), y + 14], fill=mix(sky, acc, 0.3))
    elif sub == "soulcatcher":
        for _ in range(9):
            x, y = r.randrange(40, 440), r.randrange(50, 190)
            d.ellipse([x, y, x + 14, y + 16], fill=mix(sky, acc, 0.45))
            d.polygon([(x, y + 10), (x + 14, y + 10), (x + 7, y + 30)], fill=mix(sky, acc, 0.3))
        for cx in (130, 350):
            d.rectangle([cx - 10, 170, cx + 10, 200], outline=dim((200, 170, 90), 0.4), width=2)
            d.ellipse([cx - 5, 178, cx + 5, 194], fill=dim(acc, 0.2))
        ground(d, sky)
    # vignette
    px = im.load()
    for y in range(H):
        for x in range(W):
            dx, dy = (x - W / 2) / (W / 2), (y - H / 2) / (H / 2)
            f = max(0.35, 1 - 0.45 * (dx * dx + dy * dy))
            c = px[x, y]
            px[x, y] = (int(c[0] * f), int(c[1] * f), int(c[2] * f))


def main():
    os.makedirs(OUT, exist_ok=True)
    sheet = Image.new("RGB", (4 * 240, 5 * 135))
    for i, sub in enumerate(COL):
        im, d, r, sky, acc = base(sub, i + 3)
        motif(sub, im, d, r, sky, acc)
        im.save(os.path.join(OUT, sub + ".png"))
        sheet.paste(im.resize((240, 135)), ((i % 4) * 240, (i // 4) * 135))
    os.makedirs(os.path.join(ROOT, "build"), exist_ok=True)
    sheet.save(os.path.join(ROOT, "build", "tree_backgrounds.png"))
    print("tree backgrounds:", len(COL))


if __name__ == "__main__":
    main()
