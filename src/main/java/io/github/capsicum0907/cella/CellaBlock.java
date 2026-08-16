package io.github.capsicum0907.cella;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;

/** A chest with more than one page. */
public class CellaBlock extends BaseEntityBlock {
    public static final MapCodec<CellaBlock> CODEC = simpleCodec(CellaBlock::new);

    public CellaBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CellaBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof CellaBlockEntity chest
                && player instanceof ServerPlayer server) {
            server.openMenu(chest, buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.CONSUME;
    }

    /**
     * The contents fall out, the way a chest's do.
     *
     * <p>Deliberately not carried on the item. A chest holds an amount a floor can take,
     * and keeping contents in a component is the whole family of duplication bugs that
     * comes of one set of data being shared by a stack of items. There is nothing on
     * this item to copy.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement,
            boolean moved) {
        if (!state.is(replacement.getBlock())
                && level.getBlockEntity(pos) instanceof CellaBlockEntity chest) {
            chest.spill(level, pos);
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, replacement, moved);
    }

    /**
     * One block, and no loot table to disagree with this.
     *
     * <p>The contents are not here: they are already on the floor by the time anything
     * asks what the block dropped, because {@link #onRemove} put them there.
     */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(new ItemStack(this));
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /**
     * How full the whole chest is, not how full the page somebody has open is.
     *
     * <p>Read straight off the contents rather than through a container, because there
     * is no {@code Container} here to hand to the vanilla helper — the chest is an item
     * handler and nothing else.
     */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof CellaBlockEntity chest)) {
            return 0;
        }
        float filled = 0.0F;
        int slots = chest.contents().getSlots();
        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = chest.contents().getStackInSlot(slot);
            if (!stack.isEmpty()) {
                filled += (float) stack.getCount() / Math.min(chest.contents().getSlotLimit(slot),
                        stack.getMaxStackSize());
            }
        }
        return net.minecraft.util.Mth.floor(filled / slots * 14.0F) + (filled > 0.0F ? 1 : 0);
    }
}
