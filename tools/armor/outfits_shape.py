"""
Rounder shapes: the big single boxes get layers and bends, so caps, skulls and
cloaks read as round and soft instead of as blocks.
"""
from paint import hexc, mul, fur, cloth, shaded, metal, leather
from outfits3d import _then, all_faces, ragged_bottom


def dome(k, L1, name, pos, size, mat, slot='head', part='head', pivot=(0, 0, 0), rot=(0, 0, 0)):
    b = k.box(slot, part, name, pos, size, pivot=pivot, rot=rot)
    all_faces(L1, b, mat)
    return b


def collar_roll(k, L1, mat, z=2.3, width=11):
    b = k.box('chest', 'body', 'cloakroll', (-width / 2.0, -1.9, z), (width, 2, 2))
    all_faces(L1, b, mat)


def lame(k, L1, name, mat, trim=None):
    b = k.box('chest', 'arm', name, (-4.4, -0.5, -3.3), (5, 2, 6), rot=(0, 0, -0.12))
    all_faces(L1, b, mat)
    if trim is not None:
        L1.strip(b, lambda c: trim if c.r == 1 else None)


def _berserker(k, L1, L2):
    bone, bone_d = hexc('#e4dac1'), hexc('#b3a68a')
    B = lambda c: bone_d if c.rnd.random() < 0.15 else bone
    dome(k, L1, 'cranium', (-4, -12.5, -4.5), (8, 1, 8), B)
    dome(k, L1, 'cranium2', (-2.5, -13.2, -3), (5, 1, 5), B)
    brow = dome(k, L1, 'brow', (-5.3, -9.9, -6.2), (11, 1, 2), B)
    L1.strip(brow, lambda c: mul(bone, 0.6) if c.r == 0 and c.s % 3 == 0 else None)
    nose = dome(k, L1, 'nosetip', (-1.5, -7.6, -9.4), (3, 2, 2), B)
    L1.art(nose, 'front', ['b.b', 'ttt'], {'b': hexc('#1d1915'), 't': hexc('#f4ecd6')})
    horn, horn_d = hexc('#d6c9a6'), hexc('#3d3428')
    tip = k.box('head', 'head', 'horn4', (-0.5, -3, -0.5), (1, 3, 1), pivot=(-8.5, -16.2, -0.5), rot=(0, 0, 0.6), sym=True)
    all_faces(L1, tip, lambda c: horn_d if c.r == 0 else horn)
    collar_roll(k, L1, fur(hexc('#4a4038'), hexc('#6a5c4e'), hexc('#2c2520'), hexc('#8e7e68')), z=2.6)


def _blademaster(k, L1, L2):
    blue = hexc('#2c4482')
    dome(k, L1, 'beret2', (-4.5, -3.2, -4.5), (9, 1, 9), cloth(mul(blue, 1.1)), pivot=(0, -9, 0), rot=(0.05, 0, -0.14))
    collar_roll(k, L1, cloth(mul(blue, 0.9)), z=2.5, width=10)


def _axe(k, L1, L2):
    lame(k, L1, 'lame', leather(hexc('#5b3b24')), hexc('#c9c5b8'))


def _paladin(k, L1, L2):
    lame(k, L1, 'lame', shaded(hexc('#e8e3d6')), hexc('#d4ae38'))
    collar_roll(k, L1, shaded(hexc('#8a1c1c')), z=2.7)


def _swift(k, L1, L2):
    pass


def _heavy(k, L1, L2):
    dome(k, L1, 'cap2', (-3.5, -11.6, -3.5), (7, 1, 7), metal(hexc('#9aa0a8')))


def _trapper(k, L1, L2):
    F = fur(hexc('#6e5a44'), hexc('#8e7a60'), hexc('#3e3226'), hexc('#c8b896'))
    dome(k, L1, 'cap2', (-4, -11.8, -4), (8, 1, 8), F)
    flap = k.box('head', 'head', 'earflap', (-6.2, -8.6, -2), (1, 5, 4), sym=True)
    all_faces(L1, flap, F)
    ragged_bottom(L1, flap, 1)


def _shieldfighter(k, L1, L2):
    dome(k, L1, 'dome2', (-3.5, -11.6, -3.5), (7, 1, 7), metal(hexc('#9aa0a8')))
    lame(k, L1, 'lame', metal(hexc('#9aa0a8')), hexc('#5a5e66'))


def _medic(k, L1, L2):
    dome(k, L1, 'cap2', (-3.5, -11.6, -3.5), (7, 1, 7), cloth(hexc('#ece6d8')))
    fold = dome(k, L1, 'capfold', (-5.4, -9.4, -5.4), (11, 1, 11), cloth(hexc('#d8d0c0')))
    L1.cap(fold, 'clear', where=lambda c: 1 <= c.fx <= 9 and 1 <= c.fy <= 9)


def _warden(k, L1, L2):
    collar_roll(k, L1, shaded(hexc('#26183a')), z=2.7)


def _gunner(k, L1, L2):
    dome(k, L1, 'dome2', (-3.5, -11.6, -3.5), (7, 1, 7), metal(hexc('#a0a6ae')))


for name, fn in (('berserker', _berserker), ('blademaster', _blademaster), ('axe', _axe), ('paladin', _paladin),
                 ('heavy', _heavy), ('trapper', _trapper), ('shieldfighter', _shieldfighter), ('medic', _medic),
                 ('warden', _warden), ('gunner', _gunner)):
    _then(name, fn)
