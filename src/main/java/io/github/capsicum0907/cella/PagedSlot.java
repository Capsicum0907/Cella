package io.github.capsicum0907.cella;

import java.util.function.BiPredicate;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

public class PagedSlot extends SlotItemHandler {
    private final Window window;

    private final BiPredicate<Integer, ItemStack> replaceable;

    private final java.util.function.BooleanSupplier locked;

    public PagedSlot(Window window, int index, int x, int y,
            BiPredicate<Integer, ItemStack> replaceable, java.util.function.BooleanSupplier locked) {
        super(window, index, x, y);
        this.window = window;
        this.replaceable = replaceable;
        this.locked = locked;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return !locked.getAsBoolean() && super.mayPlace(stack)
                && replaceable.test(getSlotIndex(), stack);
    }

    @Override
    public boolean mayPickup(net.minecraft.world.entity.player.Player player) {
        return !locked.getAsBoolean() && super.mayPickup(player);
    }

    @Override
    public boolean isActive() {
        return window.holds(getSlotIndex());
    }
}
