"""
Flat previews of an outfit: front, right side and back of a player wearing all
four pieces, next to a player without the helmet, drawn large and pixel sharp.
"""
from PIL import Image, ImageDraw
from paint import Tex, PARTS, hexc, mul, cloth

SCALE = 9


def plain_body():
    """A neutral stand-in player under the clothes."""
    t = Tex('skin')
    skin = hexc('#c89c7c')
    hair = hexc('#4a3426')
    shirt = hexc('#5f7f8f')
    pants = hexc('#3d4566')
    shoe = hexc('#55524e')
    t.strip('head', lambda c: hair if c.r <= 1 or c.face == 'back' or (c.face in ('right', 'left') and c.fx >= 5) else skin)
    t.cap('head', hair)
    t.cap('head', skin, face='bottom')
    t.art('head', 'front', ['........', '........', '........', '........', '.wb..bw.', '........', '..mmmm..', '........'],
          {'w': hexc('#f0f0f0'), 'b': hexc('#3a5a9a'), 'm': hexc('#8a5a4a')})
    t.strip('body', cloth(shirt))
    t.cap('body', shirt)
    t.strip('arm', lambda c: shirt if c.r <= 3 else skin)
    t.cap('arm', shirt)
    t.cap('arm', skin, face='bottom')
    t.strip('leg', lambda c: pants if c.r <= 9 else shoe)
    t.cap('leg', shoe, face='bottom')
    return t


def crop(tex, part, face):
    x, y, w, h = tex.face_rect(part, face)
    return tex.img.crop((x, y, x + w, y + h))


def flip(img):
    return img.transpose(Image.FLIP_LEFT_RIGHT)


def view(layers, side):
    """layers: list of textures from the inside out. side: front, back or right."""
    if side == 'right':
        canvas = Image.new('RGBA', (8, 32), (0, 0, 0, 0))
    else:
        canvas = Image.new('RGBA', (16, 32), (0, 0, 0, 0))
    for tex, parts in layers:
        if side == 'front':
            if 'leg' in parts:
                canvas.alpha_composite(crop(tex, 'leg', 'front'), (4, 20))
                canvas.alpha_composite(flip(crop(tex, 'leg', 'front')), (8, 20))
            if 'body' in parts:
                canvas.alpha_composite(crop(tex, 'body', 'front'), (4, 8))
            if 'arm' in parts:
                canvas.alpha_composite(crop(tex, 'arm', 'front'), (0, 8))
                canvas.alpha_composite(flip(crop(tex, 'arm', 'front')), (12, 8))
            if 'head' in parts:
                canvas.alpha_composite(crop(tex, 'head', 'front'), (4, 0))
            if 'hat' in parts:
                canvas.alpha_composite(crop(tex, 'hat', 'front'), (4, 0))
        elif side == 'back':
            if 'leg' in parts:
                canvas.alpha_composite(flip(crop(tex, 'leg', 'back')), (4, 20))
                canvas.alpha_composite(crop(tex, 'leg', 'back'), (8, 20))
            if 'body' in parts:
                canvas.alpha_composite(crop(tex, 'body', 'back'), (4, 8))
            if 'arm' in parts:
                canvas.alpha_composite(flip(crop(tex, 'arm', 'back')), (0, 8))
                canvas.alpha_composite(crop(tex, 'arm', 'back'), (12, 8))
            if 'head' in parts:
                canvas.alpha_composite(crop(tex, 'head', 'back'), (4, 0))
            if 'hat' in parts:
                canvas.alpha_composite(crop(tex, 'hat', 'back'), (4, 0))
        else:
            if 'leg' in parts:
                canvas.alpha_composite(crop(tex, 'leg', 'right'), (2, 20))
            if 'body' in parts:
                canvas.alpha_composite(crop(tex, 'body', 'right'), (2, 8))
            if 'arm' in parts:
                canvas.alpha_composite(crop(tex, 'arm', 'right'), (2, 8))
            if 'head' in parts:
                canvas.alpha_composite(crop(tex, 'head', 'right'), (0, 0))
            if 'hat' in parts:
                canvas.alpha_composite(crop(tex, 'hat', 'right'), (0, 0))
    return canvas


def sheet(name, title, layer1, layer2, out):
    body = plain_body()
    full = [(body, ('leg', 'body', 'arm', 'head')),
            (layer2, ('leg', 'body')),
            (layer1, ('leg', 'body', 'arm', 'head', 'hat'))]
    nohelm = [(body, ('leg', 'body', 'arm', 'head')),
              (layer2, ('leg', 'body')),
              (layer1, ('leg', 'body', 'arm'))]
    views = [view(full, 'front'), view(full, 'right'), view(full, 'back'), view(nohelm, 'front')]
    pad = 2 * SCALE
    w = sum(v.width * SCALE for v in views) + pad * (len(views) + 1)
    h = 32 * SCALE + pad * 2 + 14
    img = Image.new('RGBA', (w, h), hexc('#2b2f38'))
    x = pad
    for v in views:
        big = v.resize((v.width * SCALE, v.height * SCALE), Image.NEAREST)
        img.alpha_composite(big, (x, pad + 14))
        x += big.width + pad
    d = ImageDraw.Draw(img)
    d.text((pad, 4), title, fill=(235, 225, 200, 255))
    img.save(out)
    return img
