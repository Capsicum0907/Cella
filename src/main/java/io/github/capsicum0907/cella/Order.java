package io.github.capsicum0907.cella;

import java.util.Comparator;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public enum Order {
    REGISTRY("registry", Comparator.comparing(Order::name)),

    DISPLAY("display", Comparator
            .comparing((ItemStack stack) -> stack.getHoverName().getString()
                    .toLowerCase(java.util.Locale.ROOT))
            .thenComparing(Order::name)),

    SOURCE("source", Comparator
            .comparing((ItemStack stack) -> key(stack).getNamespace())
            .thenComparing(Order::name));

    private final String id;
    private final Comparator<ItemStack> grouping;

    Order(String id, Comparator<ItemStack> grouping) {
        this.id = id;
        this.grouping = grouping;
    }

    public String id() {
        return id;
    }

    public Comparator<ItemStack> grouping() {
        return grouping;
    }

    public Comparator<ItemStack> full() {
        return grouping.thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed());
    }

    public Component label() {
        return Component.translatable("gui.cella.sort." + id);
    }

    public Order next() {
        Order[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static Order of(String id) {
        for (Order order : values()) {
            if (order.id.equals(id)) {
                return order;
            }
        }
        return REGISTRY;
    }

    private static ResourceLocation key(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    private static String name(ItemStack stack) {
        return key(stack).toString();
    }
}
