"""
The 3D parts of the outfits. Each function adds boxes through the kit and paints
them; the flat outfit from outfits.py is painted underneath first.
Coordinates are model pixels: y points down, -z is the front, -x the right side.
The head armour spans x/z -5..5 and y -9..1; the body armour x -5..5, y -1..13,
z -3..3; an arm (local) x -4..2, y -3..11; a leg (local) x -3..3, y -1..13.
"""
from paint import hexc, mul, cloth, shaded, quilt, mail, fur, leather, metal, lozenge, solid

DESIGNS = {}


def design(name):
    def reg(fn):
        DESIGNS[name] = fn
        return fn
    return reg


def clear_hat(L1):
    """The flat drawings on the hat box are replaced by real parts."""
    x0, y0 = L1.parts['hat'][0], L1.parts['hat'][1]
    for x in range(x0, x0 + 32):
        for y in range(y0, y0 + 16):
            L1.set(x, y, (0, 0, 0, 0))


def all_faces(tex, part, mat):
    tex.strip(part, mat)
    tex.cap(part, mat)
    tex.cap(part, mat, face='bottom')


def ragged_bottom(tex, part, depth=1, faces=('front', 'back', 'right', 'left')):
    """Leaves gaps in the lowest rows, for torn cloth and shaggy fur."""
    tex.strip(part, 'clear', faces=faces, where=lambda c: c.r >= c.fh - depth and (c.s * 7 + c.r) % 3 == 0)


def ragged_top(tex, part, faces=('front', 'back', 'right', 'left')):
    tex.strip(part, 'clear', faces=faces, where=lambda c: c.r == 0 and (c.s * 5) % 3 == 0)


# ---------------------------------------------------------------------- warriors

@design('berserker')
def berserker(k, L1, L2):
    F = fur(hexc('#5f5145'), hexc('#7f6f5d'), hexc('#41372e'), hexc('#a8977d'))
    bone, bone_d, bone_s = hexc('#e4dac1'), hexc('#b3a68a'), hexc('#7a6e58')
    socket = hexc('#110e0b')
    strap = hexc('#5a3a22')
    hide = hexc('#4a3426')
    clear_hat(L1)

    # The skull of a great beast over the head: cap, snout with teeth, horns, its pelt behind.
    skull = k.box('head', 'head', 'skull', (-5, -11.5, -5.5), (10, 3, 10))
    all_faces(L1, skull, lambda c: bone_d if c.rnd.random() < 0.12 else bone)
    L1.art(skull, 'front', ['bBBBBBBBBb', 'BSSBbbBSSB', 'bSoB..BoSb'], {'b': bone_d, 'B': bone, 'S': socket, 'o': hexc('#3a2a1c')})
    L1.art(skull, 'top', ['..........', '...s......', '...ss.....', '....s..s..', '.......s..'], {'s': bone_s})
    L1.cap(skull, mul(bone, 0.55), face='bottom')
    snout = k.box('head', 'head', 'snout', (-2.5, -9.5, -8.5), (5, 3, 4))
    all_faces(L1, snout, lambda c: bone_d if c.rnd.random() < 0.15 else bone)
    L1.art(snout, 'front', ['bBBBb', 'BnBnB', 'tktkt'], {'b': bone_d, 'B': bone, 'n': socket, 't': hexc('#f4ecd6'), 'k': hexc('#2a2218')})
    L1.art(snout, 'right', ['bBBB', 'BBBB', 'tktk'], {'b': bone_d, 'B': bone, 't': hexc('#f4ecd6'), 'k': hexc('#2a2218')})
    L1.art(snout, 'left', ['BBBb', 'BBBB', 'ktkt'], {'b': bone_d, 'B': bone, 't': hexc('#f4ecd6'), 'k': hexc('#2a2218')})
    horn, horn_d, horn_t = hexc('#d6c9a6'), hexc('#9a8c6c'), hexc('#3d3428')
    h1 = k.box('head', 'head', 'horn1', (-8, -11.5, -1.5), (3, 2, 3), sym=True)
    all_faces(L1, h1, lambda c: horn_d if c.r == 1 else horn)
    h2 = k.box('head', 'head', 'horn2', (-9.5, -14.5, -1.5), (2, 4, 3), sym=True)
    all_faces(L1, h2, lambda c: horn if c.r >= 2 else horn_d)
    h3 = k.box('head', 'head', 'horn3', (-9, -16.5, -1), (1, 2, 2), sym=True)
    all_faces(L1, h3, lambda c: horn_t if c.r == 0 else horn_d)
    L1.cap(h3, horn_t)
    pelt = k.box('head', 'head', 'pelt', (-5.5, -10, 4.2), (11, 12, 2))
    all_faces(L1, pelt, F)
    ragged_bottom(L1, pelt, 2)

    # Shoulders and back: a fur mantle, fur pads on the arms, a pelt cloak that swings.
    mantle = k.box('chest', 'body', 'mantle', (-5.5, -2.5, -1.5), (11, 4, 5))
    all_faces(L1, mantle, F)
    ragged_bottom(L1, mantle, 1)
    pad = k.box('chest', 'arm', 'pad', (-4.6, -3.6, -3.5), (6, 4, 7))
    all_faces(L1, pad, F)
    ragged_bottom(L1, pad, 1)
    cloak = k.box('chest', 'body', 'cloak', (-5, 0, 0), (10, 15, 1), pivot=(0, -1, 3.3), cape=True)
    all_faces(L1, cloak, F)
    ragged_bottom(L1, cloak, 2)
    belt = k.box('chest', 'body', 'belt', (-5, 8.5, -3), (10, 2, 6), grow=0.3)
    all_faces(L1, belt, leather(strap))
    L1.strip(belt, lambda c: hexc('#2a1c10') if c.r == 1 else None)
    buckle = k.box('chest', 'body', 'buckle', (-1.5, 8, -3.9), (3, 3, 1))
    all_faces(L1, buckle, bone)
    L1.art(buckle, 'front', ['BbB', 'SBS', '.t.'], {'B': bone, 'b': bone_d, 'S': socket, 't': hexc('#f4ecd6')})
    bracer = k.box('chest', 'arm', 'bracer', (-4, 6, -3), (6, 4, 6), grow=0.3)
    all_faces(L1, bracer, lambda c: hexc('#b9a68a') if (c.s % 3 == 1 and c.r in (1, 2)) else leather(hexc('#634733'))(c))
    L1.strip(bracer, lambda c: socket if c.r == 0 or c.r == 3 else None)

    # A hide loincloth over the trousers.
    loin = k.box('legs', 'body', 'loin', (-3, 11, -3.3), (6, 6, 1))
    all_faces(L2, loin, leather(hide))
    L2.strip(loin, F, rows=(0, 1))
    ragged_bottom(L2, loin, 1)
    loinb = k.box('legs', 'body', 'loinb', (-3, 11, 2.3), (6, 5, 1))
    all_faces(L2, loinb, leather(hide))
    ragged_bottom(L2, loinb, 1)

    # Fur boot wraps bound with cord.
    wrap = k.box('feet', 'leg', 'wrap', (-3.5, 6, -3.5), (7, 4, 7))
    all_faces(L1, wrap, lambda c: hexc('#b9a68a') if c.r == 2 and c.s % 2 == 0 else F(c))
    ragged_top(L1, wrap)


@design('blademaster')
def blademaster(k, L1, L2):
    blue, blue_l, blue_d = hexc('#2c4482'), hexc('#4362aa'), hexc('#1c2b57')
    white = hexc('#e9e3d3')
    gold = hexc('#cfa83e')
    sash, sash_l = hexc('#9c2832'), hexc('#c23c42')
    D = shaded(blue)
    clear_hat(L1)

    # A wide, tilted beret with a plume and a gold brooch.
    beret = k.box('head', 'head', 'beret', (-6, -2.2, -6), (12, 2, 12), pivot=(0, -9, 0), rot=(0.05, 0, -0.14))
    all_faces(L1, beret, cloth(blue))
    L1.cap(beret, lambda c: mul(blue_l, 1 + c.rnd.uniform(-0.05, 0.05)) if 2 <= c.fx <= 9 and 2 <= c.fy <= 9 else cloth(blue)(c))
    L1.strip(beret, lambda c: blue_d if c.r == 1 else None)
    L1.cap(beret, blue_d, face='bottom')
    plume = k.box('head', 'head', 'plume', (-0.5, -9, -1), (1, 9, 3), pivot=(5.2, -10, 1), rot=(0.95, 0, 0.3))
    all_faces(L1, plume, lambda c: hexc('#f4f1e8') if (c.r + c.s) % 4 else hexc('#b9b3a6'))
    L1.strip(plume, lambda c: hexc('#c23c42') if c.r >= 7 else None)
    brooch = k.box('head', 'head', 'brooch', (2.5, -11.6, -6.6), (2, 2, 1))
    all_faces(L1, brooch, gold)
    L1.art(brooch, 'front', ['gY', 'Yg'], {'g': gold, 'Y': hexc('#f6e7a0')})

    # A pleated ruff, puffed and slashed sleeves, a short cape, the knot of the sash.
    ruff = k.box('chest', 'body', 'ruff', (-5.5, -2.8, -1.6), (11, 2, 5))
    all_faces(L1, ruff, lambda c: white if c.s % 2 == 0 else hexc('#c9c2b0'))
    puff = k.box('chest', 'arm', 'puff', (-4.6, -2.2, -3.6), (7, 5, 7))
    all_faces(L1, puff, lambda c: mul(blue_l, 1.0 - 0.04 * c.r) if c.s % 3 == 0 else mul(white, 1.03 - 0.04 * c.r))
    L1.strip(puff, lambda c: gold if c.r == 4 and c.s % 2 == 0 else (blue_d if c.r == 4 else None))
    L1.cap(puff, D)
    cape = k.box('chest', 'body', 'cape', (-5, 0, 0), (10, 9, 1), pivot=(0, -1.5, 3.3), cape=True)
    all_faces(L1, cape, D)
    L1.strip(cape, lambda c: gold if c.r == 8 or c.fx in (0, c.fw - 1) and c.face in ('front', 'back') else None)
    knot = k.box('chest', 'body', 'knot', (2.6, 8.4, -3.9), (2, 5, 1))
    all_faces(L1, knot, lambda c: sash_l if c.r < 2 else sash)
    ragged_bottom(L1, knot, 1)

    # Paned trunk hose.
    hose = k.box('legs', 'leg', 'hose', (-3, -0.6, -3), (6, 5, 6))
    all_faces(L2, hose, lambda c: mul(blue, 1.05 - 0.05 * c.r) if c.s % 2 == 0 else mul(white, 1.02 - 0.04 * c.r))
    L2.strip(hose, lambda c: blue_d if c.r == 4 else None)
    L2.cap(hose, D)

    # Turned-down tops of the riding boots.
    cuff = k.box('feet', 'leg', 'cuff', (-3.6, 4.6, -3.6), (7, 2, 7))
    all_faces(L1, cuff, leather(hexc('#7a5434')))
    L1.cap(cuff, hexc('#2a2020'))


@design('axe')
def axe(k, L1, L2):
    iron = hexc('#8b9097')
    brass = hexc('#b08a3a')
    rust = hexc('#8f4a22')
    lam, lam_l, lam_d = hexc('#5b3b24'), hexc('#83593a'), hexc('#36230f')
    I = metal(iron)

    def lamellar(c):
        px = (c.s + (c.r // 2) % 2) % 2
        py = c.r % 2
        if py == 1:
            return lam_d if px == 0 else lam
        return lam_l if px == 0 else mul(lam, 1.05)

    clear_hat(L1)
    # Spangenhelm: an iron cone in tiers with brass bands, a knob, a nasal.
    tiers = [((-5.5, -9.6, -5.5), (11, 2, 11)), ((-4.5, -11.2, -4.5), (9, 2, 9)), ((-3, -12.6, -3), (6, 2, 6))]
    for i, (pos, size) in enumerate(tiers):
        t = k.box('head', 'head', 'helm%d' % i, pos, size)
        all_faces(L1, t, lambda c: brass if (c.s % 4 == 1 or (c.face == 'top' and (c.fx == c.fw // 2 or c.fy == c.fh // 2))) else I(c))
        L1.strip(t, lambda c: mul(iron, 0.6) if c.r == 1 and c.s % 2 else None)
    knob = k.box('head', 'head', 'knob', (-1, -13.6, -1), (2, 1, 2))
    all_faces(L1, knob, brass)
    nasal = k.box('head', 'head', 'nasal', (-0.5, -8.5, -6.3), (1, 5, 1))
    all_faces(L1, nasal, lambda c: mul(iron, 1.15 - 0.06 * c.r))

    # Lamellar shoulders and vest, the tunic skirt, a belt with a hand axe at the hip.
    pauld = k.box('chest', 'arm', 'pauldron', (-4.8, -3.2, -3.6), (6, 5, 7), rot=(0, 0, -0.18))
    all_faces(L1, pauld, lamellar)
    L1.strip(pauld, lambda c: hexc('#c9c5b8') if c.r == 4 and c.s % 2 == 0 else None)
    vest = k.box('chest', 'body', 'vest', (-5, -0.6, -3), (10, 9, 6), grow=0.35)
    all_faces(L1, vest, lamellar)
    skirt = k.box('chest', 'body', 'skirt', (-5, 10.4, -3), (10, 4, 6), grow=0.3)
    all_faces(L1, skirt, shaded(rust))
    L1.strip(skirt, lozenge(hexc('#d8c8a0'), hexc('#2f4a32')), rows=(2, 3))
    belt = k.box('chest', 'body', 'belt', (-5, 8.8, -3), (10, 2, 6), grow=0.5)
    all_faces(L1, belt, leather(hexc('#3a2616')))
    buckle = k.box('chest', 'body', 'buckle', (-1, 8.4, -4), (2, 2, 1))
    all_faces(L1, buckle, lambda c: mul(iron, 1.2) if c.r == 0 else iron)
    haft = k.box('chest', 'body', 'haft', (5.4, 8, -0.5), (1, 8, 1))
    all_faces(L1, haft, lambda c: hexc('#7a5634') if c.r % 3 else hexc('#5a3a22'))
    blade = k.box('chest', 'body', 'blade', (5.3, 13, -2.6), (1, 3, 3))
    all_faces(L1, blade, lambda c: mul(iron, 1.3) if c.fx == 0 and c.face in ('right', 'left') else I(c))


@design('paladin')
def paladin(k, L1, L2):
    white = hexc('#e8e3d6')
    gold, gold_l, gold_d = hexc('#d4ae38'), hexc('#f2d77c'), hexc('#8f7020')
    red, red_d = hexc('#8a1c1c'), hexc('#5e1212')
    steel = hexc('#b4bac3')
    W = shaded(white, 1.04, 0.86)
    clear_hat(L1)

    # Gold circlet with a red stone over the mail coif.
    circ = k.box('head', 'head', 'circlet', (-5.5, -8.6, -5.5), (11, 1, 11))
    all_faces(L1, circ, lambda c: gold_l if c.s % 3 == 0 else gold)
    gem = k.box('head', 'head', 'gem', (-1, -9.2, -6.3), (2, 2, 1))
    all_faces(L1, gem, gold)
    L1.art(gem, 'front', ['Rr', 'rd'], {'R': hexc('#e04040'), 'r': hexc('#9a1c1c'), 'd': hexc('#5e1212')})

    # The surcoat falls in front to mid thigh; shoulder pieces; a long red cloak.
    tab = k.box('chest', 'body', 'tabard', (-4, 0.4, -3.6), (8, 16, 1))
    all_faces(L1, tab, W)
    L1.strip(tab, lambda c: gold if c.fx in (0, c.fw - 1) or c.r == 15 else None, faces=('front', 'back'))
    L1.art(tab, 'front', ['...gG...', '...gG...', '.gggGGg.', '.dddgdd.', '...gG...', '...gG...', '...dg...'],
           {'g': gold, 'G': gold_l, 'd': gold_d}, y=2)
    pauld = k.box('chest', 'arm', 'pauldron', (-4.7, -3.3, -3.6), (6, 3, 7), rot=(0, 0, -0.15))
    all_faces(L1, pauld, W)
    L1.strip(pauld, lambda c: gold if c.r == 2 else None)
    L1.cap(pauld, lambda c: gold if c.fx in (0, c.fw - 1) else W(c))
    cloak = k.box('chest', 'body', 'cloak', (-5.5, 0, 0), (11, 20, 1), pivot=(0, -1, 3.4), cape=True)
    all_faces(L1, cloak, shaded(red, 1.05, 0.8))
    L1.strip(cloak, lambda c: gold if c.r == 19 or (c.face in ('front', 'back') and c.fx in (0, c.fw - 1)) else None)
    L1.cap(cloak, gold)
    belt = k.box('chest', 'body', 'belt', (-5, 8, -3), (10, 1, 6), grow=0.8)
    all_faces(L1, belt, leather(hexc('#5a3c26')))
    buckle = k.box('chest', 'body', 'buckle', (-1, 7.5, -4.5), (2, 2, 1))
    all_faces(L1, buckle, gold)
    L1.art(buckle, 'front', ['Gg', 'gd'], {'G': gold_l, 'g': gold, 'd': gold_d})

    # Steel knee cops; steel toe caps.
    knee = k.box('legs', 'leg', 'knee', (-2.5, 4, -3.4), (5, 2, 1))
    all_faces(L2, knee, metal(steel))
    L2.art(knee, 'front', ['..g..'], {'g': gold})
    toe = k.box('feet', 'leg', 'toe', (-2.5, 10.6, -4.7), (5, 2, 2))
    all_faces(L1, toe, metal(steel))
    L1.strip(toe, lambda c: mul(steel, 0.6) if c.r == 1 else None)


# ---------------------------------------------------------------------- shared 3D pieces

def hood_box(k, L1, name, mat, rim, opening=(1, 1, 9, 6)):
    """A hood shaped in layers: a rounded crown, the hood itself open for the face,
    a peak over the brow, a wider lower part falling to the neck and a tail that
    falls down the back in three bends. No cowl on the shoulders: a body piece
    there cuts into the hood whenever the head turns (Simon, 24 October 2026)."""
    top = k.box('head', 'head', name + '_crown', (-4.5, -11.2, -4), (9, 2, 9))
    all_faces(L1, top, mat)
    top2 = k.box('head', 'head', name + '_crown2', (-3, -12, -2.5), (6, 1, 6))
    all_faces(L1, top2, mat)
    h = k.box('head', 'head', name, (-5.6, -9.8, -5.6), (11, 6, 11))
    all_faces(L1, h, mat)
    x1, y1, x2, y2 = opening
    L1.strip(h, 'clear', faces=('front',), where=lambda c: x1 <= c.fx <= x2 and y1 <= c.fy <= y2)
    L1.strip(h, rim, faces=('front',), where=lambda c: (c.fx in (x1 - 1, x2 + 1) and c.fy >= y1 - 1) or (c.fy == y1 - 1 and x1 - 1 <= c.fx <= x2 + 1))
    L1.cap(h, 'clear', face='bottom')
    # The lower part ends above the shoulders, so it never cuts into them when the head turns.
    low = k.box('head', 'head', name + '_low', (-6.2, -4, -5.4), (12, 3, 12))
    all_faces(L1, low, mat)
    L1.strip(low, 'clear', faces=('front',), where=lambda c: 1 <= c.fx <= 10)
    L1.strip(low, rim, faces=('front',), where=lambda c: c.fx in (0, 11))
    L1.cap(low, 'clear', face='bottom')
    L1.cap(low, 'clear', where=lambda c: 1 <= c.fx <= 10 and 1 <= c.fy <= 10)
    peak = k.box('head', 'head', name + '_peak', (-4.5, -10.4, -6.6), (9, 1, 1))
    all_faces(L1, peak, rim)
    for i, (piv, rot, size) in enumerate((((0, -10, 5.2), 0.45, (5, 3, 3)), ((0, -7.6, 6.6), 0.85, (4, 3, 2)), ((0, -5.4, 7.9), 1.2, (2, 4, 1)))):
        t = k.box('head', 'head', '%s_tail%d' % (name, i), (-size[0] / 2.0, 0, -size[2] / 2.0), size, pivot=piv, rot=(rot, 0, 0))
        all_faces(L1, t, mat)
    return h


def cloak_box(k, L1, mat, trim=None, length=18, width=11):
    c0 = k.box('chest', 'body', 'cloak', (-width / 2.0, 0, 0), (width, length, 1), pivot=(0, -1, 3.4), cape=True)
    all_faces(L1, c0, mat)
    if trim is not None:
        L1.strip(c0, lambda c: trim if c.r == length - 1 or (c.face in ('front', 'back') and c.fx in (0, c.fw - 1)) else None)
    return c0


def skirt_box(k, tex, slot, mat, top=10.4, h=4, grow=0.3):
    s = k.box(slot, 'body', 'skirt', (-5, top, -3), (10, h, 6), grow=grow)
    all_faces(tex, s, mat)
    tex.cap(s, 'clear')
    tex.cap(s, 'clear', face='bottom')
    return s


def lenses(k, L1, frame, glass, glint, y=-8.2):
    g = k.box('head', 'head', 'lens', (-4.2, y, -6.0), (3, 3, 1), sym=True)
    all_faces(L1, g, frame)
    L1.art(g, 'front', ['fff', 'fgf', 'fff'], {'f': frame, 'g': glass})
    L1.art(g, 'front', ['...', '.G.'], {'G': glint})
    return g


# ---------------------------------------------------------------------- archers

@design('swift')
def swift(k, L1, L2):
    green, green_d = hexc('#3e6a36'), hexc('#26421f')
    G = shaded(green, 1.08, 0.85)
    leather_c = hexc('#6b4a2c')
    clear_hat(L1)
    hood_box(k, L1, 'hood', G, lambda c: green_d)
    cape = k.box('chest', 'body', 'capelet', (-5.5, -1.2, -1.6), (11, 5, 5))
    all_faces(L1, cape, G)
    ragged_bottom(L1, cape, 1)
    L1.cap(cape, 'clear', face='bottom')
    q = k.box('chest', 'body', 'quiver', (-1.5, -5, -1.5), (3, 10, 3), pivot=(2.5, 4.5, 4.6), rot=(0, 0, 0.4))
    all_faces(L1, q, lambda c: hexc('#a07c50') if c.r in (0, 9) else leather(hexc('#5a3a22'))(c))
    arrows = k.box('chest', 'body', 'arrows', (-1, -8, -1), (2, 3, 2), pivot=(2.5, 4.5, 4.6), rot=(0, 0, 0.4))
    all_faces(L1, arrows, lambda c: hexc('#e8e2d0') if (c.s + c.r) % 2 else hexc('#b03028'))
    bracer = k.box('chest', 'arm', 'bracer', (-4, 6.5, -3), (6, 4, 6), grow=0.25)
    all_faces(L1, bracer, lambda c: hexc('#d9c9a0') if c.r in (1, 3) and c.s % 3 == 0 else leather(leather_c)(c))
    cuff = k.box('feet', 'leg', 'cuff', (-3.5, 6.4, -3.5), (7, 2, 7))
    all_faces(L1, cuff, leather(hexc('#a07c50')))
    L1.cap(cuff, hexc('#3a2a1a'))


@design('heavy')
def heavy(k, L1, L2):
    steel = hexc('#9aa0a8')
    red, white = hexc('#9a2a24'), hexc('#e6dfcf')
    jack = hexc('#a07a44')
    S = metal(steel)
    clear_hat(L1)
    cap = k.box('head', 'head', 'cap', (-5, -10.6, -5), (10, 2, 10))
    all_faces(L1, cap, S)
    brim = k.box('head', 'head', 'brim', (-6.5, -9.1, -6.5), (13, 1, 13))
    all_faces(L1, brim, lambda c: mul(steel, 0.75) if c.s % 3 == 0 else S(c))
    L1.cap(brim, lambda c: mul(steel, 0.85))
    collar = k.box('chest', 'body', 'collar', (-5.5, -2.2, -1.5), (11, 2, 5))
    all_faces(L1, collar, quilt(jack))
    skirt = skirt_box(k, L1, 'chest', lambda c: cloth(white)(c) if (c.face == 'front' and c.fx < 5) or (c.face == 'back' and c.fx >= 5) or c.face == 'right' else cloth(red)(c))
    L1.strip(skirt, lambda c: hexc('#d4ae38') if c.r == 3 and c.s % 2 == 0 else None)
    bag = k.box('chest', 'body', 'bag', (4.2, 8, -1.5), (2, 6, 3))
    all_faces(L1, bag, cloth(hexc('#c9bfa6')))
    shafts = k.box('chest', 'body', 'shafts', (4.5, 4.5, -1), (1, 4, 2))
    all_faces(L1, shafts, lambda c: hexc('#e8e2d0') if c.r == 0 else hexc('#7a5634'))
    bracer = k.box('chest', 'arm', 'bracer', (-4, 7, -3), (6, 3, 6), grow=0.25)
    all_faces(L1, bracer, leather(hexc('#5a3a22')))


@design('bolter')
def bolter(k, L1, L2):
    orange, black, cream = hexc('#c0622a'), hexc('#2a2424'), hexc('#e8dcc0')
    felt = hexc('#3a2e2a')
    clear_hat(L1)
    hat = k.box('head', 'head', 'hat', (-6.5, -2, -6.5), (13, 2, 13), pivot=(0, -9, 0), rot=(0, 0, 0.18))
    all_faces(L1, hat, cloth(felt))
    L1.cap(hat, lambda c: mul(felt, 1.15) if 3 <= c.fx <= 9 and 3 <= c.fy <= 9 else cloth(felt)(c))
    for i, (piv, rot, size, col) in enumerate([((-5, -10, 0), (0.6, 0, -0.55), (2, 10, 3), cream),
                                               ((-5.5, -10, 1.5), (0.9, 0, -0.75), (2, 8, 2), hexc('#c23a32'))]):
        p = k.box('head', 'head', 'plume%d' % i, (-1, -size[1], -1), size, pivot=piv, rot=rot)
        all_faces(L1, p, lambda c, col=col: col if (c.s + c.r) % 3 else mul(col, 0.8))
    puff = k.box('chest', 'arm', 'puff', (-4.6, -2.2, -3.6), (7, 5, 7))
    all_faces(L1, puff, lambda c: (orange if (c.s // 2) % 2 == 0 else black) if c.r % 4 != 2 else cream)
    puff2 = k.box('chest', 'arm', 'puff2', (-4.3, 4.6, -3.3), (6, 3, 6))
    all_faces(L1, puff2, lambda c: orange if c.s % 2 == 0 else cream)
    quiver = k.box('chest', 'body', 'quiver', (4.2, 8, -2), (2, 6, 3))
    all_faces(L1, quiver, leather(hexc('#5a3a22')))
    bolts = k.box('chest', 'body', 'bolts', (4.6, 5.5, -1.5), (1, 3, 2))
    all_faces(L1, bolts, lambda c: hexc('#c8c0a8') if c.r == 0 else hexc('#6b4a2c'))
    knee = k.box('legs', 'leg', 'kneepuff', (-3, 3, -3), (6, 3, 6))
    all_faces(L2, knee, lambda c: orange if c.s % 2 == 0 else black)
    toe = k.box('feet', 'leg', 'toe', (-3.3, 10.6, -4.6), (7, 2, 3))
    all_faces(L1, toe, leather(hexc('#2e2420')))


@design('trapper')
def trapper(k, L1, L2):
    F = fur(hexc('#6e5a44'), hexc('#8e7a60'), hexc('#3e3226'), hexc('#c8b896'))
    hide, hide_l, hide_d = hexc('#7a5634'), hexc('#9a7046'), hexc('#4e3420')
    clear_hat(L1)
    cap = k.box('head', 'head', 'cap', (-5.4, -10.8, -5.4), (11, 4, 11))
    all_faces(L1, cap, F)
    L1.cap(cap, 'clear', face='bottom')
    ragged_bottom(L1, cap, 1)
    tail = k.box('head', 'head', 'tail', (-1, 0, -1), (2, 9, 2), pivot=(2, -8, 5.8), rot=(0.25, 0, -0.15))
    all_faces(L1, tail, lambda c: hexc('#2e241c') if c.r % 3 == 2 else hexc('#9a8668'))
    L1.strip(tail, lambda c: hexc('#1e1814') if c.r == 8 else None)
    collar = k.box('chest', 'body', 'collar', (-5.5, -2.2, -1.6), (11, 3, 5))
    all_faces(L1, collar, F)
    ragged_bottom(L1, collar, 1)
    coat = skirt_box(k, L1, 'chest', leather(hide), top=10.2, h=5)
    L1.strip(coat, 'clear', where=lambda c: c.r == 4 and c.s % 2 == 1)
    rope = k.box('chest', 'body', 'rope', (-5.4, -0.6, -3.4), (10, 1, 6), grow=0.45, pivot=(0, 4, 0), rot=(0, 0, 0.62))
    all_faces(L1, rope, lambda c: hexc('#c9b48a') if (c.s + c.r) % 2 else hexc('#8a7454'))
    pouch = k.box('chest', 'body', 'pouch', (-4.6, 9.2, -4.1), (3, 3, 1), sym=True)
    all_faces(L1, pouch, leather(hide_d))
    L1.art(pouch, 'front', ['LLL', 'l.l', 'lll'], {'L': hide_l, 'l': hide_d})
    cuff = k.box('feet', 'leg', 'cuff', (-3.5, 6.2, -3.5), (7, 2, 7))
    all_faces(L1, cuff, F)
    ragged_top(L1, cuff)


# ---------------------------------------------------------------------- guardians

@design('shieldfighter')
def shieldfighter(k, L1, L2):
    steel = hexc('#9aa0a8')
    blue, gold = hexc('#2f4f8a'), hexc('#d4ae38')
    S = metal(steel)
    clear_hat(L1)
    dome = k.box('head', 'head', 'dome', (-5, -10.6, -5), (10, 2, 10))
    all_faces(L1, dome, S)
    brim = k.box('head', 'head', 'brim', (-7, -9.1, -7), (14, 1, 14))
    all_faces(L1, brim, lambda c: mul(steel, 0.7) if c.s % 4 == 0 else S(c))
    L1.cap(brim, lambda c: mul(steel, 0.65) if c.fx in (0, 13) or c.fy in (0, 13) else S(c))
    spaul = k.box('chest', 'arm', 'spaulder', (-4.6, -3.2, -3.6), (6, 3, 7), rot=(0, 0, -0.15))
    all_faces(L1, spaul, S)
    L1.strip(spaul, lambda c: mul(steel, 0.6) if c.r == 2 else None)
    for name, z in (('tabard', -3.6), ('tabardb', 2.6)):
        t = k.box('chest', 'body', name, (-4, 9, z), (8, 7, 1))
        all_faces(L1, t, lambda c: cloth(blue)(c) if (c.fx < 4) == (c.face == 'front') else cloth(gold)(c))
        L1.strip(t, lambda c: hexc('#1f3560') if c.r == 6 else None)
    shield = k.box('chest', 'body', 'shield', (-3.5, -0.5, 3.4), (7, 9, 1))
    all_faces(L1, shield, lambda c: cloth(blue)(c) if c.fx < 4 else cloth(gold)(c))
    L1.art(shield, 'back', ['sssssss', 's.....s', 's..w..s', 's.wWw.s', 's..w..s', 's.....s', '.s...s.', '..s.s..', '...s...'],
           {'s': mul(steel, 0.8), 'w': hexc('#efe9da'), 'W': hexc('#c9c2b0')})
    L1.strip(shield, 'clear', faces=('back', 'front'), where=lambda c: (c.fy == 8 and c.fx != 3) or (c.fy == 7 and c.fx in (0, 6)))


@design('bulwark')
def bulwark(k, L1, L2):
    steel = hexc('#a7adb6')
    brass = hexc('#c9a43a')
    S = metal(steel)
    # A great helm: under the dark eye slit a plain face plate (no breathing holes, Simon),
    # a brass ridge down the middle splits the slit in two (the old snout visor looked
    # like a strange face, Simon 24 October 2026).
    plate = k.box('head', 'head', 'faceplate', (-5.5, -4, -5.7), (11, 5, 3))
    all_faces(L1, plate, S)
    L1.art(plate, 'front', ['DDDDDDDDDDD', '...........', '...........', '...........', 'ddddddddddd'],
           {'D': mul(steel, 0.45), 'd': mul(steel, 0.7)})
    L1.strip(plate, lambda c: mul(steel, 0.45) if c.r == 0 else (mul(steel, 0.7) if c.r == 4 else None), faces=('right', 'left'))
    ridge = k.box('head', 'head', 'ridge', (-0.5, -9, -6.2), (1, 10, 1))
    all_faces(L1, ridge, lambda c: mul(brass, 1.15) if c.face == 'front' and c.r % 3 == 0 else brass)
    crest = k.box('head', 'head', 'crest', (-0.5, -10.4, -4.5), (1, 2, 9))
    all_faces(L1, crest, lambda c: brass if c.r == 0 else S(c))
    gorget = k.box('chest', 'body', 'gorget', (-5.5, -2.6, -1.5), (11, 2, 5))
    all_faces(L1, gorget, S)
    for i, (y, h) in enumerate(((-3.6, 4), (0.2, 2))):
        p = k.box('chest', 'arm', 'paul%d' % i, (-5, y, -3.8), (7, h, 8), rot=(0, 0, -0.25))
        all_faces(L1, p, S)
        L1.strip(p, lambda c: mul(steel, 0.55) if c.r == h - 1 else (brass if c.r == 0 and c.s % 4 == 0 else None))
    fauld = skirt_box(k, L1, 'chest', S, top=10, h=4, grow=0.5)
    L1.strip(fauld, lambda c: mul(steel, 0.55) if c.r in (1, 3) else None)
    poleyn = k.box('legs', 'leg', 'poleyn', (-2.6, 4, -3.6), (5, 3, 2))
    all_faces(L2, poleyn, S)
    L2.art(poleyn, 'front', ['.ddd.', 'dbbbd', '.ddd.'], {'d': mul(steel, 0.65), 'b': brass})
    toe = k.box('feet', 'leg', 'toe', (-2.6, 10.6, -4.7), (5, 2, 2))
    all_faces(L1, toe, S)


@design('medic')
def medic(k, L1, L2):
    white, white_d = hexc('#ece6d8'), hexc('#c4bdac')
    robe = hexc('#5f6e5c')
    green, green_d = hexc('#3a9a4a'), hexc('#246a30')
    cap = k.box('head', 'head', 'cap', (-5, -10.6, -5), (10, 2, 10))
    all_faces(L1, cap, cloth(white))
    L1.art(cap, 'front', ['...gg.....', '..gggg....'], {'g': green})
    apron = k.box('chest', 'body', 'apron', (-3.5, 8, -3.6), (7, 9, 1))
    all_faces(L1, apron, shaded(white, 1.02, 0.9))
    L1.art(apron, 'front', ['.......', '...g...', '..gGg..', '...g...'], {'g': green, 'G': green_d}, y=2)
    satchel = k.box('chest', 'body', 'satchel', (-6.8, 7.5, -2), (2, 4, 4))
    all_faces(L1, satchel, leather(hexc('#7a5634')))
    L1.art(satchel, 'right', ['....', '.gg.', '.gg.', '....'], {'g': green})
    roll = k.box('chest', 'arm', 'roll', (-4.3, 4, -3.3), (6, 2, 6))
    all_faces(L1, roll, lambda c: mul(robe, 0.9) if c.r == 1 else robe)
    flask = k.box('chest', 'body', 'flask', (4.3, 8.5, -1), (2, 3, 2))
    all_faces(L1, flask, lambda c: hexc('#7fe08a') if c.r > 0 else hexc('#7a5634'))


@design('warden')
def warden(k, L1, L2):
    violet, violet_d = hexc('#3e2a5a'), hexc('#26183a')
    silver, glow = hexc('#c9d0de'), hexc('#b48cff')
    V = shaded(violet, 1.06, 0.8)
    clear_hat(L1)
    hood_box(k, L1, 'hood', V, lambda c: silver if c.r % 2 == 0 else violet_d)
    mantle = k.box('chest', 'body', 'mantle', (-5.6, -1.2, -1.6), (11, 4, 5))
    all_faces(L1, mantle, lambda c: silver if c.r == 4 and c.s % 2 == 0 else (glow if c.r == 4 else shaded(violet_d)(c)))
    L1.cap(mantle, 'clear', face='bottom')
    cloak_box(k, L1, V, silver, length=19)
    for i, x in enumerate((-2.5, 1.5)):
        st = k.box('chest', 'body', 'rune%d' % i, (x, 9.5, -4.2), (1, 2, 1))
        all_faces(L1, st, lambda c: glow if c.r == 0 else hexc('#5a4a7a'))


# ---------------------------------------------------------------------- engineers

@design('gunner')
def gunner(k, L1, L2):
    steel = hexc('#a0a6ae')
    brass = hexc('#c09a40')
    coat = hexc('#5e3e26')
    S = metal(steel)
    clear_hat(L1)
    dome = k.box('head', 'head', 'dome', (-5, -10.6, -5), (10, 2, 10))
    all_faces(L1, dome, S)
    crest = k.box('head', 'head', 'crest', (-0.5, -13, -5), (1, 3, 10))
    all_faces(L1, crest, lambda c: mul(steel, 1.2) if c.r == 0 else S(c))
    brim = k.box('head', 'head', 'brim', (-6.5, -9, -6.5), (13, 1, 13))
    all_faces(L1, brim, lambda c: brass if c.s % 3 == 0 else S(c))
    for i, (x, y) in enumerate(((-4, 1.5), (-2, 3.5), (0, 5.5))):
        fl = k.box('chest', 'body', 'flask%d' % i, (x, y, -4.1), (2, 2, 1))
        all_faces(L1, fl, lambda c: brass if c.r == 0 else hexc('#3a2a1e'))
    tails = skirt_box(k, L1, 'chest', shaded(coat), top=10, h=7, grow=0.35)
    L1.strip(tails, 'clear', faces=('front',), where=lambda c: c.fx in (4, 5))
    cuff = k.box('feet', 'leg', 'cuff', (-3.6, 4.6, -3.6), (7, 2, 7))
    all_faces(L1, cuff, leather(hexc('#4a3a2c')))
    L1.cap(cuff, hexc('#1a1412'))


@design('electrician')
def electrician(k, L1, L2):
    copper, copper_l = hexc('#b8693a'), hexc('#e09a5e')
    spark = hexc('#7fe8ff')
    steel = hexc('#8a9098')
    lenses(k, L1, hexc('#b08a3a'), spark, hexc('#e8ffff'))
    coil = k.box('chest', 'arm', 'coil', (-3, -6.5, -1.5), (3, 4, 3))
    all_faces(L1, coil, lambda c: copper_l if c.r % 2 == 0 else copper)
    ball = k.box('chest', 'arm', 'ball', (-2.5, -8.3, -1), (2, 2, 2))
    all_faces(L1, ball, spark)
    pack = k.box('chest', 'body', 'pack', (-3.5, 0, 3.2), (7, 8, 3))
    all_faces(L1, pack, metal(steel))
    L1.art(pack, 'back', ['ccccccc', 'c.....c', 'c.s.s.c', 'c.....c', 'ccccccc', '.......', '.k.k.k.', '.......'],
           {'c': copper, 's': spark, 'k': copper_l})
    tool = k.box('chest', 'body', 'wrench', (4.6, 8, -1), (1, 5, 1))
    all_faces(L1, tool, lambda c: mul(steel, 1.2) if c.r in (0, 4) else steel)


@design('demolition')
def demolition(k, L1, L2):
    pad = hexc('#4a4038')
    lenses(k, L1, hexc('#2a2220'), hexc('#d98a20'), hexc('#ffd070'))
    flap = k.box('head', 'head', 'flap', (-5.4, -4.5, 4.2), (11, 6, 1))
    all_faces(L1, flap, leather(hexc('#4e3a2a')))
    shoulder = k.box('chest', 'arm', 'pad', (-4.6, -3.2, -3.6), (6, 3, 7))
    all_faces(L1, shoulder, quilt(pad, 0.7))
    apron = k.box('chest', 'body', 'apron', (-4.5, 1, -3.7), (9, 15, 1))
    all_faces(L1, apron, quilt(hexc('#3a322c'), 0.7))
    L1.strip(apron, lambda c: hexc('#141210') if c.rnd.random() < 0.12 else None)
    for i, (x, y) in enumerate(((-4, 1.2), (-1, 3.4), (2, 5.6))):
        b = k.box('chest', 'body', 'bomb%d' % i, (x, y, -5.3), (2, 2, 2))
        all_faces(L1, b, lambda c: hexc('#1e1c1c') if c.rnd.random() > 0.15 else hexc('#3a3636'))
        L1.cap(b, lambda c: hexc('#ffb040') if (c.fx, c.fy) == (1, 0) else hexc('#1e1c1c'))
    toe = k.box('feet', 'leg', 'toe', (-2.6, 10.6, -4.7), (5, 2, 2))
    all_faces(L1, toe, metal(hexc('#7a7e86')))


@design('alchemist')
def alchemist(k, L1, L2):
    """The plague doctor: wide-brimmed hat, beaked mask with glowing green lenses,
    a short shoulder cape (back and sides only), the long coat, vials on the belt."""
    coat, coat_d = hexc('#342d2a'), hexc('#1a1514')
    hat_c = hexc('#221d1c')
    band = hexc('#4a3424')
    brass = hexc('#b08a3a')
    bone, bone_d, bone_l = hexc('#d9cdb0'), hexc('#a8987a'), hexc('#ece2c8')
    glow = hexc('#9cf06a')
    H = shaded(hat_c, 1.2, 0.85)
    C = shaded(coat, 1.15, 0.8)
    clear_hat(L1)
    # The hat: a broad brim, a tall flat crown with a leather band and a brass buckle.
    brim = k.box('head', 'head', 'brim', (-7.5, -0.6, -7.5), (15, 1, 15), pivot=(0, -9, 0), rot=(0.06, 0, 0))
    all_faces(L1, brim, H)
    L1.cap(brim, lambda c: mul(hat_c, 1.5) if c.fx in (0, 14) or c.fy in (0, 14) else None)
    crown = k.box('head', 'head', 'crown', (-4.5, -4.4, -4.5), (9, 4, 9), pivot=(0, -9, 0), rot=(0.06, 0, 0))
    all_faces(L1, crown, H)
    L1.strip(crown, lambda c: leather(band)(c) if c.r == 3 else None)
    L1.art(crown, 'front', ['.........', '.........', '.........', '...dhd...'], {'h': brass, 'd': mul(brass, 0.6)})
    top = k.box('head', 'head', 'crowntop', (-4, -5.2, -4), (8, 1, 8), pivot=(0, -9, 0), rot=(0.06, 0, 0))
    all_faces(L1, top, H)
    # Round glass eyes in brass rims, glowing like the brew.
    lens = k.box('head', 'head', 'lens', (-4.6, -6.6, -5.9), (3, 3, 1), sym=True)
    all_faces(L1, lens, brass)
    L1.art(lens, 'front', ['dbd', 'bgb', 'dbd'], {'b': brass, 'd': mul(brass, 0.65), 'g': glow})
    # The beak in three bends, curving down, stitched along the top.
    for i, (pos, size, rot) in enumerate((((-1.5, -1.5, -3), (3, 3, 3), 0.16),
                                          ((-1.5, -1, -6), (3, 2, 3), 0.3),
                                          ((-1, -0.6, -8.5), (2, 1, 3), 0.46))):
        b = k.box('head', 'head', 'beak%d' % i, pos, size, pivot=(0, -3.2, -5), rot=(rot, 0, 0))
        all_faces(L1, b, lambda c: bone_d if c.rnd.random() < 0.15 else bone)
        L1.cap(b, lambda c: mul(bone, 0.6) if c.fx == c.fw // 2 and c.fy % 2 == 0 else bone_l)
        L1.cap(b, bone_d, face='bottom')
    # A short shoulder cape, open in front (nothing in front of the neck: the head turns freely).
    cape = k.box('chest', 'body', 'mantle', (-5.6, -1.2, -1.6), (11, 4, 5))
    all_faces(L1, cape, C)
    L1.strip(cape, lambda c: coat_d if c.r == 3 else (hexc('#5a4c44') if c.r == 0 else None))
    L1.cap(cape, 'clear', face='bottom')
    # The skirt of the long coat, split in front.
    tails = skirt_box(k, L1, 'chest', C, top=10, h=6, grow=0.35)
    L1.strip(tails, 'clear', faces=('front',), where=lambda c: c.fx in (4, 5))
    L1.strip(tails, lambda c: coat_d if c.r == 5 else None)
    # Flared glove cuffs.
    cuff = k.box('chest', 'arm', 'glovecuff', (-4.3, 7.4, -3.3), (6, 2, 6))
    all_faces(L1, cuff, leather(hexc('#4a3424')))
    L1.cap(cuff, 'clear', face='bottom')
    # Glowing vials on the belt, a flask at the hip.
    colours = [hexc('#9cf06a'), hexc('#d080ff'), hexc('#7ad0ff'), hexc('#ffb050')]
    for i, x in enumerate((-4.5, -2.5, 1.5, 3.5)):
        v = k.box('chest', 'body', 'vial%d' % i, (x, 5.6, -4.1), (1, 2, 1))
        all_faces(L1, v, lambda c, col=colours[i]: hexc('#c9b48a') if c.r == 0 else col)
    flask = k.box('chest', 'body', 'flask', (4.4, 8.5, -1), (2, 3, 2))
    all_faces(L1, flask, lambda c: brass if c.r == 0 else glow)


# ---------------------------------------------------------------------- the epic pass (details)

def _then(name, extra):
    base = DESIGNS[name]

    def both(k, L1, L2):
        base(k, L1, L2)
        extra(k, L1, L2)
    DESIGNS[name] = both


def _berserker_more(k, L1, L2):
    iron = hexc('#6e7278')
    fang, fang_d = hexc('#efe6cf'), hexc('#a89c80')
    blood = hexc('#7a1a14')
    # A necklace of fangs on a thong over the chest.
    neck = k.box('chest', 'body', 'fangs', (-3.5, 0.2, -3.8), (7, 3, 1))
    all_faces(L1, neck, 'clear')
    L1.art(neck, 'front', ['ttttttt', 'f.F.f.F', 'F...F..'], {'t': hexc('#4a3020'), 'f': fang, 'F': fang_d})
    # War paint: red claw marks on the skull, iron rings on the horns and the belt.
    L1.art('skull', 'top', ['..........', '..........', '.r..r..r..', '..r..r..r.', '...r..r..r'], {'r': blood})
    for horn in ('horn1', 'horn2'):
        L1.strip(horn, lambda c: iron if c.r == 0 else None)
    L1.strip('belt', lambda c: mul(iron, 1.3) if c.r == 0 and c.s % 4 == 0 else None)
    L1.strip('bracer', lambda c: mul(iron, 1.25) if c.r in (0, 3) and c.s % 3 == 0 else None)
    # A torn wolf tail hanging from the belt.
    tail = k.box('chest', 'body', 'wolftail', (3.5, 9.5, -1.5), (2, 7, 2))
    all_faces(L1, tail, fur(hexc('#4a4038'), hexc('#6a5c4e'), hexc('#2c2520'), hexc('#8e7e68')))
    ragged_bottom(L1, tail, 1)


def _blademaster_more(k, L1, L2):
    gold, gold_d = hexc('#c9a03a'), hexc('#7a5e1c')
    silver = hexc('#cfd5de')
    # Embroidered gold scrolls along the edges of the cape and the puffs.
    L1.strip('cape', lambda c: (gold if (c.s + c.r) % 3 == 0 else gold_d) if c.r in (6, 7) else None)
    L1.art('cape', 'back', ['..........', '....gg....', '...g..g...', '....gg....'], {'g': gold}, y=1)
    L1.strip('puff', lambda c: gold if c.r == 0 and c.s % 2 == 0 else None)
    L1.strip('beret', lambda c: gold if c.r == 1 and c.s % 3 == 0 else None)
    # A swept rapier hilt at the left hip.
    cup = k.box('chest', 'body', 'hilt', (4.6, 7.4, -2.6), (2, 2, 2))
    all_faces(L1, cup, lambda c: silver if (c.s + c.r) % 2 else mul(silver, 0.7))
    grip = k.box('chest', 'body', 'grip', (5.1, 4.6, -2.1), (1, 3, 1))
    all_faces(L1, grip, lambda c: hexc('#2a1c14') if c.r else silver)
    scab = k.box('chest', 'body', 'scabbard', (5.1, 9.4, -1.6), (1, 9, 1), rot=(0.35, 0, 0))
    all_faces(L1, scab, lambda c: silver if c.r in (0, 8) else hexc('#1e1614'))
    L1.strip('knot', lambda c: gold if c.r == c.fh - 1 and c.s % 2 == 0 else None)


def _axe_more(k, L1, L2):
    F = fur(hexc('#4c4238'), hexc('#6e6050'), hexc('#2e2620'), hexc('#9a8a70'))
    iron = hexc('#7a7f86')
    # A fur collar over the lamellar, rivets on the plates, knotwork on the belt.
    collar = k.box('chest', 'body', 'collar', (-5.6, -2.4, -1.7), (11, 3, 5))
    all_faces(L1, collar, F)
    ragged_bottom(L1, collar, 1)
    L1.strip('pauldron', lambda c: mul(iron, 1.3) if c.r % 2 == 0 and c.s % 3 == 1 else None)
    L1.strip('vest', lambda c: mul(iron, 1.25) if c.r % 3 == 0 and c.s % 4 == 2 else None)
    L1.strip('belt', lambda c: hexc('#9a7a44') if (c.s + c.r) % 3 == 0 else None)
    for i in range(3):
        L1.strip('helm%d' % i, lambda c: hexc('#d8b860') if c.r == 0 and c.s % 4 == 1 else None)
    # Braided leather ties on the arms.
    tie = k.box('chest', 'arm', 'tie', (-4.2, 6.5, -3.2), (6, 2, 6), grow=0.2)
    all_faces(L1, tie, lambda c: hexc('#5a3a22') if (c.s + c.r) % 2 else hexc('#8a6238'))


def _paladin_more(k, L1, L2):
    gold, gold_l, gold_d = hexc('#c9a032'), hexc('#ead27a'), hexc('#7a5c18')
    # A golden sun embroidered on the back of the cloak.
    L1.art('cloak', 'back', [
        '.....g.....',
        '..g..g..g..',
        '...g.g.g...',
        '....GGG....',
        'gggGGdGGggg',
        '....GGG....',
        '...g.g.g...',
        '..g..g..g..',
        '.....g.....',
    ], {'g': gold, 'G': gold_l, 'd': gold_d}, y=3)
    # Filigree on the shoulder pieces, a zigzag border on the surcoat.
    L1.strip('pauldron', lambda c: gold_l if c.r == 0 and c.s % 2 == 0 else None)
    L1.cap('pauldron', lambda c: gold if (c.fx + c.fy) % 3 == 0 else None)
    L1.strip('tabard', lambda c: (gold if (c.fx + c.r) % 2 == 0 else gold_d) if c.face == 'front' and c.r >= 13 else None)
    # A holy book on a chain at the hip.
    book = k.box('chest', 'body', 'book', (4.5, 8.4, -2), (2, 3, 3))
    all_faces(L1, book, lambda c: hexc('#4a1c14') if c.face not in ('top',) else hexc('#e8e0c8'))
    L1.art(book, 'right', ['ggg', 'g.g', 'ggg'], {'g': gold})


_then('berserker', _berserker_more)
_then('blademaster', _blademaster_more)
_then('axe', _axe_more)
_then('paladin', _paladin_more)
