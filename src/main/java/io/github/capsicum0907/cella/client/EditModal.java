package io.github.capsicum0907.cella.client;

import io.github.capsicum0907.cella.Edit;
import io.github.capsicum0907.cella.Plan;
import io.github.capsicum0907.cella.Shelf;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;

public final class EditModal {
    public static final int ROW = 14;

    private static final int HEAD = 26;

    private static final int BAR = 6;

    private static final int PAD = 4;

    private static final int SWATCH = 10;

    private static final int NUMBER = 34;

    private static final int BIN = 12;

    private static final int LINE = 9;

    private static final int TEXT = 0x404040;
    private static final int FAINT = 0x707070;
    private static final int STOPPED = 0xA0A0A0;
    private static final int BACK = 0xFFC6C6C6;
    private static final int EDGE = 0xFF000000;
    private static final int WELL = 0xFF373737;
    private static final int TRACK = 0xFF8B8B8B;
    private static final int SPARE = 0xFF505050;
    private static final int OVER = 0x30000000;
    private static final int SHADE = 0xA0101010;

    public static final int NAME = 0;
    public static final int SIZE = 1;

    private boolean open;

    private int scroll;

    private int editing = -1;
    private int field = NAME;

    private int palette = -1;

    public boolean open() {
        return open;
    }

    public void open(boolean wanted) {
        open = wanted;
        if (!wanted) {
            editing = -1;
            palette = -1;
        }
    }

    public int editing() {
        return editing;
    }

    public int field() {
        return field;
    }

    public boolean picking() {
        return palette >= 0;
    }

    public static int count(Shelf shelf) {
        return shelf.divided() ? shelf.slices().size() : 0;
    }

    private int rows(Shelf shelf) {
        return count(shelf) + 1;
    }

    private int listTall(int tall) {
        return tall - HEAD - BAR - LINE - 1 - PAD * 3;
    }

    public boolean scrolled(Shelf shelf, double amount, int tall) {
        int most = Math.max(0, rows(shelf) * ROW - listTall(tall));
        if (most <= 0) {
            return false;
        }
        scroll = Mth.clamp(scroll - (int) (amount * ROW), 0, most);
        return true;
    }

    private int columnNumber(int x, int wide, int which) {
        return x + wide - PAD - BIN - PAD - NUMBER * (3 - which);
    }

    private int nameLeft(int x) {
        return x + PAD + SWATCH + PAD;
    }

    private int nameWide(int x, int wide) {
        return columnNumber(x, wide, 0) - nameLeft(x) - PAD;
    }

    public int rowAt(Shelf shelf, double mouseY, int y, int tall) {
        int top = y + PAD + HEAD;
        if (mouseY < top || mouseY >= top + listTall(tall)) {
            return -1;
        }
        int at = (int) ((mouseY - top + scroll) / ROW);
        return at >= 0 && at < rows(shelf) ? at : -1;
    }

    public int rowTop(int row, int y) {
        return y + PAD + HEAD + row * ROW - scroll;
    }

    public boolean adding(Shelf shelf, int row) {
        return row == count(shelf);
    }

    public int columnOf(Shelf shelf, double mouseX, int x, int wide) {
        if (mouseX >= x + PAD && mouseX < x + PAD + SWATCH) {
            return -2;
        }
        if (mouseX >= nameLeft(x) && mouseX < nameLeft(x) + nameWide(x, wide)) {
            return NAME;
        }
        int size = columnNumber(x, wide, 1);
        if (mouseX >= size && mouseX < size + NUMBER) {
            return SIZE;
        }
        if (mouseX >= x + wide - PAD - BIN && mouseX < x + wide - PAD) {
            return -3;
        }
        return -1;
    }

    public void edit(int row, int which) {
        editing = row;
        field = which;
        palette = -1;
    }

    public void stop() {
        editing = -1;
    }

    public void pick(int row) {
        palette = palette == row ? -1 : row;
    }

    public void shut() {
        palette = -1;
    }

    public int palette() {
        return palette;
    }

    public DyeColor dyeAt(double mouseX, double mouseY, int x, int y, int wide) {
        if (palette < 0) {
            return null;
        }
        int left = x + PAD;
        int top = rowTop(palette, y) + ROW;
        DyeColor[] all = DyeColor.values();
        for (int at = 0; at < all.length; at++) {
            int cx = left + (at % 8) * (SWATCH + 1);
            int cy = top + (at / 8) * (SWATCH + 1);
            if (mouseX >= cx && mouseX < cx + SWATCH && mouseY >= cy && mouseY < cy + SWATCH) {
                return all[at];
            }
        }
        return null;
    }

    public void place(EditBox box, Shelf shelf, int x, int y, int wide) {
        if (editing < 0 || editing >= count(shelf)) {
            box.visible = false;
            return;
        }
        Shelf.Slice slice = shelf.slices().get(editing);
        int top = rowTop(editing, y) + 2;
        box.visible = true;
        if (field == NAME) {
            box.setPosition(nameLeft(x), top);
            box.setWidth(nameWide(x, wide));
            box.setMaxLength(Plan.LONGEST);
            box.setValue(slice.name());
        } else {
            box.setPosition(columnNumber(x, wide, 1), top);
            box.setWidth(NUMBER);
            box.setMaxLength(5);
            box.setValue(Integer.toString(slice.length()));
        }
        box.setFocused(true);
        box.setCursorPosition(box.getValue().length());
        box.setHighlightPos(0);
    }

    public Edit committed(EditBox box, Shelf shelf) {
        if (editing < 0 || editing >= count(shelf)) {
            return null;
        }
        Shelf.Slice slice = shelf.slices().get(editing);
        String said = box.getValue();
        if (field == NAME) {
            return Edit.setting(editing, said, slice.dye(), slice.start(), slice.length());
        }
        int many;
        try {
            many = Integer.parseInt(said.trim());
        } catch (NumberFormatException wrong) {
            return null;
        }
        return Edit.setting(editing, slice.name(), slice.dye(), slice.start(),
                Math.max(0, many));
    }

    public void draw(GuiGraphics graphics, Font font, Shelf shelf, int x, int y,
            int wide, int tall, int mouseX, int mouseY) {
        graphics.fill(x - 2000, y - 2000, x + 4000, y + 4000, SHADE);
        graphics.fill(x, y, x + wide, y + tall, EDGE);
        graphics.fill(x + 1, y + 1, x + wide - 1, y + tall - 1, BACK);

        graphics.fill(x + PAD, y + PAD, x + PAD + LINE, y + PAD + LINE, WELL);
        graphics.drawString(font, "x", x + PAD + 2, y + PAD + 1, 0xFFFFFF, false);

        head(graphics, font, x, y, wide);

        int listTop = y + PAD + HEAD;
        int listTall = listTall(tall);
        graphics.enableScissor(x + 1, listTop, x + wide - 1, listTop + listTall);
        for (int at = 0; at < rows(shelf); at++) {
            int top = listTop + at * ROW - scroll;
            if (top + ROW <= listTop || top >= listTop + listTall) {
                continue;
            }
            if (at < count(shelf)) {
                row(graphics, font, shelf, at, x, top, wide, mouseX, mouseY);
            } else {
                plus(graphics, font, x, top, wide, mouseX, mouseY);
            }
        }
        graphics.disableScissor();

        int barY = y + tall - PAD - BAR - LINE - 1;
        bar(graphics, shelf, x + PAD, barY, wide - 2 * PAD);
        int carved = 0;
        for (Shelf.Slice slice : shelf.slices()) {
            carved += shelf.divided() ? slice.length() : 0;
        }
        int whole = shelf.slots() / Plan.LC;
        graphics.drawString(font, Component.translatable("gui.cella.edit.carved",
                carved, whole, whole - carved), x + PAD, barY + BAR + 2, FAINT, false);

        if (palette >= 0 && palette < count(shelf)) {
            swatches(graphics, x, y);
        }
    }

    private void head(GuiGraphics graphics, Font font, int x, int y, int wide) {
        int top = y + PAD + LINE + 3;
        graphics.drawString(font, Component.translatable("gui.cella.edit.name"),
                nameLeft(x), top, FAINT, false);
        String[] keys = { "gui.cella.edit.share", "gui.cella.edit.size", "gui.cella.edit.used" };
        for (int at = 0; at < keys.length; at++) {
            graphics.drawString(font, Component.translatable(keys[at]),
                    columnNumber(x, wide, at), top, FAINT, false);
        }
        graphics.fill(x + PAD, y + PAD + HEAD - 4, x + wide - PAD, y + PAD + HEAD - 3, WELL);
    }

    private void row(GuiGraphics graphics, Font font, Shelf shelf, int at, int x, int top,
            int wide, int mouseX, int mouseY) {
        Shelf.Slice slice = shelf.slices().get(at);
        if (mouseY >= top && mouseY < top + ROW && mouseX >= x && mouseX < x + wide) {
            graphics.fill(x + 1, top, x + wide - 1, top + ROW - 1, OVER);
        }
        graphics.fill(x + PAD, top + 2, x + PAD + SWATCH, top + 2 + SWATCH,
                0xFF000000 | slice.dye().getTextureDiffuseColor());

        if (editing != at) {
            graphics.drawString(font, font.plainSubstrByWidth(slice.name(), nameWide(x, wide)),
                    nameLeft(x), top + 3, TEXT, false);
        }

        int share = shelf.slots() <= 0 ? 0 : Math.round(slice.slots() * 100.0F / shelf.slots());
        graphics.drawString(font, share + "%", columnNumber(x, wide, 0), top + 3, FAINT, false);
        if (editing != at || field != SIZE) {
            graphics.drawString(font, Integer.toString(slice.length()),
                    columnNumber(x, wide, 1), top + 3, TEXT, false);
        }
        graphics.drawString(font, Math.round(slice.filled() * 100.0F) + "%",
                columnNumber(x, wide, 2), top + 3, FAINT, false);

        boolean empty = slice.used() == 0;
        graphics.drawString(font, "✖", x + wide - PAD - BIN, top + 3,
                empty ? 0xB03030 : STOPPED, false);
    }

    private void plus(GuiGraphics graphics, Font font, int x, int top, int wide,
            int mouseX, int mouseY) {
        if (mouseY >= top && mouseY < top + ROW && mouseX >= x && mouseX < x + wide) {
            graphics.fill(x + 1, top, x + wide - 1, top + ROW - 1, OVER);
        }
        graphics.drawString(font, "+", x + PAD + 2, top + 3, TEXT, false);
    }

    private void bar(GuiGraphics graphics, Shelf shelf, int x, int y, int wide) {
        graphics.fill(x, y, x + wide, y + BAR, WELL);
        graphics.fill(x + 1, y + 1, x + wide - 1, y + BAR - 1, SPARE);
        if (shelf.slots() <= 0) {
            return;
        }
        int inner = wide - 2;
        for (Shelf.Slice slice : shelf.slices()) {
            int from = x + 1 + Math.round(slice.start() * (float) Plan.LC / shelf.slots() * inner);
            int much = Math.max(1, Math.round(slice.slots() / (float) shelf.slots() * inner));
            graphics.fill(from, y + 1, Math.min(x + wide - 1, from + much), y + BAR - 1,
                    0xFF000000 | slice.dye().getTextureDiffuseColor());
        }
    }

    private void swatches(GuiGraphics graphics, int x, int y) {
        int left = x + PAD;
        int top = rowTop(palette, y) + ROW;
        DyeColor[] all = DyeColor.values();
        int wide = 8 * (SWATCH + 1) + 1;
        int tall = 2 * (SWATCH + 1) + 1;
        graphics.fill(left - 1, top - 1, left + wide, top + tall, EDGE);
        graphics.fill(left, top, left + wide - 1, top + tall - 1, TRACK);
        for (int at = 0; at < all.length; at++) {
            int cx = left + (at % 8) * (SWATCH + 1);
            int cy = top + (at / 8) * (SWATCH + 1);
            graphics.fill(cx, cy, cx + SWATCH, cy + SWATCH,
                    0xFF000000 | all[at].getTextureDiffuseColor());
        }
    }

    public boolean overClose(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x + PAD && mouseX < x + PAD + LINE
                && mouseY >= y + PAD && mouseY < y + PAD + LINE;
    }

    public boolean inside(double mouseX, double mouseY, int x, int y, int wide, int tall) {
        return mouseX >= x && mouseX < x + wide && mouseY >= y && mouseY < y + tall;
    }
}
