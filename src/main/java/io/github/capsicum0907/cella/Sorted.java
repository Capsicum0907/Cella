package io.github.capsicum0907.cella;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.neoforged.neoforge.items.ItemStackHandler;

public class Sorted extends ItemStackHandler {
    public interface Carve {
        int count();

        int first(int index);

        int past(int index);
    }

    private Carve carve;

    private int[] used = new int[1];

    private boolean raw;

    private Order order = Order.REGISTRY;

    public Sorted(int size) {
        super(size);
        carve = whole();
    }

    public Sorted() {
        super();
        carve = whole();
    }

    private Carve whole() {
        return new Carve() {
            @Override
            public int count() {
                return 1;
            }

            @Override
            public int first(int index) {
                return 0;
            }

            @Override
            public int past(int index) {
                return getSlots();
            }
        };
    }

    public void carve(Carve wanted) {
        Carve next = wanted == null ? whole() : wanted;
        java.util.List<ItemStack> stray = new java.util.ArrayList<>();
        it.unimi.dsi.fastutil.ints.IntArrayList from =
                new it.unimi.dsi.fastutil.ints.IntArrayList();
        for (int slot = 0; slot < getSlots(); slot++) {
            ItemStack stack = stacks.get(slot);
            if (stack.isEmpty() || covered(next, slot)) {
                continue;
            }
            stray.add(stack.copy());
            from.add(slot);
            stacks.set(slot, ItemStack.EMPTY);
        }
        carve = next;
        settle();
        for (int at = 0; at < stray.size(); at++) {
            ItemStack one = stray.get(at);
            for (int part = 0; part < carve.count() && !one.isEmpty(); part++) {
                one = put(one, part);
            }
            stacks.set(from.getInt(at), one);
        }
    }

    private static boolean covered(Carve of, int slot) {
        for (int part = 0; part < of.count(); part++) {
            if (slot >= of.first(part) && slot < of.past(part)) {
                return true;
            }
        }
        return false;
    }

    public Order order() {
        return order;
    }

    public void order(Order wanted) {
        order = wanted;
        if (!raw) {
            settle();
        }
    }

    public void straight(Runnable work) {
        java.util.List<Object2IntMap<ItemStack>> before = reporting() ? census() : null;
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

    public void settle() {
        boolean was = raw;
        raw = true;
        try {
            int[] counted = new int[carve.count()];
            for (int part = 0; part < counted.length; part++) {
                counted[part] = Tidy.range(this, order, carve.first(part), carve.past(part));
            }
            used = counted;
        } finally {
            raw = was;
        }
        moved(0);
    }

    public int used() {
        int all = 0;
        for (int one : used) {
            all += one;
        }
        return all;
    }

    public int used(int part) {
        return part >= 0 && part < used.length ? used[part] : 0;
    }

    public int parts() {
        return used.length;
    }

    public int partOf(int slot) {
        for (int part = 0; part < carve.count(); part++) {
            if (slot >= carve.first(part) && slot < carve.past(part)) {
                return part;
            }
        }
        return -1;
    }

    public int firstFree(int part) {
        return part < 0 ? -1 : carve.first(part) + used(part);
    }

    public int roomIn(int part, ItemStack stack) {
        return part < 0 ? 0 : room(stack, part);
    }

    public boolean rearranging() {
        return raw;
    }

    @Override
    public void setSize(int size) {
        super.setSize(size);
        used = new int[Math.max(1, used.length)];
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
        int part = partOf(slot);
        if (part < 0) {
            return;
        }
        int from = getSlots();
        boolean ran = false;
        if (slot < firstFree(part)) {
            from = Math.min(from, slot);
            ItemStack gone = copyWith(stacks.get(slot), 1);
            int had = total(gone, part);
            pull(slot, part);
            poured(part, gone, had, total(gone, part));
            ran = true;
        }
        if (!stack.isEmpty()) {
            ItemStack over = put(stack.copy(), part);
            from = Math.min(from, landed);
            ran |= shifted;
            if (!over.isEmpty()) {
                stacks.set(slot, over);
                used[part] = Math.max(used[part], slot - carve.first(part) + 1);
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
        int part = partOf(slot);
        if (part < 0) {
            return stack;
        }
        if (simulate) {
            int taken = Math.min(stack.getCount(), room(stack, part));
            return taken >= stack.getCount()
                    ? ItemStack.EMPTY
                    : copyWith(stack, stack.getCount() - taken);
        }
        ItemStack over = put(stack.copy(), part);
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
        int part = partOf(slot);
        if (amount <= 0 || part < 0 || slot >= firstFree(part)) {
            return ItemStack.EMPTY;
        }
        ItemStack asked = stacks.get(slot);
        int last = endOf(slot, part) - 1;
        ItemStack from = stacks.get(last);
        int taken = Math.min(amount, from.getCount());
        if (simulate) {
            return copyWith(asked, taken);
        }
        ItemStack out = copyWith(from, taken);
        ItemStack kind = copyWith(from, 1);
        int had = total(kind, part);
        if (taken >= from.getCount()) {
            pull(last, part);
            moved(last);
        } else {
            from.shrink(taken);
            onContentsChanged(last);
        }
        poured(part, kind, had, total(kind, part));
        return out;
    }

    private int landed;

    private boolean shifted;

    private ItemStack put(ItemStack incoming, int part) {
        landed = getSlots();
        shifted = false;
        ItemStack kind = copyWith(incoming, 1);
        int had = total(kind, part);
        int at = start(incoming, part);
        int end = endOf(at, part);

        if (at < end) {
            ItemStack open = stacks.get(end - 1);
            if (same(open, incoming) && open.getCount() < limitFor(open)) {
                int moved = Math.min(limitFor(open) - open.getCount(), incoming.getCount());
                open.grow(moved);
                incoming.shrink(moved);
                landed = end - 1;
            }
        }

        while (!incoming.isEmpty() && firstFree(part) < carve.past(part)) {
            int room = Math.min(limitFor(incoming), incoming.getCount());
            ItemStack one = copyWith(incoming, room);
            incoming.shrink(room);
            int where = place(one, part);
            shiftRight(where, part);
            stacks.set(where, one);
            used[part]++;
            shifted = true;
            landed = Math.min(landed, where);
        }
        poured(part, kind, had, total(kind, part));
        return incoming;
    }

    private int place(ItemStack stack, int part) {
        int low = carve.first(part);
        int high = firstFree(part);
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

    private int start(ItemStack stack, int part) {
        int low = carve.first(part);
        int high = firstFree(part);
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

    private int endOf(int slot, int part) {
        int end = firstFree(part);
        if (slot >= end) {
            return end;
        }
        ItemStack like = stacks.get(slot);
        int at = slot + 1;
        while (at < end && order.grouping().compare(stacks.get(at), like) == 0) {
            at++;
        }
        return at;
    }

    private void pull(int slot, int part) {
        int end = firstFree(part);
        for (int at = slot; at + 1 < end; at++) {
            stacks.set(at, stacks.get(at + 1));
        }
        stacks.set(end - 1, ItemStack.EMPTY);
        used[part]--;
    }

    private void shiftRight(int slot, int part) {
        for (int at = firstFree(part); at > slot; at--) {
            stacks.set(at, stacks.get(at - 1));
        }
        stacks.set(slot, ItemStack.EMPTY);
    }

    private int room(ItemStack stack, int part) {
        int at = start(stack, part);
        int end = endOf(at, part);
        int open = 0;
        if (at < end) {
            ItemStack last = stacks.get(end - 1);
            if (same(last, stack)) {
                open = limitFor(last) - last.getCount();
            }
        }
        return open + (carve.past(part) - firstFree(part)) * limitFor(stack);
    }

    private int total(ItemStack like, int part) {
        int at = start(like, part);
        int end = firstFree(part);
        int sum = 0;
        while (at < end && order.grouping().compare(stacks.get(at), like) == 0) {
            if (same(stacks.get(at), like)) {
                sum += stacks.get(at).getCount();
            }
            at++;
        }
        return sum;
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

    private java.util.List<Object2IntMap<ItemStack>> census() {
        java.util.List<Object2IntMap<ItemStack>> all = new java.util.ArrayList<>();
        for (int part = 0; part < carve.count(); part++) {
            Object2IntMap<ItemStack> counts =
                    new Object2IntOpenCustomHashMap<>(ItemStackLinkedSet.TYPE_AND_TAG);
            int end = firstFree(part);
            for (int at = carve.first(part); at < end; at++) {
                ItemStack stack = stacks.get(at);
                counts.mergeInt(copyWith(stack, 1), stack.getCount(), Integer::sum);
            }
            all.add(counts);
        }
        return all;
    }

    private void difference(java.util.List<Object2IntMap<ItemStack>> before,
            java.util.List<Object2IntMap<ItemStack>> after) {
        int parts = Math.min(before.size(), after.size());
        for (int part = 0; part < parts; part++) {
            Object2IntMap<ItemStack> was = before.get(part);
            Object2IntMap<ItemStack> now = after.get(part);
            for (Object2IntMap.Entry<ItemStack> one : was.object2IntEntrySet()) {
                poured(part, one.getKey(), one.getIntValue(), now.getInt(one.getKey()));
            }
            for (Object2IntMap.Entry<ItemStack> one : now.object2IntEntrySet()) {
                if (!was.containsKey(one.getKey())) {
                    poured(part, one.getKey(), 0, one.getIntValue());
                }
            }
        }
    }

    protected void poured(int part, ItemStack kind, int before, int after) {
    }

    protected void moved(int from) {
        onContentsChanged(from);
    }
}
