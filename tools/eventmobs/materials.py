"""
Materials of the event gear: every face pixel of a gear box gets its colour (and, for
glowing parts, its glow) from the material. {@code tip} runs from 0 at the base of the
box to 1 at its top end (spikes, horns and crystals grow towards -y, the top face).
"""
import random


def clamp(v):
    return max(0, min(255, int(round(v))))


def mix(a, b, t):
    return tuple(clamp(a[k] + (b[k] - a[k]) * t) for k in range(3))


def mul(c, k):
    return tuple(clamp(x * k) for x in c)


def hexc(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


FACE_LIGHT = {'top': 1.12, 'bottom': 0.7, 'front': 1.0, 'back': 0.86, 'right': 0.93, 'left': 0.93}


class Px:
    """What a material decides for one pixel."""

    def __init__(self, face, i, j, fw, fh, tip, rng, edge):
        self.face, self.i, self.j, self.fw, self.fh, self.tip, self.rng, self.edge = face, i, j, fw, fh, tip, rng, edge


def ramp(stops, t):
    t = max(0.0, min(1.0, t))
    for k in range(len(stops) - 1):
        a, b = stops[k], stops[k + 1]
        if t <= b[0]:
            return mix(a[1], b[1], (t - a[0]) / max(1e-6, b[0] - a[0]))
    return stops[-1][1]


def noise(p, amp):
    return 1.0 + (p.rng.random() - 0.5) * 2 * amp


# ---------------------------------------------------------------- the materials
# Each returns (rgb, glow) where glow is an rgb or None.

def bone(p):
    c = ramp([(0, hexc('8f8370')), (0.5, hexc('d9d0b8')), (1, hexc('f2ecdc'))], 0.25 + 0.75 * p.tip)
    if p.rng.random() < 0.10:
        c = mul(c, 0.72)   # cracks
    return mul(c, noise(p, 0.05)), None


def blood_bone(p):
    c, _ = bone(p)
    stain = 1.0 - p.tip
    if p.rng.random() < stain * 0.9:
        c = mix(c, hexc('6e0b0f'), 0.55 + 0.35 * stain)
    return c, None


def blood_crystal(p):
    c = ramp([(0, hexc('3a0507')), (0.6, hexc('a3121a')), (1, hexc('ff5a5a'))], p.tip)
    glow = None
    if p.edge or p.rng.random() < 0.25 + 0.4 * p.tip:
        glow = mix(hexc('7a0a10'), hexc('ff3b3b'), p.tip)
    return mul(c, noise(p, 0.06)), glow


def dark_horn(p):
    c = ramp([(0, hexc('2b2420')), (0.7, hexc('4a3d33')), (1, hexc('7a6a58'))], p.tip)
    if p.j % 2 == 0 and p.face not in ('top', 'bottom'):
        c = mul(c, 0.85)   # ridges
    return mul(c, noise(p, 0.05)), None


def obsidian(p):
    c = ramp([(0, hexc('0d0812')), (0.6, hexc('231536')), (1, hexc('4b2d73'))], p.tip)
    if p.rng.random() < 0.08:
        c = hexc('7d52b8')   # violet glints
    return mul(c, noise(p, 0.05)), None


def gold(p):
    c = ramp([(0, hexc('8a5a12')), (0.5, hexc('e0a92e')), (1, hexc('ffe28a'))], 0.35 + 0.65 * p.tip)
    if p.face == 'top':
        c = mix(c, hexc('fff2c0'), 0.4)
    return mul(c, noise(p, 0.04)), mix(hexc('b97a12'), hexc('ffd65a'), 0.4 + 0.6 * p.tip)


def gold_trim(p):
    """Gold that does not glow (edges of dark things)."""
    c, _ = gold(p)
    return mul(c, 0.85), None


def moss(p):
    c = ramp([(0, hexc('2d3b22')), (0.5, hexc('4d6436')), (1, hexc('7c8f55'))], p.rng.random())
    return c, None


def rag(p):
    c = ramp([(0, hexc('8d8f8e')), (1, hexc('c9cbc6'))], 0.3 + 0.5 * p.tip + (p.rng.random() - 0.5) * 0.3)
    if p.face not in ('top', 'bottom') and p.j >= p.fh - 2 and p.rng.random() < 0.35:
        return None, None   # torn ends
    return c, None


def web(p):
    if (p.i + p.j) % 2 == 1 and p.rng.random() < 0.5:
        return None, None
    return mix(hexc('d8dcdf'), hexc('ffffff'), p.rng.random() * 0.5), None


def iron(p):
    return mul(hexc('3d4043'), noise(p, 0.08)), None


def ghost_light(p):
    c = ramp([(0, hexc('8fa3a8')), (1, hexc('e8fbff'))], 0.5 + 0.5 * p.rng.random())
    return c, mix(hexc('7fb3bf'), hexc('e6fdff'), 0.6)


def copper(p):
    c = ramp([(0, hexc('6b3a22')), (0.5, hexc('b5683f')), (1, hexc('e09a6a'))], 0.3 + 0.5 * p.rng.random())
    if p.rng.random() < 0.12:
        c = hexc('4f9e86')   # verdigris
    return mul(c, FACE_LIGHT.get(p.face, 1.0)), None


def copper_tip(p):
    if p.tip > 0.55:
        c = mix(hexc('9fdcff'), hexc('ffffff'), p.tip)
        return c, mix(hexc('3aa0ff'), hexc('d8f2ff'), p.tip)
    return copper(p)


def coil(p):
    c = mix(hexc('2a6fd6'), hexc('9fd8ff'), p.rng.random() * 0.6)
    return c, mix(hexc('2a7cff'), hexc('b8e6ff'), 0.5)


def basalt(p):
    c = ramp([(0, hexc('1c1a1c')), (1, hexc('3b383a'))], p.rng.random())
    return c, None


def magma(p):
    c = ramp([(0, hexc('2a1208')), (0.5, hexc('8a2e0a')), (1, hexc('ffb347'))], 0.2 + 0.8 * p.tip)
    glow = None
    if p.edge or p.rng.random() < 0.35 + 0.5 * p.tip:
        glow = mix(hexc('c2410c'), hexc('ffcf5a'), p.tip)
    return mul(c, noise(p, 0.05)), glow


def horn_magma(p):
    if p.tip > 0.78:
        return magma(p)
    return dark_horn(p)


def ice(p):
    c = ramp([(0, hexc('6fa8d8')), (0.6, hexc('b9e1ff')), (1, hexc('f3fbff'))], 0.3 + 0.7 * p.tip)
    if p.edge:
        c = mix(c, hexc('ffffff'), 0.5)
    glow = mix(hexc('3d8fd6'), hexc('cfefff'), p.tip) if (p.i + p.j + int(p.tip * 4)) % 3 == 0 else None
    return mul(c, noise(p, 0.04)), glow


def icicle(p):
    """A hanging icicle: its top (the base) is dense white, its lower end clear blue."""
    c = ramp([(0, hexc('7fbbe6')), (1, hexc('e2f4ff'))], p.tip)
    return mul(c, noise(p, 0.04)), None


def snow(p):
    return mix(hexc('e9f2f7'), hexc('ffffff'), p.rng.random() * 0.6), None


def storm_cloud(p):
    up = {'top': 0.35, 'bottom': -0.35}.get(p.face, 0.0)
    c = ramp([(0, hexc('2a3240')), (0.5, hexc('5a6578')), (1, hexc('a4afc0'))], 0.35 + 0.35 * p.rng.random() + up)
    glow = None
    if p.face != 'top' and p.rng.random() < 0.08:
        c = hexc('bfeaff')
        glow = hexc('6cc4ff')
    return c, glow


def bolt(p):
    c = mix(hexc('8fdcff'), hexc('ffffff'), 0.3 + 0.7 * p.rng.random() * (0.5 + 0.5 * p.tip))
    return c, mix(hexc('5ab8ff'), hexc('eaf8ff'), 0.4 + 0.6 * p.tip)


def storm_iron(p):
    if p.face not in ('top', 'bottom') and p.j == p.fh // 2:
        return hexc('9fe4ff'), hexc('4fb0ff')
    return mul(hexc('2c3038'), noise(p, 0.1)), None


MATERIALS = {name: fn for name, fn in globals().items()
             if callable(fn) and name not in ('clamp', 'mix', 'mul', 'hexc', 'ramp', 'noise', 'Px')
             and not name[0].isupper()}
