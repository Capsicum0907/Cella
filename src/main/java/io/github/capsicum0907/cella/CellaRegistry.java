package io.github.capsicum0907.cella;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CellaRegistry {
    private static BlockBehaviour.Properties particular(BlockBehaviour.Properties properties,
            Kind kind) {
        return kind.trait().particular() ? properties.requiresCorrectToolForDrops() : properties;
    }

    private static Item.Properties fireproof(Item.Properties properties, Kind kind) {
        return kind.trait().unbreakableAsAnItem() ? properties.fireResistant() : properties;
    }

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Cella.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Cella.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Cella.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Cella.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPES =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Cella.MODID);
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Cella.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Held>> KEPT =
            COMPONENTS.register("kept", () -> DataComponentType.<Held>builder()
                    .persistent(Held.CODEC)
                    .networkSynchronized(Held.STREAM_CODEC)
                    .build());

    public static final DeferredHolder<RecipeSerializer<?>, Spawning.Serializer> SPAWNING =
            RECIPES.register("spawning", Spawning.Serializer::new);

    public static final DeferredHolder<RecipeSerializer<?>, Fusing.Serializer> FUSING =
            RECIPES.register("fusing", Fusing.Serializer::new);

    private static final Map<Kind, DeferredBlock<CellaBlock>> BLOCK = new EnumMap<>(Kind.class);
    private static final Map<Kind, DeferredItem<BlockItem>> ITEM = new EnumMap<>(Kind.class);

    static {
        for (Kind kind : Kind.values()) {
            BLOCK.put(kind, BLOCKS.register(kind.id(), () -> new CellaBlock(kind,
                    particular(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOD)
                            .strength(2.5F, kind.trait().resistance())
                            .sound(SoundType.WOOD), kind))));
            ITEM.put(kind, ITEMS.register(kind.id(),
                    () -> new CellaItem(BLOCK.get(kind).get(), fireproof(new Item.Properties(),
                            kind))));
        }
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CellaBlockEntity>>
            BLOCK_ENTITY = BLOCK_ENTITIES.register("cella",
                    () -> BlockEntityType.Builder.of(CellaBlockEntity::new, blocks()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<CellaMenu>> MENU =
            MENUS.register("cella", () -> IMenuTypeExtension.create(
                    (id, inventory, buffer) -> CellaMenu.at(id, inventory,
                            buffer.readBlockPos(), buffer.readVarInt(),
                            buffer.readVarInt(), buffer.readVarInt())));

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Cella.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
            TABS.register("cella", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + Cella.MODID))
                    .icon(() -> new net.minecraft.world.item.ItemStack(item(Kind.PERFECT).get()))
                    .displayItems((parameters, output) -> {
                        for (Kind kind : Kind.values()) {
                            output.accept(item(kind).get());
                        }
                    })
                    .build());

    private CellaRegistry() {
    }

    public static DeferredBlock<CellaBlock> block(Kind kind) {
        return BLOCK.get(kind);
    }

    public static DeferredItem<BlockItem> item(Kind kind) {
        return ITEM.get(kind);
    }

    private static Block[] blocks() {
        return BLOCK.values().stream().map(DeferredBlock::get).toArray(Block[]::new);
    }
}
