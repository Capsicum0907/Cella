package io.github.capsicum0907.cella.client;

import io.github.capsicum0907.cella.Order;
import io.github.capsicum0907.cella.Edit;
import io.github.capsicum0907.cella.Into;
import io.github.capsicum0907.cella.Peek;
import io.github.capsicum0907.cella.Plan;
import io.github.capsicum0907.cella.Shelf;
import io.github.capsicum0907.cella.Pick;
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
import net.minecraft.util.Mth;
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

    private static final int ITEM = 16;

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

    private static final int TYPED = 0xFFFFFF;

    private static final int CHIP = 8;

    private static final int CHIP_GAP = 2;

    private int chips;

    private boolean whole() {
        return menu.viewing() == Peek.WHOLE;
    }

    private int stripLeft() {
        return leftPos + inventoryLabelX + font.width(playerInventoryTitle) + APART;
    }

    private int stripRight() {
        return leftPos + movers - APART - (selector != null ? BUTTON + SPACE : 0);
    }

    private IconButton selector;

    private static final int PICKED = 0x8060A0FF;

    private int stripTop() {
        return topPos + inventoryLabelY - 1;
    }

    private int fits() {
        return Math.max(0, (stripRight() - stripLeft() + CHIP_GAP) / (CHIP + CHIP_GAP));
    }

    private int shownChips() {
        return Math.min(fits(), ShelfHolder.latest().slices().size() - chips);
    }

    private int chipsLeft() {
        int many = shownChips();
        int span = Math.max(0, many * (CHIP + CHIP_GAP) - CHIP_GAP);
        int centred = leftPos + imageWidth / 2 - span / 2;
        return Mth.clamp(centred, stripLeft(), Math.max(stripLeft(), stripRight() - span));
    }

    private int chipLeft(int at) {
        return chipsLeft() + at * (CHIP + CHIP_GAP);
    }

    private int chipAt(double x, double y) {
        if (!whole() || y < stripTop() || y >= stripTop() + CHIP) {
            return Into.NONE;
        }
        int many = shownChips();
        for (int at = 0; at < many; at++) {
            int left = chipLeft(at);
            if (x >= left && x < left + CHIP) {
                return chips + at;
            }
        }
        return Into.NONE;
    }

    private void strip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!whole()) {
            return;
        }
        Shelf shelf = ShelfHolder.latest();
        chips = Mth.clamp(chips, 0, Math.max(0, shelf.slices().size() - fits()));
        int many = shownChips();
        for (int at = 0; at < many; at++) {
            int index = chips + at;
            int left = chipLeft(at);
            boolean here = index == menu.into();
            graphics.fill(left, stripTop(), left + CHIP, stripTop() + CHIP,
                    here ? 0xFF000000 : 0xFF5B5B5B);
            graphics.fill(left + 1, stripTop() + 1, left + CHIP - 1, stripTop() + CHIP - 1,
                    0xFF000000 | shelf.slices().get(index).dye().getTextureDiffuseColor());
            if (!here) {
                graphics.fill(left + 1, stripTop() + 1, left + CHIP - 1, stripTop() + CHIP - 1,
                        0x60000000);
            }
        }
    }

    private void stripTip(GuiGraphics graphics, int mouseX, int mouseY) {
        int at = chipAt(mouseX, mouseY);
        if (at == Into.NONE) {
            return;
        }
        Shelf.Slice slice = ShelfHolder.latest().slices().get(at);
        Component name = slice.name().isEmpty()
                ? Component.translatable("gui.cella.partition.unnamed")
                : Component.literal(slice.name());
        graphics.renderTooltip(font, Component.translatable("gui.cella.into.pick", name),
                mouseX, mouseY);
    }

    private static final String ELLIPSIS = "...";

    private static final int[] PLAIN = { CellaMenu.MATCHING, CellaMenu.TAKING };
    private static final int[] WIDE = { CellaMenu.STOW, CellaMenu.TAKE };
    private static final String[] NAMES = { "stow", "take" };
    private static final String[] MOVERS = { Icons.STOW, Icons.TAKE };

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
                    Icons.of(MOVERS[at]), told(NAMES[at]),
                    () -> send(Screen.hasShiftDown() ? wide : plain)));
        }

        if (divides()) {
            selector = addRenderableWidget(new IconButton(
                    leftPos + movers - SPACE - BUTTON, topPos + inventoryLabelY - 2,
                    Icons.of(Icons.SELECT), Component.translatable("gui.cella.select"),
                    () -> send(CellaMenu.SELECT)));
        }

        int left = TITLE_X;
        knob = divides() ? left : -1;
        if (knob >= 0) {
            left += BUTTON + SPACE;
        }
        int sort = imageWidth - TITLE_X - BUTTON;

        ordering = addRenderableWidget(new IconButton(leftPos + sort, topPos + BUTTON_Y,
                Icons.of(Icons.SORT), sorting(), () -> send(CellaMenu.SORT)));
        kept = menu.order();
        peek(Peek.LIST, false);
        cell = new net.minecraft.client.gui.components.EditBox(font, 0, 0, 10, 12,
                Component.empty());
        cell.visible = false;

        int next = sort - APART - BUTTON;
        controls = next - SPACE - BUTTON;
        back = addRenderableWidget(new IconButton(leftPos + controls, topPos + BUTTON_Y,
                Icons.of(Icons.PREV), Component.translatable("gui.cella.prev"), () -> turn(-1)));
        on = addRenderableWidget(new IconButton(leftPos + next, topPos + BUTTON_Y,
                Icons.of(Icons.NEXT), Component.translatable("gui.cella.next"), () -> turn(1)));

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
        looking.setTextColor(TYPED);
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
        if (menu.selecting()) {
            return;
        }
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
        if (selector != null) {
            selector.visible = !list;
            selector.icon(Icons.of(menu.selecting() ? Icons.SELECTED : Icons.SELECT));
        }
        if (looking != null) {
            looking.setEditable(!menu.selecting());
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
        if (looking == null) {
            return;
        }
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
        int chip = chipAt(x, y);
        if (chip != Into.NONE) {
            int wanted = chip == menu.into() ? Into.NONE : chip;
            menu.into(wanted);
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new Into(wanted));
            return true;
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
            if (at == Peek.WHOLE) {
                pane.unpick();
                peek(Peek.WHOLE, true);
                return true;
            }
            if (at != Peek.LIST) {
                if (pane.chosen() == at) {
                    peek(at, true);
                } else {
                    pane.choose(at);
                    peek(at, false);
                }
                return true;
            }
            if (pane.chosen() != Peek.LIST) {
                pane.unpick();
                peek(Peek.LIST, false);
            }
        }
        boolean onBox = finding && looking != null && looking.isMouseOver(x, y);
        boolean handled = super.mouseClicked(x, y, button);
        if (!onBox && !grabbing && looking != null && looking.isFocused()) {
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
                        Edit.setting(modal.palette(), slice.name(), picked,
                                slice.length()));
            }
            modal.shut();
            return true;
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
                    net.minecraft.world.item.DyeColor.byId(1 + EditModal.count(shelf) % 15),
                    0));
            return true;
        }
        int column = modal.columnOf(shelf, x, modalLeft(), modalWide());
        if (column == EditModal.SWATCH_COLUMN) {
            modal.pick(row);
        } else if (column == EditModal.PACK_COLUMN) {
            Shelf.Slice slice = shelf.slices().get(row);
            int least = (slice.used() + Plan.LC - 1) / Plan.LC;
            if (least < slice.length()) {
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        Edit.setting(row, slice.name(), slice.dye(), least));
            }
        } else if (column == EditModal.OUTLET_COLUMN) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                    io.github.capsicum0907.cella.Assign.toggled(row, shelf.assigned()));
        } else if (column == EditModal.BIN_COLUMN) {
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

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (modal.open() && cell != null && cell.visible) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                    || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                commit();
                return true;
            }
            if (key != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
                    && (cell.keyPressed(key, scan, modifiers) || cell.canConsumeInput())) {
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
        if (modal.open() && cell != null && cell.visible) {
            return true;
        }
        if (modal.open() && scrollY != 0
                && modal.scrolled(ShelfHolder.latest(), scrollY, modalTall())) {
            return true;
        }
        if (listing() && scrollY != 0
                && pane.scrolled(ShelfHolder.latest(), scrollY, paneTall())) {
            return true;
        }
        if (whole() && scrollY != 0 && mouseY >= stripTop() && mouseY < stripTop() + CHIP
                && ShelfHolder.latest().slices().size() > fits()) {
            chips = Mth.clamp(chips - (int) scrollY, 0,
                    ShelfHolder.latest().slices().size() - fits());
            return true;
        }
        if (menu.pages() > 1 && scrollY != 0 && overPanel(mouseX, mouseY)
                && (hoveredSlot == null || !hoveredSlot.hasItem())) {
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

    private final ShelfPane pane = new ShelfPane();

    private final EditModal modal = new EditModal();

    private net.minecraft.client.gui.components.EditBox cell;

    private int modalLeft() {
        return leftPos + (imageWidth - modalWide()) / 2;
    }

    private int modalTop() {
        return topPos + 6;
    }

    private int modalWide() {
        return Math.max(imageWidth - 12, EditModal.least());
    }

    private int modalTall() {
        return Math.max(menu.rows() * CellaMenu.SLOT + 24, EditModal.leastTall());
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

    private void knob(GuiGraphics graphics, int slot, String icon) {
        graphics.fill(leftPos + slot, gearY(), leftPos + slot + BUTTON, gearY() + BUTTON,
                0xFF373737);
        graphics.fill(leftPos + slot + 1, gearY() + 1, leftPos + slot + BUTTON - 1,
                gearY() + BUTTON - 1, 0xFF8B8B8B);
        Icons.draw(graphics, icon, leftPos + slot + Icons.inset(BUTTON),
                gearY() + Icons.inset(BUTTON), LABEL);
    }

    private void knobs(GuiGraphics graphics) {
        if (knob >= 0) {
            knob(graphics, knob, listing() ? Icons.LIST : Icons.BACK);
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

    private final java.util.List<net.minecraft.client.gui.components.AbstractWidget> veiled =
            new java.util.ArrayList<>();

    private void veil(boolean on) {
        if (on) {
            if (!veiled.isEmpty()) {
                return;
            }
            for (net.minecraft.client.gui.components.events.GuiEventListener child : children()) {
                if (child instanceof net.minecraft.client.gui.components.AbstractWidget one
                        && one.visible) {
                    one.visible = false;
                    veiled.add(one);
                }
            }
        } else {
            for (net.minecraft.client.gui.components.AbstractWidget one : veiled) {
                one.visible = true;
            }
            veiled.clear();
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
                && modal.columnOf(shelf, mouseX, modalLeft(), modalWide()) == EditModal.BIN_COLUMN
                && shelf.slices().get(row).used() > 0) {
            graphics.renderTooltip(font, Component.translatable("gui.cella.edit.full"),
                    mouseX, mouseY);
        }
        if (row >= 0 && row < shelf.slices().size()
                && modal.columnOf(shelf, mouseX, modalLeft(), modalWide())
                        == EditModal.PACK_COLUMN) {
            graphics.renderTooltip(font, Component.translatable("gui.cella.edit.pack"),
                    mouseX, mouseY);
        }
        if (row >= 0 && row < shelf.slices().size()
                && modal.columnOf(shelf, mouseX, modalLeft(), modalWide())
                        == EditModal.OUTLET_COLUMN) {
            graphics.renderTooltip(font, Component.translatable("gui.cella.edit.outlet"),
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

    @Override
    protected void slotClicked(net.minecraft.world.inventory.Slot slot, int id, int button,
            net.minecraft.world.inventory.ClickType type) {
        if (menu.selecting()) {
            if (slot != null && slot.index < menu.pageSize()) {
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(new Pick(
                        menu.page() * menu.pageSize() + slot.index, true));
                dragFrom = slot.index;
                swept = false;
                dragged.clear();
                return;
            }
            if (type == net.minecraft.world.inventory.ClickType.QUICK_MOVE
                    || type == net.minecraft.world.inventory.ClickType.PICKUP_ALL) {
                return;
            }
        }
        super.slotClicked(slot, id, button, type);
    }

    private int dragFrom = -1;

    private final java.util.Set<Integer> dragged = new java.util.HashSet<>();

    private boolean swept;

    private int chestSlotAt(double x, double y) {
        for (int at = 0; at < menu.pageSize(); at++) {
            net.minecraft.world.inventory.Slot slot = menu.slots.get(at);
            if (slot.isActive() && isHovering(slot.x, slot.y, ITEM, ITEM, x, y)) {
                return at;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (!menu.selecting() || dragFrom < 0 || button != 0) {
            return super.mouseDragged(x, y, button, dx, dy);
        }
        int here = chestSlotAt(x, y);
        if (here < 0 || (here == dragFrom && !swept)) {
            return true;
        }
        swept = true;
        int columns = menu.columns();
        int top = Math.min(dragFrom / columns, here / columns);
        int bottom = Math.max(dragFrom / columns, here / columns);
        int left = Math.min(dragFrom % columns, here % columns);
        int right = Math.max(dragFrom % columns, here % columns);
        for (int row = top; row <= bottom; row++) {
            for (int column = left; column <= right; column++) {
                int at = row * columns + column;
                int shown = menu.page() * menu.pageSize() + at;
                if (at < menu.pageSize() && menu.slots.get(at).hasItem()
                        && !menu.selected(shown) && dragged.add(shown)) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                            new Pick(shown, false));
                }
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (dragFrom >= 0 && button == 0) {
            dragFrom = -1;
            dragged.clear();
            if (menu.selecting()) {
                return true;
            }
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    protected void renderSlot(GuiGraphics graphics, net.minecraft.world.inventory.Slot slot) {
        super.renderSlot(graphics, slot);
        if (menu.selecting() && slot.index < menu.pageSize()
                && menu.selected(menu.page() * menu.pageSize() + slot.index)) {
            renderSlotHighlight(graphics, slot.x, slot.y, 0, PICKED);
        }
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
        strip(graphics, mouseX, mouseY);
        java.util.List<Shelf.Slice> slices = ShelfHolder.latest().slices();
        for (net.minecraft.world.inventory.Slot slot : menu.slots) {
            if (slot.isActive()) {
                graphics.blit(BACKGROUND, leftPos + slot.x - 1, topPos + slot.y - 1,
                        SLOT_U, SLOT_V, SLOT, SLOT);
                int owner = slot.index < menu.pageSize()
                        && isHovering(slot.x, slot.y, ITEM, ITEM, mouseX, mouseY)
                        ? menu.owner(slot.index)
                        : -1;
                if (owner >= 0 && owner < slices.size()) {
                    graphics.renderOutline(leftPos + slot.x - 1, topPos + slot.y - 1, SLOT, SLOT,
                            0xFF000000 | slices.get(owner).dye().getTextureDiffuseColor());
                }
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

    private String openName() {
        java.util.List<Shelf.Slice> slices = ShelfHolder.latest().slices();
        int open = menu.viewing();
        return open >= 0 && open < slices.size() ? slices.get(open).name() : "";
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Component page = Component.literal((menu.page() + 1) + " / " + menu.pages());
        if (!finding) {
            int room = room(page);
            Component named = title;
            String partition = openName();
            if (!partition.isEmpty()) {
                named = Component.literal(partition);
            } else if (menu.kind().grows()) {
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

        if (menu.nothingShown() && !listing() && finding
                && looking != null && !looking.getValue().isBlank()) {
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
        veil(modal.open());
        super.render(graphics, mouseX, mouseY, partial);
        renderTooltip(graphics, mouseX, mouseY);
        if (!modal.open()) {
            stripTip(graphics, mouseX, mouseY);
        }
        overlay(graphics, mouseX, mouseY, partial);
    }
}
