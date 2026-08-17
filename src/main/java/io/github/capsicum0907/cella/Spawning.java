package io.github.capsicum0907.cella;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

/**
 * A recipe that gives one of its ingredients back: the parent is not consumed.
 *
 * <p>Cella Jr. is made from a Perfect Cella and <b>the Perfect stays where it is</b>. It
 * is not being spent, it is spawning — which is what Cell did, seven at a time, and stood
 * there afterwards.
 *
 * <p><b>Why this is a recipe and not a property of the item.</b> An item can name what it
 * leaves behind when it is used ({@code craftRemainder}), and that would work here and
 * break everything else: a Perfect that always came back would make Super Perfect free,
 * since that recipe eats four of them. What stays behind is a fact about <em>this</em>
 * recipe, so it lives on the recipe.
 *
 * <p>The rule is "any chest of this mod used here stays", rather than naming Perfect. A
 * recipe that spawns has one parent in it by construction, and saying so generally means
 * the next one of these does not need a second class.
 */
public class Spawning extends ShapedRecipe {
    private final ItemStack made;

    public Spawning(String group, CraftingBookCategory category, ShapedRecipePattern pattern,
            ItemStack result) {
        super(group, category, pattern, result);
        this.made = result;
    }

    /** Kept for the codec: the superclass holds the result where nobody else can read it. */
    public ItemStack made() {
        return made;
    }

    /**
     * The same requirement a fusion has: every Cella laid out has finished growing.
     *
     * <p>Spawning hands the parent back rather than eating it, which is a difference about
     * what happens to the ingredients and not about which ingredients qualify. See
     * {@link Formula#grown}.
     *
     * <p><b>Kept deliberately, after being asked whether it should be.</b> ⚠ It is not a
     * price — the parent comes back with everything it had, so it is paid once and never
     * again, and after the first time it costs nothing to spawn seven more. What it is is
     * a gate on <em>when</em>: a Perfect that has taken in all it can use is one nether
     * star away from ending itself, so requiring it says <b>Junior comes to somebody who
     * has already taken a Perfect to the top</b>. Which is where it comes from in the story
     * as well.
     *
     * <p>⚠ The argument against is the same fact read the other way: a gate that only bites
     * once is friction every time after that. If it starts reading as friction, this is the
     * line to delete — and the rule in {@link Formula#grown} would then have its first
     * exception, which is worth a sentence there when it happens.
     */
    @Override
    public boolean matches(CraftingInput input, Level level) {
        return super.matches(input, level) && Formula.grown(input);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> left = super.getRemainingItems(input);
        for (int at = 0; at < input.size(); at++) {
            ItemStack ingredient = input.getItem(at);
            if (ingredient.getItem() instanceof BlockItem block
                    && block.getBlock() instanceof CellaBlock) {
                // Back where it was, contents and all: it never went anywhere.
                left.set(at, ingredient.copy());
            }
        }
        return left;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return CellaRegistry.SPAWNING.get();
    }

    /**
     * The same five fields a shaped recipe has, read back off this class.
     *
     * <p>Written out rather than wrapped round the vanilla codec because that one makes a
     * {@code ShapedRecipe} and there is no way to take one apart again — {@code result},
     * {@code group} and {@code category} are not readable from outside its package.
     */
    public static class Serializer implements RecipeSerializer<Spawning> {
        private static final MapCodec<Spawning> CODEC = RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                                Codec.STRING.optionalFieldOf("group", "")
                                        .forGetter(Recipe::getGroup),
                                CraftingBookCategory.CODEC
                                        .fieldOf("category")
                                        .orElse(CraftingBookCategory.MISC)
                                        .forGetter(Spawning::category),
                                ShapedRecipePattern.MAP_CODEC
                                        .forGetter(recipe -> recipe.pattern),
                                ItemStack.STRICT_CODEC.fieldOf("result")
                                        .forGetter(Spawning::made))
                        .apply(instance, Spawning::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, Spawning> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, Recipe::getGroup,
                        CraftingBookCategory.STREAM_CODEC, Spawning::category,
                        ShapedRecipePattern.STREAM_CODEC, recipe -> recipe.pattern,
                        ItemStack.STREAM_CODEC, Spawning::made,
                        Spawning::new);

        @Override
        public MapCodec<Spawning> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, Spawning> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
