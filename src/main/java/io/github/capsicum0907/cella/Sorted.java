package io.github.capsicum0907.cella;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.neoforged.neoforge.items.ItemStackHandler;

public class Sorted extends ItemStackHandler {
    private int used;

    private boolean raw;

    private Order order = Order.REGISTRY;

    public Order order() {
        return order;
    }

    public void order(Order wanted) {
        order = wanted;

        if (!raw) {
            settle();
        }
    }

    public Sorted(int size) {
        super(size);
    }

    public Sorted() {
        super();
    }

    public void straight(Runnable work) {
        Object2IntMap<ItemStack> before = reporting() ? census() : null;
        boolean was = raw;
        raw = true;
        try {
            work.run();
        } finally {
            raw = was;
        }
        if (!raw) {
            settle();
        }
        if (before != null) {
            difference(before, census());
        }
    }

    protected boolean reporting() {
        return false;
    }

    private Object2IntMap<ItemStack> census() {
        Object2IntMap<ItemStack> counts =
                new Object2IntOpenCustomHashMap<>(ItemStackLinkedSet.TYPE_AND_TAG);
        for (int at = 0; at < used; at++) {
            ItemStack stack = stacks.get(at);
            counts.mergeInt(copyWith(stack, 1), stack.getCount(), Integer::sum);
        }
        return counts;
    }

    private void difference(Object2IntMap<ItemStack> before, Object2IntMap<ItemStack> after) {
        for (Object2IntMap.Entry<ItemStack> was : before.object2IntEntrySet()) {
            poured(was.getKey(), was.getIntValue(), after.getInt(was.getKey()));
        }
        for (Object2IntMap.Entry<ItemStack> now : after.object2IntEntrySet()) {
            if (!before.containsKey(now.getKey())) {
                poured(now.getKey(), 0, now.getIntValue());
            }
        }
    }

    public void settle() {
        boolean was = raw;
        raw = true;
        try {
            Tidy.everything(this, order);
        } finally {
            raw = was;
        }
        used = 0;
        while (used < getSlots() && !stacks.get(used).isEmpty()) {
            used++;
        }
        moved(0);
    }

    public int used() {
        return used;
    }

    public boolean rearranging() {
        return raw;
    }

    @Override
    public void setSize(int size) {
        super.setSize(size);
        used = 0;
    }

    private static final String ORDER = "Order";

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        CompoundTag tag = super.serializeNBT(registries);
        tag.putString(ORDER, order.id());
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
        super.deserializeNBT(registries, tag);

        order = Order.of(tag.getString(ORDER));
        settle();
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (raw) {
            super.setStackInSlot(slot, stack);
            return;
        }
        validateSlotIndex(slot);
        int from = getSlots();
        boolean ran = false;
        if (slot < used) {
            from = Math.min(from, slot);
            ItemStack gone = copyWith(stacks.get(slot), 1);
            int had = total(gone);
            pull(slot);
            poured(gone, had, total(gone));
            ran = true;
        }
        if (!stack.isEmpty()) {
            ItemStack over = put(stack.copy());
            from = Math.min(from, landed);
            ran |= shifted;
            if (!over.isEmpty()) {
                stacks.set(slot, over);
                used = Math.max(used, slot + 1);
                ran = true;
            }
        }
        if (from < getSlots()) {
            if (ran) {
                moved(from);
            } else {
                onContentsChanged(from);
            }
        }
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (raw || stack.isEmpty()) {
            return raw ? super.insertItem(slot, stack, simulate) : stack;
        }
        validateSlotIndex(slot);
        if (simulate) {
            int taken = Math.min(stack.getCount(), room(stack));
            return taken >= stack.getCount() ? ItemStack.EMPTY : copyWith(stack, stack.getCount() - taken);
        }
        ItemStack over = put(stack.copy());
        if (over.getCount() != stack.getCount()) {
            if (shifted) {
                moved(landed);
            } else {
                onContentsChanged(landed);
            }
        }
        return over;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (raw) {
            return super.extractItem(slot, amount, simulate);
        }
        validateSlotIndex(slot);
        if (amount <= 0 || slot >= used) {
            return ItemStack.EMPTY;
        }
        ItemStack asked = stacks.get(slot);

        int last = endOf(slot) - 1;
        ItemStack from = stacks.get(last);
        int taken = Math.min(amount, from.getCount());
        if (simulate) {
            return copyWith(asked, taken);
        }
        ItemStack out = copyWith(from, taken);
        ItemStack kind = copyWith(from, 1);
        int had = total(kind);
        if (taken >= from.getCount()) {
            pull(last);
            moved(last);
        } else {
            from.shrink(taken);
            onContentsChanged(last);
        }
        poured(kind, had, total(kind));
        return out;
    }

    private int landed;

    private boolean shifted;

    private ItemStack put(ItemStack incoming) {
        landed = getSlots();
        shifted = false;
        ItemStack kind = copyWith(incoming, 1);
        int had = total(kind);
        int at = start(incoming);
        int end = endOf(at);

        if (at < end) {
            ItemStack open = stacks.get(end - 1);
            if (same(open, incoming) && open.getCount() < limitFor(open)) {
                int moved = Math.min(limitFor(open) - open.getCount(), incoming.getCount());
                open.grow(moved);
                incoming.shrink(moved);
                landed = end - 1;
            }
        }

        while (!incoming.isEmpty() && used < getSlots()) {
            int room = Math.min(limitFor(incoming), incoming.getCount());
            ItemStack one = copyWith(incoming, room);
            incoming.shrink(room);
            int where = place(one);
            shiftRight(where);
            shifted = true;
            stacks.set(where, one);
            used++;
            landed = Math.min(landed, where);
        }
        poured(kind, had, total(kind));
        return incoming;
    }

    private int place(ItemStack stack) {
        int low = 0;
        int high = used;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (order.full().compare(stacks.get(middle), stack) <= 0) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private int start(ItemStack stack) {
        int low = 0;
        int high = used;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (order.grouping().compare(stacks.get(middle), stack) < 0) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private int endOf(int slot) {
        ItemStack like = stacks.get(slot);
        int at = slot + 1;
        while (at < used && order.grouping().compare(stacks.get(at), like) == 0) {
            at++;
        }
        return at;
    }

    private void pull(int slot) {
        for (int at = slot; at + 1 < used; at++) {
            stacks.set(at, stacks.get(at + 1));
        }
        stacks.set(used - 1, ItemStack.EMPTY);
        used--;
    }

    private void shiftRight(int slot) {
        for (int at = used; at > slot; at--) {
            stacks.set(at, stacks.get(at - 1));
        }
        stacks.set(slot, ItemStack.EMPTY);
    }

    private int room(ItemStack stack) {
        int at = start(stack);
        int end = endOf(at);
        int open = 0;
        if (at < end) {
            ItemStack last = stacks.get(end - 1);
            if (same(last, stack)) {
                open = limitFor(last) - last.getCount();
            }
        }
        return open + (getSlots() - used) * limitFor(stack);
    }

    private int limitFor(ItemStack stack) {
        return Math.min(getSlotLimit(0), stack.getMaxStackSize());
    }

    private static boolean same(ItemStack one, ItemStack other) {
        return ItemStack.isSameItemSameComponents(one, other);
    }

    private static ItemStack copyWith(ItemStack stack, int count) {
        ItemStack out = stack.copy();
        out.setCount(count);
        return out;
    }

    private int total(ItemStack like) {
        int at = start(like);
        int sum = 0;
        while (at < used && order.grouping().compare(stacks.get(at), like) == 0) {
            if (same(stacks.get(at), like)) {
                sum += stacks.get(at).getCount();
            }
            at++;
        }
        return sum;
    }

    protected void poured(ItemStack kind, int before, int after) {
    }

    protected void moved(int from) {
        onContentsChanged(from);
    }
}
