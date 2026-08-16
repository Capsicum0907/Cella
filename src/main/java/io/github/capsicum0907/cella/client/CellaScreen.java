package io.github.capsicum0907.cella.client;

import io.github.capsicum0907.cella.Cella;
import io.github.capsicum0907.cella.CellaConfig;
import io.github.capsicum0907.cella.CellaMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
 * <p>Two buttons and a number. Pressing one sends {@code clickMenuButton} <b>and</b>
 * turns this side's own window, because that packet only runs on the server — the
 * screen would otherwise sit on the old page until something else forced it to
 * refresh.
 */
@IPNPlayerSideOnly
public class CellaScreen extends AbstractContainerScreen<CellaMenu> {
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    /**
     * Drawn rather than written, because the arrows that mean "sort" in a font are
     * hairline strokes at this size and did not say what the button does anyway. Three
     * bars, longest first, which is what a sort button looks like in most things.
     *
     * <p>Eleven square, and the numbers passed to blit are the file's own size — the
     * background above is what happens when they are not.
     */
    private static final ResourceLocation SORT_ICON =
            ResourceLocation.fromNamespaceAndPath(Cella.MODID, "textures/gui/sort.png");

    /** An arrow going down into a shelf, drawn to pair with the one above. */
    private static final ResourceLocation STOW_ICON =
            ResourceLocation.fromNamespaceAndPath(Cella.MODID, "textures/gui/stow.png");

    /** The same shelf with one item standing beside the arrow. */
    private static final ResourceLocation MATCHING_ICON =
            ResourceLocation.fromNamespaceAndPath(Cella.MODID, "textures/gui/matching.png");

    /** The same two coming the other way: the shelf on top and the arrow leaving it. */
    private static final ResourceLocation TAKE_ICON =
            ResourceLocation.fromNamespaceAndPath(Cella.MODID, "textures/gui/take.png");
    private static final ResourceLocation TAKING_ICON =
            ResourceLocation.fromNamespaceAndPath(Cella.MODID, "textures/gui/taking.png");
    private static final int ICON = 11;

    /** The strip of background above the slots, and the part below them. */
    private static final int LID = 17;
    private static final int FOOT = 96;
    private static final int FOOT_V = 126;
    private static final int SLOT = 18;

    /** Where vanilla draws a container title, and so where ours starts. */
    private static final int TITLE_X = 8;

    /**
     * The two buttons and the number between them, in the strip beside the title.
     *
     * <p><b>Beside the title, not at the right-hand end of that strip, which is where
     * they were.</b> That end is not free: Inventory Profiles Next puts its sort buttons
     * there — measured at 118 to 166 across a panel 176 wide — and being added second it
     * covered these completely. Both arrows and the page number were simply not there
     * any more.
     *
     * <p>So they begin after the title and are kept clear of that end. Nothing makes a
     * corner of a container screen belong to anybody, and a mod adding a widget to
     * somebody else's screen has nobody to ask; leaving the busy end alone is cheaper
     * than winning it.
     */
    /**
     * <b>Odd on purpose.</b> A digit is seven pixels of ink; a box of twelve has its
     * centre at eight and a half, so nothing can sit in the middle of it and the number
     * came out a pixel high. Thirteen puts the box's middle and the digits' middle both
     * on nine.
     */
    private static final int BUTTON = 13;
    private static final int DIGITS = 7;
    private static final int BUTTON_Y = 3;
    private static final int TEXT_Y = BUTTON_Y + (BUTTON - DIGITS) / 2;
    private static final int GAP = 34;
    private static final int SPACE = 3;
    private static final int AFTER_TITLE = 6;

    /** Where the other mod's buttons start. These end before it. */
    private static final int TAKEN = 114;

    /** The four movers, in the order they are drawn, and what each is called. */
    private static final int[] MOVES = {
            CellaMenu.STOW, CellaMenu.MATCHING, CellaMenu.TAKING, CellaMenu.TAKE };
    private static final String[] NAMES = { "stow", "matching", "taking", "take" };

    private final int rows;

    /** Left edge of the page controls, worked out once the title is known. */
    private int controls;

    /** Left edge of the stow button, which lives beside the inventory it empties. */
    private int stowX;

    public CellaScreen(CellaMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        // A page's worth of rows, not the chest's. The menu now holds every slot, so
        // asking it how many it has would size the panel to forty-eight rows.
        // imageWidth is left at the 176 the superclass already has.
        this.rows = menu.rows();
        this.imageHeight = 114 + rows * SLOT;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    /**
     * After the title, and never past what another mod has taken.
     *
     * <p>Measured from the title rather than fixed, so a renamed chest pushes these
     * along instead of being written over. A name long enough to reach the clamp loses
     * the argument to the buttons, which is the right way round: the buttons have
     * somewhere they must be and the name does not.
     */
    private int controlsX() {
        int after = TITLE_X + font.width(title) + AFTER_TITLE;
        return Math.min(after, TAKEN - width());
    }

    /** Prev, the number, next, a space, and sort. */
    private static int width() {
        return BUTTON + GAP + BUTTON + SPACE + BUTTON;
    }

    @Override
    protected void init() {
        super.init();

        // Beside the label of the thing it moves, and in the one row of this screen no
        // other mod has claimed. Measured from the label so a translation pushes it
        // along rather than being written over.
        // Four, in the row beside the inventory they move to and from. Left to right
        // they go into the chest and then back out of it, so the two either side of the
        // middle are the two that think about what they are moving.
        stowX = TITLE_X + font.width(playerInventoryTitle) + AFTER_TITLE;
        for (int at = 0; at < MOVES.length; at++) {
            int id = MOVES[at];
            addRenderableWidget(Button.builder(Component.empty(), button -> click(id))
                    .tooltip(Tooltip.create(Component.translatable("gui.cella." + NAMES[at])))
                    .bounds(leftPos + stowX + at * (BUTTON + SPACE), topPos + inventoryLabelY - 2,
                            BUTTON, BUTTON)
                    .build());
        }

        if (menu.pages() <= 1) {
            return;
        }
        controls = controlsX();
        addRenderableWidget(Button.builder(Component.literal("<"), button -> turn(-1))
                .bounds(leftPos + controls, topPos + BUTTON_Y, BUTTON, BUTTON)
                .build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> turn(1))
                .bounds(leftPos + controls + BUTTON + GAP, topPos + BUTTON_Y, BUTTON, BUTTON)
                .build());
        // Sorting is the chest's own, not the page's: see Tidy.
        addRenderableWidget(Button.builder(Component.empty(), button -> click(CellaMenu.SORT))
                .tooltip(Tooltip.create(Component.translatable("gui.cella.sort")))
                .bounds(leftPos + controls + width() - BUTTON, topPos + BUTTON_Y, BUTTON, BUTTON)
                .build());
    }

    /**
     * Nothing is sent. The page is which slots this screen draws, and the server never
     * asks: {@code isActive} appears nowhere in {@code AbstractContainerMenu} and the
     * click path does not consult it. Under the old design this had to travel, and
     * arriving late was how a page turn drew a hole in itself.
     */
    private void turn(int by) {
        menu.turnTo(Math.floorMod(menu.page() + by, menu.pages()));
    }

    /**
     * <p><b>The six-argument blit, which is the one that assumes 256 by 256 — and the
     * file is 256 by 256.</b> Saying so explicitly with the nine-argument version is
     * what broke this the first time: the numbers passed were 176 by 222, which is the
     * size of the <em>picture</em> inside the file and not the size of the file. Every
     * texture coordinate is divided by what is declared, so the background came out
     * scaled by 176/256 across and 222/256 down while the slots stayed where they
     * belonged, and the two drifted apart towards the bottom right.
     */
    /**
     * Sent, not done here. The server owns the contents; this side only has the pages it
     * has been shown, so it waits to be told what the answer is.
     */
    private void click(int id) {
        Minecraft client = Minecraft.getInstance();
        if (client.gameMode != null) {
            client.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

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
        // Centred in the gap between the two buttons, which is the only place it fits.
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
        ResourceLocation[] icons = { STOW_ICON, MATCHING_ICON, TAKING_ICON, TAKE_ICON };
        for (int at = 0; at < icons.length; at++) {
            icon(graphics, icons[at], stowX + at * (BUTTON + SPACE), inventoryLabelY - 2);
        }
        if (menu.pages() > 1) {
            // After the widgets, because the button draws its own face first. Centred
            // by the pixel: eleven in thirteen leaves one either side.
            icon(graphics, SORT_ICON, controls + width() - BUTTON, BUTTON_Y);
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    /**
     * An icon in the middle of a button, drawn after it because the button draws its own
     * face first. Eleven in thirteen leaves one either side, which is why both are odd.
     */
    private void icon(GuiGraphics graphics, ResourceLocation which, int x, int y) {
        int inset = (BUTTON - ICON) / 2;
        graphics.blit(which, leftPos + x + inset, topPos + y + inset, 0, 0, ICON, ICON, ICON, ICON);
    }
}
