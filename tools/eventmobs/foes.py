"""
Own looks for the update-1 foes (design update, Simon 28/29 October 2026: "the new monsters need
their own designs"): every foe is told apart by silhouette and colour, in every night (an event
night only tints them through the event's sky, their look stays). Built by build.py next to the
bosses; every foe is a folder "foe_<id>" and the client swaps a foe's textures for the folder's
(EventTextures, the server marks the foes like the bosses).

    revenant         zombie    grave-grey skin, a linen shroud, a candle burning on the head
    tunneler         zombie    earth-brown, a miner's helmet with a lamp, dirt on the arms
    iron_knight      zombie    full plate: visored helm with a crest, pauldrons, a tabard
    standard_bearer  pillager  crimson coat, a tall red standard on the back, gold trim
    grappler         pillager  dark leather, a rope coil over the shoulder, a hook at the belt
    treasure_raider  pillager  a golden hood, a coin sack, gold glinting on the coat
"""
import zlib
from PIL import Image
import skins
from gear import block, chain, sym, cluster, ring
from materials import hexc, mix, mul, ramp, noise
import materials

stops = skins.stops
NEUTRAL = dict(bone=stops('4a3b34', '9a8a7c', 'd8cbb6', 'f2e8d6'), creeper=stops('1e1a1a', '4a4040', '7d6f6a', 'a69890'),
               spider=stops('0d0707', '241010', '3d1818', '5a2424'))

PALETTES = {
    'foe_revenant': dict(skin=stops('1e2422', '485450', '7a8680', 'aab4ac'), cloth=stops('242220', '4a4640', '78726a', 'a8a298'),
                         pants=stops('16140f', '2c2820', '463e32', '5e5444'), eye=hexc('ffcf6a'), glow=hexc('e89a2a'),
                         detail=hexc('d8c8a0'), **NEUTRAL),
    'foe_tunneler': dict(skin=stops('1e1a12', '44402e', '6e6a4c', '969270'), cloth=stops('1c120a', '3e2a16', '664628', '8c6236'),
                         pants=stops('120c06', '281a0e', '402a18', '583c24'), eye=hexc('ffe28a'), glow=hexc('e8b040'),
                         detail=hexc('5a3e22'), **NEUTRAL),
    'foe_iron_knight': dict(skin=stops('22262c', '4a5058', '7a828c', 'b0b8c2'), cloth=stops('1a1e26', '3a4250', '5e6a7c', '8a96a8'),
                            pants=stops('14161a', '2a2e36', '444a56', '606874'), eye=hexc('ff4a3a'), glow=hexc('c02018'),
                            detail=hexc('a8202a'), **NEUTRAL),
    'foe_standard_bearer': dict(skin=stops('262428', '4a474e', '76727c', 'a09ca8'), cloth=stops('1c0306', '540915', '8f1424', 'c42e38'),
                                pants=stops('140a08', '2e1a12', '4c2c1e', '6a3e2a'), eye=hexc('ff3a2a'), glow=hexc('9c1018'),
                                detail=hexc('d8a62a'), **NEUTRAL),
    'foe_grappler': dict(skin=stops('262428', '4a474e', '76727c', 'a09ca8'), cloth=stops('120c08', '2a1e14', '46321e', '644a2c'),
                         pants=stops('0e0a08', '201812', '34281c', '4a3a28'), eye=hexc('9cffd0'), glow=hexc('3aa878'),
                         detail=hexc('a8824a'), **NEUTRAL),
    'foe_treasure_raider': dict(skin=stops('262428', '4a474e', '76727c', 'a09ca8'), cloth=stops('3a2606', '7a5414', 'b8862a', 'e8c050'),
                                pants=stops('1a1206', '342410', '503a1c', '6c5028'), eye=hexc('fff0a0'), glow=hexc('e8b828'),
                                detail=hexc('ffe28a'), **NEUTRAL),
}
skins.EVENTS.update(PALETTES)

FOES = {
    'foe_revenant': [('zombie/zombie.png', 'zombie', 'humanoid')],
    'foe_tunneler': [('zombie/zombie.png', 'zombie', 'humanoid')],
    'foe_iron_knight': [('zombie/zombie.png', 'zombie', 'humanoid')],
    'foe_standard_bearer': [('illager/pillager.png', 'pillager', 'illager')],
    'foe_grappler': [('illager/pillager.png', 'pillager', 'illager')],
    'foe_treasure_raider': [('illager/pillager.png', 'pillager', 'illager')],
}
FAMILIES = {k: (v[0][2],) for k, v in FOES.items()}
GOLD = hexc('d8a62a')


# ---------------------------------------------------------------- materials

def linen(p):
    c = ramp([(0, hexc('5a5446')), (0.5, hexc('a49c86')), (1, hexc('e2dac2'))], 0.3 + 0.5 * p.rng.random())
    if p.face not in ('top', 'bottom') and p.j >= p.fh - 2 and p.rng.random() < 0.35:
        return None, None   # frayed
    return mul(c, materials.FACE_LIGHT.get(p.face, 1.0)), None


def wax(p):
    return mul(ramp([(0, hexc('a89c80')), (1, hexc('f0e6c8'))], 0.4 + 0.5 * p.rng.random()), materials.FACE_LIGHT.get(p.face, 1.0)), None


def flame(p):
    c = ramp([(0, hexc('ff7a14')), (0.6, hexc('ffc23a')), (1, hexc('fff6c8'))], p.tip)
    return c, mix(hexc('ff9a2a'), hexc('fff0a0'), p.tip)


def lamp(p):
    return hexc('fff2b0'), hexc('ffe070')


def leather(p):
    c = ramp([(0, hexc('2a1a0e')), (0.5, hexc('4a301a')), (1, hexc('6e4826'))], 0.3 + 0.5 * p.rng.random())
    return mul(c, materials.FACE_LIGHT.get(p.face, 1.0)), None


def plate(p):
    c = ramp([(0, hexc('3a4048')), (0.5, hexc('6a727c')), (1, hexc('a8b2bc'))], 0.35 + 0.45 * p.rng.random())
    if p.face not in ('top', 'bottom') and (p.j == 0 or p.i == 0) and p.rng.random() < 0.5:
        c = hexc('c8d2dc')   # a lit edge
    return mul(c, materials.FACE_LIGHT.get(p.face, 1.0)), None


def crimson(p):
    if p.face not in ('top', 'bottom') and (p.i == 0 or p.i == p.fw - 1 or p.j == p.fh - 1):
        return mul(GOLD, noise(p, 0.05)), None
    c = ramp([(0, hexc('3a050c')), (0.5, hexc('8e1420')), (1, hexc('c42e38'))], 0.3 + 0.4 * p.rng.random())
    return mul(c, materials.FACE_LIGHT.get(p.face, 1.0)), None


def rope(p):
    c = hexc('a8824a') if (p.i + p.j) % 3 else hexc('6e5228')
    return mul(c, materials.FACE_LIGHT.get(p.face, 1.0)), None


def coin_gold(p):
    c = ramp([(0, hexc('8a6414')), (0.6, hexc('d8a62a')), (1, hexc('ffe28a'))], 0.3 + 0.7 * p.rng.random())
    glow = hexc('c89820') if p.rng.random() < 0.25 else None
    return c, glow


def dirt(p):
    c = ramp([(0, hexc('2e2014')), (1, hexc('5e4228'))], p.rng.random())
    return c, None


for _name, _fn in (('linen', linen), ('wax', wax), ('flame', flame), ('lamp', lamp), ('leather', leather),
                   ('plate', plate), ('crimson', crimson), ('rope', rope), ('coin_gold', coin_gold), ('dirt', dirt)):
    materials.MATERIALS[_name] = _fn


# ---------------------------------------------------------------- the gear
# humanoid head x -4..4, y -8..0, z -4..4; body x -4..4, y 0..12, z -2..2
# illager head x -4..4, y -10..0; body x -4..4, y 0..12, z -3..3

def revenant(f):
    out = []
    if f != 'humanoid':
        return out
    # the shroud: a hood of linen over the head, hanging down the back
    out.append(block('head', (-4.6, -8.6, -4.6), (9, 1, 9), 'linen', key='hood_top'))
    out += sym([block('head', (-4.6, -8.0, -4.6), (1, 6, 9), 'linen', key='hood_side')])
    out.append(block('head', (-4.6, -8.0, 3.6), (9, 7, 1), 'linen', key='hood_back'))
    out.append(block('body', (-4.4, 0.0, 2.1), (9, 13, 1), 'linen', key='shroud'))
    # a candle on the head, burning
    out.append(block('head', (1.0, -11.6, -1.0), (2, 3, 2), 'wax', key='candle'))
    out += chain('head', (2.0, -11.6, 0.0), (0, 0, 0), [(1, 2, 1)], 'flame', name='fl')
    # grave dirt on the arms
    out += sym([block('right_arm', (-3.2, 8.0, -2.2), (4, 2, 4), 'dirt', key='mud')])
    return out


def tunneler(f):
    out = []
    if f != 'humanoid':
        return out
    # a miner's helmet with a lamp in front
    out.append(block('head', (-4.6, -9.0, -4.6), (9, 3, 9), 'leather', key='helm'))
    out.append(block('head', (-5.0, -6.4, -5.0), (10, 1, 10), 'leather', key='brim'))
    out.append(block('head', (-1.5, -8.6, -5.6), (3, 2, 1), 'lamp', key='lamp'))
    out.append(block('head', (-2.0, -9.0, -5.2), (4, 3, 1), 'iron', key='lampcase'))
    # dirt on the arms and a sack on the back
    out += sym([block('right_arm', (-3.2, 6.0, -2.2), (4, 4, 4), 'dirt', key='dirt')])
    out.append(block('body', (-3.0, 2.0, 2.1), (6, 6, 2), 'leather', key='sack'))
    return out


def iron_knight(f):
    out = []
    if f != 'humanoid':
        return out
    # a closed helm with a slit and a red crest
    out.append(block('head', (-4.7, -8.7, -4.7), (9, 4, 9), 'plate', key='helm'))
    out.append(block('head', (-4.7, -3.7, -4.7), (9, 4.4, 9), 'plate', key='visor'))
    out += sym([block('head', (-4.7, -4.7, -4.7), (3, 1, 9), 'plate', key='slit')])
    out.append(block('head', (-0.5, -11.7, -3.0), (1, 3, 7), 'crimson', key='crest'))
    # pauldrons and a tabard
    out += sym([block('body', (-7.0, -1.4, -3.0), (4, 3, 6), 'plate', key='pad')])
    out.append(block('body', (-3.0, 2.0, -2.3), (6, 11, 1), 'crimson', key='tabard'))
    out += sym([block('right_arm', (-3.4, 4.0, -2.4), (5, 4, 5), 'plate', key='vamb')])
    return out


def standard_bearer(f):
    out = []
    if f != 'illager':
        return out
    # a tall standard on the back: pole, crossbar, a long crimson flag with a gold border
    out.append(block('body', (-0.5, -26.0, 4.2), (1, 36, 1), 'pole', key='pole'))
    out.append(block('body', (-5.0, -25.0, 4.2), (10, 1, 1), 'pole', key='bar'))
    out.append(block('body', (-4.5, -24.0, 4.6), (9, 16, 1), 'crimson', key='flag'))
    out.append(block('body', (-1.0, -27.5, 3.7), (2, 2, 2), 'gold', key='tip'))
    out.append(block('body', (-2.0, 2.0, 3.0), (4, 2, 2), 'leather', key='holder'))
    return out


def grappler(f):
    out = []
    if f != 'illager':
        return out
    # a coil of rope over the shoulder and across the chest, a hook at the belt
    out += ring('body', (-1.0, 4.0, 0.0), 4.6, 10, (2, 1, 1), 'rope', plane='xy', name='coil')
    out.append(block('body', (-4.6, 0.0, -3.3), (2, 12, 1), 'rope', key='strap'))
    out += chain('body', (3.5, 11.0, -3.2), (0, 0, 0.3), [(1, 3, 1), (1, 2, 1)], 'iron', bend=(0, 0, -0.9), name='hook')
    return out


def treasure_raider(f):
    out = []
    if f != 'illager':
        return out
    # a golden hood and a coin sack on the back
    out.append(block('head', (-4.6, -10.6, -4.6), (9, 2, 9), 'coin_gold', key='hood_top'))
    out += sym([block('head', (-4.6, -9.6, -4.6), (1, 7, 9), 'coin_gold', key='hood_side')])
    out.append(block('head', (-4.6, -9.6, 3.6), (9, 9, 1), 'coin_gold', key='hood_back'))
    out.append(block('body', (-3.5, 3.0, 3.2), (7, 7, 3), 'leather', key='sack'))
    out += cluster('body', (0.0, 3.0, 4.5), (0, 0, 0), 'coin_gold', sizes=((2, 2, 2), (1, 1, 1), (1, 1, 1)), spread=1.0,
                   name='coins')
    return out


DESIGNS = {'foe_revenant': revenant, 'foe_tunneler': tunneler, 'foe_iron_knight': iron_knight,
           'foe_standard_bearer': standard_bearer, 'foe_grappler': grappler, 'foe_treasure_raider': treasure_raider}


def details(tex, family, ev, seed):
    glow = Image.new('RGBA', tex.size, (0, 0, 0, 0))
    p = skins.Painter(tex, glow, family, ev, seed)
    sides = skins.SIDES
    if ev == 'foe_revenant':
        p.lines(['head', 'arm', 'body'], sides, 1, 6, hexc('2a3a30'), k=0.8)
        p.spots(['body', 'leg'], sides, 3, hexc('3a3020'), k=0.7, size=2)
    elif ev == 'foe_tunneler':
        p.spots(['arm', 'leg', 'body'], sides, 5, hexc('3a2a16'), k=0.8, size=2)
    elif ev == 'foe_iron_knight':
        p.edge(['body'], sides, -1, hexc('c8d2dc'))
        p.edge(['arm', 'leg'], sides, 0, hexc('8a929c'))
    elif ev == 'foe_standard_bearer' and family == 'illager':
        p.edge(['body', 'robe'], sides, -1, GOLD)
        for x, y in skins.EYES.get(family, [])[:4:3]:
            p.put(x, y + 1, hexc('c0101a'), k=0.9)
    elif ev == 'foe_grappler' and family == 'illager':
        p.lines(['body', 'robe'], sides, 1, 5, hexc('2a1e14'), k=0.8)
    elif ev == 'foe_treasure_raider' and family == 'illager':
        p.spots(['body', 'robe', 'arm'], sides, 4, hexc('ffe28a'), glow=hexc('c89820'), k=0.9, size=1)
    p.eyes()
    return tex, glow


def build(van, out_root, atlas):
    """Builds every foe folder; returns {folder: shapes}, {folder: textures}, {folder: glows}."""
    import os
    all_shapes, all_textures, all_glows = {}, {}, {}
    for ev, mobs in FOES.items():
        out = os.path.join(out_root, ev)
        os.makedirs(out, exist_ok=True)
        textures, glows = [], []
        for path, name, family in mobs:
            img = Image.open(os.path.join(van, path)).convert('RGBA')
            tex = skins.recolor(img, name, ev)
            tex, glow = details(tex, family, ev, seed=zlib.crc32(f'{ev}/{name}'.encode()))
            if glow.getbbox():
                glow.save(os.path.join(out, name + '_glow.png'))
                glows.append(name)
            tex.save(os.path.join(out, name + '.png'))
            textures.append((path, name))
        shapes = {}
        for fam in FAMILIES[ev]:
            boxes = DESIGNS[ev](fam)
            size = atlas.pack(boxes)
            gtex, gglow = atlas.paint(boxes, size)
            gtex.save(os.path.join(out, f'gear_{fam}.png'))
            gglow.save(os.path.join(out, f'gear_{fam}_glow.png'))
            shapes[fam] = (size, boxes)
        all_shapes[ev], all_textures[ev], all_glows[ev] = shapes, textures, glows
    return all_shapes, all_textures, all_glows
