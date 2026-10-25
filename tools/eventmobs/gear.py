"""
The 3D gear of the event monsters: horns, spikes, crystals, halos, lightning rods,
lanterns and moss that sit on the bones of the vanilla models. Every event has its own
set per model family (humanoid = zombie/husk/drowned, skeleton = skeleton/stray/bogged/
wither skeleton, creeper, spider).
"""
import math
import random
from dataclasses import dataclass, field, replace

R = math.radians


@dataclass
class Box:
    bone: str
    pos: tuple          # cube corner relative to the pivot
    size: tuple         # w, h, d (whole pixels)
    pivot: tuple        # in the bone's space
    rot: tuple          # x, y, z (radians, applied Z then Y then X like Minecraft)
    mat: str
    mirror: bool = False
    key: str = ''       # boxes with the same key share their texture spot
    u: int = 0
    v: int = 0


# ------------------------------------------------------------------ rotation helpers

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


def euler(m):
    ry = math.asin(max(-1.0, min(1.0, -m[2][0])))
    rx = math.atan2(m[2][1], m[2][2])
    rz = math.atan2(m[1][0], m[0][0])
    return (rx, ry, rz)


# ------------------------------------------------------------------ shape helpers

def chain(bone, base, rot, segs, mat, bend=(0, 0, 0), name='', mats=None):
    """A horn or spike: segments (w, h, d) growing from {@code base} along -y, each turned a
    little more by {@code bend}. Returns the boxes."""
    out = []
    m = rot_matrix(*rot)
    b = rot_matrix(*bend)
    p = list(base)
    for k, (w, h, d) in enumerate(segs):
        r = euler(m)
        out.append(Box(bone, (-w / 2, -h, -d / 2), (w, h, d), tuple(round(x, 3) for x in p),
                       tuple(round(x, 4) for x in r), (mats[k] if mats else mat), key=f'{name}{k}'))
        step = mv(m, [0, -(h - 0.6), 0])
        p = [p[i] + step[i] for i in range(3)]
        m = mm(m, b)
    return out


def block(bone, pos, size, mat, pivot=(0, 0, 0), rot=(0, 0, 0), key=''):
    return Box(bone, tuple(pos), tuple(size), tuple(pivot), tuple(rot), mat, key=key)


def mirror(boxes):
    """The same boxes on the other side (right ↔ left, x flipped)."""
    out = []
    for b in boxes:
        bone = b.bone.replace('right_', '§').replace('left_', 'right_').replace('§', 'left_')
        x, y, z = b.pos
        w = b.size[0]
        out.append(replace(b, bone=bone, pos=(-(x + w), y, z), pivot=(-b.pivot[0], b.pivot[1], b.pivot[2]),
                           rot=(b.rot[0], -b.rot[1], -b.rot[2]), mirror=not b.mirror))
    return out


def sym(boxes):
    return boxes + mirror(boxes)


def ring(bone, center, radius, segments, seg_size, mat, plane='xy', spikes=0, spike_mat=None, name='ring'):
    """A halo: short boxes around a centre (plane xy faces forward, xz lies flat)."""
    out = []
    w, h, d = seg_size
    for k in range(segments):
        a = 2 * math.pi * k / segments
        if plane == 'xy':
            p = (center[0] + math.cos(a) * radius, center[1] + math.sin(a) * radius, center[2])
            rot = (0, 0, a + math.pi / 2)
        else:
            p = (center[0] + math.cos(a) * radius, center[1], center[2] + math.sin(a) * radius)
            rot = (0, -(a + math.pi / 2), 0)
        out.append(Box(bone, (-w / 2, -h / 2, -d / 2), (w, h, d), tuple(round(x, 3) for x in p),
                       tuple(round(x, 4) for x in rot), mat, key=name))
        if spikes and k % spikes == 0:
            if plane == 'xy':
                q = (center[0] + math.cos(a) * (radius + 0.6), center[1] + math.sin(a) * (radius + 0.6), center[2])
                srot = (0, 0, a + math.pi / 2)
            else:
                q = (center[0] + math.cos(a) * (radius + 0.6), center[1], center[2] + math.sin(a) * (radius + 0.6))
                srot = (R(90), -(a + math.pi / 2) , 0)
            out.append(Box(bone, (-0.5, -3, -0.5), (1, 3, 1), tuple(round(x, 3) for x in q),
                           tuple(round(x, 4) for x in srot), spike_mat or mat, key=name + 's'))
    return out


def cluster(bone, base, rot, mat, sizes=((2, 5, 2), (1, 3, 1), (1, 4, 1)), spread=1.2, tilt=0.45, name='cl'):
    """A cluster of crystals growing from one spot."""
    out = []
    for k, (w, h, d) in enumerate(sizes):
        a = 2 * math.pi * k / max(1, len(sizes)) + 0.6
        off = (math.cos(a) * spread * (k > 0), 0, math.sin(a) * spread * (k > 0))
        m = rot_matrix(*rot)
        local = mv(m, off)
        p = (base[0] + local[0], base[1] + local[1], base[2] + local[2])
        t = rot_matrix(math.sin(a) * tilt * (k > 0), 0, -math.cos(a) * tilt * (k > 0))
        r = euler(mm(m, t))
        out.append(Box(bone, (-w / 2, -h, -d / 2), (w, h, d), tuple(round(x, 3) for x in p),
                       tuple(round(x, 4) for x in r), mat, key=f'{name}{k}'))
    return out


def zigzag(bone, base, rot, segs, mat, angle=0.7, name='zz'):
    """A lightning bolt: segments that turn left and right in turn."""
    out = []
    m = rot_matrix(*rot)
    p = list(base)
    for k, (w, h, d) in enumerate(segs):
        turn = rot_matrix(0, 0, angle if k % 2 == 0 else -angle)
        mk = mm(m, turn)
        out.append(Box(bone, (-w / 2, -h, -d / 2), (w, h, d), tuple(round(x, 3) for x in p),
                       tuple(round(x, 4) for x in euler(mk)), mat, key=f'{name}{k}'))
        step = mv(mk, [0, -(h - 0.5), 0])
        p = [p[i] + step[i] for i in range(3)]
    return out
