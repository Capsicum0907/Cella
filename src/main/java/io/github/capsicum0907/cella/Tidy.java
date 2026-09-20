package io.github.capsicum0907.cella;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
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
        // ⚠ A chest that keeps itself in order closes the gap behind every slot this
        // empties, so the loop below would read some slots twice and miss others. Asking
        // it to stand still first is the whole of the difference; see Sorted#rearranging.
        if (contents instanceof Sorted sorted && !sorted.rearranging()) {
            sorted.settle();
            return;
        }
        List<ItemStack> gathered = new ArrayList<>();
        for (int slot = 0; slot < contents.getSlots(); slot++) {
            ItemStack stack = contents.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                gathered.add(stack.copy());
            }
            contents.setStackInSlot(slot, ItemStack.EMPTY);
        }

        List<ItemStack> merged = merge(gathered);
        merged.sort(ORDER);

        for (int slot = 0; slot < merged.size() && slot < contents.getSlots(); slot++) {
            contents.setStackInSlot(slot, merged.get(slot));
        }
    }

    /**
     * The order the chest is kept in.
     *
     * <p>Registry name, and then the full stacks of each kind before its odd remainder.
     * <b>Both halves are load-bearing now that the chest is kept in this order rather than
     * put into it on request</b> — see {@link Sorted}. Full stacks first means a kind is a
     * run of full stacks with at most one partial at the end of it, so an item arriving
     * tops up that one partial and an item leaving comes out of it, and neither disturbs
     * anything before it.
     *
     * <p>⚠ <b>Nothing here changes when a count changes, except within its own kind.</b> An
     * order keyed on anything that moves with the contents — how many there are, how full
     * the chest is — would have items swapping places as they arrive, which is both
     * expensive and unreadable.
     */
    public static final Comparator<ItemStack> ORDER = Comparator
            .comparing((ItemStack stack) -> BuiltInRegistries.ITEM.getKey(stack.getItem()))
            .thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed());

    /**
     * Pours partial stacks of the same thing together.
     *
     * <p><b>Looked up, not searched for.</b> The obvious way is to scan what has been
     * gathered so far for something this will go into, which is fine for a chest holding
     * three kinds and quadratic for one holding a thousand. A chest of eighteen hundred
     * slots full of distinct things is about a million and a half comparisons; ten
     * thousand slots would be fifty million, and that is the wall this design walks into
     * as chests get bigger.
     *
     * <p>So the open stack of each kind is kept in a map keyed on item <em>and</em>
     * components - {@code ItemStackLinkedSet.TYPE_AND_TAG} is the game's own hash for
     * exactly that question, so an enchanted pickaxe still does not pour into the plain
     * ones. One lookup per stack, and the whole thing is linear.
     */
    private static List<ItemStack> merge(List<ItemStack> gathered) {
        List<ItemStack> merged = new ArrayList<>();
        Map<ItemStack, ItemStack> open =
                new Object2ObjectOpenCustomHashMap<>(ItemStackLinkedSet.TYPE_AND_TAG);

        for (ItemStack stack : gathered) {
            ItemStack into = open.get(stack);
            if (into != null) {
                int room = into.getMaxStackSize() - into.getCount();
                int moved = Math.min(room, stack.getCount());
                into.grow(moved);
                stack.shrink(moved);
                if (into.getCount() >= into.getMaxStackSize()) {
                    // Full: it can take no more, so it stops being the one to look up.
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
