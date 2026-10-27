"""
Kit weapons, second round (Simon, 27 October 2026: "design the models more elaborately and
closer to the armour"). Same 16x16 size and layout as designs.py (handle bottom left, tip top
right, lit from the top left), but every weapon takes the colours and motifs of its subclass's
outfit (textures/entity/equipment/humanoid/class_*.png), with more shading, ornaments and a
tinted outline instead of a flat black one.

python3 tools/weapons/kit_v2.py [--install]
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import designs as D  # noqa: E402

hexc = D.hexc
Sprite = D.Sprite
D.DESIGNS.clear()
design = D.design


def R(*h):
    return [hexc(x) for x in h]


def mix(c1, c2, t):
    return tuple(int(round(c1[i] * (1 - t) + c2[i] * t)) for i in range(3)) + (255,)


def dark(c, f):
    return (int(c[0] * f), int(c[1] * f), int(c[2] * f), 255)


# ------------------------------------------------------------------ helpers
def outline(s, f=0.38, keep=()):
    """A rim around the weapon, tinted after what it borders (vanilla items do the same)."""
    rim = {}
    for (x, y), c in list(s.px.items()):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            p = (x + dx, y + dy)
            if 0 <= p[0] < 16 and 0 <= p[1] < 16 and p not in s.px:
                rim.setdefault(p, []).append(c)
    for p, cs in rim.items():
        r = sum(c[0] for c in cs) / len(cs)
        g = sum(c[1] for c in cs) / len(cs)
        b = sum(c[2] for c in cs) / len(cs)
        s.px[p] = (int(r * f), int(g * f), int(b * f), 255)


def blade(s, a0, a1, w, ramp, taper=3, curve=0.0, notch=None, fuller=None, back=None):
    """A blade from a0 to a1, half width w. ramp = shadow .. highlight (4-5 colours); the lit
    edge (top left) is brightest, the back edge darkest. curve bends it towards the lit side
    near the tip (a sabre). notch(a) -> True cuts the lit edge there. fuller = colour of the
    groove along the middle."""
    n = len(ramp)
    for x in range(16):
        for y in range(16):
            a, d = x - y, x + y - 14
            if a < a0 or a > a1:
                continue
            t = (a - a0) / max(1, a1 - a0)
            off = -curve * t * t
            ww = w if a <= a1 - taper else w * (a1 - a) / taper
            dd = d - off
            if abs(dd) > ww + 0.01:
                continue
            if notch and dd < -ww + 1.2 and notch(a):
                continue
            # lit edge -> highlight, back -> shadow
            u = (dd + ww) / max(0.01, 2 * ww)
            i = n - 1 - min(n - 1, int(u * n))
            c = ramp[i]
            if fuller is not None and abs(dd) < 0.6 and a0 + 2 <= a <= a1 - taper - 1:
                c = fuller
            if back is not None and dd > ww - 0.6:
                c = back
            s.put(x, y, c)


def grip(s, a0, a1, c1, c2, w=1):
    """A wrapped grip: two colours in diagonal turns."""
    for x in range(16):
        for y in range(16):
            a, d = x - y, x + y - 14
            if a0 <= a <= a1 and abs(d) <= w:
                s.put(x, y, c1 if ((a + (1 if d > 0 else 0)) // 2) % 2 == 0 else c2)


def shaft(s, a0, a1, ramp, w=1):
    """A round shaft: lit side, middle, shadow side."""
    for x in range(16):
        for y in range(16):
            a, d = x - y, x + y - 14
            if a0 <= a <= a1 and abs(d) <= w:
                s.put(x, y, ramp[2] if d < 0 else ramp[1] if d == 0 else ramp[0])


def guard(s, a, half, ramp, bend=0.0, thick=0.9, gem=None, gemhi=None):
    """A cross guard across the weapon at a, `half` to each side; bend > 0 curves its ends
    towards the blade. ramp = shadow, mid, light (lit side = negative d)."""
    for x in range(16):
        for y in range(16):
            aa, d = x - y, x + y - 14
            if abs(d) > half + 0.01:
                continue
            ac = a + bend * (d / max(1, half)) ** 2
            if abs(aa - ac) <= thick:
                c = ramp[2] if aa > ac else ramp[1] if d < 0 else ramp[0]
                if abs(d) >= half - 0.5:
                    c = ramp[2] if d < 0 else ramp[1]
                s.put(x, y, c)
    if gem is not None:
        s.field(lambda aa, d, x, y: (gemhi if gemhi and d < 0 else gem) if abs(aa - a) <= 1 and abs(d) <= 0 else None)


def hilt(s, rows, pal):
    """Hand-drawn pixels for the hilt corner (x 0..7, y 8..15); '.' keeps, 'x' clears."""
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == 'x':
                s.clear(i, 8 + j)
            elif ch != '.':
                s.put(i, 8 + j, pal[ch])


def px_ad(a, d):
    """Pixel of diagonal coordinates (a and d of the same parity)."""
    return ((a + d + 14) // 2, (d - a + 14) // 2)


def dot(s, a, d, c):
    x, y = px_ad(a, d)
    s.put(x, y, c)


# ------------------------------------------------------------------ colours from the outfits
STEEL = R('3a3f4a', '6d7584', 'a9b1bf', 'd6dce6', 'ffffff')
WHITESTEEL = R('7c828c', 'a8afba', 'cfd5de', 'ebeff4', 'ffffff')
DARKIRON = R('201f23', '3a3940', '57565e', '7d7c85', 'a4a3ab')
BONE = R('6b6555', '8d8168', 'b3aa94', 'cbc1a7', 'ece4cc')
GOLD = R('6b5214', '9a7a1c', 'c9a23a', 'e8c860', 'fff0a8')
NAVY = R('0e1733', '1a2b5c', '2a4594', '4a6cc8')
LEATHER = R('2c1b0e', '4a2d19', '6a4429', '8d5f38')
COPPER = R('5a2c12', '975026', 'b57641', 'ce8649', 'f2b984')


# ------------------------------------------------------------------ warrior
@design('blademaster_saber', 'Krieger', 'Klingenmeister', 'Duellsäbel',
        'Geschwungener Säbel aus hellem Stahl, goldenes Korbgefäß mit Knöchelbügel und Rubin, '
        'marineblauer Griff mit weißer Wicklung, rote Quaste – die Paradeuniform.')
def blademaster(s):
    blade(s, -5, 13, 1.1, STEEL[1:], taper=4, curve=2.4, back=STEEL[1])
    hilt(s, [
        '..q.....',
        '...G....',
        '...nr...',
        '..wNwG..',
        '.nwn..g.',
        'PPn...g.',
        'tp...h..',
        'Tthhh...',
    ], {'q': GOLD[4], 'G': GOLD[3], 'g': GOLD[2], 'h': GOLD[1], 'n': NAVY[2], 'N': NAVY[1], 'w': hexc('d8d2c0'),
        'r': hexc('e03a3a'), 'P': GOLD[3], 'p': GOLD[1], 't': hexc('c22a2a'), 'T': hexc('8a1717')})
    outline(s)


@design('berserker_bloodblade', 'Krieger', 'Berserker', 'Blutklinge',
        'Breites, schartiges Hackschwert aus dunklem Eisen mit Blutrinne, Knochenparier mit '
        'Hörnern wie am Schädelhelm, Fellgriff mit Knochenringen, ein kleiner Schädel als Knauf.')
def berserker(s):
    blade(s, -4, 12, 2.6, DARKIRON[1:], taper=4, notch=lambda a: a % 4 == 3, fuller=hexc('8e1b1b'))
    s.field(lambda a, d, x, y: hexc('c03030') if d == 0 and a in (2, 6) else None)
    for (x, y) in ((11, 1), (12, 2), (10, 3)):
        if (x, y) in s.px:
            s.put(x, y, hexc('9c1c1c'))
    pal = {'B': BONE[4], 'b': BONE[3], 'm': BONE[2], 'k': BONE[1], 'f': hexc('5a4a3a'), 'F': hexc('342b23'),
           'e': hexc('2a1d14')}
    hilt(s, [
        '..b.....',
        '...B....',
        '...fB...',
        '..fkFb..',
        '.fkF..m.',
        'mbF.....',
        'ebe.....',
        'beb.....',
    ], pal)
    s.put(2, 7, BONE[3]); s.put(3, 6, BONE[4])      # horn tips towards the blade
    s.put(7, 12, BONE[2]); s.put(8, 11, BONE[3])
    s.put(7, 13, BONE[1])
    outline(s, 0.34)


@design('axe_bearded', 'Krieger', 'Axtkämpfer', 'Bartaxt',
        'Bartaxt mit Stahlkopf und eingelegtem Goldkreuz wie am Helm, Eschenstiel mit '
        'rostroten Lederwicklungen und Kupfernieten.')
def axe(s):
    c = 4.0
    wood = R('3a2412', '5c3a1f', '7d5230', '9c6c42')
    for x in range(16):
        for y in range(16):
            a, d = x - y, x + y - 14
            if -13 <= a <= 7 and abs(d) <= 1:
                band = a in (-11, -10, -7, -6)
                s.put(x, y, (hexc('b5602a') if d <= 0 else hexc('7d3a14')) if band else
                      wood[2] if d < 0 else wood[1] if d == 0 else wood[0])

    def head(a, d, x, y):
        m = -d
        if m < 1 or m > 7.6:
            return None
        t = (m - 1) / 6.6
        u = a - c
        lo = -(1.2 + 1.2 * t) - 6.0 * t ** 2.2
        hi = 1.4 + 1.6 * t
        if not (lo <= u <= hi) or m > 7.6 - 0.06 * (u - (lo + hi) / 2) ** 2:
            return None
        if m >= 6.5:
            return hexc('f4f6fa')
        if m >= 5.6:
            return hexc('c9ced8')
        if u <= lo + 1.0 and t > 0.4:
            return hexc('4e5156')
        return hexc('9aa0ab') if u > hi - 1.2 else hexc('767c87')
    s.field(head)
    # gold cross inlay on the cheek of the head
    for (a, d) in ((3, -3), (5, -3), (4, -2), (4, -4), (4, -3)):
        dot(s, a, d, GOLD[3] if (a, d) != (4, -3) else GOLD[4])
    s.field(lambda a, d, x, y: hexc('4e5156') if c - 1 <= a <= c + 1 and 2 <= d <= 3 else None)   # poll
    dot(s, 5, 3, COPPER[3])
    dot(s, 8, 0, hexc('767c87'))
    outline(s, 0.36)


@design('paladin_sunaxe', 'Krieger', 'Paladin', 'Sonnenaxt',
        'Doppelaxt aus Weißstahl, in der Mitte die goldene Sonne auf rotem Grund wie auf dem '
        'Wappenrock, weißer Stiel mit Goldringen.')
def paladin(s):
    c = 4.0
    for x in range(16):
        for y in range(16):
            a, d = x - y, x + y - 14
            if -13 <= a <= 10 and abs(d) <= 1:
                ring = a in (-11, -8, -1)
                s.put(x, y, (GOLD[3] if d <= 0 else GOLD[1]) if ring else
                      hexc('efe9dc') if d < 0 else hexc('cfc7b4') if d == 0 else hexc('a39b88'))

    def heads(a, d, x, y):
        m = abs(d)
        if m < 1 or m > 7.6:
            return None
        t = (m - 1) / 6.6
        u = a - c
        w = 1.3 + 3.2 * t ** 1.4
        if abs(u) > w or m > 7.6 - 0.1 * u * u:
            return None
        if m >= 6.3:
            return GOLD[3] if d < 0 else GOLD[2]          # gilded edges
        if m >= 5.4:
            return WHITESTEEL[4] if d < 0 else WHITESTEEL[2]
        return WHITESTEEL[3] if d < 0 else WHITESTEEL[1]
    s.field(heads)

    def sun(a, d, x, y):
        r = abs(a - c) + abs(d)
        if r <= 1:
            return GOLD[4]
        if r == 2:
            return GOLD[3] if (a - c) * d == 0 or abs(a - c) == abs(d) else hexc('8a1a1a')
        if r == 3 and abs(a - c) <= 2 and abs(d) <= 2:
            return hexc('5c0e0e')
    s.field(sun)
    s.field(lambda a, d, x, y: GOLD[4] if 11 <= a <= 12 and d == 0 or a == 11 and abs(d) == 1 else None)
    # a red pennant under the head
    for (a, d, h) in ((-1, 2, '8a1a1a'), (-2, 3, '5c0e0e'), (0, 3, 'a82424')):
        dot(s, a, d, hexc(h))
    outline(s, 0.36)


# ------------------------------------------------------------------ archer
def bow(s, k, wmid, wood, grip_c, string, nock, recurve=False, plates=None, grip2=None):
    """A bow as Minecraft draws it (string top left to bottom right, limbs to the top right),
    with the drawing states of designs.STATE."""
    t1, t2 = (2, 1), (13, 12)
    cx, cy = 7.5 - k, 6.5 + k
    Rr = math.dist((cx, cy), t1)
    a1 = math.atan2(t1[1] - cy, t1[0] - cx)
    a2 = math.atan2(t2[1] - cy, t2[0] - cx)
    for x in range(16):
        for y in range(16):
            r = math.dist((cx, cy), (x, y))
            th = math.atan2(y - cy, x - cx)
            u = (th - a1) / (a2 - a1)
            if not (-0.02 <= u <= 1.02):
                continue
            mid = 1 - abs(2 * u - 1)
            w = 0.9 + (wmid - 0.9) * mid
            g = abs(u - 0.5) < 0.11
            if g:
                w += 0.6
            if Rr - w <= r <= Rr + 0.45:
                if g:
                    c = grip_c if (grip2 is None or (x + y) % 2 == 0) else grip2
                elif r > Rr - 0.55:
                    c = wood[3] if u < 0.35 else wood[2]
                elif r > Rr - 1.3:
                    c = wood[2] if u < 0.5 else wood[1]
                else:
                    c = wood[1] if u < 0.5 else wood[0]
                if plates and not g and r > Rr - 0.55 and abs(u - 0.5) > 0.17 and int(u * 20) % 3 == 0:
                    c = plates[1] if u < 0.5 else plates[0]
                s.put(x, y, c)
    pull = {'pull0': 1.0, 'pull1': 2.0, 'pull2': 2.9}.get(D.STATE)
    if pull is None:
        for x in range(3, 13):
            if (x, x - 1) not in s.px:
                s.put(x, x - 1, string)
    else:
        nx, ny = 7.5 - pull, 6.5 + pull
        s.segment(t1, (nx, ny), string, 0.5, over=False)
        s.segment(t2, (nx, ny), string, 0.5, over=False)
        tip = min(Rr + 2.6, math.dist((cx, cy), (15.0, 0.0)))
        tx, ty = round(cx + tip * 0.7071), round(cy - tip * 0.7071)
        s.segment((nx + 0.6, ny - 0.6), (tx - 1.5, ty + 1.5), D.ARROW_SHAFT, 0.5)
        for (px, py) in ((tx, ty), (tx - 1, ty), (tx, ty + 1), (tx - 1, ty + 1)):
            s.put(px, py, D.ARROW_HEAD)
        fx, fy = round(nx), round(ny)
        for (px, py) in ((fx - 1, fy), (fx, fy + 1), (fx - 1, fy + 1)):
            s.put(px, py, D.ARROW_FEATHER)
    s.put(*t1, nock)
    s.put(*t2, nock)
    if recurve:
        s.put(3, 0, nock)
        s.put(15, 12, nock)


@design('swift_huntbow', 'Bogenschütze', 'Schnellschuss', 'Jägerbogen',
        'Leichter Reflexbogen aus Grünholz wie die Kapuze, Blätter an den Wurfarmen, '
        'brauner Ledergriff mit grünen Federn.')
def swift(s):
    bow(s, 2.5, 1.9, R('1c3516', '2b5024', '3f7a33', '6fae4a'), hexc('6e4524'), hexc('e8e4d8'),
        hexc('a7d05a'), recurve=True, grip2=hexc('8d5f38'))
    for (x, y) in ((5, 1), (12, 7)):          # leaves on the limbs
        s.put(x, y, hexc('8fd05a'))
    s.put(9, 6, hexc('a7d05a'))
    s.put(9, 7, hexc('5f9a3a'))               # feathers at the grip
    if D.STATE == 'standby':
        s.put(10, 7, hexc('d9d2c2'))
    outline(s, 0.36)


@design('heavy_siegebow', 'Bogenschütze', 'Wuchtschütze', 'Belagerungsbogen',
        'Wuchtiger Langbogen aus dunklem Holz mit Stahlplatten wie am Helm, '
        'rot-weiß gestreifter Griff wie die Schärpe.')
def heavy(s):
    bow(s, 3.0, 2.6, R('1f140a', '352010', '4a2e16', '664b25'), hexc('a81e1e'), hexc('d8d2c4'),
        hexc('a8acb2'), plates=R('5a5e63', '9ca0a8'), grip2=hexc('e8e2d4'))
    outline(s, 0.34)


def crossbow(s, stock, prod, string, bolt, head, butt=None, stripes=None):
    def stock_f(a, d, x, y):
        if -13 <= a <= 6 and abs(d) <= 1:
            if stripes and a % 3 == 0 and a < 3:
                return stripes[0] if d <= 0 else stripes[1]
            return stock[2] if d < 0 else stock[1] if d == 0 else stock[0]
        if -13 <= a <= -9 and abs(d) <= 2:
            b = butt or stock
            return b[2] if d < 0 else b[1] if d <= 1 else b[0]
    s.field(stock_f)
    nut = {'standby': None, 'pull0': 2.6, 'pull1': 1.6, 'pull2': 0.6}.get(D.STATE, 0.0)
    bend = 2.6 if D.STATE == 'standby' else 3.2
    if nut is None:
        s.segment((5, 2), (12, 9), string, 0.5)
    else:
        n = ((nut + 14) / 2, (14 - nut) / 2)
        s.segment((5, 2), n, string, 0.5)
        s.segment((12, 9), n, string, 0.5)

    def prod_f(a, d, x, y):
        if abs(d) > 7:
            return None
        ac = 6.5 - bend * (d / 7.0) ** 2
        if abs(a - ac) <= 0.95:
            if abs(d) >= 6:
                return prod[3]
            return prod[2] if a > ac else prod[1] if d < 2 else prod[0]
    s.field(prod_f)
    if D.STATE == 'arrow':
        s.field(lambda a, d, x, y: bolt if d == 0 and 0 <= a <= 9 else
                head if 10 <= a <= 12 and abs(d) <= (12 - a) * 0.7 + 0.2 else None)
    if D.STATE == 'firework':
        s.field(lambda a, d, x, y: (hexc('e0393f') if d < 0 else hexc('b02a30') if d == 0 else hexc('7a1a1f'))
                if 1 <= a <= 9 and abs(d) <= 1 else hexc('f2f2f2')
                if (10 <= a <= 11 and d == 0) or (a == 10 and abs(d) == 1) else None)


@design('bolter_launcher', 'Bogenschütze', 'Bolzenschütze', 'Bolzenwerfer',
        'Schwere Armbrust, Schaft schwarz mit orangen Streifen wie die Weste, '
        'Stahlbogen, beiges Bolzenmagazin und Spannkurbel.')
def bolter(s):
    crossbow(s, R('191515', '2e2420', '4a3a33'), R('4a4f5c', '6d7584', 'a9b1bf', 'd6dce6'),
             hexc('e0d8c0'), hexc('6b4a2a'), STEEL[3], butt=R('7a3410', '9e4a18', 'd9742c'),
             stripes=R('b3541b', '7a3410'))
    # magazine on top of the stock (lit side)
    s.field(lambda a, d, x, y: (hexc('d8cfb4') if d == -2 else hexc('b6ac92') if d == -3 else hexc('807866'))
            if -6 <= a <= -3 and -4 <= d <= -2 else None)
    dot(s, -10, 3, STEEL[2])
    dot(s, -11, 4, STEEL[1])                   # crank
    outline(s, 0.36)


@design('trapper_netbow', 'Bogenschütze', 'Fallensteller', 'Netzarmbrust',
        'Armbrust mit fellbezogenem, gestreiftem Schaft wie die Mütze, helle Seiltrommel, '
        'dunkler Holzbogen, verschießt Netzbolzen.')
def trapper(s):
    crossbow(s, R('3f3224', '6e5a3d', 'b8a27a'), R('1e140c', '3a2818', '5c4630', 'b8a27a'),
             hexc('efe4c2'), hexc('8a7240'), hexc('d8c79a'), butt=R('231b14', '594733', '9f8c67'),
             stripes=R('231b14', '1a140e'))
    s.field(lambda a, d, x, y: (hexc('efe2b8') if a % 2 == 0 else hexc('8a7240'))
            if -7 <= a <= -3 and 2 <= d <= 3 else None)
    if D.STATE == 'arrow':
        s.field(lambda a, d, x, y: hexc('efe4c2') if 10 <= a <= 13 and abs(d) <= 2 and
                (a % 2 == 0 or abs(d) + (a - 10) >= 4) and abs(d) + (a - 10) <= 4 else None)
    outline(s, 0.36)


# ------------------------------------------------------------------ guardian
@design('shieldfighter_crestsword', 'Wächter', 'Schildkämpfer', 'Wappenschwert',
        'Ritterschwert mit blauer Hohlkehle, goldenes Kreuzgefäß mit blauem Stein, '
        'blau-goldener Griff wie der Wappenrock.')
def shieldfighter(s):
    blade(s, -5, 11, 1.4, STEEL[1:], taper=3, fuller=hexc('4a6cc8'))
    guard(s, -6, 4, GOLD[1:4], bend=0.0, thick=0.9, gem=hexc('203c6f'), gemhi=hexc('4a7ae0'))
    grip(s, -10, -8, hexc('203c6f'), GOLD[3])
    s.field(lambda a, d, x, y: (GOLD[4] if d < 0 else GOLD[2]) if a in (-11, -12) and abs(d) <= 1 else None)
    dot(s, -12, 0, hexc('2f57b0'))
    outline(s, 0.36)


@design('bulwark_wallbreaker', 'Wächter', 'Bollwerk', 'Wallbrecher',
        'Schweres Breitschwert aus Plattenstahl, auf der Klingenwurzel das rot-goldene '
        'Karomuster der Brustplatte, wuchtiges Parier mit Goldkante.')
def bulwark(s):
    blade(s, -4, 11, 2.6, R('4b4e53', '6d7279', '9ca2ac', 'b2b9c4', 'e4e9ef'), taper=3)

    def checker(a, d, x, y):
        if -3 <= a <= 0 and abs(d) <= 2:
            return hexc('d8b445') if (a // 1) % 2 == 0 else hexc('8a1a1a')
    s.field(checker)
    hilt(s, [
        'x.Gs....',
        '..Gss...',
        '...Gs...',
        '..lLSs..',
        '.lLl.SS.',
        'PPL...S.',
        'pP......',
        '........',
    ], {'G': hexc('d8b445'), 's': hexc('9ca2ac'), 'S': hexc('6d7279'), 'l': hexc('6e4a2c'), 'L': hexc('3e2a1a'),
        'P': hexc('dfe4ea'), 'p': hexc('6d7279')})
    s.put(1, 7, hexc('d8b445')); s.put(7, 13, hexc('4b4e53'))
    outline(s, 0.36)


@design('medic_lancet', 'Wächter', 'Sanitäter', 'Lazarettklinge',
        'Schlanke Klinge aus hellem Stahl, weißes Parier mit grünem Kreuz wie auf dem Kittel, '
        'salbeigrüner Griff mit weißen Binden, im Knauf ein roter Tropfen.')
def medic(s):
    blade(s, -4, 12, 1.1, R('8a9590', 'bcc6c1', 'dfe7e3', 'ffffff'), taper=4)
    hilt(s, [
        '..l.....',
        '...wc...',
        '..cCc...',
        '..sgcw..',
        '.sws..l.',
        'PPs.....',
        '.pr.....',
        '........',
    ], {'l': hexc('a9b1ad'), 'w': hexc('f1f1ec'), 'c': hexc('2fae5b'), 'C': hexc('7fe8a0'), 's': hexc('536250'),
        'g': hexc('e8e4d8'), 'P': hexc('dfe7e3'), 'p': hexc('9aa59f'), 'r': hexc('c22a2a')})
    outline(s, 0.38)


@design('warden_wardblade', 'Wächter', 'Bannwächter', 'Bannklinge',
        'Silberklinge mit leuchtenden violetten Runen, Halbmond-Parier aus dunklem Violett '
        'mit Lavendel-Raute wie auf dem Banner der Robe.')
def warden(s):
    blade(s, -4, 12, 1.3, R('5a5f6e', '9ca2af', 'c9cdd8', 'eef0f6'), taper=3)
    for a in range(-2, 10):
        if a % 3 != 1:
            dot(s, a, 0 if a % 2 == 0 else 1, hexc('9a66e8') if a % 3 else hexc('d8c4ff'))
    hilt(s, [
        '..m.....',
        '...M....',
        '...pL...',
        '..qPlm..',
        '.pqP..M.',
        'LLq.....',
        '.L......',
        '........',
    ], {'m': hexc('4a3570'), 'M': hexc('6b4fa0'), 'p': hexc('291a3e'), 'P': hexc('1c122a'), 'q': hexc('5a3f8a'),
        'L': hexc('c4a8ff'), 'l': hexc('e6dcff')})
    s.put(2, 7, hexc('6b4fa0')); s.put(2, 6, hexc('c4a8ff'))       # crescent tips
    s.put(7, 12, hexc('4a3570')); s.put(8, 12, hexc('c4a8ff'))
    outline(s, 0.36)


# ------------------------------------------------------------------ engineer
@design('gunner_wrenchblade', 'Techniker', 'Schütze', 'Schraubklinge',
        'Messingklinge, deren Spitze ein offener Schraubenschlüssel ist, Stahlzahnrad als '
        'Parier, brauner Ledergriff mit orangem Band wie am Helm.')
def gunner(s):
    blade(s, -5, 8, 1.2, R('6c541b', '9a7827', 'c9a040', 'e8cc7a'), taper=1)
    s.art(['.aab.', 'a...b', 'a...c', '.bcc.'], {'a': hexc('f2dc8a'), 'b': hexc('c9a040'), 'c': hexc('7e6220')},
          ox=11, oy=0)
    s.clear(13, 1)
    s.clear(12, 2)
    s.art(['.a.a.', 'abbba', '.bcb.', 'abbba', '.a.a.'], {'a': hexc('8a909a'), 'b': hexc('a9b0ba'), 'c': hexc('3c4048')},
          ox=1, oy=9)
    grip(s, -13, -10, hexc('553e25'), hexc('3c2b1a'))
    dot(s, -10, 1, hexc('d9742c'))
    dot(s, -11, 0, hexc('d9742c'))
    for (a, d) in ((1, 0), (4, -1), (6, 0)):
        dot(s, a, d, hexc('7e6220'))           # screw heads on the blade
    outline(s, 0.36)


@design('electrician_coilblade', 'Techniker', 'Elektriker', 'Spulenklinge',
        'Kupferstab mit dunklen Drahtwicklungen, an der Spitze ein türkiser Funke wie die '
        'Lampen am Anzug, marineblauer Gummigriff.')
def electrician(s):
    shaft(s, -6, 10, COPPER[1:4])
    for a in range(-4, 10, 3):
        for d in (-1, 1):
            dot(s, a if (a + d) % 2 == 0 else a + 1, d, hexc('2e3846'))
        dot(s, a + 1 if (a + 1) % 2 == 0 else a, 0, COPPER[4])
    s.art(['..a..', '.aba.', 'abcba', '.aba.', '..a..'], {'a': hexc('2fa8c8'), 'b': hexc('66d1e8'), 'c': hexc('ffffff')},
          ox=11, oy=0)
    s.put(15, 0, hexc('b8f4ff'))
    s.put(10, 1, hexc('66d1e8'))
    s.put(14, 5, hexc('66d1e8'))
    guard(s, -7, 2, R('212124', '3a3e48', '5a606c'), thick=0.8)
    grip(s, -12, -9, hexc('212124'), hexc('2f3a48'))
    dot(s, -13, 0, hexc('66d1e8'))
    outline(s, 0.34)


@design('demolition_fusemachete', 'Techniker', 'Sprengmeister', 'Zündermachete',
        'Geschwärzte Machete mit glimmender Schneide, rotes Halstuch um den Griff wie am '
        'Mantel, am Knauf eine Zündschnur mit Funken.')
def demolition(s):
    blade(s, -4, 11, 1.8, R('1b1919', '2b2727', '3e3a3a', '5e5a58'), taper=2, back=hexc('141212'))
    s.field(lambda a, d, x, y: (hexc('ffb050') if a % 6 == 4 else hexc('b8541c')) if -2 <= a <= 9 and d == -2 and a % 2 == 0 else None)
    hilt(s, [
        '..k.....',
        '...K....',
        '...rk...',
        '..RrRK..',
        '.rRr..k.',
        'fbR.rR..',
        'ff..Rr..',
        'Y...r...',
    ], {'k': hexc('3f2d1e'), 'K': hexc('5a4632'), 'r': hexc('c22a2a'), 'R': hexc('8a1a1a'),
        'f': hexc('c9b27a'), 'b': hexc('5e5a58'), 'Y': hexc('ffd34a')})
    s.put(0, 9, hexc('ff7a1a')); s.put(0, 10, hexc('c9b27a')); s.put(1, 8, hexc('ffd34a'))
    outline(s, 0.4)


@design('alchemist_vialknife', 'Techniker', 'Alchemist', 'Phiolenklinge',
        'Schwarze Klinge mit gläserner Giftrinne voller Blasen, Parier wie der Schnabel der '
        'Maske, am Knauf eine grüne und eine rosa Phiole wie am Gürtel.')
def alchemist(s):
    blade(s, -4, 11, 1.6, R('141110', '282220', '3c3432', '5e5450'), taper=3)
    s.field(lambda a, d, x, y: (hexc('c8ff9a') if a % 6 == 2 else hexc('6fe04a') if a % 4 else hexc('3a9a2a'))
            if d == 0 and -2 <= a <= 8 else None)
    hilt(s, [
        '..B.....',
        '...Bb...',
        '...kbk..',
        '..dKkb..',
        '.kdK..b.',
        'vvd.....',
        'gGv.....',
        'GgvpP...',
    ], {'B': hexc('e2d6b4'), 'b': hexc('b8a88a'), 'k': hexc('51433b'), 'K': hexc('1d1817'), 'd': hexc('282220'),
        'v': hexc('cfe8ff'), 'g': hexc('6fe04a'), 'G': hexc('3a9a2a'), 'p': hexc('e070b8'), 'P': hexc('a84a88')})
    outline(s, 0.42)


# ------------------------------------------------------------------ summoner
@design('tamer_packstaff', 'Beschwörer', 'Tierbändiger', 'Rudelstab',
        'Knorriger Holzstab, oben in graues Wolfsfell gewickelt wie der Umhang, '
        'darüber eine Sichel aus Wolfszähnen, Lederriemen und eine Feder.')
def tamer(s):
    shaft(s, -13, 5, R('3e2613', '5c3a1f', '7d5230'))
    s.field(lambda a, d, x, y: hexc('8a5a32') if a in (-9, -4) and d == -1 else None)   # knots
    # wolf fur wrap
    s.field(lambda a, d, x, y: (hexc('9a958f') if (x * 7 + y * 3) % 5 < 2 else hexc('6d6863') if d > 0 else hexc('7d7873'))
            if 0 <= a <= 5 and abs(d) <= 2 else None)
    # fang crescent
    s.art([
        '...aab',
        '..a..b',
        '.a...c',
        'a....c',
        'a...cc',
    ], {'a': BONE[4], 'b': BONE[2], 'c': BONE[1]}, ox=9, oy=0)
    s.put(11, 4, BONE[3])
    s.put(13, 2, BONE[3])                     # inner teeth
    s.put(5, 10, hexc('4a2d19'))
    s.put(6, 11, hexc('4a2d19'))               # strap
    s.put(4, 11, hexc('d8d2c4'))
    s.put(3, 12, hexc('a8a29a'))               # feather
    outline(s, 0.36)


@design('golemsmith_forgestaff', 'Beschwörer', 'Golemschmied', 'Schmiedestab',
        'Stahlstab mit Amboss-Hammerkopf und türkisem Golemkern wie die Rune auf der '
        'Schürze, brauner Lederschaft mit Nieten.')
def golemsmith(s):
    shaft(s, -13, 5, R('33363b', '464951', '6d7279'))
    grip(s, -10, -5, hexc('4e3018'), hexc('6b4423'))
    s.field(lambda a, d, x, y: hexc('a9b0ba') if a in (-11, -4) and d == -1 else None)
    s.art([
        '.aaaaab',
        'acccccb',
        'acdddcb',
        '.cdedc.',
        '..cdc..',
        '..bbb..',
    ], {'a': hexc('d6dce6'), 'b': hexc('464951'), 'c': hexc('8a909a'), 'd': hexc('2f9e95'), 'e': hexc('9ff4ea')},
        ox=9, oy=0)
    s.put(11, 2, hexc('5fd6c8'))
    outline(s, 0.34)


@design('necromancer_bonestaff', 'Beschwörer', 'Totenbeschwörer', 'Knochenstab',
        'Stab aus Wirbelknochen, oben ein Schädel mit violett glühenden Augen und goldener '
        'Zackenkrone wie an der Kapuze, schwarz-violettes Tuch.')
def necromancer(s):
    for x in range(16):
        for y in range(16):
            a, d = x - y, x + y - 14
            if -13 <= a <= 5 and abs(d) <= 1:
                knot = a % 3 == 0
                s.put(x, y, (BONE[4] if d < 0 else BONE[2]) if knot else BONE[3] if d < 0 else BONE[2] if d == 0 else BONE[1])
    s.art([
        '.g.g.g',
        '.aaaaa',
        'abbbbb',
        'bcbbcb',
        'bbbbbb',
        '.bdbd.',
        '..b...',
    ], {'a': BONE[4], 'b': BONE[3], 'c': hexc('b26bff'), 'd': BONE[1], 'g': GOLD[3]}, ox=9, oy=0)
    s.put(10, 3, hexc('e6ccff'))
    # cloth
    for (x, y, h) in ((5, 9, '3a2a52'), (6, 10, '1f182a'), (4, 10, '3a2a52'), (5, 11, '1f182a'), (4, 12, '3a2a52'),
                      (6, 9, '8a4fd8')):
        s.put(x, y, hexc(h))
    outline(s, 0.32)


@design('soulcatcher_lanternstaff', 'Beschwörer', 'Seelenfänger', 'Seelenlaterne',
        'Dunkler Schieferstab mit türkiser Rune wie auf dem Mantel, oben ein Haken mit Kette, '
        'daran eine Laterne mit Seelenflamme.')
def soulcatcher(s):
    shaft(s, -13, 6, R('1b2329', '2d3a44', '46586a'))
    s.field(lambda a, d, x, y: hexc('3fcfc0') if a in (-6, -4) and d == 0 or a == -5 and abs(d) == 1 else None)
    s.art(['..aab', '.a...', '.a...'], {'a': hexc('46586a'), 'b': hexc('7c8a96')}, ox=10, oy=0)
    s.put(14, 1, hexc('7c8a96'))
    s.put(14, 2, hexc('5a6a78'))               # chain
    s.art([
        '..h..',
        '.FfF.',
        'Fcdcf',
        'Fdedf',
        'Fcdcf',
        '.FFF.',
    ], {'h': hexc('9aa8b4'), 'F': hexc('4a5a68'), 'f': hexc('8a9aa8'), 'c': hexc('2fa89a'), 'd': hexc('8ff4ee'),
        'e': hexc('ffffff')}, ox=12, oy=3)
    outline(s, 0.32)


if __name__ == '__main__':
    if '--install' in sys.argv:
        D.install(os.path.join(os.path.dirname(__file__), '..', '..'))
        print('installed')
    os.makedirs(D.OUT, exist_ok=True)
    D.sheet(os.path.join(D.OUT, 'sheet.png'))
    D.states_sheet(os.path.join(D.OUT, 'states.png'))
    print('ok', len(D.DESIGNS))
