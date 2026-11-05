"""
The four new bosses of the content update (design update, Simon 29 October 2026; their fights come
with the content update, which picks them up by these folder names):

    boss_frost_jarl      iron golem   packed ice, icicle beard, cold blue cracks that glow
    boss_ember_colossus  iron golem   basalt with lava veins (also a magma cube skin, should it be one)
    boss_storm_witch     witch        a gown of storm clouds, a crown of lightning; her phantom
    boss_ram_titan       ravager      armoured dark steel with gold rivets, an iron ram head

Bosse 2 (Simon 05.11.: "die vier neuen bosse müssen nochmal verbessert werden"): their 3D gear.

    frost jarl       an icicle beard, a crown of ice spikes, shoulders of pack ice, a merlon club, ice gauntlet
    ember colossus   a crust of basalt crags with glowing seams; three glow stages (66 % and 33 % life:
                     boss_ember_colossus_2 and _3, the server switches the look)
    storm witch      a weather staff, three yellow lightning orbs; the phantom's storm wings and tail cloud
    ram titan        an oak-log ram with an iron head, armour plates with gold rivets, chains, greaves
"""
import zlib
from PIL import Image
import skins
from gear import block, chain, sym, cluster
from materials import hexc
import materials  # noqa: F401
import bosses  # noqa: F401  (registers the boss materials)
from materials import hexc as _h, mix, mul, ramp, noise
from gear import ring, zigzag, R

stops = skins.stops
skins.WHOLE = skins.WHOLE + ('iron_golem',)
BONE = stops('4a3b34', '9a8a7c', 'd8cbb6', 'f2e8d6')
SPIDER = stops('0d0707', '241010', '3d1818', '5a2424')

PALETTES = {
    'boss_frost_jarl': dict(skin=stops('1e3a56', '3f6a92', '78a8d0', 'c4e4ff'), cloth=stops('16283a', '2c4a66', '4e7494', '7ea2c0'),
                            pants=stops('101c2a', '20344a', '36506a', '50708e'), bone=BONE, spider=SPIDER,
                            creeper=stops('122c48', '2e5a86', '6a9cc8', 'bfe2fa'),
                            eye=hexc('bff0ff'), glow=hexc('5fbfff'), detail=hexc('f2fbff')),
    'boss_ember_colossus': dict(skin=stops('0e0c0e', '262224', '403a3c', '5e5658'), cloth=stops('080606', '1c1410', '34241c', '4c3426'),
                                pants=stops('060505', '161010', '281c1a', '3a2a26'), bone=BONE, spider=SPIDER,
                                creeper=stops('0c0a0a', '221c1c', '3a3232', '564a48'),
                                eye=hexc('ffa23a'), glow=hexc('ff7a14'), detail=hexc('ffcf5a')),
    'boss_storm_witch': dict(skin=stops('121820', '2a3442', '4a5668', '76849a'), cloth=stops('0e121a', '222c3a', '3c4a5e', '5e708a'),
                             pants=stops('0e1218', '1e2530', '323c4a', '4a5666'), bone=BONE, spider=SPIDER,
                             creeper=stops('0e121a', '222c3a', '3c4a5e', '5e708a'),
                             eye=hexc('fff27a'), glow=hexc('ffd83a'), detail=hexc('fff6b0')),
    'boss_ram_titan': dict(skin=stops('262428', '4a474e', '76727c', 'a09ca8'), cloth=stops('0c0c10', '22222a', '3a3a46', '585866'),
                           pants=stops('140507', '2e0b10', '4c1218', '6c1e26'), bone=BONE, spider=SPIDER,
                           creeper=stops('16161c', '2e2e38', '4c4c5a', '6e6e80'),
                           eye=hexc('ff2c2c'), glow=hexc('9c1018'), detail=hexc('c8962a')),
}
PALETTES['boss_ember_colossus_2'] = PALETTES['boss_ember_colossus']
PALETTES['boss_ember_colossus_3'] = PALETTES['boss_ember_colossus']
skins.EVENTS.update(PALETTES)


# ---------------------------------------------------------------- materials (Bosse 2)

def castle_stone(p):
    """Stone bricks of a castle wall, rimed with frost on top."""
    if p.face == 'top' or (p.face not in ('bottom',) and p.j == 0 and p.rng.random() < 0.6):
        return mix(_h('dcecf6'), _h('ffffff'), p.rng.random() * 0.5), None
    mortar = p.face not in ('top', 'bottom') and (p.j % 3 == 2 or (p.i + (p.j // 3) * 2) % 4 == 0)
    c = _h('4e4e52') if mortar else ramp([(0, _h('6a6a70')), (1, _h('9a9aa2'))], p.rng.random())
    return mul(c, materials.FACE_LIGHT.get(p.face, 1.0)), None


def oak_log(p):
    if p.face in ('front', 'back'):
        ring_ = (p.i + p.j) % 3 == 0
        return (_h('7a5a32') if ring_ else _h('a8824c')), None   # the cut end
    c = ramp([(0, _h('3a2a18')), (1, _h('5e4426'))], p.rng.random())
    if p.i % 2 == 0:
        c = mul(c, 0.85)   # bark furrows
    return c, None


def ram_iron(p):
    """The dark iron head of the ram, a glowing red rune in the middle of its front."""
    c = mul(_h('2e3036'), noise(p, 0.1))
    if p.face == 'front' and abs(p.i - p.fw // 2) <= 1 and abs(p.j - p.fh // 2) <= 1 and (p.i + p.j) % 2 == 0:
        return _h('ff3a2a'), _h('ff2418')
    if p.face not in ('top', 'bottom') and (p.i == 0 or p.i == p.fw - 1):
        c = mix(c, _h('c8962a'), 0.7)   # gold rim
    return c, None


def ember_crust(p):
    """Basalt crags with glowing seams: the closer to the tip, the more it glows."""
    if p.edge and p.rng.random() < 0.35 + 0.4 * p.tip:
        return _h('ffb347'), mix(_h('ff6a0a'), _h('ffd37a'), p.tip)
    c = ramp([(0, _h('141214')), (1, _h('3a3436'))], p.rng.random())
    return c, None


def yellow_bolt(p):
    c = mix(_h('fff27a'), _h('ffffff'), 0.2 + 0.6 * p.rng.random())
    return c, mix(_h('ffd83a'), _h('fff6b0'), 0.4 + 0.6 * p.tip)


for _name, _fn in (('castle_stone', castle_stone), ('oak_log', oak_log), ('ram_iron', ram_iron),
                   ('ember_crust', ember_crust), ('yellow_bolt', yellow_bolt)):
    materials.MATERIALS[_name] = _fn

BOSSES = {
    'boss_frost_jarl': [('iron_golem/iron_golem.png', 'iron_golem', 'whole')],
    'boss_ember_colossus': [('iron_golem/iron_golem.png', 'iron_golem', 'whole'), ('slime/magmacube.png', 'magmacube', 'whole')],
    # the brighter glow of the colossus at 66 % and 33 % of its life (Bosse 2)
    'boss_ember_colossus_2': [('slime/magmacube.png', 'magmacube', 'whole')],
    'boss_ember_colossus_3': [('slime/magmacube.png', 'magmacube', 'whole')],
    'boss_storm_witch': [('witch/witch.png', 'witch', 'witch'), ('phantom/phantom.png', 'phantom', 'whole')],
    'boss_ram_titan': [('illager/ravager.png', 'ravager', 'whole')],
}
FAMILIES = {'boss_storm_witch': ('witch', 'phantom'), 'boss_frost_jarl': ('iron_golem',),
            'boss_ember_colossus': ('magma_cube',), 'boss_ember_colossus_2': ('magma_cube',),
            'boss_ember_colossus_3': ('magma_cube',), 'boss_ram_titan': ('ravager',)}


def storm_witch(f):
    if f == 'phantom':
        return storm_phantom()
    if f != 'witch':
        return []
    out = [block('body', (-5.0, 0.0, 3.0), (10, 18, 1), 'storm_cloud', key='gown')]
    out += sym([block('body', (-6.0, -1.0, -3.6), (3, 3, 7), 'storm_cloud', key='cloud')])
    for k, x in enumerate((-3.5, -1.0, 1.5, 3.5)):
        out += chain('head', (x, -11, 0), (0, 0, 0.2 * (k - 1.5)), [(1, 3, 1), (1, 2, 1)], 'bolt', name=f'bolt{k}')
    # Bosse 2: the weather staff at her side, a lightning crystal on top, and three yellow orbs round her hat
    out.append(block('body', (5.5, -9.0, -4.0), (1, 23, 1), 'storm_iron', key='staff'))
    out.append(block('body', (4.5, -12.0, -5.0), (3, 3, 3), 'yellow_bolt', key='staffgem'))
    out += sym([block('body', (4.0, -10.0, -4.5), (1, 1, 2), 'yellow_bolt', key='prong')])
    for k, (x, y, z) in enumerate(((-8.0, -10.0, -1.0), (6.0, -13.0, 1.0), (-2.0, -17.0, -6.5))):
        out.append(block('head', (x, y, z), (2, 2, 2), 'yellow_bolt', key='orb'))
    return out


def storm_phantom():
    """Her phantom (Bosse 2): storm clouds along the wings with lightning veins, a cloud on the tail, lightning horns."""
    tip = 'body/left_wing_base/left_wing_tip'
    base = 'body/left_wing_base'
    out = []
    out += sym([block(tip, (1.0, -1.0, 6.0), (11, 2, 4), 'storm_cloud', key='wingcloud'),
                block(tip, (2.0, -0.6, 2.0), (9, 1, 1), 'yellow_bolt', key='vein'),
                block(tip, (9.0, -0.6, 3.0), (1, 1, 3), 'yellow_bolt', key='vein2'),
                block(base, (0.5, -1.0, 5.5), (5, 2, 4), 'storm_cloud', key='basecloud')])
    out.append(block('body/tail_base/tail_tip', (-2.0, -1.5, 3.0), (4, 3, 4), 'storm_cloud', key='tailcloud'))
    out.append(block('body/tail_base/tail_tip', (-0.5, -0.5, 6.5), (1, 1, 2), 'yellow_bolt', key='tailbolt'))
    out += chain('body/head', (-3.0, -2.0, -3.0), (0.3, 0, -0.5), [(1, 3, 1), (1, 2, 1)], 'yellow_bolt', name='hornl')
    out += chain('body/head', (2.0, -2.0, -3.0), (0.3, 0, 0.5), [(1, 3, 1), (1, 2, 1)], 'yellow_bolt', name='hornr')
    return out


def frost_jarl(f):
    """The frost jarl on the iron golem (Bosse 2)."""
    if f != 'iron_golem':
        return []
    out = []
    # a crown of ice spikes round the top of the head (head: x -4..4, y -12..-2, z -5.5..2.5)
    out += chain('head', (0.0, -12.0, -4.5), (0, 0, 0), [(2, 4, 2), (1, 3, 1), (1, 2, 1)], 'icicle', name='crownm')
    out += sym(chain('head', (-2.5, -12.0, -4.5), (0, 0, -0.2), [(2, 3, 2), (1, 2, 1)], 'icicle', name='crowns'))
    out += sym(chain('head', (-3.2, -12.0, 1.2), (-0.2, 0, -0.3), [(2, 2, 2), (1, 2, 1)], 'icicle', name='crownb'))
    # the icicle beard under the nose and along the jaw
    out += chain('head', (0.0, -1.2, -6.5), (R(180), 0, 0), [(2, 4, 2), (1, 3, 1), (1, 2, 1)], 'icicle', name='beardm')
    out += sym(chain('head', (-2.6, -2.2, -5.2), (R(180), 0, 0.15), [(2, 3, 2), (1, 3, 1)], 'icicle', name='beards'))
    out += sym(chain('head', (-3.6, -2.4, -2.5), (R(180), 0, 0.2), [(1, 3, 1), (1, 2, 1)], 'icicle', name='beardj'))
    # shoulders of pack ice with spikes (body: x -9..9, y -2..10, z -6..5)
    out += sym([block('body', (-11.0, -4.0, -6.5), (7, 3, 12), 'ice', key='shoulder')])
    out += sym(chain('body', (-8.0, -4.0, -2.0), (0, 0, -0.35), [(2, 4, 2), (1, 3, 1)], 'icicle', name='spikef'))
    out += sym(chain('body', (-7.0, -4.0, 3.0), (-0.3, 0, -0.25), [(2, 3, 2), (1, 2, 1)], 'icicle', name='spikeb'))
    # crystals of ice on his back
    out += chain('body', (0.0, 1.0, 5.0), (R(-65), 0, 0), [(3, 4, 3), (2, 3, 2), (1, 2, 1)], 'ice', name='backm')
    out += sym(chain('body', (-4.0, 4.0, 5.0), (R(-70), 0, -0.3), [(2, 3, 2), (1, 2, 1)], 'ice', name='backs'))
    # the club: a merlon of a castle wall in his right fist (arm: x -13..-9, y -2.5..27.5)
    out.append(block('right_arm', (-15.5, 25.0, -5.0), (9, 7, 10), 'castle_stone', key='club'))
    out.append(block('right_arm', (-15.5, 32.0, -5.0), (3, 3, 10), 'castle_stone', key='merlon'))
    out.append(block('right_arm', (-9.5, 32.0, -5.0), (3, 3, 10), 'castle_stone', key='merlon'))
    out.append(block('right_arm', (-13.5, 2.0, -3.5), (5, 2, 7), 'ice', key='band'))
    # an ice gauntlet on the left, icicles hanging from it
    out.append(block('left_arm', (8.5, 19.0, -3.5), (5, 9, 7), 'ice', key='gauntlet'))
    out += chain('left_arm', (10.0, 28.0, -1.5), (R(180), 0, 0), [(2, 3, 2), (1, 2, 1)], 'icicle', name='gic1')
    out += chain('left_arm', (12.0, 28.0, 1.5), (R(180), 0, 0), [(1, 3, 1), (1, 2, 1)], 'icicle', name='gic2')
    # snow boots (legs: x -3.5..2.5, y -3..13 below their pivots)
    out.append(block('right_leg', (-4.0, 10.0, -3.5), (7, 3, 6), 'snow', key='boot'))
    out.append(block('left_leg', (-4.0, 10.0, -3.5), (7, 3, 6), 'snow', key='boot'))
    return out


def ember_colossus(f):
    """The ember colossus on the magma cube (Bosse 2): basalt crags on its crust, glowing seams.
    The cube is eight slices (cube0 on top … cube7 at the bottom, x/z -4..4); the gear sits on them."""
    if f != 'magma_cube':
        return []
    out = []
    # crags on the top
    out += chain('cube0', (0.0, 16.0, 0.0), (0, 0, 0), [(3, 2, 3), (2, 2, 2), (1, 1, 1)], 'ember_crust', name='topm')
    for k, (x, z, rx, rz) in enumerate(((-2.6, -2.6, -0.3, -0.3), (2.6, -2.2, -0.3, 0.3), (2.4, 2.6, 0.3, 0.3), (-2.4, 2.4, 0.3, -0.3))):
        out += chain('cube0', (x, 16.0, z), (rx, 0, rz), [(2, 2, 2), (1, 2, 1)], 'ember_crust', name=f'top{k % 2}')
    # plates of basalt on the sides, each on its own slice (they move as it squashes)
    for k, (bone, y) in enumerate((('cube2', 18.0), ('cube5', 21.0))):
        out += sym([block(bone, (-4.7, y, -3.0 + k), (1, 2, 4), 'ember_crust', key=f'side{k}')])
        out.append(block(bone, (-2.5 - k, y, -4.7), (4, 2, 1), 'ember_crust', key=f'front{k}'))
        out.append(block(bone, (-1.5 + k, y, 3.7), (4, 2, 1), 'ember_crust', key=f'back{k}'))
    # glowing lava where the crust has broken
    out.append(block('cube3', (-4.4, 19.0, 1.5), (1, 1, 2), 'magma', key='lava'))
    out.append(block('cube4', (3.4, 20.0, -2.0), (1, 1, 2), 'magma', key='lava'))
    out.append(block('cube6', (0.5, 22.0, -4.4), (2, 1, 1), 'magma', key='lava2'))
    return out


def ram_titan(f):
    """The ram titan on the ravager (Bosse 2). The head is a bone of the neck; the body lies turned by 90°
    (its local +z is the top of the back, local y runs from the front -10 to the rear 19)."""
    if f != 'ravager':
        return []
    head = 'neck/head'
    out = []
    # the ram: an oak log on the forehead, bound with iron, an iron head with a red rune (head front at z -14)
    out.append(block(head, (-3.0, -15.0, -25.0), (6, 6, 12), 'oak_log', key='log'))
    out.append(block(head, (-4.0, -16.0, -29.0), (8, 8, 4), 'ram_iron', key='ram'))
    for z in (-22.0, -17.0):
        out.append(block(head, (-3.5, -15.5, z), (7, 7, 1), 'iron', key='band'))
    for x, y in ((-4.6, -16.6), (3.6, -16.6), (-4.6, -9.4), (3.6, -9.4)):
        out.append(block(head, (x, y, -29.6), (1, 1, 1), 'gold', key='rivet'))
    # a plate of steel over the brow and skulls at the sides of the head
    out.append(block(head, (-8.5, -21.0, -12.0), (17, 2, 12), 'war_steel', key='brow'))
    out += sym([block(head, (-9.8, -12.0, -9.0), (2, 4, 4), 'bone', key='skull')])
    # armour on the back with gold rivets, chains down the sides
    out.append(block('body', (-8.0, -10.0, 13.0), (16, 16, 2), 'war_steel', key='plate'))
    out.append(block('body', (-7.0, 6.0, 11.0), (14, 13, 2), 'war_steel', key='plate2'))
    for y in (-7.0, -1.0, 4.0):
        out += sym([block('body', (-6.0, y, 15.0), (1, 1, 1), 'gold', key='rivet')])
    out += sym([block('body', (-8.0, -6.0, 5.0), (1, 1, 8), 'iron', key='chain'),
                block('body', (-7.0, 9.0, 3.0), (1, 1, 8), 'iron', key='chain')])
    # greaves on the legs (legs: x -4..4, y 0..37, z -4..4)
    for leg in ('right_front_leg', 'right_hind_leg'):
        out += sym([block(leg, (-4.5, 6.0, -4.5), (9, 7, 9), 'war_steel', key='greave')])
    return out


DESIGNS = {'boss_storm_witch': storm_witch, 'boss_frost_jarl': frost_jarl, 'boss_ember_colossus': ember_colossus,
           'boss_ember_colossus_2': ember_colossus, 'boss_ember_colossus_3': ember_colossus, 'boss_ram_titan': ram_titan}


def details(tex, family, ev, seed):
    glow = Image.new('RGBA', tex.size, (0, 0, 0, 0))
    p = skins.Painter(tex, glow, family, ev, seed)
    pal = PALETTES[ev]
    n = max(4, tex.width * tex.height // 200)
    if ev == 'boss_frost_jarl':
        p.anywhere(n, 8, hexc('9fe4ff'), glow=hexc('3aa8e8'), k=0.9)
        p.anywhere(n // 2, 3, hexc('f2fbff'), k=0.9)
    elif ev == 'boss_ember_colossus':
        p.anywhere(n // 2 + 2, 7, hexc('ff8a1a'), glow=hexc('ff6a0a'), k=0.95)
    elif ev == 'boss_ember_colossus_2':
        # Bosse 2: brighter at 66 % of his life
        p.anywhere(n + 2, 8, hexc('ffa53a'), glow=hexc('ff8a1a'), k=0.95)
    elif ev == 'boss_ember_colossus_3':
        # ... and white-hot at 33 %
        p.anywhere(2 * n + 4, 9, hexc('ffd37a'), glow=hexc('ffc04a'), k=0.97)
    elif ev == 'boss_storm_witch':
        if family == 'witch':
            p.edge(['body', 'robe'], skins.SIDES, -1, pal['detail'])
        else:
            p.anywhere(n, 6, hexc('fff27a'), glow=hexc('ffd83a'), k=0.9)
    elif ev == 'boss_ram_titan':
        p.anywhere(n, 3, hexc('c8962a'), k=0.9)
        p.anywhere(n // 2, 6, hexc('8a1018'), k=0.8)
        p.anywhere(max(2, n // 4), 4, hexc('d0202a'), glow=hexc('ff3030'), k=0.9)   # glowing war paint (Bosse 2)
    if family != 'whole':
        p.eyes()
    return tex, glow


def build(van, out_root, atlas):
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
        shapes = {}
        for fam in FAMILIES.get(ev, ()):
            boxes = DESIGNS[ev](fam)
            size = atlas.pack(boxes)
            gtex, gglow = atlas.paint(boxes, size)
            gtex.save(os.path.join(out, f'gear_{fam}.png'))
            gglow.save(os.path.join(out, f'gear_{fam}_glow.png'))
            shapes[fam] = (size, boxes)
        all_shapes[ev], all_textures[ev], all_glows[ev] = shapes, textures, glows
    return all_shapes, all_textures, all_glows
