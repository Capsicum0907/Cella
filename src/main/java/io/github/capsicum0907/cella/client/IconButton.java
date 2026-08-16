package io.github.capsicum0907.cella.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A small flat square with a picture in it.
 *
 * <p><b>Not a vanilla {@code Button}.</b> One of those is drawn for a menu — twenty
 * pixels tall, a two-pixel black outline and a bevel — and eight of them crowded into
 * the lid of a chest screen read as a toolbar bolted on. The buttons a sorting mod puts
 * on the same screen are flat squares the size of a slot's inside, which is what a
 * control on a container screen looks like when it belongs there.
 *
 * <p>The colours are measured from one of those rather than guessed: edge, face and the
 * lighter face under the pointer. They live here and nowhere else.
 */
public class IconButton extends AbstractWidget {
    /** Eleven, which is what the neighbouring mod's are, and odd so a nine fits centred. */
    public static final int SIZE = 11;
    public static final int ICON = 9;

    private static final int EDGE = 0xFF373737;
    private static final int FACE = 0xFF6F6F6F;
    private static final int LIT = 0xFF8B8B8B;

    private final ResourceLocation icon;
    private final Runnable pressed;

    public IconButton(int x, int y, ResourceLocation icon, Component tooltip, Runnable pressed) {
        super(x, y, SIZE, SIZE, tooltip);
        this.icon = icon;
        this.pressed = pressed;
        setTooltip(net.minecraft.client.gui.components.Tooltip.create(tooltip));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        graphics.fill(getX(), getY(), getX() + SIZE, getY() + SIZE, EDGE);
        graphics.fill(getX() + 1, getY() + 1, getX() + SIZE - 1, getY() + SIZE - 1,
                isHovered() ? LIT : FACE);

        // Centred by the pixel: nine in eleven leaves one either side, which is why both
        // are odd. The size passed is the file's own, the lesson from the background.
        int inset = (SIZE - ICON) / 2;
        graphics.blit(icon, getX() + inset, getY() + inset, 0, 0, ICON, ICON, ICON, ICON);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        pressed.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
