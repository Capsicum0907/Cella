# Config

English | [日本語](config.ja.md)

Back to the [README](../README.md).

## Server

`config/cella-server.toml`, kept with the world.

| Setting | Default | Range | |
|---|---:|---|---|
| `history` | 4096 | 0 – 1048576 | How many movements a chest remembers. 0 turns it off |

Then one section per form — `[larval]`, `[imperfect]`, `[semi_perfect]`, `[perfect]`,
`[junior]`, `[super_perfect]`, `[max]` — each with:

| Setting | Default | Range | |
|---|---|---|---|
| `rows` | 6 or 12 | 1 – 12 | Rows on one page |
| `columns` | 9 or 16 | 1 – 18 | Columns on one page |

Defaults are 6×9 for Larval and Imperfect, 6×16 for Semi-Perfect, and 12×16 for the rest.

⚠ A chest keeps the size it was built at. Turning these down does not shrink chests that
already exist; it changes how many slots the next one of that form is made with.

The panel is 114 pixels plus 18 a row, and 14 pixels plus 18 a column. Twelve rows is 330
and wants about 640×360 of GUI — a 1080p screen at scale 3. Sixteen columns is 302, which
still fits the 320 that Minecraft's automatic GUI scale leaves at its narrowest; eighteen
would be 338 and would not.

## Client

`config/cella-client.toml`.

| Setting | Default | Range | |
|---|---|---|---|
| `page.automatic` | true | | Fit the page to the window, every time it changes |
| `page.rows` | 12 | 1 – 64 | Rows to allow when `automatic` is false |
| `page.columns` | 16 | 1 – 64 | Columns to allow when `automatic` is false |

These are a ceiling, not a shape: a chest is never cut into a bigger page than its form is
meant to have, only a smaller one.
