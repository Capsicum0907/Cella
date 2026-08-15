# Cella

A chest with more than one page.

*Cella* is Latin for a storeroom, and also a compartment inside one.

> **Status: scaffold only.** The mod loads and does nothing.

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
has, so there is nothing to send of our own.

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
- [ ] **1** — the feature above, in a form that can be watched
- [ ] **2** — checked by game tests rather than by eye

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
