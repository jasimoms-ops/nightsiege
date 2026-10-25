"""Preview sheets: every family of one event, from the front and the back by day, and by night."""
import os
import sys
from PIL import Image, ImageDraw
import designs
import atlas
import render

RES = os.path.join(os.path.dirname(__file__), '../../src/main/resources/assets/nightsiege/textures/entity/event')
VAN = os.environ.get('VANILLA', '')

SKINS = {'humanoid': ('zombie', 'zombie_outer'), 'skeleton': ('skeleton', 'skeleton_outer'),
         'creeper': ('creeper', None), 'spider': ('spider', None)}


def load(path):
    return Image.open(path).convert('RGBA') if path and os.path.exists(path) else None


def event_sheet(event, out, tex_dir=None, gear_dir=None, scale=7):
    tex_dir = tex_dir or os.path.join(RES, event)
    tiles = []
    for fam in designs.FAMILY_NAMES:
        base, outer = SKINS[fam]
        skin = load(os.path.join(tex_dir, base + '.png'))
        glow = load(os.path.join(tex_dir, base + '_e.png'))
        if fam == 'spider':
            eyes = load(os.path.join(tex_dir, 'spider_eyes.png'))
            if eyes is not None:
                glow = eyes if glow is None else Image.alpha_composite(glow, eyes)
                skin = Image.alpha_composite(skin, eyes)
        clothes = load(os.path.join(tex_dir, outer + '.png')) if outer else None
        boxes = designs.design(event, fam)
        if gear_dir:
            gtex = load(os.path.join(gear_dir, f'gear_{fam}.png'))
            gglow = load(os.path.join(gear_dir, f'gear_{fam}_glow.png'))
            size = gtex.size
            atlas.pack(boxes, size[0])
        else:
            size = atlas.pack(boxes)
            gtex, gglow = atlas.paint(boxes, size)
        sc = render.mob_scene(fam, skin, glow, clothes, boxes, gtex, gglow)
        big = fam == 'spider'
        kw = dict(size=(44, 30), origin=(0.5, 0.05)) if big else dict(size=(34, 40), origin=(0.5, 0.33))
        if fam == 'creeper':
            kw = dict(size=(30, 34), origin=(0.5, 0.42))
        tiles.append((fam, [sc.render(-30, 14, scale, **kw), sc.render(150, 14, scale, **kw),
                            sc.render(-30, 14, scale, night=True, **kw)]))
    w = max(sum(t.width for t in ims) + 15 * 4 for _, ims in tiles)
    h = sum(max(t.height for t in ims) + 22 for _, ims in tiles) + 30
    img = Image.new('RGBA', (w, h), (58, 62, 72, 255))
    d = ImageDraw.Draw(img)
    d.text((12, 8), f'{event}  (Tag vorn / Tag hinten / Nacht)', fill=(240, 230, 210, 255))
    y = 30
    for fam, ims in tiles:
        x = 12
        d.text((x, y), fam, fill=(200, 200, 210, 255))
        for k, t in enumerate(ims):
            if k == 2:
                bg = Image.new('RGBA', t.size, (14, 16, 26, 255))
                bg.alpha_composite(t)
                t = bg
            img.alpha_composite(t, (x, y + 14))
            x += t.width + 15
        y += max(t.height for t in ims) + 22
    img.save(out)


if __name__ == '__main__':
    out_dir = sys.argv[1]
    os.makedirs(out_dir, exist_ok=True)
    for ev in (sys.argv[2:] or designs.EVENTS):
        event_sheet(ev, os.path.join(out_dir, f'{ev}.png'))
        print('sheet', ev)
