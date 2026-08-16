package io.github.capsicum0907.cella;

import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * A slot that belongs to a page, and is simply not there while another page is shown.
 *
 * <p><b>Every slot of the chest is in the menu — all of them, on every page.</b> That is
 * the whole design, and it is the opposite of what this mod did first. Paging used to be
 * a window that moved underneath fifty-four fixed slots; now the slots are fixed to the
 * contents and it is <em>being shown</em> that moves.
 *
 * <p>Three things follow, and none of them had to be arranged:
 *
 * <ul>
 *   <li><b>Another mod can see the whole chest.</b> A sorting mod works on the slots the
 *       menu has, and now that is every slot. Under the window it could only ever reach
 *       the page on screen, however it asked.
 *   <li><b>The page never has to be sent.</b> {@code isActive} is asked by the screen and
 *       by nothing else — it appears nowhere in {@code AbstractContainerMenu}, and the
 *       server's click path does not consult it. Turning a page changes what is drawn and
 *       nothing else, so it is a client-side fact and no packet exists for it.
 *   <li><b>Slot <em>i</em> is contents <em>i</em>, always.</b> The game works out what to
 *       send a client by comparing each slot with what it last said that slot held, which
 *       is sound exactly when nothing moves underneath a slot. Under the window it was
 *       not, and two pages holding the same thing in the same place sent nothing and drew
 *       a hole. There is nothing to be careful about here any more.
 * </ul>
 *
 * <p>All the pages' slots sit at the same coordinates on top of one another, which is
 * harmless because the screen looks at {@code isActive} before it draws one, before it
 * calls it hovered, and before it decides which one the mouse is in.
 *
 * <p>Expanded Storage, which is where the idea of keeping every slot came from, hid the
 * other pages by moving them two thousand pixels away instead. That works and it is why
 * sorting mods handled it; asking the question directly is the same thought without the
 * coordinates having to lie.
 */
public class PagedSlot extends SlotItemHandler {
    private final CellaMenu menu;
    private final int page;

    public PagedSlot(CellaMenu menu, IItemHandler contents, int index, int x, int y) {
        super(contents, index, x, y);
        this.menu = menu;
        this.page = index / menu.pageSize();
    }

    @Override
    public boolean isActive() {
        return page == menu.page();
    }
}
