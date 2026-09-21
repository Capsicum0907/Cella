package io.github.capsicum0907.cella;

import net.neoforged.neoforge.items.SlotItemHandler;

public class PagedSlot extends SlotItemHandler {
    private final Window window;

    public PagedSlot(Window window, int index, int x, int y) {
        super(window, index, x, y);
        this.window = window;
    }

    @Override
    public boolean isActive() {
        return window.holds(getSlotIndex());
    }
}
