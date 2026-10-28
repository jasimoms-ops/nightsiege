"""
The dungeon champions in the style of the fragment they guard (design update, Simon 28/29 October
2026): blood red, storm yellow, frost blue, mist grey-violet, shadow violet, ember orange. Built by
build.py; every champion is a folder "champion_<dungeon>" swapped in by the client (EventTextures).

    stone_crypt   wither skeleton   grave warden: rusty chainmail, a red-eyed skull crown
    desert_crypt  husk              mummy lord: linen wraps, a gold crown with a red stone
    illager_keep  vindicator        fortress lord: a storm banner on the back, yellow trim
    frost_keep    stray             frost lord: an icicle crown, a fur mantle
    poison_swamp  witch             swamp mother: a moss cloak, green lanterns at the belt
    deep_cellar   skeleton          shadow warden: black plate, violet eyes, shoulder spikes
    nether_forge  zombie (knight)   ember lord: glowing seams, a forge hammer on the back
    basalt_forge  wither skeleton   soul smith: a soul-fire crown, an anvil-plate chest
"""
import zlib
from PIL import Image
import skins
from gear import block, chain, sym, cluster, ring
from materials import hexc, mix, mul, ramp, noise
import materials
import foes  # noqa: F401  (registers linen, plate, rope, crimson ...)

stops = skins.stops
N = dict(creeper=stops('1e1a1a', '4a4040', '7d6f6a', 'a69890'), spider=stops('0d0707', '241010', '3d1818', '5a2424'))

PALETTES = {
    'champion_stone_crypt': dict(skin=stops('1a1416', '3a2e30', '5e4c4c', '84706c'), cloth=stops('1a0405', '4e090c', '8a141a', 'b8282c'),
                                 pants=stops('120a0a', '2c1716', '4c2826', '6b3a36'), bone=stops('140c0c', '3a2222', '6a4440', '9a7068'),
                                 eye=hexc('ff2c2c'), glow=hexc('b0101a'), detail=hexc('8a1a10'), **N),
    'champion_desert_crypt': dict(skin=stops('2a2016', '5a4a32', '8c7a56', 'c2ae84'), cloth=stops('3a2a14', '6e5228', 'a8864a', 'd8c08a'),
                                  pants=stops('201810', '3e3020', '5e4a32', '7e6646'), bone=stops('4a3b34', '9a8a7c', 'd8cbb6', 'f2e8d6'),
                                  eye=hexc('ff4a2a'), glow=hexc('b0201a'), detail=hexc('d8a62a'), **N),
    'champion_illager_keep': dict(skin=stops('262428', '4a474e', '76727c', 'a09ca8'), cloth=stops('141210', '2e2a22', '4c4636', '6a624a'),
                                  pants=stops('100e0a', '24201a', '3a342a', '524a3c'), bone=stops('4a3b34', '9a8a7c', 'd8cbb6', 'f2e8d6'),
                                  eye=hexc('ffe23a'), glow=hexc('e8b818'), detail=hexc('ffd84a'), **N),
    'champion_frost_keep': dict(skin=stops('1e3a56', '3f6a92', '78a8d0', 'c4e4ff'), cloth=stops('16283a', '2c4a66', '4e7494', '7ea2c0'),
                                pants=stops('101c2a', '20344a', '36506a', '50708e'), bone=stops('5a7c98', '8fb2cc', 'c4e0f2', 'f4fbff'),
                                eye=hexc('bff0ff'), glow=hexc('5fbfff'), detail=hexc('f2fbff'), **N),
    'champion_poison_swamp': dict(skin=stops('2a2a2e', '4e4c56', '76727e', 'a29eaa'), cloth=stops('141a10', '2a3620', '445432', '627448'),
                                  pants=stops('10120c', '20261a', '343e2a', '4a563c'), bone=stops('4a4652', '86828e', 'bab6c4', 'e2deea'),
                                  eye=hexc('9cff6a'), glow=hexc('5ac030'), detail=hexc('8a7aa8'), **N),
    'champion_deep_cellar': dict(skin=stops('0a080e', '1c1626', '30263e', '463858'), cloth=stops('08060c', '16101e', '261c34', '3a2a4c'),
                                 pants=stops('060508', '100c16', '1c1626', '2a2238'), bone=stops('0c0a10', '262030', '463c56', '6a5c80'),
                                 eye=hexc('c86aff'), glow=hexc('8a2ad8'), detail=hexc('b07aff'), **N),
    'champion_nether_forge': dict(skin=stops('120e0c', '2c2420', '4a3c34', '6a5648'), cloth=stops('0e0a08', '241a14', '3e2c20', '583e2c'),
                                  pants=stops('0a0806', '1c1410', '30221a', '463224'), bone=stops('1c1612', '3e342c', '6a5a4a', '948068'),
                                  eye=hexc('ffa23a'), glow=hexc('ff7a14'), detail=hexc('ffcf5a'), **N),
    'champion_basalt_forge': dict(skin=stops('121214', '28282c', '424248', '5e5e66'), cloth=stops('0a0c10', '181e26', '28323e', '3a4858'),
                                  pants=stops('08090c', '141820', '222a36', '323e4c'), bone=stops('101012', '26262c', '44444e', '64646e'),
                                  eye=hexc('62f2ff'), glow=hexc('1ea8cc'), detail=hexc('7ff8ff'), **N),
}
skins.EVENTS.update(PALETTES)

CHAMPIONS = {
    'champion_stone_crypt': [('skeleton/wither_skeleton.png', 'wither_skeleton', 'skeleton')],
    'champion_desert_crypt': [('zombie/husk.png', 'husk', 'humanoid')],
    'champion_illager_keep': [('illager/vindicator.png', 'vindicator', 'illager')],
    'champion_frost_keep': [('skeleton/stray.png', 'stray', 'skeleton')],
    'champion_poison_swamp': [('witch/witch.png', 'witch', 'witch')],
    'champion_deep_cellar': [('skeleton/skeleton.png', 'skeleton', 'skeleton')],
    'champion_nether_forge': [('zombie/zombie.png', 'zombie', 'humanoid')],
    'champion_basalt_forge': [('skeleton/wither_skeleton.png', 'wither_skeleton', 'skeleton')],
}
FAMILIES = {k: (v[0][2],) for k, v in CHAMPIONS.items()}
GOLD = hexc('d8a62a')


def chainmail(p):
    c = ramp([(0, hexc('2a1e1a')), (0.5, hexc('5a3e30')), (1, hexc('8a5a40'))], 0.3 + 0.5 * p.rng.random())
    if (p.i + p.j) % 2 == 0:
        c = mul(c, 0.75)
    return mul(c, materials.FACE_LIGHT.get(p.face, 1.0)), None


def storm_cloth(p):
    if p.face not in ('top', 'bottom') and (p.i == 0 or p.i == p.fw - 1 or p.j == p.fh - 1):
        return hexc('ffd84a'), hexc('c8a018')
    c = ramp([(0, hexc('101418')), (0.5, hexc('242c38')), (1, hexc('3a4658'))], 0.3 + 0.4 * p.rng.random())
    return mul(c, materials.FACE_LIGHT.get(p.face, 1.0)), None


def fur(p):
    c = ramp([(0, hexc('6a6a72')), (0.5, hexc('b0b4bc')), (1, hexc('eef2f6'))], 0.3 + 0.6 * p.rng.random())
    return mul(c, materials.FACE_LIGHT.get(p.face, 1.0)), None


def green_lantern(p):
    return mix(hexc('2a6a1a'), hexc('aaff6a'), p.rng.random()), hexc('6ad83a')


def shadow_plate(p):
    c = ramp([(0, hexc('08060c')), (0.5, hexc('1c1628')), (1, hexc('3a2e52'))], 0.3 + 0.5 * p.rng.random())
    glow = hexc('8a2ad8') if p.edge and p.rng.random() < 0.3 else None
    return mul(c, materials.FACE_LIGHT.get(p.face, 1.0)), glow


def ember_iron(p):
    c = ramp([(0, hexc('1a1410')), (0.5, hexc('3a2c22')), (1, hexc('5a4434'))], 0.3 + 0.4 * p.rng.random())
    glow = None
    if p.rng.random() < 0.12 or (p.face not in ('top', 'bottom') and p.j == p.fh // 2):
        c, glow = hexc('ff9a2a'), hexc('ff7a14')
    return mul(c, materials.FACE_LIGHT.get(p.face, 1.0)), glow


for _name, _fn in (('chainmail', chainmail), ('storm_cloth', storm_cloth), ('fur', fur),
                   ('green_lantern', green_lantern), ('shadow_plate', shadow_plate), ('ember_iron', ember_iron)):
    materials.MATERIALS[_name] = _fn


def crown(bone, y, mat, gem):
    out = [block(bone, (-4.6, y, -4.6), (9, 2, 1), mat, key='cbf'), block(bone, (-4.6, y, 3.6), (9, 2, 1), mat, key='cbf')]
    out += sym([block(bone, (-4.6, y, -3.6), (1, 2, 7), mat, key='cbs')])
    out.append(block(bone, (-1.0, y - 3, -4.6), (2, 3, 1), mat, key='cpm'))
    out += sym([block(bone, (-4.6, y - 2, -4.6), (1, 2, 1), mat, key='cp')])
    out.append(block(bone, (-0.5, y - 2.0, -4.9), (1, 1, 1), gem, key='gem'))
    return out


def stone_crypt(f):
    if f != 'skeleton':
        return []
    out = [block('body', (-4.4, -0.2, -2.4), (9, 10, 5), 'chainmail', key='mail')]
    out += sym([block('body', (-6.6, -1.4, -2.8), (4, 3, 6), 'chainmail', key='pad')])
    out += crown('head', -9.6, 'blood_bone', 'blood_crystal')
    return out


def desert_crypt(f):
    if f != 'humanoid':
        return []
    out = crown('head', -9.6, 'gold', 'blood_crystal')
    out.append(block('body', (-4.4, 6.0, -2.4), (9, 2, 5), 'linen', key='wrap'))
    out += sym([block('right_arm', (-3.3, 2.0, -2.3), (4, 2, 4), 'linen', key='wrapa')])
    out.append(block('body', (-4.5, 0.2, 2.15), (9, 15, 1), 'royal', key='cape'))
    return out


def illager_keep(f):
    if f != 'illager':
        return []
    out = [block('body', (-0.5, -24.0, 4.2), (1, 34, 1), 'pole', key='pole'),
           block('body', (-4.5, -22.0, 4.6), (9, 12, 1), 'storm_cloth', key='flag'),
           block('body', (-2.0, 2.0, 3.0), (4, 2, 2), 'war_steel', key='holder')]
    out += sym([block('body', (-6.8, -1.2, -3.4), (4, 3, 7), 'war_steel', key='pad')])
    out.append(block('head', (-4.6, -10.8, -4.6), (9, 3, 9), 'war_steel', key='helm'))
    return out


def frost_keep(f):
    if f != 'skeleton':
        return []
    out = []
    for k, (x, h) in enumerate(((-3.5, 3), (-1.5, 5), (0.5, 4), (2.5, 5), (3.5, 3))):
        out += chain('head', (x, -8, -3.2), (0, 0, 0), [(1, h, 1)], 'icicle', name=f'ic{k}')
    out.append(block('body', (-6.0, -1.6, -3.0), (12, 3, 6), 'fur', key='mantle'))
    out.append(block('body', (-4.5, 0.0, 2.1), (9, 12, 1), 'fur', key='cloak'))
    return out


def poison_swamp(f):
    if f != 'witch':
        return []
    out = [block('body', (-4.6, 0.0, 3.0), (9, 18, 1), 'moss', key='cloak')]
    out += sym([block('body', (-5.6, -1.0, -3.4), (3, 2, 7), 'moss', key='shmoss')])
    out += sym([block('body', (-5.4, 9.0, -1.0), (2, 3, 2), 'green_lantern', key='lantern')])
    out.append(block('head', (-5.4, -10.6, -5.4), (11, 1, 11), 'moss', key='brim'))
    return out


def deep_cellar(f):
    if f != 'skeleton':
        return []
    out = [block('body', (-4.4, -0.2, -2.4), (9, 8, 5), 'shadow_plate', key='plate')]
    out += sym(chain('body', (-5.5, 0.5, 0), (0.1, 0, -0.7), [(2, 4, 2), (1, 3, 1)], 'shadow_plate', name='sp'))
    out.append(block('head', (-4.6, -8.6, -4.6), (9, 3, 9), 'shadow_plate', key='helm'))
    return out


def nether_forge(f):
    if f != 'humanoid':
        return []
    out = [block('head', (-4.7, -8.7, -4.7), (9, 4, 9), 'ember_iron', key='helm'),
           block('head', (-4.7, -3.7, -4.7), (9, 4, 9), 'ember_iron', key='visor')]
    out += sym([block('body', (-7.0, -1.4, -3.0), (4, 3, 6), 'ember_iron', key='pad')])
    out.append(block('body', (-0.6, -6.0, 2.6), (1, 16, 1), 'pole', key='haft'))
    out.append(block('body', (-3.0, -9.0, 2.2), (6, 4, 2), 'ember_iron', key='hammer'))
    return out


def basalt_forge(f):
    if f != 'skeleton':
        return []
    out = ring('head', (0, -5, 5.6), 6.0, 12, (3, 1, 1), 'soul', spikes=2, spike_mat='soul', name='halo')
    out.append(block('body', (-4.4, 0.0, -2.6), (9, 6, 1), 'iron', key='anvil'))
    out.append(block('body', (-3.0, 6.0, -2.6), (6, 2, 1), 'iron', key='anvil2'))
    out += cluster('body', (0, 2.5, -3.0), (0.9, 0, 0), 'soul_crystal', sizes=((2, 2, 2), (1, 2, 1)), spread=1.0, name='core')
    return out


DESIGNS = {'champion_stone_crypt': stone_crypt, 'champion_desert_crypt': desert_crypt,
           'champion_illager_keep': illager_keep, 'champion_frost_keep': frost_keep,
           'champion_poison_swamp': poison_swamp, 'champion_deep_cellar': deep_cellar,
           'champion_nether_forge': nether_forge, 'champion_basalt_forge': basalt_forge}


def details(tex, family, ev, seed):
    glow = Image.new('RGBA', tex.size, (0, 0, 0, 0))
    p = skins.Painter(tex, glow, family, ev, seed)
    pal = PALETTES[ev]
    sides = skins.SIDES
    if family in ('humanoid', 'skeleton'):
        p.lines(['arm', 'body', 'leg'], sides, 1, 5, pal['detail'], glow=pal['glow'], k=0.85, dirs=((0, 1),))
    elif family in ('illager', 'witch'):
        p.edge(['body', 'robe'], sides, -1, pal['detail'])
    p.eyes()
    return tex, glow


def build(van, out_root, atlas):
    import os
    all_shapes, all_textures, all_glows = {}, {}, {}
    for ev, mobs in CHAMPIONS.items():
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
