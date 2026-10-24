"""
The sixteen class outfits, painted in the spirit of medieval clothing mods:
wool, linen and leather in earthy colours, woven trims, belts and buckles,
fur, a little metal. Each outfit paints layer 1 (helmet, chestplate, boots)
and layer 2 (leggings).
"""
from paint import (Tex, hexc, mul, mix, cloth, shaded, quilt, mail, fur, leather, metal, stripes,
                   solid, lozenge, rows, outline_hem, SIDES)

OUTFITS = {}


def outfit(name):
    def reg(fn):
        OUTFITS[name] = fn
        return fn
    return reg


# ---------------------------------------------------------------------- shared pieces

def sole(L1, col):
    L1.cap('leg', col, face='bottom')
    L1.strip('leg', lambda c: mul(col, 1.0), rows=(11, 11))


def plain_boots(L1, top, mat, sole_col, cuff=None):
    """Boots from a row down to the sole; an optional turned-down cuff on top."""
    L1.strip('leg', mat, rows=(top, 11))
    if cuff is not None:
        L1.strip('leg', cuff, rows=(top, top + 1))
    outline_hem(L1, 'leg', top, 0.8)
    sole(L1, sole_col)


def hand(L1, mat):
    L1.cap('arm', mat, face='bottom')


def belt(tex, row, strap, buckle, buckle_hi=None, wide=1):
    tex.strip('body', strap, rows=(row, row + wide - 1))
    pal = {'b': buckle, 'h': buckle_hi or mul(buckle, 1.3), 'd': mul(buckle, 0.6)}
    tex.art('body', 'front', ['..dhbd..'] if wide == 1 else ['..dhbd..', '..dbbd..'], pal, y=row)


# ---------------------------------------------------------------------- warriors

@outfit('berserker')
def berserker(L1, L2):
    """Wolf pelt over a dark wool tunic, leather straps, a horned skull as a helm."""
    F = fur(hexc('#5f5145'), hexc('#7f6f5d'), hexc('#41372e'), hexc('#a8977d'))
    tunic = shaded(hexc('#3b302a'))
    red, redl = hexc('#7e241e'), hexc('#a8382a')
    strap, strap_l = hexc('#5a3a22'), hexc('#80562f')
    bone, bone_d, bone_s = hexc('#e4dac1'), hexc('#b3a68a'), hexc('#6e6450')
    socket = hexc('#110e0b')
    hide, hide_l = hexc('#4a3426'), hexc('#634733')

    # Chest: tunic, a red woven hem, the pelt over the shoulders and down the back.
    L1.strip('body', tunic)
    L1.strip('body', lozenge(redl, red), rows=(10, 11))
    L1.strip('body', F, rows=(0, 2))
    L1.strip('body', lambda c: F(c) if c.r <= 2 + (c.s * 3) % 2 else None, rows=(3, 3))
    L1.strip('body', lambda c: F(c) if c.r <= 9 + (c.s * 5 % 3) else None, faces=('back',))
    L1.cap('body', F)
    L1.art('body', 'front', [
        'ss....ss',
        '.ss..ss.',
        '..sbbs..',
        '..sbBs..',
        '.ss..ss.',
    ], {'s': leather(strap), 'b': bone, 'B': bone_d}, y=3)
    belt(L1, 8, leather(strap_l), bone, bone, wide=1)
    # Arms: fur on the shoulders, dark sleeves, laced leather bracers.
    L1.strip('arm', F, rows=(0, 3))
    L1.strip('arm', lambda c: F(c) if (c.s % 3) != 1 else None, rows=(4, 4))
    L1.strip('arm', tunic, rows=(5, 7))
    L1.strip('arm', lambda c: socket if (c.s % 4 == 1 and c.r % 2 == 0) else hexc('#b9a68a') if (c.s % 4 == 1) else leather(hide_l)(c), rows=(8, 11))
    outline_hem(L1, 'arm', 8, 0.75)
    L1.cap('arm', F)
    hand(L1, leather(hide))
    # Helm: the top of a beast's skull over the brow, the rest of its hide as a hood.
    L1.strip('head', F)
    L1.cap('head', lambda c: bone if c.rnd.random() > 0.12 else bone_d)
    L1.art('head', 'top', ['........', '...s....', '...ss...', '....s...', '........', '........', 'ffffffff', 'ffffffff'],
           {'s': bone_s, 'f': F})
    L1.art('head', 'front', [
        'BBBBBBBB',
        'BbBBBBbB',
        'BSSBBSSB',
        'bSoBBoSb',
        'Ft.bb.tF',
        'F______F',
        'F______F',
        'F______F',
    ], {'B': bone, 'b': bone_d, 'S': socket, 'o': hexc('#3a2a1c'), 't': hexc('#efe7d2'), 'F': F})
    for side in ('right', 'left'):
        art = ['ffffbbbb', 'ffffffbb', 'ffffffff', 'ffffffff', 'ffffffff', 'ffffffff', 'ffffffff', 'ffffffff']
        pal = {'f': F, 'b': bone}
        if side == 'right':
            L1.art('head', side, art, pal)
        else:
            L1.mirror_art('head', side, art, pal)
    # Horns on the larger hat box, and shaggy fur at the back.
    horn, horn_d, horn_t = hexc('#cfc3a2'), hexc('#8f8268'), hexc('#3d3428')
    hpal = {'h': horn, 'd': horn_d, 't': horn_t}
    L1.art('hat', 'right', ['.tdh....', 'dh.hh...', 'h...dh..', '.....d..'], hpal, x=0)
    L1.mirror_art('hat', 'left', ['.tdh....', 'dh.hh...', 'h...dh..', '.....d..'], hpal, x=0)
    L1.art('hat', 'front', ['h......h', 'd......d'], hpal)
    L1.strip('hat', lambda c: F(c) if c.rnd.random() < 0.7 else None, rows=(4, 7), faces=('back',))
    # Leggings: hide trousers with cords at the knee.
    L2.strip('leg', leather(hide))
    L2.strip('leg', lambda c: hexc('#b9a68a') if (c.s + c.r) % 3 == 0 else leather(hide_l)(c), rows=(6, 7))
    L2.strip('body', leather(hide), rows=(6, 11))
    belt(L2, 7, leather(strap_l), bone, wide=1)
    # Boots: fur wraps, bound with cord, on hide soles.
    L1.strip('leg', lambda c: F(c) if c.r >= 7 + (c.s * 2) % 2 else None, rows=(7, 10))
    L1.strip('leg', lambda c: hexc('#b9a68a') if (c.s + c.r) % 4 == 0 else F(c), rows=(9, 10))
    sole(L1, hide)


@outfit('blademaster')
def blademaster(L1, L2):
    """A fencing master: blue doublet with silver buttons, slashed white sleeves, a sash, a feathered beret."""
    blue = hexc('#2c4482')
    blue_l, blue_d = hexc('#4362aa'), hexc('#1c2b57')
    white, white_d = hexc('#e9e3d3'), hexc('#bdb5a2')
    silver = hexc('#dde2ea')
    black = hexc('#262020')
    sash, sash_l = hexc('#9c2832'), hexc('#c23c42')
    gold = hexc('#cfa83e')
    D = shaded(blue)

    L1.strip('body', D)
    L1.cap('body', D)
    L1.strip('body', cloth(white), rows=(0, 0))
    L1.art('body', 'front', [
        'w......w',
        '.w.pp.w.',
        '...pp...',
        '...sp...',
        '...pp...',
        '...sp...',
        '...pp...',
        '...sp...',
    ], {'w': white, 'p': mul(blue, 1.15), 's': silver}, y=0)
    # The sash, from the right shoulder to the left hip and round the back.
    L1.art('body', 'front', ['rr......', '.rR.....', '..rR....', '...rR...', '....rR..', '.....rR.', '......rR', '.......r'],
           {'r': sash, 'R': sash_l}, y=1)
    L1.mirror_art('body', 'back', ['rr......', '.rR.....', '..rR....', '...rR...', '....rR..', '.....rR.', '......rR', '.......r'],
                  {'r': mul(sash, 0.9), 'R': sash}, y=1)
    belt(L1, 9, leather(black), gold)
    L1.strip('body', lambda c: blue_d if c.r == 11 else D(c), rows=(10, 11))
    L1.strip('body', lambda c: gold if c.r == 11 and c.s % 3 == 0 else None, rows=(11, 11))
    # Sleeves: blue shoulder rolls, puffed white sleeves with blue slashes, blue cuffs, black gloves.
    L1.strip('arm', D, rows=(0, 1))
    L1.strip('arm', lambda c: gold if c.s % 2 == 0 else blue_d, rows=(2, 2))
    L1.strip('arm', lambda c: mul(blue_l, 1 - 0.04 * c.r) if c.s % 3 == 0 else mul(white, 1.04 - 0.03 * (c.r - 3)), rows=(3, 7))
    L1.strip('arm', solid(blue_d), rows=(8, 8))
    L1.strip('arm', leather(hexc('#3a302b')), rows=(9, 9))
    L1.strip('arm', leather(black), rows=(10, 11))
    L1.cap('arm', D)
    hand(L1, black)
    # Beret: band round the head, the cap puffed out on the larger hat box, a white feather.
    L1.strip('head', lambda c: D(c) if c.r <= 1 else None)
    L1.strip('head', lambda c: blue_d, rows=(1, 1))
    L1.cap('head', D)
    L1.strip('hat', lambda c: D(c) if c.r <= (1 if c.face != 'right' else 2) else None)
    L1.cap('hat', D)
    L1.art('hat', 'front', ['......g.', '.....gYg'], {'g': gold, 'Y': hexc('#f6e7a0')})
    feather, feather_d = hexc('#f4f1e8'), hexc('#a9a396')
    L1.art('hat', 'left', ['_fFf_...', 'f_fFFf..', '____fFf_', '______fF'], {'f': feather, 'F': feather_d})
    # Leggings: paned trunk hose over dark hose.
    L2.strip('leg', lambda c: (mul(blue, 1.05 - 0.05 * c.r) if c.s % 2 == 0 else mul(white, 1.02 - 0.04 * c.r)), rows=(0, 4))
    L2.strip('leg', solid(blue_d), rows=(4, 4))
    L2.strip('leg', cloth(hexc('#2b2a31'), 0.05), rows=(5, 11))
    L2.strip('body', D, rows=(7, 11))
    # Tall riding boots with turned-down tops.
    plain_boots(L1, 6, leather(black), hexc('#151212'), cuff=leather(hexc('#6e4b31')))
    L1.strip('leg', lambda c: silver if c.face == 'right' and c.fx == 1 else None, rows=(9, 9))


@outfit('axe')
def axe(L1, L2):
    """A northern axeman: rust wool tunic with woven trim, a leather lamellar vest, a spangenhelm with mail."""
    rust = hexc('#8f4a22')
    T = shaded(rust)
    trim = lozenge(hexc('#d8c8a0'), hexc('#2f4a32'))
    lam, lam_l, lam_d = hexc('#5b3b24'), hexc('#83593a'), hexc('#36230f')
    iron = hexc('#8b9097')
    brass = hexc('#b08a3a')
    M = mail(hexc('#80868e'))

    def lamellar(c):
        # Little plates two wide, two tall, every other row shifted.
        px = (c.s + (c.r // 2) % 2) % 2
        py = c.r % 2
        if py == 1:
            return lam_d if px == 0 else lam
        return lam_l if px == 0 else mul(lam, 1.05)

    L1.strip('body', T)
    L1.cap('body', lamellar)
    L1.strip('body', trim, rows=(0, 0))
    L1.strip('body', lamellar, rows=(1, 7), faces=('front', 'back'), where=lambda c: 1 <= c.fx <= 6)
    belt(L1, 8, leather(hexc('#3a2616')), iron)
    L1.strip('body', trim, rows=(10, 11))
    L1.strip('arm', lamellar, rows=(0, 2))
    outline_hem(L1, 'arm', 2, 0.7)
    L1.strip('arm', T, rows=(3, 7))
    L1.strip('arm', trim, rows=(8, 8))
    L1.strip('arm', lambda c: hexc('#c9c5b8') if (c.s + c.r) % 3 == 0 else leather(hexc('#6b4a2c'))(c), rows=(9, 11))
    L1.cap('arm', lamellar)
    hand(L1, leather(hexc('#4a3220')))
    # Spangenhelm: an iron bowl with brass bands, a nasal, mail hanging round the neck.
    I = metal(iron)
    L1.strip('head', lambda c: I(c) if c.r <= 2 else M(c))
    L1.strip('head', lambda c: brass if c.s % 4 == 3 else None, rows=(0, 2))
    L1.strip('head', lambda c: mul(iron, 0.7) if c.s % 2 else mul(iron, 0.95), rows=(3, 3))
    L1.cap('head', lambda c: brass if c.fx in (3, 4) or c.fy in (3, 4) else I(c))
    L1.art('head', 'front', [
        '........',
        '........',
        '........',
        '........',
        'm__nn__m',
        'm__nn__m',
        'm______m',
        'mm____mm',
    ], {'m': M, 'n': mul(iron, 1.1)})
    # Leggings: green-grey wool trousers, wrapped from knee to ankle.
    L2.strip('leg', cloth(hexc('#48543d')))
    L2.strip('leg', lambda c: hexc('#7a6a50') if (c.s + c.r) % 3 == 0 else cloth(hexc('#a99a7a'), 0.04)(c), rows=(5, 11))
    L2.strip('body', cloth(hexc('#48543d')), rows=(6, 11))
    L2.strip('body', lambda c: hexc('#c9b98f') if c.s % 2 else hexc('#5b4a30'), rows=(8, 8))
    # Turnshoes.
    plain_boots(L1, 9, leather(hexc('#5a3a22')), hexc('#2c1c10'))
    L1.strip('leg', lambda c: hexc('#c9b98f') if c.face == 'front' and c.fx in (1, 2) else None, rows=(9, 9))


@outfit('paladin')
def paladin(L1, L2):
    """A white and gold surcoat with a sun cross over mail, a mail coif with a gold circlet."""
    white, white_d = hexc('#e8e3d6'), hexc('#bdb6a3')
    gold, gold_l, gold_d = hexc('#d4ae38'), hexc('#f2d77c'), hexc('#8f7020')
    M = mail(hexc('#959ba3'))
    W = shaded(white, 1.04, 0.86)
    brown = hexc('#5a3c26')

    L1.strip('body', M)
    L1.cap('body', M)
    L1.strip('body', W, rows=(1, 11), faces=('front', 'back'))
    L1.strip('body', lambda c: gold if c.fx in (0, c.fw - 1) else None, rows=(1, 11), faces=('front', 'back'))
    L1.strip('body', lambda c: gold_d if c.s % 2 else gold, rows=(11, 11), faces=('front', 'back'))
    cross = [
        '...gG...',
        '...gG...',
        '.gggGGg.',
        '.dddgdd.',
        '...gG...',
        '...dg...',
    ]
    pal = {'g': gold, 'G': gold_l, 'd': gold_d}
    L1.art('body', 'front', cross, pal, y=2)
    L1.art('body', 'back', ['...gG...', '..gggG..', '...dg...'], pal, y=3)
    belt(L1, 8, leather(brown), gold, gold_l)
    L1.strip('arm', W, rows=(0, 1))
    L1.strip('arm', lambda c: gold if c.s % 2 == 0 else gold_d, rows=(2, 2))
    L1.strip('arm', M, rows=(3, 8))
    L1.strip('arm', lambda c: gold_l if c.s % 2 == 0 else gold, rows=(9, 9))
    L1.strip('arm', leather(hexc('#6a4a30')), rows=(10, 11))
    L1.cap('arm', W)
    hand(L1, leather(hexc('#4e3622')))
    # Mail coif with an open face; a gold circlet with a red stone on the hat box.
    L1.strip('head', M)
    L1.cap('head', M)
    L1.art('head', 'front', ['........', '........', '.______.', '.______.', '.______.', '.______.', '.______.', '..____..'], {})
    L1.strip('hat', lambda c: gold_l if c.s % 3 == 0 else gold, rows=(1, 1))
    L1.strip('hat', lambda c: gold_d, rows=(2, 2), where=lambda c: c.s % 2 == 0)
    L1.art('hat', 'front', ['...gg...', '..gRrg..', '...dd...'], {'g': gold_l, 'R': hexc('#e04040'), 'r': hexc('#9a1c1c'), 'd': gold_d})
    # Leggings: mail chausses under the split skirt of the surcoat.
    L2.strip('leg', M)
    L2.strip('leg', W, rows=(0, 4), faces=('front', 'back'))
    L2.strip('leg', lambda c: gold if c.s % 2 == 0 else gold_d, rows=(5, 5), faces=('front', 'back'))
    L2.strip('body', W, rows=(6, 11))
    # Steel sabatons with a gold rivet.
    steel = metal(hexc('#b4bac3'))
    plain_boots(L1, 8, steel, hexc('#4a4e55'))
    L1.strip('leg', lambda c: mul(hexc('#b4bac3'), 0.6), rows=(9, 9))
    L1.strip('leg', lambda c: gold if c.face == 'front' and c.fx in (1, 2) else None, rows=(8, 8))


# ---------------------------------------------------------------------- more shared pieces

def hood(L1, mat, rim=None, open_rows=(2, 7), open_cols=(1, 6), point=True):
    """A cloth hood: covers the head, open for the face, a rim on the larger hat box."""
    L1.strip('head', mat)
    L1.cap('head', mat)
    x1, x2 = open_cols
    y1, y2 = open_rows
    L1.strip('head', 'clear', faces=('front',), where=lambda c: x1 <= c.fx <= x2 and y1 <= c.fy <= y2)
    if rim is not None:
        L1.strip('hat', rim, faces=('front',), where=lambda c: (c.fx in (x1 - 1, x2 + 1) and c.fy >= y1 - 1) or c.fy == y1 - 1)
        L1.strip('hat', lambda c: rim(c) if c.fy == y1 - 1 else None, faces=('right', 'left'), where=lambda c: c.fx in ((6, 7) if c.face == 'right' else (0, 1)))
    if point:
        # The tip of the hood falls down the back on the hat box.
        L1.strip('hat', mat, faces=('back',), where=lambda c: 2 <= c.fx <= 5 and c.fy <= 3)


def quiver_back(L1, wood, leather_col, fletch):
    """A quiver on the back with feathered shafts sticking out at the top."""
    L1.art('body', 'back', [
        '....fF..',
        '....ff..',
        '...qQq..',
        '...qQq..',
        '...qQq..',
        '...qwq..',
        '...qQq..',
        '...qQq..',
        '....q...',
    ], {'f': fletch, 'F': mul(fletch, 0.7), 'q': leather_col, 'Q': mul(leather_col, 1.2), 'w': wood}, y=0)


def strap_diag(L1, col, hi, flip=False, y=0, face='front'):
    lines = ['s.......', 'Ss......', '.Ss.....', '..Ss....', '...Ss...', '....Ss..', '.....Ss.', '......Ss', '.......S']
    pal = {'s': col, 'S': hi}
    if flip:
        L1.mirror_art('body', face, lines, pal, y=y)
    else:
        L1.art('body', face, lines, pal, y=y)


def pouches(L1, row, col, flap):
    L1.art('body', 'front', ['pP....pP', 'pp....pp'], {'p': col, 'P': flap}, y=row)


# ---------------------------------------------------------------------- archers

@outfit('swift')
def swift(L1, L2):
    """A woodland runner: green hood and short cape, leather jerkin, quiver on the back."""
    green, green_l, green_d = hexc('#3e6a36'), hexc('#5a8a48'), hexc('#26421f')
    G = shaded(green)
    jerk, jerk_l = hexc('#6b4a2c'), hexc('#8a6238')
    L1.strip('body', G)
    L1.cap('body', G)
    L1.strip('body', leather(jerk), rows=(2, 9), faces=('front',), where=lambda c: c.fx not in (3, 4))
    L1.strip('body', lambda c: hexc('#d9c9a0') if c.fy in (3, 5, 7) else mul(jerk, 0.7), rows=(2, 9), faces=('front',),
             where=lambda c: c.fx in (3, 4))
    L1.strip('body', G, rows=(0, 9), faces=('back',))
    L1.strip('body', lambda c: green_d if c.r == 9 + (c.s % 2) else None, faces=('back',), rows=(9, 10))
    strap_diag(L1, leather(hexc('#4a3220')), hexc('#7a5634'), flip=True, y=1)
    belt(L1, 9, leather(hexc('#3a2616')), hexc('#b9a26a'))
    L1.strip('body', shaded(jerk), rows=(10, 11))
    quiver_back(L1, hexc('#9c7a4a'), hexc('#5a3a22'), hexc('#d8d2c2'))
    L1.strip('arm', G, rows=(0, 7))
    L1.strip('arm', lambda c: green_d if c.s % 2 else green, rows=(3, 3))
    L1.strip('arm', leather(hexc('#7a5634')), rows=(8, 11))
    L1.strip('arm', lambda c: hexc('#d9c9a0') if c.face == 'front' and c.r in (8, 10) else None, rows=(8, 11))
    L1.cap('arm', G)
    hand(L1, leather(hexc('#5a3a22')))
    hood(L1, shaded(green, 1.1, 0.85), rim=shaded(green_d), open_rows=(2, 7), open_cols=(1, 6))
    L1.strip('hat', lambda c: G(c) if c.fy >= 5 else None, faces=('back', 'right', 'left'))
    # Leggings: dark green trousers; soft tan boots.
    L2.strip('leg', cloth(hexc('#4a4632')))
    L2.strip('body', cloth(hexc('#4a4632')), rows=(6, 11))
    plain_boots(L1, 7, leather(hexc('#8a6a44')), hexc('#3a2a1a'), cuff=leather(hexc('#a07c50')))
    L1.strip('leg', lambda c: hexc('#5a3a22') if (c.s + c.r) % 3 == 0 else None, rows=(9, 10))


@outfit('heavy')
def heavy(L1, L2):
    """A longbowman in a padded jack, red and white livery, a steel cap over a linen coif."""
    jack = hexc('#a07a44')
    red, red_d = hexc('#9a2a24'), hexc('#6a1a16')
    white = hexc('#e6dfcf')
    steel = hexc('#9aa0a8')
    L1.strip('body', quilt(jack))
    L1.cap('body', quilt(jack))
    # Livery: white on the right, red on the left, front and back.
    L1.strip('body', lambda c: cloth(white)(c) if c.fx < 4 else cloth(red)(c), rows=(1, 9), faces=('front',))
    L1.strip('body', lambda c: cloth(red)(c) if c.fx < 4 else cloth(white)(c), rows=(1, 9), faces=('back',))
    L1.art('body', 'front', ['..yy....', '.yYYy...', '..yy....'], {'y': hexc('#d4ae38'), 'Y': hexc('#f2d77c')}, y=3)
    belt(L1, 8, leather(hexc('#4a3220')), steel)
    L1.strip('body', quilt(jack), rows=(10, 11))
    L1.art('body', 'front', ['......aA', '......aa', '.......a'], {'a': hexc('#e8e2d0'), 'A': hexc('#8a2a24')}, y=9)
    L1.strip('arm', quilt(jack), rows=(0, 8))
    L1.strip('arm', lambda c: red if c.s % 2 else red_d, rows=(0, 0))
    L1.strip('arm', leather(hexc('#5a3a22')), rows=(9, 11))
    L1.strip('arm', lambda c: hexc('#c9c2b0') if c.face == 'front' and c.r == 10 else None, rows=(10, 10))
    L1.cap('arm', quilt(jack))
    hand(L1, leather(hexc('#4a3220')))
    # Linen coif under a round steel cap.
    L1.strip('head', cloth(white))
    L1.cap('head', metal(steel))
    L1.strip('head', metal(steel), rows=(0, 2))
    L1.strip('head', lambda c: mul(steel, 0.65), rows=(2, 2))
    L1.art('head', 'front', ['........', '........', '........', '._____..', '_______.', '________', '________', '_w____w_'],
           {'w': white})
    L1.art('head', 'front', ['........', '........', '........', 'w______w', 'w______w', 'w______w', 'w______w', 'ww____ww'], {'w': cloth(white)})
    L1.strip('hat', lambda c: mul(steel, 0.8) if c.s % 3 else mul(steel, 1.1), rows=(2, 2))
    # Leggings: red-brown hose, ankle boots.
    L2.strip('leg', cloth(hexc('#6e3a2a')))
    L2.strip('body', cloth(hexc('#6e3a2a')), rows=(6, 11))
    plain_boots(L1, 9, leather(hexc('#5a3a22')), hexc('#2a1c12'))


@outfit('bolter')
def bolter(L1, L2):
    """A crossbow mercenary: striped and slashed doublet, a broad felt hat with a plume, striped hose."""
    orange, orange_d = hexc('#c0622a'), hexc('#8a4018')
    black, black_l = hexc('#2a2424'), hexc('#3e3634')
    cream = hexc('#e8dcc0')
    S = stripes(cloth(orange), cloth(black), width=2)
    L1.strip('body', S)
    L1.cap('body', cloth(black))
    # Slashes: short cream slits in the orange panes.
    L1.strip('body', lambda c: cream if (c.s // 2) % 2 == 0 and c.r in (2, 3, 5, 6) and c.s % 2 == 1 else None, rows=(1, 7))
    L1.strip('body', cloth(cream), rows=(0, 0))
    L1.art('body', 'front', ['...cc...', '...bb...'], {'c': cream, 'b': hexc('#c9a43a')}, y=1)
    belt(L1, 8, leather(hexc('#4a3220')), hexc('#c9a43a'), wide=2)
    L1.art('body', 'front', ['......qq', '......qQ'], {'q': hexc('#5a3a22'), 'Q': hexc('#c8c0a8')}, y=10)
    L1.strip('body', lambda c: black if c.r == 11 else orange_d, rows=(10, 11))
    # Puffed sleeves in bands, black cuffs.
    L1.strip('arm', lambda c: (orange if c.s % 2 == 0 else cream) if c.r <= 5 else (black_l if c.r % 2 else orange_d), rows=(0, 8))
    L1.strip('arm', lambda c: black, rows=(5, 5))
    L1.strip('arm', leather(hexc('#6b4a2c')), rows=(9, 11))
    L1.cap('arm', cloth(orange))
    hand(L1, leather(hexc('#4a3220')))
    # Broad felt hat with a white and red plume.
    felt = hexc('#3a2e2a')
    L1.strip('head', lambda c: cloth(felt)(c) if c.r <= 1 else None)
    L1.cap('head', cloth(felt))
    L1.strip('hat', lambda c: mul(felt, 0.9) if c.r == 2 else (cloth(felt)(c) if c.r <= 1 else None))
    L1.cap('hat', cloth(felt))
    L1.art('hat', 'right', ['....pPp.', '..pp..pP', '.p......'], {'p': hexc('#f2eee6'), 'P': hexc('#c23a32')})
    L1.art('hat', 'front', ['.....pP.'], {'p': hexc('#f2eee6'), 'P': hexc('#c23a32')})
    # Striped hose and wide cow-mouth shoes.
    L2.strip('leg', stripes(cloth(orange), cloth(black), width=1))
    L2.strip('leg', lambda c: cream if c.r in (2, 3) and c.s % 2 == 0 else None, rows=(2, 3))
    L2.strip('body', S, rows=(6, 11))
    plain_boots(L1, 10, leather(hexc('#2e2420')), hexc('#161010'))


@outfit('trapper')
def trapper(L1, L2):
    """A trapper: fur cap with a tail, patched hide coat with fringes, a green scarf, pouches and rope."""
    hide, hide_l, hide_d = hexc('#7a5634'), hexc('#9a7046'), hexc('#4e3420')
    F = fur(hexc('#6e5a44'), hexc('#8e7a60'), hexc('#3e3226'), hexc('#c8b896'))
    scarf = hexc('#3e6a3e')
    H = leather(hide)
    L1.strip('body', H)
    L1.cap('body', F)
    L1.strip('body', F, rows=(0, 1))
    L1.strip('body', lambda c: cloth(scarf)(c) if c.face == 'front' and c.fy == 2 or (c.face == 'front' and c.fy == 3 and c.fx in (2, 3)) else None, rows=(2, 3))
    L1.art('body', 'front', ['..pP....', '..pp....'], {'p': hide_l, 'P': hexc('#c9b48a')}, y=5)
    L1.art('body', 'back', ['.pp.....', '.pP.....'], {'p': hide_l, 'P': hexc('#c9b48a')}, y=6)
    # A coil of rope over the shoulder.
    L1.art('body', 'front', ['.....rr.', '....r..r', '....r..r', '.....rr.'], {'r': hexc('#c9b48a')}, y=3)
    belt(L1, 8, leather(hexc('#3a2616')), hexc('#9aa0a8'))
    pouches(L1, 9, hide_d, hide_l)
    L1.strip('body', lambda c: hide_d if (c.s % 2 == 0 and c.r == 11) else None, rows=(11, 11))
    L1.strip('arm', F, rows=(0, 1))
    L1.strip('arm', H, rows=(2, 11))
    L1.strip('arm', lambda c: hide_d if (c.face == 'right' and c.r % 2 == 0 and c.r > 2) else None)
    L1.strip('arm', F, rows=(10, 11))
    L1.cap('arm', F)
    hand(L1, leather(hide_d))
    # Fur cap: the cap on the head box, a striped tail down the back on the hat box.
    L1.strip('head', lambda c: F(c) if c.r <= 2 else None)
    L1.cap('head', F)
    L1.art('hat', 'back', ['...dd...', '...ll...', '...dd...', '...ll...', '...dd...', '...lD...'],
           {'d': hexc('#3a2e24'), 'l': hexc('#9a8668'), 'D': hexc('#1e1814')}, y=1)
    L1.strip('hat', lambda c: F(c) if c.r == 2 and c.rnd.random() < 0.6 else None)
    # Leggings: dark hide, fur-lined boots.
    L2.strip('leg', leather(hide_d))
    L2.strip('leg', lambda c: hide_l if c.face == 'right' and c.fx == 3 and c.r % 2 == 0 else None)
    L2.strip('body', leather(hide_d), rows=(6, 11))
    plain_boots(L1, 7, leather(hexc('#5a3a22')), hexc('#2a1c12'), cuff=F)


# ---------------------------------------------------------------------- guardians

@outfit('shieldfighter')
def shieldfighter(L1, L2):
    """A shield guard: quilted gambeson, a blue and gold tabard with a shield badge, a kettle hat."""
    gamb = hexc('#d6cfbd')
    blue, blue_d = hexc('#2f4f8a'), hexc('#1f3560')
    gold, gold_d = hexc('#d4ae38'), hexc('#8f7020')
    steel = hexc('#9aa0a8')
    Q = quilt(gamb)
    L1.strip('body', Q)
    L1.cap('body', Q)
    L1.strip('body', lambda c: cloth(blue)(c) if c.fx < 4 else cloth(gold)(c), rows=(0, 10), faces=('front',),
             where=lambda c: 1 <= c.fx <= 6)
    L1.strip('body', lambda c: cloth(gold)(c) if c.fx < 4 else cloth(blue)(c), rows=(0, 10), faces=('back',),
             where=lambda c: 1 <= c.fx <= 6)
    L1.art('body', 'front', ['..wWWw..', '..wBGw..', '..wBGw..', '...wW...'],
           {'w': hexc('#efe9da'), 'W': hexc('#c9c2b0'), 'B': blue_d, 'G': gold_d}, y=3)
    belt(L1, 8, leather(hexc('#4a3220')), steel)
    L1.strip('body', lambda c: blue_d if c.face in ('front', 'back') and 1 <= c.fx <= 6 else None, rows=(10, 10))
    L1.strip('arm', Q, rows=(0, 9))
    L1.strip('arm', mail(hexc('#8a9098')), rows=(0, 1))
    L1.strip('arm', leather(hexc('#6b4a2c')), rows=(10, 11))
    L1.cap('arm', mail(hexc('#8a9098')))
    hand(L1, leather(hexc('#4a3220')))
    # Kettle hat: a steel dome, its brim as a ring on the larger hat box, mail at the neck.
    S = metal(steel)
    L1.strip('head', lambda c: S(c) if c.r <= 2 else (mail(hexc('#8a9098'))(c) if c.face != 'front' else None))
    L1.cap('head', S)
    L1.strip('head', lambda c: mail(hexc('#8a9098'))(c) if c.fx in (0, 7) and c.fy >= 3 else None, faces=('front',))
    L1.strip('hat', lambda c: mul(steel, 1.15) if c.s % 4 else mul(steel, 0.75), rows=(2, 2))
    L1.strip('hat', lambda c: mul(steel, 0.6), rows=(3, 3), where=lambda c: c.s % 2 == 0)
    # Leggings: blue hose; leather boots.
    L2.strip('leg', cloth(hexc('#34405e')))
    L2.strip('body', cloth(hexc('#34405e')), rows=(6, 11))
    plain_boots(L1, 8, leather(hexc('#5a3a22')), hexc('#2a1c12'))


@outfit('bulwark')
def bulwark(L1, L2):
    """A living wall: riveted brigandine, plate shoulders and gauntlets, a closed bascinet."""
    cloth_c = hexc('#6a2424')
    brass = hexc('#c9a43a')
    steel = hexc('#a7adb6')
    S = metal(steel)
    B = cloth(cloth_c)
    L1.strip('body', lambda c: brass if (c.s % 2 == 0 and c.r % 2 == 1) else B(c))
    L1.cap('body', S)
    L1.strip('body', S, rows=(0, 1))
    L1.strip('body', lambda c: mul(steel, 0.6), rows=(1, 1), where=lambda c: c.s % 3 == 0)
    belt(L1, 8, leather(hexc('#3a2616')), brass, wide=1)
    L1.strip('body', mail(hexc('#8a9098')), rows=(10, 11))
    L1.strip('arm', S, rows=(0, 3))
    L1.strip('arm', lambda c: mul(steel, 0.55), rows=(1, 1))
    L1.strip('arm', lambda c: mul(steel, 0.55), rows=(3, 3))
    L1.strip('arm', mail(hexc('#8a9098')), rows=(4, 7))
    L1.strip('arm', S, rows=(8, 11))
    L1.strip('arm', lambda c: mul(steel, 0.55), rows=(9, 9))
    L1.cap('arm', S)
    hand(L1, mul(steel, 0.7))
    # Great helm: a brow band, a dark eye slit; the face plate and the ridge are 3D (outfits3d).
    L1.strip('head', S)
    L1.cap('head', S)
    L1.art('head', 'front', [
        '........',
        '.hhhhhh.',
        'dddddddd',
        'kkkkkkkk',
        'D......D',
        '........',
        '........',
        'D......D',
    ], {'h': mul(steel, 1.2), 'd': mul(steel, 0.7), 'D': mul(steel, 0.5), 'k': hexc('#0e0e12')})
    L1.strip('head', mail(hexc('#8a9098')), rows=(6, 7), faces=('right', 'left', 'back'))
    L1.strip('hat', lambda c: mul(steel, 1.2), rows=(0, 0), faces=('front',), where=lambda c: c.fx in (3, 4))
    # Leggings: mail with steel knee cops; plate sabatons.
    L2.strip('leg', mail(hexc('#8a9098')))
    L2.strip('leg', S, rows=(5, 7), faces=('front', 'right', 'left'))
    L2.strip('leg', lambda c: brass if c.face == 'front' and c.fx in (1, 2) else None, rows=(6, 6))
    L2.strip('body', mail(hexc('#8a9098')), rows=(6, 11))
    plain_boots(L1, 8, S, mul(steel, 0.5))
    L1.strip('leg', lambda c: mul(steel, 0.6), rows=(10, 10))


@outfit('medic')
def medic(L1, L2):
    """A field healer: grey-green robe, white apron with a green cross, a satchel, a linen cap."""
    robe = hexc('#5f6e5c')
    white, white_d = hexc('#ece6d8'), hexc('#c4bdac')
    green, green_d = hexc('#3a9a4a'), hexc('#246a30')
    R = shaded(robe)
    L1.strip('body', R)
    L1.cap('body', R)
    L1.strip('body', shaded(white, 1.04, 0.9), rows=(2, 11), faces=('front',), where=lambda c: 1 <= c.fx <= 6)
    L1.art('body', 'front', ['...gG...', '..gggG..', '...gG...'], {'g': green, 'G': green_d}, y=4)
    L1.strip('body', lambda c: white_d if c.face == 'front' and c.fy == 2 else None, rows=(2, 2))
    strap_diag(L1, leather(hexc('#6b4a2c')), hexc('#8a6238'), flip=False, y=0)
    L1.art('body', 'right', ['....', 'bbbb', 'bBBb', 'bbbb'], {'b': hexc('#7a5634'), 'B': hexc('#9a7046')}, y=7)
    belt(L1, 8, leather(hexc('#4a3220')), hexc('#9aa0a8'))
    # Sleeves rolled up over linen.
    L1.strip('arm', R, rows=(0, 4))
    L1.strip('arm', lambda c: mul(robe, 0.75) if c.s % 2 else mul(robe, 0.95), rows=(5, 5))
    L1.strip('arm', cloth(white), rows=(6, 9))
    L1.strip('arm', lambda c: green if c.face == 'right' and c.r == 7 and c.fx in (1, 2) else None, rows=(7, 7))
    L1.strip('arm', cloth(white_d), rows=(10, 11))
    L1.cap('arm', R)
    hand(L1, white_d)
    # A linen cap tied under the chin.
    L1.strip('head', lambda c: cloth(white)(c) if c.r <= 1 or (c.face != 'front' and c.r <= 5) else None)
    L1.cap('head', cloth(white))
    L1.strip('head', lambda c: green if c.r == 1 and c.face == 'front' and c.fx in (3, 4) else None, rows=(1, 1))
    L1.strip('head', lambda c: white_d if c.face == 'front' and c.fx in (0, 7) and c.fy <= 7 else None)
    # Robe skirt over grey trousers.
    L2.strip('leg', cloth(hexc('#5a5a58')))
    L2.strip('leg', R, rows=(0, 5))
    L2.strip('leg', lambda c: white if c.face == 'front' and c.r <= 5 else None, rows=(0, 5))
    L2.strip('body', R, rows=(6, 11))
    plain_boots(L1, 9, leather(hexc('#4a3a2c')), hexc('#241c14'))


@outfit('warden')
def warden(L1, L2):
    """A rune warden: deep violet hooded robe with silver runes, a mantle, rune stones at the belt."""
    violet, violet_d = hexc('#3e2a5a'), hexc('#26183a')
    silver = hexc('#c9d0de')
    glow = hexc('#b48cff')
    V = shaded(violet, 1.08, 0.8)
    L1.strip('body', V)
    L1.cap('body', V)
    L1.strip('body', lambda c: silver if c.face == 'front' and c.fx in (3, 4) and c.fy % 3 != 1 else None)
    L1.strip('body', lambda c: glow if c.face == 'front' and c.fx in (3, 4) and c.fy % 3 == 1 else None)
    L1.strip('body', shaded(violet_d), rows=(0, 3), faces=('right', 'left', 'back'))
    L1.strip('body', lambda c: silver if c.r == 3 and c.s % 2 == 0 else None, rows=(3, 3))
    belt(L1, 8, leather(hexc('#2a2030')), silver)
    L1.art('body', 'front', ['g......g', 's......s'], {'g': glow, 's': silver}, y=9)
    L1.strip('body', lambda c: silver if c.r == 11 and c.s % 3 == 1 else (glow if c.r == 11 and c.s % 3 == 2 else None), rows=(11, 11))
    L1.strip('arm', V, rows=(0, 11))
    L1.strip('arm', shaded(violet_d), rows=(0, 3))
    L1.strip('arm', lambda c: silver if c.r == 3 and c.s % 2 == 0 else None, rows=(3, 3))
    L1.strip('arm', lambda c: glow if c.face == 'right' and (c.r, c.fx) in ((6, 1), (7, 2), (8, 1), (7, 1)) else None)
    L1.strip('arm', lambda c: silver if c.r == 10 else (violet_d if c.r == 11 else None), rows=(10, 11))
    L1.cap('arm', shaded(violet_d))
    hand(L1, violet_d)
    # Deep hood, the face in its shadow.
    hood(L1, shaded(violet, 1.05, 0.8), rim=lambda c: violet_d, open_rows=(2, 7), open_cols=(1, 6))
    L1.art('head', 'front', ['........', '...gg...', '.ssssss.'], {'g': glow, 's': violet_d})
    L1.art('hat', 'front', ['...ss...'], {'s': silver})
    # Robe down to the shins, dark boots.
    L2.strip('leg', V)
    L2.strip('leg', lambda c: silver if c.r == 8 and c.s % 2 == 0 else None, rows=(8, 8))
    L2.strip('leg', cloth(hexc('#241c2c')), rows=(9, 11))
    L2.strip('body', V, rows=(6, 11))
    plain_boots(L1, 10, leather(hexc('#2a2230')), hexc('#140f18'))


# ---------------------------------------------------------------------- engineers

@outfit('gunner')
def gunner(L1, L2):
    """A master gunner: long leather coat with brass buttons, a bandolier of powder flasks, a crested morion."""
    coat, coat_l, coat_d = hexc('#5e3e26'), hexc('#7e5634'), hexc('#3a2616')
    brass, brass_l = hexc('#c09a40'), hexc('#ead27e')
    C = shaded(coat)
    L1.strip('body', C)
    L1.cap('body', C)
    L1.strip('body', lambda c: coat_d if c.face == 'front' and c.fx == 3 else None)
    L1.strip('body', lambda c: brass if c.face == 'front' and c.fx == 4 and c.fy % 2 == 1 and c.fy < 9 else None)
    L1.art('body', 'front', ['k.......', '.k......', 'f.k.....', '...k....', '..f.k...', '.....k..', '....f.k.', '.......k'],
           {'k': hexc('#2a1c12'), 'f': hexc('#cfc7b4')}, y=1)
    L1.strip('body', lambda c: coat_l if c.r == 0 else None, rows=(0, 0))
    belt(L1, 8, leather(hexc('#2a1c12')), brass, brass_l)
    L1.strip('arm', C, rows=(0, 8))
    L1.strip('arm', lambda c: coat_l if c.r == 0 else None)
    L1.strip('arm', lambda c: brass if c.face == 'front' and c.r == 7 else (coat_l if c.r in (7, 8) else None), rows=(7, 8))
    L1.strip('arm', leather(hexc('#a07a4a')), rows=(9, 11))
    L1.cap('arm', C)
    hand(L1, leather(hexc('#7a5634')))
    # Morion: a domed brass-trimmed steel helm with a crest along the top.
    steel = hexc('#a0a6ae')
    S = metal(steel)
    L1.strip('head', lambda c: S(c) if c.r <= 2 else None)
    L1.strip('head', lambda c: brass if c.r == 2 else None, rows=(2, 2))
    L1.cap('head', S)
    L1.cap('hat', lambda c: mul(steel, 1.25) if c.fx in (3, 4) else None)
    L1.strip('hat', lambda c: mul(steel, 1.1) if c.fx in (3, 4) and c.fy <= 0 else None, faces=('front', 'back'))
    L1.strip('hat', lambda c: brass if c.r == 3 and c.s % 2 == 0 else (mul(steel, 0.8) if c.r == 3 else None), rows=(3, 3))
    # Coat tails over dark breeches; tall boots.
    L2.strip('leg', cloth(hexc('#2e2a2a')))
    L2.strip('leg', C, rows=(0, 4), faces=('right', 'back', 'left'))
    L2.strip('leg', lambda c: coat_d if c.r == 4 else None, rows=(4, 4), faces=('right', 'back', 'left'))
    L2.strip('body', C, rows=(6, 11))
    plain_boots(L1, 6, leather(hexc('#2a2220')), hexc('#120e0c'), cuff=leather(hexc('#4a3a2c')))


@outfit('electrician')
def electrician(L1, L2):
    """A tinkerer: blue-grey work coat, copper coils on the shoulders, rubber gloves, goggles on a leather cap."""
    coat = hexc('#3e4a5a')
    copper, copper_l = hexc('#b8693a'), hexc('#e09a5e')
    spark = hexc('#7fe8ff')
    apron = hexc('#5a4030')
    C = shaded(coat)
    L1.strip('body', C)
    L1.cap('body', C)
    L1.strip('body', leather(apron), rows=(3, 11), faces=('front',), where=lambda c: 1 <= c.fx <= 6)
    L1.strip('body', lambda c: hexc('#2a1e16') if c.face == 'front' and c.fy == 3 and 1 <= c.fx <= 6 else None, rows=(3, 3))
    L1.art('body', 'front', ['.s....s.', 's......s'], {'s': hexc('#2a1e16')}, y=1)
    L1.art('body', 'front', ['..tT.w..', '..tt.W..'], {'t': hexc('#9aa0a8'), 'T': hexc('#d0d4da'), 'w': copper, 'W': copper_l}, y=5)
    L1.art('body', 'back', ['...kk...', '..k..k..', '...kk...', '..k..k..', '...kk...'], {'k': copper}, y=2)
    belt(L1, 9, leather(hexc('#2a1e16')), hexc('#9aa0a8'))
    L1.strip('arm', C, rows=(0, 8))
    L1.strip('arm', lambda c: copper_l if (c.s + c.r) % 2 == 0 else copper, rows=(0, 2))
    L1.strip('arm', lambda c: spark if c.face == 'right' and c.r == 1 and c.fx == 2 else None, rows=(1, 1))
    L1.strip('arm', leather(hexc('#1e2a22')), rows=(8, 11))
    L1.strip('arm', lambda c: hexc('#2e4436'), rows=(8, 8))
    L1.cap('arm', lambda c: copper_l if (c.fx + c.fy) % 2 == 0 else copper)
    hand(L1, hexc('#1e2a22'))
    # Leather cap with brass goggles pushed up on the brow.
    cap = hexc('#5a3e2a')
    L1.strip('head', lambda c: leather(cap)(c) if c.r <= 2 else None)
    L1.cap('head', leather(cap))
    L1.strip('head', lambda c: hexc('#2a1e16') if c.r == 2 else None, rows=(2, 2))
    L1.art('head', 'front', ['........', '.bBb.bBb', '.bLb.bLb'], {'b': hexc('#b08a3a'), 'B': hexc('#e0c070'), 'L': spark}, x=-1)
    # Work trousers with a tool loop; heavy boots.
    L2.strip('leg', cloth(hexc('#2e3440')))
    L2.strip('leg', lambda c: hexc('#5a4030') if c.face == 'right' and c.r in (2, 3, 4) and c.fx in (1, 2) else None)
    L2.strip('body', cloth(hexc('#2e3440')), rows=(6, 11))
    plain_boots(L1, 8, leather(hexc('#262022')), hexc('#0e0c0c'))
    L1.strip('leg', lambda c: spark if c.face == 'right' and c.r == 9 and c.fx == 2 else None, rows=(9, 9))


@outfit('demolition')
def demolition(L1, L2):
    """A blaster: thick sooty blast apron, a red scarf over the mouth, big amber goggles, a bandolier of bombs."""
    pad, pad_d = hexc('#4a4038'), hexc('#2e2822')
    red, red_d = hexc('#b0302a'), hexc('#7a1e1a')
    P = quilt(pad, 0.7, 2, 0.06)
    L1.strip('body', P)
    L1.cap('body', P)
    L1.strip('body', lambda c: pad_d if c.rnd.random() < 0.15 else None)
    L1.strip('body', cloth(red), rows=(0, 1), faces=('front', 'right', 'left'))
    L1.art('body', 'front', ['b.......', '.b......', 'oob.....', 'ooOb....', '....bo..', '....oObb', '......oo', '.......o'],
           {'b': hexc('#5a3a22'), 'o': hexc('#1e1c1c'), 'O': hexc('#ffb040')}, y=2)
    belt(L1, 9, leather(hexc('#3a2616')), hexc('#9aa0a8'))
    L1.strip('arm', P, rows=(0, 7))
    L1.strip('arm', lambda c: hexc('#1e1a16') if c.rnd.random() < 0.1 else None, rows=(3, 7))
    L1.strip('arm', leather(hexc('#6a5a44')), rows=(8, 11))
    L1.strip('arm', lambda c: mul(hexc('#6a5a44'), 1.3), rows=(8, 8))
    L1.cap('arm', P)
    hand(L1, leather(hexc('#4a3c2c')))
    # Leather hood, amber goggles, a red scarf up over nose and mouth.
    L1.strip('head', leather(hexc('#4e3a2a')))
    L1.cap('head', leather(hexc('#4e3a2a')))
    L1.art('head', 'front', [
        '........',
        'kkkkkkkk',
        'kAaKKAak',
        'kaaKKaak',
        '._____..',
        'rRrrrRrr',
        'rrrRrrrr',
        'rrrrrrRr',
    ], {'k': hexc('#2a2220'), 'A': hexc('#ffd070'), 'a': hexc('#d98a20'), 'K': hexc('#5a4a3a'), 'r': cloth(red), 'R': red_d})
    L1.art('head', 'front', ['........', '........', '........', '........', '________'], {})
    L1.strip('head', lambda c: hexc('#2a2220') if c.r == 1 else None, rows=(1, 1), faces=('right', 'left', 'back'))
    L1.strip('head', cloth(red), rows=(5, 7), faces=('right', 'left'))
    L1.strip('hat', lambda c: hexc('#2a2220') if c.r == 2 and c.face == 'front' and c.fx in (0, 7) else None)
    # Padded trousers; heavy scorched boots.
    L2.strip('leg', quilt(hexc('#3e3a34'), 0.75))
    L2.strip('body', quilt(hexc('#3e3a34'), 0.75), rows=(6, 11))
    plain_boots(L1, 8, leather(hexc('#2e2620')), hexc('#0e0c0a'))
    L1.strip('leg', lambda c: hexc('#141210') if c.rnd.random() < 0.25 else None, rows=(8, 10))


@outfit('alchemist')
def alchemist(L1, L2):
    """The plague doctor (Simon, 24 October 2026): a long black waxed coat with brass buttons,
    a leather hood under the beaked mask, gloves, a belt of glowing vials."""
    coat, coat_l, coat_d = hexc('#342d2a'), hexc('#5a4c44'), hexc('#1a1514')
    brass, brass_l = hexc('#b08a3a'), hexc('#e0c070')
    bone, bone_d = hexc('#d9cdb0'), hexc('#a8987a')
    glove = hexc('#3a2a1e')
    C = shaded(coat, 1.15, 0.8)
    L1.strip('body', C)
    L1.cap('body', C)
    # Buttoned down the front, a waxed sheen beside the buttons.
    L1.strip('body', lambda c: coat_d if c.face == 'front' and c.fx == 4 else None)
    L1.strip('body', lambda c: (brass_l if c.fy % 4 == 1 else brass) if c.face == 'front' and c.fx == 3 and c.fy % 2 == 1 and c.fy < 7 else None)
    L1.strip('body', lambda c: coat_l if c.face == 'front' and c.fx == 1 and 1 <= c.fy <= 6 else None)
    L1.strip('body', lambda c: coat_l if c.r == 0 else None, rows=(0, 0))
    strap_diag(L1, leather(hexc('#5a3a22')), hexc('#8a6238'), y=0)
    belt(L1, 7, leather(hexc('#4a3020')), brass, brass_l)
    L1.strip('body', lambda c: coat_d if c.face == 'back' and c.fx in (3, 4) and c.fy >= 8 else None)
    # Sleeves of the coat, long leather gloves with a turned cuff.
    L1.strip('arm', C, rows=(0, 7))
    L1.strip('arm', lambda c: coat_l if c.r == 0 else None, rows=(0, 0))
    L1.strip('arm', leather(glove), rows=(8, 11))
    L1.strip('arm', lambda c: mul(glove, 1.35) if c.r == 8 else None, rows=(8, 8))
    L1.cap('arm', C)
    hand(L1, leather(mul(glove, 0.8)))
    # A black leather hood; the face hidden by the ivory mask (beak and lenses are 3D).
    L1.strip('head', leather(coat_d))
    L1.cap('head', leather(coat_d))
    mask = lambda c: bone_d if c.rnd.random() < 0.18 else bone
    L1.art('head', 'front', [
        'kkkkkkkk',
        'kmmmmmmk',
        'kmmmmmmk',
        'kmmmmmmk',
        'kmmsmmmk',
        'kmmsmmmk',
        'kkmmmmkk',
        'kkkmmkkk',
    ], {'k': leather(coat_d), 'm': mask, 's': mul(bone, 0.6)})
    # The coat falls to the shins over dark breeches; tall boots.
    L2.strip('leg', cloth(hexc('#221e1e')))
    L2.strip('leg', C, rows=(0, 6), faces=('right', 'back', 'left'))
    L2.strip('leg', lambda c: coat_d if c.r == 6 else None, rows=(6, 6), faces=('right', 'back', 'left'))
    L2.strip('body', C, rows=(6, 11))
    plain_boots(L1, 7, leather(hexc('#241c18')), hexc('#0e0a08'), cuff=leather(hexc('#3a2c22')))


# ---------------------------------------------------------------------- summoners

@outfit('tamer')
def tamer(L1, L2):
    """The beast tamer: a wolf pelt for a hood, a leather jerkin with fur on the shoulders,
    a cord of claws and teeth, laced bracers, fur-wrapped boots."""
    F = fur(hexc('#7d7873'), hexc('#a7a19a'), hexc('#4d4945'), hexc('#d2cdc5'))
    jerk, jerk_l = hexc('#5e4029'), hexc('#7e5836')
    bone = hexc('#e4dac1')
    teal = hexc('#5fd8b8')
    L1.strip('body', leather(jerk))
    L1.cap('body', F)
    L1.strip('body', F, rows=(0, 1))
    L1.strip('body', lambda c: F(c) if c.r <= 1 + (c.s * 3) % 2 else None, rows=(2, 2))
    L1.strip('body', lambda c: F(c) if c.r <= 10 + (c.s * 5 % 3) else None, faces=('back',))
    L1.strip('body', lambda c: mul(jerk, 0.7) if c.face == 'front' and c.fx in (3, 4) and c.r >= 3 else None)
    L1.strip('body', lambda c: bone if c.face == 'front' and c.fx in (3, 4) and c.r in (4, 6) else None)
    belt(L1, 8, leather(hexc('#3a2616')), bone, bone)
    L1.art('body', 'front', ['t.......'], {'t': teal}, y=9)
    L1.strip('arm', F, rows=(0, 2))
    L1.strip('arm', leather(jerk_l), rows=(3, 7))
    L1.strip('arm', lambda c: hexc('#d9c9a0') if c.face == 'front' and c.r % 2 == 0 else leather(hexc('#4a3220'))(c), rows=(8, 11))
    L1.cap('arm', F)
    hand(L1, leather(hexc('#3a2616')))
    # The wolf's hide over the head, open for the face (its head sits on top, in 3D).
    hood(L1, F, rim=lambda c: mul(hexc('#4d4945'), 0.9), open_rows=(2, 7), open_cols=(1, 6), point=False)
    # Leggings: hide trousers; boots wrapped in fur and bound with cord.
    L2.strip('leg', leather(hexc('#4a3426')))
    L2.strip('leg', lambda c: hexc('#b9a68a') if (c.s + c.r) % 3 == 0 else None, rows=(6, 6))
    L2.strip('body', leather(hexc('#4a3426')), rows=(6, 11))
    L1.strip('leg', lambda c: F(c) if c.r >= 7 else None, rows=(7, 10))
    L1.strip('leg', lambda c: hexc('#b9a68a') if (c.s + c.r) % 4 == 0 else None, rows=(8, 10))
    sole(L1, hexc('#2e2218'))


@outfit('golemsmith')
def golemsmith(L1, L2):
    """The golemsmith: a slate work shirt under leather braces, a leather cap with goggles,
    heavy gloves, iron-capped boots; the runes glow like the staff."""
    shirt = hexc('#3e4654')
    leather_c, leather_d = hexc('#5a3a22'), hexc('#3a2616')
    iron = hexc('#a7adb6')
    teal = hexc('#5fd8b8')
    S = shaded(shirt, 1.1, 0.85)
    L1.strip('body', S)
    L1.cap('body', S)
    # Braces over the shoulders, a leather harness with a rune plate on the back.
    L1.strip('body', lambda c: leather(leather_c)(c) if c.face in ('front', 'back') and c.fx in (1, 6) else None, rows=(0, 7))
    L1.strip('body', lambda c: iron if c.face == 'front' and c.fx in (1, 6) and c.r == 2 else None)
    L1.strip('body', lambda c: leather(leather_d)(c) if c.face == 'back' and c.r == 3 else None, rows=(3, 3))
    belt(L1, 8, leather(leather_d), iron, mul(iron, 1.2), wide=1)
    L1.strip('arm', S, rows=(0, 5))
    L1.strip('arm', lambda c: mul(shirt, 0.8) if c.r == 5 else None, rows=(5, 5))
    L1.strip('arm', leather(hexc('#6b4a2c')), rows=(6, 11))
    L1.strip('arm', lambda c: mul(hexc('#6b4a2c'), 1.25) if c.r == 6 else None, rows=(6, 6))
    L1.cap('arm', S)
    hand(L1, leather(hexc('#4a3220')))
    # A leather cap: only the top rows, the face stays free (goggles in 3D).
    L1.strip('head', lambda c: leather(leather_c)(c) if c.r <= 1 else None)
    L1.strip('head', lambda c: mul(leather_c, 0.7) if c.r == 2 else None, faces=('back', 'right', 'left'))
    L1.cap('head', leather(leather_c))
    L1.art('head', 'top', ['........', '........', '...tt...', '...tt...'], {'t': teal}, y=2)
    # Leggings: dark work trousers with leather knee pads; boots with iron caps.
    L2.strip('leg', cloth(hexc('#2e3038')))
    L2.strip('leg', lambda c: leather(leather_c)(c) if c.face == 'front' else None, rows=(5, 7))
    L2.strip('body', cloth(hexc('#2e3038')), rows=(6, 11))
    plain_boots(L1, 8, leather(hexc('#3a2a1e')), hexc('#141210'), cuff=leather(hexc('#5a3a22')))


@outfit('necromancer')
def necromancer(L1, L2):
    """The necromancer: a black robe with violet runes, bone at the shoulders and on the chest,
    a deep hood; the dead obey him."""
    robe, robe_l, robe_d = hexc('#231c2e'), hexc('#3a2e4a'), hexc('#140f1c')
    violet, glow = hexc('#7a4ab8'), hexc('#b48cff')
    bone = hexc('#e4dac1')
    R = shaded(robe, 1.18, 0.8)
    L1.strip('body', R)
    L1.cap('body', R)
    # A violet rune band down the front, little bone toggles.
    L1.strip('body', lambda c: (glow if c.fy % 3 == 1 else violet) if c.face == 'front' and c.fx in (3, 4) else None)
    L1.strip('body', lambda c: bone if c.face == 'front' and c.fx in (2, 5) and c.fy in (2, 5, 8) else None)
    L1.strip('body', lambda c: robe_l if c.r == 0 else None, rows=(0, 0))
    belt(L1, 8, leather(hexc('#1e1612')), bone, bone)
    L1.strip('arm', R, rows=(0, 9))
    L1.strip('arm', lambda c: violet if c.r == 9 else None, rows=(9, 9))
    L1.strip('arm', lambda c: robe_d if c.r >= 10 else None, rows=(10, 11))
    L1.cap('arm', R)
    hand(L1, leather(hexc('#2a2026')))
    hood(L1, shaded(robe, 1.15, 0.82), rim=lambda c: violet, open_rows=(2, 7), open_cols=(1, 6), point=False)
    # The robe to the ankles; dark wraps.
    L2.strip('leg', cloth(hexc('#1a1520')))
    L2.strip('leg', R, rows=(0, 8), faces=('right', 'back', 'left'))
    L2.strip('leg', lambda c: violet if c.r == 8 else None, rows=(8, 8), faces=('right', 'back', 'left'))
    L2.strip('body', R, rows=(6, 11))
    plain_boots(L1, 8, leather(hexc('#1e1612')), hexc('#0a080c'))


@outfit('soulcatcher')
def soulcatcher(L1, L2):
    """The soulcatcher: a grey-blue hunting coat with chains, a hood and a cloth mask,
    soul jars and a soul lantern at the belt; the souls glow teal."""
    coat, coat_l, coat_d = hexc('#3b4a55'), hexc('#56697a'), hexc('#24303a')
    teal, teal_l = hexc('#6fe0d0'), hexc('#c8fff6')
    chain = hexc('#8a8e94')
    C = shaded(coat, 1.15, 0.82)
    L1.strip('body', C)
    L1.cap('body', C)
    L1.strip('body', lambda c: coat_d if c.face == 'front' and c.fx == 4 else None)
    # A chain across the chest from the left shoulder.
    L1.art('body', 'front', ['c.......', '.c......', '..c.....', '...c....', '....c...', '.....c..', '......c.', '.......c'],
           {'c': chain}, y=0)
    L1.strip('body', lambda c: coat_l if c.r == 0 else None, rows=(0, 0))
    belt(L1, 8, leather(hexc('#2a2420')), chain, mul(chain, 1.3))
    L1.strip('arm', C, rows=(0, 8))
    L1.strip('arm', lambda c: teal if c.face == 'front' and c.r == 7 and c.s % 2 == 0 else None, rows=(7, 7))
    L1.strip('arm', leather(hexc('#2a2420')), rows=(9, 11))
    L1.cap('arm', C)
    hand(L1, leather(hexc('#1e1a18')))
    hood(L1, shaded(coat, 1.12, 0.84), rim=lambda c: coat_d, open_rows=(2, 7), open_cols=(1, 6), point=True)
    # A dark cloth mask over mouth and nose, only the eyes stay free.
    L1.art('head', 'front', ['........', '........', '........', '........', '........', '.mmmmmm.', '.mmmmmm.', '..mmmm..'],
           {'m': cloth(hexc('#1c2228'))})
    # Leggings: dark trousers, high boots.
    L2.strip('leg', cloth(hexc('#232a30')))
    L2.strip('leg', C, rows=(0, 4), faces=('right', 'back', 'left'))
    L2.strip('body', C, rows=(6, 11))
    plain_boots(L1, 6, leather(hexc('#2a2420')), hexc('#0e0c0a'), cuff=leather(hexc('#3a322c')))
