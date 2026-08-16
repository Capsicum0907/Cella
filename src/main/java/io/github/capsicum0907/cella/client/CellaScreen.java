package io.github.capsicum0907.cella.client;

import io.github.capsicum0907.cella.CellaConfig;
import io.github.capsicum0907.cella.CellaMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * The chest screen, drawn on the vanilla chest background.
 *
 * <p>The background is {@code generic_54}, borrowed rather than copied: it is already
 * exactly the right picture, and a copy would be one more file to keep in step with a
 * texture pack the player installed.
 *
 * <p>Two buttons and a number. Pressing one sends {@code clickMenuButton} <b>and</b>
 * turns this side's own window, because that packet only runs on the server — the
 * screen would otherwise sit on the old page until something else forced it to
 * refresh.
 */
public class CellaScreen extends AbstractContainerScreen<CellaMenu> {
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    /** The strip of background above the slots, and the part below them. */
    private static final int LID = 17;
    private static final int FOOT = 96;
    private static final int FOOT_V = 126;
    private static final int SLOT = 18;

    /**
     * The two buttons and the number between them, measured from the right edge of the
     * panel so the layout does not have to know how wide it is.
     */
    private static final int BUTTON = 12;
    private static final int BUTTON_Y = 3;
    private static final int PREV_X = -58;
    private static final int NEXT_X = -20;

    private final int rows;

    public CellaScreen(CellaMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        // Asked of the window rather than of the config: the screen draws what it was
        // given, and the panel is as tall as that. imageWidth is left at the 176 the
        // superclass already has, which is what the picture is.
        this.rows = menu.window().getSlots() / CellaConfig.COLUMNS;
        this.imageHeight = 114 + rows * SLOT;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        if (menu.window().pages() <= 1) {
            return;
        }
        addRenderableWidget(Button.builder(Component.literal("<"), button -> turn(-1))
                .bounds(leftPos + imageWidth + PREV_X, topPos + BUTTON_Y, BUTTON, BUTTON)
                .build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> turn(1))
                .bounds(leftPos + imageWidth + NEXT_X, topPos + BUTTON_Y, BUTTON, BUTTON)
                .build());
    }

    /**
     * Both sides, in that order.
     *
     * <p>The page wanted is worked out here and sent as the button id, so the two sides
     * never have to agree about where they were — only about where to go.
     */
    private void turn(int by) {
        int pages = menu.window().pages();
        int wanted = Math.floorMod(menu.window().page() + by, pages);
        Minecraft client = Minecraft.getInstance();
        if (client.gameMode != null) {
            client.gameMode.handleInventoryButtonClick(menu.containerId, wanted);
        }
        menu.clickMenuButton(client.player, wanted);
    }

    /**
     * <p><b>The six-argument blit, which is the one that assumes 256 by 256 — and the
     * file is 256 by 256.</b> Saying so explicitly with the nine-argument version is
     * what broke this the first time: the numbers passed were 176 by 222, which is the
     * size of the <em>picture</em> inside the file and not the size of the file. Every
     * texture coordinate is divided by what is declared, so the background came out
     * scaled by 176/256 across and 222/256 down while the slots stayed where they
     * belonged, and the two drifted apart towards the bottom right.
     */
    @Override
    protected void renderBg(GuiGraphics graphics, float partial, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, rows * SLOT + LID);
        graphics.blit(BACKGROUND, leftPos, topPos + rows * SLOT + LID, 0, FOOT_V, imageWidth, FOOT);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        if (menu.window().pages() <= 1) {
            return;
        }
        // One-based, because the first page is the first page and not the noughth.
        // Centred in the gap between the two buttons, which is the only place it fits.
        Component page = Component.literal((menu.window().page() + 1) + " / " + menu.window().pages());
        int gap = NEXT_X - (PREV_X + BUTTON);
        graphics.drawString(font, page,
                imageWidth + PREV_X + BUTTON + (gap - font.width(page)) / 2, BUTTON_Y + 2,
                0x404040, false);
    }

    /**
     * The same two lines vanilla's chest screen has, and no more.
     *
     * <p>{@code AbstractContainerScreen#render} already dims the world behind it. Doing
     * it again here as well darkened the whole screen twice over.
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        super.render(graphics, mouseX, mouseY, partial);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
