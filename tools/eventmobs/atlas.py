"""Packs the gear boxes into one texture per event and family and paints them."""
import random
import zlib
from PIL import Image
from geom import rects
import materials

FACES = ('top', 'bottom', 'right', 'front', 'left', 'back')


def footprint(b):
    w, h, d = b.size
    return 2 * (w + d), d + h


def pack(boxes, width=64):
    """Gives every distinct key a texture spot (shelf packing); returns (width, height)."""
    spots = {}
    order = []
    for b in boxes:
        k = (b.key or id(b), b.size, b.mat)
        if k not in spots:
            spots[k] = None
            order.append((k, b))
    order.sort(key=lambda kb: -footprint(kb[1])[1])
    x = y = shelf = 0
    for k, b in order:
        fw, fh = footprint(b)
        if x + fw > width:
            x, y, shelf = 0, y + shelf, 0
        spots[k] = (x, y)
        x += fw
        shelf = max(shelf, fh)
    height = y + shelf
    h = 32
    while h < height:
        h *= 2
    for b in boxes:
        b.u, b.v = spots[(b.key or id(b), b.size, b.mat)]
    return width, h


def paint(boxes, size, seed=7):
    tex = Image.new('RGBA', size, (0, 0, 0, 0))
    glow = Image.new('RGBA', size, (0, 0, 0, 0))
    tp, gp = tex.load(), glow.load()
    done = set()
    for b in boxes:
        if (b.u, b.v) in done:
            continue
        done.add((b.u, b.v))
        fn = materials.MATERIALS[b.mat]
        rng = random.Random(zlib.crc32(f'{b.key}|{b.mat}|{seed}'.encode()))
        w, h, d = b.size
        for face, (ru, rv, rw, rh) in rects(b.u, b.v, w, h, d).items():
            for j in range(rh):
                for i in range(rw):
                    if face in ('top',):
                        tip = 1.0
                    elif face == 'bottom':
                        tip = 0.0
                    else:
                        tip = 1.0 - (j + 0.5) / rh
                    edge = i == 0 or j == 0 or i == rw - 1 or j == rh - 1
                    c, g = fn(materials.Px(face, i, j, rw, rh, tip, rng, edge))
                    if c is None:
                        continue
                    c = materials.mul(c, materials.FACE_LIGHT.get(face, 1.0) * 0.5 + 0.5)
                    tp[ru + i, rv + j] = c + (255,)
                    if g is not None:
                        gp[ru + i, rv + j] = g + (255,)
    return tex, glow
