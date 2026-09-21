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

public class Spawning extends ShapedRecipe {
    private final ItemStack made;

    public Spawning(String group, CraftingBookCategory category, ShapedRecipePattern pattern,
            ItemStack result) {
        super(group, category, pattern, result);
        this.made = result;
    }

    public ItemStack made() {
        return made;
    }

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
                left.set(at, ingredient.copy());
            }
        }
        return left;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return CellaRegistry.SPAWNING.get();
    }

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
