package io.github.capsicum0907.cella;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

public final class Space {
    private final IItemHandler slots;

    public Space(IItemHandler slots) {
        this.slots = slots;
    }

    private int limit(int slot, ItemStack kind) {
        return Math.min(slots.getSlotLimit(slot), kind.getMaxStackSize());
    }

    public int room(ItemStack kind) {
        int room = 0;
        for (int slot = 0; slot < slots.getSlots(); slot++) {
            ItemStack held = slots.getStackInSlot(slot);
            if (held.isEmpty()) {
                room += limit(slot, kind);
            } else if (ItemStack.isSameItemSameComponents(held, kind)) {
                room += Math.max(0, limit(slot, kind) - held.getCount());
            }
        }
        return room;
    }

    public boolean fits(java.util.List<ItemStack> kinds, java.util.List<Integer> counts) {
        int empty = 0;
        for (int slot = 0; slot < slots.getSlots(); slot++) {
            if (slots.getStackInSlot(slot).isEmpty()) {
                empty++;
            }
        }
        int needed = 0;
        for (int at = 0; at < kinds.size(); at++) {
            ItemStack kind = kinds.get(at);
            int topUp = 0;
            int limit = slots.getSlots() == 0 ? 0 : limit(0, kind);
            if (limit <= 0) {
                return false;
            }
            for (int slot = 0; slot < slots.getSlots(); slot++) {
                ItemStack held = slots.getStackInSlot(slot);
                if (!held.isEmpty() && ItemStack.isSameItemSameComponents(held, kind)) {
                    topUp += Math.max(0, limit(slot, kind) - held.getCount());
                }
            }
            int left = Math.max(0, counts.get(at) - topUp);
            needed += (left + limit - 1) / limit;
        }
        return needed <= empty;
    }

    public int takeOut(ItemStack kind, int many) {
        IItemHandlerModifiable from = (IItemHandlerModifiable) slots;
        int left = many;
        for (int slot = from.getSlots() - 1; slot >= 0 && left > 0; slot--) {
            ItemStack held = from.getStackInSlot(slot);
            if (!held.isEmpty() && ItemStack.isSameItemSameComponents(held, kind)) {
                int moved = Math.min(left, held.getCount());
                int rest = held.getCount() - moved;
                from.setStackInSlot(slot, rest > 0 ? held.copyWithCount(rest) : ItemStack.EMPTY);
                left -= moved;
            }
        }
        return many - left;
    }

    public boolean full() {
        for (int slot = 0; slot < slots.getSlots(); slot++) {
            ItemStack held = slots.getStackInSlot(slot);
            if (held.isEmpty() || held.getCount() < limit(slot, held)) {
                return false;
            }
        }
        return true;
    }

    public int put(ItemStack kind, int many) {
        IItemHandlerModifiable into = (IItemHandlerModifiable) slots;
        int left = many;
        for (int slot = 0; slot < into.getSlots() && left > 0; slot++) {
            ItemStack held = into.getStackInSlot(slot);
            if (!held.isEmpty() && ItemStack.isSameItemSameComponents(held, kind)) {
                int moved = Math.min(left, Math.max(0, limit(slot, kind) - held.getCount()));
                into.setStackInSlot(slot, held.copyWithCount(held.getCount() + moved));
                left -= moved;
            }
        }
        for (int slot = 0; slot < into.getSlots() && left > 0; slot++) {
            if (into.getStackInSlot(slot).isEmpty()) {
                int moved = Math.min(left, limit(slot, kind));
                into.setStackInSlot(slot, kind.copyWithCount(moved));
                left -= moved;
            }
        }
        return many - left;
    }
}
