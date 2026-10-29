"""
The eight dungeon champions (Simon, 29 October 2026: "kümmer du dich um Dungeon-Wächter"):
each wears the colour of the night fragment he drops and the look of his dungeon. Built by
build.py next to the events and the bosses; every champion is its own folder
"champion_<dungeon>" and the mod swaps the champion's textures for the folder's like a
boss look (EventTextures, BossLooks).

    stone_crypt    Grabwächter      wither skeleton  blood    rusty mail coif, a gravestone on his back, red candles
    desert_crypt   Mumienfürst      husk             blood    linen wraps, a gold and lapis crown, a broad collar
    illager_keep   Festungsherr     vindicator       storm    helmet with a lightning rod, copper pauldrons, storm banner
    frost_keep     Frostherr        stray            frost    icicle crown, fur mantle and a frost cape, ice on the shoulders
    poison_swamp   Sumpfmutter      witch            mist     moss on hat and shoulders, mushrooms, green lanterns
    deep_cellar    Schattenwärter   skeleton         shadow   black helm with a violet visor, a key ring, broken shackles
    nether_forge   Glutherr         zombie           ember    great helm with an ember slit, glowing seams, a forge hammer
    basalt_forge   Seelenschmied    wither skeleton  ember    an anvil with soul fire on his back, basalt pauldrons, an apron

    python3 champions.py <vanilla textures/entity dir> <out dir>     (textures and preview sheets only)
"""
import os
import sys
import zlib
from PIL import Image, ImageDraw
import skins
import bosses   # noqa: F401  (its materials: soul, pole, royal ...)
from geom import rects
from gear import block, chain, sym, ring, cluster, R
from materials import hexc, mix, mul, ramp, noise
import materials

stops = skins.stops

# fragment colours: blood red, storm yellow, frost blue, mist grey-violet, shadow violet, ember orange
PALETTES = {
    'champion_stone_crypt': dict(
        skin=stops('2e2626', '6b605c', 'a89c94', 'd6ccc2'), cloth=stops('121214', '2e2c2c', '544e4a', '7a706a'),
        pants=stops('120a0a', '2c1716', '4c2826', '6b3a36'), bone=stops('0e0c0d', '2a2427', '4e4448', '746a6e'),
        creeper=stops('1e1a1a', '4a4040', '7d6f6a', 'a69890'), spider=stops('0d0707', '241010', '3d1818', '5a2424'),
        eye=hexc('ff3a2a'), glow=hexc('a8180e'), detail=hexc('b8281e')),
    'champion_desert_crypt': dict(
        skin=stops('241a10', '4e3c26', '7e6444', 'aa8c64'), cloth=stops('3e3424', '7e6e50', 'b8a682', 'e2d6b2'),
        pants=stops('0a1230', '16265a', '263e86', '3e5aae'), bone=stops('4a3b34', '9a8a7c', 'd8cbb6', 'f2e8d6'),
        creeper=stops('1e1a1a', '4a4040', '7d6f6a', 'a69890'), spider=stops('0d0707', '241010', '3d1818', '5a2424'),
        eye=hexc('ff3a2a'), glow=hexc('a8180e'), detail=hexc('d8a62a')),
    'champion_illager_keep': dict(
        skin=stops('262828', '545a5a', '868c8c', 'b0b6b6'), cloth=stops('0e121a', '1e2634', '344054', '505e76'),
        pants=stops('140f0b', '2a2016', '463624', '5e4a32'), bone=stops('4a3b34', '9a8a7c', 'd8cbb6', 'f2e8d6'),
        creeper=stops('1e1a1a', '4a4040', '7d6f6a', 'a69890'), spider=stops('0d0707', '241010', '3d1818', '5a2424'),
        eye=hexc('ffe24a'), glow=hexc('e0a812'), detail=hexc('ffd23a')),
    'champion_frost_keep': dict(
        skin=stops('1e3a56', '3f6a92', '78a8d0', 'c4e4ff'), cloth=stops('101e34', '22406a', '466c9c', '86a8cc'),
        pants=stops('101c2a', '20344a', '36506a', '50708e'), bone=stops('5a7c98', '8fb2cc', 'c4e0f2', 'f4fbff'),
        creeper=stops('284a68', '4a7698', '7eaccc', 'b8dcf2'), spider=stops('0e1a28', '1c3046', '2e4a66', '466888'),
        eye=hexc('bff0ff'), glow=hexc('5fbfff'), detail=hexc('f2fbff')),
    'champion_poison_swamp': dict(
        skin=stops('1a241a', '3a4c3a', '627662', '90a28c'), cloth=stops('120c18', '261e32', '42384e', '5e526c'),
        pants=stops('0e180c', '20301a', '38502c', '547042'), bone=stops('4a3b34', '9a8a7c', 'd8cbb6', 'f2e8d6'),
        creeper=stops('1e1a1a', '4a4040', '7d6f6a', 'a69890'), spider=stops('0d0707', '241010', '3d1818', '5a2424'),
        eye=hexc('e6d8ff'), glow=hexc('9a88c8'), detail=hexc('8fbf5a')),
    'champion_deep_cellar': dict(
        skin=stops('2e2626', '6b605c', 'a89c94', 'd6ccc2'), cloth=stops('06050a', '120d1a', '20182e', '322646'),
        pants=stops('120a0a', '2c1716', '4c2826', '6b3a36'), bone=stops('100c14', '2a2234', '4a3e5e', '706288'),
        creeper=stops('1e1a1a', '4a4040', '7d6f6a', 'a69890'), spider=stops('0d0707', '241010', '3d1818', '5a2424'),
        eye=hexc('c878ff'), glow=hexc('8a2be2'), detail=hexc('b070ff')),
    'champion_nether_forge': dict(
        skin=stops('100a08', '261a14', '443024', '624634'), cloth=stops('0c0c0e', '1c1c20', '303036', '4a4a52'),
        pants=stops('0a0a0c', '18181c', '2a2a30', '404048'), bone=stops('4a3b34', '9a8a7c', 'd8cbb6', 'f2e8d6'),
        creeper=stops('1e1a1a', '4a4040', '7d6f6a', 'a69890'), spider=stops('0d0707', '241010', '3d1818', '5a2424'),
        eye=hexc('ffa23a'), glow=hexc('ff7a14'), detail=hexc('ffcf5a')),
    'champion_basalt_forge': dict(
        skin=stops('2e2626', '6b605c', 'a89c94', 'd6ccc2'), cloth=stops('180c06', '36200f', '5a381e', '80522e'),
        pants=stops('120a0a', '2c1716', '4c2826', '6b3a36'), bone=stops('0c0c10', '222228', '40404a', '62626c'),
        creeper=stops('1e1a1a', '4a4040', '7d6f6a', 'a69890'), spider=stops('0d0707', '241010', '3d1818', '5a2424'),
        eye=hexc('62f2ff'), glow=hexc('1ea8cc'), detail=hexc('ff8a2a')),
}
skins.EVENTS.update(PALETTES)

# folder: [(vanilla path under textures/entity, our file name, family for the details)]
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
# clothes: (vanilla layout, our file name, recolour class, how it is worn, swapped as a vanilla texture?)
# "zombie_outer"/"skeleton_outer" are drawn by EventClothingLayer; the stray's overlay is its own vanilla layer.
CLOTHES = {
    'champion_stone_crypt': ('skeleton/stray_overlay.png', 'skeleton_outer', 'stray_overlay', 'mail', False),
    'champion_desert_crypt': ('zombie/drowned_outer_layer.png', 'zombie_outer', 'drowned_outer_layer', 'wraps', False),
    'champion_frost_keep': ('skeleton/stray_overlay.png', 'stray_overlay', 'stray_overlay', 'cloak', True),
    'champion_deep_cellar': ('skeleton/stray_overlay.png', 'skeleton_outer', 'stray_overlay', 'tabard', False),
    'champion_basalt_forge': ('skeleton/stray_overlay.png', 'skeleton_outer', 'stray_overlay', 'apron', False),
}
FAMILIES = {k: (v[0][2],) for k, v in CHAMPIONS.items()}
# the German names, the fragment and the epithet (for the sheets)
NAMES = {
    'champion_stone_crypt': ('Grabwächter', 'Hüter der stillen Halle', 'Steingruft · Blut'),
    'champion_desert_crypt': ('Mumienfürst', 'Der im Sand nicht schläft', 'Wüstengruft · Blut'),
    'champion_illager_keep': ('Festungsherr', 'Herr über Wall und Banner', 'Illager-Feste · Gewitter'),
    'champion_frost_keep': ('Frostherr', 'Der Winter, der nicht weicht', 'Frostfeste · Frost'),
    'champion_poison_swamp': ('Sumpfmutter', 'Sie, die im Nebel wartet', 'Giftsumpf · Nebel'),
    'champion_deep_cellar': ('Schattenwärter', 'Schlüssel zur letzten Zelle', 'Tiefenkerker · Schatten'),
    'champion_nether_forge': ('Glutherr', 'Meister der ewigen Esse', 'Nether-Schmiede · Glut'),
    'champion_basalt_forge': ('Seelenschmied', 'Er schmiedet, was nicht stirbt', 'Basaltschmiede · Glut'),
}

GOLD = hexc('d8a62a')
LAPIS = hexc('22408e')


def lit(p, c):
    return mul(c, materials.FACE_LIGHT.get(p.face, 1.0))


def side(p):
    return p.face not in ('top', 'bottom')


# ---------------------------------------------------------------- materials

def grave_stone(p):
    """Weathered stone with moss; a red cross carved into the back that glows."""
    c = ramp([(0, hexc('3a3b3d')), (0.5, hexc('68696c')), (1, hexc('929396'))], 0.3 + 0.5 * p.rng.random())
    if p.rng.random() < 0.12:
        c = mix(c, hexc('4d6436'), 0.7)
    if p.face == 'back' and p.fw >= 5:
        mid = p.fw // 2
        if (p.i == mid and 2 <= p.j <= p.fh - 3) or (p.j == 4 and abs(p.i - mid) <= 2):
            return hexc('d8301e'), hexc('a8180e')
    if p.edge and side(p):
        c = mul(c, 0.82)
    return mul(c, noise(p, 0.04)), None


def rust_iron(p):
    c = ramp([(0, hexc('2a1810')), (0.5, hexc('5a3420')), (1, hexc('8a5634'))], 0.3 + 0.5 * p.rng.random())
    if p.rng.random() < 0.15:
        c = hexc('3e4044')   # bare iron under the rust
    if p.edge and side(p):
        c = mul(c, 1.2)
    return c, None


def chain_rust(p):
    c = hexc('6a4228') if p.j % 2 == 0 else hexc('2e1e14')
    return mul(c, noise(p, 0.08)), None


def chain_iron(p):
    c = hexc('4a4c52') if p.j % 2 == 0 else hexc('1e2024')
    return mul(c, noise(p, 0.08)), None


def wax_red(p):
    if p.face == 'top':
        return hexc('3a0608'), None
    return ramp([(0, hexc('5a0a10')), (1, hexc('b8202a'))], 0.4 + 0.5 * p.tip), None


def flame_red(p):
    return mix(hexc('ff5a2a'), hexc('ffd27a'), 0.3 + 0.5 * p.tip), mix(hexc('d8301a'), hexc('ffb04a'), p.tip)


def gold_lapis(p):
    """A gold band with a lapis stripe through the middle."""
    if side(p) and p.fh >= 2 and p.j == p.fh - 1:
        return mul(LAPIS, noise(p, 0.06)), None
    return mul(GOLD, noise(p, 0.06)), None


def nemes(p):
    """Striped head cloth: gold and lapis rows."""
    row = p.j if side(p) else p.i
    c = GOLD if row % 2 == 0 else LAPIS
    return mul(c, noise(p, 0.05)), None


def collar(p):
    """A broad collar of beads: rows of gold, lapis, gold, carnelian, turquoise from the outside in."""
    cols = [GOLD, LAPIS, GOLD, hexc('9a1a18'), hexc('2a9a8a')]
    if side(p):
        k = p.j
    else:
        k = min(p.i, p.j, p.fw - 1 - p.i, p.fh - 1 - p.j)
    return mul(cols[k % len(cols)], noise(p, 0.05)), None


def keep_steel(p):
    """Dark steel with a brass rim."""
    c = ramp([(0, hexc('1a1c22')), (0.5, hexc('323640')), (1, hexc('545a66'))], 0.35 + 0.4 * p.rng.random())
    if side(p) and (p.j == 0 or p.j == p.fh - 1):
        c = hexc('b08a2a')
    return lit(p, c), None


def rod_storm(p):
    if p.tip > 0.7:
        return mix(hexc('fff2a0'), hexc('ffffff'), (p.tip - 0.7) * 2), mix(hexc('e0a812'), hexc('fff2a0'), p.tip)
    return materials.copper(p)


BOLT = {(5, 1), (5, 2), (4, 2), (4, 3), (3, 3), (3, 4), (4, 4), (5, 4), (6, 4), (5, 5), (4, 5), (4, 6), (3, 6), (3, 7), (2, 8)}


def storm_banner(p):
    """A slate-blue banner with a yellow lightning bolt and a brass hem."""
    c = ramp([(0, hexc('141a24')), (1, hexc('2a3446'))], 0.4 + 0.3 * p.rng.random())
    if p.face in ('front', 'back') and p.fw >= 7:
        i = p.i if p.face == 'front' else p.fw - 1 - p.i
        if (i, p.j) in BOLT:
            return hexc('ffe24a'), hexc('e0a812')
        if p.j == p.fh - 1:
            return (hexc('b08a2a'), None) if p.i % 2 == 0 else (None, None)   # tassels
        if p.i == 0 or p.i == p.fw - 1:
            c = mul(c, 0.8)
    return c, None


def fur(p):
    c = ramp([(0, hexc('9aa8b4')), (0.6, hexc('dfe8ef')), (1, hexc('ffffff'))], 0.3 + 0.6 * p.rng.random())
    if side(p) and p.j >= p.fh - 1 and p.rng.random() < 0.4:
        return None, None
    return c, None


def frost_cape(p):
    """A deep blue cape with a fur hem and frost on its shoulders."""
    if side(p) and p.j >= p.fh - 2:
        return fur(p)
    if side(p) and p.j <= 1:
        return mix(hexc('b9e1ff'), hexc('ffffff'), p.rng.random() * 0.5), None
    c = ramp([(0, hexc('0e1a30')), (0.5, hexc('1e3660')), (1, hexc('34568a'))], 0.3 + 0.4 * p.rng.random())
    if p.rng.random() < 0.05:
        return hexc('a8deff'), hexc('3f8fcf')
    return c, None


def moss_strand(p):
    c, _ = materials.moss(p)
    if side(p) and p.j >= p.fh - 1 and p.rng.random() < 0.5:
        return None, None
    return c, None


def mushroom(p):
    if p.face == 'bottom':
        return hexc('c8b89a'), None
    c = hexc('b8202a') if p.rng.random() > 0.18 else hexc('f0ece0')
    return lit(p, c), None


def stem(p):
    return lit(p, hexc('d8ceb8')), None


def swamp_lantern(p):
    """An iron lantern with a pale green light inside."""
    if p.face in ('top', 'bottom') or (side(p) and (p.j == 0 or p.j == p.fh - 1)):
        return mul(hexc('2a2e2a'), noise(p, 0.1)), None
    return mix(hexc('9ae86a'), hexc('e8ffc0'), p.rng.random() * 0.5), hexc('6ad84a')


def spore(p):
    """Moss with a few glowing spores."""
    c, _ = materials.moss(p)
    if p.rng.random() < 0.08:
        return hexc('d8f0a0'), hexc('8fbf5a')
    return c, None


def dark_plate(p):
    c = ramp([(0, hexc('0a080e')), (0.5, hexc('1c1826')), (1, hexc('34304a'))], 0.3 + 0.4 * p.rng.random())
    if side(p) and p.j == 0:
        c = hexc('b08a2a')   # a gold rim, like the lines on the shadow fragment
    return lit(p, c), None


def shadow_visor(p):
    """The black helm: two violet slits glow where the eyes are."""
    c, _ = dark_plate(p)
    if p.face == 'front' and p.j == 4 and p.i in (1, 2, 3, 5, 6, 7):
        return hexc('e0b0ff'), hexc('a050ff')
    if p.face == 'front' and p.j == 5 and p.i in (2, 6):
        return hexc('7a3ab8'), hexc('5a1ab0')
    return c, None


def old_iron(p):
    c = ramp([(0, hexc('34363a')), (1, hexc('6a6c70'))], 0.3 + 0.5 * p.rng.random())
    if p.rng.random() < 0.2:
        c = hexc('5a3420')
    return c, None


def brass(p):
    """Old brass: the keys of the last cell."""
    c = ramp([(0, hexc('5a4214')), (0.6, hexc('a8822a')), (1, hexc('e0c060'))], 0.3 + 0.5 * p.rng.random())
    return c, None


def amethyst(p):
    c = ramp([(0, hexc('2a1240')), (0.6, hexc('7a3ab8')), (1, hexc('e0b0ff'))], p.tip)
    glow = None
    if p.edge or p.rng.random() < 0.3 + 0.4 * p.tip:
        glow = mix(hexc('5a1ab0'), hexc('d8a0ff'), p.tip)
    return mul(c, noise(p, 0.06)), glow


def ember_plate(p):
    """Blackened iron whose seams glow like embers."""
    c = ramp([(0, hexc('101012')), (0.5, hexc('222228')), (1, hexc('3a3a44'))], 0.3 + 0.4 * p.rng.random())
    if side(p) and p.j == p.fh - 1 and p.rng.random() < 0.6:
        return mix(hexc('ff7a14'), hexc('ffcf5a'), p.rng.random() * 0.5), hexc('ff6a0a')
    return lit(p, c), None


def ember_visor(p):
    """A great helm with a T-shaped slit full of embers."""
    c = ramp([(0, hexc('101012')), (0.5, hexc('222228')), (1, hexc('3a3a44'))], 0.3 + 0.4 * p.rng.random())
    if p.face == 'front' and ((p.j == 4 and 1 <= p.i <= 7) or (p.i == 4 and 4 <= p.j <= 7)):
        return mix(hexc('ff8a1f'), hexc('ffe08a'), p.rng.random() * 0.4), hexc('ff7a14')
    if side(p) and p.j == p.fh - 1:
        c = hexc('4a3a30')
    return lit(p, c), None


def hot_iron(p):
    """A hammer head: dark iron, white-hot at its striking faces."""
    if p.face in ('left', 'right'):
        return mix(hexc('ff8a1f'), hexc('fff0b0'), p.rng.random() * 0.6), hexc('ff9a2a')
    if p.edge:
        return mix(hexc('6a2a0a'), hexc('ff7a14'), 0.4), hexc('c2410c')
    return lit(p, mul(hexc('2a2a30'), noise(p, 0.1))), None


def anvil_iron(p):
    c = ramp([(0, hexc('3a3c44')), (1, hexc('6e7078'))], 0.3 + 0.5 * p.rng.random())
    return lit(p, c), None


def anvil_top(p):
    """The anvil's face: glowing hot in the middle."""
    if p.face == 'top':
        mid = 1 - abs(p.i - (p.fw - 1) / 2) / max(1, p.fw / 2)
        if mid > 0.35:
            return mix(hexc('ff7a14'), hexc('ffe08a'), mid), mix(hexc('c2410c'), hexc('ffb030'), mid)
    return anvil_iron(p)


def crying(p):
    c = ramp([(0, hexc('0a0410')), (1, hexc('2a0c4a'))], p.rng.random())
    if p.rng.random() < 0.3:
        return hexc('b040ff'), hexc('8a20e8')
    return c, None


def basalt_pillar(p):
    """Polished basalt: light grey with dark vertical grooves."""
    c = ramp([(0, hexc('4a4a50')), (1, hexc('7a7a82'))], 0.4 + 0.4 * p.rng.random())
    if side(p) and p.i % 2 == 1:
        c = mul(c, 0.7)
    return lit(p, c), None


def leather(p):
    c = ramp([(0, hexc('2a160a')), (1, hexc('6a3e1e'))], 0.3 + 0.5 * p.rng.random())
    return lit(p, c), None


for _name, _fn in list(globals().items()):
    if _name in ('grave_stone', 'rust_iron', 'chain_rust', 'chain_iron', 'wax_red', 'flame_red', 'gold_lapis', 'nemes',
                 'collar', 'keep_steel', 'rod_storm', 'storm_banner', 'fur', 'frost_cape', 'moss_strand', 'mushroom',
                 'stem', 'swamp_lantern', 'spore', 'dark_plate', 'shadow_visor', 'old_iron', 'amethyst', 'ember_plate',
                 'ember_visor', 'hot_iron', 'anvil_iron', 'anvil_top', 'crying', 'leather', 'basalt_pillar', 'brass'):
        materials.MATERIALS['cw_' + _name] = _fn   # own prefix: foes.py has a "leather" of its own


# ---------------------------------------------------------------- the gear
# Coordinates are model pixels in the bone's space (y down, -z is the front). Humanoid and
# skeleton heads: (-4..4, -8..0, -4..4), bodies (-4..4, 0..12, -2..2); illager and witch heads
# (-4..4, -10..0, -4..4), bodies (-4..4, 0..12, -3..3) with the robe to y 20; the witch's hat
# brim lies at y -10.

def grave_warden(f):
    """Grabwächter: a gravestone on his back, a rusty circlet, red candles on his shoulders."""
    if f != 'skeleton':
        return []
    out = []
    # the gravestone, its rounded top above the shoulders, a red cross on its back
    out.append(block('body', (-4.0, -3.0, 2.4), (8, 12, 2), 'cw_grave_stone', key='stone'))
    out.append(block('body', (-3.0, -4.0, 2.4), (6, 1, 2), 'cw_grave_stone', key='stone2'))
    out.append(block('body', (-2.0, -5.0, 2.4), (4, 1, 2), 'cw_grave_stone', key='stone3'))
    out.append(block('body', (-4.5, 1.0, 2.0), (9, 1, 1), 'cw_chain_rust', key='strap'))
    # a broken iron circlet, three points in front
    out.append(block('head', (-4.6, -8.6, -4.6), (9, 1, 1), 'cw_rust_iron', key='band_f'))
    out.append(block('head', (-4.6, -8.6, 3.6), (9, 1, 1), 'cw_rust_iron', key='band_f'))
    out += sym([block('head', (-4.6, -8.6, -3.6), (1, 1, 7), 'cw_rust_iron', key='band_s')])
    for k, (x, h) in enumerate(((-3.6, 2), (-0.5, 3), (2.6, 2))):
        out.append(block('head', (x, -8.6 - h, -4.6), (1, h, 1), 'cw_rust_iron', key=f'pt{h}'))
    # rusty pauldrons with red candles on them
    out += sym([block('body', (-6.6, -1.2, -2.5), (3, 2, 5), 'cw_rust_iron', key='pad')])
    out += sym([block('body', (-6.2, -4.2, -1.5), (1, 3, 1), 'cw_wax_red', key='candle3')])
    out += sym([block('body', (-6.2, -5.2, -1.5), (1, 1, 1), 'cw_flame_red', key='flame')])
    out += sym([block('body', (-5.0, -3.2, 0.6), (1, 2, 1), 'cw_wax_red', key='candle2')])
    out += sym([block('body', (-5.0, -4.2, 0.6), (1, 1, 1), 'cw_flame_red', key='flame')])
    # a chain across the chest
    out.append(block('body', (-0.5, -7.5, -0.5), (1, 15, 1), 'cw_chain_rust', pivot=(0, 5.5, -2.5), rot=(0, 0, R(38)),
                     key='chain'))
    return out


def mummy_lord(f):
    """Mumienfürst: a gold crown with a lapis band and a red scarab, striped lappets, a broad collar."""
    if f != 'humanoid':
        return []
    out = []
    out.append(block('head', (-4.6, -9.6, -4.6), (9, 2, 1), 'cw_gold_lapis', key='band_f'))
    out.append(block('head', (-4.6, -9.6, 3.6), (9, 2, 1), 'cw_gold_lapis', key='band_f'))
    out += sym([block('head', (-4.6, -9.6, -3.6), (1, 2, 7), 'cw_gold_lapis', key='band_s')])
    out.append(block('head', (-1.0, -13.6, -4.6), (2, 4, 1), 'gold', key='pt_mid'))
    out += sym([block('head', (-3.6, -11.6, -4.6), (1, 2, 1), 'gold', key='pt2')])
    out += sym([block('head', (-4.6, -11.6, -0.5), (1, 2, 1), 'gold', key='pt2')])
    out.append(block('head', (-0.5, -11.6, 3.6), (1, 2, 1), 'gold', key='pt2'))
    out.append(block('head', (-0.5, -12.4, -4.9), (1, 1, 1), 'blood_crystal', key='gem'))
    # striped lappets beside the face down onto the chest, a drape down the back of the head
    out += sym([block('head', (-5.1, -7.6, -3.0), (1, 8, 2), 'cw_nemes', key='lap')])
    out.append(block('head', (-4.6, -7.6, 4.1), (9, 5, 1), 'cw_nemes', key='drape'))
    out += sym([block('body', (-4.1, 0.0, -2.6), (2, 4, 1), 'cw_nemes', key='lap2')])
    # the broad collar and gold bands on the outstretched arms
    out.append(block('body', (-5.0, -0.6, -3.0), (10, 2, 6), 'cw_collar', key='cw_collar'))
    out += sym([block('right_arm', (-3.5, 6, -2.5), (5, 2, 5), 'gold', key='brace')])
    out += sym([block('right_arm', (-3.5, 2, -2.5), (5, 1, 5), 'cw_gold_lapis', key='brace2')])
    return out


def keep_lord(f):
    """Festungsherr: a steel helmet with a lightning rod, copper pauldrons, a storm banner on his back."""
    if f != 'illager':
        return []
    out = []
    out.append(block('head', (-4.6, -10.8, -4.6), (9, 4, 9), 'cw_keep_steel', key='helm'))
    out.append(block('head', (-1.0, -11.8, -1.0), (2, 1, 2), 'copper', key='knob'))
    out.append(block('head', (-0.5, -15.8, -0.5), (1, 4, 1), 'cw_rod_storm', key='rod'))
    out += sym([block('body', (-6.8, -1.2, -3.4), (4, 3, 7), 'copper', key='pad')])
    out.append(block('body', (-0.5, -21.0, 4.4), (1, 30, 1), 'pole', key='pole'))
    out.append(block('body', (0.5, -20.5, 4.4), (8, 10, 1), 'cw_storm_banner', key='flag'))
    out.append(block('body', (-1.0, -22.0, 3.9), (2, 1, 2), 'cw_rod_storm', key='tip'))
    out.append(block('body', (-2.0, 2.0, 3.0), (4, 2, 2), 'cw_keep_steel', key='holder'))
    return out


def frost_lord(f):
    """Frostherr: an icicle crown, a fur mantle and a frost cape, ice on his shoulders."""
    if f != 'skeleton':
        return []
    out = []
    for k, (x, z, h, tilt) in enumerate(((-3.5, -3.6, 3, -0.25), (-1.4, -3.8, 5, -0.08), (1.4, -3.8, 5, 0.08),
                                         (3.5, -3.6, 3, 0.25), (-3.6, 0, 4, -0.3), (3.6, 0, 4, 0.3),
                                         (-2.0, 3.4, 3, -0.15), (2.0, 3.4, 3, 0.15))):
        out += chain('head', (x, -8, z), (0, 0, tilt), [(1, h, 1)], 'ice', name=f'cr{k}')
    out += chain('head', (0, -8, -4.0), (0, 0, 0), [(2, 4, 1), (1, 3, 1)], 'ice', name='crm')
    for k, x in enumerate((-3.0, -1.0, 1.5)):
        out.append(block('head', (x, 0, -4.2), (1, 2 + (k % 2), 1), 'icicle', key=f'ic{k % 2}'))
    out.append(block('body', (-5.5, -1.5, -3.0), (11, 3, 6), 'cw_fur', key='mantle'))
    out.append(block('body', (-4.5, 1.0, 2.3), (9, 16, 1), 'cw_frost_cape', key='cape'))
    out += sym(cluster('body', (-5.0, -1.5, 0), (0, 0, -0.45), 'ice', sizes=((2, 4, 2), (1, 3, 1)), name='shc'))
    return out


def swamp_mother(f):
    """Sumpfmutter: moss on hat and shoulders, mushrooms on the brim, two green lanterns."""
    if f != 'witch':
        return []
    out = []
    # moss on the brim (it spans x and z -5..5 at y -10)
    out.append(block('head', (-5.3, -10.8, -5.3), (5, 1, 3), 'cw_spore', key='m1'))
    out.append(block('head', (1.2, -10.8, 2.0), (4, 1, 3), 'cw_spore', key='m2'))
    out.append(block('head', (-5.3, -10.8, 1.0), (2, 1, 4), 'cw_spore', key='m3'))
    for k, (x, z, h) in enumerate(((-5.3, -4.0, 4), (-5.3, 1.5, 6), (4.3, -1.0, 5), (4.3, 3.0, 3), (-2.0, 4.3, 6),
                                   (1.5, 4.3, 4), (3.5, -5.3, 3))):
        out.append(block('head', (x, -9.0, z), (1, h, 1), 'cw_moss_strand', key=f'st{h}'))
    # mushrooms on the brim
    out.append(block('head', (2.0, -11.8, -4.5), (2, 1, 2), 'cw_mushroom', key='cap'))
    out.append(block('head', (2.5, -10.8, -4.0), (1, 1, 1), 'cw_stem', key='cw_stem'))
    out.append(block('head', (-4.0, -11.6, 3.0), (2, 1, 2), 'cw_mushroom', key='cap'))
    out.append(block('head', (-3.5, -10.6, 3.5), (1, 1, 1), 'cw_stem', key='cw_stem'))
    # a lantern hanging from the brim, one at her belt
    out.append(block('head', (-4.6, -9.0, -5.9), (1, 2, 1), 'cw_chain_iron', key='lch'))
    out.append(block('head', (-5.1, -7.0, -6.4), (2, 3, 2), 'cw_swamp_lantern', key='lantern'))
    out.append(block('body', (3.3, 8.0, -3.9), (1, 2, 1), 'cw_chain_iron', key='lch'))
    out.append(block('body', (2.8, 10.0, -4.4), (2, 3, 2), 'cw_swamp_lantern', key='lantern'))
    # the moss mantle with strands down her back
    out.append(block('body', (-5.0, -0.8, -3.6), (10, 2, 7), 'cw_spore', key='mantle'))
    for k, (x, h) in enumerate(((-3.5, 7), (-0.5, 10), (2.5, 6))):
        out.append(block('body', (x, 1.2, 3.2), (1, h, 1), 'cw_moss_strand', key=f'bs{h}'))
    return out


def shadow_warden(f):
    """Schattenwärter: a black helm with a violet visor, spiked pauldrons, a key ring, broken shackles."""
    if f != 'skeleton':
        return []
    out = []
    out.append(block('head', (-4.5, -8.6, -4.5), (9, 9, 9), 'cw_shadow_visor', key='helm'))
    out.append(block('head', (-0.5, -10.6, -3.5), (1, 2, 8), 'cw_dark_plate', key='crest'))
    out += sym([block('body', (-6.8, -1.4, -2.8), (4, 2, 6), 'cw_dark_plate', key='pad')])
    out += sym(chain('body', (-5.6, -1.2, 0), (0, 0, -0.5), [(1, 3, 1), (1, 2, 1)], 'cw_dark_plate', name='padsp'))
    # the key ring at his hip and three keys on it
    out.append(block('body', (-4.2, 10.0, -2.4), (9, 1, 5), 'cw_dark_plate', key='belt'))
    out += ring('body', (-1.5, 11.8, -2.7), 1.2, 8, (1, 1, 1), 'cw_brass', name='keyring')
    for k, (x, h) in enumerate(((-2.0, 3), (-0.6, 4))):
        out.append(block('body', (x - 0.5, 12.8, -2.9), (1, h, 1), 'cw_brass', key=f'key{h}'))
        out.append(block('body', (x, 11.8 + h, -2.9), (1, 1, 1), 'cw_brass', key='bit'))
    # shackles with broken chains on his wrists
    out += sym([block('right_arm', (-1.5, 7.0, -1.5), (3, 2, 3), 'cw_old_iron', key='cuff')])
    out += sym([block('right_arm', (-0.5, 9.0, -0.5), (1, 4, 1), 'cw_chain_iron', key='cuffch')])
    # an amethyst cluster between the shoulder blades
    out += cluster('body', (0, 3.5, 2.2), (-0.9, 0, 0), 'cw_amethyst', sizes=((2, 4, 2), (1, 3, 1), (1, 2, 1)), name='am')
    return out


def ember_lord(f):
    """Glutherr: a great helm with an ember slit and horns, plate with glowing seams, a forge hammer."""
    if f != 'humanoid':
        return []
    out = []
    out.append(block('head', (-4.6, -8.6, -4.6), (9, 9, 9), 'cw_ember_visor', key='helm'))
    out += sym(chain('head', (-4.4, -7.0, -0.5), (-0.1, 0, -1.25), [(2, 2, 2), (1, 3, 1)], 'horn_magma',
                     bend=(-0.25, 0, 0.55), name='horn'))
    out += sym([block('body', (-7.2, -1.8, -3.0), (5, 3, 6), 'cw_ember_plate', key='pad1')])
    out += sym([block('body', (-7.6, 0.8, -3.0), (4, 2, 6), 'cw_ember_plate', key='pad2')])
    out.append(block('body', (-1.0, 3.0, -2.6), (2, 3, 1), 'magma', key='core'))
    out += sym([block('right_arm', (-3.5, 4.5, -2.5), (5, 4, 5), 'cw_ember_plate', key='vamb')])
    # the forge hammer across his back
    out.append(block('body', (-0.5, -9.0, -0.5), (1, 17, 1), 'pole', pivot=(0, 5, 2.8), rot=(0, 0, R(-32)), key='handle'))
    out.append(block('body', (-2.5, 8.0, -1.5), (5, 3, 3), 'cw_hot_iron', pivot=(0, 5, 2.8), rot=(0, 0, R(-32)),
                     key='hhead'))
    return out


def soul_smith(f):
    """Seelenschmied: an anvil on his back with soul fire on it, basalt pauldrons, a crying obsidian heart."""
    if f != 'skeleton':
        return []
    out = []
    # the anvil, its glowing face up behind his shoulders, soul flames rising from it
    out.append(block('body', (-4.5, 0.5, 2.4), (9, 2, 4), 'cw_anvil_top', key='atop'))
    out.append(block('body', (-6.5, 1.0, 3.4), (2, 1, 2), 'cw_anvil_iron', key='ahorn'))
    out.append(block('body', (-1.5, 2.5, 2.9), (3, 3, 3), 'cw_anvil_iron', key='awaist'))
    out.append(block('body', (-3.5, 5.5, 2.4), (7, 2, 4), 'cw_anvil_iron', key='afoot'))
    for k, (x, z, h) in enumerate(((-3.0, 3.6, 3), (0.0, 3.0, 5), (2.5, 4.4, 3), (-1.0, 5.0, 2))):
        out.append(block('body', (x, 0.5 - h, z), (1, h, 1), 'soul', key=f'wisp{h}'))
    out += sym([block('body', (-3.5, -0.3, -2.3), (1, 7, 1), 'cw_leather', key='strap')])
    # basalt pauldrons standing like columns
    out += sym([block('body', (-6.6, -2.4, -2.2), (3, 3, 4), 'cw_basalt_pillar', key='pad')])
    out += sym([block('body', (-6.2, -4.4, -1.0), (2, 2, 2), 'cw_basalt_pillar', key='col')])
    out.append(block('body', (-1.0, 2.0, -2.6), (2, 2, 1), 'cw_crying', key='heart'))
    return out


DESIGNS = {'champion_stone_crypt': grave_warden, 'champion_desert_crypt': mummy_lord,
           'champion_illager_keep': keep_lord, 'champion_frost_keep': frost_lord,
           'champion_poison_swamp': swamp_mother, 'champion_deep_cellar': shadow_warden,
           'champion_nether_forge': ember_lord, 'champion_basalt_forge': soul_smith}

import designs as _designs
_event_design = _designs.design


def _design(event, family):
    if event in DESIGNS:
        return DESIGNS[event](family)
    return _event_design(event, family)


_designs.design = _design


# ---------------------------------------------------------------- the textures

def details(tex, family, ev, seed):
    """The champion's own markings on top of his recoloured skin, and the glow map."""
    glow = Image.new('RGBA', tex.size, (0, 0, 0, 0))
    p = skins.Painter(tex, glow, family, ev, seed)
    pal = PALETTES[ev]
    sides = skins.SIDES
    if ev == 'champion_stone_crypt':
        # a red rune on the skull, blood cracks down the bones
        rect = p.faces['head']['front']
        u, v, w, h = rect
        for dx, dy in ((3, 0), (4, 0), (3, 1), (4, 1), (2, 2), (5, 2)):
            p.put(u + dx, v + dy, hexc('d8301e'), glow=hexc('a8180e'))
        p.lines(['body', 'arm', 'leg'], sides, 1, 6, hexc('7a1410'), glow=hexc('6a0e0a'), k=0.8, dirs=((0, 1),))
    elif ev == 'champion_desert_crypt':
        # dark cracks in the dried skin, gold at the belt, kohl under the eyes
        p.spots(['body', 'leg', 'arm'], sides, 3, hexc('140c06'), k=0.6, size=2)
        p.lines(['head', 'arm'], sides, 1, 5, hexc('2a1a0e'), k=0.7)
        p.edge(['body'], sides, -1, GOLD)
        for x, y in skins.EYES.get(family, []):
            p.put(x, y - 1, hexc('101418'), k=0.9)
    elif ev == 'champion_illager_keep':
        # yellow war paint like a lightning bolt under the eyes, a brass hem on the coat
        for x, y in skins.EYES.get(family, [])[:4:3]:
            for dx, dy in ((0, 1), (1, 2)):
                p.put(x + dx, y + dy, hexc('ffe24a'), glow=hexc('c08a0e') if dy == 1 else None, k=0.85)
        p.edge(['body', 'robe'], sides, -1, hexc('b08a2a'))
        p.edge(['body'], ('front',), 0, hexc('b08a2a'))
        p.lines(['robe'], ('front', 'back'), 1, 7, hexc('d8b02a'), k=0.7, dirs=((1, 1), (-1, 1), (0, 1)))
    elif ev == 'champion_frost_keep':
        p.edge(['head', 'body', 'arm', 'leg'], ('top',), 0, pal['detail'])
        p.edge(['head', 'body', 'arm'], sides, 0, pal['detail'], chance=0.7)
        p.lines(['head', 'body', 'arm', 'leg'], sides, 1, 6, hexc('a8deff'), glow=hexc('3f8fcf'), k=0.9)
    elif ev == 'champion_poison_swamp':
        # moss on the robe, a few glowing spores, a pale mist mark on the chest
        p.spots(['body', 'robe', 'arm'], sides, 3, pal['detail'], k=0.6, size=1)
        p.lines(['robe'], sides, 1, 5, hexc('3c5230'), k=0.6, dirs=((0, 1),))
        p.spots(['robe', 'body'], sides, 2, hexc('d8f0a0'), glow=hexc('8fbf5a'))
        rect = p.faces['body']['front']
        u, v, w, h = rect
        for dx, dy in ((3, 3), (4, 3), (3, 4), (4, 4), (4, 5)):
            p.put(u + dx, v + dy, hexc('e6d8ff'), glow=hexc('9a88c8'))
    elif ev == 'champion_deep_cellar':
        p.lines(['body', 'arm', 'leg'], sides, 1, 6, hexc('8a4ad8'), glow=hexc('5a1ab0'), k=0.8, dirs=((0, 1),))
        p.spots(['body', 'leg'], sides, 3, hexc('050308'), k=0.7, size=2)
    elif ev == 'champion_nether_forge':
        # plate seams that glow like embers: rows at the edges and the middle of every part
        for part in ('body', 'arm', 'leg'):
            p.edge([part], sides, 0, hexc('ff8a1f'), glow=hexc('ff6a0a'), chance=0.45)
        p.edge(['body'], sides, 7, hexc('ffb03a'), glow=hexc('ff7a14'), chance=0.5)
        p.edge(['leg'], sides, 5, hexc('ffb03a'), glow=hexc('ff7a14'), chance=0.35)
        p.lines(['body', 'arm', 'leg'], sides, 1, 3, hexc('ff8a1f'), glow=hexc('ff6a0a'), k=0.9, dirs=((0, 1),))
        p.spots(['body', 'arm', 'leg'], sides, 3, hexc('050404'), k=0.7, size=2)
    elif ev == 'champion_basalt_forge':
        # soul fire behind the ribs, embers on the arms
        rect = p.faces['body']['front']
        u, v, w, h = rect
        for dy in (2, 4, 6):
            for dx in range(1, w - 1):
                if dx != w // 2:
                    p.put(u + dx, v + dy, hexc('62f2ff'), glow=hexc('1ea8cc'), k=0.9)
        p.spots(['arm'], sides, 2, hexc('ff8a2a'), glow=hexc('e8620e'))
    p.eyes()
    return tex, glow


def clothes(img, ev, how, seed):
    """Recoloured clothes, cut to how the champion wears them."""
    tex = skins.recolor(img, CLOTHES[ev][2], ev)
    px = tex.load()
    glow = Image.new('RGBA', tex.size, (0, 0, 0, 0))
    p = skins.Painter(tex, glow, None, ev, seed)
    head = rects(0, 0, 8, 8, 8)
    body = rects(16, 16, 8, 12, 4)
    arm = rects(40, 16, 4, 12, 4)
    leg = rects(0, 16, 4, 12, 4)

    def clear(rect, keep=lambda x, y: False):
        u, v, w, h = rect
        for y in range(v, v + h):
            for x in range(u, u + w):
                if not keep(x - u, y - v):
                    px[x, y] = (0, 0, 0, 0)

    def each(fn):
        for y in range(tex.height):
            for x in range(tex.width):
                if px[x, y][3] > 0:
                    fn(x, y)

    if how == 'mail':
        # rusty chain mail: rings in a checker of light and dark, a coif that leaves the face free
        each(lambda x, y: p.put(x, y, hexc('0e0e10'), k=0.3) if (x + y) % 2 else None)
        p.rng.seed(seed)
        each(lambda x, y: p.put(x, y, hexc('7a3e1e'), k=0.7) if p.rng.random() < 0.18 else None)
        clear(head['front'], keep=lambda x, y: y == 0 or x in (0, 7))
        clear(head['bottom'])
    elif how == 'wraps':
        # linen bandages: rows with gaps, the eyes free
        each(lambda x, y: p.put(x, y, hexc('4a3e2c'), k=0.75) if y % 3 == 2 else p.put(x, y, hexc('efe4c4'), k=0.25))
        for y in range(tex.height):
            for x in range(tex.width):
                if px[x, y][3] > 0 and y % 4 == 3 and (x * 7 + y) % 5 < 2:
                    px[x, y] = (0, 0, 0, 0)
        u, v, w, h = head['front']
        for y in range(v + 3, v + 5):
            for x in range(u, u + w):
                px[x, y] = (0, 0, 0, 0)
    elif how == 'cloak':
        # the stray's rags become a frost cloak with white fur at the top
        for part in (head, body, arm, leg):
            u, v, w, h = part['top']
            for x in range(u, u + w):
                for y in range(v, v + h):
                    if px[x, y][3] > 0:
                        px[x, y] = (238, 244, 248, 255)
        for part in (body, arm):
            for face in ('front', 'back', 'left', 'right'):
                u, v, w, h = part[face]
                for x in range(u, u + w):
                    if px[x, v][3] > 0:
                        px[x, v] = (238, 244, 248, 255)
        each(lambda x, y: p.put(x, y, hexc('a8deff'), k=0.25) if (x * 5 + y * 3) % 11 == 0 else None)
    elif how == 'tabard':
        # a black tabard with a violet hem; the helm covers the head, so the hood goes
        for face in head.values():
            clear(face)
        for face in ('front', 'back', 'left', 'right'):
            u, v, w, h = body[face]
            for x in range(u, u + w):
                if px[x, v + h - 1][3] > 0:
                    px[x, v + h - 1] = (176, 138, 42, 255)
    elif how == 'apron':
        # only a leather apron: the chest and the front of the legs
        keep = {body['front'], leg['front']}
        for part in (head, body, arm, leg):
            for face in part.values():
                if face not in keep:
                    clear(face)
        clear(body['front'], keep=lambda x, y: y >= 7)
        each(lambda x, y: p.put(x, y, hexc('1a0e06'), k=0.6) if (x + 2 * y) % 9 == 0 else None)
    return tex, glow


def build(van, out_root, atlas):
    """Builds every champion folder; returns {folder: shapes}, {folder: textures}, {folder: glows}."""
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
        if ev in CLOTHES:
            path, name, cls, how, swapped = CLOTHES[ev]
            img = Image.open(os.path.join(van, path)).convert('RGBA')
            tex, _ = clothes(img, ev, how, zlib.crc32(f'{ev}/{name}'.encode()))
            tex.save(os.path.join(out, name + '.png'))
            if swapped:
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


# ---------------------------------------------------------------- preview sheets

def scene(ev, folder):
    """The champion's preview scene; returns (scene, index of the first gear quad)."""
    import render
    import atlas
    path, name, fam = CHAMPIONS[ev][0]
    load = lambda n: Image.open(os.path.join(folder, n)).convert('RGBA') if os.path.exists(os.path.join(folder, n)) else None
    skin, glow = load(name + '.png'), load(name + '_glow.png')
    outer = None
    if ev in CLOTHES:
        outer = load(CLOTHES[ev][1] + '.png')
    boxes = DESIGNS[ev](fam)
    gtex = load(f'gear_{fam}.png')
    gglow = load(f'gear_{fam}_glow.png')
    atlas.pack(boxes, gtex.size[0])
    sc = render.mob_scene(fam, skin, glow, outer, (), None, None)
    start = len(sc.quads)
    parts = bones(fam)
    for b in boxes:
        xf = parts[b.bone].then(b.pivot, b.rot)
        sc.cube(gtex, gglow, xf, b.pos, b.size, (b.u, b.v), 0.0, b.mirror)
    return sc, start


def bones(family):
    import render
    from geom import FAMILIES as GF
    fam = GF[family]
    parts = {}
    for name, spec in fam['bones'].items():
        pivot, rot, cubes = spec[:3]
        parent = parts[spec[3]] if len(spec) > 3 else render.Xf()
        parts[name] = parent.then(pivot, fam['pose'].get(name, rot))
    parts.setdefault('hat', parts.get('head'))
    return parts


def draw(sc_start, yaw, pitch, scale, night=False, size=(44, 56), origin=(0.5, 0.45), bias=1.0):
    """Like Scene.render, but gear lying flat on the skin is drawn over it (the painter's sort
    alone mixes two parallel faces 0.5 px apart into a grid; the game has a depth buffer)."""
    import math
    from gear import rot_matrix, mm, mv
    sc, start = sc_start
    view = mm(rot_matrix(math.radians(pitch), 0, 0), rot_matrix(0, math.radians(yaw), 0))
    items = []
    for k, (corners, n, c, g) in enumerate(sc.quads):
        vn = mv(view, n)
        if vn[2] >= -1e-6:
            continue
        pts = [mv(view, p) for p in corners]
        depth = sum(p[2] for p in pts) / 4 - (bias if k >= start else 0.0)
        shade = 0.62 + 0.38 * max(0.0, -vn[2] * 0.6 - vn[1] * 0.5 + 0.15 * vn[0])
        if night:
            shade *= 0.32
        col = [min(255, int(c[i] * shade)) for i in range(3)]
        if g is not None:
            col = [min(255, max(col[i], g[i])) for i in range(3)] if night else \
                  [min(255, int(col[i] * 0.6 + g[i] * 0.5)) for i in range(3)]
        items.append((depth, pts, tuple(col) + (255,)))
    items.sort(key=lambda it: -it[0])
    W, H = int(size[0] * scale), int(size[1] * scale)
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    ox, oy = W * origin[0], H * origin[1]
    for depth, pts, col in items:
        dr.polygon([(ox + p[0] * scale, oy + p[1] * scale) for p in pts], fill=col)
    return img


KW = dict(size=(44, 56), origin=(0.5, 0.45))


def font(size):
    from PIL import ImageFont
    for path in ('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', '/usr/share/fonts/dejavu/DejaVuSans.ttf'):
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def sheet(ev, folder, out, scale=8):
    """One champion: front and back by day, front by night, with his name."""
    sc = scene(ev, folder)
    tiles = [draw(sc, -28, 12, scale, **KW), draw(sc, 152, 12, scale, **KW), draw(sc, -28, 12, scale, night=True, **KW)]
    w = sum(t.width for t in tiles) + 40
    h = tiles[0].height + 70
    img = Image.new('RGBA', (w, h), (34, 31, 39, 255))
    d = ImageDraw.Draw(img)
    name, epithet, where = NAMES[ev]
    d.text((14, 8), f'{name} – {epithet}', fill=(232, 199, 122, 255), font=font(20))
    d.text((14, 34), f'{where}   (Tag vorn / Tag hinten / Nacht)', fill=(200, 194, 184, 255), font=font(14))
    x = 10
    for k, t in enumerate(tiles):
        if k == 2:
            bg = Image.new('RGBA', t.size, (14, 12, 20, 255))
            bg.alpha_composite(t)
            t = bg
        img.alpha_composite(t, (x, 60))
        x += t.width + 10
    img.save(out)


def lineup(folders, out, scale=7):
    """All eight from the front, by day, side by side."""
    tiles = [(ev, draw(scene(ev, folders[ev]), -25, 10, scale, **KW)) for ev in CHAMPIONS]
    w = sum(t.width for _, t in tiles) + 10 * (len(tiles) + 1)
    h = tiles[0][1].height + 20
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    x = 10
    for ev, t in tiles:
        img.alpha_composite(t, (x, 10))
        x += t.width + 10
    img.save(out)


if __name__ == '__main__':
    import atlas
    van, out = sys.argv[1], sys.argv[2]
    tex_root = os.path.join(out, 'textures')
    build(van, tex_root, atlas)
    os.makedirs(os.path.join(out, 'sheets'), exist_ok=True)
    folders = {ev: os.path.join(tex_root, ev) for ev in CHAMPIONS}
    for ev in CHAMPIONS:
        sheet(ev, folders[ev], os.path.join(out, 'sheets', ev + '.png'))
        print('sheet', ev)
    lineup(folders, os.path.join(out, 'sheets', 'lineup.png'))
