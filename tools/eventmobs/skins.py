"""
The event textures of the monsters, made from the vanilla ones: every pixel keeps its
light and shade but takes the event's colours (skin, clothes, trousers, bone, creeper,
spider), then the event paints its details (veins, runes, moss, lightning scars, magma
cracks, frost) and the glow map (the "_e" file Entity Texture Features draws full bright).
"""
import colorsys
import random
from PIL import Image
from geom import rects
from materials import hexc, mix, mul, ramp

# ---------------------------------------------------------------- palettes

def stops(*hexes):
    n = len(hexes) - 1
    return [(k / n, hexc(h)) for k, h in enumerate(hexes)]


EVENTS = {
    'blood_moon': dict(
        skin=stops('2e2626', '6b605c', 'a89c94', 'd6ccc2'), cloth=stops('1a0405', '4e090c', '8a141a', 'b8282c'),
        pants=stops('120a0a', '2c1716', '4c2826', '6b3a36'), bone=stops('4a3b34', '9a8a7c', 'd8cbb6', 'f2e8d6'),
        creeper=stops('1e1a1a', '4a4040', '7d6f6a', 'a69890'), spider=stops('0d0707', '241010', '3d1818', '5a2424'),
        eye=hexc('ff2c2c'), glow=hexc('9c1018'), detail=hexc('5e0a0e')),
    'eclipse': dict(
        skin=stops('07050c', '1a1228', '33244d', '4f3a72'), cloth=stops('060409', '160d24', '2a1945', '402868'),
        pants=stops('050307', '100a18', '1e1430', '2c1e46'), bone=stops('121018', '2c2638', '4c4460', '6e6488'),
        creeper=stops('08060e', '1c1430', '332654', '4a3a78'), spider=stops('050308', '120c1c', '22183a', '342456'),
        eye=hexc('ffcf3a'), glow=hexc('e8a51e'), detail=hexc('c9922a')),
    'fog': dict(
        skin=stops('4a504e', '7c8380', 'aab1ad', 'd8ddd8'), cloth=stops('2a302e', '4e5652', '737c77', '9aa29d'),
        pants=stops('222826', '3c4441', '58615d', '757e79'), bone=stops('6a706c', '9da39f', 'c8cec9', 'eef2ee'),
        creeper=stops('4a5450', '76817b', 'a1aba5', 'c8d0ca'), spider=stops('1e2422', '343c39', '4d5652', '68726d'),
        eye=hexc('e8fbff'), glow=hexc('8fb8c0'), detail=hexc('4d6436')),
    'thunder': dict(
        skin=stops('121820', '2a3442', '4a5668', '76849a'), cloth=stops('161d27', '2e3a4a', '4d5d72', '7c8ea6'),
        pants=stops('0e1218', '1e2530', '323c4a', '4a5666'), bone=stops('2a3038', '56606e', '8c97a6', 'c4ccd8'),
        creeper=stops('141c26', '2c3a4a', '4a5c70', '72869c'), spider=stops('0a0d12', '161c24', '242c38', '36404e'),
        eye=hexc('bff0ff'), glow=hexc('5fc2ff'), detail=hexc('9fe4ff')),
    'nether_rift': dict(
        skin=stops('0e0c0e', '262224', '403a3c', '5e5658'), cloth=stops('080606', '1c1410', '34241c', '4c3426'),
        pants=stops('060505', '161010', '281c1a', '3a2a26'), bone=stops('1c1612', '3e342c', '6a5a4a', '948068'),
        creeper=stops('0c0a0a', '221e1e', '3a3434', '544c4c'), spider=stops('080606', '161010', '261a18', '382622'),
        eye=hexc('ffa23a'), glow=hexc('ff7a14'), detail=hexc('ffcf5a')),
    'frost': dict(
        skin=stops('1e3a56', '3f6a92', '78a8d0', 'c4e4ff'), cloth=stops('16283a', '2c4a66', '4e7494', '7ea2c0'),
        pants=stops('101c2a', '20344a', '36506a', '50708e'), bone=stops('5a7c98', '8fb2cc', 'c4e0f2', 'f4fbff'),
        creeper=stops('284a68', '4a7698', '7eaccc', 'b8dcf2'), spider=stops('0e1a28', '1c3046', '2e4a66', '466888'),
        eye=hexc('bff0ff'), glow=hexc('5fbfff'), detail=hexc('f2fbff')),
}

# Where the eyes sit (from the old glow maps; they stay the same).
EYES = {'humanoid': [(9, 12), (10, 12), (13, 12), (14, 12)], 'skeleton': [(9, 12), (10, 12), (13, 12), (14, 12)],
        'creeper': [(9, 10), (10, 10), (13, 10), (14, 10), (9, 11), (10, 11), (13, 11), (14, 11)]}


def hsv(c):
    return colorsys.rgb_to_hsv(c[0] / 255, c[1] / 255, c[2] / 255)


def lum(c):
    return (0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2]) / 255


# ---------------------------------------------------------------- what each pixel is

def humanoid_region(x, y):
    """Class by place on the 64x64 humanoid layout (adults)."""
    if y < 16:
        return 'skin'
    if y < 32:
        return 'pants' if x < 16 else 'cloth' if x < 40 else 'skin' if x < 56 else 'cloth'
    if y >= 48:
        return 'pants' if x < 32 else 'skin' if x < 48 else 'cloth'
    return 'pants' if x < 16 else 'cloth'


def classify(mob):
    def zombie(x, y, c):
        h, s, v = hsv(c)
        if v < 0.13:
            return 'dark'
        hd = h * 360
        if 60 <= hd < 165 and s > 0.15:
            return 'skin'
        if 165 <= hd < 205:
            return 'cloth'
        if 205 <= hd < 300:
            return 'pants'
        return 'skin'

    def husk(x, y, c):
        h, s, v = hsv(c)
        if v < 0.1:
            return 'dark'
        if mob.endswith('_baby'):
            return 'cloth' if v > 0.6 else 'skin' if v > 0.3 else 'pants'
        return humanoid_region(x, y)

    def drowned(x, y, c):
        h, s, v = hsv(c)
        if v < 0.1:
            return 'dark'
        if mob.endswith('_baby') or 'outer' in mob:
            return 'cloth' if 'outer' in mob else ('skin' if 150 <= h * 360 < 200 else 'cloth')
        return humanoid_region(x, y)

    def bones(x, y, c):
        h, s, v = hsv(c)
        if mob == 'parched' and v < 0.34:
            return 'cloth'
        if 'overlay' in mob:
            return 'cloth'
        if v < 0.09 and mob != 'wither_skeleton':
            return 'dark'
        return 'bone'

    if mob.startswith('zombie'):
        return zombie
    if mob.startswith('husk'):
        return husk
    if mob.startswith('drowned'):
        return drowned
    if mob in ('skeleton', 'stray', 'bogged', 'wither_skeleton', 'parched', 'stray_overlay', 'bogged_overlay'):
        return bones
    if mob == 'creeper':
        return lambda x, y, c: 'dark' if hsv(c)[2] < 0.12 else 'creeper'
    if mob in ('spider', 'cave_spider'):
        return lambda x, y, c: 'spider'
    raise KeyError(mob)


def recolor(img, mob, ev):
    pal = EVENTS[ev]
    cls = classify(mob)
    px = img.load()
    seen = {}
    for y in range(img.height):
        for x in range(img.width):
            c = px[x, y]
            if c[3] == 0:
                continue
            k = cls(x, y, c)
            seen.setdefault(k, []).append(lum(c))
    ranges = {k: (min(v), max(v)) for k, v in seen.items()}
    out = Image.new('RGBA', img.size, (0, 0, 0, 0))
    op = out.load()
    for y in range(img.height):
        for x in range(img.width):
            c = px[x, y]
            if c[3] == 0:
                continue
            k = cls(x, y, c)
            if k == 'dark':
                base = pal['skin' if mob.startswith(('zombie', 'husk', 'drowned')) else
                           'creeper' if mob == 'creeper' else 'bone']
                op[x, y] = mul(base[0][1], 0.5) + (c[3],)
                continue
            lo, hi = ranges[k]
            t = 0.5 if hi - lo < 1e-3 else (lum(c) - lo) / (hi - lo)
            if mob == 'wither_skeleton':
                t *= 0.55   # the wither skeleton stays the darkest
            if mob == 'cave_spider':
                t = 0.15 + t * 0.85
            op[x, y] = ramp(pal[k], 0.08 + t * 0.92) + (c[3],)
    return out


# ---------------------------------------------------------------- details

def faces_of(family):
    """Face rectangles of the model's boxes, with the part they belong to."""
    f = {}
    if family in ('humanoid', 'skeleton', 'creeper'):
        f['head'] = rects(0, 0, 8, 8, 8)
        f['body'] = rects(16, 16, 8, 12, 4)
    if family == 'humanoid':
        f['arm'] = rects(40, 16, 4, 12, 4)
        f['leg'] = rects(0, 16, 4, 12, 4)
    if family == 'skeleton':
        f['arm'] = rects(40, 16, 2, 12, 2)
        f['leg'] = rects(0, 16, 2, 12, 2)
    if family == 'creeper':
        f['leg'] = rects(0, 16, 4, 6, 4)
    if family == 'spider':
        f['head'] = rects(32, 4, 8, 8, 8)
        f['neck'] = rects(0, 0, 6, 6, 6)
        f['body'] = rects(0, 12, 10, 8, 12)
        f['leg'] = rects(18, 0, 16, 2, 2)
    return f


def walk(rng, rect, length, dirs=((0, 1), (0, 1), (1, 0), (-1, 0), (1, 1), (-1, 1))):
    u, v, w, h = rect
    x, y = u + rng.randrange(w), v + rng.randrange(max(1, h // 2))
    pts = []
    for _ in range(length):
        pts.append((x, y))
        dx, dy = rng.choice(dirs)
        x, y = x + dx, y + dy
        if not (u <= x < u + w and v <= y < v + h):
            break
    return pts


class Painter:
    def __init__(self, img, glow, family, ev, seed):
        self.img, self.glow, self.family, self.ev = img, glow, family, ev
        self.px, self.gx = img.load(), glow.load()
        self.pal = EVENTS[ev]
        self.rng = random.Random(seed)
        self.faces = faces_of(family)
        self.protect = set(EYES.get(family, []))
        # the creeper's face and the skull's holes stay as they are
        if family == 'creeper':
            self.protect |= {(x, y) for x in range(8, 16) for y in range(8, 16)}

    def ok(self, x, y):
        return (x, y) not in self.protect and 0 <= x < self.img.width and 0 <= y < self.img.height \
            and self.px[x, y][3] > 0

    def put(self, x, y, col, glow=None, k=1.0):
        if not self.ok(x, y):
            return
        c = self.px[x, y]
        self.px[x, y] = mix(c[:3], col, k) + (c[3],)
        if glow is not None:
            self.gx[x, y] = glow + (255,)

    def lines(self, parts, faces, count, length, col, glow=None, k=1.0, dirs=None):
        for part in parts:
            if part not in self.faces:
                continue
            for face in faces:
                rect = self.faces[part].get(face)
                if not rect or rect[2] <= 0:
                    continue
                for _ in range(count):
                    for x, y in walk(self.rng, rect, length, dirs) if dirs else walk(self.rng, rect, length):
                        self.put(x, y, col, glow, k)

    def spots(self, parts, faces, count, col, glow=None, k=1.0, size=1):
        for part in parts:
            if part not in self.faces:
                continue
            for face in faces:
                rect = self.faces[part].get(face)
                if not rect or rect[2] <= 0:
                    continue
                u, v, w, h = rect
                for _ in range(count):
                    x, y = u + self.rng.randrange(w), v + self.rng.randrange(h)
                    for dx in range(size):
                        for dy in range(size):
                            if u <= x + dx < u + w and v <= y + dy < v + h:
                                self.put(x + dx, y + dy, col, glow, k)

    def edge(self, parts, faces, row, col, glow=None, k=1.0, chance=1.0):
        """Paints one row of a face (0 = top), e.g. frost on the top edge."""
        for part in parts:
            for face in faces:
                rect = self.faces.get(part, {}).get(face)
                if not rect:
                    continue
                u, v, w, h = rect
                for x in range(u, u + w):
                    if self.rng.random() < chance:
                        self.put(x, v + (row if row >= 0 else h + row), col, glow, k)

    def eyes(self):
        for x, y in EYES.get(self.family, []):
            if 0 <= x < self.img.width and self.px[x, y][3] > 0:
                self.px[x, y] = self.pal['eye'] + (255,)
                self.gx[x, y] = self.pal['eye'] + (255,)


SIDES = ('front', 'left', 'right', 'back')


def details(img, family, ev, seed=1, baby=False):
    glow = Image.new('RGBA', img.size, (0, 0, 0, 0))
    p = Painter(img, glow, family, ev, seed)
    pal = EVENTS[ev]
    if not baby and family != 'spider':
        if ev == 'blood_moon':
            p.lines(['head', 'arm', 'body'], SIDES, 1, 7, hexc('7a0a10'), glow=hexc('6a0a10'), k=0.85)
            p.lines(['leg'], ('front', 'back'), 1, 5, hexc('5e0a0e'), k=0.7)
            p.spots(['body', 'leg'], SIDES, 3, hexc('3d0507'), k=0.8, size=2)
            for x, y in EYES.get(family, [])[:4:3]:
                for dy in (1, 2, 3):
                    p.put(x, y + dy, hexc('8a0a10'), glow=hexc('5a0a0e') if dy == 1 else None, k=0.9)
        elif ev == 'eclipse':
            rune = [(0, 0), (2, 0), (1, 1), (0, 2), (2, 2), (1, 3)]
            for part, face in (('body', 'front'), ('body', 'back'), ('head', 'back')):
                rect = p.faces.get(part, {}).get(face)
                if rect:
                    u, v, w, h = rect
                    ox, oy = u + w // 2 - 1, v + (2 if part == 'body' else 2)
                    for dx, dy in rune:
                        p.put(ox + dx, oy + dy, pal['detail'], glow=pal['glow'])
            p.lines(['arm', 'leg'], ('front', 'back'), 1, 4, pal['detail'], glow=hexc('a8761a'),
                    dirs=((0, 1),))
            p.spots(['head', 'body', 'arm', 'leg'], SIDES, 2, hexc('8a6a2a'), k=0.6)
        elif ev == 'fog':
            p.edge(['head'], ('top',), 0, pal['detail'], chance=1.0)
            p.spots(['head', 'body'], ('top', 'front', 'left', 'right'), 3, pal['detail'], k=0.9, size=2)
            p.lines(['body', 'arm', 'leg', 'head'], SIDES, 1, 6, hexc('3a403e'), k=0.5, dirs=((0, 1),))
            rect = p.faces.get('body', {}).get('front')
            if rect:
                u, v, w, h = rect
                for dx, dy in ((3, 3), (4, 3), (3, 4), (4, 4), (4, 5)):
                    p.put(u + dx, v + dy, hexc('d6f6ff'), glow=hexc('6f9aa3'))
        elif ev == 'thunder':
            zig = ((1, 1), (-1, 1), (1, 1), (0, 1))
            p.lines(['body', 'arm', 'head', 'leg'], ('front', 'back', 'left'), 1, 7, pal['detail'], glow=pal['glow'], dirs=zig)
            p.lines(['head', 'body', 'arm', 'leg'], SIDES, 2, 6, hexc('5a6a80'), k=0.45, dirs=((0, 1),))
        elif ev == 'nether_rift':
            p.lines(['head', 'body', 'arm', 'leg'], SIDES, 2, 7, hexc('ff8a1f'), glow=hexc('e8620e'), k=1.0)
            p.lines(['body', 'leg'], ('front',), 1, 5, hexc('ffcf5a'), glow=hexc('ffb030'))
            p.spots(['head', 'body'], SIDES, 3, hexc('050404'), k=0.7, size=2)
        elif ev == 'frost':
            p.edge(['head', 'body', 'arm', 'leg'], ('top',), 0, pal['detail'])
            p.edge(['head', 'body', 'arm'], SIDES, 0, pal['detail'], chance=0.7)
            p.lines(['head', 'body', 'arm', 'leg'], SIDES, 1, 6, hexc('a8deff'), glow=hexc('3f8fcf'), k=0.9)
            p.spots(['body', 'leg', 'arm'], SIDES, 3, hexc('f2fbff'), k=0.8)
    if family == 'spider':
        if ev == 'blood_moon':
            rect = p.faces['body']['top']
            u, v, w, h = rect
            for dx, dy in ((4, 2), (5, 2), (4, 3), (5, 3), (3, 4), (6, 4), (4, 5), (5, 5), (4, 6), (5, 6), (3, 7), (4, 7), (5, 7), (6, 7)):
                p.put(u + dx, v + dy, hexc('b0121a'), glow=hexc('7a0a10'))
        elif ev == 'eclipse':
            p.lines(['body'], ('top', 'back', 'left', 'right'), 3, 6, pal['detail'], glow=pal['glow'])
        elif ev == 'fog':
            p.spots(['body', 'head'], ('top',), 6, pal['detail'], size=2)
            p.spots(['body'], ('left', 'right', 'back'), 5, hexc('d8dcdf'), k=0.8)
        elif ev == 'thunder':
            p.lines(['body', 'head'], ('top', 'left', 'right', 'back'), 2, 7, hexc('7fc8ff'), glow=pal['glow'],
                    dirs=((1, 1), (-1, 1), (1, 1), (0, 1)))
        elif ev == 'nether_rift':
            p.lines(['body', 'head', 'leg'], ('top', 'left', 'right', 'back', 'front'), 2, 8, hexc('ff8a1f'), glow=hexc('e8620e'))
        elif ev == 'frost':
            p.edge(['body', 'head'], ('top',), 0, pal['detail'])
            p.spots(['body', 'head', 'leg'], ('top', 'left', 'right'), 6, hexc('f2fbff'))
            p.lines(['body'], ('top', 'back'), 2, 6, hexc('a8deff'), glow=hexc('3f8fcf'))
    p.eyes()
    return img, glow


def spider_eyes(van, ev):
    """The spider's glowing eyes in the event's colour."""
    pal = EVENTS[ev]
    out = Image.new('RGBA', van.size, (0, 0, 0, 0))
    vp, op = van.load(), out.load()
    for y in range(van.height):
        for x in range(van.width):
            c = vp[x, y]
            if c[3] == 0:
                continue
            t = lum(c)
            op[x, y] = mix(mul(pal['eye'], 0.55), pal['eye'], min(1.0, t * 1.6)) + (c[3],)
    return out
