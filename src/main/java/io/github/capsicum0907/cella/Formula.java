package io.github.capsicum0907.cella;

import java.util.LinkedHashMap;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.world.level.ItemLike;

/**
 * How one kind of chest is made, as data.
 *
 * <p>There was a column for "the ingredient in the middle" and one for "the form before
 * this one", which was enough while every recipe was the same shape. They are not: one is
 * nine different animals, one is eight of the last form round a block of gold, one is four
 * of the last form and four nether stars round a dragon egg, and one does not exist at all
 * because that form is not crafted.
 *
 * <p>So a kind carries its pattern and what the letters in it mean, and the recipe
 * provider walks that instead of knowing the shapes itself.
 *
 * <p>The ingredients are suppliers because a kind is built before the registries are, and
 * because several of them are other kinds of this same chest — which cannot be looked up
 * until they exist.
 */
public record Formula(List<String> pattern, Map<Character, Supplier<ItemLike>> of,
        int count, boolean spawns, boolean fuses) {
    /** A builder, because a map literal of nine entries is not a thing Java says nicely. */
    public static class Builder {
        private final List<String> pattern;
        private final Map<Character, Supplier<ItemLike>> of = new LinkedHashMap<>();
        private int count = 1;
        private boolean spawns;
        private boolean eatsAChest;

        private Builder(String top, String middle, String bottom) {
            this.pattern = List.of(top, middle, bottom);
        }

        public Builder key(char letter, Supplier<ItemLike> what) {
            of.put(letter, what);
            return this;
        }

        /**
         * The letter stands for another of these chests.
         *
         * <p>Which is also how a recipe knows it is a fusion: something that eats a Cella
         * has contents to carry into what it makes. Said by using this rather than by a
         * second flag, so the two cannot disagree.
         */
        public Builder key(char letter, Kind kind) {
            eatsAChest = true;
            return key(letter, () -> CellaRegistry.block(kind).get());
        }

        public Builder count(int made) {
            this.count = made;
            return this;
        }

        /**
         * The parent is not spent: it is still there afterwards.
         *
         * <p>A property of this recipe rather than of the block. The block coming back
         * every time it was crafted with - the way a bucket does - would make the form
         * above it free, since that one eats four.
         */
        public Builder spawning() {
            this.spawns = true;
            return this;
        }

        /**
         * <b>The order the letters were given in is kept.</b>
         *
         * <p>Not tidiness. {@code Map.copyOf} hands back an immutable map whose iteration
         * order is randomised once per JVM run, so the first entry out of it is not the
         * first entry in — and data generation reads exactly that to decide what unlocks
         * the recipe. It was picking a different ingredient every time it ran: noise in
         * every diff, and an advancement that said "has obsidian" where it meant "has an
         * Imperfect Cella". A generator has to be a function of its input.
         */
        public Formula done() {
            return new Formula(pattern,
                    java.util.Collections.unmodifiableMap(new LinkedHashMap<>(of)),
                    count, spawns, eatsAChest && !spawns);
        }
    }

    public static Builder shaped(String top, String middle, String bottom) {
        return new Builder(top, middle, bottom);
    }

    /**
     * Whether every Cella laid out has finished growing.
     *
     * <p><b>A recipe will not take one that has not.</b> Nothing here is put together out
     * of parts that were not ready — a form is what it ate, and half of what it ate is not
     * half a form, it is a form that is not done. So the check is on the ingredients rather
     * than on the result.
     *
     * <p><b>Here rather than in either recipe class</b>, because both want it and they are
     * different classes: {@code Fusing} eats what it is given and {@code Spawning} hands it
     * back, which is a difference about the ingredients afterwards and not about whether
     * they qualify.
     *
     * <p><b>Asked against the threshold written on the item</b>, not the one this version
     * declares. The bar the player watched fill to the end was drawn from the figure on the
     * item, and a bench that then refuses it would be calling that bar a liar. ⚠ Which also
     * means a Cella from before any of this could be fed carries no threshold at all, and
     * is refused: it never filled anything, and there is no way to tell whether it would
     * have.
     *
     * <p>Nothing explains the refusal, in keeping with the fit check above — a recipe that
     * does not hold simply is not a recipe, and the bench shows nothing. What tells the
     * player which one is short is the percentage on each item.
     */
    public static boolean grown(CraftingInput input) {
        for (int at = 0; at < input.size(); at++) {
            ItemStack laid = input.getItem(at);
            if (!(laid.getItem() instanceof BlockItem block)
                    || !(block.getBlock() instanceof CellaBlock chest)
                    || !chest.kind().grows()) {
                continue;
            }
            Held held = laid.get(CellaRegistry.KEPT.get());
            if (held == null || !held.grows() || held.grown() < 1.0F) {
                return false;
            }
        }
        return true;
    }
}
