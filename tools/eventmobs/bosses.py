"""
The four bosses of the boss nights (Simon, 25 October 2026): their textures, glow maps,
clothes and 3D gear, in the blood moon's dark style with one signature colour each.
Built by build.py next to the events; every boss is its own folder "boss_<id>" and the
mod swaps a boss monster's textures for the folder's (EventTextures, per monster).

    grave king   zombie       rotten skin, royal crimson, a gold crown, a cape, gold pauldrons
    spider queen spider       black-crimson carapace, a glowing hourglass, a crown, egg sacs
    bone lich    skeleton     blackened bones, a dark robe, soul-blue eyes, a soul halo
    warlord      vindicator   dark steel, crimson, war paint, a horned helmet, a war banner
                 ravager      scarred and war-painted
"""
import zlib
from PIL import Image
import skins
from gear import Box, chain, block, sym, ring, cluster, R
from materials import hexc, mix, mul, ramp, noise
import materials

stops = skins.stops

PALETTES = {
    'boss_grave_king': dict(
        skin=stops('18201a', '3c4a38', '687659', '949c7a'), cloth=stops('1c0306', '540915', '8f1424', 'c42e38'),
        pants=stops('100810', '24121e', '3c1e32', '542a46'), bone=stops('4a3b34', '9a8a7c', 'd8cbb6', 'f2e8d6'),
        creeper=stops('1e1a1a', '4a4040', '7d6f6a', 'a69890'), spider=stops('0d0707', '241010', '3d1818', '5a2424'),
        eye=hexc('ff4a1a'), glow=hexc('a8200e'), detail=hexc('d8a62a')),
    'boss_spider_queen': dict(
        skin=stops('2e2626', '6b605c', 'a89c94', 'd6ccc2'), cloth=stops('1a0405', '4e090c', '8a141a', 'b8282c'),
        pants=stops('120a0a', '2c1716', '4c2826', '6b3a36'), bone=stops('4a3b34', '9a8a7c', 'd8cbb6', 'f2e8d6'),
        creeper=stops('1e1a1a', '4a4040', '7d6f6a', 'a69890'), spider=stops('07030a', '1a0816', '320e28', '4e1a3e'),
        eye=hexc('ff2448'), glow=hexc('b0102a'), detail=hexc('d8a62a')),
    'boss_bone_lich': dict(
        skin=stops('2e2626', '6b605c', 'a89c94', 'd6ccc2'), cloth=stops('05070c', '0e1626', '1a2a44', '284064'),
        pants=stops('05060a', '0c1220', '162238', '22344e'), bone=stops('0c0b0e', '2a2530', '575064', '8c8498'),
        creeper=stops('1e1a1a', '4a4040', '7d6f6a', 'a69890'), spider=stops('0d0707', '241010', '3d1818', '5a2424'),
        eye=hexc('62f2ff'), glow=hexc('1ea8cc'), detail=hexc('7ff8ff')),
    'boss_warlord': dict(
        skin=stops('262428', '4a474e', '76727c', 'a09ca8'), cloth=stops('0c0c10', '22222a', '3a3a46', '585866'),
        pants=stops('140507', '2e0b10', '4c1218', '6c1e26'), bone=stops('4a3b34', '9a8a7c', 'd8cbb6', 'f2e8d6'),
        creeper=stops('120908', '30201a', '56392c', '7c5a48'), spider=stops('0d0707', '241010', '3d1818', '5a2424'),
        eye=hexc('ff2c2c'), glow=hexc('9c1018'), detail=hexc('c8962a')),
}
skins.EVENTS.update(PALETTES)

# boss folder: [(vanilla path under textures/entity, our file name, family for the details)]
BOSSES = {
    'boss_grave_king': [('zombie/zombie.png', 'zombie', 'humanoid')],
    'boss_spider_queen': [('spider/spider.png', 'spider', 'spider')],
    'boss_bone_lich': [('skeleton/skeleton.png', 'skeleton', 'skeleton')],
    'boss_warlord': [('illager/vindicator.png', 'vindicator', 'illager'), ('illager/ravager.png', 'ravager', 'whole')],
}
# eyes layers of the boss (the queen's eyes glow in her colour)
EYES = {'boss_spider_queen': [('spider/spider_eyes.png', 'spider_eyes')]}
# clothes layers (zombie: the drowned's outer layer; skeleton: the stray's)
CLOTHES = {'boss_grave_king': ('zombie/drowned_outer_layer.png', 'zombie_outer', 'drowned_outer_layer'),
           'boss_bone_lich': ('skeleton/stray_overlay.png', 'skeleton_outer', 'stray_overlay')}
# the gear families each boss carries
FAMILIES = {'boss_grave_king': ('humanoid',), 'boss_spider_queen': ('spider',), 'boss_bone_lich': ('skeleton',),
            'boss_warlord': ('illager',)}

GOLD = hexc('d8a62a')
GOLD_LIGHT = hexc('ffe28a')


# ---------------------------------------------------------------- materials of the bosses

def royal(p):
    """Crimson cloth with a gold hem."""
    if p.face not in ('top', 'bottom') and (p.j >= p.fh - 1 or p.i == 0 or p.i == p.fw - 1):
        return mul(GOLD, noise(p, 0.05)), None
    c = ramp([(0, hexc('3a050c')), (0.5, hexc('7e0e1c')), (1, hexc('a8182a'))], 0.3 + 0.4 * p.rng.random())
    if p.face not in ('top', 'bottom') and p.j >= p.fh - 3 and p.rng.random() < 0.2:
        return None, None   # worn at the bottom
    return mul(c, materials.FACE_LIGHT.get(p.face, 1.0)), None


def soul(p):
    c = ramp([(0, hexc('0e4a5a')), (0.6, hexc('2fc8e8')), (1, hexc('c8fcff'))], 0.4 + 0.6 * p.tip)
    return mul(c, noise(p, 0.05)), mix(hexc('1ea8cc'), hexc('9ffcff'), 0.3 + 0.7 * p.tip)


def soul_crystal(p):
    c = ramp([(0, hexc('061a24')), (0.6, hexc('1888a8')), (1, hexc('8ff4ff'))], p.tip)
    glow = None
    if p.edge or p.rng.random() < 0.3 + 0.4 * p.tip:
        glow = mix(hexc('0e6a88'), hexc('8ff8ff'), p.tip)
    return mul(c, noise(p, 0.06)), glow


def black_bone(p):
    c = ramp([(0, hexc('100e12')), (0.6, hexc('2e2832')), (1, hexc('5a5266'))], 0.2 + 0.8 * p.tip)
    if p.rng.random() < 0.12:
        c = mul(c, 0.7)
    return mul(c, noise(p, 0.05)), None


def war_steel(p):
    c = ramp([(0, hexc('1a1a20')), (0.5, hexc('34343e')), (1, hexc('5a5a68'))], 0.35 + 0.4 * p.rng.random())
    if p.face not in ('top', 'bottom') and (p.j == 0 or p.j == p.fh - 1):
        c = mul(GOLD, 0.9)   # gold rims
    elif p.rng.random() < 0.06:
        c = hexc('8a1018')   # blood on the steel
    return mul(c, materials.FACE_LIGHT.get(p.face, 1.0)), None


def pole(p):
    c = ramp([(0, hexc('2a1a10')), (1, hexc('5a3a22'))], 0.3 + 0.5 * p.rng.random())
    return mul(c, materials.FACE_LIGHT.get(p.face, 1.0)), None


def egg(p):
    c = ramp([(0, hexc('8a7a6a')), (0.6, hexc('d8ccb8')), (1, hexc('f4ece0'))], 0.3 + 0.6 * p.rng.random())
    if p.rng.random() < 0.1:
        c = hexc('6a0a14')   # veins
    return c, None


for _name, _fn in (('royal', royal), ('soul', soul), ('soul_crystal', soul_crystal), ('black_bone', black_bone),
                   ('war_steel', war_steel), ('pole', pole), ('egg', egg)):
    materials.MATERIALS[_name] = _fn


# ---------------------------------------------------------------- the gear

def grave_king(f):
    out = []
    if f != 'humanoid':
        return out
    # the crown: a gold band round the top of the head, points on it, a red stone in front
    out.append(block('head', (-4.6, -9.6, -4.6), (9, 2, 1), 'gold', key='band_f'))
    out.append(block('head', (-4.6, -9.6, 3.6), (9, 2, 1), 'gold', key='band_f'))
    out += sym([block('head', (-4.6, -9.6, -3.6), (1, 2, 7), 'gold', key='band_s')])
    # points: a tall one in the middle of the front, lower ones at the corners and the back
    out.append(block('head', (-1.0, -13.6, -4.6), (2, 4, 1), 'gold', key='pt_mid'))
    out += sym([block('head', (-4.6, -11.6, -4.6), (1, 2, 1), 'gold', key='pt2')])
    out += sym([block('head', (-4.6, -11.6, 3.6), (1, 2, 1), 'gold', key='pt2')])
    out.append(block('head', (-0.5, -11.6, 3.6), (1, 2, 1), 'gold', key='pt2'))
    out.append(block('head', (-0.5, -12.6, -4.9), (1, 1, 1), 'blood_crystal', key='gem'))
    out += sym([block('head', (-3.0, -9.1, -5.0), (1, 1, 1), 'blood_crystal', key='gem')])
    # gold pauldrons with a spike, a crimson cape down the back
    out += sym([block('body', (-6.8, -1.4, -2.8), (4, 2, 6), 'gold', key='pad')])
    out += sym(chain('body', (-5.2, -1.2, 0), (0, 0, -0.5), [(1, 3, 1)], 'gold', name='padsp'))
    out.append(block('body', (-4.5, 0.2, 2.15), (9, 15, 1), 'royal', key='cape'))
    # gold bands on the outstretched arms, blood crystals on the chest
    out += sym([block('right_arm', (-3.5, 6, -2.5), (5, 2, 5), 'gold', key='brace')])
    out += cluster('body', (-2.0, 3.5, -2.2), (0.9, 0, 0), 'blood_crystal', sizes=((2, 2, 2), (1, 2, 1)), spread=1.0,
                   name='chest')
    return out


def spider_queen(f):
    out = []
    if f != 'spider':
        return out
    # a small crown on her head (head: x -4..4, y -4..4, z -8..0)
    out.append(block('head', (-3.5, -5.0, -7.5), (7, 1, 6), 'gold', key='cband'))
    for k, x in enumerate((-3.5, -1.5, 0.5, 2.5)):
        h = 3 if k in (1, 2) else 2
        out.append(block('head', (x, -5.0 - h, -7.5), (1, h, 1), 'gold', key=f'cp{h}'))
    out.append(block('head', (-0.5, -5.6, -8.1), (1, 1, 1), 'blood_crystal', key='gem'))
    # bone spikes along the abdomen, a crystal cluster on top, egg sacs at the back
    for k, (z, h) in enumerate(((-4, 3), (-0.5, 4), (3, 3))):
        out += sym(chain('body1', (-3.5, -4, z), (-0.25, 0, -0.55), [(2, h, 2), (1, 3, 1)], 'blood_bone', name=f'ab{k}'))
    out += cluster('body1', (0, -4, -1), (-0.2, 0, 0), 'blood_crystal', sizes=((2, 5, 2), (1, 3, 1), (1, 4, 1)), name='bc')
    out.append(block('body1', (-2.5, -1.5, 6.0), (5, 4, 2), 'egg', key='egg1'))
    out.append(block('body1', (-3.5, 1.5, 5.6), (3, 3, 2), 'egg', key='egg2'))
    out.append(block('body1', (1.0, 1.0, 5.6), (3, 3, 2), 'egg', key='egg2'))
    out.append(block('body1', (-3.0, -2.2, 6.4), (6, 1, 1), 'web', key='webs'))
    # fangs
    out += sym(chain('head', (-1.5, 3, -7.5), (R(162), 0, 0.1), [(1, 3, 1), (1, 2, 1)], 'bone', bend=(-0.4, 0, 0), name='fang'))
    return out


def bone_lich(f):
    out = []
    if f != 'skeleton':
        return out
    # a halo of soul fire behind the head, a crown of black bone
    out += ring('head', (0, -5, 5.6), 6.2, 14, (3, 1, 1), 'soul', spikes=2, spike_mat='soul', name='halo')
    for k, (x, h, tilt) in enumerate(((-3.5, 3, -0.3), (-1.2, 4, -0.1), (1.2, 4, 0.1), (3.5, 3, 0.3))):
        out += chain('head', (x, -8, -3.2), (0, 0, tilt), [(1, h, 1)], 'black_bone', name=f'cr{k}')
    out += sym(chain('head', (-3.6, -7.6, 0.5), (-0.2, 0, -0.9), [(2, 3, 2), (1, 3, 1)], 'black_bone', bend=(-0.2, 0, 0.5),
                     name='horn'))
    # shoulder spikes, a soul crystal on the chest, a chain of soul stones round the waist
    out += sym(chain('body', (-5.5, 0.5, 0), (0.1, 0, -0.7), [(2, 4, 2), (1, 3, 1)], 'black_bone', name='sh'))
    out += cluster('body', (0, 3.5, -2.2), (0.9, 0, 0), 'soul_crystal', sizes=((2, 3, 2), (1, 2, 1), (1, 2, 1)), spread=1.1,
                   name='heart')
    for k, x in enumerate((-3.5, -1.0, 1.5)):
        out.append(block('body', (x, 10.2, -2.6), (2, 1, 1), 'soul', key='belt'))
    return out


def warlord(f):
    out = []
    if f != 'illager':
        return out
    # the horned helmet (illager head: x -4..4, y -10..0, z -4..4; eyes at y -6)
    out.append(block('head', (-4.6, -10.8, -4.6), (9, 4, 9), 'war_steel', key='helm'))
    out.append(block('head', (-0.5, -6.8, -4.9), (1, 3, 1), 'war_steel', key='nose'))
    out += sym(chain('head', (-4.4, -9.4, -0.5), (-0.1, 0, -1.3), [(2, 3, 2), (2, 3, 2), (1, 3, 1)], 'dark_horn',
                     bend=(-0.25, 0, 0.55), name='horn'))
    # pauldrons of steel and a war banner on his back (illager body: x -4..4, y 0..12, z -3..3)
    out += sym([block('body', (-6.8, -1.2, -3.4), (4, 3, 7), 'war_steel', key='pad')])
    out += sym(chain('body', (-5.4, -1.0, 0), (0, 0, -0.6), [(1, 3, 1)], 'dark_horn', name='padsp'))
    out.append(block('body', (-0.5, -21.0, 4.4), (1, 30, 1), 'pole', key='pole'))
    out.append(block('body', (0.5, -20.5, 4.4), (8, 10, 1), 'royal', key='flag'))
    out.append(block('body', (-1.0, -22.0, 3.9), (2, 1, 2), 'gold', key='tip'))
    out.append(block('body', (-2.0, 2.0, 3.0), (4, 2, 2), 'war_steel', key='holder'))
    return out


DESIGNS = {'boss_grave_king': grave_king, 'boss_spider_queen': spider_queen, 'boss_bone_lich': bone_lich,
           'boss_warlord': warlord}

import designs as _designs
_event_design = _designs.design


def _design(event, family):
    if event in DESIGNS:
        return DESIGNS[event](family)
    return _event_design(event, family)


_designs.design = _design


# ---------------------------------------------------------------- the textures

def details(tex, family, ev, seed):
    """The boss's own markings on top of its recoloured skin, and the glow map."""
    glow = Image.new('RGBA', tex.size, (0, 0, 0, 0))
    p = skins.Painter(tex, glow, family, ev, seed)
    pal = PALETTES[ev]
    sides = skins.SIDES
    if ev == 'boss_grave_king':
        p.lines(['head', 'arm', 'body', 'leg'], sides, 1, 7, hexc('8a1a10'), glow=hexc('7a120a'), k=0.85)
        p.spots(['body', 'leg', 'arm'], sides, 3, hexc('0c140c'), k=0.7, size=2)
        p.edge(['body'], sides, -1, GOLD)
        p.edge(['body'], ('front',), 0, GOLD)
        for x, y in skins.EYES.get(family, [])[:4:3]:
            for dy in (1, 2):
                p.put(x, y + dy, hexc('8a1a10'), glow=hexc('5a0a0e') if dy == 1 else None, k=0.9)
    elif ev == 'boss_spider_queen':
        if family == 'spider':
            u, v, w, h = p.faces['body']['top']
            hourglass = ((4, 1), (5, 1), (3, 2), (4, 2), (5, 2), (6, 2), (4, 3), (5, 3), (4, 4), (5, 4),
                         (4, 5), (5, 5), (4, 6), (5, 6), (3, 7), (4, 7), (5, 7), (6, 7), (4, 8), (5, 8))
            for dx, dy in hourglass:
                p.put(u + dx, v + dy, hexc('ff2a40'), glow=hexc('c0102a'))
            p.lines(['body'], ('left', 'right', 'back'), 2, 6, hexc('6a1a5a'), glow=hexc('4a0a40'), k=0.8)
            p.lines(['leg'], sides, 1, 8, hexc('3a0a2a'), k=0.7)
            p.spots(['head'], ('front',), 3, hexc('ff2448'), glow=hexc('a0102a'))
    elif ev == 'boss_bone_lich':
        rune = [(0, 0), (2, 0), (1, 1), (0, 2), (2, 2), (1, 3)]
        for part, face in (('body', 'front'), ('head', 'front'), ('body', 'back')):
            rect = p.faces.get(part, {}).get(face)
            if rect:
                u, v, w, h = rect
                ox, oy = u + w // 2 - 1, v + (5 if part == 'body' else 0)
                if part == 'head':
                    oy = v + 1
                for dx, dy in rune:
                    p.put(ox + dx, oy + dy, pal['detail'], glow=pal['glow'])
        p.lines(['arm', 'leg', 'body'], sides, 1, 5, hexc('3fd8ff'), glow=hexc('1a8cb0'), k=0.9, dirs=((0, 1),))
    elif ev == 'boss_warlord':
        if family == 'illager':
            # war paint: two red stripes down from the eyes, gold on the coat
            for x, y in skins.EYES.get(family, [])[:4:3]:
                for dy in (1, 2, 3):
                    p.put(x, y + dy, hexc('c0101a'), glow=hexc('7a0a10') if dy < 3 else None)
            p.edge(['body', 'robe'], sides, -1, GOLD)
            p.edge(['body'], ('front',), 0, GOLD)
            p.lines(['robe'], ('front',), 2, 6, hexc('8a6a1a'), k=0.8, dirs=((0, 1),))
            p.spots(['body', 'robe', 'arm'], sides, 3, hexc('5a0a10'), k=0.8, size=1)
        else:
            n = max(4, tex.width * tex.height // 240)
            p.anywhere(n, 7, hexc('a0101a'), glow=hexc('6a0a10'), k=0.9)
            p.anywhere(n // 2, 3, hexc('c8962a'), k=0.85)
    p.eyes()
    return tex, glow


def build(van, out_root, atlas):
    """Builds every boss folder; returns {folder: shapes}, {folder: textures}, {folder: glows}."""
    import os
    all_shapes, all_textures, all_glows = {}, {}, {}
    for ev, mobs in BOSSES.items():
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
        for path, name in EYES.get(ev, []):
            eyes = Image.open(os.path.join(van, path)).convert('RGBA')
            skins.spider_eyes(eyes, ev).save(os.path.join(out, name + '.png'))
            textures.append((path, name))
        if ev in CLOTHES:
            path, name, cls = CLOTHES[ev]
            img = Image.open(os.path.join(van, path)).convert('RGBA')
            tex = skins.recolor(img, cls, ev)
            if ev == 'boss_grave_king':
                # the robe leaves the face free (the crown sits on it)
                px = tex.load()
                for y in range(16):
                    for x in range(tex.width):
                        px[x, y] = (0, 0, 0, 0)
            glow = Image.new('RGBA', tex.size, (0, 0, 0, 0))
            p = skins.Painter(tex, glow, None, ev, zlib.crc32(f'{ev}/{name}'.encode()))
            col = GOLD if ev == 'boss_grave_king' else PALETTES[ev]['detail']
            p.anywhere(max(3, tex.width * tex.height // 400), 5, col, glow=None if ev == 'boss_grave_king' else PALETTES[ev]['glow'],
                       k=0.8, dirs=((0, 1),))
            tex.save(os.path.join(out, name + '.png'))
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
