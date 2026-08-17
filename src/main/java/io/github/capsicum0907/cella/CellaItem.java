package io.github.capsicum0907.cella;

import java.util.List;
import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/**
 * The chest as an item. It stacks while it is empty and not once it holds a chest.
 *
 * <p><b>Derived, not stamped.</b> The answer is worked out from whether the name is on
 * the stack, every time it is asked, rather than written onto the item at the moment it
 * is filled. Acervus had this the other way round and it was the bug: a value that has to
 * be written is a value that has somewhere it can be forgotten, and there is always one
 * more place that writes than the person counting them thinks.
 *
 * <p>Why refuse to stack at all, when {@link Kept} means there is nothing on the item to
 * duplicate: two items naming one chest are two views of one chest, and the first of them
 * to be placed takes the contents. That is defensible when a mod does it and unpleasant
 * when a stack of two does it in a player's hand. So they do not stack.
 */
public class CellaItem extends BlockItem {
    public CellaItem(Block block, Properties properties) {
        super(block, properties);
    }

    /** Vanilla's own bar is thirteen pixels of it. */
    private static final int BAR = 13;

    private static final int BLUE = 0x3B48D8;

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return stack.has(CellaRegistry.KEPT.get()) ? 1 : super.getMaxStackSize(stack);
    }

    /**
     * The little bar under the icon, so a full one can be told from an empty one without
     * hovering over it.
     *
     * <p>An item picked up empty carries nothing and shows nothing, which is the whole
     * distinction: a bar means there is a chest in there. An item written by an older
     * version carries a name and no counts, and shows nothing either - it does not know,
     * and drawing a bar at nought would be saying that it does.
     */
    @Override
    public boolean isBarVisible(ItemStack stack) {
        Held held = stack.get(CellaRegistry.KEPT.get());
        return held != null && held.counted();
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        Held held = stack.get(CellaRegistry.KEPT.get());
        if (held == null) {
            return 0;
        }
        int width = Math.round(held.filled() * BAR);
        // Something is not nothing. A single item in a Cella Max is a thousandth of a
        // pixel, and rounding it away would draw a chest with things in it as empty.
        return held.used() > 0 ? Math.clamp(width, 1, BAR) : 0;
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BLUE;
    }

    /** The wide bar, drawn by {@code FillBar} on the client. */
    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return Optional.ofNullable(stack.get(CellaRegistry.KEPT.get()))
                .filter(held -> held.counted() || held.grows())
                .map(held -> held);
    }

    /**
     * The figures the bar cannot give: how many slots, out of how many.
     *
     * <p>A percent alone is no use at this size - six percent of a Cella Max is thirteen
     * thousand slots - and the raw pair is hard to picture, which is what the bar is for.
     * Neither replaces the other.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            List<Component> tooltip, TooltipFlag flag) {
        Held held = stack.get(CellaRegistry.KEPT.get());
        if (held == null) {
            return;
        }
        if (held.counted()) {
            tooltip.add(Component.translatable("tooltip.cella.filled",
                            Held.count(held.used()), Held.count(held.slots()),
                            Math.round(held.filled() * 100.0F))
                    .withStyle(ChatFormatting.GRAY));
        }
        // A percent on its own, because the points behind it are not a figure anybody has
        // a feel for - a player knows how many levels they handed over and not how many
        // points those were worth.
        if (held.grows()) {
            tooltip.add(Component.translatable("tooltip.cella.grown",
                            Math.round(held.grown() * 100.0F))
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
