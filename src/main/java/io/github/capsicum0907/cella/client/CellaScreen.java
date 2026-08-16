package io.github.capsicum0907.cella.client;

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
    private static final int TEXTURE_WIDTH = 176;
    private static final int TEXTURE_HEIGHT = 222;

    /** The strip of background above the slots, and the part below them. */
    private static final int LID = 17;
    private static final int FOOT = 96;
    private static final int FOOT_V = 126;
    private static final int SLOT = 18;

    private static final int BUTTON = 12;
    private static final int BUTTON_Y = 3;

    private final int rows;

    public CellaScreen(CellaMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.rows = menu.window().getSlots() / 9;
        this.imageWidth = TEXTURE_WIDTH;
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
                .bounds(leftPos + imageWidth - 58, topPos + BUTTON_Y, BUTTON, BUTTON)
                .build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> turn(1))
                .bounds(leftPos + imageWidth - 20, topPos + BUTTON_Y, BUTTON, BUTTON)
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

    @Override
    protected void renderBg(GuiGraphics graphics, float partial, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, rows * SLOT + LID,
                TEXTURE_WIDTH, TEXTURE_HEIGHT);
        graphics.blit(BACKGROUND, leftPos, topPos + rows * SLOT + LID, 0, FOOT_V, imageWidth, FOOT,
                TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        if (menu.window().pages() <= 1) {
            return;
        }
        // One-based, because the first page is the first page and not the noughth.
        Component page = Component.literal((menu.window().page() + 1) + " / " + menu.window().pages());
        graphics.drawString(font, page,
                imageWidth - 44 + (BUTTON + 26 - font.width(page)) / 2 - 12, BUTTON_Y + 2,
                0x404040, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        renderBackground(graphics, mouseX, mouseY, partial);
        super.render(graphics, mouseX, mouseY, partial);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
