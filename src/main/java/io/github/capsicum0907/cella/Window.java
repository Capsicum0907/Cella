package io.github.capsicum0907.cella;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * One page of a chest, offered as if it were a chest of that size.
 *
 * <p><b>This is the only thing in the mod that knows which page is being shown.</b> The
 * menu's slots are ordinary slots numbered nought upwards into this, and turning a page
 * moves {@link #first} and nothing else. Nothing else has to be told, because nothing
 * else was ever asked.
 *
 * <p><b>Why a layer and not a clever slot.</b> The obvious version is a slot that works
 * out its own index from the page every time it is asked. It does not survive contact
 * with {@code SlotItemHandler}, which keeps its index in a {@code protected final} field
 * and reads that field directly in all seven of its methods rather than going through a
 * getter — so a slot with a moving index has to override every one of them, and the
 * eighth that gets added upstream is a bug nobody writes. Putting the page underneath
 * the slot instead leaves the slot with nothing to remember.
 *
 * <p><b>The last page is allowed to be short.</b> A chest built when the config said
 * more pages, or fewer, is the size it was built at — see
 * {@link CellaBlockEntity#loadAdditional} — and that size need not divide by a page. The
 * slots past the end are here so the grid is a grid, and every one of them refuses
 * everything: nothing to take, nothing that fits, no room. {@link PagedSlot} also hides
 * them, but a slot that is merely hidden is one mod away from being usable, so the
 * refusal is here, underneath, where it is not a matter of being drawn.
 */
public final class Window implements IItemHandlerModifiable {
    private final IItemHandlerModifiable whole;
    private final int size;

    /** Where this page starts in the whole chest. The one moving part. */
    private int first;

    public Window(IItemHandlerModifiable whole, int size) {
        this.whole = whole;
        this.size = size;
    }

    /** How many pages the chest comes to, and never fewer than the one. */
    public int pages() {
        return Math.max(1, (whole.getSlots() + size - 1) / size);
    }

    public int page() {
        return first / size;
    }

    /** Shows a page. The caller has already decided there is one; see {@code CellaMenu}. */
    public void openAt(int page) {
        this.first = page * size;
    }

    /** Whether that slot of this page is a slot of the chest at all. */
    public boolean holds(int slot) {
        return first + slot < whole.getSlots();
    }

    private int at(int slot) {
        return first + slot;
    }

    @Override
    public int getSlots() {
        return size;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return holds(slot) ? whole.getStackInSlot(at(slot)) : ItemStack.EMPTY;
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (holds(slot)) {
            whole.setStackInSlot(at(slot), stack);
        }
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        // What comes back is what would not go in, so refusing is returning it whole.
        return holds(slot) ? whole.insertItem(at(slot), stack, simulate) : stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return holds(slot) ? whole.extractItem(at(slot), amount, simulate) : ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return holds(slot) ? whole.getSlotLimit(at(slot)) : 0;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return holds(slot) && whole.isItemValid(at(slot), stack);
    }
}
