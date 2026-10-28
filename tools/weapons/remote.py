"""
The engineer's remote, design B "brass transmitter" (Simon, 29 October 2026): a brass body with a
small green screen, a red and a blue button, a riveted grip and an antenna with a cyan crystal.
32x32 like the kit weapons, finished with their bevel, grain and tinted outline (kit_v3.finish).

python3 tools/weapons/remote.py [--install]
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import kit_v3 as K  # noqa: E402

from PIL import Image

BRASS = (184, 140, 62, 255)
BRASS_D = (132, 96, 38, 255)
COPPER = (170, 98, 58, 255)
SCREEN = (60, 200, 110, 255)
SCREEN_D = (24, 80, 48, 255)
RED = (214, 52, 44, 255)
BLUE = (60, 120, 220, 255)
DARK = (34, 28, 24, 255)
CRYSTAL = (110, 240, 235, 255)
CRYSTAL_D = (40, 160, 170, 255)
IRON = (140, 144, 152, 255)


def draw():
    px = {}

    def rect(x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                px[(x, y)] = c

    # antenna (tilted a little to the right) with the crystal on top
    for i in range(9):
        x, y = 19 + i // 4, 12 - i
        px[(x, y)] = IRON
    for (dx, dy, c) in ((0, -2, CRYSTAL), (-1, -1, CRYSTAL), (0, -1, CRYSTAL_D), (1, -1, CRYSTAL),
                        (-1, 0, CRYSTAL_D), (0, 0, CRYSTAL), (1, 0, CRYSTAL_D), (0, 1, CRYSTAL_D)):
        px[(21 + dx, 2 + dy)] = c
    # the body
    rect(9, 12, 23, 27, BRASS)
    rect(9, 26, 23, 27, BRASS_D)
    rect(10, 12, 22, 12, BRASS_D)
    # copper side plates with rivets
    rect(8, 14, 8, 25, COPPER)
    rect(24, 14, 24, 25, COPPER)
    for y in (15, 24):
        px[(8, y)] = IRON
        px[(24, y)] = IRON
    # the screen with a little wave
    rect(11, 14, 21, 19, SCREEN_D)
    for x, y in ((12, 17), (13, 16), (14, 16), (15, 17), (16, 18), (17, 18), (18, 17), (19, 16), (20, 16)):
        px[(x, y)] = SCREEN
    # the buttons
    rect(12, 22, 14, 24, DARK)
    rect(12, 22, 13, 23, RED)
    rect(18, 22, 20, 24, DARK)
    rect(18, 22, 19, 23, BLUE)
    # the grip under the body
    rect(13, 28, 19, 30, BRASS_D)
    rect(14, 28, 18, 29, DARK)
    return px


def picture():
    out = K.finish(draw(), 41)
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    for (x, y), c in out.items():
        if 0 <= x < 32 and 0 <= y < 32:
            im.putpixel((x, y), c)
    return im


if __name__ == "__main__":
    im = picture()
    root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    if "--install" in sys.argv:
        im.save(os.path.join(root, "src/main/resources/assets/nightsiege/textures/item/remote_control.png"))
    os.makedirs(os.path.join(root, "build"), exist_ok=True)
    im.resize((256, 256), Image.NEAREST).save(os.path.join(root, "build", "remote_preview.png"))
    print("remote ok")
