package io.github.capsicum0907.cella;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
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

/**
 * Registration, one of everything per {@link Kind}.
 *
 * <p><b>One block entity type over all of them, and one menu.</b> A type per kind would
 * buy nothing: the block entity behaves the same whatever it is in, and asks the block
 * it sits in how big it should be. Keeping one also means the block's ticker keeps
 * working without a thought, because a ticker is matched on the type.
 */
public final class CellaRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Cella.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Cella.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Cella.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Cella.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPES =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Cella.MODID);

    /** The recipe that gives its parent back; see {@link Spawning}. */
    public static final DeferredHolder<RecipeSerializer<?>, Spawning.Serializer> SPAWNING =
            RECIPES.register("spawning", Spawning.Serializer::new);

    private static final Map<Kind, DeferredBlock<CellaBlock>> BLOCK = new EnumMap<>(Kind.class);
    private static final Map<Kind, DeferredItem<BlockItem>> ITEM = new EnumMap<>(Kind.class);

    static {
        for (Kind kind : Kind.values()) {
            BLOCK.put(kind, BLOCKS.register(kind.id(), () -> new CellaBlock(kind,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOD)
                            .strength(2.5F)
                            .sound(SoundType.WOOD))));
            ITEM.put(kind, ITEMS.register(kind.id(),
                    () -> new BlockItem(BLOCK.get(kind).get(), new Item.Properties())));
        }
    }

    /**
     * One type, told about every block it may sit in.
     *
     * <p>{@code Builder.of} checks each block against a data fixer type it has no entry
     * for and grumbles once per block in the log. That is noise, not a fault.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CellaBlockEntity>>
            BLOCK_ENTITY = BLOCK_ENTITIES.register("cella",
                    () -> BlockEntityType.Builder.of(CellaBlockEntity::new, blocks()).build(null));

    /**
     * The client builds its own menu from what is in the packet: where the chest is, how
     * many slots it has, and how tall a page is. None of the three is derivable on the
     * client — a chest keeps the size it was built with, and the client's copy of the
     * block entity was made at whatever the config says now.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<CellaMenu>> MENU =
            MENUS.register("cella", () -> IMenuTypeExtension.create(
                    (id, inventory, buffer) -> CellaMenu.at(id, inventory,
                            buffer.readBlockPos(), buffer.readVarInt(),
                            buffer.readVarInt(), buffer.readVarInt())));

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
