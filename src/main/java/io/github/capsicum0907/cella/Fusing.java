package io.github.capsicum0907.cella;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;

public class Fusing extends ShapedRecipe {
    private final ItemStack made;

    public Fusing(String group, CraftingBookCategory category, ShapedRecipePattern pattern,
            ItemStack result) {
        super(group, category, pattern, result);
        this.made = result;
    }

    public ItemStack made() {
        return made;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return super.matches(input, level) && Formula.grown(input) && used(input) <= room(made);
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack result = super.assemble(input, registries);
        List<UUID> chests = new ArrayList<>();
        for (int at = 0; at < input.size(); at++) {
            Held held = input.getItem(at).get(CellaRegistry.KEPT.get());
            if (held != null) {
                chests.addAll(held.chests());
            }
        }
        if (!chests.isEmpty()) {
            result.set(CellaRegistry.KEPT.get(), new Held(List.copyOf(chests), used(input),
                    room(result), 0, growth(result)));
        }
        return result;
    }

    private static int growth(ItemStack stack) {
        return kindOf(stack).map(Kind::growth).orElse(0);
    }

    private static int used(CraftingInput input) {
        int used = 0;
        for (int at = 0; at < input.size(); at++) {
            Held held = input.getItem(at).get(CellaRegistry.KEPT.get());
            if (held != null) {
                used += held.used();
            }
        }
        return used;
    }

    private static int room(ItemStack stack) {
        return kindOf(stack).map(Kind::slots).orElse(0);
    }

    private static java.util.Optional<Kind> kindOf(ItemStack stack) {
        return stack.getItem() instanceof BlockItem block
                && block.getBlock() instanceof CellaBlock chest
                ? java.util.Optional.of(chest.kind())
                : java.util.Optional.empty();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return CellaRegistry.FUSING.get();
    }

    public static class Serializer implements RecipeSerializer<Fusing> {
        private static final MapCodec<Fusing> CODEC = RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                                Codec.STRING.optionalFieldOf("group", "")
                                        .forGetter(Recipe::getGroup),
                                CraftingBookCategory.CODEC
                                        .fieldOf("category")
                                        .orElse(CraftingBookCategory.MISC)
                                        .forGetter(Fusing::category),
                                ShapedRecipePattern.MAP_CODEC
                                        .forGetter(recipe -> recipe.pattern),
                                ItemStack.STRICT_CODEC.fieldOf("result")
                                        .forGetter(Fusing::made))
                        .apply(instance, Fusing::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, Fusing> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, Recipe::getGroup,
                        CraftingBookCategory.STREAM_CODEC, Fusing::category,
                        ShapedRecipePattern.STREAM_CODEC, recipe -> recipe.pattern,
                        ItemStack.STREAM_CODEC, Fusing::made,
                        Fusing::new);

        @Override
        public MapCodec<Fusing> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, Fusing> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
