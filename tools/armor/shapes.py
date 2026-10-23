"""
3D parts of the class outfits: boxes that stick out of the armour (hat brims,
hoods, horns, collars, cloaks, belts, skirts). Each box gets its own place in
the 128x64 texture of its layer; the Java side builds the model from the list
this module writes (ClassArmorShapes.java).
"""

TEX_W, TEX_H = 128, 64
LIMBS = {'arm': ('right_arm', 'left_arm'), 'leg': ('right_leg', 'left_leg')}


class Box:
    def __init__(self, slot, part, name, pos, size, grow, pivot, rot, u, v, mirror, cape):
        self.slot, self.part, self.name = slot, part, name
        self.pos, self.size, self.grow = pos, size, grow
        self.pivot, self.rot = pivot, rot
        self.u, self.v, self.mirror, self.cape = u, v, mirror, cape


class Packer:
    """Finds free room in a texture; the vanilla armour layout keeps the top left."""

    def __init__(self):
        self.used = [[False] * TEX_W for _ in range(TEX_H)]
        for y in range(32):
            for x in range(64):
                self.used[y][x] = True

    def alloc(self, w, h):
        for y in range(TEX_H - h + 1):
            for x in range(TEX_W - w + 1):
                if self._free(x, y, w, h):
                    for j in range(y, y + h):
                        for i in range(x, x + w):
                            self.used[j][i] = True
                    return x, y
        raise ValueError('no room for a %dx%d box' % (w, h))

    def _free(self, x, y, w, h):
        for j in range(y, y + h):
            row = self.used[j]
            for i in range(x, x + w):
                if row[i]:
                    return False
        return True


def mirrored(box_pos, size, pivot, rot):
    x, y, z = box_pos
    w = size[0]
    px, py, pz = pivot
    rx, ry, rz = rot
    return (-x - w, y, z), (-px, py, pz), (rx, -ry, -rz)


class Kit:
    """Collects the boxes of one outfit and gives each its texture room."""

    def __init__(self, L1, L2):
        self.L1, self.L2 = L1, L2
        self.boxes = []
        self.packers = {id(L1): Packer(), id(L2): Packer()}

    def box(self, slot, part, name, pos, size, grow=0.0, pivot=(0, 0, 0), rot=(0, 0, 0), sym=False, cape=False):
        """Adds a box and returns its name for painting. Arms and legs get a mirrored twin
        automatically; sym=True mirrors a head or body box to the other side as well."""
        tex = self.L2 if slot == 'legs' else self.L1
        w, h, d = size
        u, v = self.packers[id(tex)].alloc(2 * (w + d), h + d)
        tex.parts[name] = (u, v, w, h, d)
        if part in LIMBS:
            right, left = LIMBS[part]
            self.boxes.append(Box(slot, right, name, pos, size, grow, pivot, rot, u, v, False, cape))
            mpos, mpiv, mrot = mirrored(pos, size, pivot, rot)
            self.boxes.append(Box(slot, left, name + '_l', mpos, size, grow, mpiv, mrot, u, v, True, cape))
        else:
            self.boxes.append(Box(slot, part, name, pos, size, grow, pivot, rot, u, v, False, cape))
            if sym:
                mpos, mpiv, mrot = mirrored(pos, size, pivot, rot)
                self.boxes.append(Box(slot, part, name + '_m', mpos, size, grow, mpiv, mrot, u, v, True, cape))
        return name


def java(shapes):
    """The Java list of every outfit's boxes."""
    out = []
    out.append('package com.mki.nightsiege.client;')
    out.append('')
    out.append('import java.util.List;')
    out.append('import java.util.Map;')
    out.append('')
    out.append('/**')
    out.append(' * The 3D parts of the class outfits. Written by tools/armor/build.py; do not edit by hand.')
    out.append(' */')
    out.append('public final class ClassArmorShapes {')
    out.append('')
    out.append('    /** One box: slot (head, chest, legs, feet), model part, place, size, texture spot, pivot and turn. */')
    out.append('    public record Box(String slot, String part, String name, float x, float y, float z, int w, int h, int d,')
    out.append('                      float grow, int u, int v, boolean mirror, float px, float py, float pz,')
    out.append('                      float rx, float ry, float rz, boolean cape) {')
    out.append('    }')
    out.append('')
    out.append('    public static final int TEXTURE_WIDTH = %d;' % TEX_W)
    out.append('    public static final int TEXTURE_HEIGHT = %d;' % TEX_H)
    out.append('')
    out.append('    private ClassArmorShapes() {')
    out.append('    }')
    out.append('')
    out.append('    public static final Map<String, List<Box>> BOXES = Map.ofEntries(')
    entries = []
    for look, boxes in shapes.items():
        lines = []
        for b in boxes:
            f = lambda v: ('%.3ff' % v).replace('.000f', '.0f')
            lines.append('                    new Box("%s", "%s", "%s", %s, %s, %s, %d, %d, %d, %s, %d, %d, %s, %s, %s, %s, %s, %s, %s, %s)'
                         % (b.slot, b.part, b.name, f(b.pos[0]), f(b.pos[1]), f(b.pos[2]), b.size[0], b.size[1], b.size[2],
                            f(b.grow), b.u, b.v, 'true' if b.mirror else 'false', f(b.pivot[0]), f(b.pivot[1]), f(b.pivot[2]),
                            f(b.rot[0]), f(b.rot[1]), f(b.rot[2]), 'true' if b.cape else 'false'))
        entries.append('            Map.entry("%s", List.of(%s))' % (look, ('\n' + ',\n'.join(lines)) if lines else ''))
    out.append(',\n'.join(entries) + ');')
    out.append('}')
    return '\n'.join(out) + '\n'
