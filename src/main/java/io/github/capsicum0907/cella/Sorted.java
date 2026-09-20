package io.github.capsicum0907.cella;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The contents of a chest, kept in {@link Tidy#ORDER} at all times rather than put into it
 * when somebody asks.
 *
 * <h2>Why the chest owns the arrangement</h2>
 *
 * <p><b>A Cella has too many squares for a square to mean anything.</b> Perfect is 13,824
 * of them and Max is 221,184; nobody remembers that the coal lives at 9,310, and nobody
 * can put it back there after taking some out. A place only carries meaning while you can
 * hold the whole of it in your head, and these passed that a long way back. So the
 * arrangement stops being something the player maintains and becomes something the chest
 * guarantees — and what the player decides moves up a layer, to which chest a thing goes
 * in rather than which square of it.
 *
 * <p><b>Sorting on request cannot become sorting always by being called more often.</b>
 * {@link Tidy#everything} reads and rewrites every slot, which is the right price to pay
 * once on a button and an impossible one to pay per hopper tick: one item arriving would
 * rewrite two hundred thousand slots. Kept rather than restored, the work is bounded by
 * what moved instead of by how big the chest is.
 *
 * <h2>What it costs, and it is paid in one place</h2>
 *
 * <p>Slots hold the contents packed to the front, in order, and everything else follows:
 *
 * <ul>
 * <li><b>A kind is a run</b> of full stacks with at most one partial at the end. An item
 *     arriving tops up that partial; an item leaving comes out of it. Neither touches
 *     anything before it, which is why the common case moves one slot.
 * <li><b>A kind arriving or leaving shifts the tail.</b> That is a move of the array and
 *     nothing else — no reading of items, no comparisons — and it is the only operation
 *     here whose cost is the size of the chest rather than the size of the change.
 * <li><b>Writing to a particular slot does not put anything at that slot.</b> There is
 *     nowhere to put it: the slot a stack belongs at is decided by what the stack is. So a
 *     write is a removal of whatever was there followed by an ordinary arrival.
 * </ul>
 *
 * <p>⚠ <b>Whole-chest work goes through {@link CellaBlockEntity#inOneGo} instead.</b>
 * Sorting, pouring eight chests in, emptying one onto the floor — all of them write most
 * of the slots, and maintaining the order between each write would be maintaining it
 * against work that is about to overwrite it. Inside that, writes go straight through and
 * the order is restored once at the end. Outside it, every write keeps the order.
 */
public class Sorted extends ItemStackHandler {
    /**
     * How many slots at the front are spoken for. The rest are empty, and that is an
     * invariant rather than a coincidence.
     */
    private int used;

    /** Set while a whole-chest operation is writing; see {@link #straight}. */
    private boolean raw;

    public Sorted(int size) {
        super(size);
    }

    public Sorted() {
        super();
    }

    /**
     * Runs something that writes the slots directly, and puts the order back afterwards.
     *
     * <p>⚠ The caller is not trusted to leave it tidy — it is made tidy for them. What they
     * are trusted with is that they meant to write that many slots.
     */
    public void straight(Runnable work) {
        boolean was = raw;
        raw = true;
        try {
            work.run();
        } finally {
            raw = was;
        }
        if (!raw) {
            settle();
        }
    }

    /** Puts the whole chest into order and counts what is in it. */
    public void settle() {
        boolean was = raw;
        raw = true;
        try {
            Tidy.everything(this);
        } finally {
            raw = was;
        }
        used = 0;
        while (used < getSlots() && !stacks.get(used).isEmpty()) {
            used++;
        }
        moved(0);
    }

    /** How many slots hold something. Cheap, because it is never counted. */
    public int used() {
        return used;
    }

    /**
     * Whether the order is being left alone for the moment.
     *
     * <p>⚠ <b>Walking the slots of a chest that is keeping itself in order is not safe.</b>
     * Emptying a slot closes the gap, so the next index holds what the next index but one
     * held a moment ago — a loop over {@code 0..getSlots()} reads some slots twice and
     * never reads others. Anything that means to rewrite the whole chest has to say so
     * first, and {@link Tidy#everything} asks this before deciding which it is doing.
     */
    public boolean rearranging() {
        return raw;
    }

    @Override
    public void setSize(int size) {
        super.setSize(size);
        used = 0;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
        super.deserializeNBT(registries, tag);
        settle();
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (raw) {
            super.setStackInSlot(slot, stack);
            return;
        }
        validateSlotIndex(slot);
        int from = getSlots();
        boolean ran = false;
        if (slot < used) {
            from = Math.min(from, slot);
            pull(slot);
            ran = true;
        }
        if (!stack.isEmpty()) {
            ItemStack over = put(stack.copy());
            from = Math.min(from, landed);
            ran |= shifted;
            if (!over.isEmpty()) {
                // Nowhere for it. Dropping it silently is how contents go missing, so it
                // stays where it was asked to go and the next settle will place it.
                stacks.set(slot, over);
                used = Math.max(used, slot + 1);
                ran = true;
            }
        }
        if (from < getSlots()) {
            if (ran) {
                moved(from);
            } else {
                onContentsChanged(from);
            }
        }
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (raw || stack.isEmpty()) {
            return raw ? super.insertItem(slot, stack, simulate) : stack;
        }
        validateSlotIndex(slot);
        if (simulate) {
            int taken = Math.min(stack.getCount(), room(stack));
            return taken >= stack.getCount() ? ItemStack.EMPTY : copyWith(stack, stack.getCount() - taken);
        }
        ItemStack over = put(stack.copy());
        if (over.getCount() != stack.getCount()) {
            // ⚠ Topping up the one open stack of a kind is the common case and moves
            // nothing else, so it is announced as the single slot it is. Only a kind
            // arriving needs the heavier word.
            if (shifted) {
                moved(landed);
            } else {
                onContentsChanged(landed);
            }
        }
        return over;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (raw) {
            return super.extractItem(slot, amount, simulate);
        }
        validateSlotIndex(slot);
        if (amount <= 0 || slot >= used) {
            return ItemStack.EMPTY;
        }
        ItemStack asked = stacks.get(slot);
        // ⚠ Out of the remainder of that kind rather than out of that slot. What comes back
        // is the same item either way, and taking from the end is what leaves a run of full
        // stacks with one partial behind it instead of gaps in the middle of it.
        int last = endOf(slot) - 1;
        ItemStack from = stacks.get(last);
        int taken = Math.min(amount, from.getCount());
        if (simulate) {
            return copyWith(asked, taken);
        }
        ItemStack out = copyWith(from, taken);
        if (taken >= from.getCount()) {
            pull(last);
            moved(last);
        } else {
            from.shrink(taken);
            onContentsChanged(last);
        }
        return out;
    }

    /**
     * Where the last arrival went, so a caller can say what moved without looking for it.
     *
     * <p>Set by {@link #put} and read immediately; it is a second return value and not a
     * fact about the chest.
     */
    private int landed;

    /** Whether the last arrival needed a slot of its own, and so moved everything after it. */
    private boolean shifted;

    /**
     * An arrival, into the place it belongs.
     *
     * @return what would not fit, which is empty in every case but a full chest
     */
    private ItemStack put(ItemStack incoming) {
        landed = getSlots();
        shifted = false;
        int at = start(incoming);
        int end = endOf(at);

        // The one partial of that kind, if it has one, is the last of its run.
        if (at < end) {
            ItemStack open = stacks.get(end - 1);
            if (same(open, incoming) && open.getCount() < limitFor(open)) {
                int moved = Math.min(limitFor(open) - open.getCount(), incoming.getCount());
                open.grow(moved);
                incoming.shrink(moved);
                landed = end - 1;
            }
        }

        while (!incoming.isEmpty() && used < getSlots()) {
            int room = Math.min(limitFor(incoming), incoming.getCount());
            ItemStack one = copyWith(incoming, room);
            incoming.shrink(room);
            int where = place(one);
            shiftRight(where);
            shifted = true;
            stacks.set(where, one);
            used++;
            landed = Math.min(landed, where);
        }
        return incoming;
    }

    /** The first slot that should come after this stack. The array is in order, so bisect. */
    private int place(ItemStack stack) {
        int low = 0;
        int high = used;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (Tidy.ORDER.compare(stacks.get(middle), stack) <= 0) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    /** The first slot holding the same registry name as that stack. */
    private int start(ItemStack stack) {
        ResourceLocation wanted = key(stack);
        int low = 0;
        int high = used;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (key(stacks.get(middle)).compareTo(wanted) < 0) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    /** One past the last slot sharing that slot's registry name. */
    private int endOf(int slot) {
        ResourceLocation wanted = key(stacks.get(slot));
        int at = slot + 1;
        while (at < used && key(stacks.get(at)).equals(wanted)) {
            at++;
        }
        return at;
    }

    private void pull(int slot) {
        for (int at = slot; at + 1 < used; at++) {
            stacks.set(at, stacks.get(at + 1));
        }
        stacks.set(used - 1, ItemStack.EMPTY);
        used--;
    }

    private void shiftRight(int slot) {
        for (int at = used; at > slot; at--) {
            stacks.set(at, stacks.get(at - 1));
        }
        stacks.set(slot, ItemStack.EMPTY);
    }

    /** What a chest that is packed to the front can still take of that kind. */
    private int room(ItemStack stack) {
        int at = start(stack);
        int end = endOf(at);
        int open = 0;
        if (at < end) {
            ItemStack last = stacks.get(end - 1);
            if (same(last, stack)) {
                open = limitFor(last) - last.getCount();
            }
        }
        return open + (getSlots() - used) * limitFor(stack);
    }

    private int limitFor(ItemStack stack) {
        return Math.min(getSlotLimit(0), stack.getMaxStackSize());
    }

    private static boolean same(ItemStack one, ItemStack other) {
        return ItemStack.isSameItemSameComponents(one, other);
    }

    private static ResourceLocation key(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    private static ItemStack copyWith(ItemStack stack, int count) {
        ItemStack out = stack.copy();
        out.setCount(count);
        return out;
    }

    /**
     * Says that everything from that slot onwards may be somewhere else now.
     *
     * <p>⚠ <b>Announcing each moved slot separately is not an option.</b> A kind arriving at
     * the front of a Max shifts two hundred thousand of them, and every announcement is a
     * neighbour update and an entry in a log somebody is reading. So one is sent, and
     * whoever is following knows that a run moved rather than a slot.
     */
    protected void moved(int from) {
        onContentsChanged(from);
    }
}
