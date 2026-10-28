"""
The grappler's hook (Simon, 27 October 2026: "please design a grappling hook"): a three-clawed
iron grapnel on a ring with a hemp rope, 32x32 like the kit weapons, finished with their bevel,
grain and tinted outline (kit_v3.finish). Handle (rope) bottom left, claws top right.

python3 tools/weapons/grapnel.py [--install]
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import kit_v3 as K  # noqa: E402

N = 32
IRON = (126, 131, 143, 255)
IRON_DARK = (84, 88, 99, 255)
IRON_LIGHT = (176, 181, 192, 255)
ROPE = (160, 118, 66, 255)
ROPE_DARK = (112, 78, 40, 255)


def bezier(p0, p1, p2, t):
    a = (1 - t) * (1 - t)
    b = 2 * (1 - t) * t
    c = t * t
    return (a * p0[0] + b * p1[0] + c * p2[0], a * p0[1] + b * p1[1] + c * p2[1])


def stroke(px, p0, p1, p2, w0, w1, color_fn, steps=80):
    """A curved stroke whose width runs from w0 to w1; color_fn(t, side) picks the colour
    (side < 0: the lit top-left side of the stroke)."""
    pts = [bezier(p0, p1, p2, i / steps) for i in range(steps + 1)]
    for x in range(N):
        for y in range(N):
            cx, cy = x + 0.5, y + 0.5
            best = None
            for i in range(steps):
                (ax, ay), (bx, by) = pts[i], pts[i + 1]
                dx, dy = bx - ax, by - ay
                L2 = dx * dx + dy * dy or 1e-9
                u = max(0.0, min(1.0, ((cx - ax) * dx + (cy - ay) * dy) / L2))
                qx, qy = ax + u * dx, ay + u * dy
                dist = math.hypot(cx - qx, cy - qy)
                t = (i + u) / steps
                w = w0 + (w1 - w0) * t
                if dist <= w and (best is None or dist < best[0]):
                    # which side: normal pointing to the top left is lit
                    nx, ny = -dy, dx
                    side = (cx - qx) * nx + (cy - qy) * ny
                    if nx + ny > 0:
                        side = -side
                    best = (dist, t, side / max(w, 1e-6))
            if best is not None:
                c = color_fn(best[1], best[2])
                if c is not None:
                    px[(x, y)] = c


def iron(t, side):
    if side < -0.35:
        return IRON_LIGHT
    if side > 0.45:
        return IRON_DARK
    return IRON


def rope(t, side):
    # a twisted rope: dark strands every few steps along it
    k = int(t * 26 + side * 1.6) % 3
    if k == 0:
        return ROPE_DARK
    return ROPE if side > -0.3 else K.lighten(ROPE, 0.18)


def draw():
    px = {}
    # The ring the rope is tied to (open in the middle).
    for x in range(N):
        for y in range(N):
            d = math.hypot(x + 0.5 - 11.2, y + 0.5 - 20.8)
            if 1.2 <= d <= 2.6:
                ang = math.atan2(y + 0.5 - 20.8, x + 0.5 - 11.2)
                px[(x, y)] = IRON_LIGHT if -2.6 < ang < -0.6 else IRON_DARK if 0.6 < ang < 2.6 else IRON
    # The rope: from the bottom left corner to the ring, with a loop of slack.
    stroke(px, (0.6, 31.4), (2.0, 25.0), (8.4, 23.4), 1.55, 1.3, rope)
    # The shank.
    stroke(px, (13.6, 18.4), (17.6, 14.4), (21.6, 10.4), 1.5, 1.6, iron)
    # The crown where the claws meet.
    for x in range(N):
        for y in range(N):
            d = math.hypot(x + 0.5 - 22.4, y + 0.5 - 9.6)
            if d <= 2.4:
                px[(x, y)] = IRON_LIGHT if (x + 0.5 - 22.4) + (y + 0.5 - 9.6) < -1.0 else IRON
    # Three thick claws curling back towards the rope, sharp at the end.
    stroke(px, (21.0, 8.0), (14.5, 3.0), (12.8, 8.8), 1.8, 0.7, iron)      # left claw
    stroke(px, (24.0, 11.0), (29.0, 17.5), (23.2, 19.2), 1.8, 0.7, iron)   # right claw
    stroke(px, (24.0, 8.0), (29.2, 4.2), (26.6, 1.8), 1.6, 0.6, iron)      # front claw
    return px


WRAP = (52, 96, 196, 255)
WRAP_LIGHT = (96, 140, 230, 255)


def draw_kit():
    """The kit's own hook (Simon 28.10.): the same grapnel with a blue wrap on the shank, so the own
    tool and the enemy's are told apart."""
    px = draw()
    for (x, y), c in list(px.items()):
        # the shank runs from (13.6, 18.4) to (21.6, 10.4): wrap its middle part in bands
        u = ((x + 0.5 - 13.6) - (y + 0.5 - 18.4)) / 16.0
        side = abs((x + 0.5 - 13.6) + (y + 0.5 - 18.4)) / 2.0
        if 0.2 <= u <= 0.75 and side < 2.2 and c in (IRON, IRON_LIGHT, IRON_DARK):
            px[(x, y)] = WRAP_LIGHT if int(u * 20) % 3 == 0 else WRAP
    return px


def picture():
    return K.finish(draw(), 23)


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
    save(px, os.path.join(out, 'grappling_hook.png'))
    save(px, os.path.join(out, 'grappling_hook_big.png'), 12)
    if '--install' in sys.argv:
        root = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'nightsiege')
        tex = os.path.join(root, 'textures', 'item', 'foe')
        os.makedirs(tex, exist_ok=True)
        save(px, os.path.join(tex, 'grappling_hook.png'))
        models = os.path.join(root, 'models', 'item', 'foe')
        items = os.path.join(root, 'items', 'foe')
        os.makedirs(models, exist_ok=True)
        os.makedirs(items, exist_ok=True)
        with open(os.path.join(models, 'grappling_hook.json'), 'w') as f:
            f.write('{\n  "parent": "minecraft:item/handheld",\n  "textures": {\n    "layer0": "nightsiege:item/foe/grappling_hook"\n  }\n}\n')
        with open(os.path.join(items, 'grappling_hook.json'), 'w') as f:
            f.write('{\n  "model": {\n    "type": "minecraft:model",\n    "model": "nightsiege:item/foe/grappling_hook"\n  }\n}\n')
        kit = K.finish(draw_kit(), 23)
        save(kit, os.path.join(out, 'grappling_hook_kit_big.png'), 12)
        os.makedirs(os.path.join(root, 'textures', 'item', 'kit'), exist_ok=True)
        save(kit, os.path.join(root, 'textures', 'item', 'kit', 'grappling_hook.png'))
        os.makedirs(os.path.join(root, 'models', 'item', 'kit'), exist_ok=True)
        os.makedirs(os.path.join(root, 'items', 'kit'), exist_ok=True)
        with open(os.path.join(root, 'models', 'item', 'kit', 'grappling_hook.json'), 'w') as f:
            f.write('{\n  "parent": "minecraft:item/handheld",\n  "textures": {\n    "layer0": "nightsiege:item/kit/grappling_hook"\n  }\n}\n')
        with open(os.path.join(root, 'items', 'kit', 'grappling_hook.json'), 'w') as f:
            f.write('{\n  "model": {\n    "type": "minecraft:model",\n    "model": "nightsiege:item/kit/grappling_hook"\n  }\n}\n')
        print('installed')
