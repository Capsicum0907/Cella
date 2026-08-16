package io.github.capsicum0907.cella;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * One page's worth of slots, laid over contents that are longer than that.
 *
 * <p><b>This is the whole of the paging.</b> A slot cannot be told to look somewhere
 * else: vanilla's {@code Slot} reads its own {@code slot} field directly in
 * {@code getItem}, {@code set}, {@code remove} and {@code mayPlace}, and never asks
 * {@code getSlotIndex()}. NeoForge's {@code SlotItemHandler} reads its index the same
 * way. So nothing above this class needs to know pages exist — the slots are ordinary
 * ones, numbered nought upwards, and what moves underneath them is this.
 *
 * <p><b>Mutable, and there is only ever one per open screen.</b> A slot captures the
 * handler it was built with, so turning the page has to change this object rather than
 * make a new one. Rebuilding the slots instead would leave the client holding the old
 * list and reading items out of slots the server no longer has.
 *
 * <p>Because it belongs to the screen rather than to the chest, two players have two
 * of these and can be on different pages of the same chest. Nothing arranges that; it
 * is what ownership already means.
 */
public final class Window implements IItemHandlerModifiable {
    private final IItemHandlerModifiable contents;
    private final int size;
    private int page;

    public Window(IItemHandlerModifiable contents, int size) {
        this.contents = contents;
        this.size = size;
    }

    /** How many pages the contents behind this window come to, rounded up. */
    public int pages() {
        return Math.max(1, (contents.getSlots() + size - 1) / size);
    }

    public int page() {
        return page;
    }

    /**
     * Turns to a page, ignoring one that is not there.
     *
     * <p>Ignoring rather than clamping: the number arrives from a packet, so it is
     * whatever the other side said it was, and a client asking for page nine hundred
     * should be left where it is rather than quietly moved somewhere it did not ask
     * for.
     */
    public void turnTo(int wanted) {
        if (wanted >= 0 && wanted < pages()) {
            page = wanted;
        }
    }

    /**
     * Where slot {@code i} of this page really is.
     *
     * @return -1 when the last page is a short one and this slot is off the end
     */
    private int behind(int slot) {
        if (slot < 0 || slot >= size) {
            return -1;
        }
        int index = page * size + slot;
        return index < contents.getSlots() ? index : -1;
    }

    @Override
    public int getSlots() {
        return size;
    }

    @Override
    public int getSlotLimit(int slot) {
        int index = behind(slot);
        return index < 0 ? 0 : contents.getSlotLimit(index);
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        int index = behind(slot);
        return index < 0 ? ItemStack.EMPTY : contents.getStackInSlot(index);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        int index = behind(slot);
        if (index >= 0) {
            contents.setStackInSlot(index, stack);
        }
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        int index = behind(slot);
        return index < 0 ? stack : contents.insertItem(index, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        int index = behind(slot);
        return index < 0 ? ItemStack.EMPTY : contents.extractItem(index, amount, simulate);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        int index = behind(slot);
        return index >= 0 && contents.isItemValid(index, stack);
    }
}
