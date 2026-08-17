package io.github.capsicum0907.cella.data;

import java.util.concurrent.CompletableFuture;

import io.github.capsicum0907.cella.Annihilation;
import io.github.capsicum0907.cella.Cella;
import io.github.capsicum0907.cella.CellaRegistry;
import io.github.capsicum0907.cella.Formula;
import io.github.capsicum0907.cella.Fusing;
import io.github.capsicum0907.cella.Kind;
import io.github.capsicum0907.cella.Spawning;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.tags.BlockTags;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ModelBuilder;
import net.neoforged.neoforge.client.model.generators.ModelFile;
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

        // The model provider refuses a texture it cannot find, and these are made by the
        // provider two lines further down - in this same run, and not yet. Saying so is
        // what stops it stopping the build: "does not exist in any known resource pack"
        // is true and beside the point.
        ExistingFileHelper helper = event.getExistingFileHelper();
        ExistingFileHelper.ResourceType texture = new ExistingFileHelper.ResourceType(
                PackType.CLIENT_RESOURCES, ".png", "textures");
        for (Kind kind : Kind.values()) {
            helper.trackGenerated(
                    ResourceLocation.fromNamespaceAndPath(Cella.MODID, "block/" + kind.id()),
                    texture);
        }

        generator.addProvider(event.includeClient(), new Models(output, helper));
        generator.addProvider(event.includeClient(), new Language(output));
        generator.addProvider(event.includeServer(), new Recipes(output, event.getLookupProvider()));
        generator.addProvider(event.includeServer(), new TestStructures(output));
        generator.addProvider(event.includeClient(), new KindTextures(output));
        generator.addProvider(event.includeClient(), new ChestAtlas(output));
        generator.addProvider(event.includeServer(),
                new Tags(output, event.getLookupProvider(), event.getExistingFileHelper()));

        // The damage type is datapack contents, and its tags name it - so the tag provider
        // has to look it up in a world that already has it. getRegistryProvider() is that
        // world; handing it event.getLookupProvider() instead would be asking about a type
        // that has not been written yet.
        DatapackBuiltinEntriesProvider damage = new DatapackBuiltinEntriesProvider(output,
                event.getLookupProvider(),
                new RegistrySetBuilder().add(Registries.DAMAGE_TYPE,
                        context -> context.register(Annihilation.KEY, Annihilation.TYPE)),
                java.util.Set.of(Cella.MODID));
        generator.addProvider(event.includeServer(), damage);
        generator.addProvider(event.includeServer(),
                new Bypasses(output, damage.getRegistryProvider(), event.getExistingFileHelper()));
    }

    /**
     * Everything that would otherwise let something live through the wave, turned off.
     *
     * <p>The list is {@link Annihilation#BYPASSES} and is not repeated here: the type and
     * the tags saying what it ignores are one decision, and two copies of a decision are
     * two things to keep in step.
     */
    private static class Bypasses extends net.minecraft.data.tags.TagsProvider<
            net.minecraft.world.damagesource.DamageType> {
        Bypasses(PackOutput output, CompletableFuture<HolderLookup.Provider> registries,
                ExistingFileHelper existingFileHelper) {
            super(output, Registries.DAMAGE_TYPE, registries, Cella.MODID, existingFileHelper);
        }

        @Override
        protected void addTags(HolderLookup.Provider registries) {
            for (net.minecraft.tags.TagKey<net.minecraft.world.damagesource.DamageType> bypass
                    : Annihilation.BYPASSES) {
                tag(bypass).add(Annihilation.KEY);
            }
        }
    }

    /** An axe, because it is a wooden box. */
    private static class Tags extends BlockTagsProvider {
        Tags(PackOutput output, CompletableFuture<HolderLookup.Provider> registries,
                ExistingFileHelper existingFileHelper) {
            super(output, registries, Cella.MODID, existingFileHelper);
        }

        @Override
        protected void addTags(HolderLookup.Provider registries) {
            for (Kind kind : Kind.values()) {
                tag(BlockTags.MINEABLE_WITH_AXE).add(CellaRegistry.block(kind).get());
            }
        }
    }

    private static class Models extends BlockStateProvider {
        Models(PackOutput output, ExistingFileHelper existingFileHelper) {
            super(output, Cella.MODID, existingFileHelper);
        }

        /**
         * The block's model has no geometry at all - only a particle, for the dust when
         * it breaks and the crack overlay while it is being mined. What is seen is
         * {@code CellaRenderer}. Leaving a cube here would draw one inside the chest.
         *
         * <p>{@code horizontalBlock} makes the four turned variants, so the state matches
         * the way the renderer reads FACING.
         *
         * <p>The item is an ordinary model, built to the same proportions the renderer
         * uses: a body and a lid with the join showing. An item cannot be drawn by a
         * block entity renderer without a whole other client hook, and it does not need
         * to be - nobody turns a chest over in their hand.
         */
        /**
         * The block's model has no geometry at all - only a particle, for the dust when
         * it breaks and the crack overlay while it is being mined. What is seen is
         * {@code CellaRenderer}. Leaving a cube here would draw one inside the chest.
         *
         * <p>Per kind, and named per kind: one builder reused under one name would leave
         * every chest looking like whichever was generated last.
         */
        @Override
        protected void registerStatesAndModels() {
            for (Kind kind : Kind.values()) {
                String name = kind.id();
                horizontalBlock(CellaRegistry.block(kind).get(),
                        models().getBuilder(name).texture("particle", modLoc("block/" + name)));

                // block/block for the parent, which carries the display transforms a
                // block is held and dropped with. item/generated is for a flat sprite and
                // would lay this on its side in the hand.
                itemModels().getBuilder(name)
                        .parent(new ModelFile.UncheckedModelFile("block/block"))
                        .texture("all", modLoc("block/" + name))
                        .texture("particle", modLoc("block/" + name))
                        .element().from(1, 0, 1).to(15, 10, 15)
                                .allFaces((face, builder) -> whole(builder)).end()
                        .element().from(1, 10, 1).to(15, 14, 15)
                                .allFaces((face, builder) -> whole(builder)).end()
                        .element().from(7, 7, 0).to(9, 11, 1)
                                .allFaces((face, builder) -> whole(builder)).end();
            }
        }

        /**
         * The whole texture on the face, edge included.
         *
         * <p>Without this the coordinates are worked out from where the element is, and
         * an element from one to fifteen samples one to fifteen - which crops off exactly
         * the near-black ring the texture is drawn with. The block had its edges and the
         * thing in your hand did not, which is a strange thing to notice and an obvious
         * one once noticed.
         */
        private static void whole(ModelBuilder<?>.ElementBuilder.FaceBuilder builder) {
            builder.texture("#all").uvs(0, 0, 16, 16);
        }
    }

    private static class Language extends LanguageProvider {
        Language(PackOutput output) {
            super(output, Cella.MODID, "en_us");
        }

        @Override
        protected void addTranslations() {
            for (Kind kind : Kind.values()) {
                add(CellaRegistry.block(kind).get(), kind.displayName());
            }
            add("itemGroup." + Cella.MODID, "Cella");
            add("tooltip.cella.filled", "%s of %s slots used (%s%%)");
            add("gui.cella.sort", "Sort every page");
            add("gui.cella.stow", "Put in what this chest already keeps");
            add("gui.cella.stow.shift", "Shift: your whole inventory");
            add("gui.cella.take", "Take more of what you are carrying");
            add("gui.cella.take.shift", "Shift: as much as will fit");
            add("gui.cella.prev", "Previous page");
            add("gui.cella.next", "Next page");

            // Experience. A percent everywhere rather than points: a player knows how many
            // levels they handed over and has no feel at all for what those were worth.
            add("container.cella.grown", "%s (%s%%)");
            add("tooltip.cella.grown", "%s%% grown");
            add("message.cella.fed", "Absorbed. %s%% grown.");
            add("message.cella.full", "Absorbed. It has taken in all it can use.");
            add("message.cella.grown", "It has already taken in all it can use.");
            add("message.cella.nothing", "You have no experience to give it.");

            // Ending itself. Three refusals rather than one, because there are three
            // different things to go and do about it.
            // ⚠ No article before the name. Which one it wants depends on the word that
            // lands there - "a Perfect Cella" against "an Imperfect Cella" - and a format
            // string cannot choose. Any message that inserts a name has this in it, so the
            // rule is to write around the article rather than guess at one.
            add("message.cella.ripened", "It has eaten enough. It is now %s.");
            add("death.attack." + Annihilation.MESSAGE, "%1$s was annihilated by Cella");
            add("message.cella.lighting", "It begins to shake.");
            add("message.cella.lit", "It has already begun.");
            add("message.cella.notyet", "It has not taken in enough to survive that.");
            add("message.cella.elsewhere", "Not here. Somewhere the world can be spared.");

            // /cella kept — see KeptCommand. Three answers about a form rather than two,
            // because "names one this version does not have" is not "names none".
            add("commands.cella.kept.none", "No chest contents are kept.");
            // ⚠ Cella takes no article and no plural. It is a name rather than a kind of
            // box - the chest is the creature - so it is "annihilated by Cella" and never
            // "a Cella", the way you would not write "a Steve". That is narrower than it
            // sounds: counts of chests and hours below are ordinary English and still
            // inflect. What is exempt is the name.
            //
            // Two keys rather than an "s" added in Java: which words change with a count
            // is a fact about the language, so it belongs on this side of the file.
            add("commands.cella.kept.header",
                    "%s chests kept, %s slots spoken for between them:");
            add("commands.cella.kept.header.one", "One chest kept, %s slots spoken for:");
            add("commands.cella.kept.row", "%s - %s of %s slots, %s");
            add("commands.cella.kept.row.claimed", "%s - %s of %s slots, %s, already given out");
            add("commands.cella.kept.again",
                    "It had been given out %s times already. Only the first one placed gets "
                            + "the contents; the others go down empty, and their tooltips "
                            + "will not say so.");
            add("commands.cella.kept.unformed", "form unrecorded");
            add("commands.cella.kept.foreign", "%s, which this version does not have");
            add("commands.cella.kept.age", "filed %s ago");
            add("commands.cella.kept.undated", "filed before that was recorded");
            // ⚠ Abbreviated, and not for brevity: "1 hours" is wrong and "%s hour(s)" is
            // worse, and the days line carries two numbers either of which can be one, so
            // singular keys would come to four strings for one duration. A unit that does
            // not inflect has none of that. Same rule as the article in message.cella.ripened.
            add("commands.cella.kept.days", "%sd %sh");
            add("commands.cella.kept.hours", "%sh");
            add("commands.cella.kept.recent", "under an hour");
            add("commands.cella.kept.more", "...and %s more, not shown.");
            add("commands.cella.kept.click", "Click to write a give command for %s");
            add("commands.cella.kept.gave",
                    "Gave you %s, naming %s of %s slots. The contents stay kept until it "
                            + "is placed, and come back once.");
            add("commands.cella.kept.missing", "Nothing is kept under %s.");
            add("commands.cella.kept.noform", "There is no form called %s.");
            add("commands.cella.kept.forgot.claimed",
                    "A name for it had been given out %s times. Those chests go down empty "
                            + "now, and their tooltips will not say so.");
            add("commands.cella.kept.forgot",
                    "Destroyed the contents kept as %s: %s of %s slots. That cannot be "
                            + "undone.");
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

        /**
         * Whatever each kind says it is made of, and nothing where one says nothing.
         *
         * <p>The shapes have no two alike - nine animals, eight of the last form round a
         * gold block, four of the last and four nether stars round a dragon egg - so this
         * walks the pattern rather than knowing any of them.
         */
        /**
         * Swaps the shaped recipe for the kind of recipe it actually is.
         *
         * <p>{@code ShapedRecipeBuilder} only ever makes a plain {@code ShapedRecipe}, so
         * this catches it on the way out and rebuilds it as a {@link Spawning} or a
         * {@link Fusing} - a recipe that eats a Cella carries its contents, unless it is
         * the one that spawns rather than fuses. The
         * result is put together here rather than read off the recipe, because a shaped
         * recipe will not tell anyone outside its package what it makes without being
         * handed the registries.
         */
        private RecipeOutput rebuilt(RecipeOutput output, Kind kind, Formula formula) {
            if (!formula.spawns() && !formula.fuses()) {
                return output;
            }
            return new RecipeOutput() {
                @Override
                public Advancement.Builder advancement() {
                    return output.advancement();
                }

                @Override
                public void accept(ResourceLocation id, Recipe<?> recipe,
                        AdvancementHolder advancement, ICondition... conditions) {
                    Recipe<?> given = recipe;
                    if (recipe instanceof ShapedRecipe shaped) {
                        ItemStack result = new ItemStack(CellaRegistry.block(kind).get(),
                                formula.count());
                        given = formula.spawns()
                                ? new Spawning(shaped.getGroup(), shaped.category(),
                                        shaped.pattern, result)
                                : new Fusing(shaped.getGroup(), shaped.category(),
                                        shaped.pattern, result);
                    }
                    output.accept(id, given, advancement, conditions);
                }
            };
        }

        @Override
        protected void buildRecipes(RecipeOutput output) {
            for (Kind kind : Kind.values()) {
                kind.formula().ifPresent(formula -> {
                    ShapedRecipeBuilder shaped = ShapedRecipeBuilder.shaped(
                            RecipeCategory.DECORATIONS,
                            CellaRegistry.block(kind).get(), formula.count());
                    formula.pattern().forEach(shaped::pattern);
                    formula.of().forEach((letter, what) -> shaped.define(letter, what.get()));

                    // Unlocked by the first thing it asks for, which for everything past
                    // the first form is the form before it.
                    ItemLike first = formula.of().values().iterator().next().get();
                    shaped.unlockedBy("has_" + BuiltInRegistries.ITEM.getKey(first.asItem())
                            .getPath(), has(first));
                    shaped.save(rebuilt(output, kind, formula));
                });
            }
        }
    }
}
