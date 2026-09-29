package io.github.capsicum0907.cella;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CellaBlock extends BaseEntityBlock {
    public static final MapCodec<CellaBlock> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                            Codec.STRING.fieldOf("kind")
                                    .forGetter(block -> block.kind.id()),
                            propertiesCodec())
                    .apply(instance, (id, properties) -> new CellaBlock(Kind.valueOf(
                            id.toUpperCase(java.util.Locale.ROOT)), properties)));

    private final Kind kind;

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 14.0, 15.0);

    private final VoxelShape shape;

    private static VoxelShape shaped(float scale) {
        if (scale == 1.0F) {
            return SHAPE;
        }
        double half = 7.0 * scale;
        return Block.box(8.0 - half, 0.0, 8.0 - half, 8.0 + half, 14.0 * scale, 8.0 + half);
    }

    public CellaBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        this.shape = shaped(kind.scale());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return shape;
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
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.NETHER_STAR) || kind.becomes().isEmpty() || kind.ripens()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof CellaBlockEntity chest)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (chest.lit()) {
            return refused(player, "message.cella.lit");
        }

        if (level.dimension() != Level.END) {
            return refused(player, "message.cella.elsewhere");
        }
        if (chest.grown() < 1.0F) {
            return refused(player, "message.cella.notyet");
        }
        if (!chest.light()) {
            return refused(player, "message.cella.notyet");
        }
        stack.consume(1, player);
        level.playSound(null, pos, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.6F, 1.6F);
        player.displayClientMessage(Component.translatable("message.cella.lighting"), true);
        return ItemInteractionResult.CONSUME;
    }

    private static ItemInteractionResult refused(Player player, String why) {
        player.displayClientMessage(Component.translatable(why), true);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof CellaBlockEntity chest && player.isShiftKeyDown()) {
            return fed(chest, player, level, pos);
        }
        if (level.getBlockEntity(pos) instanceof CellaBlockEntity chest
                && player instanceof ServerPlayer server) {
            Room room = Room.of(player);
            int size = chest.contents().getSlots();
            int rows = room.rowsFor(kind);
            int columns = room.columnsFor(kind);
            server.openMenu(new SimpleMenuProvider(
                    (id, inventory, who) -> CellaMenu.at(id, inventory, pos, size, rows, columns),
                    chest.getDisplayName()),
                    buffer -> {
                        buffer.writeBlockPos(pos);
                        buffer.writeVarInt(size);
                        buffer.writeVarInt(rows);
                        buffer.writeVarInt(columns);
                    });
            chest.opened(player);
        }
        return InteractionResult.CONSUME;
    }

    private InteractionResult fed(CellaBlockEntity chest, Player player, Level level,
            BlockPos pos) {
        if (!kind.grows()) {
            return InteractionResult.PASS;
        }
        int taken = chest.absorb(player);

        if (taken > 0 && kind.ripens() && level instanceof net.minecraft.server.level.ServerLevel
                server && chest.ripen(server)) {
            player.displayClientMessage(Component.translatable("message.cella.ripened",
                    CellaRegistry.block(kind.becomes().orElseThrow()).get().getName()), true);
            return InteractionResult.CONSUME;
        }
        if (taken > 0) {
            boolean done = chest.grown() >= 1.0F;
            level.playSound(null, pos,
                    done ? SoundEvents.PLAYER_LEVELUP : SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.BLOCKS, done ? 1.0F : 0.6F,
                    0.8F + level.getRandom().nextFloat() * 0.2F);
            player.displayClientMessage(done
                    ? Component.translatable("message.cella.full")
                    : Component.translatable("message.cella.fed",
                            Math.round(chest.grown() * 100.0F)), true);
        } else {
            player.displayClientMessage(Component.translatable(
                    chest.grown() >= 1.0F ? "message.cella.grown" : "message.cella.nothing"), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement,
            boolean moved) {
        if (!state.is(replacement.getBlock())
                && level.getBlockEntity(pos) instanceof CellaBlockEntity chest) {
            if (kind.keeps()) {
                chest.handOver(level, pos);
            } else {
                chest.spill(level, pos);
            }
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, replacement, moved);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
                instanceof CellaBlockEntity chest && chest.given()) {
            return List.of();
        }
        return List.of(new ItemStack(this));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            @javax.annotation.Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        Held held = stack.get(CellaRegistry.KEPT.get());
        if (level.isClientSide || held == null
                || !(level.getBlockEntity(pos) instanceof CellaBlockEntity chest)) {
            return;
        }
        Kept.of(level).ifPresent(kept -> {
            List<Kept.Chest> filed = held.chests().stream()
                    .map(kept::take)
                    .flatMap(java.util.Optional::stream)
                    .toList();
            if (filed.isEmpty()) {
                return;
            }

            if (filed.size() == 1 && !held.fused()) {
                chest.restore(level.registryAccess(), filed.getFirst());
                return;
            }
            for (ItemStack over : chest.fuse(level.registryAccess(), filed, Plan.NONE)) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), over);
            }
        });
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, CellaRegistry.BLOCK_ENTITY.get(),
                        CellaBlockEntity::lidTick)
                : createTickerHelper(type, CellaRegistry.BLOCK_ENTITY.get(),
                        (l, p, s, chest) -> chest.serverTick());
    }

    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int value) {
        super.triggerEvent(state, level, pos, id, value);
        BlockEntity entity = level.getBlockEntity(pos);
        return entity != null && entity.triggerEvent(id, value);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof CellaBlockEntity chest)) {
            return 0;
        }
        net.neoforged.neoforge.items.IItemHandlerModifiable read = chest.outlet();
        int slots = read.getSlots();
        if (slots <= 0) {
            return 0;
        }
        float filled = 0.0F;
        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = read.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                filled += (float) stack.getCount() / Math.min(read.getSlotLimit(slot),
                        stack.getMaxStackSize());
            }
        }
        return net.minecraft.util.Mth.floor(filled / slots * 14.0F) + (filled > 0.0F ? 1 : 0);
    }
}
