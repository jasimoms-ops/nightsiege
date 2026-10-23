"""
Small pixel-painting kit for the class outfits.

An outfit is painted onto two 64x32 equipment textures, the same layout as
vanilla armour: layer 1 ("humanoid": helmet, chestplate, boots) and layer 2
("humanoid_leggings": leggings). Every box of the model (head, hat, body, arm,
leg) is addressed by its faces; the four sides of a box lie next to each other
in the texture, so a band painted along the "strip" runs all the way round.
"""
import random
from PIL import Image

# u, v, width, height, depth of every box (texture offsets of the armour model)
PARTS = {
    'head': (0, 0, 8, 8, 8),
    'hat': (32, 0, 8, 8, 8),
    'body': (16, 16, 8, 12, 4),
    'arm': (40, 16, 4, 12, 4),
    'leg': (0, 16, 4, 12, 4),
}
SIDES = ('right', 'front', 'left', 'back')


def clamp(v):
    return max(0, min(255, int(round(v))))


def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mul(c, k):
    return (clamp(c[0] * k), clamp(c[1] * k), clamp(c[2] * k), c[3])


def mix(a, b, t):
    return tuple(clamp(a[i] * (1 - t) + b[i] * t) for i in range(3)) + (255,)


class Ctx:
    """Where a pixel sits: its box, face, place on the face and on the strip."""
    __slots__ = ('part', 'face', 'fx', 'fy', 'fw', 'fh', 's', 'r', 'sw', 'rnd')


class Tex:
    def __init__(self, seed, size=(64, 32)):
        self.img = Image.new('RGBA', size, (0, 0, 0, 0))
        self.px = self.img.load()
        self.rnd = random.Random(seed)
        self.parts = dict(PARTS)
        self.w, self.h = size

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[x, y] = c

    def get(self, x, y):
        return self.px[x, y]

    # ------------------------------------------------------------------ faces

    def face_rect(self, part, face):
        u, v, w, h, d = self.parts[part]
        return {
            'top': (u + d, v, w, d),
            'bottom': (u + d + w, v, w, d),
            'right': (u, v + d, d, h),
            'front': (u + d, v + d, w, h),
            'left': (u + d + w, v + d, d, h),
            'back': (u + 2 * d + w, v + d, w, h),
        }[face]

    def _paint(self, part, face, fx, fy, s, r, sw, mat, where):
        x0, y0, fw, fh = self.face_rect(part, face)
        c = Ctx()
        c.part, c.face, c.fx, c.fy, c.fw, c.fh, c.s, c.r, c.sw, c.rnd = part, face, fx, fy, fw, fh, s, r, sw, self.rnd
        if where is not None and not where(c):
            return
        col = mat(c) if callable(mat) else mat
        if col is None:
            return
        if col == 'clear':
            self.set(x0 + fx, y0 + fy, (0, 0, 0, 0))
        else:
            self.set(x0 + fx, y0 + fy, col)

    def strip(self, part, mat, rows=None, faces=SIDES, where=None):
        """Paints the four sides of a box; rows are counted from its top."""
        u, v, w, h, d = self.parts[part]
        widths = {'right': d, 'front': w, 'left': d, 'back': w}
        sw = 2 * (w + d)
        s0 = 0
        for face in SIDES:
            fw = widths[face]
            if face in faces:
                for fx in range(fw):
                    for r in range(h):
                        if rows is not None and not (rows[0] <= r <= rows[1]):
                            continue
                        self._paint(part, face, fx, r, s0 + fx, r, sw, mat, where)
            s0 += fw

    def cap(self, part, mat, face='top', where=None):
        x0, y0, fw, fh = self.face_rect(part, face)
        for fx in range(fw):
            for fy in range(fh):
                self._paint(part, face, fx, fy, fx, fy, fw, mat, where)

    def art(self, part, face, lines, pal, x=0, y=0):
        """Hand-drawn pixels: one string per row; '.' keeps what is there, '_' erases."""
        x0, y0, fw, fh = self.face_rect(part, face)
        for j, line in enumerate(lines):
            for i, ch in enumerate(line):
                if ch in '. ':
                    continue
                fx, fy = x + i, y + j
                if not (0 <= fx < fw and 0 <= fy < fh):
                    continue
                if ch == '_':
                    self.set(x0 + fx, y0 + fy, (0, 0, 0, 0))
                    continue
                col = pal[ch]
                if callable(col):
                    c = Ctx()
                    c.part, c.face, c.fx, c.fy, c.fw, c.fh, c.s, c.r, c.sw, c.rnd = part, face, fx, fy, fw, fh, fx, fy, fw, self.rnd
                    col = col(c)
                self.set(x0 + fx, y0 + fy, col)

    def mirror_art(self, part, face, lines, pal, x=0, y=0):
        """The same drawing on a face, flipped left to right (for the back or the other side)."""
        self.art(part, face, [l[::-1] for l in lines], pal, x, y)

    def save(self, path):
        self.img.save(path)


# ---------------------------------------------------------------------- materials

def cloth(base, var=0.06, weave=0.03):
    """Wool or linen: a little noise and a faint weave."""
    def m(c):
        k = 1 + c.rnd.uniform(-var, var) + (weave if (c.s + c.r) % 2 == 0 else -weave)
        return mul(base, k)
    return m


def shaded(base, top=1.08, bottom=0.82, var=0.05):
    """Cloth that is lighter at the top of a box and darker towards its hem."""
    def m(c):
        t = c.r / max(1, (c.fh - 1))
        k = top + (bottom - top) * t + c.rnd.uniform(-var, var)
        return mul(base, k)
    return m


def quilt(base, line=0.78, every=2, var=0.04):
    """A quilted gambeson: dark seams in a regular grid of puffs."""
    def m(c):
        k = 1 + c.rnd.uniform(-var, var)
        if c.s % every == 0:
            k *= line
        elif c.r % 3 == 2:
            k *= 0.9
        else:
            k *= 1.06
        return mul(base, k)
    return m


def mail(base):
    """Riveted chainmail: small rings in staggered rows, light on top, dark below."""
    hi = mul(base, 1.16)
    lo = mul(base, 0.74)
    def m(c):
        x = (c.s + (c.r // 2) % 2) % 2
        y = c.r % 2
        col = hi if (x, y) == (0, 0) else lo if (x, y) == (1, 1) else base
        return mul(col, 1 + c.rnd.uniform(-0.04, 0.04))
    return m


def fur(base, light, dark, tip=None):
    """Shaggy fur: long strands running down, a few light tips."""
    def m(c):
        strand = (c.s * 7 + 3) % 5
        drift = (c.r + (c.s * 3) % 4) // 3
        tone = (strand + drift) % 5
        col = dark if tone == 0 else light if tone == 3 else base
        if tip is not None and c.rnd.random() < 0.05:
            col = tip
        return mul(col, 1 + c.rnd.uniform(-0.05, 0.05))
    return m


def leather(base, var=0.07):
    def m(c):
        k = 1 + c.rnd.uniform(-var, var)
        if c.rnd.random() < 0.06:
            k *= 1.18
        return mul(base, k)
    return m


def metal(base, var=0.04):
    """Plate or helm: bright at the top, darker below, a glint now and then."""
    def m(c):
        t = c.r / max(1, (c.fh - 1))
        k = 1.18 - 0.36 * t + c.rnd.uniform(-var, var)
        if c.rnd.random() < 0.05:
            k += 0.25
        return mul(base, k)
    return m


def stripes(a, b, width=1, vertical=True):
    def m(c):
        i = (c.s if vertical else c.r) // width
        return a(c) if i % 2 == 0 else b(c)
    return m


def solid(col):
    return lambda c: col


def lozenge(fg, bg, ground=None):
    """A tablet-woven band: small diamonds on a ground colour (three rows tall)."""
    def m(c):
        k = c.s % 4
        row = c.r % 3
        on = (row == 1 and k in (0, 2)) or (row != 1 and k == 1)
        return fg if on else bg
    return m


def rows(*spec):
    """Picks a material by strip row: rows((0,2,matA),(3,11,matB))."""
    def m(c):
        for lo, hi, mat in spec:
            if lo <= c.r <= hi:
                return mat(c) if callable(mat) else mat
        return None
    return m


# ---------------------------------------------------------------------- shapes

def front_only(c):
    return c.face == 'front'


def back_only(c):
    return c.face == 'back'


def face_open(c, x1=1, y1=2, x2=6, y2=7):
    """True for the part of the head's front where the face looks out."""
    return c.face == 'front' and x1 <= c.fx <= x2 and y1 <= c.fy <= y2


def jagged(c, base_row, rnd_amp=1):
    """A ragged lower edge (fur, torn cloth): true above a row that wobbles."""
    return c.r <= base_row + ((c.s * 5) % 3) - 1


def outline_hem(tex, part, row, k=0.7, faces=SIDES):
    """Darkens one row of the strip, as a hem or the edge of a garment."""
    u, v, w, h, d = tex.parts[part]
    widths = {'right': d, 'front': w, 'left': d, 'back': w}
    for face in faces:
        x0, y0, fw, fh = tex.face_rect(part, face)
        for fx in range(fw):
            c = tex.get(x0 + fx, y0 + row)
            if c[3] > 0:
                tex.set(x0 + fx, y0 + row, mul(c, k))
