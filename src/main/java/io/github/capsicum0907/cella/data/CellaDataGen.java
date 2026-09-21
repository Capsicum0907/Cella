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
import net.minecraft.world.item.crafting.Ingredient;
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

@EventBusSubscriber(modid = Cella.MODID, value = { Dist.CLIENT, Dist.DEDICATED_SERVER })
public final class CellaDataGen {
    private CellaDataGen() {
    }

    @SubscribeEvent
    public static void gather(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();

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

        DatapackBuiltinEntriesProvider damage = new DatapackBuiltinEntriesProvider(output,
                event.getLookupProvider(),
                new RegistrySetBuilder().add(Registries.DAMAGE_TYPE,
                        context -> context.register(Annihilation.KEY, Annihilation.TYPE)),
                java.util.Set.of(Cella.MODID));
        generator.addProvider(event.includeServer(), damage);
        generator.addProvider(event.includeServer(),
                new Bypasses(output, damage.getRegistryProvider(), event.getExistingFileHelper()));
    }

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

    private static class Tags extends BlockTagsProvider {
        Tags(PackOutput output, CompletableFuture<HolderLookup.Provider> registries,
                ExistingFileHelper existingFileHelper) {
            super(output, registries, Cella.MODID, existingFileHelper);
        }

        @Override
        protected void addTags(HolderLookup.Provider registries) {
            for (Kind kind : Kind.values()) {
                net.minecraft.world.level.block.Block block = CellaRegistry.block(kind).get();
                tag(kind.trait().tool()).add(block);
                kind.trait().level().ifPresent(level -> tag(level).add(block));
                if (kind.trait().witherproof()) {
                    tag(BlockTags.WITHER_IMMUNE).add(block);
                }
            }
        }
    }

    private static class Models extends BlockStateProvider {
        Models(PackOutput output, ExistingFileHelper existingFileHelper) {
            super(output, Cella.MODID, existingFileHelper);
        }

        @Override
        protected void registerStatesAndModels() {
            for (Kind kind : Kind.values()) {
                String name = kind.id();
                horizontalBlock(CellaRegistry.block(kind).get(),
                        models().getBuilder(name).texture("particle", modLoc("block/" + name)));

                float scale = kind.scale();
                itemModels().getBuilder(name)
                        .parent(new ModelFile.UncheckedModelFile("block/block"))
                        .texture("all", modLoc("block/" + name))
                        .texture("particle", modLoc("block/" + name))
                        .element().from(across(1, scale), up(0, scale), across(1, scale))
                                .to(across(15, scale), up(10, scale), across(15, scale))
                                .allFaces((face, builder) -> whole(builder)).end()
                        .element().from(across(1, scale), up(10, scale), across(1, scale))
                                .to(across(15, scale), up(14, scale), across(15, scale))
                                .allFaces((face, builder) -> whole(builder)).end()
                        .element().from(across(7, scale), up(7, scale), across(0, scale))
                                .to(across(9, scale), up(11, scale), across(1, scale))
                                .allFaces((face, builder) -> whole(builder)).end();
            }
        }

        private static float across(float at, float scale) {
            return 8.0F + (at - 8.0F) * scale;
        }

        private static float up(float at, float scale) {
            return at * scale;
        }

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
            add("gui.cella.sort", "order: %s");
            add("gui.cella.sort.registry", "item id");
            add("gui.cella.sort.display", "name");
            add("gui.cella.sort.source", "mod");
            add("gui.cella.stow", "Put in what this chest already keeps");
            add("gui.cella.stow.shift", "Shift: your whole inventory");
            add("gui.cella.take", "Take more of what you are carrying");
            add("gui.cella.take.shift", "Shift: as much as will fit");
            add("gui.cella.find", "Search this chest");
            add("gui.cella.find.hint", "Search");
            add("gui.cella.find.none", "Nothing here answers to that");
            add("gui.cella.partition.none", "Pick one to see what is in it");
            add("gui.cella.partition.share", "%s%% of chest");
            add("gui.cella.partition.edit", "Edit partitions");
            add("gui.cella.edit.name", "Name");
            add("gui.cella.edit.share", "Share");
            add("gui.cella.edit.size", "LC");
            add("gui.cella.edit.used", "Used");
            add("gui.cella.edit.full", "Empty it before deleting it");
            add("gui.cella.edit.pack", "Give back the room it is not using");
            add("gui.cella.partition.unnamed", "Unnamed");
            add("gui.cella.partition.everything", "Everything");
            add("gui.cella.into.none", "Pick where to store");
            add("gui.cella.into.pick", "Store into %s");
            add("gui.cella.edit.nofit", "%s LC does not fit there");
            add("gui.cella.edit.shrink", "It holds more than %s LC would");
            add("gui.cella.edit.carved", "%s / %s LC carved, %s free");
            add("gui.cella.prev", "Previous page");
            add("gui.cella.next", "Next page");

            add("container.cella.grown", "%s (%s%%)");
            add("tooltip.cella.grown", "%s%% grown");
            add("message.cella.fed", "Absorbed. %s%% grown.");
            add("message.cella.full", "Absorbed. It has taken in all it can use.");
            add("message.cella.grown", "It has already taken in all it can use.");
            add("message.cella.nothing", "You have no experience to give it.");

            add("message.cella.ripened", "It has eaten enough. It is now %s.");
            add("death.attack." + Annihilation.MESSAGE, "%1$s was annihilated by Cella");
            add("message.cella.lighting", "It begins to shake.");
            add("message.cella.lit", "It has already begun.");
            add("message.cella.notyet", "It has not taken in enough to survive that.");
            add("message.cella.elsewhere", "Not here. Somewhere the world can be spared.");

            add("commands.cella.kept.none", "No chest contents are kept.");

            add("commands.cella.kept.header",
                    "%s chests kept, %s slots spoken for between them:");
            add("commands.cella.kept.header.one", "One chest kept, %s slots spoken for:");
            add("commands.cella.kept.row", "%s - %s of %s slots, %s");
            add("commands.cella.kept.row.claimed", "%s - %s of %s slots, %s, already given out");

            add("commands.cella.kept.again",
                    "There are already %s names for it out in the world. Only the first one "
                            + "placed gets the contents; the others go down empty, and "
                            + "their tooltips will not say so.");
            add("commands.cella.kept.unformed", "form unrecorded");
            add("commands.cella.kept.foreign", "%s, which this version does not have");
            add("commands.cella.kept.age", "filed %s ago");
            add("commands.cella.kept.undated", "filed before that was recorded");

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
                    "%s names for it were out in the world. Those chests go down empty now, "
                            + "and their tooltips will not say so.");
            add("commands.cella.kept.forgot",
                    "Destroyed the contents kept as %s: %s of %s slots. That cannot be "
                            + "undone.");
        }
    }

    private static class Recipes extends RecipeProvider {
        Recipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries);
        }

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
                    formula.of().forEach((letter, any) -> shaped.define(letter,
                            Ingredient.of(any.stream().map(java.util.function.Supplier::get)
                                    .toArray(ItemLike[]::new))));

                    ItemLike first = formula.of().values().iterator().next().getFirst().get();
                    shaped.unlockedBy("has_" + BuiltInRegistries.ITEM.getKey(first.asItem())
                            .getPath(), has(first));
                    shaped.save(rebuilt(output, kind, formula));
                });
            }
        }
    }
}
