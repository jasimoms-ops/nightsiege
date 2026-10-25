"""
The gear of each event per model family. Coordinates are model pixels in the bone's
space (y down, -z is the front). Humanoid bones: head (-4..4, -8..0, -4..4), body
(-4..4, 0..12, -2..2); zombies hold their arms out in front, so on their arms local -z is
up and +z is down. Skeleton arms hang. Creeper head and body like the humanoid's; spider
head (-4..4, -4..4, -8..0) and abdomen body1 (-5..5, -4..4, -6..6).
"""
import math
from gear import Box, chain, block, mirror, sym, ring, cluster, zigzag, R


def blood(f):
    out = []
    if f in ('humanoid', 'skeleton', 'creeper'):
        out += sym(chain('head', (-3.2, -7.2, -1), (-0.1, 0, -1.25), [(2, 2, 2), (2, 3, 2), (1, 3, 1)], 'bone',
                         bend=(-0.2, 0, 0.62), name='horn'))
    if f in ('humanoid', 'skeleton'):
        out += sym(chain('body', (-5.5, 0.5, 0), (0, 0, -0.6), [(2, 3, 2), (1, 3, 1)], 'blood_bone',
                         bend=(0, 0, -0.15), name='sh'))
        out += sym(chain('body', (-4.2, 0.5, 1.4), (-0.4, 0, -0.3), [(1, 3, 1)], 'blood_bone', name='sh2'))
    if f in ('humanoid', 'skeleton', 'creeper'):
        for k, (y, segs) in enumerate(((2, [(2, 2, 2), (1, 3, 1)]), (5.5, [(2, 2, 2), (1, 2, 1)]), (9, [(1, 3, 1)]))):
            out += chain('body', (0, y, 2), (-0.9, 0, 0), segs, 'blood_bone', name=f'sp{k}')
        out += cluster('body', (-2.5, 4, 2), (-0.9, 0, -0.3), 'blood_crystal', sizes=((2, 4, 2), (1, 3, 1)), name='bc')
    if f == 'humanoid':
        for k, y in enumerate((3, 6.5)):
            out += sym(chain('right_arm', (-1, y, -2), (R(90), 0, -0.25), [(1, 3, 1)], 'blood_bone', name=f'arm{k}'))
    if f == 'creeper':
        out += sym(chain('body', (-3.5, 3, 2), (-0.8, 0, -0.5), [(1, 3, 1)], 'blood_bone', name='cs'))
    if f == 'spider':
        for k, (z, h) in enumerate(((-4, 3), (-0.5, 4), (3, 3))):
            out += sym(chain('body1', (-3.5, -4, z), (-0.25, 0, -0.55), [(2, h, 2), (1, 3, 1)], 'blood_bone', name=f'ab{k}'))
        out += sym(chain('head', (-1.5, 3, -7.5), (R(162), 0, 0.1), [(1, 3, 1), (1, 2, 1)], 'bone', bend=(-0.4, 0, 0), name='fang'))
        out += sym(chain('head', (-2.5, -4, -5), (-0.3, 0, -0.3), [(1, 2, 1), (1, 2, 1)], 'bone', bend=(-0.3, 0, 0), name='sh'))
        out += cluster('body1', (0, -4, 0), (-0.2, 0, 0), 'blood_crystal', sizes=((2, 4, 2), (1, 3, 1), (1, 2, 1)), name='bc')
    return out


def eclipse(f):
    out = []
    if f in ('humanoid', 'skeleton'):
        out += ring('head', (0, -5, 5.6), 6, 14, (3, 1, 1), 'gold', spikes=2, name='halo')
        out += sym(chain('body', (-5, 0.5, 0), (0.2, 0, -0.7), [(2, 4, 2), (1, 3, 1)], 'obsidian', name='sh'))
        out += sym(chain('body', (-4, 0.5, 1.5), (-0.5, 0, -0.3), [(1, 4, 1)], 'obsidian', name='sh2'))
        out += chain('body', (0, 3, 2), (-1.0, 0, 0), [(2, 3, 2), (1, 2, 1)], 'obsidian', name='sp', mats=['obsidian', 'gold'])
        out += chain('body', (0, 7, 2), (-1.0, 0, 0), [(2, 2, 2), (1, 2, 1)], 'obsidian', name='sq', mats=['obsidian', 'gold'])
    if f == 'creeper':
        out += ring('head', (0, -11, 0), 5, 12, (3, 1, 1), 'gold', plane='xz', spikes=2, name='halo')
        out += chain('body', (0, 3, 2), (-1.0, 0, 0), [(2, 3, 2), (1, 2, 1)], 'obsidian', name='sp', mats=['obsidian', 'gold'])
        out += sym(chain('body', (-3, 6, 2), (-0.9, 0, -0.4), [(1, 3, 1)], 'obsidian', name='cs'))
    if f == 'spider':
        out += ring('body1', (0, -7.5, 0), 5.5, 12, (3, 1, 1), 'gold', plane='xz', spikes=2, name='halo')
        for k, (x, z) in enumerate(((-3, -3), (-3, 2))):
            out += sym(chain('body1', (x, -4, z), (-0.3, 0, -0.35), [(2, 3, 2), (1, 2, 1)], 'obsidian', name=f'ab{k}',
                             mats=['obsidian', 'gold']))
    return out


def fog(f):
    out = []
    if f in ('humanoid', 'skeleton', 'creeper'):
        out.append(block('head', (-4.5, -8.6, -3.5), (9, 1, 8), 'moss', key='cap'))
        for k, (x, h) in enumerate(((-4.2, 6), (-2.6, 9), (-1.0, 7), (0.6, 10), (2.2, 6), (3.4, 8))):
            out.append(block('head', (x, -8, 4.1), (1, h, 1), 'moss', key=f'hair{h}'))
    if f in ('humanoid', 'skeleton'):
        out += sym([block('body', (-6.5, -1.2, -2.6), (4, 2, 5), 'moss', key='pad')])
        for k, (x, z, h) in enumerate(((-3, -2.6, 5), (-0.5, -2.6, 6), (2, -2.6, 4), (-2, 1.6, 6), (1.5, 1.6, 5))):
            out.append(block('body', (x, 10.5, z), (1, h, 1), 'rag', key=f'rag{h}'))
        out.append(block('body', (-5.4, 7.5, -0.5), (1, 3, 1), 'iron', key='hook'))
        out.append(block('body', (-6.5, 10.2, -1.5), (3, 4, 3), 'ghost_light', key='lantern'))
        out.append(block('body', (-6.9, 9.6, -1.9), (4, 1, 4), 'iron', key='lcap'))
    if f == 'humanoid':
        for k, y in enumerate((3, 7)):
            out += sym(chain('right_arm', (-1, y, 2), (R(-90), 0, 0), [(1, 4 + k, 1)], 'moss', name=f'arm{k}'))
    if f == 'creeper':
        for k, x in enumerate((-3.5, -1, 1.5, 3)):
            out.append(block('head', (x, 0, -4.4), (1, 3 + (k % 2) * 2, 1), 'moss', key=f'fr{k}'))
        out += sym([block('body', (-4.6, 2, -1), (1, 7, 1), 'web', key='web')])
    if f == 'spider':
        out.append(block('body1', (-4, -4.8, -4), (8, 1, 9), 'moss', key='cap'))
        for k, (z, h) in enumerate(((-4, 5), (-1, 7), (2, 6), (4.5, 4))):
            out += sym([block('body1', (-5.6, -1, z), (1, h, 1), 'moss', key=f'st{h}')])
        out.append(block('body1', (-3, -1, 6.2), (6, 4, 1), 'web', key='webb'))
        out.append(block('head', (-3, -4.6, -7), (6, 1, 5), 'web', key='webh'))
    return out


def thunder(f):
    """Storm-charged: storm clouds on the shoulders, lightning bolts for horns, a bolt struck
    through the back."""
    out = []
    if f in ('humanoid', 'skeleton', 'creeper'):
        out += sym(zigzag('head', (-2.5, -8, -0.5), (-0.1, 0, -0.35), [(1, 3, 1), (1, 3, 1), (1, 2, 1)], 'bolt',
                          angle=0.75, name='hb'))
    if f in ('humanoid', 'skeleton'):
        out += sym(cluster('body', (-5.6, 0.6, 0), (0, 0, 0), 'storm_cloud',
                           sizes=((5, 3, 5), (4, 3, 4), (3, 2, 4), (3, 2, 3)), spread=2.2, tilt=0.15, name='cloud'))
        out += zigzag('body', (2.5, 8, 2.2), (-0.55, 0, 0.5), [(2, 4, 1), (1, 4, 1), (1, 4, 1), (1, 3, 1)], 'bolt',
                      angle=0.8, name='bb')
    if f == 'humanoid':
        out += sym([block('right_arm', (-3.5, 6, -2.5), (5, 2, 5), 'storm_iron', key='brace')])
    if f == 'skeleton':
        out += sym([block('right_arm', (-1.5, 6, -1.5), (3, 2, 3), 'storm_iron', key='brace')])
    if f == 'creeper':
        out += cluster('head', (0, -7.5, 0), (0, 0, 0), 'storm_cloud', sizes=((6, 3, 6), (4, 3, 5), (4, 2, 4), (3, 2, 3)),
                       spread=2.6, tilt=0.12, name='cloud')
        out += zigzag('body', (0, 9, 2.2), (-0.6, 0, 0.3), [(2, 4, 1), (1, 3, 1), (1, 3, 1)], 'bolt', angle=0.8, name='bb')
    if f == 'spider':
        out += cluster('body1', (0, -4, 0.5), (0, 0, 0), 'storm_cloud', sizes=((6, 2, 6), (4, 2, 4), (3, 2, 4)), spread=2.4,
                       tilt=0.15, name='cloud')
        out += sym(zigzag('body1', (-2, -5.5, 0), (-0.2, 0, -0.3), [(1, 3, 1), (1, 3, 1), (1, 2, 1)], 'bolt', angle=0.75, name='sb'))
        out += sym(zigzag('head', (-2.5, -4, -5), (-0.3, 0, -0.4), [(1, 2, 1), (1, 2, 1)], 'bolt', angle=0.7, name='hb'))
    return out


def nether(f):
    out = []
    if f in ('humanoid', 'skeleton', 'creeper'):
        out += sym(chain('head', (-3.4, -6.5, -0.5), (-0.15, 0, -1.35), [(3, 3, 3), (2, 3, 2), (2, 3, 2), (1, 3, 1)], 'horn_magma',
                         bend=(-0.3, 0, 0.55), name='horn', mats=['dark_horn', 'dark_horn', 'dark_horn', 'magma']))
    if f in ('humanoid', 'skeleton'):
        out += sym(cluster('body', (-5.5, 0, 0), (0, 0, -0.4), 'magma', name='mc'))
        out += chain('body', (0, 3, 2), (-0.9, 0, 0), [(2, 3, 2), (1, 3, 1)], 'basalt', name='sp', mats=['basalt', 'magma'])
        out += chain('body', (-1.5, 7, 2), (-0.9, 0, -0.3), [(2, 2, 2), (1, 2, 1)], 'basalt', name='sq', mats=['basalt', 'magma'])
    if f == 'creeper':
        out += cluster('body', (0, 3, 2), (-0.9, 0, 0), 'magma', name='mc')
        out += sym(chain('body', (-3, 7, 2), (-0.9, 0, -0.4), [(1, 3, 1)], 'magma', name='cs'))
    if f == 'spider':
        out += sym(chain('head', (-2.5, -4, -4), (-0.2, 0, -0.6), [(2, 2, 2), (1, 3, 1)], 'dark_horn', bend=(-0.4, 0, 0.3),
                         name='horn', mats=['dark_horn', 'magma']))
        out += cluster('body1', (-2.5, -4, 0), (0, 0, -0.2), 'magma', name='m1')
        out += cluster('body1', (2.5, -4, -2.5), (0, 0, 0.2), 'magma', sizes=((2, 4, 2), (1, 3, 1)), name='m2')
        out += cluster('body1', (0.5, -4, 3.5), (-0.3, 0, 0), 'magma', sizes=((1, 4, 1), (1, 3, 1)), name='m3')
    return out


def frost(f):
    out = []
    if f in ('humanoid', 'skeleton', 'creeper'):
        out += cluster('head', (-2, -8, 0), (0, 0, -0.25), 'ice', sizes=((1, 4, 1), (1, 3, 1)), name='cr1')
        out += cluster('head', (2, -8, -1), (0, 0, 0.25), 'ice', sizes=((1, 5, 1), (1, 3, 1)), name='cr2')
        out += cluster('head', (0, -8, 2.5), (-0.3, 0, 0), 'ice', sizes=((1, 3, 1),), name='cr3')
        for k, x in enumerate((-3.5, -2, 1, 2.5)):
            out.append(block('head', (x, 0, -4.2), (1, 2 + (k % 2), 1), 'icicle', key=f'ic{k % 2}'))
    if f in ('humanoid', 'skeleton'):
        out += sym(cluster('body', (-5.5, 0, 0), (0, 0, -0.35), 'ice', sizes=((2, 5, 2), (1, 4, 1), (1, 3, 1)), name='sc'))
        out += chain('body', (0, 3, 2), (-0.9, 0, 0), [(2, 3, 2), (1, 3, 1)], 'ice', name='sp')
        out.append(block('body', (-4.3, -0.6, -2.3), (9, 1, 5), 'snow', key='snow'))
    if f == 'humanoid':
        for k, y in enumerate((4, 8)):
            out += sym(chain('right_arm', (-1, y, 2), (R(-90), 0, 0), [(1, 2 + k, 1)], 'icicle', name=f'ai{k}'))
    if f == 'creeper':
        out += cluster('body', (0, 3, 2), (-0.9, 0, 0), 'ice', name='bc')
        for k, x in enumerate((-3.5, -1.5, 0.5, 2.5)):
            out.append(block('head', (x, 0, 3.2), (1, 2 + (k % 2), 1), 'icicle', key=f'ic{k % 2}'))
    if f == 'spider':
        out += cluster('body1', (-2.5, -4, -1), (0, 0, -0.25), 'ice', name='a1')
        out += cluster('body1', (2.5, -4, 2), (0, 0, 0.25), 'ice', sizes=((2, 4, 2), (1, 3, 1)), name='a2')
        for k, z in enumerate((-4, -1, 2, 5)):
            out += sym([block('body1', (-5.2, 4, z), (1, 2 + (k % 2), 1), 'icicle', key=f'ic{k % 2}')])
        out.append(block('head', (-3, -4.6, -7), (6, 1, 5), 'snow', key='snow'))
    return out


EVENTS = {'blood_moon': blood, 'eclipse': eclipse, 'fog': fog, 'thunder': thunder, 'nether_rift': nether, 'frost': frost}
FAMILY_NAMES = ('humanoid', 'skeleton', 'creeper', 'spider')


def design(event, family):
    return EVENTS[event](family)
