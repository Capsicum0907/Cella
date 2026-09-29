package io.github.capsicum0907.cella;

import java.util.Set;

import it.unimi.dsi.fastutil.Hash;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;

public final class Alike {
    public static final Hash.Strategy<? super ItemStack> HASH = ItemStackLinkedSet.TYPE_AND_TAG;

    private Alike() {
    }

    public static boolean same(ItemStack one, ItemStack other) {
        return ItemStack.isSameItemSameComponents(one, other);
    }

    public static Set<ItemStack> set() {
        return ItemStackLinkedSet.createTypeAndComponentsSet();
    }

    public static int rank(ItemStack one, ItemStack other) {
        if (same(one, other)) {
            return 0;
        }
        int by = Integer.compare(one.getComponentsPatch().hashCode(),
                other.getComponentsPatch().hashCode());
        if (by != 0) {
            return by;
        }
        return one.getComponentsPatch().toString().compareTo(other.getComponentsPatch().toString());
    }
}
