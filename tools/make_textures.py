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
# than read: the shelf is low for going in and high for coming out, and that is the
# only thing they have to say. Narrowing either one to matching kinds is shift, which
# needs no picture of its own - which is why there are two of these and not four.
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


def _arrow():
    """Shaft at the top, head under it, pointing down."""
    return _row(0, 2) | _row(1, 2) | _row(2, 6) | _row(3, 4)


SHELF_LOW = _row(5, ICON)
SHELF_HIGH = _row(0, ICON)


def sort_icon():
    """Three bars, longest first, which is what a sort button looks like everywhere."""
    return _rows(_row(0, 6) | _row(2, 4) | _row(4, 2))


def stow_icon():
    """Down into the shelf: everything."""
    return _rows(SHELF_LOW | _arrow())


def take_icon():
    """Out of the shelf and down, because the chest is the half above."""
    return _rows(SHELF_HIGH | _flip(_arrow()))


def _chevron(pointing_left):
    """A two-pixel arrowhead, so the page arrows are drawn like everything else here."""
    cells = set()
    for y in range(ICON):
        reach = abs(2 * y - (ICON - 1)) // 2
        for dx in range(2):
            x = reach + dx
            cells.add((x if pointing_left else ICON - 1 - x, y))
    return cells


def prev_icon():
    return _rows(_chevron(True))


def next_icon():
    return _rows(_chevron(False))


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
