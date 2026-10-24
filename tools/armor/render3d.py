"""
A small software renderer for previews: draws the player model with the armour
layers and the extra boxes from two angles, the way Minecraft maps the textures.
"""
import math
from PIL import Image, ImageDraw

# Model parts of a standing player: pivot and base cube (local), texture spot of the base cube.
LIMB_PIVOT = {'head': (0, 0, 0), 'body': (0, 0, 0), 'right_arm': (-5, 2, 0), 'left_arm': (5, 2, 0),
              'right_leg': (-1.9, 12, 0), 'left_leg': (1.9, 12, 0)}
BASE = {
    'head': ((-4, -8, -4), (8, 8, 8), (0, 0)),
    'hat': ((-4, -8, -4), (8, 8, 8), (32, 0)),
    'body': ((-4, 0, -2), (8, 12, 4), (16, 16)),
    'right_arm': ((-3, -2, -2), (4, 12, 4), (40, 16)),
    'left_arm': ((-1, -2, -2), (4, 12, 4), (40, 16)),
    'right_leg': ((-2, 0, -2), (4, 12, 4), (0, 16)),
    'left_leg': ((-2, 0, -2), (4, 12, 4), (0, 16)),
}


def rot_matrix(rx, ry, rz):
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    Rx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    Ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    Rz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    return mm(Rz, mm(Ry, Rx))


def mm(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def mv(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


class Xf:
    """Rotation then translation."""

    def __init__(self, r=None, t=(0, 0, 0)):
        self.r = r or [[1, 0, 0], [0, 1, 0], [0, 0, 1]]
        self.t = list(t)

    def then_local(self, pivot, rot):
        r = rot_matrix(*rot)
        return Xf(mm(self.r, r), [self.t[i] + mv(self.r, pivot)[i] for i in range(3)])

    def apply(self, p):
        q = mv(self.r, p)
        return [q[i] + self.t[i] for i in range(3)]

    def normal(self, n):
        return mv(self.r, n)


FACES = {
    # face: (rect index), corners TL, TR, BL as functions of the box, outward normal
    'top': lambda x0, y0, z0, x1, y1, z1: ((x0, y0, z1), (x1, y0, z1), (x0, y0, z0), (0, -1, 0)),
    'bottom': lambda x0, y0, z0, x1, y1, z1: ((x0, y1, z0), (x1, y1, z0), (x0, y1, z1), (0, 1, 0)),
    'right': lambda x0, y0, z0, x1, y1, z1: ((x0, y0, z1), (x0, y0, z0), (x0, y1, z1), (-1, 0, 0)),
    'front': lambda x0, y0, z0, x1, y1, z1: ((x0, y0, z0), (x1, y0, z0), (x0, y1, z0), (0, 0, -1)),
    'left': lambda x0, y0, z0, x1, y1, z1: ((x1, y0, z0), (x1, y0, z1), (x1, y1, z0), (1, 0, 0)),
    'back': lambda x0, y0, z0, x1, y1, z1: ((x1, y0, z1), (x0, y0, z1), (x1, y1, z1), (0, 0, 1)),
}


def rects(u, v, w, h, d):
    return {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'right': (u, v + d, d, h),
            'front': (u + d, v + d, w, h), 'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h)}


class Scene:
    def __init__(self):
        self.quads = []

    def cube(self, img, xf, pos, size, uv, grow=0.0, mirror=False):
        x, y, z = pos
        w, h, d = size
        x0, y0, z0 = x - grow, y - grow, z - grow
        x1, y1, z1 = x + w + grow, y + h + grow, z + d + grow
        px = img.load()
        for face, rect in rects(uv[0], uv[1], w, h, d).items():
            tl, tr, bl, n = FACES[face](x0, y0, z0, x1, y1, z1)
            if mirror:
                flip = lambda p: (x0 + x1 - p[0], p[1], p[2])
                tl, tr, bl = flip(tl), flip(tr), flip(bl)
                n = (-n[0], n[1], n[2])
            ru, rv, rw, rh = rect
            if rw <= 0 or rh <= 0:
                continue
            wn = xf.normal(n)
            for j in range(rh):
                for i in range(rw):
                    tx, ty = ru + i, rv + j
                    if tx >= img.width or ty >= img.height:
                        continue
                    c = px[tx, ty]
                    if c[3] < 128:
                        continue

                    def at(a, b):
                        return [tl[k] + (tr[k] - tl[k]) * a + (bl[k] - tl[k]) * b for k in range(3)]
                    corners = [at(i / rw, j / rh), at((i + 1) / rw, j / rh), at((i + 1) / rw, (j + 1) / rh), at(i / rw, (j + 1) / rh)]
                    self.quads.append(([xf.apply(p) for p in corners], wn, c))

    def render(self, yaw, pitch, scale, light=True):
        ry = rot_matrix(0, math.radians(yaw), 0)
        rx = rot_matrix(math.radians(pitch), 0, 0)
        view = mm(rx, ry)
        items = []
        for corners, n, c in self.quads:
            vn = mv(view, n)
            if vn[2] >= -1e-6:
                continue   # facing away
            pts = [mv(view, p) for p in corners]
            depth = sum(p[2] for p in pts) / 4
            shade = 1.0
            if light:
                shade = 0.62 + 0.38 * max(0.0, -vn[2] * 0.6 - vn[1] * 0.5 + 0.15 * vn[0])
            col = tuple(min(255, int(c[k] * shade)) for k in range(3)) + (255,)
            items.append((depth, pts, col))
        items.sort(key=lambda it: -it[0])
        W, H = int(30 * scale), int(50 * scale)
        img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        dr = ImageDraw.Draw(img)
        ox, oy = W / 2, H * 0.42
        for depth, pts, col in items:
            dr.polygon([(ox + p[0] * scale, oy + p[1] * scale) for p in pts], fill=col)
        return img


def player_scene(skin, l1, l2, boxes, slots=('head', 'chest', 'legs', 'feet'), cape_lean=6.0, head=(0, 0, 0)):
    """Builds the scene: skin, layer 2 (leggings), layer 1, extra boxes. {@code head} turns
    the head (pitch, yaw in degrees; pitch > 0 looks down) to show where pieces cut into each other."""
    sc = Scene()
    parts = {name: Xf().then_local(LIMB_PIVOT.get(name, (0, 0, 0)), (0, 0, 0)) for name in LIMB_PIVOT}
    parts['head'] = Xf().then_local((0, 0, 0), (math.radians(head[0]), math.radians(head[1]), 0))
    parts['hat'] = parts['head']
    for name in ('head', 'body', 'right_arm', 'left_arm', 'right_leg', 'left_leg'):
        pos, size, uv = BASE[name]
        sc.cube(skin, parts[name], pos, size, uv, 0.0, mirror=name.startswith('left'))
    if 'legs' in slots:
        for name in ('body', 'right_leg', 'left_leg'):
            pos, size, uv = BASE[name]
            sc.cube(l2, parts[name], pos, size, uv, 0.5, mirror=name.startswith('left'))
    layer1 = []
    if 'head' in slots:
        layer1 += [('head', 1.0), ('hat', 1.5)]
    if 'chest' in slots:
        layer1 += [('body', 1.0), ('right_arm', 1.0), ('left_arm', 1.0)]
    if 'feet' in slots:
        layer1 += [('right_leg', 1.0), ('left_leg', 1.0)]
    for name, grow in layer1:
        pos, size, uv = BASE[name]
        sc.cube(l1, parts[name], pos, size, uv, grow, mirror=name.startswith('left'))
    for b in boxes:
        if b.slot not in slots:
            continue
        tex = l2 if b.slot == 'legs' else l1
        rot = list(b.rot)
        if b.cape:
            rot[0] += math.radians(cape_lean)
        xf = parts[b.part].then_local(b.pivot, rot)
        sc.cube(tex, xf, b.pos, b.size, (b.u, b.v), b.grow, b.mirror)
    return sc


def sheet(title, skin, l1, l2, boxes, out, scale=11):
    from PIL import ImageDraw as D
    views = []
    full = player_scene(skin, l1, l2, boxes)
    views.append(full.render(-28, 12, scale))
    views.append(full.render(152, 12, scale))
    views.append(full.render(90, 8, scale))
    # The head turned and bent: pieces of head and body must not cut into each other.
    turned = []
    for head, yaw, pitch in (((25, 45), -20, 10), ((40, 0), 90, 5), ((-25, -45), -20, 10), ((10, 60), 160, 10)):
        turned.append(player_scene(skin, l1, l2, boxes, head=head).render(yaw, pitch, scale * 0.75))
    w = sum(v.width for v in views) + 20 * (len(views) + 1)
    w = max(w, sum(v.width for v in turned) + 20 * (len(turned) + 1))
    h = max(v.height for v in views) + 40 + max(v.height for v in turned) + 10
    img = Image.new('RGBA', (w, h), (43, 47, 56, 255))
    x = 20
    for v in views:
        img.alpha_composite(v, (x, 30))
        x += v.width + 20
    x = 20
    y2 = 30 + max(v.height for v in views) + 10
    for v in turned:
        img.alpha_composite(v, (x, y2))
        x += v.width + 20
    D.Draw(img).text((20, 8), title, fill=(235, 225, 200, 255))
    img.save(out)
    return img
