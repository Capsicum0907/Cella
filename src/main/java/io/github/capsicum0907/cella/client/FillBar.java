package io.github.capsicum0907.cella.client;

import io.github.capsicum0907.cella.Held;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

public record FillBar(Held held) implements ClientTooltipComponent {
    private static final int WIDTH = 100;

    private static final int HEIGHT = 7;

    private static final int GAP = 1;
    private static final int TEXT = 9;

    private static final int OUTLINE = 0xFF000000;
    private static final int TRACK = 0xFFFFFFFF;
    private static final int FILL = 0xFF3B48D8;

    private static final int GROWTH = 0xFF7FBF3F;

    private static final int LABEL = 0xFFAAAAAA;

    private static final Component EMPTY = Component.literal("0%");
    private static final Component FULL = Component.literal("100%");

    @Override
    public int getHeight() {
        return HEIGHT + (held.grows() ? GAP + HEIGHT : 0) + GAP + TEXT;
    }

    @Override
    public int getWidth(Font font) {
        return Math.max(WIDTH, font.width(EMPTY) + font.width(FULL) + GAP);
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        bar(graphics, x, y, held.filled(), held.used() > 0, FILL);

        int under = y + HEIGHT;
        if (held.grows()) {
            under += GAP;
            bar(graphics, x, under, held.grown(), held.experience() > 0, GROWTH);
            under += HEIGHT;
        }

        under += GAP;
        graphics.drawString(font, EMPTY, x, under, LABEL, false);
        graphics.drawString(font, FULL, x + WIDTH - font.width(FULL), under, LABEL, false);
    }

    private static void bar(GuiGraphics graphics, int x, int y, float part, boolean any,
            int colour) {
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, OUTLINE);
        graphics.fill(x + 1, y + 1, x + WIDTH - 1, y + HEIGHT - 1, TRACK);

        int inside = WIDTH - 2;
        int filled = Math.round(part * inside);
        if (any) {
            filled = Math.clamp(filled, 1, inside);
        }
        graphics.fill(x + 1, y + 1, x + 1 + filled, y + HEIGHT - 1, colour);
    }
}
