"""
Paints every class outfit into the mod's textures and writes preview sheets.

    python3 tools/armor/build.py [preview_dir] [name ...]
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from paint import Tex  # noqa: E402
from outfits import OUTFITS  # noqa: E402
import preview  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), '..', '..')
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'nightsiege')
LAYER1 = os.path.join(ASSETS, 'textures', 'entity', 'equipment', 'humanoid')
LAYER2 = os.path.join(ASSETS, 'textures', 'entity', 'equipment', 'humanoid_leggings')
EQUIPMENT = os.path.join(ASSETS, 'equipment')


def main():
    previews = sys.argv[1] if len(sys.argv) > 1 else None
    only = set(sys.argv[2:])
    for d in (LAYER1, LAYER2, EQUIPMENT):
        os.makedirs(d, exist_ok=True)
    for name, paint in OUTFITS.items():
        if only and name not in only:
            continue
        l1 = Tex(name + ':1')
        l2 = Tex(name + ':2')
        paint(l1, l2)
        l1.save(os.path.join(LAYER1, 'class_' + name + '.png'))
        l2.save(os.path.join(LAYER2, 'class_' + name + '.png'))
        with open(os.path.join(EQUIPMENT, 'class_' + name + '.json'), 'w') as f:
            f.write('{\n  "layers": {\n    "humanoid": [{"texture": "nightsiege:class_%s"}],\n'
                    '    "humanoid_leggings": [{"texture": "nightsiege:class_%s"}]\n  }\n}\n' % (name, name))
        if previews:
            os.makedirs(previews, exist_ok=True)
            preview.sheet(name, name, l1, l2, os.path.join(previews, name + '.png'))
        print('painted', name)


if __name__ == '__main__':
    main()
