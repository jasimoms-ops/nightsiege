"""
The epic pass for the twelve outfits besides the warriors: more details on top
of outfits3d. Imported by build.py after outfits3d.
"""
from paint import hexc, mul, fur, leather, metal, cloth
from outfits3d import DESIGNS, _then, all_faces, ragged_bottom


def _swift(k, L1, L2):
    # Fletching stripes on the quiver, a hunting knife (the clasp went with the front of the cape).
    L1.strip('quiver', lambda c: hexc('#c9a060') if c.r in (2, 6) else None)
    knife = k.box('chest', 'body', 'knife', (-5.6, 8.4, -1), (1, 5, 2))
    all_faces(L1, knife, lambda c: hexc('#3a2a1a') if c.r < 2 else hexc('#5a3a22'))
    L1.strip('capelet', lambda c: hexc('#5a7a3a') if c.r == 0 and c.s % 3 == 0 else None)


def _heavy(k, L1, L2):
    # Rivets round the cap, a bow string over the chest, a lion badge.
    L1.strip('cap', lambda c: hexc('#d8dde4') if c.r == 1 and c.s % 3 == 0 else None)
    string = k.box('chest', 'body', 'bowstring', (-4.6, -0.5, -3.7), (9, 13, 1))
    all_faces(L1, string, 'clear')
    L1.art(string, 'front', ['s........', '.s.......', '..s......', '...s.....', '....s....', '.....s...',
                             '......s..', '.......s.', '........s'], {'s': hexc('#e8e0c8')}, y=2)
    badge = k.box('chest', 'body', 'badge', (1.2, 2, -3.9), (2, 2, 1))
    all_faces(L1, badge, hexc('#c9a032'))


def _bolter(k, L1, L2):
    # Slashes edged in gold, a crossbow cranequin at the hip, ribbons on the hat.
    L1.strip('puff', lambda c: hexc('#c9a032') if c.r % 4 == 1 and c.s % 3 == 0 else None)
    crank = k.box('chest', 'body', 'crank', (-6, 8, -1.5), (1, 3, 3))
    all_faces(L1, crank, metal(hexc('#7a7e86')))
    ribbon = k.box('head', 'head', 'ribbon', (-1, 0, -0.5), (2, 5, 1), pivot=(5, -9.5, 3), rot=(0.3, 0, 0.2))
    all_faces(L1, ribbon, lambda c: hexc('#c0622a') if c.r % 2 else hexc('#2a2424'))


def _trapper(k, L1, L2):
    # Teeth on a cord, a hatchet, a steel trap hanging from the belt.
    teeth = k.box('chest', 'body', 'teeth', (-3, 0.4, -3.9), (6, 2, 1))
    all_faces(L1, teeth, 'clear')
    L1.art(teeth, 'front', ['cccccc', 't.t.t.'], {'c': hexc('#4a3020'), 't': hexc('#efe6cf')})
    trap = k.box('chest', 'body', 'trap', (-6.4, 9.5, -2), (2, 3, 4))
    all_faces(L1, trap, lambda c: hexc('#8a8e94') if (c.s + c.r) % 2 else hexc('#4a4e54'))
    hatchet = k.box('chest', 'body', 'hatchet', (4.6, 8, -0.5), (1, 6, 1))
    all_faces(L1, hatchet, lambda c: hexc('#6b4a2c'))
    head = k.box('chest', 'body', 'hatchethead', (4.5, 8, -2), (1, 2, 2))
    all_faces(L1, head, metal(hexc('#8a8e94')))


def _shieldfighter(k, L1, L2):
    # A plume on the kettle hat, a sword at the hip, studs on the spaulders.
    plume = k.box('head', 'head', 'plume', (-0.5, -4, -0.5), (1, 4, 1), pivot=(0, -10.5, 0), rot=(-0.2, 0, 0))
    all_faces(L1, plume, lambda c: hexc('#2f4f8a') if c.r % 2 else hexc('#d4ae38'))
    L1.strip('spaulder', lambda c: hexc('#d8dde4') if c.r == 0 and c.s % 3 == 1 else None)
    sword = k.box('chest', 'body', 'sword', (4.8, 7, -2), (1, 9, 1), rot=(0.3, 0, 0))
    all_faces(L1, sword, lambda c: hexc('#d4ae38') if c.r == 0 else hexc('#2a1c14'))


def _bulwark(k, L1, L2):
    # Engraved brass edges, a chain over the chest, a tower-shield boss on the back.
    for p in ('paul0', 'paul1', 'gorget'):
        L1.strip(p, lambda c: hexc('#c9a43a') if c.r == 0 and c.s % 2 == 0 else None)
    boss = k.box('chest', 'body', 'boss', (-4, -0.5, 3.4), (8, 11, 1))
    all_faces(L1, boss, metal(hexc('#6a6e76')))
    L1.art(boss, 'back', ['bbbbbbbb', 'b......b', 'b..cc..b', 'b.cCCc.b', 'b..cc..b', 'b......b'], {'b': hexc('#c9a43a'), 'c': hexc('#8a8e94'), 'C': hexc('#d8dde4')}, y=2)


def _medic(k, L1, L2):
    # Herbs sticking out of the satchel, a bandage roll, bottles on the belt.
    herbs = k.box('chest', 'body', 'herbs', (-6.6, 5.6, -1.5), (1, 2, 3))
    all_faces(L1, herbs, lambda c: hexc('#5a9a3a') if (c.s + c.r) % 2 else hexc('#8ad35a'))
    roll = k.box('chest', 'body', 'bandage', (-5.6, 9, 1.5), (2, 2, 2))
    all_faces(L1, roll, lambda c: hexc('#ece6d8') if c.r else hexc('#c4bdac'))
    for i, x in enumerate((-2.5, 0.5)):
        b = k.box('chest', 'body', 'bottle%d' % i, (x, 7.4, -4.1), (1, 2, 1))
        all_faces(L1, b, lambda c: hexc('#c03030') if c.r else hexc('#7a5634'))


def _warden(k, L1, L2):
    # A glowing rune sigil on the cloak and a rune staff strap.
    L1.art('cloak', 'back', ['.....s.....', '....s.s....', '...s.g.s...', '..s.ggg.s..', '...s.g.s...', '....s.s....', '.....s.....'],
           {'s': hexc('#c9d0de'), 'g': hexc('#b48cff')}, y=4)
    L1.strip('hood', lambda c: hexc('#b48cff') if c.r == 0 and c.s % 4 == 0 else None)


def _gunner(k, L1, L2):
    # A slow match cord on the shoulder, brass rivets, a powder horn.
    horn = k.box('chest', 'body', 'powderhorn', (-6.2, 7.5, -1.5), (2, 4, 3), rot=(0, 0, 0.3))
    all_faces(L1, horn, lambda c: hexc('#d8c8a0') if c.r < 3 else hexc('#c09a40'))
    L1.strip('brim', lambda c: hexc('#ead27e') if c.s % 6 == 0 else None)
    match = k.box('chest', 'arm', 'match', (-4.4, 1, -3.3), (6, 1, 6), grow=0.2)
    all_faces(L1, match, lambda c: hexc('#ff8a30') if c.s % 7 == 0 else hexc('#6a5034'))


def _electrician(k, L1, L2):
    # Cables from the backpack to the shoulder coils, a lightning badge.
    cable = k.box('chest', 'body', 'cable', (-4.6, -1.6, 3.2), (9, 1, 1))
    all_faces(L1, cable, lambda c: hexc('#2a2a2e') if c.s % 3 else hexc('#b8693a'))
    badge = k.box('chest', 'body', 'bolt', (1, 1.5, -3.9), (2, 3, 1))
    all_faces(L1, badge, 'clear')
    L1.art(badge, 'front', ['.y', 'yy', 'y.'], {'y': hexc('#7fe8ff')})


def _demolition(k, L1, L2):
    # A lit fuse on the top bomb, a detonator plunger at the hip, soot stains.
    fuse = k.box('chest', 'body', 'fuse', (-3.5, -0.4, -4.8), (1, 2, 1))
    all_faces(L1, fuse, lambda c: hexc('#ffb040') if c.r == 0 else hexc('#c9b48a'))
    plunger = k.box('chest', 'body', 'plunger', (4.4, 8.4, -1.5), (2, 3, 3))
    all_faces(L1, plunger, lambda c: hexc('#b03028') if c.r == 0 else hexc('#5a3a22'))
    L1.strip('apron', lambda c: hexc('#0e0c0a') if c.rnd.random() < 0.08 else None)


def _alchemist(k, L1, L2):
    # A small grimoire at the hip, a posy in the hat.
    tome = k.box('chest', 'body', 'tome', (-6.4, 8.4, -1.5), (2, 3, 3))
    all_faces(L1, tome, lambda c: hexc('#2a3a22') if c.face != 'top' else hexc('#e8e0c8'))
    L1.art(tome, 'right', ['...', '.g.', '...'], {'g': hexc('#9cf06a')})
    # A sprig of herbs in the band of the hat (the plague doctor's posy).
    herbs = k.box('head', 'head', 'herbs', (2.5, -3.6, -5.2), (2, 3, 1), pivot=(0, -9, 0), rot=(0.06, 0, 0.3))
    all_faces(L1, herbs, lambda c: hexc('#5a9a3a') if (c.s + c.r) % 2 else hexc('#c84a8a'))


for name, fn in (('swift', _swift), ('heavy', _heavy), ('bolter', _bolter), ('trapper', _trapper),
                 ('shieldfighter', _shieldfighter), ('bulwark', _bulwark), ('medic', _medic), ('warden', _warden),
                 ('gunner', _gunner), ('electrician', _electrician), ('demolition', _demolition), ('alchemist', _alchemist)):
    _then(name, fn)
