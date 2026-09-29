package io.github.capsicum0907.cella;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class Filing {
    static final String SIZE = "Size";
    static final String ORDER = "Order";
    static final String PLAN = "Plan";

    static final String PARTS = "Parts";
    private static final String LOOSE = "Loose";
    private static final String ITEMS = "Items";

    private Filing() {
    }

    public record Opened(int size, Order order, Plan plan, List<List<ItemStack>> held,
            List<ItemStack> loose) {
    }

    public static CompoundTag write(Sorted contents, Plan plan, HolderLookup.Provider registries) {
        int slots = contents.getSlots();
        boolean[] claimed = new boolean[slots];
        ListTag parts = new ListTag();
        for (int index = 0; index < plan.count(slots); index++) {
            ListTag one = new ListTag();
            int end = plan.past(index, slots);
            for (int slot = plan.first(index, slots); slot < end; slot++) {
                claimed[slot] = true;
                ItemStack stack = contents.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    one.add(stack.save(registries));
                }
            }
            parts.add(one);
        }
        ListTag loose = new ListTag();
        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = contents.getStackInSlot(slot);
            if (!claimed[slot] && !stack.isEmpty()) {
                loose.add(stack.save(registries));
            }
        }

        CompoundTag tag = new CompoundTag();
        tag.putInt(SIZE, slots);
        tag.putString(ORDER, contents.order().id());
        tag.put(PLAN, plan.save());
        tag.put(PARTS, parts);
        if (!loose.isEmpty()) {
            tag.put(LOOSE, loose);
        }
        return tag;
    }

    public static Opened read(CompoundTag tag, HolderLookup.Provider registries,
            CompoundTag carvedElsewhere, int sized) {
        Order order = Order.of(tag.getString(ORDER));
        CompoundTag carved = tag.contains(PLAN, Tag.TAG_COMPOUND)
                ? tag.getCompound(PLAN)
                : carvedElsewhere;
        return tag.contains(PARTS, Tag.TAG_LIST)
                ? parted(tag, registries, order, carved, sized)
                : slotted(tag, registries, order, carved, sized);
    }

    private static Opened parted(CompoundTag tag, HolderLookup.Provider registries, Order order,
            CompoundTag carved, int sized) {
        int size = tag.contains(SIZE, Tag.TAG_INT) ? tag.getInt(SIZE) : sized;
        Plan plan = new Plan();
        plan.load(carved, size);
        int count = plan.count(size);
        List<List<ItemStack>> held = new ArrayList<>();
        List<ItemStack> loose = new ArrayList<>(stacks(tag.getList(LOOSE, Tag.TAG_COMPOUND), registries));
        ListTag parts = tag.getList(PARTS, Tag.TAG_LIST);
        for (int index = 0; index < parts.size(); index++) {
            List<ItemStack> one = stacks(parts.getList(index), registries);
            if (index < count) {
                held.add(one);
            } else {
                loose.addAll(one);
            }
        }
        while (held.size() < count) {
            held.add(new ArrayList<>());
        }
        return new Opened(size, order, plan, held, loose);
    }

    private static Opened slotted(CompoundTag tag, HolderLookup.Provider registries, Order order,
            CompoundTag carved, int sized) {
        ItemStackHandler from = new ItemStackHandler(sized);
        from.deserializeNBT(registries, tag);
        int size = from.getSlots();
        Plan plan = new Plan();
        plan.load(carved, size);
        boolean[] claimed = new boolean[size];
        List<List<ItemStack>> held = new ArrayList<>();
        for (int index = 0; index < plan.count(size); index++) {
            List<ItemStack> one = new ArrayList<>();
            int end = plan.past(index, size);
            for (int slot = plan.first(index, size); slot < end; slot++) {
                claimed[slot] = true;
                if (!from.getStackInSlot(slot).isEmpty()) {
                    one.add(from.getStackInSlot(slot));
                }
            }
            held.add(one);
        }
        List<ItemStack> loose = new ArrayList<>();
        for (int slot = 0; slot < size; slot++) {
            if (!claimed[slot] && !from.getStackInSlot(slot).isEmpty()) {
                loose.add(from.getStackInSlot(slot));
            }
        }
        return new Opened(size, order, plan, held, loose);
    }

    private static List<ItemStack> stacks(ListTag list, HolderLookup.Provider registries) {
        List<ItemStack> out = new ArrayList<>();
        for (int at = 0; at < list.size(); at++) {
            ItemStack stack = ItemStack.parseOptional(registries, list.getCompound(at));
            if (!stack.isEmpty()) {
                out.add(stack);
            }
        }
        return out;
    }

    public static int used(CompoundTag tag) {
        if (!tag.contains(PARTS, Tag.TAG_LIST)) {
            return tag.getList(ITEMS, Tag.TAG_COMPOUND).size();
        }
        int used = tag.getList(LOOSE, Tag.TAG_COMPOUND).size();
        ListTag parts = tag.getList(PARTS, Tag.TAG_LIST);
        for (int index = 0; index < parts.size(); index++) {
            used += parts.getList(index).size();
        }
        return used;
    }

    public static int size(CompoundTag tag) {
        return tag.getInt(SIZE);
    }
}
