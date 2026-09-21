package io.github.capsicum0907.cella.client;

import io.github.capsicum0907.cella.Cella;
import io.github.capsicum0907.cella.Order;
import io.github.capsicum0907.cella.Edit;
import io.github.capsicum0907.cella.Peek;
import io.github.capsicum0907.cella.Shelf;
import io.github.capsicum0907.cella.ShelfHolder;
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

@IPNPlayerSideOnly
public class CellaScreen extends AbstractContainerScreen<CellaMenu> {
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    private static final int LID = 17;
    private static final int SLOT = 18;

    private static final int SLOT_U = 7;
    private static final int SLOT_V = 17;

    private static final int PANEL = 0xFFC6C6C6;
    private static final int PANEL_LIT = 0xFFFFFFFF;
    private static final int PANEL_DARK = 0xFF555555;
    private static final int OUTLINE = 0xFF000000;

    private static final int BEVEL = 3;

    private static final int TITLE_X = 8;

    private static final int SETTLES = 4;

    private static final int BUTTON = IconButton.SIZE;

    private static final int TEXT_Y = 6;
    private static final int BUTTON_Y = TEXT_Y - 2;

    private static final ResourceLocation FIND =
            ResourceLocation.withDefaultNamespace("textures/gui/sprites/icon/search.png");

    private static final int FIND_SIZE = 12;

    private static final int FIND_Y = BUTTON_Y;

    private static final int SPACE = 2;
    private static final int APART = 4;
    private static final int AFTER_TITLE = 6;

    private static final int BESIDE = 3;

    private static final int LABEL = 0x404040;

    private static final String ELLIPSIS = "...";

    private static final int[] PLAIN = { CellaMenu.MATCHING, CellaMenu.TAKING };
    private static final int[] WIDE = { CellaMenu.STOW, CellaMenu.TAKE };
    private static final String[] NAMES = { "stow", "take" };

    private final int rows;

    private int controls;
    private int movers;

    public CellaScreen(CellaMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);

        this.rows = menu.rows();
        this.imageWidth = menu.width();
        this.imageHeight = CellaMenu.height(rows);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    private static Component told(String name) {
        return Component.translatable("gui.cella." + name)
                .append(CommonComponents.NEW_LINE)
                .append(Component.translatable("gui.cella." + name + ".shift")
                        .withStyle(ChatFormatting.GRAY));
    }

    private static ResourceLocation icon(String name) {
        return ResourceLocation.fromNamespaceAndPath(Cella.MODID, "textures/gui/" + name + ".png");
    }

    private int after(Component label, int width) {
        return Math.min(TITLE_X + font.width(label) + AFTER_TITLE, imageWidth - TITLE_X - width);
    }

    @Override
    protected void init() {
        super.init();

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

        int left = TITLE_X;
        knob = divides() ? left : -1;
        if (knob >= 0) {
            left += BUTTON + SPACE;
        }
        int sort = imageWidth - TITLE_X - BUTTON;

        ordering = addRenderableWidget(new IconButton(leftPos + sort, topPos + BUTTON_Y,
                icon("sort"), sorting(), () -> send(CellaMenu.SORT)));
        kept = menu.order();
        peek(Peek.LIST, false);
        cell = new net.minecraft.client.gui.components.EditBox(font, 0, 0, 10, 12,
                Component.empty());
        cell.visible = false;

        int next = sort - APART - BUTTON;
        controls = next - SPACE - BUTTON;
        back = addRenderableWidget(new IconButton(leftPos + controls, topPos + BUTTON_Y,
                icon("prev"), Component.translatable("gui.cella.prev"), () -> turn(-1)));
        on = addRenderableWidget(new IconButton(leftPos + next, topPos + BUTTON_Y,
                icon("next"), Component.translatable("gui.cella.next"), () -> turn(1)));

        if (!menu.kind().trait().finds()) {
            this.titleLabelX = left;
            paging();
            return;
        }

        int find = left;
        this.titleLabelX = find + FIND_SIZE + SPACE;
        addRenderableWidget(new IconButton(leftPos + find, topPos + FIND_Y, FIND_SIZE,
                FIND, FIND_SIZE, Component.translatable("gui.cella.find"), false, this::toggle));

        looking = new EditBox(font, leftPos + titleLabelX, topPos + TEXT_Y,
                controls - titleLabelX - BESIDE - font.width("99 / 99"), font.lineHeight,
                Component.translatable("gui.cella.find"));
        looking.setMaxLength(Look.LONGEST);

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

    private String asked = "";

    private int settles = -1;

    private boolean finding;

    private boolean grabbing;

    private void toggle() {
        finding = !finding;
        looking.setVisible(finding);
        looking.setFocused(finding);
        setFocused(finding ? looking : null);
        if (!finding && !looking.getValue().isEmpty()) {
            looking.setValue("");
        }
        settles = finding ? -1 : 0;

        grabbing = finding;
    }

    private void paging() {
        boolean many = menu.pages() > 1;
        back.visible = many;
        on.visible = many;
    }

    private IconButton ordering;

    private Order kept;

    private Component sorting() {
        return Component.translatable("gui.cella.sort", menu.order().label());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        paging();
        boolean list = listing();
        if (ordering != null) {
            ordering.visible = !list;
        }
        if (back != null && on != null) {
            boolean many = menu.pages() > 1 && !list;
            back.visible = many;
            on.visible = many;
        }
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

    private void letGo() {
        looking.setFocused(false);
        if (getFocused() == looking) {
            setFocused(null);
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (modal.open()) {
            return inModal(x, y, button);
        }
        if (overKnob(x, y)) {
            if (listing()) {
                modal.open(true);
                peek(Peek.LIST, false);
            } else {
                pane.forget();
                peek(Peek.LIST, true);
            }
            return true;
        }
        if (listing()) {
            int at = pane.hit(ShelfHolder.latest(), x, y, paneLeft(), paneTop(),
                    paneWide(), paneTall());
            if (at != Peek.LIST) {
                if (pane.chosen() == at) {
                    peek(at, true);
                } else {
                    pane.choose(at);
                    peek(at, false);
                }
                return true;
            }
        }
        boolean onBox = finding && looking.isMouseOver(x, y);
        boolean handled = super.mouseClicked(x, y, button);
        if (!onBox && !grabbing && looking.isFocused()) {
            letGo();
        }
        return handled;
    }

    private boolean inModal(double x, double y, int button) {
        Shelf shelf = ShelfHolder.latest();
        if (modal.picking()) {
            net.minecraft.world.item.DyeColor picked =
                    modal.dyeAt(x, y, modalLeft(), modalTop(), modalWide());
            if (picked != null) {
                Shelf.Slice slice = shelf.slices().get(modal.palette());
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        Edit.setting(modal.palette(), slice.name(), picked, slice.start(),
                                slice.length()));
                modal.pick(modal.palette());
                return true;
            }
        }
        if (modal.overClose(x, y, modalLeft(), modalTop())) {
            commit();
            modal.open(false);
            return true;
        }
        if (!modal.inside(x, y, modalLeft(), modalTop(), modalWide(), modalTall())) {
            if (cell != null && cell.visible) {
                commit();
                return true;
            }
            modal.open(false);
            return true;
        }
        if (cell != null && cell.visible && cell.isMouseOver(x, y)) {
            return cell.mouseClicked(x, y, button);
        }
        commit();

        int row = modal.rowAt(shelf, y, modalTop(), modalTall());
        if (row < 0) {
            return true;
        }
        if (modal.adding(shelf, row)) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(Edit.adding(
                    "Partition " + (EditModal.count(shelf) + 1),
                    net.minecraft.world.item.DyeColor.byId(EditModal.count(shelf) % 16),
                    nextGap(shelf), 1));
            return true;
        }
        int column = modal.columnOf(shelf, x, modalLeft(), modalWide());
        if (column == -2) {
            modal.pick(row);
        } else if (column == -3) {
            if (shelf.slices().get(row).used() == 0) {
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        Edit.removing(row));
            }
        } else if (column >= 0) {
            modal.edit(row, column);
            modal.place(cell, shelf, modalLeft(), modalTop(), modalWide());
        }
        return true;
    }

    private static int nextGap(Shelf shelf) {
        if (!shelf.divided()) {
            return 0;
        }
        int at = 0;
        for (Shelf.Slice slice : shelf.slices()) {
            if (slice.start() > at) {
                return at;
            }
            at = Math.max(at, slice.start() + slice.length());
        }
        return at;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (modal.open() && cell != null && cell.visible) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                    || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                commit();
                return true;
            }
            if (key != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
                    && cell.keyPressed(key, scan, modifiers)) {
                return true;
            }
        }
        if (finding && key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            toggle();
            return true;
        }
        if (finding && looking.isFocused()
                && (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                        || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)) {
            settles = 0;
            letGo();
            return true;
        }
        if (finding && (looking.keyPressed(key, scan, modifiers) || looking.canConsumeInput())) {
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (modal.open() && scrollY != 0
                && modal.scrolled(ShelfHolder.latest(), scrollY, modalTall())) {
            return true;
        }
        if (listing() && scrollY != 0
                && pane.scrolled(ShelfHolder.latest(), scrollY, paneTall())) {
            return true;
        }
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

    private void turn(int by) {
        send(Math.floorMod(menu.page() + by, menu.pages()));
    }

    private static final String GEAR = "\u2261";

    private static final String HOME = "\u25C0";

    private final ShelfPane pane = new ShelfPane();

    private final EditModal modal = new EditModal();

    private net.minecraft.client.gui.components.EditBox cell;

    private int modalLeft() {
        return leftPos + 6;
    }

    private int modalTop() {
        return topPos + 6;
    }

    private int modalWide() {
        return imageWidth - 12;
    }

    private int modalTall() {
        return menu.rows() * CellaMenu.SLOT + 24;
    }

    private int knob = -1;

    private boolean divides() {
        return menu.kind().trait().divides();
    }

    private int gearY() {
        return topPos + BUTTON_Y;
    }

    private boolean at(int slot, double x, double y) {
        return slot >= 0 && x >= leftPos + slot && x < leftPos + slot + BUTTON
                && y >= gearY() && y < gearY() + BUTTON;
    }

    private boolean overKnob(double x, double y) {
        return at(knob, x, y);
    }

    private void knob(GuiGraphics graphics, int slot, String glyph) {
        graphics.fill(leftPos + slot, gearY(), leftPos + slot + BUTTON, gearY() + BUTTON,
                0xFF373737);
        graphics.fill(leftPos + slot + 1, gearY() + 1, leftPos + slot + BUTTON - 1,
                gearY() + BUTTON - 1, 0xFF8B8B8B);
        graphics.drawString(font, glyph, leftPos + slot + 3, gearY() + 2, LABEL, false);
    }

    private void knobs(GuiGraphics graphics) {
        if (knob >= 0) {
            knob(graphics, knob, listing() ? GEAR : HOME);
        }
    }

    private void commit() {
        if (cell == null || !cell.visible) {
            return;
        }
        Edit done = modal.committed(cell, ShelfHolder.latest());
        modal.stop();
        cell.visible = false;
        cell.setFocused(false);
        if (done != null) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(done);
        }
    }

    private void overlay(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        if (!modal.open()) {
            return;
        }
        Shelf shelf = ShelfHolder.latest();
        modal.draw(graphics, font, shelf, modalLeft(), modalTop(),
                modalWide(), modalTall(), mouseX, mouseY);
        if (cell != null && cell.visible) {
            cell.render(graphics, mouseX, mouseY, partial);
        }
        int row = modal.rowAt(shelf, mouseY, modalTop(), modalTall());
        if (row >= 0 && row < EditModal.count(shelf)
                && modal.columnOf(shelf, mouseX, modalLeft(), modalWide()) == -3
                && shelf.slices().get(row).used() > 0) {
            graphics.renderTooltip(font, Component.translatable("gui.cella.edit.full"),
                    mouseX, mouseY);
        }
    }

    @Override
    public boolean charTyped(char typed, int modifiers) {
        if (modal.open() && cell != null && cell.visible) {
            return cell.charTyped(typed, modifiers);
        }
        return super.charTyped(typed, modifiers);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (listing()) {
            net.minecraft.world.item.ItemStack over = pane.over(ShelfHolder.latest(),
                    mouseX, mouseY, paneLeft(), paneTop(), paneWide(), paneTall());
            if (!over.isEmpty()) {
                graphics.renderTooltip(font, net.minecraft.network.chat.Component.literal(
                        ShelfPane.grouped(over.getCount()) + " ").append(over.getHoverName()),
                        mouseX, mouseY);
                return;
            }
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }

    private boolean listing() {
        return menu.viewing() == Peek.LIST && !ShelfHolder.latest().slices().isEmpty();
    }

    private int paneLeft() {
        return leftPos + TITLE_X;
    }

    private int paneTop() {
        return topPos + menu.slots.getFirst().y;
    }

    private int paneWide() {
        return imageWidth - 2 * TITLE_X;
    }

    private int paneTall() {
        return menu.rows() * CellaMenu.SLOT;
    }

    private void peek(int index, boolean open) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new Peek(index, open));
    }

    private void send(int id) {
        Minecraft client = Minecraft.getInstance();
        if (client.gameMode != null) {
            client.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partial, int mouseX, int mouseY) {
        panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        if (listing()) {
            pane.draw(graphics, font, ShelfHolder.latest(), paneLeft(), paneTop(),
                    paneWide(), paneTall(), mouseX, mouseY);
        }
        knobs(graphics);
        for (net.minecraft.world.inventory.Slot slot : menu.slots) {
            if (slot.isActive()) {
                graphics.blit(BACKGROUND, leftPos + slot.x - 1, topPos + slot.y - 1,
                        SLOT_U, SLOT_V, SLOT, SLOT);
            }
        }
    }

    private void panel(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x + 1, y + 2, x + w - 1, y + h - 2, PANEL);
        graphics.fill(x + 2, y + 1, x + w - 2, y + 2, PANEL);
        graphics.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, PANEL);

        graphics.fill(x + 1, y + 1, x + w - 1, y + BEVEL + 1, PANEL_LIT);
        graphics.fill(x + 1, y + 1, x + BEVEL + 1, y + h - 1, PANEL_LIT);
        graphics.fill(x + 1, y + h - BEVEL - 1, x + w - 1, y + h - 1, PANEL_DARK);
        graphics.fill(x + w - BEVEL - 1, y + 1, x + w - 1, y + h - 1, PANEL_DARK);

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
            graphics.drawString(font, page, controls - BESIDE - font.width(page), TEXT_Y,
                    LABEL, false);
        }

        if (menu.nothingShown() && !listing()) {
            Component none = Component.translatable("gui.cella.find.none");
            int top = menu.slots.getFirst().y;
            int middle = top + (menu.rows() * CellaMenu.SLOT - font.lineHeight) / 2;
            graphics.drawString(font, none, (imageWidth - font.width(none)) / 2, middle,
                    LABEL, false);
        }
    }

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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        super.render(graphics, mouseX, mouseY, partial);
        renderTooltip(graphics, mouseX, mouseY);
        overlay(graphics, mouseX, mouseY, partial);
    }
}
