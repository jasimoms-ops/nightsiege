"""
The four new bosses of the content update (design update, Simon 29 October 2026; their fights come
with the content update, which picks them up by these folder names):

    boss_frost_jarl      iron golem   packed ice, icicle beard, cold blue cracks that glow
    boss_ember_colossus  iron golem   basalt with lava veins (also a magma cube skin, should it be one)
    boss_storm_witch     witch        a gown of storm clouds, a crown of lightning; her phantom
    boss_ram_titan       ravager      armoured dark steel with gold rivets, an iron ram head
"""
import zlib
from PIL import Image
import skins
from gear import block, chain, sym, cluster
from materials import hexc
import materials  # noqa: F401
import bosses  # noqa: F401  (registers the boss materials)

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
skins.EVENTS.update(PALETTES)

BOSSES = {
    'boss_frost_jarl': [('iron_golem/iron_golem.png', 'iron_golem', 'whole')],
    'boss_ember_colossus': [('iron_golem/iron_golem.png', 'iron_golem', 'whole'), ('slime/magmacube.png', 'magmacube', 'whole')],
    'boss_storm_witch': [('witch/witch.png', 'witch', 'witch'), ('phantom/phantom.png', 'phantom', 'whole')],
    'boss_ram_titan': [('illager/ravager.png', 'ravager', 'whole')],
}
FAMILIES = {'boss_storm_witch': ('witch',)}


def storm_witch(f):
    if f != 'witch':
        return []
    out = [block('body', (-5.0, 0.0, 3.0), (10, 18, 1), 'storm_cloud', key='gown')]
    out += sym([block('body', (-6.0, -1.0, -3.6), (3, 3, 7), 'storm_cloud', key='cloud')])
    for k, x in enumerate((-3.5, -1.0, 1.5, 3.5)):
        out += chain('head', (x, -11, 0), (0, 0, 0.2 * (k - 1.5)), [(1, 3, 1), (1, 2, 1)], 'bolt', name=f'bolt{k}')
    return out


DESIGNS = {'boss_storm_witch': storm_witch}


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
    elif ev == 'boss_storm_witch':
        if family == 'witch':
            p.edge(['body', 'robe'], skins.SIDES, -1, pal['detail'])
        else:
            p.anywhere(n, 6, hexc('fff27a'), glow=hexc('ffd83a'), k=0.9)
    elif ev == 'boss_ram_titan':
        p.anywhere(n, 3, hexc('c8962a'), k=0.9)
        p.anywhere(n // 2, 6, hexc('8a1018'), k=0.8)
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
