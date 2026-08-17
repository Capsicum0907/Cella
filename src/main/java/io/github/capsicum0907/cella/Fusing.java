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

/**
 * A recipe that carries the contents of what it ate into what it made.
 *
 * <p><b>A fusion keeps; a spawning does not.</b> Eight Imperfects become a Semi-Perfect
 * and everything in the eight is in the one afterwards. Cella Jr. is the exception and
 * the exception has a reason rather than a rule of its own: it is a {@link Spawning} —
 * seven come out and the parent stays standing — so there is no one chest for the
 * contents to go to, and nothing was consumed to need moving.
 *
 * <p><b>It fits, and that is a property of the ladder rather than luck.</b> A form holds
 * exactly what its recipe eats: eight Imperfects are 8 × 216 = 1,728 slots and a
 * Semi-Perfect is 1,728. The same is true at every step up to 221,184. That invariant is
 * why capacity is not a setting; see {@code CellaConfig}.
 *
 * <h2>Nothing is filed while a bench is being looked at</h2>
 *
 * <p>{@code assemble} runs whenever the ingredients sit on a bench, not when the result
 * is taken — laying eight chests out and changing your mind calls it repeatedly. So this
 * has <b>no side effects</b>: it reads the names off the ingredients, adds up how full
 * they were, and writes both onto the result. The contents are not touched, and no new
 * chest is filed anywhere.
 *
 * <p>The pouring happens when the new chest is <b>put down</b>, which is the first moment
 * anything needs to be in it. A name is spent when it is taken, so it cannot happen twice.
 */
public class Fusing extends ShapedRecipe {
    private final ItemStack made;

    public Fusing(String group, CraftingBookCategory category, ShapedRecipePattern pattern,
            ItemStack result) {
        super(group, category, pattern, result);
        this.made = result;
    }

    /** Kept for the codec: the superclass holds the result where nobody else can read it. */
    public ItemStack made() {
        return made;
    }

    /**
     * <b>It has to fit, and if it would not, this is not a recipe.</b>
     *
     * <p>Prevention rather than handling. The alternative is to make the chest anyway and
     * put the remainder somewhere, and there is nowhere: dropping it means thousands of
     * item entities on one block, which is not a mess to tidy but a world that may not
     * open again — and it would land on exactly the people who filled their chests.
     *
     * <p>With capacity fixed this cannot happen at all. It is checked because it could
     * once: a world may still hold chests built when the numbers were different, and
     * eight of those can be fuller than the thing they are being fused into.
     */
    @Override
    public boolean matches(CraftingInput input, Level level) {
        return super.matches(input, level) && used(input) <= room(made);
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
            // How full it will be is how full they were, in the room the new one has.
            result.set(CellaRegistry.KEPT.get(),
                    new Held(List.copyOf(chests), used(input), room(result)));
        }
        return result;
    }

    /** How many slots the ingredients have something in, off their own snapshots. */
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

    /** How many slots the thing being made will have. Nought if it is not one of ours. */
    private static int room(ItemStack stack) {
        return stack.getItem() instanceof BlockItem block
                && block.getBlock() instanceof CellaBlock chest
                ? chest.kind().slots()
                : 0;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return CellaRegistry.FUSING.get();
    }

    /** The same five fields a shaped recipe has, for the same reason as {@link Spawning}. */
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
