package io.github.capsicum0907.cella.client;

import io.github.capsicum0907.cella.Held;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

/**
 * How full a picked-up chest is, drawn.
 *
 * <p>A number would do for a large chest and not for these. Six percent of a Cella Max is
 * thirteen thousand slots, and neither of those two figures answers "have I room for this"
 * on its own — one is too abstract and the other is too big to picture. A bar answers it
 * at a glance and the figures are written above it for when the glance is not enough.
 *
 * <p><b>A slot that is spoken for is never drawn as empty.</b> One item in a Cella Max
 * rounds to nought pixels of a hundred, so the fill is floored at one: a chest with
 * something in it must not look like a chest with nothing in it. Which way that rounds is
 * the difference between a bar and a lie, and it is the same rule as the empty frames.
 */
public record FillBar(Held held) implements ClientTooltipComponent {
    /** Wide enough for a percent to be worth reading, and no wider than a tooltip wants. */
    private static final int WIDTH = 100;

    /** One pixel of outline each side, five of track between them. */
    private static final int HEIGHT = 7;

    /** The gap over the labels, and the line they sit on. */
    private static final int GAP = 1;
    private static final int TEXT = 9;

    private static final int OUTLINE = 0xFF000000;
    private static final int TRACK = 0xFFFFFFFF;
    private static final int FILL = 0xFF3B48D8;

    /** The grey vanilla writes the quiet half of a tooltip in. */
    private static final int LABEL = 0xFFAAAAAA;

    private static final Component EMPTY = Component.literal("0%");
    private static final Component FULL = Component.literal("100%");

    @Override
    public int getHeight() {
        return HEIGHT + GAP + TEXT;
    }

    @Override
    public int getWidth(Font font) {
        return Math.max(WIDTH, font.width(EMPTY) + font.width(FULL) + GAP);
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, OUTLINE);
        graphics.fill(x + 1, y + 1, x + WIDTH - 1, y + HEIGHT - 1, TRACK);

        int inside = WIDTH - 2;
        int filled = Math.round(held.filled() * inside);
        if (held.used() > 0) {
            filled = Math.clamp(filled, 1, inside);
        }
        graphics.fill(x + 1, y + 1, x + 1 + filled, y + HEIGHT - 1, FILL);

        int under = y + HEIGHT + GAP;
        graphics.drawString(font, EMPTY, x, under, LABEL, false);
        graphics.drawString(font, FULL, x + WIDTH - font.width(FULL), under, LABEL, false);
    }
}
