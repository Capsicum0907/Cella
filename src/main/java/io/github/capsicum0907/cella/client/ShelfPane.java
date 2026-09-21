package io.github.capsicum0907.cella.client;

import io.github.capsicum0907.cella.Peek;
import io.github.capsicum0907.cella.Shelf;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public final class ShelfPane {
    public static final int ITEM = 28;

    private static final int GAP = 2;

    private static final int STRIPE = 2;

    private static final int PAD = 3;

    private static final int BAR = 3;

    private static final int LINE = 9;

    private static final int TALLY = 18;

    private static final int TEXT = 0x404040;
    private static final int FAINT = 0x707070;
    private static final int TRACK = 0xFF8B8B8B;
    private static final int WELL = 0xFF373737;
    private static final int CHOSEN = 0x60FFFFFF;
    private static final int OVER = 0x30FFFFFF;

    private int scroll;

    private int chosen = Peek.LIST;

    public int chosen() {
        return chosen;
    }

    public void choose(int index) {
        chosen = index;
    }

    public void forget() {
        chosen = Peek.LIST;
        scroll = 0;
    }

    public void unpick() {
        chosen = Peek.LIST;
    }

    private int listWide(int wide) {
        return (wide - GAP) * 7 / 10;
    }

    private int rows(Shelf shelf) {
        return shelf.slices().size();
    }

    private int reach(Shelf shelf, int tall) {
        return Math.max(0, rows(shelf) * ITEM - tall);
    }

    public boolean scrolled(Shelf shelf, double amount, int tall) {
        int most = reach(shelf, tall);
        if (most <= 0) {
            return false;
        }
        scroll = Mth.clamp(scroll - (int) (amount * ITEM), 0, most);
        return true;
    }

    public int hit(Shelf shelf, double x, double y, int left, int top, int wide, int tall) {
        if (x < left || x >= left + listWide(wide) || y < top || y >= top + tall) {
            return Peek.LIST;
        }
        int at = (int) ((y - top + scroll) / ITEM);
        return at >= 0 && at < rows(shelf) ? at : Peek.LIST;
    }

    public void draw(GuiGraphics graphics, Font font, Shelf shelf, int left, int top,
            int wide, int tall, int mouseX, int mouseY) {
        int listWide = listWide(wide);
        int detailLeft = left + listWide + GAP;
        int detailWide = wide - listWide - GAP;

        well(graphics, left, top, listWide, tall);
        well(graphics, detailLeft, top, detailWide, tall);

        graphics.enableScissor(left, top, left + listWide, top + tall);
        int over = hit(shelf, mouseX, mouseY, left, top, wide, tall);
        for (int at = 0; at < rows(shelf); at++) {
            int y = top + at * ITEM - scroll;
            if (y + ITEM <= top || y >= top + tall) {
                continue;
            }
            item(graphics, font, shelf.slices().get(at), left, y, listWide,
                    at == chosen, at == over);
        }
        graphics.disableScissor();

        detail(graphics, font, shelf, detailLeft, top, detailWide, tall);
    }

    private void well(GuiGraphics graphics, int x, int y, int wide, int tall) {
        graphics.fill(x, y, x + wide, y + tall, WELL);
        graphics.fill(x + 1, y + 1, x + wide - 1, y + tall - 1, TRACK);
    }

    private void item(GuiGraphics graphics, Font font, Shelf.Slice slice, int x, int y,
            int wide, boolean picked, boolean under) {
        if (picked) {
            graphics.fill(x + 1, y, x + wide - 1, y + ITEM - 1, CHOSEN);
        } else if (under) {
            graphics.fill(x + 1, y, x + wide - 1, y + ITEM - 1, OVER);
        }
        graphics.fill(x + 1, y + 1, x + 1 + STRIPE, y + ITEM - 2,
                0xFF000000 | slice.dye().getTextureDiffuseColor());

        int textLeft = x + 1 + STRIPE + PAD;
        int room = wide - (textLeft - x) - PAD;

        String name = slice.name().isEmpty()
                ? Component.translatable("gui.cella.partition.unnamed").getString()
                : slice.name();
        graphics.drawString(font, font.plainSubstrByWidth(name, room), textLeft, y + PAD,
                TEXT, false);

        int barY = y + PAD + LINE + 1;
        bar(graphics, textLeft, barY, room, slice.filled(), slice.dye());

        String numbers = grouped(slice.used()) + "/" + grouped(slice.slots())
                + " (" + Math.round(slice.filled() * 100.0F) + "%)";
        graphics.drawString(font, font.plainSubstrByWidth(numbers, room), textLeft,
                barY + BAR + 2, FAINT, false);
    }

    private void bar(GuiGraphics graphics, int x, int y, int wide, float filled,
            net.minecraft.world.item.DyeColor dye) {
        graphics.fill(x, y, x + wide, y + BAR, WELL);
        int much = Math.round(Mth.clamp(filled, 0.0F, 1.0F) * (wide - 2));
        if (much > 0) {
            graphics.fill(x + 1, y + 1, x + 1 + much, y + BAR - 1,
                    0xFF000000 | dye.getTextureDiffuseColor());
        }
    }

    private void detail(GuiGraphics graphics, Font font, Shelf shelf, int x, int y,
            int wide, int tall) {
        if (chosen < 0 || chosen >= shelf.slices().size()) {
            Component none = Component.translatable("gui.cella.partition.none");
            graphics.drawString(font, font.plainSubstrByWidth(none.getString(), wide - 2 * PAD),
                    x + PAD, y + PAD, FAINT, false);
            return;
        }
        Shelf.Slice slice = shelf.slices().get(chosen);
        int room = wide - 2 * PAD;
        int at = y + PAD;

        String name = slice.name().isEmpty()
                ? Component.translatable("gui.cella.partition.unnamed").getString()
                : slice.name();
        graphics.drawString(font, font.plainSubstrByWidth(name, room), x + PAD, at, TEXT, false);
        at += LINE + 1;

        int share = shelf.slots() <= 0 ? 0 : Math.round(slice.slots() * 100.0F / shelf.slots());
        graphics.drawString(font, font.plainSubstrByWidth(
                Component.translatable("gui.cella.partition.share", share).getString(), room),
                x + PAD, at, FAINT, false);
        at += LINE;

        graphics.drawString(font, font.plainSubstrByWidth(
                grouped(slice.used()) + "/" + grouped(slice.slots()), room),
                x + PAD, at, FAINT, false);
        at += LINE + 2;

        if (shelf.shown() != chosen) {
            return;
        }
        for (Shelf.Tally tally : shelf.tallies()) {
            if (at + TALLY > y + tall - PAD) {
                return;
            }
            graphics.renderItem(tally.kind(), x + PAD, at);
            graphics.drawString(font, brief(tally.count()), x + PAD + TALLY + PAD,
                    at + (TALLY - LINE) / 2, TEXT, false);
            at += TALLY;
        }
    }

    public ItemStack over(Shelf shelf, double mouseX, double mouseY, int x, int y,
            int wide, int tall) {
        if (chosen < 0 || chosen >= shelf.slices().size() || shelf.shown() != chosen) {
            return ItemStack.EMPTY;
        }
        int listWide = listWide(wide);
        int left = x + listWide + GAP;
        if (mouseX < left || mouseX >= x + wide) {
            return ItemStack.EMPTY;
        }
        int at = y + PAD + (LINE + 1) + LINE + LINE + 2;
        for (Shelf.Tally tally : shelf.tallies()) {
            if (mouseY >= at && mouseY < at + TALLY) {
                return tally.kind().copyWithCount(tally.count());
            }
            at += TALLY;
        }
        return ItemStack.EMPTY;
    }

    public static String grouped(int count) {
        return String.format(java.util.Locale.ROOT, "%,d", count);
    }

    public static String brief(int count) {
        if (count < 1000) {
            return Integer.toString(count);
        }
        if (count < 1000000) {
            return trim(count / 1000.0, "k");
        }
        return trim(count / 1000000.0, "M");
    }

    private static String trim(double much, String suffix) {
        if (much >= 100.0) {
            return Math.round(much) + suffix;
        }
        String said = String.format(java.util.Locale.ROOT, "%.1f", much);
        return (said.endsWith(".0") ? said.substring(0, said.length() - 2) : said) + suffix;
    }
}
