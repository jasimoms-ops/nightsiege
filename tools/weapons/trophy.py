"""
The nemesis trophy (update 2 "Fluch & Rache", Simon 28 October 2026: "Rache: Trophäe"): a dark oak
plaque with a gold rim, crossed bones and a skull with red eyes, a blood-red ribbon below. 32x32,
finished like the kit weapons (kit_v3.finish: bevel, grain, tinted outline). Monsters with a head
of their own (zombie, skeleton, wither skeleton, piglin) give that head instead.

python3 tools/weapons/trophy.py [--install]
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import kit_v3 as K  # noqa: E402

N = 32
WOOD = (96, 62, 36, 255)
WOOD_DARK = (70, 44, 26, 255)
GOLD = (196, 152, 52, 255)
BONE = (226, 218, 196, 255)
BONE_DARK = (170, 158, 128, 255)
SKULL_RIM = (58, 40, 30, 255)
SOCKET = (34, 22, 22, 255)
EYE = (196, 22, 20, 255)
RIBBON = (150, 30, 36, 255)
RIBBON_DARK = (112, 20, 26, 255)


def inside_plaque(x, y):
    """A heraldic shield: flat top with rounded corners, straight sides, a point at the bottom."""
    cx = 15.5
    if y < 2 or y > 29:
        return False
    if y <= 19:
        half = 11.5
        # rounded top corners
        if y < 4:
            half = 11.5 - (4 - y) * 0.9
        return abs(x - cx) <= half
    # the lower part narrows to a point
    t = (y - 19) / 10.0
    half = 11.5 * math.sqrt(max(0.0, 1.0 - t * t))
    return abs(x - cx) <= half


def draw():
    px = {}
    for x in range(N):
        for y in range(N):
            if inside_plaque(x + 0.5, y + 0.5):
                edge = not all(inside_plaque(x + 0.5 + dx, y + 0.5 + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                if edge:
                    px[(x, y)] = GOLD
                else:
                    # planks: a darker seam every few rows
                    px[(x, y)] = WOOD_DARK if y in (9, 16, 23) else WOOD
    # Crossed bones behind the skull.
    for (ax, ay, bx, by) in ((8.0, 7.0, 23.5, 24.5), (23.5, 7.0, 8.0, 24.5)):
        for i in range(200):
            t = i / 199
            x = ax + (bx - ax) * t
            y = ay + (by - ay) * t
            for ox in (0.0, 0.7):
                px[(int(x + ox), int(y))] = BONE_DARK
        for (kx, ky) in ((ax, ay), (bx, by)):
            for dx in range(-2, 3):
                for dy in range(-2, 3):
                    if dx * dx + dy * dy <= 3:
                        px[(int(kx + dx * 0.8), int(ky + dy * 0.8))] = BONE_DARK
    # The skull: a round cranium and a jaw with teeth, set off by a dark rim.
    skull = set()
    for x in range(N):
        for y in range(N):
            d = math.hypot(x + 0.5 - 15.8, y + 0.5 - 12.8)
            if d <= 5.9:
                skull.add((x, y))
    for x in range(12, 20):
        for y in range(17, 21):
            skull.add((x, y))
    for (x, y) in skull:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y + dy)
            if q not in skull and q in px:
                px[q] = SKULL_RIM
    for q in skull:
        px[q] = BONE
    for x in (13, 15, 17):
        px[(x, 20)] = SOCKET   # gaps between the teeth
    # eye sockets with a red glow, the nose
    for (ex, ey) in ((12, 12), (17, 12)):
        for dx in range(3):
            for dy in range(3):
                px[(ex + dx, ey + dy)] = SOCKET
        px[(ex + 1, ey + 1)] = EYE
    px[(15, 16)] = SOCKET
    px[(16, 16)] = SOCKET
    px[(15, 15)] = SOCKET
    # The ribbon across the lower part, with its tails.
    for x in range(7, 25):
        for y in range(23, 26):
            px[(x, y)] = RIBBON_DARK if y == 25 else RIBBON
    for (x, y) in ((6, 24), (6, 25), (5, 26), (6, 26), (25, 24), (25, 25), (25, 26), (26, 26)):
        px[(x, y)] = RIBBON_DARK
    return px


def picture():
    return K.finish(draw(), 31)


def save(px, path, scale=1):
    from PIL import Image
    im = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    for (x, y), c in px.items():
        if 0 <= x < N and 0 <= y < N:
            im.putpixel((x, y), tuple(int(v) for v in c))
    if scale != 1:
        im = im.resize((N * scale, N * scale), Image.NEAREST)
    im.save(path)
    return im


if __name__ == '__main__':
    out = os.path.join(os.path.dirname(__file__), 'out32')
    os.makedirs(out, exist_ok=True)
    px = picture()
    save(px, os.path.join(out, 'nemesis_trophy.png'))
    save(px, os.path.join(out, 'nemesis_trophy_big.png'), 12)
    if '--install' in sys.argv:
        root = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'nightsiege')
        tex = os.path.join(root, 'textures', 'item', 'trophy')
        os.makedirs(tex, exist_ok=True)
        save(px, os.path.join(tex, 'nemesis.png'))
        models = os.path.join(root, 'models', 'item', 'trophy')
        items = os.path.join(root, 'items', 'trophy')
        os.makedirs(models, exist_ok=True)
        os.makedirs(items, exist_ok=True)
        with open(os.path.join(models, 'nemesis.json'), 'w') as f:
            f.write('{\n  "parent": "minecraft:item/generated",\n  "textures": {\n    "layer0": "nightsiege:item/trophy/nemesis"\n  }\n}\n')
        with open(os.path.join(items, 'nemesis.json'), 'w') as f:
            f.write('{\n  "model": {\n    "type": "minecraft:model",\n    "model": "nightsiege:item/trophy/nemesis"\n  }\n}\n')
        print('installed')
