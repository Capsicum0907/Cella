package io.github.capsicum0907.cella.client;

import io.github.capsicum0907.cella.Cella;
import io.github.capsicum0907.cella.CellaMenu;

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
 * <p>Five controls, all {@link IconButton}: the two page arrows and sort in the lid,
 * and beside the inventory the two ways of moving - in and out, with shift narrowing
 * either to the kinds already on the other side.
 */
@IPNPlayerSideOnly
public class CellaScreen extends AbstractContainerScreen<CellaMenu> {
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    /** The strip of background above the slots, and the part below them. */
    private static final int LID = 17;
    private static final int FOOT = 96;
    private static final int FOOT_V = 126;
    private static final int SLOT = 18;

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

    /** Room for "8 / 32" between the arrows; two apart is the neighbours' spacing. */
    private static final int GAP = 28;
    private static final int SPACE = 2;
    private static final int AFTER_TITLE = 6;

    /**
     * Two movers, not four: in and out.
     *
     * <p>There were four - in, in-matching, out-matching, out - and the person who asked
     * for them could not say what they were a day later. Four buttons meant two things to
     * tell apart at once, direction and reach, and six pixels of picture will carry one.
     *
     * <p>So reach moved onto <b>shift</b>, which in this game already means "the same
     * thing, done the other way": shift-click to move a stack, shift to place, shift to
     * take the lot. Nobody has to be taught it, and the tooltip says it anyway.
     */
    private static final int[] MOVES = { CellaMenu.STOW, CellaMenu.TAKE };
    private static final int[] PICKED = { CellaMenu.MATCHING, CellaMenu.TAKING };
    private static final String[] NAMES = { "stow", "take" };

    private final int rows;

    /** Left edge of the page controls, and of the movers, once the labels are known. */
    private int controls;
    private int movers;

    public CellaScreen(CellaMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        // A page's worth of rows, not the chest's. The menu holds every slot, so asking
        // it how many it has would size the panel to forty-eight rows. imageWidth is
        // left at the 176 the superclass already has.
        this.rows = menu.rows();
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

    /** Prev, the number, next, a space, and sort. */
    private static int lidWidth() {
        return BUTTON + GAP + BUTTON + SPACE + BUTTON;
    }

    /**
     * After a label, and never off the end of the panel.
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

        movers = after(playerInventoryTitle, MOVES.length * BUTTON + (MOVES.length - 1) * SPACE);
        for (int at = 0; at < MOVES.length; at++) {
            int whole = MOVES[at];
            int picked = PICKED[at];
            addRenderableWidget(new IconButton(
                    leftPos + movers + at * (BUTTON + SPACE), topPos + inventoryLabelY - 2,
                    icon(NAMES[at]), told(NAMES[at]),
                    () -> send(Screen.hasShiftDown() ? picked : whole)));
        }

        if (menu.pages() <= 1) {
            return;
        }
        controls = after(title, lidWidth());
        addRenderableWidget(new IconButton(leftPos + controls, topPos + BUTTON_Y,
                icon("prev"), Component.translatable("gui.cella.prev"), () -> turn(-1)));
        addRenderableWidget(new IconButton(leftPos + controls + BUTTON + GAP, topPos + BUTTON_Y,
                icon("next"), Component.translatable("gui.cella.next"), () -> turn(1)));
        addRenderableWidget(new IconButton(leftPos + controls + lidWidth() - BUTTON,
                topPos + BUTTON_Y, icon("sort"), Component.translatable("gui.cella.sort"),
                () -> send(CellaMenu.SORT)));
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
     * Turning a page tells nobody.
     *
     * <p>The page is which slots this screen draws, and the server never asks:
     * {@code isActive} appears nowhere in {@code AbstractContainerMenu} and the click
     * path does not consult it.
     */
    private void turn(int by) {
        menu.turnTo(Math.floorMod(menu.page() + by, menu.pages()));
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
     * <b>The six-argument blit, which is the one that assumes 256 by 256 — and the file
     * is 256 by 256.</b> Saying so explicitly with the nine-argument version is what
     * broke this the first time: the numbers passed were 176 by 222, which is the size of
     * the <em>picture</em> inside the file and not the size of the file. Every texture
     * coordinate is divided by what is declared, so the background came out scaled by
     * 176/256 across and 222/256 down while the slots stayed where they belonged.
     */
    @Override
    protected void renderBg(GuiGraphics graphics, float partial, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, rows * SLOT + LID);
        graphics.blit(BACKGROUND, leftPos, topPos + rows * SLOT + LID, 0, FOOT_V, imageWidth, FOOT);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        if (menu.pages() <= 1) {
            return;
        }
        // One-based, because the first page is the first page and not the noughth.
        Component page = Component.literal((menu.page() + 1) + " / " + menu.pages());
        graphics.drawString(font, page,
                controls + BUTTON + (GAP - font.width(page)) / 2, TEXT_Y, 0x404040, false);
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
