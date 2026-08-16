package io.github.capsicum0907.cella;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registration. One block, its item, its block entity, and the screen's menu. */
public final class CellaRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Cella.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Cella.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Cella.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Cella.MODID);

    /**
     * Wood and iron, and no {@code requiresCorrectToolForDrops}: what is inside falls
     * out when it is broken either way, so refusing to drop the block would only cost
     * the block.
     */
    public static final DeferredBlock<CellaBlock> BLOCK = BLOCKS.register("cella",
            () -> new CellaBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)));

    public static final DeferredItem<BlockItem> ITEM = ITEMS.register("cella",
            () -> new BlockItem(BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CellaBlockEntity>>
            BLOCK_ENTITY = BLOCK_ENTITIES.register("cella",
                    () -> BlockEntityType.Builder.of(CellaBlockEntity::new, BLOCK.get()).build(null));

    /**
     * The client builds its own menu from what is in the packet: where the chest is, and
     * <b>how many slots it has</b>. The second is not derivable on the client — a chest
     * keeps the size it was built with, and the client's copy of the block entity was
     * made at whatever the config says now. See {@link CellaMenu#at}.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<CellaMenu>> MENU =
            MENUS.register("cella", () -> IMenuTypeExtension.create(
                    (id, inventory, buffer) -> CellaMenu.at(id, inventory,
                            buffer.readBlockPos(), buffer.readVarInt())));

    private CellaRegistry() {
    }
}
