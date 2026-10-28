"""
Builds the event monsters: textures with their glow maps, the gear textures and
EventGearShapes.java, and preview sheets.

    python3 build.py <vanilla textures/entity dir> [preview dir] [events...]
"""
import os
import sys
import zlib
from PIL import Image
import atlas
import designs
import skins

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../..')
OUT = os.path.join(ROOT, 'src/main/resources/assets/nightsiege/textures/entity/event')
JAVA = os.path.join(ROOT, 'src/main/java/com/mki/nightsiege/client/EventGearShapes.java')

# event file name: (vanilla path under textures/entity, family for the details, baby, only in these events)
ALL_EVENTS = None
MOBS = {
    'zombie': ('zombie/zombie.png', 'humanoid', False, ALL_EVENTS),
    'zombie_baby': ('zombie/zombie_baby.png', 'humanoid', True, ALL_EVENTS),
    'husk': ('zombie/husk.png', 'humanoid', False, ALL_EVENTS),
    'husk_baby': ('zombie/husk_baby.png', 'humanoid', True, ALL_EVENTS),
    'drowned': ('zombie/drowned.png', 'humanoid', False, ALL_EVENTS),
    'drowned_baby': ('zombie/drowned_baby.png', 'humanoid', True, ALL_EVENTS),
    'drowned_outer_layer': ('zombie/drowned_outer_layer.png', None, False, ALL_EVENTS),
    'drowned_outer_layer_baby': ('zombie/drowned_outer_layer_baby.png', None, True, ALL_EVENTS),
    'skeleton': ('skeleton/skeleton.png', 'skeleton', False, ALL_EVENTS),
    'stray': ('skeleton/stray.png', 'skeleton', False, ALL_EVENTS),
    'stray_overlay': ('skeleton/stray_overlay.png', None, False, ALL_EVENTS),
    'bogged': ('skeleton/bogged.png', 'skeleton', False, ALL_EVENTS),
    'bogged_overlay': ('skeleton/bogged_overlay.png', None, False, ALL_EVENTS),
    'parched': ('skeleton/parched.png', 'skeleton', False, ALL_EVENTS),
    'wither_skeleton': ('skeleton/wither_skeleton.png', 'skeleton', False, ALL_EVENTS),
    'creeper': ('creeper/creeper.png', 'creeper', False, ALL_EVENTS),
    'spider': ('spider/spider.png', 'spider', False, ALL_EVENTS),
    'cave_spider': ('spider/cave_spider.png', 'spider', False, ALL_EVENTS),
    # stage 2: illagers and the witch
    'pillager': ('illager/pillager.png', 'illager', False, ALL_EVENTS),
    'vindicator': ('illager/vindicator.png', 'illager', False, ALL_EVENTS),
    'evoker': ('illager/evoker.png', 'illager', False, ALL_EVENTS),
    'illusioner': ('illager/illusioner.png', 'illager', False, ALL_EVENTS),
    'witch': ('witch/witch.png', 'witch', False, ALL_EVENTS),
    # stage 3: the rest (layouts not painted part by part)
    'ravager': ('illager/ravager.png', 'whole', False, ALL_EVENTS),
    'breeze': ('breeze/breeze.png', 'whole', False, ALL_EVENTS),
    'breeze_wind': ('breeze/breeze_wind.png', None, False, ALL_EVENTS),
    'camel_husk': ('camel/camel_husk.png', 'whole', False, ALL_EVENTS),
    'phantom': ('phantom/phantom.png', 'whole', False, ('eclipse',)),
    'blaze': ('blaze/blaze.png', 'whole', False, ('nether_rift',)),
    'piglin_brute': ('piglin/piglin_brute.png', 'whole', False, ('nether_rift',)),
    'zombified_piglin': ('piglin/zombified_piglin.png', 'whole', False, ('nether_rift',)),
    'zombified_piglin_baby': ('piglin/zombified_piglin_baby.png', 'whole', True, ('nether_rift',)),
    'magmacube': ('slime/magmacube.png', 'whole', False, ('nether_rift',)),
    'hoglin': ('hoglin/hoglin.png', 'whole', False, ('nether_rift',)),
    'hoglin_baby': ('hoglin/hoglin_baby.png', 'whole', True, ('nether_rift',)),
}
# eyes layers recoloured in the event's eye colour
EYES = {'spider_eyes': ('spider/spider_eyes.png', ALL_EVENTS), 'phantom_eyes': ('phantom/phantom_eyes.png', ('eclipse',))}


def build_event(van, ev):
    out = os.path.join(OUT, ev)
    os.makedirs(out, exist_ok=True)
    for old in os.listdir(out):
        if old.endswith('_e.png') or old.endswith('_glow.png'):
            os.remove(os.path.join(out, old))
    textures, glows = [], []
    for name, (path, family, baby, only) in MOBS.items():
        if only and ev not in only:
            continue
        img = Image.open(os.path.join(van, path)).convert('RGBA')
        tex = skins.recolor(img, name, ev)
        if family:
            tex, glow = skins.details(tex, family, ev, seed=zlib.crc32(f'{ev}/{name}'.encode()), baby=baby)
            if glow.getbbox():
                glow.save(os.path.join(out, name + '_glow.png'))
                glows.append(name)
        tex.save(os.path.join(out, name + '.png'))
        textures.append((path, name))
    for name, (path, only) in EYES.items():
        if only and ev not in only:
            continue
        eyes = Image.open(os.path.join(van, path)).convert('RGBA')
        skins.spider_eyes(eyes, ev).save(os.path.join(out, name + '.png'))
        textures.append((path, name))
    for clothes in ('zombie_outer', 'skeleton_outer'):
        if os.path.exists(os.path.join(out, clothes + '.png')):
            pass   # the clothes layers are kept from the first version (thunder recoloured)
    shapes = {}
    for fam in designs.FAMILY_NAMES:
        boxes = designs.design(ev, fam)
        size = atlas.pack(boxes)
        tex, glow = atlas.paint(boxes, size)
        tex.save(os.path.join(out, f'gear_{fam}.png'))
        glow.save(os.path.join(out, f'gear_{fam}_glow.png'))
        shapes[fam] = (size, boxes)
    return shapes, textures, glows


def java(all_shapes, all_textures, all_glows):
    lines = ['package com.mki.nightsiege.client;', '', 'import java.util.List;', 'import java.util.Map;', '',
             '/**', ' * The 3D gear of the event monsters (horns, spikes, halos, rods, crystals). Written by',
             ' * tools/eventmobs/build.py; do not edit by hand.', ' */', 'public final class EventGearShapes {', '',
             '    /** One box on a bone: place, size, texture spot, pivot and turn (like the class outfits). */',
             '    public record Box(String bone, float x, float y, float z, int w, int h, int d, int u, int v, boolean mirror,',
             '                      float px, float py, float pz, float rx, float ry, float rz) {', '    }', '',
             '    /** The gear of one event on one model family, with the size of its texture. */',
             '    public record Set(int width, int height, List<Box> boxes) {', '    }', '',
             '    private EventGearShapes() {', '    }', '',
             '    /** Key: event folder + "/" + family (humanoid, skeleton, creeper, spider, illager, witch). */',
             '    public static final Map<String, Set> SETS = new java.util.HashMap<>();', '',
             '    static {']
    for ev in all_shapes:
        lines.append(f'        {ev}();')
    lines.append('    }')
    f = lambda v: f'{v:.3f}f'
    for ev, shapes in all_shapes.items():
        lines.append('')
        lines.append(f'    private static void {ev}() {{')
        for fam, ((w, h), boxes) in shapes.items():
            rows = []
            for b in boxes:
                rows.append(f'                new Box("{b.bone}", {f(b.pos[0])}, {f(b.pos[1])}, {f(b.pos[2])}, '
                            f'{b.size[0]}, {b.size[1]}, {b.size[2]}, {b.u}, {b.v}, {str(b.mirror).lower()}, '
                            f'{f(b.pivot[0])}, {f(b.pivot[1])}, {f(b.pivot[2])}, {f(b.rot[0])}, {f(b.rot[1])}, {f(b.rot[2])})')
            lines.append(f'        SETS.put("{ev}/{fam}", new Set({w}, {h}, List.of(\n' + ',\n'.join(rows) + ')));')
        lines.append('    }')
    lines.append('')
    lines.append('    /** The textures each event changes: vanilla path (under textures/entity/) = event file name. */')
    lines.append('    public static final Map<String, List<String>> TEXTURES = Map.ofEntries(')
    lines.append(',\n'.join(f'            Map.entry("{ev}", List.of(' + ', '.join(f'"{p}={n}"' for p, n in tx) + '))'
                           for ev, tx in all_textures.items()) + ');')
    lines.append('')
    lines.append('    /** Event textures with a glow map ("<event>/<name>", file <name>_glow.png). */')
    lines.append('    public static final java.util.Set<String> GLOWS = java.util.Set.of(')
    lines.append(',\n'.join('            ' + ', '.join(f'"{ev}/{n}"' for n in gl) for ev, gl in all_glows.items()) + ');')
    lines.append('}')
    with open(JAVA, 'w') as fh:
        fh.write('\n'.join(lines) + '\n')


if __name__ == '__main__':
    van = sys.argv[1]
    prev = sys.argv[2] if len(sys.argv) > 2 else None
    events = sys.argv[3:] or list(designs.EVENTS)
    all_shapes, all_textures, all_glows = {}, {}, {}
    for ev in designs.EVENTS:
        all_shapes[ev], all_textures[ev], all_glows[ev] = build_event(van, ev)
        print('built', ev)
    # the bosses of the boss nights (Simon, 25 October 2026)
    import bosses
    b_shapes, b_textures, b_glows = bosses.build(van, OUT, atlas)
    all_shapes.update(b_shapes)
    all_textures.update(b_textures)
    all_glows.update(b_glows)
    print('built', ', '.join(b_shapes))
    # the dungeon champions (Simon, 29 October 2026)
    import champions
    c_shapes, c_textures, c_glows = champions.build(van, OUT, atlas)
    all_shapes.update(c_shapes)
    all_textures.update(c_textures)
    all_glows.update(c_glows)
    print('built', ', '.join(c_shapes))
    java(all_shapes, all_textures, all_glows)
    if prev:
        import preview
        os.makedirs(prev, exist_ok=True)
        for ev in events:
            preview.event_sheet(ev, os.path.join(prev, f'{ev}.png'), gear_dir=os.path.join(OUT, ev))
            print('sheet', ev)
        folders = {ev: os.path.join(OUT, ev) for ev in champions.CHAMPIONS}
        for ev in champions.CHAMPIONS:
            champions.sheet(ev, folders[ev], os.path.join(prev, f'{ev}.png'))
        champions.lineup(folders, os.path.join(prev, 'champions_lineup.png'))
        print('sheets champions')
