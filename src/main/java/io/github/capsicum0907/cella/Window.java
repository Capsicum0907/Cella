package io.github.capsicum0907.cella;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * One page of a chest, offered as if it were a chest of that size.
 *
 * <p><b>This is the only thing in the mod that knows which page is being shown.</b> The
 * menu's slots are ordinary slots numbered nought upwards into this, and turning a page
 * moves {@link #openAt} and nothing else. Nothing else has to be told, because nothing
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
 * <h2>The two sides hold different things</h2>
 *
 * <p><b>The server has the chest and finds the page in it. The client has the page.</b>
 * That is not an optimisation, it is what is true: the client is only ever sent the page
 * it is looking at, so a client-side copy of the other four thousand large chests would
 * be four thousand large chests of nothing. {@link #onto} is the server's and
 * {@link #of} is the client's, and everything above this class is written once.
 *
 * <p>Which is also why there are two questions and not one. <b>{@link #there} is whether
 * what is underneath has that slot</b> — always true of a page, true of a chest up to
 * its end. <b>{@link #holds} is whether the <em>chest</em> has it</b>, which is the one
 * gameplay cares about, and the client can answer it without having the chest because it
 * is told how many slots there are.
 *
 * <p>They differ on the client for the length of one packet. A page turn arrives as the
 * contents first and the page number second — {@code sendAllDataToRemote} sends them in
 * that order — so for that moment {@link #holds} is answering about the page before.
 * Writing has to go through anyway or the contents of a full page arriving while the
 * window still thinks it is on a short one would be dropped on the floor; showing does
 * not, and one frame of a square being drawn or not is nothing. A click in that frame is
 * safe either way: the server answers from its own window, which is not confused.
 *
 * <p><b>The last page is allowed to be short.</b> A chest is the size it was built at —
 * see {@link CellaBlockEntity#loadAdditional} — and that size need not divide by a page.
 * The slots past the end are here so the grid is a grid, and every one of them refuses
 * everything. {@link PagedSlot} also hides them, but a slot that is merely hidden is one
 * mod away from being usable, so the refusal is here, underneath, where it is not a
 * matter of being drawn.
 */
public final class Window implements IItemHandlerModifiable {
    /** The chest on the server; the page itself on the client. */
    private final IItemHandlerModifiable held;

    private final int size;

    /** Slots in the whole chest. The client is told this and does not hold it. */
    private final int total;

    /** Whether {@link #held} is the chest, so that a page has to be found inside it. */
    private final boolean chest;

    private int page;

    private Window(IItemHandlerModifiable held, int size, int total, boolean chest) {
        this.held = held;
        this.size = size;
        this.total = total;
        this.chest = chest;
    }

    /** Onto a chest, taking a page of it at a time. The server's. */
    public static Window onto(IItemHandlerModifiable chest, int size) {
        return new Window(chest, size, chest.getSlots(), true);
    }

    /**
     * Over a page and nothing else, in a chest said to be that big. The client's.
     *
     * @param shown the page, which is what the server sends and all it sends
     * @param total how many slots the chest has, which is how this knows how many pages
     *              there are and where the last one stops
     */
    public static Window of(IItemHandlerModifiable shown, int size, int total) {
        return new Window(shown, size, total, false);
    }

    /** How many pages the chest comes to, and never fewer than the one. */
    public int pages() {
        return Math.max(1, (total + size - 1) / size);
    }

    public int page() {
        return page;
    }

    /** Shows a page. The caller has already decided there is one; see {@code CellaMenu}. */
    public void openAt(int page) {
        this.page = page;
    }

    /** Whether that slot of this page is a slot of the chest at all. */
    public boolean holds(int slot) {
        return page * size + slot < total;
    }

    /** Whether what is underneath has somewhere to put it. */
    private boolean there(int slot) {
        return at(slot) < held.getSlots();
    }

    private int at(int slot) {
        return chest ? page * size + slot : slot;
    }

    @Override
    public int getSlots() {
        return size;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return there(slot) ? held.getStackInSlot(at(slot)) : ItemStack.EMPTY;
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (there(slot)) {
            held.setStackInSlot(at(slot), stack);
        }
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        // What comes back is what would not go in, so refusing is returning it whole.
        return holds(slot) ? held.insertItem(at(slot), stack, simulate) : stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return holds(slot) ? held.extractItem(at(slot), amount, simulate) : ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return holds(slot) ? held.getSlotLimit(at(slot)) : 0;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return holds(slot) && held.isItemValid(at(slot), stack);
    }
}
