package io.github.capsicum0907.cella;

import java.util.Comparator;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * The orders a chest can be kept in.
 *
 * <p>One list, and the button cycles it. ⚠ <b>Every one of them groups by something that
 * does not move when a count does</b> — a chest ordered by how many of a thing there are
 * would rearrange itself as items arrived, which is expensive to maintain and impossible
 * to read. What changes between these is only which side of a kind another kind falls on.
 *
 * <p>Each is a <b>grouping</b> and nothing else; {@link #full} adds the one rule they all
 * share, that the full stacks of a kind come before its remainder. That rule is what makes
 * a kind a run with one partial at the end of it, which is what {@link Sorted} maintains.
 */
public enum Order {
    /**
     * Registry name. <b>The one that is always right</b>, and so the one a chest starts in.
     *
     * <p>It needs nothing but the item itself: no language, no tags, no client. It also
     * groups a mod's items together without being asked, because a registry name carries
     * its namespace.
     *
     * <p>⚠ What it is not is readable. {@code andesite_wall} is followed by
     * {@code baked_potato}, and a player reading the screen sees Andesite Wall followed by
     * whatever their resource pack calls a baked potato, with nothing connecting them.
     */
    REGISTRY("registry", Comparator.comparing(Order::name)),

    /**
     * The name on screen.
     *
     * <p>What the player is actually reading, and the answer to the complaint above.
     *
     * <p>⚠ <b>It needs a language, and the search runs on the server.</b> An integrated
     * server — single player, or a world opened to LAN — is the player's own game and has
     * their language, so this is exactly right there. A dedicated server has only what it
     * loaded itself: vanilla items come out in English and modded ones come out as their
     * translation keys. It stays deterministic and the same for everybody on that server;
     * it is simply not in anyone's language. Registry name is the order to leave a
     * dedicated server in.
     */
    DISPLAY("display", Comparator
            .comparing((ItemStack stack) -> stack.getHoverName().getString()
                    .toLowerCase(java.util.Locale.ROOT))
            .thenComparing(Order::name)),

    /**
     * Which mod it came from, and then the registry name within it.
     *
     * <p>For a pack where the question is <em>where is the Mekanism half of this chest</em>
     * rather than what any one item is called. Needs no language either.
     */
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

    /** Whether two stacks belong to the same run, as far as this order is concerned. */
    public Comparator<ItemStack> grouping() {
        return grouping;
    }

    /**
     * The whole order: the grouping, and then the full stacks of a kind before its
     * remainder so that a kind is a run with at most one partial at the end.
     */
    public Comparator<ItemStack> full() {
        return grouping.thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed());
    }

    public Component label() {
        return Component.translatable("gui.cella.sort." + id);
    }

    /** The next one round, so one button can reach all of them. */
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
