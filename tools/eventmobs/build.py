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

# event file name: (vanilla path under textures/entity, family for the details, baby, glow file)
MOBS = {
    'zombie': ('zombie/zombie.png', 'humanoid', False),
    'zombie_baby': ('zombie/zombie_baby.png', 'humanoid', True),
    'husk': ('zombie/husk.png', 'humanoid', False),
    'husk_baby': ('zombie/husk_baby.png', 'humanoid', True),
    'drowned': ('zombie/drowned.png', 'humanoid', False),
    'drowned_baby': ('zombie/drowned_baby.png', 'humanoid', True),
    'drowned_outer_layer': ('zombie/drowned_outer_layer.png', None, False),
    'drowned_outer_layer_baby': ('zombie/drowned_outer_layer_baby.png', None, True),
    'skeleton': ('skeleton/skeleton.png', 'skeleton', False),
    'stray': ('skeleton/stray.png', 'skeleton', False),
    'stray_overlay': ('skeleton/stray_overlay.png', None, False),
    'bogged': ('skeleton/bogged.png', 'skeleton', False),
    'bogged_overlay': ('skeleton/bogged_overlay.png', None, False),
    'parched': ('skeleton/parched.png', 'skeleton', False),
    'wither_skeleton': ('skeleton/wither_skeleton.png', 'skeleton', False),
    'creeper': ('creeper/creeper.png', 'creeper', False),
    'spider': ('spider/spider.png', 'spider', False),
    'cave_spider': ('spider/cave_spider.png', 'spider', False),
}


def build_event(van, ev):
    out = os.path.join(OUT, ev)
    os.makedirs(out, exist_ok=True)
    for name, (path, family, baby) in MOBS.items():
        img = Image.open(os.path.join(van, path)).convert('RGBA')
        tex = skins.recolor(img, name, ev)
        if family:
            tex, glow = skins.details(tex, family, ev, seed=zlib.crc32(f'{ev}/{name}'.encode()), baby=baby)
            tex.save(os.path.join(out, name + '.png'))
            if glow.getbbox():
                glow.save(os.path.join(out, name + '_e.png'))
        else:
            tex.save(os.path.join(out, name + '.png'))
    eyes = Image.open(os.path.join(van, 'spider/spider_eyes.png')).convert('RGBA')
    skins.spider_eyes(eyes, ev).save(os.path.join(out, 'spider_eyes.png'))
    shapes = {}
    for fam in designs.FAMILY_NAMES:
        boxes = designs.design(ev, fam)
        size = atlas.pack(boxes)
        tex, glow = atlas.paint(boxes, size)
        tex.save(os.path.join(out, f'gear_{fam}.png'))
        glow.save(os.path.join(out, f'gear_{fam}_glow.png'))
        shapes[fam] = (size, boxes)
    return shapes


def java(all_shapes):
    lines = ['package com.mki.nightsiege.client;', '', 'import java.util.List;', 'import java.util.Map;', '',
             '/**', ' * The 3D gear of the event monsters (horns, spikes, halos, rods, crystals). Written by',
             ' * tools/eventmobs/build.py; do not edit by hand.', ' */', 'public final class EventGearShapes {', '',
             '    /** One box on a bone: place, size, texture spot, pivot and turn (like the class outfits). */',
             '    public record Box(String bone, float x, float y, float z, int w, int h, int d, int u, int v, boolean mirror,',
             '                      float px, float py, float pz, float rx, float ry, float rz) {', '    }', '',
             '    /** The gear of one event on one model family, with the size of its texture. */',
             '    public record Set(int width, int height, List<Box> boxes) {', '    }', '',
             '    private EventGearShapes() {', '    }', '',
             '    /** Key: event folder + "/" + family (humanoid, skeleton, creeper, spider). */',
             '    public static final Map<String, Set> SETS = Map.ofEntries(']
    entries = []
    f = lambda v: f'{v:.3f}f'
    for ev, shapes in all_shapes.items():
        for fam, ((w, h), boxes) in shapes.items():
            rows = []
            for b in boxes:
                rows.append(f'                    new Box("{b.bone}", {f(b.pos[0])}, {f(b.pos[1])}, {f(b.pos[2])}, '
                            f'{b.size[0]}, {b.size[1]}, {b.size[2]}, {b.u}, {b.v}, {str(b.mirror).lower()}, '
                            f'{f(b.pivot[0])}, {f(b.pivot[1])}, {f(b.pivot[2])}, {f(b.rot[0])}, {f(b.rot[1])}, {f(b.rot[2])})')
            entries.append(f'            Map.entry("{ev}/{fam}", new Set({w}, {h}, List.of(\n' + ',\n'.join(rows) + ')))')
    lines.append(',\n'.join(entries) + ');')
    lines.append('}')
    with open(JAVA, 'w') as fh:
        fh.write('\n'.join(lines) + '\n')


if __name__ == '__main__':
    van = sys.argv[1]
    prev = sys.argv[2] if len(sys.argv) > 2 else None
    events = sys.argv[3:] or list(designs.EVENTS)
    all_shapes = {}
    for ev in designs.EVENTS:
        all_shapes[ev] = build_event(van, ev)
        print('built', ev)
    java(all_shapes)
    if prev:
        import preview
        os.makedirs(prev, exist_ok=True)
        for ev in events:
            preview.event_sheet(ev, os.path.join(prev, f'{ev}.png'), gear_dir=os.path.join(OUT, ev))
            print('sheet', ev)
