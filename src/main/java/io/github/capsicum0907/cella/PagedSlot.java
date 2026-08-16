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
 * <p><b>The pages are also kept apart on screen, and that is not belt and braces.</b>
 * They were stacked at the same coordinates at first, on the reasoning that
 * {@code isActive} already answers every question vanilla asks — which is true, and was
 * not enough. Shift-clicking an empty slot fetched an item from the same square on
 * another page, because a mod that works out which slot the mouse is over from where it
 * is finds eight candidates in one square and is entitled to any of them. Vanilla's own
 * lookup asks {@code isActive} first; nothing obliges anyone else's to.
 *
 * <p>So Expanded Storage moving its off-page slots two thousand pixels away was not
 * laziness — it is what keeps a position honest, and this does the same. {@code isActive}
 * hides; {@link #place} makes the coordinates distinct. They are different jobs.
 */
public class PagedSlot extends SlotItemHandler {
    /**
     * How far apart the pages are stacked.
     *
     * <p>Only has to be more than a screen is tall, so that no page's slots can ever be
     * mistaken for another's by anything measuring in pixels. It is not a hiding place —
     * {@link #isActive} does the hiding — it is what makes the coordinates <em>distinct</em>.
     */
    private static final int SPREAD = 1000;

    private final CellaMenu menu;
    private final int page;
    private final int homeY;

    public PagedSlot(CellaMenu menu, IItemHandler contents, int index, int x, int y) {
        super(contents, index, x, y);
        this.menu = menu;
        this.page = index / menu.pageSize();
        this.homeY = y;
    }

    /**
     * Puts this slot where it belongs relative to the page being shown.
     *
     * <p>The open page sits at home and every other page is a screen-height away, up or
     * down. So no two slots share a position, ever.
     */
    public void place(int shown) {
        this.y = homeY + (page - shown) * SPREAD;
    }

    @Override
    public boolean isActive() {
        return page == menu.page();
    }
}
