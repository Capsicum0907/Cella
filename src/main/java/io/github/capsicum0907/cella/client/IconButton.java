package io.github.capsicum0907.cella.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class IconButton extends AbstractWidget {
    public static final int SIZE = 10;
    public static final int ICON = 6;

    private static final int LIGHT = 0xFFC6C6C6;
    private static final int FACE = 0xFF8B8B8B;
    private static final int DARK = 0xFF373737;
    private static final int LIT = 0xFFA0A0A0;

    private final ResourceLocation icon;
    private final Runnable pressed;

    private final int art;

    private final boolean plate;

    public IconButton(int x, int y, ResourceLocation icon, Component tooltip, Runnable pressed) {
        this(x, y, SIZE, icon, ICON, tooltip, true, pressed);
    }

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

            graphics.fill(x, y, x + width, y + 1, LIGHT);
            graphics.fill(x, y, x + 1, y + height, LIGHT);
            graphics.fill(x, y + height - 1, x + width, y + height, DARK);
            graphics.fill(x + width - 1, y, x + width, y + height, DARK);
        }

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
