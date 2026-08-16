# Cella

A chest with more than one page.

*Cella* is Latin for a storeroom, and also a compartment inside one.

> **Status: the chest works.** Twelve game tests, watched in a client.

## Target

| | |
|---|---|
| Minecraft | 1.21.1 |
| Loader | NeoForge 21.1.248 |
| Java | 21 |

1.21.1 is the version large tech mods stayed on, so it is where this mod is useful.

## Design

A chest whose contents are divided into pages, one shown at a time.

**Every slot is in the menu. What a page decides is what gets drawn.** The slots are
`SlotItemHandler`s over the whole contents, numbered as the contents are, all of a
page sitting at the same coordinates as all of every other page. A slot answers
`isActive()` with whether its page is the one on show, and the screen asks that
before it draws a slot, before it calls one hovered and before it works out which
one the mouse is in. Nothing else is needed to hide the rest.

Three things follow, and none of them had to be arranged:

- **Other mods see the whole chest.** A sorting mod works on the slots the menu has,
  and that is every slot. It does not need to know this mod exists.
- **A page turn sends nothing.** `isActive` appears nowhere in
  `AbstractContainerMenu` and the server's click path never consults it, so which
  page is on show is a client-side fact with no packet to its name.
- **Slot *i* is contents *i*, always.** The game decides what to send a client by
  comparing each slot with what it last said that slot held — sound exactly when
  nothing moves underneath a slot.

### The road not taken

The first version did the opposite: fifty-four fixed slots with a *window* sliding
underneath them, reading slot *i* as *page × size + i*. It works, and it has one real
advantage — the menu stays one page wide however big the chest is, so nothing grows
with the number of pages.

It was abandoned for what falls out of it. A sorting mod could only ever reach the
page on screen, because a page was all the menu had; so this mod had to grow its own
sort and stow buttons to do what an installed mod was already trying to do. And the
game's "has this slot changed" test became a lie: two pages holding the same thing in
the same place sent nothing, and the client — which had only been told about pages it
had looked at — drew a hole. That needed a full resend on every page turn to paper
over.

The idea of keeping every slot comes from Expanded Storage, which hid the pages that
were not on show by moving their slots two thousand pixels off screen. Asking
`isActive` is the same thought without the coordinates having to lie.

The price is real and is not hidden: the menu is as big as the chest, so opening one
sends every stack and each tick walks every slot. `pages` is capped at 32 for that
reason.

### What the paging does *not* touch

- **What a hopper or a pipe is offered** is the whole contents. Which page somebody
  has open is not a fact about the chest.
- **Shift-click fills the whole chest**, which is now just the ordinary vanilla move
  over the menu's own slots.
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
set — sort, and four ways of moving:

| Button | What it moves |
|---|---|
| Put your whole inventory in | everything except what is in hand |
| Put in what this chest already keeps | matched against the chest |
| Take more of what you are carrying | matched against the player, hand included |
| Take as much as will fit | stops at the first stack that will not fit |

Sorting is ordered by registry name, not display name: a display name needs a
language, and a chest that came out differently depending on who pressed the button
would not be a sort.

**Expanded Storage did not solve this — it avoided it.** Its screen picks the layout
with the fewest pages, going to 9×9 or 15×6 where it has to, so a 135-slot chest is
shown all at once and IPN's rule never bites. Growing the page is the only thing that
would make an outside sorter work here, and it costs drawing our own background: the
vanilla chest picture is six rows tall and no more.

### The size travels with the screen

A chest keeps the size it was built with, so any world whose config has been turned
down since holds chests bigger than the config says. The client cannot work that out
— its own copy of the block entity was made at the config's size — so the number is
written into the packet that opens the screen. This mattered less when the menu was
one page wide; now a client whose menu is shorter than what the server sends walks
off the end of its own list.

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
