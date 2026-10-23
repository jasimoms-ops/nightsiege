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
    mantle = k.box('chest', 'body', 'mantle', (-5.5, -2.5, -3.5), (11, 4, 7))
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
    ruff = k.box('chest', 'body', 'ruff', (-5, -2.8, -3.6), (10, 2, 7))
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
