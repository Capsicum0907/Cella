package io.github.capsicum0907.cella.client;

import io.github.capsicum0907.cella.Cella;
import io.github.capsicum0907.cella.Order;
import io.github.capsicum0907.cella.Look;
import io.github.capsicum0907.cella.CellaMenu;
import io.github.capsicum0907.cella.Mods;

import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.EditBox;
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

    private static final int PANEL = 0xFFC6C6C6;
    private static final int PANEL_LIT = 0xFFFFFFFF;
    private static final int PANEL_DARK = 0xFF555555;
    private static final int OUTLINE = 0xFF000000;

    /** How deep the highlight and the shadow run, counted off vanilla's own panel. */
    private static final int BEVEL = 3;

    /** Where vanilla draws a container title, and so where the magnifier goes. */
    private static final int TITLE_X = 8;

    /**
     * How long the box waits after the last keystroke before asking.
     *
     * <p>⚠ <b>Because the asking is not free.</b> A query is walked against every non-empty
     * slot on the server, and a full Cella Max is 221,184 of them — a packet per keystroke
     * would be that walk per keystroke. Four ticks is a fifth of a second: long enough that
     * typing a word costs one search rather than six, short enough that nobody waits.
     */
    private static final int SETTLES = 4;

    private static final int BUTTON = IconButton.SIZE;

    /**
     * Everything in the lid sits on the line vanilla puts a container's title on.
     *
     * <p>⚠ <b>That line is the reference, and it was not being used as one.</b> The buttons
     * were laid out from the top of the lid and the page number from the buttons, so the
     * whole row drifted a pixel or two above the title it was sitting beside — enough to
     * read as sloppy without being obvious enough to point at.
     *
     * <p>The text is seven pixels of ink starting here, so its middle is at {@code +3}; a
     * ten-pixel button centred on that starts a pixel and a half above, which rounds to
     * one. Half a pixel is the price of centring an even height on an odd one.
     */
    private static final int TEXT_Y = 6;
    private static final int BUTTON_Y = TEXT_Y - 2;

    /**
     * The game's own search icon, borrowed rather than drawn.
     *
     * <p>⚠ <b>Two attempts at a six-pixel magnifier were a lozenge with a tail.</b> Vanilla
     * has one — twelve pixels, a grey rim, blue glass and a wooden handle, <b>painted as an
     * object rather than drawn as an outline</b>, which is what both of mine were missing.
     * Pointing at the file rather than copying it also means a resource pack that restyles
     * the game restyles this too.
     *
     * <p>It is twelve and the other buttons are ten, and it is blitted at twelve: pixel art
     * resampled to a size it was not drawn at stops being pixel art. Two pixels at the far
     * end of the same row is not a difference anybody sees.
     */
    private static final ResourceLocation FIND =
            ResourceLocation.withDefaultNamespace("textures/gui/sprites/icon/search.png");

    private static final int FIND_SIZE = 12;

    /**
     * ⚠ <b>The same top as the ten-pixel buttons, not the same middle.</b>
     *
     * <p>The lid is eighteen pixels and the panel's highlight takes the first three, so
     * what is left to stand in is thirteen — and a twelve-pixel icon centred on the line
     * vanilla writes a title on would start at three, in the highlight. It cannot be
     * centred there; that is arithmetic and not taste.
     *
     * <p>So the tops line up instead, which is its own kind of alignment and the one that
     * survives two sizes in a row. Everything ends up within half a pixel of the title's
     * middle, the icon a shade below and the buttons a shade above.
     */
    private static final int FIND_Y = BUTTON_Y;

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
        this.imageHeight = CellaMenu.height(rows);
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
        // ⚠ The tooltip names the order, so it is rewritten when the order changes rather
        // than written once at opening. See containerTick.
        ordering = addRenderableWidget(new IconButton(leftPos + sort, topPos + BUTTON_Y,
                icon("sort"), sorting(), () -> send(CellaMenu.SORT)));
        kept = menu.order();

        // The two arrows next to each other. They were either side of the number, which
        // put fifty pixels between them - a long way to travel to press one twice.
        //
        // ⚠ Always built, and hidden when there is one page. They used not to exist at all
        // in that case, which was fine while the number of pages was fixed at opening -
        // searching changes it while the screen is up, and a widget that was never made
        // cannot come back.
        int next = sort - APART - BUTTON;
        controls = next - SPACE - BUTTON;
        back = addRenderableWidget(new IconButton(leftPos + controls, topPos + BUTTON_Y,
                icon("prev"), Component.translatable("gui.cella.prev"), () -> turn(-1)));
        on = addRenderableWidget(new IconButton(leftPos + next, topPos + BUTTON_Y,
                icon("next"), Component.translatable("gui.cella.next"), () -> turn(1)));

        // ⚠ Only where searching is one of the things this form can do. It was on every
        // screen, including a Larval's one page, which is a control answering a question
        // nobody had - and it made a property of the ladder into scenery.
        if (!menu.kind().trait().finds()) {
            paging();
            return;
        }

        // The magnifier where the title used to start, and the title moved along. It is the
        // switch for that row rather than for the contents, so it belongs beside the thing
        // it changes - and once the row is a box, a magnifier at its left edge is what a
        // search box looks like everywhere else.
        this.titleLabelX = TITLE_X + FIND_SIZE + SPACE;
        addRenderableWidget(new IconButton(leftPos + TITLE_X, topPos + FIND_Y, FIND_SIZE,
                FIND, FIND_SIZE, Component.translatable("gui.cella.find"), false, this::toggle));

        // ⚠ At the title's own y, not near it. An unbordered EditBox draws its text at
        // getY() flat - the centring in the middle of its box only happens when it has a
        // border - so anything else here puts the query on a different line from the name
        // it replaced. It was four pixels high.
        looking = new EditBox(font, leftPos + titleLabelX, topPos + TEXT_Y,
                controls - titleLabelX - BESIDE - font.width("99 / 99"), font.lineHeight,
                Component.translatable("gui.cella.find"));
        looking.setMaxLength(Look.LONGEST);
        // Unbordered and in the title's own grey, because it is standing where the title
        // stands: the lid should look like the lid with a word in it, not like a form.
        looking.setBordered(false);
        looking.setTextColor(LABEL);
        looking.setHint(Component.translatable("gui.cella.find.hint"));
        looking.setResponder(typed -> settles = SETTLES);
        looking.setVisible(false);
        addRenderableWidget(looking);
        paging();
    }

    private IconButton back;
    private IconButton on;
    private EditBox looking;

    /** What the server was last told, so an unchanged box asks nothing. */
    private String asked = "";

    /** Ticks left before the box asks. Negative when there is nothing to ask. */
    private int settles = -1;

    /** Whether the row is a box at the moment. */
    private boolean finding;

    /** Set when the box opens; see the note in {@link #toggle}. */
    private boolean grabbing;

    /**
     * Opens the box, or shuts it and puts the chest back.
     *
     * <p>Shutting it clears the query, which is the only thing it could sensibly do: a
     * hidden box with a word still in it would be a chest showing four of its slots for
     * reasons nothing on the screen explains.
     */
    private void toggle() {
        finding = !finding;
        looking.setVisible(finding);
        looking.setFocused(finding);
        setFocused(finding ? looking : null);
        if (!finding && !looking.getValue().isEmpty()) {
            looking.setValue("");
        }
        settles = finding ? -1 : 0;
        // ⚠ Asked for again next tick, because this is not the last word on it. The click
        // that got here is still being dispatched, and when the handler returns the screen
        // gives focus to whatever was clicked - which is the magnifier, not the box. Doing
        // it now and again in a moment is the difference between a box that is open and a
        // box you can type into.
        grabbing = finding;
    }

    /** Arrows only where there is somewhere to go. Asked every tick, because it changes. */
    private void paging() {
        boolean many = menu.pages() > 1;
        back.visible = many;
        on.visible = many;
    }

    /** The sort button, kept because what it says changes. */
    private IconButton ordering;

    /** What the tooltip was last written for, so it is not rebuilt every tick. */
    private Order kept;

    private Component sorting() {
        return Component.translatable("gui.cella.sort", menu.order().label());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        paging();
        if (ordering != null && kept != menu.order()) {
            kept = menu.order();
            ordering.setTooltip(net.minecraft.client.gui.components.Tooltip.create(sorting()));
        }
        if (grabbing) {
            setFocused(looking);
            looking.setFocused(true);
            grabbing = false;
        }
        if (settles < 0) {
            return;
        }
        if (settles-- == 0 && !looking.getValue().equals(asked)) {
            asked = looking.getValue();
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new Look(asked));
        }
    }

    /**
     * Lets the row go without shutting it: the word stays, and the keyboard comes back.
     *
     * <p><b>Not {@link #toggle}.</b> Shutting the box clears the query, which is right for
     * the magnifier and wrong here — the whole point of letting go is to keep looking at
     * what the search turned up while the keys do what they usually do. An {@code EditBox}
     * that is focused answers {@code canConsumeInput}, and while it does, every letter is
     * a letter typed into it rather than a key the game knows.
     */
    private void letGo() {
        looking.setFocused(false);
        if (getFocused() == looking) {
            setFocused(null);
        }
    }

    /**
     * ⚠ <b>Clicking somewhere else does not take focus off a box by itself.</b>
     *
     * <p>{@code ContainerEventHandler} gives focus to a child that answers a click and
     * leaves focus where it is when nothing answers — and a slot is not a child. So
     * clicking a slot to look it up in a recipe viewer left the box holding the keyboard,
     * which is the thing this is here to stop.
     *
     * <p>⚠ <b>Asked before the click is dispatched</b>, because dispatching it can open
     * the box: the magnifier is a child, it answers, and {@link #toggle} focuses the box
     * on the way past. {@code grabbing} says that just happened.
     */
    @Override
    public boolean mouseClicked(double x, double y, int button) {
        boolean onBox = finding && looking.isMouseOver(x, y);
        boolean handled = super.mouseClicked(x, y, button);
        if (!onBox && !grabbing && looking.isFocused()) {
            letGo();
        }
        return handled;
    }

    /**
     * ⚠ <b>The inventory key has to reach the box before it reaches the screen.</b>
     *
     * <p>{@code AbstractContainerScreen} closes on it, and an {@code EditBox} answers false
     * to a plain letter — those arrive as typed characters, not as key presses — so
     * pressing E while typing would shut the chest instead of writing an E. Asking the box
     * whether it <em>could</em> take the key is the question that gets this right; asking
     * whether it did take it is the question that does not.
     */
    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (finding && key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            toggle();
            return true;
        }
        if (finding && looking.isFocused()
                && (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                        || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)) {
            // Asked for now rather than after the wait: the wait is there to keep a chest
            // of two hundred thousand slots from being walked once per keystroke, and
            // somebody who has pressed enter has finished typing. Nought rather than a
            // send from here, so that containerTick stays the only place that asks.
            settles = 0;
            letGo();
            return true;
        }
        if (finding && (looking.keyPressed(key, scan, modifiers) || looking.canConsumeInput())) {
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
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
     * <p><b>A frame is drawn where there is a slot, and nowhere else.</b> The last page of
     * a chest built to older numbers is a short one — a chest keeps the size it was made
     * at, and that size need not divide by a page — and the squares past its end were
     * being drawn anyway, so that the grid came out rectangular. That was wrong, and the
     * comment defending it said so without noticing: <i>an empty frame is what an empty
     * slot looks like.</i> Which is the reason not to draw one where there is no slot.
     * Forty-five squares that cannot be hovered, clicked or filled, drawn exactly like
     * forty-five that can, is the screen lying about what is there — the same fault as
     * every other one this mod has had, wearing a tidier coat. Bare panel is what nothing
     * looks like.
     *
     * <p>So every frame is read off the menu, the chest's and the player's alike. One
     * list of where things are, and the picture and the clicking both use it.
     */
    @Override
    protected void renderBg(GuiGraphics graphics, float partial, int mouseX, int mouseY) {
        panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        for (net.minecraft.world.inventory.Slot slot : menu.slots) {
            if (slot.isActive()) {
                graphics.blit(BACKGROUND, leftPos + slot.x - 1, topPos + slot.y - 1,
                        SLOT_U, SLOT_V, SLOT, SLOT);
            }
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
        if (!finding) {
            // ⚠ The percentage is an extra, and an extra that does not fit is dropped
            // whole. Trimming the composed string instead is what produced "Imperfect
            // Cella (..." - half a percentage is worse than none, because it reads as the
            // name having been cut when it is the figure that was.
            int room = room(page);
            Component named = title;
            if (menu.kind().grows()) {
                Component both = Component.translatable("container.cella.grown", title,
                        menu.grown());
                if (font.width(both) <= room) {
                    named = both;
                }
            }
            graphics.drawString(font, fitted(named, room), titleLabelX, titleLabelY,
                    LABEL, false);
        }
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY,
                LABEL, false);

        if (menu.pages() > 1 || finding) {
            // One-based, because the first page is the first page and not the noughth.
            // Right-aligned against the arrows, so it grows leftwards into empty lid
            // rather than moving them when it goes from "9 / 32" to "10 / 32".
            //
            // ⚠ Shown while searching even at one page, because then it is not a control
            // saying where you are but the answer saying how big it is. A search that
            // came back is worth a number; a chest that has one page is not.
            graphics.drawString(font, page, controls - BESIDE - font.width(page), TEXT_Y,
                    LABEL, false);
        }

        if (menu.nothingShown()) {
            // ⚠ Written across the bare panel rather than left blank. Empty is what a
            // chest with nothing in it also looks like, and the difference between "your
            // word found nothing" and "this chest is empty" is the whole of what the
            // player wants to know. See CellaMenu#nothingShown.
            Component none = Component.translatable("gui.cella.find.none");
            int top = menu.slots.getFirst().y;
            int middle = top + (menu.rows() * CellaMenu.SLOT - font.lineHeight) / 2;
            graphics.drawString(font, none, (imageWidth - font.width(none)) / 2, middle,
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
