package io.github.capsicum0907.cella.client;

import io.github.capsicum0907.cella.Cella;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class Icons {
    public static final int SIZE = IconButton.ICON;

    public static final String SORT = "sort";
    public static final String STOW = "stow";
    public static final String TAKE = "take";
    public static final String PREV = "prev";
    public static final String NEXT = "next";
    public static final String LIST = "list";
    public static final String BACK = "back";
    public static final String CLOSE = "close";
    public static final String ADD = "add";
    public static final String BIN = "bin";
    public static final String PACK = "pack";
    public static final String OUTLET = "outlet";

    private Icons() {
    }

    public static ResourceLocation of(String name) {
        return ResourceLocation.fromNamespaceAndPath(Cella.MODID, "textures/gui/" + name + ".png");
    }

    public static void draw(GuiGraphics graphics, String name, int x, int y, int rgb) {
        graphics.setColor(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F, 1.0F);
        graphics.blit(of(name), x, y, 0, 0, SIZE, SIZE, SIZE, SIZE);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static int inset(int room) {
        return (room - SIZE) / 2;
    }
}
