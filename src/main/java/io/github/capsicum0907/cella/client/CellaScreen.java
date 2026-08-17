package io.github.capsicum0907.cella.client;

import io.github.capsicum0907.cella.Cella;
import io.github.capsicum0907.cella.CellaMenu;
import io.github.capsicum0907.cella.Mods;

import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import org.anti_ad.mc.ipn.api.IPNPlayerSideOnly;

/**
 * The chest screen, drawn on the vanilla chest background.
 *
 * <p>The background is {@code generic_54}, borrowed rather than copied: it is already
 * exactly the right picture, and a copy would be one more file to keep in step with a
 * texture pack the player installed.
 *
 * <p><b>Marked player-side-only for Inventory Profiles Next.</b> Its container buttons
 * move and sort what its screen can see, and this screen shows one page — a half-working
 * copy of buttons this mod already has, which is worse than not having them. The
 * annotation asks it to keep the player's half, which it can see all of, and leave this
 * one alone. Compile-time only: an annotation whose class is absent is simply not read,
 * so nothing here needs IPN installed.
 *
 * <p>Five controls, all {@link IconButton}. Paging and sorting sit at the right-hand
 * end of the lid, the corner a sorting mod would otherwise have taken; the two ways
 * of moving sit beside the inventory they move to and from, with shift narrowing
 * either to the kinds already on the other side.
 */
@IPNPlayerSideOnly
public class CellaScreen extends AbstractContainerScreen<CellaMenu> {
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    /** The strip of background above the slots, and the part below them. */
    private static final int LID = 17;
    private static final int SLOT = 18;

    /**
     * Where a slot's sunken frame sits in the vanilla chest picture.
     *
     * <p>The only thing still taken from that file. The panel round it is drawn, because
     * the picture is nine slots wide and some of these chests are not - and a chest
     * fifteen wide cannot be cut out of a picture of one that is nine.
     */
    private static final int SLOT_U = 7;
    private static final int SLOT_V = 17;

    /** Where the first row of chest slots begins, under the lid. */
    private static final int FIRST_Y = 18;

    /** The player's three rows and hotbar, which every container menu ends with. */
    private static final int PLAYER_SLOTS = 36;

    private static final int PANEL = 0xFFC6C6C6;
    private static final int PANEL_LIT = 0xFFFFFFFF;
    private static final int PANEL_DARK = 0xFF555555;
    private static final int OUTLINE = 0xFF000000;

    /** How deep the highlight and the shadow run, counted off vanilla's own panel. */
    private static final int BEVEL = 3;

    /** Where vanilla draws a container title, and so where ours starts. */
    private static final int TITLE_X = 8;

    private static final int BUTTON = IconButton.SIZE;
    private static final int BUTTON_Y = 3;

    /**
     * A digit is seven pixels of ink in a box of ten, so the two centres are half a
     * pixel apart whichever way it is rounded - the price of matching a size that is
     * even. Rounded down, because a number sitting high is what was noticed before.
     */
    private static final int DIGITS = 7;
    private static final int TEXT_Y = BUTTON_Y + (BUTTON - DIGITS + 1) / 2;

    /** Two apart is the neighbours' spacing; four sets sorting off from paging. */
    private static final int SPACE = 2;
    private static final int APART = 4;
    private static final int AFTER_TITLE = 6;

    /** Space between the page number and the arrow it belongs to. */
    private static final int BESIDE = 3;

    /** The grey vanilla writes a container's labels in. */
    private static final int LABEL = 0x404040;

    private static final String ELLIPSIS = "...";

    /**
     * Two movers, not four: in and out.
     *
     * <p>There were four - in, in-matching, out-matching, out - and the person who asked
     * for them could not say what they were a day later. Four buttons meant two things to
     * tell apart at once, direction and reach, and six pixels of picture will carry one.
     *
     * <p>So reach moved onto <b>shift</b>, and <b>shift is the wide one</b>. That is the
     * way round this game already uses it: shift-click moves the stack rather than the
     * item, shift-craft takes every one it can make. It also puts the careful answer on
     * the plain click, which is the right way for a button that empties a pack.
     */
    private static final int[] PLAIN = { CellaMenu.MATCHING, CellaMenu.TAKING };
    private static final int[] WIDE = { CellaMenu.STOW, CellaMenu.TAKE };
    private static final String[] NAMES = { "stow", "take" };

    private final int rows;

    /** Left edge of the page controls, and of the movers, once the labels are known. */
    private int controls;
    private int movers;

    public CellaScreen(CellaMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        // A page's worth of rows, which is the menu's - it is a page tall.
        this.rows = menu.rows();
        this.imageWidth = menu.width();
        this.imageHeight = 114 + rows * SLOT;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    /**
     * What a mover says: what it does, and underneath what shift makes it do.
     *
     * <p>Both lines always, rather than swapping as the key is held. A tooltip is where
     * you go to find out, and finding out is exactly what the second line is for.
     */
    private static Component told(String name) {
        return Component.translatable("gui.cella." + name)
                .append(CommonComponents.NEW_LINE)
                .append(Component.translatable("gui.cella." + name + ".shift")
                        .withStyle(ChatFormatting.GRAY));
    }

    private static ResourceLocation icon(String name) {
        return ResourceLocation.fromNamespaceAndPath(Cella.MODID, "textures/gui/" + name + ".png");
    }

    /**
     * After a label, and never off the end of the panel. Used when something else has
     * the right-hand end of a row.
     *
     * <p>Measured from the label rather than fixed, so a renamed chest or a translated
     * "Inventory" pushes the buttons along instead of being written over. A label long
     * enough to reach the clamp loses the argument, which is the right way round: the
     * buttons have somewhere they must be and a label does not.
     */
    private int after(Component label, int width) {
        return Math.min(TITLE_X + font.width(label) + AFTER_TITLE, imageWidth - TITLE_X - width);
    }

    @Override
    protected void init() {
        super.init();

        // Right-hand end, like everything else on this screen - unless the sorting mod
        // is here, whose own buttons are in that corner of this row. Then ours go after
        // the label instead and the corner is left alone. Two mods in one corner is what
        // started all of this.
        int row = PLAIN.length * BUTTON + (PLAIN.length - 1) * SPACE;
        movers = Mods.inventoryProfiles()
                ? after(playerInventoryTitle, row)
                : imageWidth - TITLE_X - row;
        for (int at = 0; at < PLAIN.length; at++) {
            int plain = PLAIN[at];
            int wide = WIDE[at];
            addRenderableWidget(new IconButton(
                    leftPos + movers + at * (BUTTON + SPACE), topPos + inventoryLabelY - 2,
                    icon(NAMES[at]), told(NAMES[at]),
                    () -> send(Screen.hasShiftDown() ? wide : plain)));
        }

        // Anchored to the right-hand end of the lid, which is the corner a sorting mod
        // would have used and has been asked not to. Anchored rather than laid out from
        // the left so that the page number, which is the only thing here that changes
        // width, grows into the space beside them instead of pushing them about.
        int sort = imageWidth - TITLE_X - BUTTON;
        addRenderableWidget(new IconButton(leftPos + sort, topPos + BUTTON_Y,
                icon("sort"), Component.translatable("gui.cella.sort"),
                () -> send(CellaMenu.SORT)));

        if (menu.pages() <= 1) {
            return;
        }
        // The two arrows next to each other. They were either side of the number, which
        // put fifty pixels between them - a long way to travel to press one twice.
        int next = sort - APART - BUTTON;
        controls = next - SPACE - BUTTON;
        addRenderableWidget(new IconButton(leftPos + controls, topPos + BUTTON_Y,
                icon("prev"), Component.translatable("gui.cella.prev"), () -> turn(-1)));
        addRenderableWidget(new IconButton(leftPos + next, topPos + BUTTON_Y,
                icon("next"), Component.translatable("gui.cella.next"), () -> turn(1)));
    }

    /**
     * The wheel turns the page, over the frame and nowhere else.
     *
     * <p><b>Not over a slot.</b> The wheel above a slot already belongs to whoever the
     * player installed to use it — moving one item at a time, scrolling a stack across —
     * and a chest that quietly ate that gesture would be a chest that broke their mouse.
     * The frame is the part of this screen nobody else has a use for, and it is a big
     * target: the lid, the margins, and the strip above the inventory.
     *
     * <p>Up goes back, which is the direction every list scrolls.
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (menu.pages() > 1 && scrollY != 0 && hoveredSlot == null && overPanel(mouseX, mouseY)) {
            turn(scrollY > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean overPanel(double mouseX, double mouseY) {
        return mouseX >= leftPos && mouseX < leftPos + imageWidth
                && mouseY >= topPos && mouseY < topPos + imageHeight;
    }

    /**
     * Turning a page is asking for one. The screen turns when the answer arrives.
     *
     * <p>A page is sent as itself — the five real buttons are negative, so the two cannot
     * collide however many pages a chest grows to. Turning early and being corrected was
     * written and thrown away: what is on screen has to be what a click will act on, and
     * a click acts on the server's page. See {@link CellaMenu#turnTo}.
     */
    private void turn(int by) {
        send(Math.floorMod(menu.page() + by, menu.pages()));
    }

    /**
     * Moving and sorting are sent, not done here. The server owns the contents; what
     * comes back arrives as the ordinary slot updates.
     */
    private void send(int id) {
        Minecraft client = Minecraft.getInstance();
        if (client.gameMode != null) {
            client.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    /**
     * The panel, drawn; the slots, borrowed.
     *
     * <p>It used to be two blits out of the vanilla chest picture, which is exactly the
     * right picture for a chest that is nine wide. These are not all nine wide, and a
     * fifteen-wide panel cannot be cut out of a nine-wide one however it is sliced. So
     * the panel is a filled rectangle with a raised edge — the same three colours a
     * button has, for the same reason — and the only thing still taken from the file is
     * the sunken frame a slot sits in, blitted once per slot wherever the menu put it.
     *
     * <p>Reading the slots off the menu rather than counting them out again is what keeps
     * the picture and the clicking from disagreeing: there is one list of where things
     * are and both use it.
     */
    @Override
    protected void renderBg(GuiGraphics graphics, float partial, int mouseX, int mouseY) {
        panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        int x = leftPos;
        int y = topPos;

        // The whole page's worth of frames, whether there is a slot behind each or not.
        // The last page of a chest built to older numbers is a short one, and drawing
        // only the slots that exist left a hole in the middle of the panel where the
        // frames stopped. An empty frame is what an empty slot looks like.
        int chestLeft = (imageWidth - menu.columns() * SLOT) / 2;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < menu.columns(); column++) {
                graphics.blit(BACKGROUND, x + chestLeft + column * SLOT - 1,
                        y + FIRST_Y + row * SLOT - 1, SLOT_U, SLOT_V, SLOT, SLOT);
            }
        }
        // The player's own, read off the menu: they are the only ones whose places this
        // screen does not work out for itself.
        for (int at = menu.slots.size() - PLAYER_SLOTS; at < menu.slots.size(); at++) {
            net.minecraft.world.inventory.Slot slot = menu.slots.get(at);
            graphics.blit(BACKGROUND, x + slot.x - 1, y + slot.y - 1, SLOT_U, SLOT_V, SLOT, SLOT);
        }
    }

    /**
     * The title, cut to fit, then the page number.
     *
     * <p><b>Not {@code super}.</b> Vanilla draws a container's title at a fixed place and
     * lets it run as far as it likes, which is fine on a screen with nothing else in the
     * lid. "Super Perfect Cella" ran straight through the page number.
     *
     * <p>So the title gets the room that is left over and is trimmed to it, with an
     * ellipsis to say it was. The controls do not move: a name is a thing you can guess
     * the rest of, and a button is not.
     */
    /**
     * The panel, built the way vanilla's picture is built.
     *
     * <p>Counted off {@code generic_54} rather than guessed: a one-pixel black outline
     * with <b>the corner cut away</b> - three pixels gone at each, so it reads as rounded
     * - then a three-pixel white highlight inside the top and left, a three-pixel #555555
     * shadow inside the bottom and right, and #C6C6C6 between. A plain rectangle was the
     * first version and looked like a plain rectangle.
     */
    private void panel(GuiGraphics graphics, int x, int y, int w, int h) {
        // The body, with the corners pulled in a pixel on the first and last rows.
        graphics.fill(x + 1, y + 2, x + w - 1, y + h - 2, PANEL);
        graphics.fill(x + 2, y + 1, x + w - 2, y + 2, PANEL);
        graphics.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, PANEL);

        graphics.fill(x + 1, y + 1, x + w - 1, y + BEVEL + 1, PANEL_LIT);
        graphics.fill(x + 1, y + 1, x + BEVEL + 1, y + h - 1, PANEL_LIT);
        graphics.fill(x + 1, y + h - BEVEL - 1, x + w - 1, y + h - 1, PANEL_DARK);
        graphics.fill(x + w - BEVEL - 1, y + 1, x + w - 1, y + h - 1, PANEL_DARK);

        // The outline last, so nothing has drawn over it.
        graphics.fill(x + 2, y, x + w - 2, y + 1, OUTLINE);
        graphics.fill(x + 2, y + h - 1, x + w - 2, y + h, OUTLINE);
        graphics.fill(x, y + 2, x + 1, y + h - 2, OUTLINE);
        graphics.fill(x + w - 1, y + 2, x + w, y + h - 2, OUTLINE);
        for (int cx : new int[] { x + 1, x + w - 2 }) {
            for (int cy : new int[] { y + 1, y + h - 2 }) {
                graphics.fill(cx, cy, cx + 1, cy + 1, OUTLINE);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Component page = Component.literal((menu.page() + 1) + " / " + menu.pages());
        graphics.drawString(font, fitted(title, room(page)), titleLabelX, titleLabelY,
                LABEL, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY,
                LABEL, false);

        if (menu.pages() > 1) {
            // One-based, because the first page is the first page and not the noughth.
            // Right-aligned against the arrows, so it grows leftwards into empty lid
            // rather than moving them when it goes from "9 / 32" to "10 / 32".
            graphics.drawString(font, page, controls - BESIDE - font.width(page), TEXT_Y,
                    LABEL, false);
        }
    }

    /** How much of the lid the title may have: everything up to what is already there. */
    private int room(Component page) {
        int leftmost = menu.pages() > 1
                ? controls - BESIDE - font.width(page)
                : imageWidth - TITLE_X - BUTTON;
        return leftmost - titleLabelX - BESIDE;
    }

    private Component fitted(Component text, int room) {
        if (font.width(text) <= room) {
            return text;
        }
        String cut = font.plainSubstrByWidth(text.getString(), room - font.width(ELLIPSIS));
        return Component.literal(cut + ELLIPSIS);
    }

    /**
     * The same two lines vanilla's chest screen has, and no more.
     *
     * <p>{@code AbstractContainerScreen#render} already dims the world behind it. Doing
     * it again here as well darkened the whole screen twice over.
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        super.render(graphics, mouseX, mouseY, partial);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
