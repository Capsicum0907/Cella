# Cella

A chest with more than one page.

*Cella* is Latin for a storeroom, and also a compartment inside one.

> **Status: the chest works.** Six game tests, no client-side testing yet.

## Target

| | |
|---|---|
| Minecraft | 1.21.1 |
| Loader | NeoForge 21.1.248 |
| Java | 21 |

1.21.1 is the version large tech mods stayed on, so it is where this mod is useful.

## Design

A chest whose contents are divided into pages, one shown at a time.

**The page belongs to the view, not to the slot.** A slot cannot be told to point
somewhere else: vanilla's `Slot` reads a private field directly in `getItem`,
`set`, `remove` and `mayPlace`, and `getSlotIndex()` is not consulted by any of
them. NeoForge's `SlotItemHandler` reads its own index the same way.

So the whole of the paging lives one layer down. The chest's contents are a single
handler holding every page; what the screen is given is a window onto it that shows
one page's worth of slots and reads slot *i* as *page × size + i*. The slot numbers
never change — only what is behind them — so the ordinary slot class is used
unmodified.

Two consequences fall out of that rather than being arranged:

- The window belongs to one open screen, so two people can have the same chest open
  on different pages.
- What the block offers to hoppers and pipes is the whole handler, not the window.
  A hopper being restricted to whichever page somebody happens to be looking at
  would be nonsense.

Turning the page rides on `clickMenuButton`, which is a packet the game already
has, so there is nothing to send of our own. The button id *is* the page wanted
rather than "next" or "previous", because a difference would need both sides to
agree about where they already were, and only the client knows that.

That packet runs on the server only, so the screen turns its own window as well as
sending. Otherwise it would sit on the old page until something else forced a
refresh.

**A page turn resends every slot.** The game works out what to send by comparing
each slot with what it last told the client that slot held, which is right as long
as a slot only changes when something is put in it. Here the slot stays still and
the storage under it moves, so two pages that happen to hold the same thing in the
same place send nothing — and the client, which has only ever been told about pages
it has looked at, draws that part of the new page empty. A whole page is a chest's
worth of packet, which is what opening a chest costs anyway.

### What the paging does *not* touch

- **Shift-click fills the whole chest.** An item can land on a page that is not on
  screen. Same answer as the hopper gets, for the same reason: which page somebody
  has open is not a fact about the chest.
- **Sorting is over every page.** A sorting mod works on the slots the open screen
  has, which is one page — right for a chest, wrong for this one, and nothing
  outside can do better because nothing outside can see past the window. Hence the
  third button. Ordered by registry name, not display name: a display name needs a
  language, and a chest that came out differently depending on who pressed the
  button would not be a sort.
- **Stowing puts the player's inventory into the whole chest**, minus whatever is in
  their hand. Same reason as sorting: the version a sorting mod offers can only fill
  the page on show and hands the rest back.

### Why sorting mods only reach one page

Because that is all this screen has. The menu holds one page of slots and the rest
is behind the window, and a mod working on somebody else's container has nothing to
work on but its slots.

The other way round exists: put *every* slot in the menu and page in the drawing
instead, hiding the rows that are not on show. Then an outside sorter sees the whole
chest for free. It costs a menu that grows with the chest rather than staying one
page wide, and a screen that has to filter what it draws and what it lets you click.
This mod took the first road, which is why it brings its own buttons.
- **A comparator reads the whole chest**, including pages nobody has open.
- **The screen only ever holds one page's worth of slots**, so how much is sent when
  something changes does not grow with the number of pages. A chest of eight pages
  costs a chest to keep in sync.

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
