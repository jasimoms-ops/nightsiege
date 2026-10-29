"""
The dungeon guards (Simon, 29 October 2026, 22:06: "baue für die Dungeons auf den Wächtern
jeweils basierende Wachen, die die Standard-Mobs ersetzen"). Every monster of a dungeon's
halls and arena waves wears its warden's look in small: the warden's colours and markings on
its own skin, and a lighter piece of his gear (a circlet instead of the crown, pauldrons
without the gravestone ...). One folder "guard_<dungeon>" per dungeon with a texture for every
kind of monster in it; the mod gives it like a foe look (BossService.foeLook).

    guard_stone_crypt    Grabwachen       rusty circlet, rusty pauldrons, mail coif
    guard_desert_crypt   Mumienwachen     gold and lapis band, lappets, linen wraps
    guard_illager_keep   Festungswachen   steel helmet, copper pauldrons
    guard_frost_keep     Frostwachen      small icicle crown, fur mantle, frost cloak
    guard_poison_swamp   Sumpfwachen      moss mantle and strands, moss cap
    guard_deep_cellar    Kerkerwachen     black helm with a violet visor, black tabard
    guard_nether_forge   Glutwachen       ember pauldrons with glowing seams, small horns
    guard_basalt_forge   Seelenwachen     basalt pauldrons, leather apron

    python3 guards.py <vanilla textures/entity dir> <out dir>     (textures and preview sheets)
"""
import os
import sys
import zlib
from PIL import Image
import skins
import champions
from champions import PALETTES, CLOTHES, details, clothes
from gear import block, chain, sym, cluster

# the monsters of each dungeon (ExpeditionKind.mobs) that the generator can paint:
# (vanilla path under textures/entity, our file name, family for the gear and markings)
ZOMBIE = ('zombie/zombie.png', 'zombie', 'humanoid')
HUSK = ('zombie/husk.png', 'husk', 'humanoid')
SKELETON = ('skeleton/skeleton.png', 'skeleton', 'skeleton')
STRAY = ('skeleton/stray.png', 'stray', 'skeleton')
BOGGED = ('skeleton/bogged.png', 'bogged', 'skeleton')
WITHER = ('skeleton/wither_skeleton.png', 'wither_skeleton', 'skeleton')
PILLAGER = ('illager/pillager.png', 'pillager', 'illager')
VINDICATOR = ('illager/vindicator.png', 'vindicator', 'illager')
WITCH = ('witch/witch.png', 'witch', 'witch')
SPIDER = ('spider/spider.png', 'spider', 'spider')
CAVE_SPIDER = ('spider/cave_spider.png', 'cave_spider', 'spider')
BLAZE = ('blaze/blaze.png', 'blaze', 'whole')

GUARDS = {
    'guard_stone_crypt': ('champion_stone_crypt', [ZOMBIE, SKELETON]),
    'guard_desert_crypt': ('champion_desert_crypt', [HUSK, SKELETON]),
    'guard_illager_keep': ('champion_illager_keep', [VINDICATOR, PILLAGER]),
    'guard_frost_keep': ('champion_frost_keep', [STRAY, PILLAGER, VINDICATOR]),
    'guard_poison_swamp': ('champion_poison_swamp', [WITCH, BOGGED, SPIDER, CAVE_SPIDER]),
    'guard_deep_cellar': ('champion_deep_cellar', [SPIDER, CAVE_SPIDER, SKELETON]),
    'guard_nether_forge': ('champion_nether_forge', [BLAZE, WITHER]),
    'guard_basalt_forge': ('champion_basalt_forge', [WITHER, BLAZE, SKELETON]),
}
NAMES = {
    'guard_stone_crypt': ('Grabwachen', 'nach dem Grabwächter'),
    'guard_desert_crypt': ('Mumienwachen', 'nach dem Mumienfürsten'),
    'guard_illager_keep': ('Festungswachen', 'nach dem Festungsherrn'),
    'guard_frost_keep': ('Frostwachen', 'nach dem Frostherrn'),
    'guard_poison_swamp': ('Sumpfwachen', 'nach der Sumpfmutter'),
    'guard_deep_cellar': ('Kerkerwachen', 'nach dem Schattenwärter'),
    'guard_nether_forge': ('Glutwachen', 'nach dem Glutherrn'),
    'guard_basalt_forge': ('Seelenwachen', 'nach dem Seelenschmied'),
}

# the guards' colours: their warden's, the forge's skeletons charred dark instead of pale bone
# blazes (painted with the 'creeper' stops) burn in the forge's colour; the spiders take the dungeon's
OVERRIDES = {'guard_nether_forge': dict(bone=skins.stops('0a0808', '1e1814', '3a2c22', '5a4434'),
                                        creeper=skins.stops('1a0a04', '5a2006', 'c8560e', 'ffb03a')),
             'guard_basalt_forge': dict(creeper=skins.stops('0a1416', '1e3a3e', '3e8a8e', '8ef2ff')),
             'guard_poison_swamp': dict(spider=skins.stops('0a1408', '1c2e14', '34502a', '5a7a44')),
             'guard_deep_cellar': dict(spider=skins.stops('08060e', '1a1228', '2e2046', '4a3670')),
             # the crypt's zombies: grave-grey, not pale
             'guard_stone_crypt': dict(skin=skins.stops('161414', '2e2828', '4e4444', '6e6260'))}
for _g, (_c, _m) in GUARDS.items():
    skins.EVENTS[_g] = dict(PALETTES[_c], **OVERRIDES.get(_g, {}))


def _hy(f):
    """Illager and witch heads are 2 higher (10 instead of 8)."""
    return -2.0 if f in ('illager', 'witch') else 0.0


def _bz(f):
    """Illager and witch bodies are 6 deep instead of 4."""
    return 1 if f in ("illager", "witch") else 0


# ---------------------------------------------------------------- the lighter gear

def grave_guard(f):
    if f not in ('humanoid', 'skeleton'):
        return []
    out = [block('head', (-4.6, -8.6, -4.6), (9, 1, 1), 'cw_rust_iron', key='band_f'),
           block('head', (-4.6, -8.6, 3.6), (9, 1, 1), 'cw_rust_iron', key='band_f')]
    out += sym([block('head', (-4.6, -8.6, -3.6), (1, 1, 7), 'cw_rust_iron', key='band_s')])
    out.append(block('head', (-0.5, -10.6, -4.6), (1, 2, 1), 'cw_rust_iron', key='pt2'))
    out += sym([block('body', (-6.2, -1.0, -2.5), (2, 2, 5), 'cw_rust_iron', key='pad')])
    return out


def mummy_guard(f):
    if f not in ('humanoid', 'skeleton'):
        return []
    out = [block('head', (-4.6, -9.1, -4.6), (9, 1, 1), 'cw_gold_lapis', key='band_f'),
           block('head', (-4.6, -9.1, 3.6), (9, 1, 1), 'cw_gold_lapis', key='band_f')]
    out += sym([block('head', (-4.6, -9.1, -3.6), (1, 1, 7), 'cw_gold_lapis', key='band_s')])
    out += sym([block('head', (-5.1, -7.6, -3.0), (1, 6, 2), 'cw_nemes', key='lap')])
    out.append(block('body', (-4.5, -0.4, -2.7), (9, 1, 5), 'cw_collar', key='collar'))
    return out


def keep_guard(f):
    if f != 'illager':
        return []
    out = [block('head', (-4.6, -10.6, -4.6), (9, 3, 9), 'cw_keep_steel', key='helm'),
           block('head', (-1.0, -11.6, -1.0), (2, 1, 2), 'copper', key='knob')]
    out += sym([block('body', (-6.4, -0.8, -3.4), (3, 2, 7), 'copper', key='pad')])
    return out


def frost_guard(f):
    if f not in ('skeleton', 'illager'):
        return []
    hy, bz = _hy(f), _bz(f)
    out = []
    for k, (x, z, h) in enumerate(((-3.0, -3.6, 2), (-1.0, -3.8, 3), (1.0, -3.8, 3), (3.0, -3.6, 2))):
        out += chain('head', (x, -8 + hy, z), (0, 0, x * 0.06), [(1, h, 1)], 'ice', name=f'cr{h}')
    out.append(block('body', (-5.0, -1.2, -2.6 - bz), (10, 2, 5 + 2 * bz), 'cw_fur', key='mantle'))
    return out


def swamp_guard(f):
    if f not in ('witch', 'skeleton'):
        return []
    hy, bz = _hy(f), _bz(f)
    out = [block('body', (-4.8, -0.8, -2.6 - bz), (10, 2, 5 + 2 * bz), 'cw_spore', key='mantle')]
    for k, (x, h) in enumerate(((-3.5, 5), (0.0, 7), (2.5, 4))):
        out.append(block('body', (x, 1.2, 2.2 + bz), (1, h, 1), 'cw_moss_strand', key=f'bs{h}'))
    if f == 'skeleton':
        out.append(block('head', (-4.6, -8.8, -4.6), (9, 1, 9), 'cw_spore', key='cap'))
        out += sym([block('head', (-4.6, -7.8, -2.0), (1, 3, 1), 'cw_moss_strand', key='st3')])
    else:
        out.append(block('head', (-5.3, -10.8, -5.3), (4, 1, 3), 'cw_spore', key='m1'))
        out.append(block('head', (2.0, -11.8, -4.5), (2, 1, 2), 'cw_mushroom', key='capm'))
        out.append(block('head', (2.5, -10.8, -4.0), (1, 1, 1), 'cw_stem', key='stem'))
    return out


def cellar_guard(f):
    if f != 'skeleton':
        return []
    out = [block('head', (-4.5, -8.6, -4.5), (9, 9, 9), 'cw_shadow_visor', key='helm')]
    out += sym([block('body', (-6.4, -1.2, -2.6), (3, 2, 5), 'cw_dark_plate', key='pad')])
    out += sym([block('right_arm', (-1.5, 7.0, -1.5), (3, 2, 3), 'cw_old_iron', key='cuff')])
    return out


def ember_guard(f):
    if f != 'skeleton':
        return []
    out = sym([block('body', (-6.6, -1.6, -2.8), (4, 3, 6), 'cw_ember_plate', key='pad1')])
    out += sym(chain('head', (-4.2, -7.0, -0.5), (-0.1, 0, -1.25), [(1, 2, 1), (1, 2, 1)], 'horn_magma',
                     bend=(-0.25, 0, 0.55), name='horn'))
    out.append(block('body', (-1.0, 3.0, -2.6), (2, 2, 1), 'magma', key='core'))
    return out


def soul_guard(f):
    if f != 'skeleton':
        return []
    out = sym([block('body', (-6.4, -2.0, -2.2), (3, 3, 4), 'cw_basalt_pillar', key='pad')])
    out += sym([block('body', (-3.5, -0.3, -2.3), (1, 7, 1), 'cw_leather', key='strap')])
    out.append(block('body', (-1.0, 2.0, -2.6), (2, 2, 1), 'cw_crying', key='heart'))
    return out


DESIGNS = {'guard_stone_crypt': grave_guard, 'guard_desert_crypt': mummy_guard, 'guard_illager_keep': keep_guard,
           'guard_frost_keep': frost_guard, 'guard_poison_swamp': swamp_guard, 'guard_deep_cellar': cellar_guard,
           'guard_nether_forge': ember_guard, 'guard_basalt_forge': soul_guard}


# ---------------------------------------------------------------- build

def build(van, out_root, atlas):
    """Builds every guard folder; returns {folder: shapes}, {folder: textures}, {folder: glows}."""
    all_shapes, all_textures, all_glows = {}, {}, {}
    for g, (champ, mobs) in GUARDS.items():
        out = os.path.join(out_root, g)
        os.makedirs(out, exist_ok=True)
        textures, glows = [], []
        families = []
        for path, name, family in mobs:
            img = Image.open(os.path.join(van, path)).convert('RGBA')
            tex = skins.recolor(img, name, g)
            if family in ('humanoid', 'skeleton', 'illager', 'witch'):
                tex, glow = details(tex, family, champ, seed=zlib.crc32(f'{g}/{name}'.encode()))
                if glow.getbbox():
                    glow.save(os.path.join(out, name + '_glow.png'))
                    glows.append(name)
            tex.save(os.path.join(out, name + '.png'))
            textures.append((path, name))
            if family not in families:
                families.append(family)
            if name in ('spider', 'cave_spider') and ('spider/spider_eyes.png', 'spider_eyes') not in textures:
                eyes = Image.open(os.path.join(van, 'spider/spider_eyes.png')).convert('RGBA')
                skins.spider_eyes(eyes, champ).save(os.path.join(out, 'spider_eyes.png'))
                textures.append(('spider/spider_eyes.png', 'spider_eyes'))
            if name == 'bogged':
                ov = Image.open(os.path.join(van, 'skeleton/bogged_overlay.png')).convert('RGBA')
                skins.recolor(ov, 'bogged_overlay', champ).save(os.path.join(out, 'bogged_overlay.png'))
                textures.append(('skeleton/bogged_overlay.png', 'bogged_overlay'))
        # the warden's clothes where the guards wear the same kind (the frost cloak is the stray's own layer)
        if champ in CLOTHES:
            path, name, cls, how, swapped = CLOTHES[champ]
            fam = 'humanoid' if name == 'zombie_outer' else 'skeleton'
            if fam in families or (swapped and any(m[1] == 'stray' for m in mobs)):
                img = Image.open(os.path.join(van, path)).convert('RGBA')
                tex, _ = clothes(img, champ, how, zlib.crc32(f'{g}/{name}'.encode()))
                tex.save(os.path.join(out, name + '.png'))
                if swapped:
                    textures.append((path, name))
        shapes = {}
        for fam in families:
            boxes = DESIGNS[g](fam)
            if not boxes:
                continue
            size = atlas.pack(boxes)
            gtex, gglow = atlas.paint(boxes, size)
            gtex.save(os.path.join(out, f'gear_{fam}.png'))
            gglow.save(os.path.join(out, f'gear_{fam}_glow.png'))
            shapes[fam] = (size, boxes)
        all_shapes[g], all_textures[g], all_glows[g] = shapes, textures, glows
    return all_shapes, all_textures, all_glows


def clothed():
    """Guard folders with a zombie_outer or skeleton_outer layer (for EventTextures.BOSS_CLOTHED)."""
    out = []
    for g, (champ, mobs) in GUARDS.items():
        if champ in CLOTHES and not CLOTHES[champ][4]:
            out.append(g)
    return out


# ---------------------------------------------------------------- previews

PAINTED = ('humanoid', 'skeleton', 'illager', 'witch')


def scene(g, folder, name, fam):
    """One guard's preview scene (like champions.scene)."""
    import render
    import atlas
    champ = GUARDS[g][0]
    load = lambda n: Image.open(os.path.join(folder, n)).convert('RGBA') if os.path.exists(os.path.join(folder, n)) else None
    skin, glow = load(name + '.png'), load(name + '_glow.png')
    outer = None
    if name == 'stray':
        outer = load('stray_overlay.png')
    elif champ in CLOTHES:
        wanted = 'zombie_outer' if fam == 'humanoid' else 'skeleton_outer' if fam == 'skeleton' else None
        if wanted and CLOTHES[champ][1] == wanted:
            outer = load(wanted + '.png')
    boxes = DESIGNS[g](fam)
    sc = render.mob_scene(fam, skin, glow, outer, (), None, None)
    start = len(sc.quads)
    if boxes:
        gtex = load(f'gear_{fam}.png')
        gglow = load(f'gear_{fam}_glow.png')
        atlas.pack(boxes, gtex.size[0])
        parts = champions.bones(fam)
        for b in boxes:
            xf = parts[b.bone].then(b.pivot, b.rot)
            sc.cube(gtex, gglow, xf, b.pos, b.size, (b.u, b.v), 0.0, b.mirror)
    return sc, start


def sheet(g, folder, champ_folder, out, scale=6):
    """The warden and his guards: the warden, every guard from the front and from behind, one by night."""
    from PIL import ImageDraw
    champ, mobs = GUARDS[g]
    kw = champions.KW
    tiles = [champions.draw(champions.scene(champ, champ_folder), -25, 10, scale, **kw)]
    for path, name, fam in mobs:
        if fam not in PAINTED:
            continue
        sc = scene(g, folder, name, fam)
        tiles.append(champions.draw(sc, -25, 10, scale, **kw))
        tiles.append(champions.draw(sc, 155, 10, scale, **kw))
    sc = None
    for path, name, fam in mobs:
        if fam in PAINTED:
            sc = scene(g, folder, name, fam)
            break
    night = champions.draw(sc, -25, 10, scale, night=True, **kw)
    bg = Image.new('RGBA', night.size, (14, 12, 20, 255))
    bg.alpha_composite(night)
    tiles.append(bg)
    w = sum(t.width for t in tiles) + 10 * (len(tiles) + 1)
    h = tiles[0].height + 56
    img = Image.new('RGBA', (w, h), (34, 31, 39, 255))
    d = ImageDraw.Draw(img)
    cname = champions.NAMES[champ][0]
    names = ', '.join(n for _, n, f in mobs)
    d.text((12, 8), f'{NAMES[g][0]} – {NAMES[g][1]}', fill=(232, 199, 122, 255), font=champions.font(18))
    d.text((12, 30), f'links {cname}, dann die Wachen vorn/hinten, rechts bei Nacht · Arten: {names}',
           fill=(200, 194, 184, 255), font=champions.font(13))
    x = 10
    for t in tiles:
        img.alpha_composite(t, (x, 50))
        x += t.width + 10
    img.save(out)
    return img


def all_sheets(out_root, prev):
    """A sheet per dungeon and all eight under each other."""
    rows = []
    for g in GUARDS:
        rows.append(sheet(g, os.path.join(out_root, g), os.path.join(out_root, GUARDS[g][0]),
                          os.path.join(prev, f'{g}.png')))
    w = max(r.width for r in rows)
    h = sum(r.height for r in rows)
    img = Image.new('RGBA', (w, h), (34, 31, 39, 255))
    y = 0
    for r in rows:
        img.alpha_composite(r, (0, y))
        y += r.height
    img.save(os.path.join(prev, 'guards_all.png'))


if __name__ == '__main__':
    import atlas
    van, out_root = sys.argv[1], sys.argv[2]
    champions.build(van, out_root, atlas)
    s, t, gl = build(van, out_root, atlas)
    for g in s:
        print(g, list(s[g]), [n for _, n in t[g]])
    if len(sys.argv) > 3:
        os.makedirs(sys.argv[3], exist_ok=True)
        all_sheets(out_root, sys.argv[3])
