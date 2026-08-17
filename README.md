# Cella

A chest with more than one page.

*Cella* is Latin for a storeroom, and also a compartment inside one.

> **Status: the chest works.** Seventeen game tests, watched in a client.

## Target

| | |
|---|---|
| Minecraft | 1.21.1 |
| Loader | NeoForge 21.1.248 |
| Java | 21 |

1.21.1 is the version large tech mods stayed on, so it is where this mod is useful.

## Design

A chest whose contents are divided into pages, one shown at a time.

**The menu holds one page. What moves is the page underneath it.** The chest's slots
are `SlotItemHandler`s over a `Window` — a handler that offers one page of the
contents as though it were a container of that size — so a slot is numbered nought
upwards into the window and knows nothing about paging at all. Turning a page moves
where the window starts. Nothing else is told, because nothing else was ever asked.

That layer is not decoration. The obvious version is a slot that works out its own
index from the page each time; it does not survive contact with `SlotItemHandler`,
which keeps its index in a `protected final` field and reads that field directly in
all seven of its methods rather than through a getter. A slot with a moving index has
to override every one of them, and the eighth that gets added upstream is a bug nobody
writes. Putting the page *underneath* the slot leaves the slot with nothing to
remember.

Three things follow:

- **An open screen costs a page**, whatever the chest is. Opening one sends a page and
  each tick compares a page. That is what makes a chest of thirteen thousand slots
  possible at all.
- **The client holds a page too.** It is only ever sent the one it is looking at, so
  that is all it keeps — a client-side copy of Cella Max would be two hundred thousand
  slots of nothing. `Window.onto` is the server's shape and `Window.of` is the
  client's; everything above the class is written once.
- **Slot *i* is not contents *i*** — and the game assumes it is. It decides what to
  send by comparing each slot against what it last told the client that slot held,
  which is sound only while nothing moves underneath a slot. Something does, exactly
  once per page turn, and `sendAllDataToRemote()` is vanilla's own way of saying
  "forget what you were told about these". It carries the page number with the
  contents, as a data slot.

**A page turn is a round trip, and the screen waits for it.** Turning at once and being
corrected was written and thrown away: what is on screen has to be what a click will act
on, and a click acts on the server's page. A screen that turns early is a screen showing
one page while the server would answer about another — which is this mod's recurring
defect, in its third design running. Shift-click into the chest waits for the same
reason: the client cannot guess where a stack lands when it does not have the chest, and
the case the button exists for is exactly the one where this page is full.

### The road not taken

Written three times, and the third is the second one again — so it is worth saying why
the middle one was left rather than leaving it looking like a circle.

**The second design put every slot of the chest in the menu**, every page's slots at
the same coordinates, with `isActive` deciding which were drawn. It was bought for one
thing: a sorting mod works on the slots the menu has, so a menu holding all of them
could be sorted whole from outside.

That turned out to be false. Inventory Profiles Next drops any slot that answers
`isActive` with false — which is every page but one, however many are in the menu.
Nothing was bought. The price was paid in full: every slot sent on opening, every slot
compared every tick, and `pages` capped because of it.

Two real defects came out of the same design. Pages stacked at one set of coordinates
meant that anything working out which slot the mouse was over from *where* it was found
eight candidates in one square, so shift-clicking an empty slot fetched an item from
another page. Moving the off-page slots a screen-height away fixed it — that is what
Expanded Storage does, and it is not laziness — and cost an **access transformer**,
because `Slot.x` and `Slot.y` are `public final`. With one page in the menu there is one
slot per square, and the transformer is gone.

What the window costs is the round trip above. The first attempt at a window had a hole
in it and this one does not: nothing then said "forget what you were told" after a page
turn, so two pages holding the same thing in the same place sent nothing and the client
drew whatever it last saw there. It was patched with a full resend, which was written
off at the time as papering over. It was not — it is the correct design, and it has a
name in `AbstractContainerMenu`.

The version before this one turned the page on the client at once and asked the server
afterwards, to keep the arrow feeling instant. That is wrong twice over. A click in the
gap is read against the server's page and takes an item other than the one under the
pointer; and the reply, which arrives as contents first and page number second, lands in
whatever page the client has since guessed its way to — so scrolling the wheel fast left
pages holding each other's items. Both go away by not guessing.

### What the paging does *not* touch

- **What a hopper or a pipe is offered** is the whole contents. Which page somebody
  has open is not a fact about the chest.
- **Shift-click fills the whole chest.** Out of the chest is the ordinary vanilla move,
  since every one of the player's slots is in the menu. Into it is not and cannot be —
  the answer to "this page is full" has to be the next page rather than your hand — so
  that direction goes to the contents underneath.
- **A comparator reads the whole chest**, including pages nobody has open.
- **Sorting and moving are over every page**, and this mod does them itself.

### It does its own moving, and asks IPN for the player's half only

Inventory Profiles Next will not touch a slot the player cannot see: `AreaTypes.kt`
drops anything failing `isActive` or sitting at a negative coordinate. That is a
defensible rule and there is nothing in its API to lift it, so on a paged screen its
container buttons move and sort one page while looking like they move and sort a
chest. That is worse than not having them.

So `CellaScreen` carries `@IPNPlayerSideOnly`. IPN keeps the player's half, which it
can see all of, and leaves this one alone. In exchange this screen brings the whole
set — sort, and two ways of moving:

| Button | Click | With shift |
|---|---|---|
| ↑ | in: only kinds the chest already keeps | in: everything except what is in hand |
| ↓ | out: only kinds you are carrying | out: as much as will fit |

Shift is the wide one, the way this game already uses it — shift-click moves the
stack rather than the item. It also leaves the careful answer on the plain click,
which is the right way round for a button that can empty a pack. The arrows point the
way the items go: these two sit beside the player's inventory, and the chest is the
half above them.

The wheel turns the page as well, over the frame — the lid, the margins, the strip
above the inventory — but never over a slot. The wheel above a slot belongs to
whatever the player installed to use it there, and a chest that ate that gesture
would be a chest that broke their mouse.

Two buttons, not four. Four meant two things to tell apart at once — direction and
reach — and six pixels of picture will carry one. Reach is shift, which in this game
already means "the same thing, done the other way".

**Nothing worn is ever moved.** `Inventory.INVENTORY_SIZE` is the pack and the hotbar;
armour and the off hand are separate compartments, and whatever Curios keeps is not in
`Inventory` at all. That is structural, not careful.

Sorting is ordered by registry name, not display name: a display name needs a
language, and a chest that came out differently depending on who pressed the button
would not be a sort.

Where those two buttons sit is the one thing that looks at what else is installed:
right-hand end like everything else here, or after the "Inventory" label when IPN is
present, because IPN's own player-side buttons are in that corner. Two mods in one
corner is what started all of this.

**Expanded Storage did not solve this — it avoided it.** Its screen picks the layout
with the fewest pages, going to 9×9 or 15×6 where it has to, so a 135-slot chest is
shown all at once and IPN's rule never bites. Growing the page is the only thing that
would make an outside sorter work here, and it costs drawing our own background: the
vanilla chest picture is six rows tall and no more.

### The size travels with the screen

A chest keeps the size it was built with, so any world whose config has been turned
down since holds chests bigger than the config says. The client cannot work that out
— its own copy of the block entity was made at the config's size — so the number is
written into the packet that opens the screen. What it decides is how many pages there
are, and the two sides have to agree on that: a client that thinks there are fewer
cannot reach the last one, and one that thinks there are more can ask for a page the
server refuses and then sit waiting for an answer that is not coming.

### There is a list of kinds, and it is the only one

Seven of them: Laravel, Imperfect, Semi-Perfect, Perfect, Super Perfect, Cella Max,
and Cella Jr. off the side of Perfect. Capacity climbs with the form, and a page gets
wider as well as taller towards the top.

**Capacity is what went into the chest.** A large chest is fifty-four slots, Imperfect is
four of them, and each step multiplies by what its recipe eats.

| | large chests | slots | page | pages |
|---|---|---|---|---|
| Laravel | ½ | 27 | 3×9 | 1 |
| Imperfect | 4 | 216 | 6×9 | 4 |
| Semi-Perfect | 32 | 1,728 | 6×9 | 32 |
| Perfect | 256 | 13,824 | 6×9 | 256 |
| Cella Jr. | 64 | 3,456 | 6×9 | 64 |
| Super Perfect | 1,024 | 55,296 | 6×12 | 768 |
| Cella Max | 4,096 | 221,184 | 6×15 | 2,458 |

**The column in `Kind` is the capacity and the pages follow from it**, which is the way
round it was not written first. How big a chest is is a fact about the chest; how it is
cut into pages is a fact about looking at it. Writing the pages down made the second
decide the first, and the two do not even divide: Cella Max's last page holds fifty-four
of its ninety squares. That is fine and it is drawn honestly. Bending the capacity so
the pages come out round would not be.

**Multiplying by what a recipe eats is addition, not fusion**, and that is the part left
open. Four put together giving four times the room is arithmetic; whatever a form is
worth beyond that is not room. Nothing here buys anything but room yet.

Two consequences of the top of the ladder, neither solved: **2,458 pages cannot be
reached with two arrows**, and the sort and the two movers read the whole chest once per
press, which at 221,184 slots is a real amount of work for one keystroke.

**The Perfect that makes Cella Jr. is not spent.** Seven come out and the parent is
still standing there, which is what happened. That is a property of the one recipe and
not of the block: a Perfect that came back every time it was crafted with — the bucket
rule — would make Super Perfect free, since that one eats four.

**Imperfect is not crafted.** A Laravel that has been fed enough becomes one, so a kind
is allowed to have no recipe at all. The rest carry theirs as data — the pattern and
what the letters mean — because no two of the shapes are alike: nine animals for the
first, eight of the form before round a block of gold, four of the form before and four
nether stars round a dragon egg.

`Kind` is an enum, and every column in it is something that differs between one chest
and the next: the id, the name it is called, how many rows and pages it is made with,
the one colour its texture is derived from, and the ingredient in the middle of its
recipe. Adding a chest is a line there. Registration, the block entity's size, the
renderer's sheet, the recipe, the language file, the config section and the texture
itself are all read from it.

**A page is as wide as its kind says**, not always nine. The player's own inventory
stays nine and sits in the middle of whatever the chest is. That means the vanilla
chest picture cannot be the background — it is nine wide, and a fifteen-wide panel
cannot be cut out of it however it is sliced — so the panel is drawn (a filled
rectangle with a raised edge) and the only thing still taken from that file is the
sunken frame a slot sits in, blitted once per slot wherever the menu put it. The
screen reads the slots off the menu rather than counting them out again, which is
what keeps the picture and the clicking from disagreeing.

**A frame is drawn where there is a slot, and nowhere else.** The last page of a chest
built to older numbers is a short one, and the squares past its end used to be drawn
anyway so the grid came out rectangular. The comment defending that said *an empty
frame is what an empty slot looks like* — which is the reason not to draw one. Forty-five
squares that cannot be hovered, clicked or filled, drawn exactly like forty-five that
can, is the screen lying about what is there. Bare panel is what nothing looks like.

**The texture is generated in Java for that reason.** It used to be drawn by a script
under `tools/`, which would have put each kind's colour in one language and everything
else about it in another — two lists to keep in step. `ChestSheets` writes the sheets
at data generation, `ChestAtlas` writes the atlas entry that puts them on the chest
sheet, and what is left in `tools/` is the pictures that belong to no kind.

One block entity type serves them all, and one menu. A type per kind would buy
nothing: the block entity behaves the same whatever it is in, and asks the block it
sits in how big it should be.

### It is drawn as a chest, and the lid opens

A block entity renderer, not a model: the three parts come from
`ModelLayers.CHEST`, so the shape and its unwrap are vanilla's and correct. The sheet
is ours, added to the chest atlas by `assets/minecraft/atlases/chests.json` — that
path is merged across packs rather than overridden, so vanilla's chests are
untouched. Recolouring Mojang's own chest texture would have been easier and would
also have been shipping their art with its hue turned; the rects are read off
`ChestRenderer`, the pixels are drawn here, and they are red.

**The lid counts openers rather than holding a flag.** Two players open it, one walks
away — a flag shuts the lid in the other one's face, and plays the sound twice each
way. `ContainerOpenersCounter` is what that is for, `blockEvent` carries the count to
everyone watching, and the server rechecks periodically because a player can stop
having a chest open without saying so: dying, a portal, a lost connection.

### The contents drop

Broken, it spills onto the floor the way a chest does — rather than carrying its
contents on the item the way a shulker box does. Both work; this one is chosen
because a chest holds an amount a floor can take, and because contents kept in an
item component are shared by every item in a stack, which is a whole family of
duplication bugs this mod then cannot have.

### Size is decided when the chest is made

`rows` and `pages` are server config. A chest that already exists keeps the size it
was built with: `ItemStackHandler` restores the `Size` it saved, so turning the
numbers down never reaches back into a world and throws away the pages that would
no longer fit.

## Build

```
run.bat                   # compile and launch a dev client - double-clickable
gradlew build             # produce the jar
gradlew runGameTestServer # run every game test, headless, then exit
gradlew runData           # regenerate models, recipes and language
```

`JAVA_HOME` must point at a JDK 21, or `java` must be on `PATH`.

## Roadmap

- [x] **0** — scaffold; the mod loads
- [x] **1** — the feature above, in a form that can be watched
- [x] **2** — checked by game tests rather than by eye
- [x] **3** — watched in a client

## Related

One of a set of small, independent mods, each doing one thing and depending on
none of the others: [Fodina](https://github.com/Capsicum0907/Fodina),
[Trivium](https://github.com/Capsicum0907/Trivium),
[Magnes](https://github.com/Capsicum0907/Magnes),
[Cella](https://github.com/Capsicum0907/Cella),
[Acervus](https://github.com/Capsicum0907/Acervus),
[Fornax](https://github.com/Capsicum0907/Fornax),
[Accumulator](https://github.com/Capsicum0907/Accumulator).

## License

Not decided yet. Until it is, the metadata says All Rights Reserved.
