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
    /**
     * Ten, and the picture six, which leaves two pixels of face all round.
     *
     * <p>Both measured off the buttons the sorting mod puts on the same screen rather
     * than chosen: ten wide, two apart. Eleven and three looked close enough written
     * down and did not look close at all beside them - a row of four came out a fifth
     * wider, which is what carried.
     */
    public static final int SIZE = 10;
    public static final int ICON = 6;

    /**
     * A raised square: light along the top and left, dark along the bottom and right,
     * face between. It is how every button in the game is lit, and the reason it reads
     * as something to press rather than a picture with a line round it - which is what
     * the first version of this was.
     */
    private static final int LIGHT = 0xFFC6C6C6;
    private static final int FACE = 0xFF8B8B8B;
    private static final int DARK = 0xFF373737;
    private static final int LIT = 0xFFA0A0A0;

    private final ResourceLocation icon;
    private final Runnable pressed;

    /** The picture's own size, which is the file's own size. See the note on {@link #SIZE}. */
    private final int art;

    /**
     * Whether it is drawn on a raised square at all.
     *
     * <p>⚠ <b>A plate says "this is one of the buttons".</b> That is right in the corner
     * where four of them sit together, and wrong for the one that stands at the left of the
     * title, in a line of text: there it reads as a button bolted onto a label. So that one
     * is the picture and nothing else, brightening under the pointer to say it can be
     * pressed.
     */
    private final boolean plate;

    public IconButton(int x, int y, ResourceLocation icon, Component tooltip, Runnable pressed) {
        this(x, y, SIZE, icon, ICON, tooltip, true, pressed);
    }

    /**
     * @param size how big the control is, which for a plated one is the plate
     * @param art the picture's own size in its file. <b>Blitted at it and never scaled</b>:
     *            pixel art resampled to a size it was not drawn at stops being pixel art,
     *            which is why borrowing a twelve-pixel icon means a twelve-pixel control
     *            rather than a squashed one
     */
    public IconButton(int x, int y, int size, ResourceLocation icon, int art, Component tooltip,
            boolean plate, Runnable pressed) {
        super(x, y, size, size, tooltip);
        this.icon = icon;
        this.art = art;
        this.plate = plate;
        this.pressed = pressed;
        setTooltip(net.minecraft.client.gui.components.Tooltip.create(tooltip));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        int x = getX();
        int y = getY();
        if (plate) {
            graphics.fill(x, y, x + width, y + height, isHovered() ? LIT : FACE);
            // The light two first, then the dark two over them, so the corners belong to
            // the shadow - which is how a raised edge actually looks.
            graphics.fill(x, y, x + width, y + 1, LIGHT);
            graphics.fill(x, y, x + 1, y + height, LIGHT);
            graphics.fill(x, y + height - 1, x + width, y + height, DARK);
            graphics.fill(x + width - 1, y, x + width, y + height, DARK);
        }

        // Centred by the pixel: seven in eleven leaves two either side, which is why both
        // are odd. The size passed is the file's own, the lesson from the background.
        int inset = (width - art) / 2;
        graphics.blit(icon, x + inset, y + inset, 0, 0, art, art, art, art);
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
