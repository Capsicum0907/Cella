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

SIZE = 16

ASSETS = pathlib.Path(__file__).resolve().parents[1] / "src/main/resources/assets/cella/textures"
OUT = ASSETS / "block/cella.png"
SORT_OUT = ASSETS / "gui/sort.png"
STOW_OUT = ASSETS / "gui/stow.png"
MATCHING_OUT = ASSETS / "gui/matching.png"
TAKE_OUT = ASSETS / "gui/take.png"
TAKING_OUT = ASSETS / "gui/taking.png"

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


# The sort icon: three bars, longest first, which is what a sort button looks like
# everywhere else. Odd-sized so it sits in the middle of an odd-sized button - the
# same parity problem the page number had.
ICON = 11
BARS = ((1, 1, 9), (1, 4, 6), (1, 7, 3))  # x, y, length; each two pixels deep
BAR_DEPTH = 2

FACE = (0xEE, 0xEE, 0xEE, 0xFF)
SHADOW = (0x3F, 0x3F, 0x3F, 0xFF)
CLEAR = (0x00, 0x00, 0x00, 0x00)


def sort_icon() -> list[list[tuple[int, int, int, int]]]:
    """White bars over a one-pixel shadow, the way the game draws its own labels."""
    bars = {(x + dx, y + dy)
            for (x, y, length) in BARS
            for dx in range(length)
            for dy in range(BAR_DEPTH)}
    shadow = {(x + 1, y + 1) for (x, y) in bars} - bars
    return [[FACE if (x, y) in bars else SHADOW if (x, y) in shadow else CLEAR
             for x in range(ICON)]
            for y in range(ICON)]


# The stow icon: an arrow going down into a shelf. Same eleven square as the sort
# icon, drawn the same way, so the two sit together as a pair.
SHAFT = {(x, y) for x in range(4, 7) for y in range(0, 4)}
HEAD = ({(x, 4) for x in range(2, 9)}
        | {(x, 5) for x in range(3, 8)}
        | {(x, 6) for x in range(4, 7)})
SHELF = {(x, y) for x in range(0, 11) for y in range(8, 10)}


def stow_icon() -> list[list[tuple[int, int, int, int]]]:
    """Down into a shelf: what the button does, in the direction it does it."""
    ink = SHAFT | HEAD | SHELF
    shadow = {(x + 1, y + 1) for (x, y) in ink} - ink
    return [[FACE if (x, y) in ink else SHADOW if (x, y) in shadow else CLEAR
             for x in range(ICON)]
            for y in range(ICON)]


# The matching icon: the same shelf, a narrower arrow, and one item standing beside
# it - "put in the ones that are like this". Drawn from the stow icon's parts so the
# three buttons read as a family.
NARROW_SHAFT = {(x, y) for x in range(3, 6) for y in range(0, 3)}
NARROW_HEAD = ({(x, 3) for x in range(1, 8)}
               | {(x, 4) for x in range(2, 7)}
               | {(x, 5) for x in range(3, 6)})
SAMPLE = ({(x, y) for x in range(7, 11) for y in (2, 5)}
          | {(x, y) for x in (7, 10) for y in range(2, 6)})


def matching_icon() -> list[list[tuple[int, int, int, int]]]:
    """One kind of thing, and the arrow that sends its like into the shelf."""
    ink = NARROW_SHAFT | NARROW_HEAD | SAMPLE | SHELF
    shadow = {(x + 1, y + 1) for (x, y) in ink} - ink
    return [[FACE if (x, y) in ink else SHADOW if (x, y) in shadow else CLEAR
             for x in range(ICON)]
            for y in range(ICON)]


# The two that come the other way. On this screen the chest is above and the player
# below, so out of the chest is downwards: the shelf goes to the top and the arrow
# leaves it. Built from the same parts moved, rather than drawn again, so the four
# icons cannot drift apart.
def _fall(cells, by):
    return {(x, y + by) for (x, y) in cells}


SHELF_TOP = {(x, y) for x in range(0, 11) for y in (0, 1)}


def take_icon() -> list[list[tuple[int, int, int, int]]]:
    """Out of the shelf and down: everything, the way stow is everything."""
    ink = SHELF_TOP | _fall(SHAFT, 3) | _fall(HEAD, 4)
    return _drawn(ink)


def taking_icon() -> list[list[tuple[int, int, int, int]]]:
    """The same, for the kinds the player already carries."""
    ink = SHELF_TOP | _fall(NARROW_SHAFT, 4) | _fall(NARROW_HEAD, 5) | _fall(SAMPLE, 3)
    return _drawn(ink)


def _drawn(ink) -> list[list[tuple[int, int, int, int]]]:
    """Ink, its one-pixel shadow, and nothing else - shared by all four icons."""
    shadow = {(x + 1, y + 1) for (x, y) in ink} - ink
    return [[FACE if (x, y) in ink else SHADOW if (x, y) in shadow else CLEAR
             for x in range(ICON)]
            for y in range(ICON)]


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
    write(MATCHING_OUT, matching_icon())
    write(TAKE_OUT, take_icon())
    write(TAKING_OUT, taking_icon())
