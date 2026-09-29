#!/usr/bin/env python3
"""Draw the gui icons.

The chest sheets are not here: they differ per kind, and the list of kinds is
in Java. Generating them from a second list in a second language would be two
lists to keep in step. See ChestSheets.

This script is the source of the sprite; the PNG under src/main/resources is its
output and is not edited by hand.

A crate: boards behind, two iron bands across, and a latch in the middle of the
lower band. Shading is derived rather than drawn -- a pixel with nothing of its own
material above-left of it catches the light, one with nothing below-right falls into
shadow, and everything else is body colour. So the bands are lit and shaded along
their length without either edge being described, and moving a band moves its
highlight with it.

No third-party libraries: the PNG is assembled from zlib and struct.

    python tools/make_textures.py
"""

from __future__ import annotations

import pathlib
import struct
import zlib

SIZE = 16  # the block texture; the gui icons are ICON square

ASSETS = pathlib.Path(__file__).resolve().parents[1] / "src/main/resources/assets/cella/textures"
GUI = ASSETS / "gui"

# One place for every colour. Body, and the two derived from it.
WOOD = (0x8A, 0x66, 0x3C, 0xFF)
WOOD_LIT = (0xA5, 0x7C, 0x4B, 0xFF)
WOOD_DARK = (0x6B, 0x4C, 0x2B, 0xFF)
SEAM = (0x5A, 0x3F, 0x23, 0xFF)

IRON = (0x8C, 0x8C, 0x94, 0xFF)
IRON_LIT = (0xB2, 0xB2, 0xBA, 0xFF)
IRON_DARK = (0x66, 0x66, 0x6E, 0xFF)

# --- the gui icons -----------------------------------------------------------
#
# Six square. They sit in a ten-pixel button (see IconButton), which leaves two
# pixels of face all round - both even, so the centring is exact, and the room round
# the picture is the point: a button with its picture pressed against the frame reads
# as a picture with a frame.
#
# Ten and two are measured off the buttons the sorting mod puts on the same screen,
# not chosen. Six pixels is very little, so these are made to be told apart rather
# than read, and they say one thing: which way the items go.
#
# Up is into the chest and down is towards the player, because these two sit beside
# the player's inventory and the chest is the half above them. They had a shelf drawn
# in as well, which cost the arrow half its height and pointed the wrong way for
# where the buttons are. Widening either one to everything is shift, which needs no
# picture at all - which is why there are two of these and not four.
ICON = 6
INK = (0xEE, 0xEE, 0xEE, 0xFF)
CLEAR = (0x00, 0x00, 0x00, 0x00)

def _rows(ink):
    return [[INK if (x, y) in ink else CLEAR for x in range(ICON)] for y in range(ICON)]


def _row(y, length):
    """A centred horizontal run, one pixel deep."""
    return {((ICON - length) // 2 + dx, y) for dx in range(length)}


def _flip(cells):
    return {(x, ICON - 1 - y) for (x, y) in cells}


def _down():
    """Shaft, then a head tapering to a point. Six rows exactly, so it is centred."""
    return (_row(0, 2) | _row(1, 2) | _row(2, 2)
            | _row(3, 6) | _row(4, 4) | _row(5, 2))


def sort_icon():
    """Three bars, longest first, which is what a sort button looks like everywhere.

    The top bar is two deep and the others one, which is not decoration: three
    one-pixel bars with a gap between them fill five rows of six, leaving the spare
    row at the bottom and the picture looking stuck to the top. Thickening the longest
    one fills the sixth, keeps the gaps even, and reads as weight going with length.
    """
    return _rows(_row(0, 6) | _row(1, 6) | _row(3, 4) | _row(5, 2))


def stow_icon():
    """Up, because the chest is the half above and this button is beside the pack."""
    return _rows(_flip(_down()))


def take_icon():
    """Down, towards the player, for the same reason."""
    return _rows(_down())


def _chevron(pointing_left):
    """A two-pixel arrowhead, so the page arrows are drawn like everything else here.

    The MARGIN is the whole of the difference between an arrow in a button and an arrow
    stuck to the side of one. Without it the ink runs 0..3 of six, whose middle is 1.5
    against the picture's 2.5 - a pixel out, and out is exactly the direction that
    reads as wrong on a pair pointing away from each other.
    """
    MARGIN = 1
    cells = set()
    for y in range(ICON):
        reach = MARGIN + abs(2 * y - (ICON - 1)) // 2
        for dx in range(2):
            x = reach + dx
            cells.add((x if pointing_left else ICON - 1 - x, y))
    return cells


def prev_icon():
    return _rows(_chevron(True))


def next_icon():
    return _rows(_chevron(False))


def list_icon():
    return _rows({(0, y) for y in (0, 2, 4)} | {(x, y) for y in (0, 2, 4) for x in range(2, ICON)})


def back_icon():
    return _rows({(ICON - 1 - y, x) for (x, y) in _down()})


def close_icon():
    return _rows({(i, i) for i in range(ICON)} | {(i, ICON - 1 - i) for i in range(ICON)})


def add_icon():
    middle = {ICON // 2 - 1, ICON // 2}
    return _rows({(x, y) for x in range(ICON) for y in range(ICON) if x in middle or y in middle})


def bin_icon():
    return _rows(_row(0, 2) | _row(1, 6)
                 | {(x, y) for y in range(2, ICON) for x in (1, ICON - 2)}
                 | _row(ICON - 1, 4))


def pack_icon():
    return _rows({(0, 1), (1, 2), (1, 3), (0, 4), (5, 1), (4, 2), (4, 3), (5, 4)})


def _drawn(*lines):
    return _rows({(x, y) for y, line in enumerate(lines) for x, mark in enumerate(line) if mark == "#"})


def outlet_icon():
    return _drawn("....#.",
                  "######",
                  "....#.",
                  ".#....",
                  "######",
                  ".#....")


TICK_LEFT = 2
TICK_TOP = -1


def select_icon():
    corner = [(2, 5), (3, 6), (4, 7)]
    rising = [(4 + step, 7 - step) for step in range(1, 8)]
    cells = set()
    for x, y in corner + rising:
        cells |= {(x - TICK_LEFT, y - TICK_TOP), (x - TICK_LEFT, y - 1 - TICK_TOP)}
    wide = max(x for x, _ in cells) + 1
    tall = max(y for _, y in cells) + 1
    return [[INK if (x, y) in cells else CLEAR for x in range(wide)] for y in range(tall)]


ICONS = {
    "sort": sort_icon,
    "stow": stow_icon,
    "take": take_icon,
    "prev": prev_icon,
    "next": next_icon,
    "list": list_icon,
    "back": back_icon,
    "close": close_icon,
    "add": add_icon,
    "bin": bin_icon,
    "pack": pack_icon,
    "outlet": outlet_icon,
    "select": select_icon,
}

BUTTON = 10
FACE = (0x8B, 0x8B, 0x8B, 0xFF)
RIM = (0x37, 0x37, 0x37, 0xFF)
PAPER = (0xC6, 0xC6, 0xC6, 0xFF)
ZOOM = 8
GAP = 4


def preview(icons):
    """Every icon in its button, side by side, blown up to be looked at."""
    names = list(icons)
    cell = BUTTON + GAP
    sheet = [[PAPER] * (GAP + cell * len(names)) for _ in range(GAP * 2 + BUTTON)]
    for at, name in enumerate(names):
        left = GAP + at * cell
        top = GAP
        for y in range(BUTTON):
            for x in range(BUTTON):
                rim = x in (0, BUTTON - 1) or y in (0, BUTTON - 1)
                sheet[top + y][left + x] = RIM if rim else FACE
        art = icons[name]()
        if len(art) == ICON and len(art[0]) == ICON:
            dx = dy = (BUTTON - ICON) // 2
        else:
            dx, dy = TICK_LEFT, TICK_TOP
        for y, row in enumerate(art):
            for x, pixel in enumerate(row):
                if pixel[3] and 0 <= top + dy + y < len(sheet):
                    sheet[top + dy + y][left + dx + x] = pixel
    return [[pixel for pixel in row for _ in range(ZOOM)] for row in sheet for _ in range(ZOOM)]


# --- the chest, as the block entity renderer wants it -------------------------
#
# The renderer is vanilla's: three parts on a 64x64 sheet, laid out by the standard
# box unwrap. Read off ChestRenderer.createSingleBodyLayer rather than guessed --
#
#     bottom  texOffs(0, 19)  14 x 10 x 14
#     lid     texOffs(0,  0)  14 x  5 x 14
#     lock    texOffs(0,  0)   2 x  4 x  1
#
# For a box w x h x d at (u, v) the six faces land at
#
#     down  (u+d,     v)      w x d      up    (u+d+w,   v)      w x d
#     east  (u,       v+d)    d x h      north (u+d,     v+d)    w x h
#     west  (u+d+w,   v+d)    d x h      south (u+d+w+d, v+d)    w x h
#
# The layout is vanilla's because the model is; the pixels are not. Recolouring
# Mojang's chest would be shipping their art with a hue turned, which is not ours to
# ship however it is generated.
SHEET = 64

def _chunk(kind: bytes, data: bytes) -> bytes:
    return (struct.pack(">I", len(data)) + kind + data
            + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF))


def write(path: pathlib.Path, pixels) -> None:
    """The header says what the pixels are, not what SIZE happens to be.

    It said SIZE while the icon was eleven rows: a file claiming to be sixteen tall
    with eleven rows in it. Declaring a size instead of measuring one is the same
    mistake that had the chest screen drawn at 176 by 222.
    """
    height = len(pixels)
    width = len(pixels[0])
    raw = b"".join(b"\x00" + bytes(v for pixel in row for v in pixel) for row in pixels)
    png = (b"\x89PNG\r\n\x1a\n"
           + _chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
           + _chunk(b"IDAT", zlib.compress(raw, 9))
           + _chunk(b"IEND", b""))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)
    print(f"wrote {path}")


if __name__ == "__main__":
    import sys
    if len(sys.argv) > 2 and sys.argv[1] == "--preview":
        write(pathlib.Path(sys.argv[2]), preview(ICONS))
        sys.exit(0)
    for name, draw in ICONS.items():
        write(GUI / f"{name}.png", draw())
