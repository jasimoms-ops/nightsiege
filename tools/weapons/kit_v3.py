"""
Kit weapons in 32x32 (Simon, 27 October 2026: "yes please, 32x32" - the outfits are drawn
at twice the vanilla resolution, 128x64, so the weapons get the same pixel size).

The designs are those of kit_v2.py; each picture is drawn there at 16x16 without its outline,
doubled with Scale2x (smooth diagonals instead of stairs), then finished at 32x32: a bevel
(lit top left edges, shaded bottom right edges), a fine material grain like the outfits, small
glints, and a tinted one-pixel outline.

python3 tools/weapons/kit_v3.py [--install]
"""
import colorsys
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import kit_v2 as V  # noqa: E402

D = V.D
N = 32
OUT = os.path.join(os.path.dirname(__file__), 'out32')


def scale2x(px):
    """EPX/Scale2x of a 16x16 dict picture -> 32x32 dict."""
    def g(x, y):
        return px.get((x, y)) if 0 <= x < 16 and 0 <= y < 16 else None
    out = {}
    for y in range(16):
        for x in range(16):
            p = g(x, y)
            a, b, c, d = g(x, y - 1), g(x + 1, y), g(x - 1, y), g(x, y + 1)
            e0 = a if (c == a and c != d and a != b) else p
            e1 = b if (a == b and a != c and b != d) else p
            e2 = c if (d == c and d != b and c != a) else p
            e3 = d if (b == d and b != a and d != c) else p
            for (dx, dy, e) in ((0, 0, e0), (1, 0, e1), (0, 1, e2), (1, 1, e3)):
                if e is not None:
                    out[(2 * x + dx, 2 * y + dy)] = e
    return out


def hsv(c):
    return colorsys.rgb_to_hsv(c[0] / 255, c[1] / 255, c[2] / 255)


def glowing(c):
    h, s, v = hsv(c)
    return s > 0.45 and v > 0.8


def shade(c, f):
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))), max(0, min(255, int(c[2] * f))), 255)


def lighten(c, t):
    return (int(c[0] + (255 - c[0]) * t), int(c[1] + (255 - c[1]) * t), int(c[2] + (255 - c[2]) * t), 255)


def noise(x, y, seed):
    v = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xffffffff
    v = (v ^ (v >> 13)) * 1274126177 & 0xffffffff
    return ((v ^ (v >> 16)) & 0xffff) / 65535.0


def kind(c):
    h, s, v = hsv(c)
    hd = h * 360
    if s > 0.45 and v > 0.8:
        return 'glow'
    if v < 0.16:
        return 'dark'
    if s < 0.2:
        return 'metal'
    if 35 <= hd <= 58 and s > 0.45 and v > 0.45:
        return 'gold'
    if 12 <= hd <= 42 and 0.3 <= s <= 0.85 and v <= 0.62:
        return 'wood'
    return 'cloth'


def lum(c):
    return (0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2]) / 255


def finish(px, seed):
    """Material-aware finish at 32x32: lit and shaded edges (also between the parts), grain,
    glints, then the tinted outline."""
    kinds = {p: kind(c) for p, c in px.items()}

    def apart(p, q):
        """True if q is outside the weapon or another part than p."""
        if q not in px:
            return True
        return kinds[q] != kinds[p] or abs(lum(px[q]) - lum(px[p])) > 0.2

    out = {}
    for (x, y), c in px.items():
        k = kinds[(x, y)]
        p = (x, y)
        lit = sum(apart(p, q) for q in ((x - 1, y), (x, y - 1), (x - 1, y - 1)))
        shd = sum(apart(p, q) for q in ((x + 1, y), (x, y + 1), (x + 1, y + 1)))
        col = c
        if k == 'glow':
            col = lighten(c, 0.45) if lit >= 2 else c
        else:
            strength = {'metal': 0.34, 'gold': 0.3, 'wood': 0.16, 'cloth': 0.18, 'dark': 0.22}[k]
            if lit >= 2 and shd == 0:
                col = lighten(c, strength)
            elif lit >= 1 and shd == 0:
                col = lighten(c, strength * 0.45)
            elif shd >= 2 and lit == 0:
                col = shade(c, 0.72)
            elif shd >= 1 and lit == 0:
                col = shade(c, 0.86)
            n = noise(x, y, seed)
            if k == 'metal':
                col = shade(col, 0.96 + 0.08 * n)
            elif k == 'wood':
                if (x + y + (1 if n > 0.7 else 0)) % 3 == 0:
                    col = shade(col, 0.84)          # grain along the shaft
                col = shade(col, 0.95 + 0.1 * n)
            elif k == 'gold':
                if n > 0.93:
                    col = lighten(col, 0.45)        # a sparkle
            else:
                col = shade(col, 0.94 + 0.12 * n)   # cloth and leather grain like the outfits
        out[p] = col
    # glints on the light metal edges
    for (x, y), c in px.items():
        h, s_, v = hsv(c)
        if kinds[(x, y)] == 'metal' and v > 0.75 and (x - 1, y - 1) not in px and noise(x, y, seed + 7) < 0.2:
            out[(x, y)] = lighten(c, 0.7)
    rim = {}
    for (x, y), c in out.items():
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y + dy)
            if 0 <= q[0] < N and 0 <= q[1] < N and q not in out:
                rim.setdefault(q, []).append(c)
    for q, cs in rim.items():
        r = sum(c[0] for c in cs) / len(cs)
        g = sum(c[1] for c in cs) / len(cs)
        b = sum(c[2] for c in cs) / len(cs)
        out[q] = (int(r * 0.32), int(g * 0.32), int(b * 0.32), 255)
    return out


def ridge(px):
    """Sword blades get a ridge along the middle: the lit half a shade lighter, a fine dark line
    on the shadow half (only possible at 32x32)."""
    out = dict(px)
    for (x, y), c in px.items():
        a, d = x - y, x + y - 30
        if a < -8 or kind(c) != 'metal':
            continue
        if d in (-1, 0):
            out[(x, y)] = lighten(c, 0.18)
        elif d == 1:
            out[(x, y)] = shade(c, 0.8)
    return out


def render(fn, seed, sword=False):
    """One picture of a design at 32x32."""
    saved = V.outline
    V.outline = lambda *a, **k: None
    try:
        s = D.Sprite()
        fn(s)
    finally:
        V.outline = saved
    big = scale2x(s.px)
    if sword:
        big = ridge(big)
    return finish(big, seed)


def image(px, scale=1):
    from PIL import Image
    im = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    for (x, y), c in px.items():
        im.putpixel((x, y), c)
    if scale != 1:
        im = im.resize((N * scale, N * scale), Image.NEAREST)
    return im


def iso(px, cube=5):
    """The item as it lies in the hand: every pixel a little block, one pixel deep."""
    from PIL import Image, ImageDraw
    w = N * cube * 2 + 8 * cube
    im = Image.new('RGBA', (w, int(w * 0.8)), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    ang = math.radians(30)
    cx, cy = im.width / 2, im.height * 0.18

    def proj(x, y, z):
        px_ = x * cube * math.cos(ang) + z * cube * 0.9
        py_ = y * cube * 0.82 + x * cube * math.sin(ang) * 0.45 - z * cube * 0.45
        return (cx + px_ - N * cube * 0.45, cy + py_)
    for (x, y), c in sorted(px.items(), key=lambda kv: kv[0][0] - kv[0][1]):
        top = lighten(c, 0.12)
        side = shade(c, 0.62)
        if (x + 1, y) not in px:
            d.polygon([proj(x + 1, y, 0), proj(x + 1, y, 1), proj(x + 1, y + 1, 1), proj(x + 1, y + 1, 0)], fill=side)
        if (x, y - 1) not in px:
            d.polygon([proj(x, y, 1), proj(x + 1, y, 1), proj(x + 1, y, 0), proj(x, y, 0)], fill=top)
        d.polygon([proj(x, y, 1), proj(x + 1, y, 1), proj(x + 1, y + 1, 1), proj(x, y + 1, 1)], fill=c)
    return im.crop(im.getbbox()) if im.getbbox() else im


STATE_FILES = {'standby': '', 'pull0': '_pulling_0', 'pull1': '_pulling_1', 'pull2': '_pulling_2',
               'arrow': '_arrow', 'firework': '_firework'}


def states_of(key):
    kind = D.KINDS.get(key, 'sword')
    return kind, (D.BOW_STATES if kind == 'bow' else D.CROSSBOW_STATES if kind == 'crossbow' else ['standby'])


def all_pictures():
    """key -> {state: 32x32 dict}"""
    pics = {}
    for i, (key, clazz, sub, name, line, fn) in enumerate(D.DESIGNS):
        kind, states = states_of(key)
        pics[key] = {}
        for st in states:
            D.STATE = st
            pics[key][st] = render(fn, i * 31 + 5, sword=kind == 'sword')
        D.STATE = 'standby'
    return pics


def install(root, pics):
    tex = os.path.join(root, 'src/main/resources/assets/nightsiege/textures/item/kit')
    os.makedirs(tex, exist_ok=True)
    for key, states in pics.items():
        kind, _ = states_of(key)
        for st, px in states.items():
            name = key + ('_standby' if kind == 'crossbow' and st == 'standby' else STATE_FILES[st])
            im = image(px)
            if kind in ('bow', 'crossbow'):
                # Minecraft holds bows and crossbows with the vanilla layout (front / upper limb to the
                # upper left); the designs point to the upper right, so the hand showed them backwards
                # (Simon, 27 October 2026: "die Armbrüste werden falsch dargestellt").
                from PIL import Image
                im = im.transpose(Image.FLIP_LEFT_RIGHT)
            im.save(os.path.join(tex, name + '.png'))


def sheet(path, pics):
    from PIL import Image, ImageDraw, ImageFont
    font_b = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 22)
    font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 15)
    font_s = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 15)
    classes = ['Krieger', 'Bogenschütze', 'Wächter', 'Techniker', 'Beschwörer']
    cw, ch, pad = 430, 380, 30
    im = Image.new('RGBA', (pad * 2 + cw * 4, 90 + len(classes) * (ch + 40)), (18, 16, 25, 255))
    d = ImageDraw.Draw(im)
    d.text((pad, 24), 'Night Siege – Kit-Waffen je Unterklasse (32×32, Pixelgröße wie die Rüstungen)',
           fill=(255, 216, 107), font=font_b)
    y = 80
    for clazz in classes:
        d.text((pad, y), clazz.upper(), fill=(224, 90, 90), font=font_s)
        y += 26
        for i, (key, _, sub, name, line, fn) in enumerate([t for t in D.DESIGNS if t[1] == clazz]):
            x0 = pad + i * cw
            d.rectangle([x0, y, x0 + cw - 14, y + ch - 14], fill=(26, 22, 34), outline=(58, 52, 80), width=2)
            px = pics[key]['standby']
            slot = Image.new('RGBA', (200, 200), (14, 12, 20, 255))
            big = image(px, 6)
            slot.paste(big, (4, 4), big)
            im.paste(slot, (x0 + 10, y + 10))
            rend = iso(px, 4)
            rend.thumbnail((200, 190), Image.LANCZOS)
            im.paste(rend, (x0 + 215 + (200 - rend.width) // 2, y + 10 + (190 - rend.height) // 2), rend)
            hb = Image.new('RGBA', (40, 40), (60, 60, 60, 255))
            small = image(px).resize((32, 32), Image.NEAREST)
            hb.paste(small, (4, 4), small)
            im.paste(hb, (x0 + 10, y + 218))
            d.text((x0 + 58, y + 218), name, fill=(255, 255, 255), font=font_b)
            d.text((x0 + 58, y + 244), sub, fill=(255, 216, 107), font=font_s)
            words, lines, cur = line.split(), [], ''
            for w_ in words:
                if d.textlength(cur + ' ' + w_, font=font) > cw - 40:
                    lines.append(cur)
                    cur = w_
                else:
                    cur = (cur + ' ' + w_).strip()
            lines.append(cur)
            for j, l in enumerate(lines[:4]):
                d.text((x0 + 12, y + 270 + j * 20), l, fill=(207, 199, 221), font=font)
        y += ch + 14
    im.save(path)


def states_sheet(path, pics):
    from PIL import Image, ImageDraw, ImageFont
    font_b = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 20)
    font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 14)
    names = {'standby': 'ungespannt', 'pull0': 'spannen 1', 'pull1': 'spannen 2', 'pull2': 'spannen 3',
             'arrow': 'geladen: Bolzen', 'firework': 'geladen: Rakete'}
    rows = [t for t in D.DESIGNS if t[1] == 'Bogenschütze']
    cell = 150
    im = Image.new('RGBA', (260 + 6 * cell, 60 + len(rows) * (cell + 20)), (18, 16, 25, 255))
    d = ImageDraw.Draw(im)
    d.text((20, 18), 'Bögen und Armbrüste in 32×32: alle Zustände', fill=(255, 216, 107), font=font_b)
    y = 60
    for key, clazz, sub, name, line, fn in rows:
        d.text((20, y + 50), name, fill=(255, 255, 255), font=font_b)
        d.text((20, y + 76), sub, fill=(255, 216, 107), font=font)
        for i, (st, px) in enumerate(pics[key].items()):
            x0 = 240 + i * cell
            d.rectangle([x0, y, x0 + 136, y + 136], fill=(14, 12, 20), outline=(44, 39, 64), width=2)
            big = image(px, 4)
            im.paste(big, (x0 + 4, y + 4), big)
            d.text((x0 + 4, y + 140), names[st], fill=(207, 199, 221), font=font)
        y += cell + 20
    im.save(path)


if __name__ == '__main__':
    pics = all_pictures()
    os.makedirs(OUT, exist_ok=True)
    for key, states in pics.items():
        for st, px in states.items():
            image(px).save(os.path.join(OUT, key + STATE_FILES[st] + '.png'))
    if '--install' in sys.argv:
        install(os.path.join(os.path.dirname(__file__), '..', '..'), pics)
        print('installed')
    sheet(os.path.join(OUT, 'sheet.png'), pics)
    states_sheet(os.path.join(OUT, 'states.png'), pics)
    print('ok', len(pics))
