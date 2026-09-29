package io.github.capsicum0907.cella;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

public final class Tidy {
    private Tidy() {
    }

    public static void everything(IItemHandlerModifiable contents) {
        everything(contents, contents instanceof Sorted sorted ? sorted.order() : Order.REGISTRY);
    }

    public static void everything(IItemHandlerModifiable contents, Order order) {
        if (contents instanceof Sorted sorted && !sorted.rearranging()) {
            sorted.settle();
            return;
        }
        range(contents, order, 0, contents.getSlots());
    }

    public static int range(IItemHandlerModifiable contents, Order order, int from, int to) {
        List<ItemStack> gathered = new ArrayList<>();
        for (int slot = from; slot < to; slot++) {
            ItemStack stack = contents.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                gathered.add(stack.copy());
            }
            contents.setStackInSlot(slot, ItemStack.EMPTY);
        }

        List<ItemStack> merged = merge(gathered);
        merged.sort(order.full());

        int at = from;
        for (ItemStack stack : merged) {
            if (at >= to) {
                break;
            }
            contents.setStackInSlot(at++, stack);
        }
        return at - from;
    }

    private static List<ItemStack> merge(List<ItemStack> gathered) {
        List<ItemStack> merged = new ArrayList<>();
        Map<ItemStack, ItemStack> open =
                new Object2ObjectOpenCustomHashMap<>(Alike.HASH);

        for (ItemStack stack : gathered) {
            ItemStack into = open.get(stack);
            if (into != null) {
                int room = into.getMaxStackSize() - into.getCount();
                int moved = Math.min(room, stack.getCount());
                into.grow(moved);
                stack.shrink(moved);
                if (into.getCount() >= into.getMaxStackSize()) {
                    open.remove(into);
                }
            }
            if (!stack.isEmpty()) {
                merged.add(stack);
                open.put(stack, stack);
            }
        }
        return merged;
    }
}
