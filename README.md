# Cella

A chest with more than one page.

*Cella* is Latin for a storeroom, and also a compartment inside one.

> **Status: the ladder works, end to end.** Forty-eight game tests, watched in a client.
>
> A Cella is fed the experience you fought for, grows into the next form, and the last
> step is not a recipe at all: a Perfect that has taken in all it can use, ended in the
> End, comes back Super Perfect standing in a crater a hundred blocks across.

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

**The sort button chooses the order rather than applying it.** The chest is already in
order — what is left to decide is which one, and pressing cycles them. The tooltip names
the one in force.

| Order | Groups by | Needs |
|---|---|---|
| item id | registry name — `andesite_wall`, then `baked_potato` | nothing |
| name | what it is called on screen | a language, see below |
| mod | namespace first, registry name within it | nothing |

Registry name is where a chest starts, because it is the one that is always available and
always the same. ⚠ **It is also the one that is not readable**: `andesite_wall` is followed
by `baked_potato`, so the screen shows Andesite Wall followed by whatever the pack calls a
baked potato, with nothing connecting them. That is what ordering by the name on screen is
for.

⚠ **Ordering by name needs a language, and the chest is sorted on the server.** An
integrated server — single player, or a world opened to LAN — is the player's own game and
has their language, so it is exactly right there. A dedicated server has only what it
loaded: vanilla items come out in English, modded ones come out as translation keys. It
stays deterministic and identical for everyone on that server; it is simply not in
anybody's language. Leave a dedicated server's chests in item id order.

The order is saved with the chest, not with whoever is looking: a hopper and a comparator
read the same slots as everybody else.

### It is in order all the time, not when asked

**A Cella has too many squares for a square to mean anything.** Perfect is 13,824 of them
and Max is 221,184. Nobody remembers that the coal lives at 9,310, and nobody can put it
back there after taking some out — a place carries meaning only while you can hold the
whole of it in your head, and these passed that a long way back. So the arrangement is the
chest's job, not yours, and what you decide moves up a layer: which chest a thing goes in,
rather than which square of it.

Contents are packed to the front in that order at all times. A kind is a run of full
stacks with at most one partial at the end of it; an item arriving pours into that
partial, and an item leaving comes out of it. Nothing before it moves. A kind that was not
there yet takes a slot of its own and everything after it shifts along.

⚠ **Putting something into a square does not leave it at that square.** There is nowhere
to leave it — the slot a stack belongs at is decided by what the stack is. The screen
stops being a grid you arrange and becomes a list you take from. A collection you want to
lay out by hand is a different thing from a store, and wants a different mod.

⚠ **How full it says it is changed with this.** Three hundred loose stone used to be three
hundred slots spoken for and are now five, because staying in order means staying merged.
The bar is reporting the chest rather than the history of how things went into it.

The sort button remains, and is now a repair rather than the only time it happens: it also
runs when a chest is loaded from a save written before this, and after anything that
rewrites most of the slots at once — a fusion pouring eight chests in, the two moving
buttons, breaking one open.

Where those two buttons sit is the one thing that looks at what else is installed:
right-hand end like everything else here, or after the "Inventory" label when IPN is
present, because IPN's own player-side buttons are in that corner. Two mods in one
corner is what started all of this.

**Expanded Storage did not solve this — it avoided it.** Its screen picks the layout
with the fewest pages, going to 9×9 or 15×6 where it has to, so a 135-slot chest is
shown all at once and IPN's rule never bites. Growing the page is the only thing that
would make an outside sorter work here, and it costs drawing our own background: the
vanilla chest picture is six rows tall and no more.

### Jade is left to struggle, and that is the answer

Jade summarises a container in its tooltip: at most 54 kinds, ten thousand slots to a
pass, resumed across ticks, showing *Collecting items…* until it has been all the way
round. A Cella Max is 221,184 slots, so that is some twenty-three passes. It is bounded
and it yields, so nothing hangs — it simply never quite catches up.

**Left alone rather than hidden from.** A chest that cannot be summed up at a glance is
telling the truth about itself, and the tooltip failing to finish says something a
finished one could not. The cost is real and proportional to capacity; it is also the
first place the top of the ladder shows through from outside this mod, which is worth
knowing rather than papering over.

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
and Cella Jr. off the side of Perfect. Capacity climbs with the form, and the page gets
both wider and taller once past Semi-Perfect.

**Capacity is what went into the chest.** A large chest is fifty-four slots, Imperfect is
four of them, and each step multiplies by what its recipe eats.

| | large chests | slots | page | pages | experience |
|---|---|---|---|---|---|
| Laravel | 1 | 54 | 6×9 | 1 | 30 levels |
| Imperfect | 4 | 216 | 6×9 | 4 | 30 levels |
| Semi-Perfect | 32 | 1,728 | 6×16 | 18 | 50 levels |
| Perfect | 256 | 13,824 | 12×16 | 72 | 100 levels |
| Cella Jr. | 64 | 3,456 | 12×16 | 18 | — |
| Super Perfect | 1,024 | 55,296 | 12×16 | 288 | 200 levels |
| Cella Max | 4,096 | 221,184 | 12×16 | 1,152 | — |

**The page widens at Semi-Perfect and grows taller at Perfect.** Below Semi-Perfect a
Cella is a chest and is drawn like one; from there up it is not, and the screen stops
pretending — which is also the rung where searching arrives, for the same reason in both
cases. A hundred and ninety-two is not chosen for looking right: every capacity from
Perfect up has to divide the page, and those four are 27 × 2⁷, 2⁹, 2¹¹ and 2¹³, so it has
to divide 3,456. Twelve by sixteen does, and 1,728 divides by ninety-six exactly.

**The column in `Kind` is the capacity and the pages follow from it**, which is the way
round it was not written first. How big a chest is is a fact about the chest; how it is
cut into pages is a fact about looking at it. Writing the pages down made the second
decide the first, and the two need not divide. An earlier shape put Cella Max's last page
at fifty-four of its ninety squares; a short last page is fine and is drawn short.
Bending the capacity so the pages come out round would not be.

**Multiplying by what a recipe eats is addition, not fusion.** Four put together giving
four times the room is arithmetic, and whatever a form is worth beyond that is not room.
What it is worth is below: experience, and a ladder of properties that ends one rung
before the top on purpose.

**1,024 pages cannot be reached with two arrows**, which is what the search is for. The
sort and the two movers still read the whole chest once per press, which at 221,184 slots
is a real amount of work for one keystroke.

### It eats what you fought for

Sneak with empty hands and right-click, and a Cella takes everything it can still use out
of your experience. **In one go, not a level at a time** - this is not something being fed,
it is something absorbing, and a creature that takes what it needs in mouthfuls is a
different creature. The gesture is already deliberate, so a small amount bought a safety
the gesture had bought already.

**Points and not levels.** A level is worth seven at the bottom and over three hundred at
the top, so a chest that counted levels would be worth a hundred times more to somebody who
had already earned some - the opposite of what a threshold is for. Every figure in the
table above is a whole level in vanilla's own arithmetic, so a bar reaches its end exactly
when a level lands.

**One way.** Nothing gives it back. What a Cella has been given is what it fought for, and
a chest that returned it would be a bank - a different mod, and one that would quietly
become the reason to build this one. The larva is the exception, because it keeps nothing:
break one and what it ate comes back as orbs. A chest you must destroy to open is not a
bank.

Breaking one used to lose it. `handOver` asked `isEmpty()`, meaning "is any slot spoken
for", so a chest holding nothing but fifty levels dropped as a plain item with the
experience still in the block. One way means there is no getting that back except by
fighting for it again, so the question is `worthKeeping()` now, which is a different
question.

### Two of them are not made, they are grown into

**A Laravel that has eaten enough becomes an Imperfect**, there and then. **A Perfect that
has eaten enough and is then ended comes back a Super Perfect.** Neither has a recipe, and
the test asks whether every form can be *reached* rather than whether every form has one.

Those are two events and not one mechanism with a flag, because the source material has
them as two: growing up needs nothing else to happen, and going from Perfect to Super
Perfect is nearly dying and coming back, which has to be done to it. One column says which
way a form goes; both read the same threshold to know when it is ready.

**A recipe will not take a Cella that has not finished growing.** A form is what it ate,
and half of what it ate is not half a form - it is a form that is not done. Nothing
explains the refusal: a recipe that does not hold is not a recipe and the bench shows
nothing, and the percentage on every item says which one is short.

**A Cella that changes form starts again at nothing.** True of growing up, of ending
itself, and of fusing - the last of which used to add its ingredients' experience up, on
the reasoning that what several were fed adds up like what several held. Wrong reasoning,
and it did not survive its own arithmetic: eight full Imperfects are 11,160 points against
a Semi-Perfect's 5,345, so a fusion arrived already finished. A rung that could never be
climbed because it was never at the bottom of it.

### What a form is, besides big

| gains | |
|---|---|
| **Imperfect** | keeps its contents / stops burning / reaches four blocks for orbs / iron axe |
| **Semi-Perfect** | cobblestone-tough / reaches eight / diamond axe / **can be searched** |
| **Perfect** | obsidian-tough / a pickaxe job / diamond pickaxe |
| **Super Perfect** | bedrock-tough / safe from a wither / netherite pickaxe / the dropped item does not go away / **gets up again with you if you die** / **stops reaching for orbs** |

**Cella Jr. is a Perfect** in everything but size. **Cella Max is a Super Perfect, on
purpose**: it is not waiting for something of its own. Nothing may have a property Super
Perfect lacks - a Perfect Cell is stronger than a Cell Max, and a ladder whose last rung
outdid it would be saying otherwise. Max buys room, which is what it is: bigger, and less.

The table is written as what a rung *arrives at* rather than what a step *gains*, even
though it reads the other way round. Listing gains means no single line ever says what a
form actually is; the cumulative reading is a property of the numbers instead, and every
column climbs. There is a test on that, and on the one place it does not: **absorption
stops at Super Perfect**, because a thing that has become complete has no reason to take in
whatever happens to be lying about.

Two of these are not the mechanism their name suggests, and both had to be looked up.
**Fire resistance is the absence of an entry** - only blocks handed to `setFlammable` burn,
and no Cella ever was, so every form was already fireproof and what makes "Imperfect gains
it" true is *registering Laravel*. And **the wither does not read blast resistance**;
`canDestroy` asks only whether the block is in `WITHER_IMMUNE`, so obsidian-tough with no
tag is exactly what obsidian is, and being safe from one is the tag and nothing else.

`fireResistant()` also only covers fire and lava. An item entity dies to explosions and to
cactus as ordinary damage, and to five minutes of waiting - which is the commonest way one
is lost. All three are closed for the top of the ladder, as one promise rather than three,
because a form that survives being blown up and then quietly times out is the same loss
arriving later.

⚠ **The void is the exception, and not a hole in the implementation.** Below the world the
game calls `discard` on anything at all - a removal and not damage, so nothing an item can
be made of refuses it. What survives is the half worth having: the contents were filed the
moment the chest was picked up and the void reports nothing, so they stay in the store and
`/cella kept give` puts them on a new item. **The name goes and the chest does not.**

**Dying is a separate column, because it is a separate thing.** A death does not destroy
what was being carried, it leaves it where the person was standing - which is ordinarily
somewhere they can walk back to, and is not, when this mod's own ending kills them in a
hundred-block crater with nothing under it. So a Super Perfect or a Cella Max in the
inventory is taken out of the death drops and handed back on respawn. It waits in the one
corner of a player's data that crosses a death *and* goes to disk, because the gap between
dying and getting up is a screen somebody can close the game on. Slots the inventory itself
holds, not a Cella inside a shulker box; and with `keepInventory` on, nothing is dropped so
nothing is taken.

### Ending itself

A nether star, on a Perfect that has taken in everything it can use, **in the End**. It
shakes for five seconds and then it goes.

Neither a command nor a button. A command would put the one rung that is not arithmetic
behind an operator; a button would sit an inch from the sort button, and this is the only
thing in the mod that cannot be undone. A nether star has no path to being pressed by
accident - flint and steel is the game's own verb for setting something off and was the
obvious pick until the obvious problem, that it is cheap and lives in a pocket.

The dimension is not flavour either. What follows removes a hundred blocks in every
direction, and there is one place in this game where that is the player's problem instead
of everybody's. **The fuse is not flavour either**: the wave takes the ground away rather
than damaging anyone, so in the End what it does to whoever is standing there is drop them
into nothing.

**The contents move block to block and never become an item.** An item at the centre of
that would be thrown by the explosion, in a dimension largely made of somewhere to fall, so
the one thing that has to survive would be the one thing put where it could not.

#### A vanilla explosion cannot do this at any radius

An explosion casts rays and takes each block's blast resistance off the ray as it passes,
so in solid ground the reach is a fraction of the radius asked for. Measured in end stone:

| radius asked | blocks removed | hole opened |
|---|---|---|
| 8 | 8 | 1 across |
| 16 | 48 | 2 |
| 32 | 283 | 4 |

Asking for a hundred would give a dozen. What makes dynamite look impressive above ground
is that air costs the ray almost nothing. **The limit is the algorithm, not the machine** -
so the blocks are removed by `Blast`, and `explode()` is left to do the noise, the light
and the throwing.

A sphere of radius a hundred is four million blocks, written at roughly two hundred a
millisecond, so it is spread over ticks: **two shells each, forty blocks a second, the full
reach in two and a half seconds**. Constant speed rather than a work budget, because a wave
that slowed down would read as the game struggling. Shells are walked as the surface of a
cube, or each step would cost the volume it encloses instead of its surface.

It will not touch anything the game says cannot be broken - **the End's way home is
bedrock** - nor **Super Perfect and Cella Max, which are as hard as the world's floor**.
⚠ That hardness is the whole of the reason: being a Cella earns nothing, so everything
from Perfect down is a box in the way. A Cella that goes files its contents and drops the
name as it goes, and the front sweeps that item up behind itself, so a crater is not a
pile of orphans. Blocks are otherwise set to air rather than broken, so nothing drops.
Everything alive inside dies:
players by a damage type this mod declares, so the screen says what happened and the
reasons it goes through armour and invulnerability are written in its tags rather than
borrowed from the void's. Everything else is simply removed, because a crater full of the
drops of what used to be standing in it is neither the picture nor anything anybody can
reach.

### It can be searched, from Semi-Perfect up

A magnifier at the left of the title; pressing it turns that row into a box. **The row is
the right one to take because everything on it is about paging** - the name, the page
number, the arrows - and the moment you are searching, the name is the least useful thing
on the screen. Nothing is displaced when it is shut.

**Where it arrives is where turning pages stops working**, not where that becomes
unbearable. Semi-Perfect is eighteen pages against Imperfect's four, and making somebody
climb to Perfect's hundred and forty-four before offering relief is charging them for
having got that far.

The window stops handing out a run of the chest and starts handing out a list; everything
above it is untouched, and the only thing that changed is which slot of the chest each
square lands on. Empty slots are never a result, which is the point: eighteen pages of
mostly nothing comes back as the handful that answered.

**A container data slot is a `short`.** A Cella Max is 221,184 slots, so the number of
slots being looked at cannot travel - what does is how many pages there are and how many
squares of this one are real, both of which fit and neither of which is the count they come
from. And the query is walked against every non-empty slot on the server, so the box waits
four ticks after the last key rather than asking once per keystroke.

**A search that answers nothing says so**, across the bare panel where the squares would
be. Empty is also what a chest with nothing in it looks like, and the difference between
*your word found nothing* and *this chest is empty* is the whole of what the player is
asking. The page count is shown while searching even when the answer fits on one page:
then it is not a control saying where you are, it is the answer saying how big it is.

⚠ **The word is matched against the registry name as well as the name on screen.** The
search runs on the server and a dedicated server has no language — asked for the name of a
modded item it hands back the translation key — so a search matched only against the
screen name worked in single player and came back empty exactly where a chest this size is
most likely to be. The registry name is always there, and is what the chest is ordered by
anyway. Underscores read as spaces, because *diamond sword* is how it is typed.

### Results are kept up with the chest, not photographed

A search writes down which slots answered. **That is a claim about the chest, and the
chest goes on moving** — a hopper fills a slot, a sort rewrites all of them, the reader
takes the last ingot out of one. Written once and trusted, the list rots in two visible
ways: a square of results holding nothing, because the slot it names was emptied, and a
square holding something that never matched, because a sort moved a different item
underneath the same number.

**Neither is fixed by asking the whole chest again.** A Cella Max is 221,184 slots and a
hopper can touch one every tick, so rebuilding on change is a fifth of a million name
comparisons a tick for as long as somebody leaves a search open.

So the list is maintained. One slot moving is a binary search into a list the length of
the answer: out if it stopped matching, in at its own place if it started. **The order is
never broken** — what is on screen shifts by a square, which is what a page of a chest
does anyway. Anything that moves more slots than are worth naming one at a time, a sort
being the case it was written for, asks the whole question again instead, at the cost the
writer had already paid.

**The chest does not tell anyone; it keeps a record.** Two players can have the same Cella
open on different words, so one change has to become a different answer in each of them,
and a register of live menus kept on the block entity is a register that has to be right
about disconnects, broken blocks and dimension changes. Instead the block entity counts
its writes and remembers the last few hundred, and each menu reads what it has missed at
the moment it was already going to talk to its client. The cost is one tick: a square that
is about to go stays for fifty milliseconds.

**A reader whose page stops existing is moved onto one that does.** Nine hundred hits
become four hundred while somebody is on page three of three. Drawing that page as nothing
at all reads as items that cannot be seen rather than as a page that is gone, so they land
on the last page there is. This is not the reset a new search does: asking a different
question puts you at the start of the answer, but the answer changing under you moves you
no further than it has to.

### A bigger screen shows more of the chest

**How a chest is cut into pages is a fact about looking at it** — the chest is a flat run
of slots and a page is a way of seeing them. So the shape belongs to whoever is looking,
and the client says: it measures its window, sends what fits, and the server remembers it
against that player. Two people can read one chest cut two different ways at the same
time, and nothing has to reconcile them, because there was never anything to reconcile.

**What arrives is a ceiling, not a shape.** The page a kind has is still the kind's
business — Laravel is six by nine because a Laravel should look like a chest — and the
screen only ever cuts that down. A wall-sized monitor gets what the chest was designed to
show and no more; the window this game opens in gets fewer rows of it and more pages.

It is measured every tick and sent only when the answer changes, because a window is
resized, made fullscreen and rescaled while the player stands there — a value sent once
on joining is wrong from the first time they drag a corner. `page.automatic` in the client
config turns the measuring off in favour of two numbers.

The arithmetic is one place run both ways: the panel is 114 pixels plus 18 a row and 14
plus 18 a column, so twelve rows by sixteen is 330 × 302 and wants about 640×360 of GUI —
a 1080p screen at scale 3. Minecraft's automatic scale leaves as little as 320×240. Six,
eight and twelve rows were all watched in a client before any of this, which is how those
numbers are known rather than guessed.

**A fusion carries what it ate.** Eight Imperfects become a Semi-Perfect and everything
in the eight is in the one afterwards. It fits because the ladder is built so that it
fits: eight Imperfects are 8 × 216 = 1,728 slots and a Semi-Perfect is 1,728, all the way
up to 221,184. That invariant is why capacity is not a setting.

The names travel on the item and the pouring happens when the new chest is **put down**.
`assemble` runs every time the ingredients sit on a bench, not when the result is taken,
so filing anything there would make a chest out of every idle glance at a recipe. Reading
the names off the ingredients and adding up how full they were has no such cost, and a
name is spent when it is taken, so it cannot pour twice.

**A fusion that would not fit is not a recipe.** Prevention rather than handling: the
alternative is to make the chest anyway and put the remainder somewhere, and the only
somewhere is the floor — thousands of item entities on one block, landing on exactly the
people who filled their chests. With capacity fixed it cannot arise at all; a world still
holding chests built to older numbers can, and there the bench simply shows nothing.

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

### Broken, it keeps what is inside — except the larva

Laravel spills onto the floor the way a chest does. Everything from Imperfect up hands
its contents to the world and drops an item that **names** them.

The line is where it is because **what spills has to be pickable up**. A player carries
thirty-six stacks and an item on the ground lasts five minutes, so 54 slots is a trip and
a half and 216 is six. Semi-Perfect's 1,728 would be forty-eight: not a mess to clear up,
but the contents being destroyed by the act of moving the chest. (The source material
agrees — the larva has what the others have and cannot use any of it yet.)

**The name is not the contents.** Putting them on the item is the shulker box's answer
and it does not reach: vanilla's `ItemContainerContents` stops at **256 slots** against
Perfect's 13,824; a full Cella Max is one to four megabytes of stacks against a **2,097,151
byte** packet frame; and whatever that came to would be re-sent on every inventory change.
So the contents go into saved data on the overworld — one store for every dimension, since
a chest dug up in the Nether is the same chest at home — and the item carries a UUID.

That also **removes** the duplication family rather than managing it. A component belongs
to the `ItemStack`, so two in a stack are one set of contents with a count of two, which
is the shape of both bugs Acervus had. Copying this stack copies a name. Taking a name
spends it, so two items naming one chest put down one full chest and one empty one — and
they do not stack anyway, because that is not a thing to hand a player by accident.

### Orphans: nothing goes looking, but whatever destroys a name says so

An item that goes into lava leaves its contents in the save with nothing left to ask for
them. After fusions that happens eight at a time, since a crafted chest carries eight
names before it is placed.

**Nothing searches for them, and that is the decision rather than the omission.** Finding
out by looking that a name has gone would mean counting every item in the world that could
be holding one, and a Cella item is anywhere: a hand, a chest, an ender chest, an item on
the floor, another mod's warehouse, *another Cella*. Any sweep that misses one — an
unloaded chunk is enough — deletes contents somebody still owns. The failure points the
wrong way.

**The other direction needs no search at all.** An item burnt, blown up, stung by a cactus,
caught by a wave or simply left lying past its five minutes is gone from the one place it
was, and the thing that removed it knew without anybody being counted. So it says so, and
the store keeps a count of **names** rather than of hand-outs: a filed chest begins with the
one item it was filed with, `give` mints another, and every one seen destroyed takes one
off. The contents go when the last name does, and not one destruction earlier.

⚠ Running out is the commonest of those by a long way — the usual way a Cella is lost is
waiting, not fire — so it is the half worth having. Everything below Super Perfect has its
five minutes; Super Perfect winds its own clock back and never gets there.

⚠ That count can only ever be too high. A name lost in a way nothing reports — the void,
a creative-mode click, some other mod eating it — leaves a chest filed with nobody left to
ask for it. Which is the direction to be wrong in, and the reason the tool below stays:

```
/cella kept list              # what is kept: form, how full, how long ago
/cella kept give <id> [form]  # hand yourself an item naming those contents
/cella kept forget <id>       # destroy them, by name
```

**Handing back comes before deleting.** Contents with a lost name are not damaged — a name
is all that reaches them — so the first thing on offer is a new item that names one. Every
row of the listing carries the click that writes its own name into the next command, since
a UUID is not something to read off a screen and type.

`give` does **not** spend the name: the contents come out of the store exactly once, when
something is placed, whichever item got there first. Running it twice makes a spare item
and not a spare chest.

It does mint a second claim, though, and the item cannot say so. The figures on a picked-up
chest are a snapshot, and that used to be safe because the only way to hold a name was to
have picked the chest up - one chest, one item. `give` broke that, so whichever of two
items is placed second goes down empty while its tooltip still describes what it is not
carrying, and `forget` does the same thing harder. Nothing on the item can tell it: it is
on a client, and the client is never told what the store holds. So the store counts the
names, and the two commands that make a second one say so at the moment they make it.

⚠ Entries filed before any of this record which form they were or when. Those load saying
so, rather than guessing — a chest keeps the size it was built at, so its size is no
evidence of its form. How full one is is read off the contents either way, because that was
never a field.

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
- [x] **4** — orphans can be found and handed back
- [x] **5** — the ladder is climbable: experience, growing up, ending itself
- [x] **6** — the forms are worth something besides room
- [x] **7** — a chest of 221,184 slots can be searched
- [ ] **8** — the numbers played with rather than reasoned about; every threshold is
      a first pass
- [x] **10** — the chest keeps itself in order, and a search keeps up with the chest
- [x] **9** — the one sweep that is safe: an orphan whose item is *seen* to burn

## Related

One of a set of small, independent mods, each doing one thing and depending on
none of the others: [Fodina](https://github.com/Capsicum0907/Fodina),
[Trivium](https://github.com/Capsicum0907/Trivium),
[Magnes](https://github.com/Capsicum0907/Magnes),
[Cella](https://github.com/Capsicum0907/Cella),
[Acervus](https://github.com/Capsicum0907/Acervus),
[Fornax](https://github.com/Capsicum0907/Fornax),
[Caldarium](https://github.com/Capsicum0907/Caldarium).

## License

MIT. Decided on 2026-09-05.

MIT is the choice that puts the fewest obstacles in front of a modpack: All Rights
Reserved would have meant pack authors quietly leaving it out. It also matches the
rest of the set, so nobody has to check which of them is which.
