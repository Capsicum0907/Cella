#!/usr/bin/env python3
"""Draw the Cella block texture.

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
OUT = ASSETS / "block/cella.png"
SORT_OUT = ASSETS / "gui/sort.png"
STOW_OUT = ASSETS / "gui/stow.png"
TAKE_OUT = ASSETS / "gui/take.png"
PREV_OUT = ASSETS / "gui/prev.png"
NEXT_OUT = ASSETS / "gui/next.png"
CHEST_OUT = ASSETS / "entity/chest/cella.png"

# One place for every colour. Body, and the two derived from it.
WOOD = (0x8A, 0x66, 0x3C, 0xFF)
WOOD_LIT = (0xA5, 0x7C, 0x4B, 0xFF)
WOOD_DARK = (0x6B, 0x4C, 0x2B, 0xFF)
SEAM = (0x5A, 0x3F, 0x23, 0xFF)

IRON = (0x8C, 0x8C, 0x94, 0xFF)
IRON_LIT = (0xB2, 0xB2, 0xBA, 0xFF)
IRON_DARK = (0x66, 0x66, 0x6E, 0xFF)

# Where the boards are divided. Vertical seams, offset per band so the crate does
# not read as one flat plank.
SEAMS = {2: (5, 11), 8: (3, 9, 13)}
BAND_ROWS = {3, 4, 11, 12}
LATCH = {(x, y) for x in range(7, 9) for y in range(10, 14)}


def _band(x: int, y: int) -> bool:
    return y in BAND_ROWS or (x, y) in LATCH


def _seam(x: int, y: int) -> bool:
    if _band(x, y):
        return False
    for start, columns in SEAMS.items():
        if start <= y < start + 6 and x in columns:
            return True
    return y in (0, 15)


def _shade(x: int, y: int, same, body, lit, dark):
    """Light where nothing of the same material is above-left, shadow below-right."""
    above = same(x, y - 1) if y > 0 else False
    left = same(x - 1, y) if x > 0 else False
    below = same(x, y + 1) if y < SIZE - 1 else False
    right = same(x + 1, y) if x < SIZE - 1 else False
    if not (above and left):
        return lit
    if not (below and right):
        return dark
    return body


def crate() -> list[list[tuple[int, int, int, int]]]:
    rows = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            if _band(x, y):
                row.append(_shade(x, y, _band, IRON, IRON_LIT, IRON_DARK))
            elif _seam(x, y):
                row.append(SEAM)
            else:
                row.append(_shade(x, y, lambda a, b: not _band(a, b) and not _seam(a, b),
                                  WOOD, WOOD_LIT, WOOD_DARK))
        rows.append(row)
    return rows


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

# Red, because a chest that is not a vanilla chest should not read as one from across
# a room.
#
# The *structure* is vanilla's and was read off its own file rather than guessed at.
# What that file is, counted: no bands and no clean lines. Each face is a wash of six
# or so shades a step apart, scattered pixel by pixel; every fourth row leans darker,
# which is where a board meets the next; and every face carries a one-pixel near-black
# edge. The lock is four shades of grey and hardly any of them.
#
# The first version here drew tidy bands every four pixels, which is what a person
# assumes wood looks like and is nothing like what is actually in the file. Beside a
# real chest it read as a striped box.
#
# The shades are ours and the arrangement is theirs. Reading how a texture is built is
# not the same as shipping it.
BODY = (
    (0xC4, 0x4E, 0x44, 0xFF),
    (0xB8, 0x43, 0x3A, 0xFF),
    (0xAE, 0x3C, 0x33, 0xFF),
    (0xA6, 0x36, 0x2E, 0xFF),
    (0x9C, 0x30, 0x29, 0xFF),
    (0x92, 0x2B, 0x24, 0xFF),
)
#: The shades a board takes where it meets the next one. Named apart from the crate's
#: single SEAM colour above, which is a different picture's idea of the same word.
JOINT = (
    (0x86, 0x25, 0x1F, 0xFF),
    (0x7C, 0x21, 0x1B, 0xFF),
    (0x72, 0x1D, 0x18, 0xFF),
)
EDGE = (0x33, 0x14, 0x10, 0xFF)
NOTHING = (0x00, 0x00, 0x00, 0x00)

LATCH = (0x8C, 0x8C, 0x94, 0xFF)
LATCH_LIT = (0xC2, 0xC2, 0xCA, 0xFF)
LATCH_DARK = (0x5C, 0x5C, 0x64, 0xFF)

#: How often a board meets the next one, counted off vanilla's own faces.
BOARD_EVERY = 4


def _scatter(x, y, choices):
    """A shade for this pixel, the same one every time this runs.

    Deliberately not random: the file has to come out identical from one run to the
    next or every regeneration is a diff. A hash of the position gives the disorder
    without the irreproducibility.
    """
    mixed = (x * 73_856_093) ^ (y * 19_349_663)
    mixed ^= mixed >> 13
    return choices[(mixed & 0x7FFFFFFF) % len(choices)]


def _faces(u, v, w, h, d):
    """Where the six faces of a box land, as (x, y, width, height, is_lengthwise)."""
    return [
        (u + d, v, w, d, True),           # down
        (u + d + w, v, w, d, True),       # up
        (u, v + d, d, h, False),          # east
        (u + d, v + d, w, h, False),      # north
        (u + d + w, v + d, d, h, False),  # west
        (u + d + w + d, v + d, w, h, False),  # south
    ]


def _board(sheet, x, y, w, h, across):
    """A face: near-black all the way round, a wash inside, darker where boards meet.

    `across` turns the boards a quarter: the faces you look down at have them running
    the length of the box, the ones you look at have them stacked.
    """
    for dy in range(h):
        for dx in range(w):
            if dx == 0 or dx == w - 1 or dy == 0 or dy == h - 1:
                sheet[y + dy][x + dx] = EDGE
                continue
            along = dx if across else dy
            joint = along % BOARD_EVERY == BOARD_EVERY - 1
            sheet[y + dy][x + dx] = _scatter(x + dx, y + dy, JOINT if joint else BODY)


def _metal(sheet, x, y, w, h):
    for dy in range(h):
        for dx in range(w):
            if dy == 0 or dx == 0:
                colour = LATCH_LIT
            elif dy == h - 1 or dx == w - 1:
                colour = LATCH_DARK
            else:
                colour = LATCH
            sheet[y + dy][x + dx] = colour


def chest_texture():
    sheet = [[NOTHING for _ in range(SHEET)] for _ in range(SHEET)]

    # The lid, then the bottom. Same treatment: the top and bottom faces get grooves
    # running the length of the boards, the sides get them across.
    for u, v, w, h, d in ((0, 0, 14, 5, 14), (0, 19, 14, 10, 14)):
        for x, y, fw, fh, lengthwise in _faces(u, v, w, h, d):
            _board(sheet, x, y, fw, fh, across=lengthwise)

    # The lock last: it sits in the corner of the sheet the lid's faces leave empty.
    for x, y, fw, fh, _ in _faces(0, 0, 2, 4, 1):
        _metal(sheet, x, y, fw, fh)
    return sheet


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
    write(OUT, crate())
    write(SORT_OUT, sort_icon())
    write(STOW_OUT, stow_icon())
    write(TAKE_OUT, take_icon())
    write(PREV_OUT, prev_icon())
    write(NEXT_OUT, next_icon())
    write(CHEST_OUT, chest_texture())
