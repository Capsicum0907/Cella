package io.github.capsicum0907.cella.data;

import java.util.concurrent.CompletableFuture;

import io.github.capsicum0907.cella.Cella;
import io.github.capsicum0907.cella.CellaRegistry;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.common.data.LanguageProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Everything under {@code src/generated/resources} comes from here, so nothing in
 * that directory is written by hand.
 *
 * <p>No loot table: {@link io.github.capsicum0907.cella.CellaBlock#getDrops} answers
 * that in code, and the contents are already on the floor by then.
 */
@EventBusSubscriber(modid = Cella.MODID, value = { Dist.CLIENT, Dist.DEDICATED_SERVER })
public final class CellaDataGen {
    private CellaDataGen() {
    }

    @SubscribeEvent
    public static void gather(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();

        generator.addProvider(event.includeClient(),
                new Models(output, event.getExistingFileHelper()));
        generator.addProvider(event.includeClient(), new Language(output));
        generator.addProvider(event.includeServer(), new Recipes(output, event.getLookupProvider()));
        generator.addProvider(event.includeServer(), new TestStructures(output));
        generator.addProvider(event.includeServer(),
                new Tags(output, event.getLookupProvider(), event.getExistingFileHelper()));
    }

    /** An axe, because it is a wooden box. */
    private static class Tags extends BlockTagsProvider {
        Tags(PackOutput output, CompletableFuture<HolderLookup.Provider> registries,
                ExistingFileHelper existingFileHelper) {
            super(output, registries, Cella.MODID, existingFileHelper);
        }

        @Override
        protected void addTags(HolderLookup.Provider registries) {
            tag(BlockTags.MINEABLE_WITH_AXE).add(CellaRegistry.BLOCK.get());
        }
    }

    private static class Models extends BlockStateProvider {
        Models(PackOutput output, ExistingFileHelper existingFileHelper) {
            super(output, Cella.MODID, existingFileHelper);
        }

        @Override
        protected void registerStatesAndModels() {
            String name = CellaRegistry.BLOCK.getId().getPath();
            simpleBlock(CellaRegistry.BLOCK.get(), models().cubeAll(name, modLoc("block/" + name)));
            itemModels().withExistingParent(name, modLoc("block/" + name));
        }
    }

    private static class Language extends LanguageProvider {
        Language(PackOutput output) {
            super(output, Cella.MODID, "en_us");
        }

        @Override
        protected void addTranslations() {
            add(CellaRegistry.BLOCK.get(), "Cella");
            add("gui.cella.sort", "Sort every page");
            add("gui.cella.matching", "Put in what this chest already keeps");
            add("gui.cella.stow", "Put your whole inventory in");
            add("gui.cella.taking", "Take more of what you are carrying");
            add("gui.cella.take", "Take as much as will fit");
            add("gui.cella.prev", "Previous page");
            add("gui.cella.next", "Next page");
        }
    }

    /**
     * Four chests around an iron core.
     *
     * <p>Priced as what it replaces rather than as what it is worth: several chests in
     * the space of one is the whole of it, so several chests is what it costs.
     */
    private static class Recipes extends RecipeProvider {
        Recipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries);
        }

        @Override
        protected void buildRecipes(RecipeOutput output) {
            ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, CellaRegistry.BLOCK.get())
                    .pattern("PCP")
                    .pattern("CIC")
                    .pattern("PCP")
                    .define('P', Items.IRON_NUGGET)
                    .define('C', Blocks.CHEST)
                    .define('I', Items.IRON_INGOT)
                    .unlockedBy("has_chest", has(Blocks.CHEST))
                    .save(output);
        }
    }
}
