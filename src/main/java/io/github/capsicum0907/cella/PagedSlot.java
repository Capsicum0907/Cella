package io.github.capsicum0907.cella;

import java.util.function.BiPredicate;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

public class PagedSlot extends SlotItemHandler {
    private final Window window;

    private final BiPredicate<Integer, ItemStack> replaceable;

    public PagedSlot(Window window, int index, int x, int y,
            BiPredicate<Integer, ItemStack> replaceable) {
        super(window, index, x, y);
        this.window = window;
        this.replaceable = replaceable;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return super.mayPlace(stack) && replaceable.test(getSlotIndex(), stack);
    }

    @Override
    public boolean isActive() {
        return window.holds(getSlotIndex());
    }
}
