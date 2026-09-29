# Forms

English | [日本語](forms.ja.md)

Back to the [README](../README.md).

## Capacity

| | slots | large chests | page | pages |
|---|---:|---:|---|---:|
| Larval Cella | 54 | 1 | 6×9 | 1 |
| Imperfect Cella | 216 | 4 | 6×9 | 4 |
| Semi-Perfect Cella | 1,728 | 32 | 6×16 | 18 |
| Cella Jr. | 3,456 | 64 | 12×16 | 18 |
| Perfect Cella | 13,824 | 256 | 12×16 | 72 |
| Super Perfect Cella | 55,296 | 1,024 | 12×16 | 288 |
| Cella Max | 221,184 | 4,096 | 12×16 | 1,152 |

Page shapes are the defaults; both can be turned down in the [config](config.md), and the
client fits the page to the window unless told not to.

## Recipes

**Larval Cella**

```
Raw Beef      Raw Porkchop   Raw Mutton
Pufferfish    Chest          Raw Rabbit
Raw Cod       Spider Eye     Rotten Flesh
```

**Imperfect Cella** — no recipe. Feed a Larval Cella.

**Semi-Perfect Cella** — eight Imperfect Cellas around one Obsidian.

**Perfect Cella** — eight Semi-Perfect Cellas around one Block of Gold.

**Super Perfect Cella** — no recipe. Feed a Perfect Cella and set it off in the End.

**Cella Max** — four Super Perfect Cellas in the corners, four Nether Stars on the edges,
one Totem of Undying in the middle.

**Cella Jr. ×7** — eight Blocks of Diamond around one Perfect Cella or one Super Perfect
Cella. The Cella in the middle is not consumed.

Every Cella used in a recipe must be full of experience. Contents come across into what is
made; experience does not.

Partitions come across too. Every partition of every Cella used is kept, with its name,
colour, size and contents, in the order of the crafting grid: left to right, top to bottom.

## Growing

Feed a Cella with shift and right click. It takes what it needs in one go, and experience
only ever goes into it.

| | to fill |
|---|---:|
| Larval Cella | 30 levels |
| Imperfect Cella | 30 levels |
| Semi-Perfect Cella | 50 levels |
| Perfect Cella | 100 levels |
| Super Perfect Cella | 200 levels |

A Larval Cella that fills up becomes an Imperfect Cella where it stands.

A Perfect Cella that is full can be set off with a Nether Star. It destroys a hundred
blocks in every direction and comes back Super Perfect — but only in the End. Anywhere
else it is simply gone.

The blast is not survivable. Stand well back.

## What each form survives

| | broken | blast resistance | tool | in the fire |
|---|---|---:|---|---|
| Larval Cella | spills its contents | 2.5 | axe | burns |
| Imperfect Cella | keeps its contents | 6 | iron axe | — |
| Semi-Perfect Cella | keeps its contents | 1,200 | diamond axe | — |
| Cella Jr. | keeps its contents | 1,200 | diamond pickaxe | — |
| Perfect Cella | keeps its contents | 1,200 | diamond pickaxe | — |
| Super Perfect Cella | keeps its contents | 3,600,000 | netherite pickaxe | survives |
| Cella Max | keeps its contents | 3,600,000 | netherite pickaxe | survives |

Everything from Imperfect up hands its contents back when it is broken: the item carries
the name of what was filed away, and putting it down again pours the contents back in.

Experience orbs are pulled in from a distance by everything between Imperfect and Perfect:
4 blocks for an Imperfect Cella, 8 for Semi-Perfect, Perfect and Cella Jr. A Larval Cella
and the two top forms do not.

Super Perfect Cella and Cella Max are the only ones a wither cannot break, the only ones
that are not destroyed as items — not by fire, lava, cactus, explosions or the void — and
the only ones that stay with you when you die.

Semi-Perfect and above can be searched. Larval and Imperfect cannot.
