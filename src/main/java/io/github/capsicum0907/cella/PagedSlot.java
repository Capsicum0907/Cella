package io.github.capsicum0907.cella;

import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * A slot of the page being shown. It does not know which page that is.
 *
 * <p>It is an ordinary slot into a {@link Window}, and the window is the page. Turning a
 * page moves the window; the slots are not told and have nothing to be told. That is the
 * whole of paging, and this class exists for the one thing left over: <b>the last page of
 * a chest can be short</b>, and a slot that is off the end must not be drawn.
 *
 * <h2>The road not taken</h2>
 *
 * <p>This was written three times and the third is the second one again, so it is worth
 * saying why the middle version was abandoned rather than leaving it looking like a
 * circle.
 *
 * <p><b>Every slot of the chest in the menu at once</b> was the second design, and it was
 * bought for one thing: a sorting mod works on the slots the menu has, so a menu holding
 * all of them could be sorted whole from outside. That turned out not to be true.
 * Inventory Profiles Next excludes slots that answer {@code isActive} with false, which is
 * every page but one however many are in the menu — so nothing was ever bought, and the
 * price was being paid in full. The price: the server sends every slot when the screen
 * opens and compares every slot every tick, which is thirteen thousand of them for a chest
 * this mod is meant to grow into.
 *
 * <p>Two real defects came out of that design as well, and both are gone with it. Every
 * page's slots sat at the same coordinates, so anything working out which slot the mouse
 * was over from where it was found eight candidates in one square; moving the off-page
 * ones a screen-height away fixed it and cost an <b>access transformer</b>, because
 * {@code Slot.x} and {@code Slot.y} are {@code public final}. With one page in the menu
 * there is one slot per square and the transformer is gone.
 *
 * <p>What the window costs, and it is the honest cost: <b>turning a page is no longer
 * free.</b> Under the second design the page never left the client. Here the server owns
 * it, because the server is the one deciding what a click means — see
 * {@link CellaMenu#turnTo}.
 */
public class PagedSlot extends SlotItemHandler {
    private final Window window;

    public PagedSlot(Window window, int index, int x, int y) {
        super(window, index, x, y);
        this.window = window;
    }

    /**
     * Off the end of a short last page is not drawn.
     *
     * <p>Hiding is all this does. A slot that is off the end also refuses to hold, give
     * or take anything, and that is arranged in {@link Window} rather than here — being
     * invisible is a fact about this screen and everything else in the game is entitled
     * to ignore it.
     */
    @Override
    public boolean isActive() {
        return window.holds(getSlotIndex());
    }
}
