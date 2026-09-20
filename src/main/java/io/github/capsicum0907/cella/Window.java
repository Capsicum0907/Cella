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

    /**
     * The slots a search turned up, in the chest's own order, or null while it is showing
     * the chest itself.
     *
     * <p><b>Paging over a list instead of over a run.</b> That is the whole of searching
     * from here down: the page still hands out {@link #size} slots and the slots above
     * still know nothing, but which slot of the chest each one lands on comes from here
     * rather than from arithmetic. ⚠ The server's only — the client is never told what it
     * cannot see.
     */
    private int[] found;

    /**
     * What the client has been told about its own page, because it cannot work it out.
     *
     * <p>⚠ <b>Neither of these can be the number of slots being looked at.</b> A container
     * data slot is a {@code short} on the wire, and a Cella Max is 221,184 slots — so what
     * travels is how many pages there are (4,096 at the very most) and how many squares of
     * this one are real (192 at the very most), both of which fit, rather than the count
     * they are derived from, which does not.
     *
     * <p><b>Unset until something arrives</b>, and until then the answers are worked out
     * from the size the chest was opened at, exactly as they were before searching existed.
     * A client that has been told nothing is looking at a whole chest, which is the only
     * thing it can be looking at.
     */
    private int pages = UNTOLD;
    private int onThisPage = UNTOLD;

    private static final int UNTOLD = -1;

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
     * Shows only the slots holding something that answers to that name, from the first of
     * them.
     *
     * <p><b>Empty slots are never a result</b>, which is what makes this worth having: a
     * search over eighteen pages of mostly nothing comes back as the handful that matched.
     * An empty query puts the chest back.
     *
     * <p>Matched against the registry name and against the name on screen, folded to lower
     * case on both sides so that neither has to guess at the other's capitals. Which of
     * the two carries it, and why it cannot be the screen name alone, is in
     * {@link #matches}.
     */
    public void search(String looking) {
        page = 0;
        if (looking.isBlank()) {
            wanted = null;
            found = null;
            return;
        }
        wanted = looking.toLowerCase(java.util.Locale.ROOT);
        rebuild();
    }

    /**
     * The word being answered, folded to lower case, or null while showing the chest.
     *
     * <p>Kept because a result list that is maintained rather than rebuilt has to be able
     * to ask the question again of one slot. See {@link #changed}.
     */
    private String wanted;

    private void rebuild() {
        int[] hits = new int[held.getSlots()];
        int count = 0;
        for (int slot = 0; slot < held.getSlots(); slot++) {
            if (matches(slot)) {
                hits[count++] = slot;
            }
        }
        found = java.util.Arrays.copyOf(hits, count);
    }

    /**
     * Whether that slot answers the word being looked for.
     *
     * <p><b>Registry name first, and the name on screen after it.</b> The search runs on
     * the server, and ⚠ <b>a dedicated server has no language</b> — it never loaded a
     * resource pack, so {@code getHoverName} on a modded item hands back the translation
     * key rather than a name. Asked only that way, a search that worked in single player
     * came back empty on a server, which is where a chest this size is most likely to be.
     *
     * <p>The registry name is always there and is what the chest is already ordered by, so
     * it is the one that has to work. The name on screen is kept as well rather than
     * dropped: where there is a language it is the word the player has in mind, and where
     * there is not it is the key, which contains the registry path anyway and so costs
     * nothing to ask.
     *
     * <p>Underscores read as spaces, because <em>diamond sword</em> is how it is typed and
     * {@code diamond_sword} is how it is spelled.
     */
    private boolean matches(int slot) {
        ItemStack stack = held.getStackInSlot(slot);
        if (stack.isEmpty()) {
            return false;
        }
        String path = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(stack.getItem()).getPath();
        return path.contains(wanted)
                || path.replace('_', ' ').contains(wanted)
                || stack.getHoverName().getString()
                        .toLowerCase(java.util.Locale.ROOT).contains(wanted);
    }

    /**
     * Puts one slot of the chest back where it belongs in the results.
     *
     * <p><b>The results are a claim about the chest, and the chest moves.</b> Taking the
     * last iron ingot out of a slot the search turned up leaves a number in {@link #found}
     * pointing at nothing, and sorting rewrites every slot underneath every number at
     * once — so the list has to be maintained rather than written once and trusted.
     *
     * <p><b>One slot at a time, because rebuilding is not affordable.</b> A Cella Max is
     * 221,184 slots and a hopper can touch one every tick; asking the whole chest again
     * each time is a fifth of a million name comparisons a tick. Asking it of the one slot
     * that moved is a binary search into a list the length of the answer.
     *
     * <p><b>The order is never broken.</b> {@link #found} is built ascending and stays
     * ascending: a slot that stops matching is taken out and the rest close up, and one
     * that starts matching goes in at its own place rather than at the end. What is on
     * screen shifts by a square, which is the same thing a page of a chest does.
     *
     * @return whether the results moved, and so whether what the client was told is stale
     */
    public boolean changed(int slot) {
        if (found == null || slot < 0 || slot >= held.getSlots()) {
            return false;
        }
        int at = java.util.Arrays.binarySearch(found, slot);
        boolean listed = at >= 0;
        if (listed == matches(slot)) {
            return false;
        }
        found = listed ? dropped(at) : added(-at - 1, slot);
        clamp();
        return true;
    }

    /**
     * Asks the whole chest again, keeping the page rather than going back to the first.
     *
     * <p>For when too much moved at once to follow slot by slot — a sort, or a menu that
     * fell behind. Unlike {@link #search} this is not the player asking a new question, so
     * the page they were reading is kept if it still exists.
     */
    public boolean again() {
        if (found == null) {
            return false;
        }
        rebuild();
        clamp();
        return true;
    }

    private int[] dropped(int at) {
        int[] out = new int[found.length - 1];
        System.arraycopy(found, 0, out, 0, at);
        System.arraycopy(found, at + 1, out, at, out.length - at);
        return out;
    }

    private int[] added(int at, int slot) {
        int[] out = new int[found.length + 1];
        System.arraycopy(found, 0, out, 0, at);
        out[at] = slot;
        System.arraycopy(found, at, out, at + 1, found.length - at);
        return out;
    }

    /**
     * Back onto a page that exists.
     *
     * <p>Results can shrink under a reader — nine hundred hits become four hundred and the
     * third page stops being there. A blank grid would be read as items that cannot be
     * seen rather than as a page that is gone, so the reader is moved to the last page
     * there is instead.
     */
    private void clamp() {
        page = Math.min(page, pages() - 1);
    }

    /** Whether it is showing results rather than the chest. */
    public boolean searching() {
        return found != null;
    }

    /** How many slots the view is over: the whole chest, or what a search turned up. */
    private int viewed() {
        return found != null ? found.length : total;
    }

    /** How many squares of the page being shown are real. The server's answer to tell. */
    public int onThisPage() {
        return Math.clamp(viewed() - page * size, 0, size);
    }

    /** What the client was told, because it cannot count what it does not have. */
    public void told(int pages, int onThisPage) {
        this.pages = pages;
        this.onThisPage = onThisPage;
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

    /** How many pages there are to turn, and never fewer than the one. */
    public int pages() {
        if (!chest && pages != UNTOLD) {
            return Math.max(1, pages);
        }
        return Math.max(1, (viewed() + size - 1) / size);
    }

    public int page() {
        return page;
    }

    /** Shows a page. The caller has already decided there is one; see {@code CellaMenu}. */
    public void openAt(int page) {
        this.page = page;
    }

    /** Whether that square of this page is a slot at all. */
    public boolean holds(int slot) {
        if (!chest && onThisPage == UNTOLD) {
            return page * size + slot < total;
        }
        return slot < (chest ? onThisPage() : onThisPage);
    }

    /** Whether what is underneath has somewhere to put it. */
    private boolean there(int slot) {
        return at(slot) < held.getSlots();
    }

    private int at(int slot) {
        if (!chest) {
            return slot;
        }
        int into = page * size + slot;
        return found != null
                ? (into < found.length ? found[into] : held.getSlots())
                : into;
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
