package io.github.capsicum0907.cella;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

public final class Window implements IItemHandlerModifiable {
    private final IItemHandlerModifiable held;

    private final int size;

    private int total;

    private int base;

    private final boolean chest;

    private int page;

    private int[] found;

    private int pages = UNTOLD;
    private int onThisPage = UNTOLD;

    private static final int UNTOLD = -1;

    private Window(IItemHandlerModifiable held, int size, int total, boolean chest) {
        this.held = held;
        this.size = size;
        this.total = total;
        this.chest = chest;
    }

    public static Window onto(IItemHandlerModifiable chest, int size) {
        return new Window(chest, size, chest.getSlots(), true);
    }

    public void search(String looking) {
        page = 0;
        if (looking.isBlank()) {
            wanted = null;
            found = null;
            return;
        }
        wanted = looking.toLowerCase(java.util.Locale.ROOT);
        rebuild();
    }

    private String wanted;

    public void limit(int first, int many) {
        base = first;
        total = many;
        page = 0;
        if (wanted != null) {
            rebuild();
        }
    }

    public int base() {
        return base;
    }

    private void rebuild() {
        int end = Math.min(held.getSlots(), base + total);
        int[] hits = new int[Math.max(0, end - base)];
        int count = 0;
        for (int slot = base; slot < end; slot++) {
            if (matches(slot)) {
                hits[count++] = slot;
            }
        }
        found = java.util.Arrays.copyOf(hits, count);
    }

    private boolean matches(int slot) {
        ItemStack stack = held.getStackInSlot(slot);
        if (stack.isEmpty()) {
            return false;
        }
        String path = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(stack.getItem()).getPath();
        return path.contains(wanted)
                || path.replace('_', ' ').contains(wanted)
                || stack.getHoverName().getString()
                        .toLowerCase(java.util.Locale.ROOT).contains(wanted);
    }

    public boolean changed(int slot) {
        if (found == null || slot < 0 || slot >= held.getSlots()) {
            return false;
        }
        int at = java.util.Arrays.binarySearch(found, slot);
        boolean listed = at >= 0;
        if (listed == matches(slot)) {
            return false;
        }
        found = listed ? dropped(at) : added(-at - 1, slot);
        clamp();
        return true;
    }

    public boolean again() {
        if (found == null) {
            return false;
        }
        rebuild();
        clamp();
        return true;
    }

    private int[] dropped(int at) {
        int[] out = new int[found.length - 1];
        System.arraycopy(found, 0, out, 0, at);
        System.arraycopy(found, at + 1, out, at, out.length - at);
        return out;
    }

    private int[] added(int at, int slot) {
        int[] out = new int[found.length + 1];
        System.arraycopy(found, 0, out, 0, at);
        out[at] = slot;
        System.arraycopy(found, at, out, at + 1, found.length - at);
        return out;
    }

    private void clamp() {
        page = Math.min(page, pages() - 1);
    }

    public boolean searching() {
        return found != null;
    }

    private int viewed() {
        return found != null ? found.length : total;
    }

    public int onThisPage() {
        if (found == null) {
            return Math.clamp(total - page * size, 0, size);
        }
        if (found.length == 0) {
            return 0;
        }
        int results = Math.clamp(found.length - page * size, 0, size);
        if (page < pages() - 1) {
            return results;
        }
        return results + Math.min(size - results,
                Math.min(held.getSlots(), base + total) - free());
    }

    private int free() {
        if (!(held instanceof Sorted sorted)) {
            return held.getSlots();
        }
        int part = sorted.partOf(base);
        return part < 0 ? held.getSlots() : sorted.firstFree(part);
    }

    public void told(int pages, int onThisPage) {
        this.pages = pages;
        this.onThisPage = onThisPage;
    }

    public static Window of(IItemHandlerModifiable shown, int size, int total) {
        return new Window(shown, size, total, false);
    }

    public int pages() {
        if (!chest && pages != UNTOLD) {
            return Math.max(1, pages);
        }
        return Math.max(1, (viewed() + size - 1) / size);
    }

    public int page() {
        return page;
    }

    public void openAt(int page) {
        this.page = page;
    }

    public boolean holds(int slot) {
        if (!chest && onThisPage == UNTOLD) {
            return page * size + slot < total;
        }
        return slot < (chest ? onThisPage() : onThisPage);
    }

    private boolean there(int slot) {
        return at(slot) < held.getSlots();
    }

    private int at(int slot) {
        if (!chest) {
            return slot;
        }
        int into = page * size + slot;
        if (found == null) {
            return base + into;
        }
        if (into < found.length) {
            return found[into];
        }

        return free() + (into - found.length);
    }

    @Override
    public int getSlots() {
        return size;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return there(slot) ? held.getStackInSlot(at(slot)) : ItemStack.EMPTY;
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (chest && !holds(slot)) {
            return;
        }
        if (there(slot)) {
            held.setStackInSlot(at(slot), stack);
        }
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return holds(slot) ? held.insertItem(at(slot), stack, simulate) : stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return holds(slot) ? held.extractItem(at(slot), amount, simulate) : ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return holds(slot) ? held.getSlotLimit(at(slot)) : 0;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return holds(slot) && held.isItemValid(at(slot), stack);
    }
}
