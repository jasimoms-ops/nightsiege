"""
Kit weapons per subclass (Simon, 27 October 2026: "for every subclass a kit weapon, like the
armour"). Each design is a 16x16 item texture in Minecraft's style: the handle bottom left,
the tip top right, lit from the top left. Drawn in "diagonal space": a = x - y runs along the
weapon (handle -13 .. tip +13), d = x + y - 15 across it (0 is the middle line).

python3 tools/weapons/designs.py  ->  tools/weapons/out/<name>.png and the design sheet.
"""
import os

W = H = 16
OUT = os.path.join(os.path.dirname(__file__), 'out')


def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def ramp(*hexes):
    """Outline, dark, mid, light, highlight (darkest first)."""
    return [hexc(h) for h in hexes]


class Sprite:
    def __init__(self):
        self.px = {}

    def put(self, x, y, c):
        if 0 <= x < W and 0 <= y < H and c is not None:
            self.px[(x, y)] = c

    def get(self, x, y):
        return self.px.get((x, y))

    def clear(self, x, y):
        self.px.pop((x, y), None)

    # ------------------------------------------------------------ diagonal tools
    def band(self, a0, a1, width, colors, off=0, taper=0, tipcolor=None):
        """A straight band along the weapon: |d - off| <= width between a0 and a1.
        colors: list across the band from the lit side (d = -width) to the shadow side.
        taper: over the last `taper` steps of a the band narrows to a point."""
        for x in range(W):
            for y in range(H):
                a = x - y
                d = x + y - 15 - off
                if a < a0 or a > a1:
                    continue
                w = width
                if taper and a > a1 - taper:
                    w = max(0, round(width * (a1 - a) / taper))
                if abs(d) > w:
                    continue
                i = d + w
                span = 2 * w
                if span == 0:
                    c = tipcolor or colors[len(colors) // 2]
                else:
                    c = colors[min(len(colors) - 1, round(i * (len(colors) - 1) / span))]
                self.put(x, y, c)

    def across(self, a, half, colors, thick=1):
        """A bar across the weapon (a guard) at along-position a, `half` diagonals to each side."""
        for x in range(W):
            for y in range(H):
                aa = x - y
                d = x + y - 15
                if a <= aa < a + thick * 2 and abs(d) <= half:
                    t = (d + half) / max(1, 2 * half)
                    c = colors[min(len(colors) - 1, int(t * (len(colors) - 1) + 0.5))]
                    self.put(x, y, c)

    def line(self, pts, c):
        for (x, y) in pts:
            self.put(x, y, c)

    def outline(self, c, only_empty=True):
        """A dark rim around everything drawn so far (4-neighbours)."""
        rim = []
        for (x, y) in list(self.px):
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                p = (x + dx, y + dy)
                if 0 <= p[0] < W and 0 <= p[1] < H and p not in self.px:
                    rim.append(p)
        for p in rim:
            self.px[p] = c

    def art(self, rows, palette, ox=0, oy=0):
        """Hand-drawn pixels: rows of characters, '.' empty."""
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch != '.' and ch != ' ':
                    self.put(ox + i, oy + j, palette[ch])

    def field(self, fn, over=True):
        """Continuous shapes: fn(a, d, x, y) -> colour or None for every pixel centre, with
        a = x - y along the weapon and d = x + y - 14 across it (0 is the exact middle, so a
        shape that is symmetric in d comes out pixel-symmetric)."""
        for x in range(W):
            for y in range(H):
                c = fn(x - y, x + y - 14, x, y)
                if c is not None and (over or (x, y) not in self.px):
                    self.put(x, y, c)

    def segment(self, p, q, c, width=0.55, over=True):
        """A straight line from p to q (pixel coordinates), as thick as `width` around its centre."""
        import math
        (x1, y1), (x2, y2) = p, q
        L = max(1e-6, math.hypot(x2 - x1, y2 - y1))
        for x in range(W):
            for y in range(H):
                t = max(0.0, min(1.0, ((x - x1) * (x2 - x1) + (y - y1) * (y2 - y1)) / (L * L)))
                if math.hypot(x - (x1 + t * (x2 - x1)), y - (y1 + t * (y2 - y1))) <= width:
                    if over or (x, y) not in self.px:
                        self.put(x, y, c)

    def image(self, scale=1):
        from PIL import Image
        im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        for (x, y), c in self.px.items():
            im.putpixel((x, y), c)
        if scale != 1:
            im = im.resize((W * scale, H * scale), Image.NEAREST)
        return im


DESIGNS = []


def design(key, clazz, sub, name, line):
    def reg(fn):
        DESIGNS.append((key, clazz, sub, name, line, fn))
        return fn
    return reg


# shared ramps
STEEL = ramp('1c1d24', '4a4f5c', '8a909e', 'c9ced8', 'f4f6fa')
DARKSTEEL = ramp('121216', '2b2c33', '4b4d57', '7a7d89', 'a9acb8')
GOLD = ramp('3a2606', '8a5c12', 'd49a24', 'f2c94c', 'fff1a8')
WOOD = ramp('24160b', '4a2e16', '6e4524', '93613a', 'b9845a')
LEATHER = ramp('1a0f08', '3b2414', '5c3a20', '7d5230', '9c6c42')
BONE = ramp('3b3326', '7d725c', 'b7ab8e', 'ddd3b8', 'f6f0de')
COPPER = ramp('2e1407', '6b3418', 'b0612f', 'd98a4e', 'f2b984')
IRON = ramp('1b1b1f', '3e4048', '6a6e79', '9ca1ac', 'd0d4dc')


# ---------------------------------------------------------------------- warrior
@design('blademaster_saber', 'Krieger', 'Klingenmeister', 'Duellsäbel',
        'Schlanke Silberklinge, Korbgefäß in Gold und Marineblau, rote Quaste – wie die Uniform.')
def blademaster(s):
    s.band(-5, 13, 1, [STEEL[1], STEEL[3], STEEL[4]], taper=3)
    s.band(-4, 12, 0, [STEEL[2]], off=1)            # fuller on the shadow side
    s.across(-7, 3, [GOLD[2], GOLD[3], GOLD[4], GOLD[3], GOLD[1]])
    s.band(-12, -8, 0, [hexc('1f3a7a')])            # navy grip
    s.band(-11, -9, 0, [hexc('2f57b0')], off=-1)
    s.put(2, 12, GOLD[3]); s.put(1, 13, GOLD[2]); s.put(1, 14, GOLD[1])   # pommel
    s.put(3, 9, hexc('2f57b0')); s.put(4, 8, GOLD[4])                    # knuckle bow
    s.put(0, 15, hexc('c0262d')); s.put(1, 15, hexc('8c1a1f')); s.put(0, 14, hexc('e0393f'))  # tassel
    s.outline(STEEL[0])


@design('berserker_bloodblade', 'Krieger', 'Berserker', 'Blutklinge',
        'Breite, gezackte Klinge aus dunklem Stahl mit Blutrinne, Knochenparier mit Hörnern.')
def berserker(s):
    def blade(a, d, x, y):
        if a < -6 or a > 12:
            return None
        w = 2.6 if a < 7 else 2.6 * (12.4 - a) / 5.4
        if abs(d) > w:
            return None
        if d < -1.5 and a % 4 == 3:                  # teeth along the lit edge
            return None
        if d == 0 and -4 <= a <= 9:
            return hexc('8e1b1b')                    # blood groove
        return DARKSTEEL[4] if d <= -2 else DARKSTEEL[3] if d < 0 else DARKSTEEL[2] if d < 2 else DARKSTEEL[1]
    s.field(blade)

    def guard(a, d, x, y):
        if abs(d) > 5:
            return None
        if abs(a - (-8 + 0.17 * d * d)) <= 0.9:
            return BONE[4] if d < -1 else BONE[3] if d < 2 else BONE[2]
    s.field(guard)

    def grip(a, d, x, y):
        if -12 <= a <= -9 and abs(d) <= 1:
            return LEATHER[3] if a % 2 else LEATHER[1]
        if a == -13 and abs(d) <= 1:
            return BONE[3]
    s.field(grip)
    s.put(12, 1, hexc('b52424')); s.put(11, 2, hexc('7d1616'))   # blood near the tip
    s.outline(DARKSTEEL[0])


@design('axe_bearded', 'Krieger', 'Axtkämpfer', 'Bartaxt',
        'Schwere Bartaxt: breiter Eisenkopf mit langem Bart, lederumwickelter Eschenstiel, Kupfernieten.')
def axe(s):
    c = 4.0                                            # where the head sits along the haft

    def haft(a, d, x, y):
        if -13 <= a <= 7 and abs(d) <= 1:
            if a in (-11, -9, -7):
                return LEATHER[3]
            return WOOD[3] if d < 0 else WOOD[2] if d == 0 else WOOD[1]
    s.field(haft)

    def head(a, d, x, y):
        m = -d
        if m < 1 or m > 7.6:
            return None
        t = (m - 1) / 6.6
        u = a - c
        lo = -(1.2 + 1.2 * t) - 6.0 * t ** 2.2         # the beard reaches down along the haft
        hi = 1.4 + 1.6 * t
        if not (lo <= u <= hi):
            return None
        if m > 7.6 - 0.06 * (u - (lo + hi) / 2) ** 2:
            return None
        if m >= 6.0:
            return IRON[4] if m >= 6.5 else IRON[3]   # the honed edge
        if u <= lo + 1.0 and t > 0.4:
            return IRON[1]
        return IRON[3] if u > hi - 1.2 else IRON[2]
    s.field(head)
    s.field(lambda a, d, x, y: IRON[1] if c - 1 <= a <= c + 1 and 2 <= d <= 3 else None)   # the poll behind
    s.field(lambda a, d, x, y: COPPER[4] if (a, d) in ((c + 1, -2), (c - 1, -2)) else None)
    s.outline(IRON[0])


@design('paladin_sunaxe', 'Krieger', 'Paladin', 'Sonnenaxt',
        'Doppelaxt aus Weißstahl mit goldener Sonnenscheibe in der Mitte, weißer Stiel mit Goldringen.')
def paladin(s):
    c = 4.0

    def haft(a, d, x, y):
        if -13 <= a <= 9 and abs(d) <= 1:
            if a in (-11, -7, -3):
                return GOLD[3] if d <= 0 else GOLD[2]
            return hexc('f2efe6') if d < 0 else hexc('cfcabd') if d == 0 else hexc('a39d8f')
    s.field(haft)

    def heads(a, d, x, y):
        m = abs(d)
        if m < 1 or m > 7.6:
            return None
        t = (m - 1) / 6.6
        u = a - c
        w = 1.3 + 3.2 * t ** 1.4
        if abs(u) > w or m > 7.6 - 0.1 * u * u:
            return None
        if m >= 6.2:
            return hexc('ffffff') if d < 0 else STEEL[3]
        return STEEL[3] if d < 0 else STEEL[2]
    s.field(heads)

    def sun(a, d, x, y):
        r = abs(a - c) + abs(d)
        if r <= 2:
            return GOLD[4] if r == 0 else GOLD[3]
        if r == 3 and (a - c == 0 or d == 0 or abs(a - c) == abs(d)):
            return GOLD[2]
    s.field(sun)
    s.field(lambda a, d, x, y: GOLD[3] if 10 <= a <= 11 and abs(d) <= 1 else None)   # gold tip
    s.outline(hexc('2a2418'))


# ---------------------------------------------------------------------- archer
def bow(s, k, wmid, wood, grip, string, nock, recurve=False):
    """A bow as Minecraft draws it: the string runs from the top left to the bottom right, the
    limbs bow out to the top right, the grip sits in the middle of the arc. wood = dark, mid,
    light. k sets how strongly the bow curves, wmid how thick the limbs are at the grip."""
    import math
    t1, t2 = (2, 1), (13, 12)                          # tips, mirror images of each other
    cx, cy = 7.5 - k, 6.5 + k
    R = math.dist((cx, cy), t1)
    a1 = math.atan2(t1[1] - cy, t1[0] - cx)
    a2 = math.atan2(t2[1] - cy, t2[0] - cx)
    for x in range(W):
        for y in range(H):
            r = math.dist((cx, cy), (x, y))
            th = math.atan2(y - cy, x - cx)
            u = (th - a1) / (a2 - a1)
            if not (-0.02 <= u <= 1.02):
                continue
            mid = 1 - abs(2 * u - 1)
            w = 0.9 + (wmid - 0.9) * mid
            if abs(u - 0.5) < 0.11:
                w += 0.6
            if R - w <= r <= R + 0.45:
                if abs(u - 0.5) < 0.11:
                    s.put(x, y, grip)
                elif r > R - 0.55:
                    s.put(x, y, wood[2] if u < 0.5 else wood[1])
                else:
                    s.put(x, y, wood[1] if u < 0.5 else wood[0])
    for x in range(3, 13):
        if (x, x - 1) not in s.px:
            s.put(x, x - 1, string)
    s.put(*t1, nock); s.put(*t2, nock)
    if recurve:
        s.put(3, 0, nock); s.put(15, 12, nock)


@design('swift_huntbow', 'Bogenschütze', 'Schnellschuss', 'Jägerbogen',
        'Leichter Reflexbogen aus Grünholz mit hellen Spitzen und Federn am Griff – für schnelle Schüsse.')
def swift(s):
    bow(s, 2.5, 1.9, [hexc('2f4a14'), hexc('4f7a22'), hexc('79a83a')], hexc('7d5230'), hexc('e8e4d8'),
        hexc('c6e58a'), recurve=True)
    s.put(9, 7, hexc('d9d2c2')); s.put(9, 8, hexc('a9d46a'))   # feathers hanging from the grip
    s.outline(hexc('141c0b'))


@design('heavy_siegebow', 'Bogenschütze', 'Wuchtschütze', 'Belagerungsbogen',
        'Wuchtiger Langbogen aus dunklem Holz, Stahlplatten an den Wurfarmen, roter Griff – zieht schwer, trifft hart.')
def heavy(s):
    bow(s, 3.0, 2.6, [hexc('2a1a0e'), hexc('4a2e18'), hexc('6b4426')], hexc('b32a2a'), hexc('d8d2c4'), IRON[4])
    for (x, y) in ((5, 1), (8, 2), (12, 6), (13, 9)):     # steel plates on the outside of the limbs
        if (x, y) in s.px:
            s.put(x, y, IRON[3])
    s.outline(hexc('0e0906'))


def crossbow(s, stock, prod, string, bolt, head, butt=None):
    """A loaded crossbow: stock from the bottom left, the prod across the front, the string
    drawn back to the nut, a bolt on top pointing to the top right."""
    def stock_f(a, d, x, y):
        if -13 <= a <= 6 and abs(d) <= 1:
            return stock[2] if d < 0 else stock[1] if d == 0 else stock[0]
        if -13 <= a <= -9 and abs(d) <= 2:
            return (butt or stock)[1]
    s.field(stock_f)
    s.segment((5, 2), (7, 7), string, 0.5)
    s.segment((12, 9), (7, 7), string, 0.5)

    def prod_f(a, d, x, y):
        if abs(d) > 7:
            return None
        if abs(a - (6.5 - 3.2 * (d / 7.0) ** 2)) <= 0.95:
            return prod[2] if d < -2 else prod[1] if d < 3 else prod[0]
    s.field(prod_f)

    def bolt_f(a, d, x, y):
        if d == 0 and 0 <= a <= 9:
            return bolt
        if 10 <= a <= 12 and abs(d) <= (12 - a) * 0.7 + 0.2:
            return head
    s.field(bolt_f)


@design('bolter_launcher', 'Bogenschütze', 'Bolzenschütze', 'Bolzenwerfer',
        'Schwere Armbrust mit schwarz-orangem Schaft, Stahlbogen und Bolzenmagazin auf dem Schaft.')
def bolter(s):
    crossbow(s, [hexc('1c1c1c'), hexc('2e2e2e'), hexc('e07a1f')], [IRON[1], IRON[2], IRON[4]],
             hexc('d8d2c4'), hexc('6b4a2a'), IRON[4], butt=[hexc('b85d12'), hexc('e07a1f')])
    s.field(lambda a, d, x, y: (hexc('3a3a3a') if d < 0 else hexc('222222')) if -6 <= a <= -3 and -3 <= d <= -1 else None)
    s.put(4, 8, hexc('e07a1f'))
    s.outline(hexc('0e0a06'))


@design('trapper_netbow', 'Bogenschütze', 'Fallensteller', 'Netzarmbrust',
        'Armbrust aus dunklem Holz mit Seiltrommel unter dem Schaft, verschießt Netzbolzen.')
def trapper(s):
    crossbow(s, [hexc('2e1c0e'), hexc('4a2e16'), hexc('7a5232')], [IRON[1], IRON[2], IRON[3]], hexc('e8dcb8'),
             hexc('8a7240'), hexc('d8c79a'))
    # rope drum below the stock (shadow side): rope windings
    s.field(lambda a, d, x, y: (hexc('d8c79a') if a % 2 == 0 else hexc('9c8450'))
            if -7 <= a <= -3 and 2 <= d <= 3 else None)
    # the net on the bolt head: a little mesh
    s.field(lambda a, d, x, y: hexc('e8dcb8') if 10 <= a <= 13 and abs(d) <= 2 and (a % 2 == 0 or abs(d) + (a - 10) >= 4) and abs(d) + (a - 10) <= 4
            else None)
    s.outline(hexc('140c06'))


# ---------------------------------------------------------------------- guardian
@design('shieldfighter_crestsword', 'Wächter', 'Schildkämpfer', 'Wappenschwert',
        'Ritterliches Kurzschwert: blanke Klinge, goldenes Kreuzgefäß mit blauem Stein.')
def shieldfighter(s):
    s.band(-5, 10, 1, [STEEL[2], STEEL[3], STEEL[4]], taper=3)
    s.band(-4, 9, 0, [STEEL[1]], off=0)
    s.across(-7, 4, [GOLD[1], GOLD[2], GOLD[3], GOLD[4], GOLD[3], GOLD[2], GOLD[1]])
    s.put(5, 11, hexc('3b82f6')); s.put(4, 12, hexc('1e4fb3'))
    s.band(-12, -8, 0, [hexc('1e3a8a')])
    s.put(1, 14, GOLD[3]); s.put(0, 15, GOLD[2])
    s.outline(STEEL[0])


@design('bulwark_wallbreaker', 'Wächter', 'Bollwerk', 'Wallbrecher',
        'Breites, schweres Breitschwert aus grauem Stahl mit Goldnieten – eine Mauer aus Klinge.')
def bulwark(s):
    s.band(-6, 11, 2, [IRON[4], IRON[3], IRON[3], IRON[2], IRON[1]], taper=3)
    for a in (-3, 1, 5):
        x = (a + 15) // 2
        s.put(x, x - a, GOLD[3])
    s.across(-8, 4, [IRON[1], IRON[2], GOLD[3], IRON[2], IRON[1]], thick=1)
    s.band(-12, -9, 0, [LEATHER[2]])
    s.put(1, 14, IRON[3]); s.put(0, 15, IRON[2]); s.put(2, 13, IRON[2])
    s.outline(IRON[0])


@design('medic_lancet', 'Wächter', 'Sanitäter', 'Lazarettklinge',
        'Schlanke, helle Klinge mit grünem Kreuz am Parier und weiß-grünem Griff.')
def medic(s):
    s.band(-5, 12, 1, [hexc('cfd8d4'), hexc('eef4f1'), hexc('ffffff')], taper=4)
    s.band(-4, 11, 0, [hexc('a9b8b1')], off=1)
    s.across(-7, 3, [hexc('d8dcd9'), hexc('f4f6f4'), hexc('2fae5b'), hexc('f4f6f4'), hexc('d8dcd9')])
    s.put(4, 10, hexc('2fae5b')); s.put(6, 12, hexc('2fae5b'))
    s.band(-12, -8, 0, [hexc('f1f1ec')])
    s.put(2, 13, hexc('2fae5b')); s.put(1, 14, hexc('cfd8d4')); s.put(0, 15, hexc('a9b8b1'))
    s.outline(hexc('263a30'))


@design('warden_wardblade', 'Wächter', 'Bannwächter', 'Bannklinge',
        'Silberklinge mit leuchtender violetter Runenrinne und Halbmond-Parier.')
def warden(s):
    s.band(-5, 12, 1, [STEEL[3], STEEL[4], STEEL[2]], taper=3)
    s.band(-4, 10, 0, [hexc('b07cff')])
    s.put(7, 5, hexc('e6d2ff')); s.put(9, 3, hexc('e6d2ff'))
    s.art(['a....', '.b...', '..b..', '...ba', '....a'], {'a': hexc('6b3fb8'), 'b': hexc('9a66e8')}, ox=1, oy=8)
    s.band(-12, -9, 0, [hexc('3b2466')])
    s.put(1, 14, hexc('b07cff')); s.put(0, 15, hexc('6b3fb8'))
    s.outline(hexc('1a1030'))


# ---------------------------------------------------------------------- engineer
@design('gunner_wrenchblade', 'Techniker', 'Schütze', 'Schraubklinge',
        'Messingklinge, deren Spitze ein offener Schraubenschlüssel ist, Zahnrad als Parier.')
def gunner(s):
    s.band(-5, 8, 1, [COPPER[2], COPPER[3], COPPER[4]])
    s.art(['.aa.', 'a..a', 'b..b', '.bb.'], {'a': COPPER[4], 'b': COPPER[2]}, ox=11, oy=0)   # jaw ring
    s.clear(13, 1); s.clear(12, 2)
    s.art(['.a.a.', 'abbba', '.bcb.', 'abbba', '.a.a.'], {'a': IRON[2], 'b': IRON[3], 'c': IRON[1]}, ox=1, oy=9)   # cog
    s.band(-13, -10, 0, [LEATHER[3]])
    s.outline(hexc('1e1008'))


@design('electrician_coilblade', 'Techniker', 'Elektriker', 'Spulenklinge',
        'Kupferstab mit Spulenwicklungen und blauem Funken an der Spitze, isolierter Griff.')
def electrician(s):
    s.band(-6, 10, 0, [COPPER[3]])
    s.band(-6, 10, 0, [COPPER[2]], off=1)
    for a in (-4, -1, 2, 5, 8):
        x = (a + 15) // 2
        s.put(x, x - a, COPPER[4]); s.put(x + 1, x - a, COPPER[4]); s.put(x, x - a - 1, COPPER[1])
    s.art(['..a.', '.aba', 'abca', '.aa.'], {'a': hexc('2fd6e8'), 'b': hexc('a8f6ff'), 'c': hexc('ffffff')}, ox=11, oy=0)
    s.across(-8, 2, [IRON[2], IRON[3], IRON[2]])
    s.band(-12, -9, 0, [hexc('222222')])
    s.put(1, 14, hexc('333333')); s.put(0, 15, hexc('2fd6e8'))
    s.outline(hexc('0f0a06'))


@design('demolition_fusemachete', 'Techniker', 'Sprengmeister', 'Zündermachete',
        'Schwarze Machete mit rotem Streifen, am Knauf eine glimmende Zündschnur.')
def demolition(s):
    s.band(-5, 11, 1, [DARKSTEEL[3], DARKSTEEL[2], DARKSTEEL[1]], taper=2)
    s.band(-4, 9, 0, [hexc('c23a2a')], off=1)
    s.put(12, 2, DARKSTEEL[4]); s.put(13, 2, DARKSTEEL[3])
    s.across(-7, 2, [IRON[2], IRON[3], IRON[2]])
    s.band(-12, -8, 0, [hexc('5a2c1a')])
    s.line([(1, 13), (0, 12), (0, 11)], hexc('d9c08a'))   # fuse
    s.put(1, 10, hexc('ffd34a')); s.put(0, 10, hexc('ff7a1a'))
    s.outline(hexc('0b0b0e'))


@design('alchemist_vialknife', 'Techniker', 'Alchemist', 'Phiolenklinge',
        'Klinge mit gläserner Rinne voll grünem Gift, dunkler Stahl, Phiole als Knauf.')
def alchemist(s):
    s.band(-5, 11, 1, [DARKSTEEL[3], DARKSTEEL[2], DARKSTEEL[1]], taper=3)
    s.band(-4, 9, 0, [hexc('6fe04a')])
    s.put(6, 7, hexc('c8ff9a')); s.put(9, 4, hexc('c8ff9a'))
    s.across(-7, 2, [hexc('3a2a1a'), hexc('5a4430'), hexc('3a2a1a')])
    s.band(-11, -8, 0, [hexc('2a2a2a')])
    s.art(['.a.', 'aba', 'aca'], {'a': hexc('cfe8ff'), 'b': hexc('6fe04a'), 'c': hexc('3a9a2a')}, ox=0, oy=12)
    s.outline(hexc('0c1008'))


# ---------------------------------------------------------------------- summoner
def staff_shaft(s, c1, c2, a1=6):
    s.band(-13, a1, 0, [c1])
    s.band(-12, a1 - 1, 0, [c2], off=1)


@design('tamer_packstaff', 'Beschwörer', 'Tierbändiger', 'Rudelstab',
        'Knorriger Holzstab mit Wolfszahn-Sichel, Lederriemen und grauen Federn.')
def tamer(s):
    staff_shaft(s, WOOD[3], WOOD[2], a1=5)
    s.art([
        '...aab',
        '..a..b',
        '.a...b',
        'a....c',
        'a...cc',
        '.dd...',
    ], {'a': BONE[4], 'b': BONE[2], 'c': WOOD[3], 'd': hexc('9a9a9a')}, ox=9, oy=0)
    s.put(5, 10, LEATHER[3]); s.put(6, 9, LEATHER[3])
    s.put(4, 9, hexc('bdbdbd')); s.put(3, 9, hexc('8a8a8a'))
    s.outline(hexc('1a120a'))


@design('golemsmith_forgestaff', 'Beschwörer', 'Golemschmied', 'Schmiedestab',
        'Eisenstab mit Amboss-Hammerkopf und glühendem Kern – Werkzeug und Zepter zugleich.')
def golemsmith(s):
    staff_shaft(s, IRON[3], IRON[2], a1=5)
    s.art([
        '.aaaaa',
        'abbbbb',
        'abcccb',
        '.bdddb',
        '..beb.',
        '..bbb.',
    ], {'a': IRON[4], 'b': IRON[2], 'c': hexc('ff9a2a'), 'd': hexc('ffd15a'), 'e': IRON[3]}, ox=9, oy=0)
    s.put(4, 11, hexc('ff9a2a')); s.put(6, 9, hexc('ff9a2a'))
    s.outline(IRON[0])


@design('necromancer_bonestaff', 'Beschwörer', 'Totenbeschwörer', 'Knochenstab',
        'Stab aus Knochen mit kleinem Schädel, violetten Kristallaugen und violettem Tuch.')
def necromancer(s):
    staff_shaft(s, BONE[3], BONE[2], a1=6)
    s.art([
        '..aaa.',
        '.abbba',
        '.bcbcb',
        '.bbbbb',
        '..bdb.',
        '...b..',
    ], {'a': BONE[4], 'b': BONE[3], 'c': hexc('b26bff'), 'd': BONE[1]}, ox=9, oy=0)
    s.put(5, 10, hexc('5b2a8c')); s.put(6, 10, hexc('7a3fb8')); s.put(5, 11, hexc('7a3fb8'))
    s.put(4, 12, hexc('5b2a8c'))
    s.outline(hexc('1d1426'))


@design('soulcatcher_lanternstaff', 'Beschwörer', 'Seelenfänger', 'Seelenlaterne',
        'Dunkler Stab mit Haken, daran eine Laterne mit türkiser Seelenflamme.')
def soulcatcher(s):
    staff_shaft(s, hexc('2b3a3a'), hexc('1e2a2a'), a1=7)
    s.art([
        '..aaa',
        '.a...',
        '.a...',
    ], {'a': hexc('3d5050')}, ox=11, oy=0)
    s.art([
        '.aaa.',
        'abcba',
        'acdca',
        'abcba',
        '.aaa.',
    ], {'a': hexc('2f3a3a'), 'b': hexc('3fd0c8'), 'c': hexc('8ff4ee'), 'd': hexc('ffffff')}, ox=9, oy=3)
    s.put(3, 11, hexc('3fd0c8')); s.put(4, 10, hexc('2a8f8a'))
    s.outline(hexc('0a1212'))


# ---------------------------------------------------------------------- sheet
def iso_render(sprite, cube=12):
    """The item as Minecraft draws it in the hand: every pixel a little block, one pixel deep."""
    from PIL import Image, ImageDraw
    import math
    w = (W + H) * cube + 4 * cube
    im = Image.new('RGBA', (int(w), int(w * 0.75)), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    ang = math.radians(30)
    cx, cy = im.width / 2, im.height * 0.18

    def proj(x, y, z):
        # x right, y down, z towards the viewer; turned a little for depth
        px = (x - y * 0.0) * cube * math.cos(ang) + z * cube * 0.9
        py = (y) * cube * 0.82 + x * cube * math.sin(ang) * 0.45 - z * cube * 0.45
        return (cx + px - W * cube * 0.45, cy + py)

    order = sorted(sprite.px.items(), key=lambda kv: (kv[0][0] - kv[0][1]))
    for (x, y), c in order:
        r, g, b, a = c
        top = (min(255, int(r * 1.15)), min(255, int(g * 1.15)), min(255, int(b * 1.15)), 255)
        side = (int(r * 0.62), int(g * 0.62), int(b * 0.62), 255)
        front = (r, g, b, 255)
        # back face is hidden; draw side (right), top, then front
        if (x + 1, y) not in sprite.px:
            d.polygon([proj(x + 1, y, 0), proj(x + 1, y, 1), proj(x + 1, y + 1, 1), proj(x + 1, y + 1, 0)], fill=side)
        if (x, y - 1) not in sprite.px:
            d.polygon([proj(x, y, 1), proj(x + 1, y, 1), proj(x + 1, y, 0), proj(x, y, 0)], fill=top)
        d.polygon([proj(x, y, 1), proj(x + 1, y, 1), proj(x + 1, y + 1, 1), proj(x, y + 1, 1)], fill=front)
    return im.crop(im.getbbox()) if im.getbbox() else im


def sheet(path):
    from PIL import Image, ImageDraw, ImageFont
    font_b = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 22)
    font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 15)
    font_s = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 15)
    classes = ['Krieger', 'Bogenschütze', 'Wächter', 'Techniker', 'Beschwörer']
    cell_w, cell_h = 430, 360
    pad = 30
    im = Image.new('RGBA', (pad * 2 + cell_w * 4, 90 + len(classes) * (cell_h + 40)), (18, 16, 25, 255))
    d = ImageDraw.Draw(im)
    d.text((pad, 24), 'Night Siege – Kit-Waffen je Unterklasse (Entwurf, 16×16 wie Vanilla)', fill=(255, 216, 107), font=font_b)
    y = 80
    for clazz in classes:
        d.text((pad, y), clazz.upper(), fill=(224, 90, 90), font=font_s)
        y += 26
        items = [t for t in DESIGNS if t[1] == clazz]
        for i, (key, _, sub, name, line, fn) in enumerate(items):
            x0 = pad + i * cell_w
            d.rectangle([x0, y, x0 + cell_w - 14, y + cell_h - 14], fill=(26, 22, 34), outline=(58, 52, 80), width=2)
            s = Sprite()
            fn(s)
            os.makedirs(OUT, exist_ok=True)
            s.image().save(os.path.join(OUT, key + '.png'))
            big = s.image(10)
            slot = Image.new('RGBA', (172, 172), (14, 12, 20, 255))
            slot.paste(big, (6, 6), big)
            im.paste(slot, (x0 + 12, y + 12))
            ImageDraw.Draw(im).rectangle([x0 + 12, y + 12, x0 + 183, y + 183], outline=(44, 39, 64), width=2)
            iso = iso_render(s, cube=9)
            iso.thumbnail((210, 175), Image.LANCZOS)
            im.paste(iso, (x0 + 200 + (210 - iso.width) // 2, y + 12 + (172 - iso.height) // 2), iso)
            # hotbar size, twice and once
            hb = Image.new('RGBA', (40, 40), (60, 60, 60, 255))
            small = s.image(2)
            hb.paste(small, (4, 4), small)
            im.paste(hb, (x0 + 12, y + 196))
            d.text((x0 + 60, y + 196), name, fill=(255, 255, 255), font=font_b)
            d.text((x0 + 60, y + 222), sub, fill=(255, 216, 107), font=font_s)
            # wrap the line
            words, lines, cur = line.split(), [], ''
            for w_ in words:
                if d.textlength(cur + ' ' + w_, font=font) > cell_w - 40:
                    lines.append(cur)
                    cur = w_
                else:
                    cur = (cur + ' ' + w_).strip()
            lines.append(cur)
            for j, l in enumerate(lines[:4]):
                d.text((x0 + 14, y + 250 + j * 20), l, fill=(207, 199, 221), font=font)
        y += cell_h + 14
    im.save(path)


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    sheet(os.path.join(OUT, 'sheet.png'))
    print('ok', len(DESIGNS))
