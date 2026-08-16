package io.github.capsicum0907.cella;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * Sorting, over every page at once.
 *
 * <p><b>Why this exists when a sorting mod is already installed.</b> A sorting mod
 * works on the slots the open screen has, and this screen has one page of them. That
 * is the right answer for a chest and the wrong one for this: the pages are one chest,
 * and half-empty stacks of the same thing sitting on pages three and six is exactly
 * what wants tidying. Nothing outside can do it, because nothing outside can see past
 * the window.
 *
 * <p>Done to the contents rather than to the window, like everything else here that is
 * not about looking: the hopper, shift-click, and the comparator all go the same way
 * round.
 */
public final class Tidy {
    private Tidy() {
    }

    /**
     * Merges what can be merged, orders the rest, and packs it to the front.
     *
     * <p>Ordered by registry name rather than by the name on screen. A display name
     * needs a language, and there is no language on a server — sorting that came out
     * differently for a French player and an English one would also mean the chest
     * changed when somebody else pressed the button. Registry name groups a mod's items
     * together, which is close to what sorting by name would give anyway.
     */
    public static void everything(IItemHandlerModifiable contents) {
        List<ItemStack> gathered = new ArrayList<>();
        for (int slot = 0; slot < contents.getSlots(); slot++) {
            ItemStack stack = contents.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                gathered.add(stack.copy());
            }
            contents.setStackInSlot(slot, ItemStack.EMPTY);
        }

        List<ItemStack> merged = merge(gathered);
        merged.sort(Comparator
                .comparing((ItemStack stack) -> BuiltInRegistries.ITEM.getKey(stack.getItem()))
                // Full stacks first, so the odd remainder of each kind ends up last and a
                // second sort has nothing left to do.
                .thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed()));

        for (int slot = 0; slot < merged.size() && slot < contents.getSlots(); slot++) {
            contents.setStackInSlot(slot, merged.get(slot));
        }
    }

    /**
     * Pours partial stacks of the same thing together.
     *
     * <p>Quadratic in the number of <em>kinds</em>, not of slots, because anything that
     * is already full stops being a candidate. A chest of four hundred slots holding one
     * kind of stone does eight comparisons.
     */
    private static List<ItemStack> merge(List<ItemStack> gathered) {
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack stack : gathered) {
            for (ItemStack into : merged) {
                if (stack.isEmpty()) {
                    break;
                }
                int room = into.getMaxStackSize() - into.getCount();
                if (room > 0 && ItemStack.isSameItemSameComponents(into, stack)) {
                    int moved = Math.min(room, stack.getCount());
                    into.grow(moved);
                    stack.shrink(moved);
                }
            }
            if (!stack.isEmpty()) {
                merged.add(stack);
            }
        }
        return merged;
    }
}
