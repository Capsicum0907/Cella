package io.github.capsicum0907.cella;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

public final class Outlet implements IItemHandlerModifiable {
    public static final Outlet NOTHING = new Outlet(null, 0, 0);

    private final IItemHandlerModifiable held;

    private final int first;

    private final int size;

    public Outlet(IItemHandlerModifiable held, int first, int size) {
        this.held = held;
        this.first = first;
        this.size = Math.max(0, size);
    }

    private boolean there(int slot) {
        return held != null && slot >= 0 && slot < size;
    }

    @Override
    public int getSlots() {
        return size;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return there(slot) ? held.getStackInSlot(first + slot) : ItemStack.EMPTY;
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (there(slot)) {
            held.setStackInSlot(first + slot, stack);
        }
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return there(slot) ? held.insertItem(first + slot, stack, simulate) : stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return there(slot) ? held.extractItem(first + slot, amount, simulate) : ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return there(slot) ? held.getSlotLimit(first + slot) : 0;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return there(slot) && held.isItemValid(first + slot, stack);
    }
}
