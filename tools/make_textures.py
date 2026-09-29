from __future__ import annotations

import pathlib
import struct
import zlib

SIZE = 16

ASSETS = pathlib.Path(__file__).resolve().parents[1] / "src/main/resources/assets/cella/textures"
GUI = ASSETS / "gui"

WOOD = (0x8A, 0x66, 0x3C, 0xFF)
WOOD_LIT = (0xA5, 0x7C, 0x4B, 0xFF)
WOOD_DARK = (0x6B, 0x4C, 0x2B, 0xFF)
SEAM = (0x5A, 0x3F, 0x23, 0xFF)

IRON = (0x8C, 0x8C, 0x94, 0xFF)
IRON_LIT = (0xB2, 0xB2, 0xBA, 0xFF)
IRON_DARK = (0x66, 0x66, 0x6E, 0xFF)

ICON = 6
INK = (0xEE, 0xEE, 0xEE, 0xFF)
CLEAR = (0x00, 0x00, 0x00, 0x00)

def _rows(ink):
    return [[INK if (x, y) in ink else CLEAR for x in range(ICON)] for y in range(ICON)]


def _row(y, length):
    return {((ICON - length) // 2 + dx, y) for dx in range(length)}


def _flip(cells):
    return {(x, ICON - 1 - y) for (x, y) in cells}


def _down():
    return (_row(0, 2) | _row(1, 2) | _row(2, 2)
            | _row(3, 6) | _row(4, 4) | _row(5, 2))


def sort_icon():
    return _rows(_row(0, 6) | _row(1, 6) | _row(3, 4) | _row(5, 2))


def stow_icon():
    return _rows(_flip(_down()))


def take_icon():
    return _rows(_down())


def _chevron(pointing_left):
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
TICK_TOP = 3
TICK_RIGHT = 8


def select_icon():
    corner = [(2, 5), (3, 6), (4, 7)]
    rising = [(4 + step, 7 - step) for step in range(1, 8)]
    cells = set()
    for x, y in corner + rising:
        for dy in (0, -1):
            if 0 <= x < TICK_RIGHT and 0 <= y + dy < BUTTON:
                cells.add((x - TICK_LEFT, y + dy - TICK_TOP))
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


SHEET = 64

def _chunk(kind: bytes, data: bytes) -> bytes:
    return (struct.pack(">I", len(data)) + kind + data
            + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF))


def write(path: pathlib.Path, pixels) -> None:
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
