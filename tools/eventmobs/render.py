"""Software previews of the event monsters: base model, clothes, gear and glow, by day and by night."""
import math
from PIL import Image, ImageDraw
from geom import FAMILIES, OUTER, rects
from gear import rot_matrix, mm, mv

FACES = {
    'top': lambda x0, y0, z0, x1, y1, z1: ((x0, y0, z1), (x1, y0, z1), (x0, y0, z0), (0, -1, 0)),
    'bottom': lambda x0, y0, z0, x1, y1, z1: ((x0, y1, z0), (x1, y1, z0), (x0, y1, z1), (0, 1, 0)),
    'right': lambda x0, y0, z0, x1, y1, z1: ((x0, y0, z1), (x0, y0, z0), (x0, y1, z1), (-1, 0, 0)),
    'front': lambda x0, y0, z0, x1, y1, z1: ((x0, y0, z0), (x1, y0, z0), (x0, y1, z0), (0, 0, -1)),
    'left': lambda x0, y0, z0, x1, y1, z1: ((x1, y0, z0), (x1, y0, z1), (x1, y1, z0), (1, 0, 0)),
    'back': lambda x0, y0, z0, x1, y1, z1: ((x1, y0, z1), (x0, y0, z1), (x1, y1, z1), (0, 0, 1)),
}


class Xf:
    def __init__(self, r=None, t=(0, 0, 0)):
        self.r = r or [[1, 0, 0], [0, 1, 0], [0, 0, 1]]
        self.t = list(t)

    def then(self, pivot, rot):
        r = rot_matrix(*rot)
        p = mv(self.r, pivot)
        return Xf(mm(self.r, r), [self.t[i] + p[i] for i in range(3)])

    def apply(self, p):
        q = mv(self.r, p)
        return [q[i] + self.t[i] for i in range(3)]


class Scene:
    def __init__(self):
        self.quads = []

    def cube(self, img, glow, xf, pos, size, uv, grow=0.0, mirror=False):
        x, y, z = pos
        w, h, d = size
        x0, y0, z0 = x - grow, y - grow, z - grow
        x1, y1, z1 = x + w + grow, y + h + grow, z + d + grow
        px = img.load()
        gx = glow.load() if glow is not None else None
        for face, rect in rects(uv[0], uv[1], w, h, d).items():
            tl, tr, bl, n = FACES[face](x0, y0, z0, x1, y1, z1)
            if mirror:
                fl = lambda p: (x0 + x1 - p[0], p[1], p[2])
                tl, tr, bl = fl(tl), fl(tr), fl(bl)
                n = (-n[0], n[1], n[2])
            ru, rv, rw, rh = rect
            wn = mv(xf.r, n)
            for j in range(rh):
                for i in range(rw):
                    tx, ty = ru + i, rv + j
                    if tx >= img.width or ty >= img.height:
                        continue
                    c = px[tx, ty]
                    if c[3] < 128:
                        continue
                    g = gx[tx, ty] if gx is not None else (0, 0, 0, 0)

                    def at(a, b):
                        return [tl[k] + (tr[k] - tl[k]) * a + (bl[k] - tl[k]) * b for k in range(3)]
                    corners = [at(i / rw, j / rh), at((i + 1) / rw, j / rh), at((i + 1) / rw, (j + 1) / rh), at(i / rw, (j + 1) / rh)]
                    self.quads.append(([xf.apply(p) for p in corners], wn, c, g if g[3] > 0 else None))

    def render(self, yaw, pitch, scale, night=False, size=(34, 36), origin=(0.5, 0.2)):
        view = mm(rot_matrix(math.radians(pitch), 0, 0), rot_matrix(0, math.radians(yaw), 0))
        items = []
        for corners, n, c, g in self.quads:
            vn = mv(view, n)
            if vn[2] >= -1e-6:
                continue
            pts = [mv(view, p) for p in corners]
            depth = sum(p[2] for p in pts) / 4
            shade = 0.62 + 0.38 * max(0.0, -vn[2] * 0.6 - vn[1] * 0.5 + 0.15 * vn[0])
            if night:
                shade *= 0.32
            col = [min(255, int(c[k] * shade)) for k in range(3)]
            if g is not None:
                col = [min(255, max(col[k], g[k])) for k in range(3)] if night else \
                      [min(255, int(col[k] * 0.6 + g[k] * 0.5)) for k in range(3)]
            items.append((depth, pts, tuple(col) + (255,)))
        items.sort(key=lambda it: -it[0])
        W, H = int(size[0] * scale), int(size[1] * scale)
        img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        dr = ImageDraw.Draw(img)
        ox, oy = W * origin[0], H * origin[1]
        for depth, pts, col in items:
            dr.polygon([(ox + p[0] * scale, oy + p[1] * scale) for p in pts], fill=col)
        return img


def mob_scene(family, skin, skin_glow=None, outer=None, gear=(), gear_tex=None, gear_glow=None):
    fam = FAMILIES[family]
    sc = Scene()
    parts = {}
    for name, (pivot, rot, cubes) in fam['bones'].items():
        r = fam['pose'].get(name, rot)
        parts[name] = Xf().then(pivot, r)
    parts['hat'] = parts.get('head')
    for name, (pivot, rot, cubes) in fam['bones'].items():
        for pos, size, uv, mir in cubes:
            sc.cube(skin, skin_glow, parts[name], pos, size, uv, 0.0, mir)
    if outer is not None:
        for name, cubes in OUTER.get(family, {}).items():
            for pos, size, uv, grow in cubes:
                sc.cube(outer, None, parts[name], pos, size, uv, grow, name.startswith('left'))
    for b in gear:
        xf = parts[b.bone].then(b.pivot, b.rot)
        sc.cube(gear_tex, gear_glow, xf, b.pos, b.size, (b.u, b.v), 0.0, b.mirror)
    return sc
