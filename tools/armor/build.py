"""
Paints every class outfit into the mod's textures, writes the list of 3D parts
for the Java side and preview sheets.

    python3 tools/armor/build.py [preview_dir] [name ...]
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from paint import Tex, finish  # noqa: E402
from outfits import OUTFITS  # noqa: E402
from outfits3d import DESIGNS  # noqa: E402
import shapes  # noqa: E402
import preview  # noqa: E402
import render3d  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), '..', '..')
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'nightsiege')
LAYER1 = os.path.join(ASSETS, 'textures', 'entity', 'equipment', 'humanoid')
LAYER2 = os.path.join(ASSETS, 'textures', 'entity', 'equipment', 'humanoid_leggings')
EQUIPMENT = os.path.join(ASSETS, 'equipment')
# Outfits that get the final grade (deeper colours, shading, outlines).
GRADED = {'berserker', 'blademaster', 'axe', 'paladin'}
JAVA = os.path.join(ROOT, 'src', 'main', 'java', 'com', 'mki', 'nightsiege', 'client', 'ClassArmorShapes.java')


def main():
    previews = sys.argv[1] if len(sys.argv) > 1 else None
    only = set(sys.argv[2:])
    for d in (LAYER1, LAYER2, EQUIPMENT):
        os.makedirs(d, exist_ok=True)
    all_shapes = {}
    skin = preview.plain_body()
    for name, paint in OUTFITS.items():
        size = (shapes.TEX_W, shapes.TEX_H)
        l1 = Tex(name + ':1', size)
        l2 = Tex(name + ':2', size)
        paint(l1, l2)
        kit = shapes.Kit(l1, l2)
        if name in DESIGNS:
            DESIGNS[name](kit, l1, l2)
        all_shapes[name] = kit.boxes
        if name in GRADED:
            finish(l1)
            finish(l2)
        l1.save(os.path.join(LAYER1, 'class_' + name + '.png'))
        l2.save(os.path.join(LAYER2, 'class_' + name + '.png'))
        with open(os.path.join(EQUIPMENT, 'class_' + name + '.json'), 'w') as f:
            f.write('{\n  "layers": {\n    "humanoid": [{"texture": "nightsiege:class_%s"}],\n'
                    '    "humanoid_leggings": [{"texture": "nightsiege:class_%s"}]\n  }\n}\n' % (name, name))
        if previews and (not only or name in only):
            os.makedirs(previews, exist_ok=True)
            render3d.sheet(name, skin.img, l1.img, l2.img, kit.boxes, os.path.join(previews, name + '_3d.png'))
        print('painted', name, len(kit.boxes), 'boxes')
    with open(JAVA, 'w') as f:
        f.write(shapes.java(all_shapes))


if __name__ == '__main__':
    main()
