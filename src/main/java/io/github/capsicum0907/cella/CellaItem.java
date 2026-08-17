package io.github.capsicum0907.cella;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
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

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return stack.has(CellaRegistry.KEPT.get()) ? 1 : super.getMaxStackSize(stack);
    }
}
