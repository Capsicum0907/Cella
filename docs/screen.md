# The screen

English | [日本語](screen.ja.md)

Back to the [README](../README.md).

## Pages

A Cella shows one page at a time. The arrows at the top right turn them, and the number
beside the arrows says which page you are on and how many there are. The mouse wheel also
turns the page, over the panel and the lid — but never over a slot, where it belongs to
whatever you installed to use it there.

How big a page is depends on the form and on your window. By default the client fits the
page to the window, so a larger window or a smaller GUI scale shows more of the chest and
fewer pages of it. That can be turned off in the [config](config.md).

## It is always in order

The contents are kept sorted and merged at all times. Nothing has to be pressed for it,
and there is nothing to keep tidy.

⚠ Putting an item into a particular square does not leave it in that square. Which slot a
stack sits in is decided by what the stack is, so the screen is a list to take things from
rather than a grid to arrange.

The button at the top left chooses which order, and pressing it cycles them. The tooltip
says which is in force.

| Order | Groups by |
|---|---|
| item id | Registry name, so `andesite_wall` comes before `baked_potato` |
| name | What the item is called on screen |
| mod | Which mod it came from, then registry name |

⚠ **Ordering by name needs a language, and the chest is sorted on the server.** In single
player, or a world opened to LAN, that is your own game and your own language. On a
dedicated server, vanilla items come out in English and modded ones come out as
translation keys. Leave a dedicated server's chests in item id order.

The order is saved with the chest. A hopper and a redstone comparator read the same slots
as everybody else.

## Searching

From Semi-Perfect up, a magnifier at the left of the title turns that row into a search
box. The chest then shows only what matched, paged the same way.

The word is matched against the registry name and against the name on screen, and
underscores read as spaces — so `diamond sword`, `diamond_sword` and `sword` all find one.

The last page of an answer is filled out to the end of itself with spare slots, so there
is always somewhere to put something down and always a visible end to the answer. A search
that found nothing says so in words instead.

Results follow the chest. Take the last of something out and its square goes; put more of
something in and it appears where it belongs.

## The two moving buttons

Beside your own inventory, pointing the way the items go.

| Button | Click | With shift |
|---|---|---|
| ↑ | in: only kinds the chest already holds | in: everything except what is in your hand |
| ↓ | out: only kinds you are carrying | out: as much as will fit |

Both reach the whole chest, not the page on screen. Shift-clicking a stack into the chest
does too.

What is in your hand is never taken. Armour, the off hand and anything Curios keeps are
never touched.

## What it remembers

A chest writes down what goes in and out of it through its own screen: the kind, how much
there was, and how much there is. Enchantments and other components are kept, so an
enchanted book is not recorded as a book.

Hoppers and other automation are not recorded.

How many movements a chest keeps is `history` in the [config](config.md).

⚠ Nothing shows this yet. It is gathered and saved; reading it back is not built.
