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

/**
 * A chest with more than one page.
 *
 * <p>It is drawn the way a chest is drawn — by a block entity renderer, not by a model
 * — so it faces the way it was placed, stands fourteen sixteenths tall, and leaves a
 * gap round its sides. {@link #getRenderShape} says {@code ENTITYBLOCK_ANIMATED} and
 * the model this points at has nothing in it but a particle, or the cube would be drawn
 * inside the chest.
 */
public class CellaBlock extends BaseEntityBlock {
    /**
     * <b>Not simpleCodec.</b> That one rebuilds a block from its properties alone, and a
     * kind is not a property - it is which block this is. The codec has to carry it or a
     * world reloads every chest as the first kind.
     */
    public static final MapCodec<CellaBlock> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                            Codec.STRING.fieldOf("kind")
                                    .forGetter(block -> block.kind.id()),
                            propertiesCodec())
                    .apply(instance, (id, properties) -> new CellaBlock(Kind.valueOf(
                            id.toUpperCase(java.util.Locale.ROOT)), properties)));

    private final Kind kind;

    /** Which way it was put down, the same property a vanilla chest uses. */
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    /** A chest is not a full block: one sixteenth in on each side, and two short. */
    private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 14.0, 15.0);

    public CellaBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /** Which of them this is. Everything that differs between chests is asked of this. */
    public Kind kind() {
        return kind;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Facing the player who put it down, which is what every chest does. */
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
        return SHAPE;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CellaBlockEntity(pos, state);
    }

    /**
     * Drawn by {@code CellaRenderer}, not by a model. The model this block's state
     * points at carries a particle texture and no geometry, so what is seen is the
     * renderer's chest and nothing else.
     */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    /**
     * A nether star on a form that has taken in all it can use, in the End: it lights.
     *
     * <p><b>Neither a command nor a button on its screen.</b> A command would put the one
     * step of the ladder that is not arithmetic behind an operator, which would make it
     * something only a server owner could do. A button in the chest's own screen sits an
     * inch from the sort button, and this is the one action in the mod that cannot be
     * taken back.
     *
     * <p><b>A nether star has no path to being pressed by accident.</b> Flint and steel is
     * the game's own verb for setting something off, and was the obvious choice until the
     * obvious problem with it: it is cheap and it lives in a pocket, so a storage room full
     * of these would be a storage room one misclick from a crater. Nobody idly right-clicks
     * a chest holding a nether star. It also comes off a fight, which is what everything
     * else about this step is made of.
     *
     * <p><b>Every refusal says which refusal it is.</b> Not enough taken in, wrong world,
     * already going: three different things to do about it, so three different answers.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        // A form that ripens is never standing there full, so a star has nothing to do to
        // it: the star passes through and behaves like any other item on any other chest.
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
        // ⚠ The End, and the reason is not flavour. What follows removes a hundred blocks
        // in every direction, and there is exactly one place in this game where that is
        // somebody's problem and not everybody's.
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

    /** A refusal that says which one it is, and takes nothing for having said so. */
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
            // The kind's own shape, cut down to what this player's screen can show.
            // Somebody else standing at the same chest may be looking at a different
            // shape, and neither of them has to know: see Room.
            //
            // Worked out once, into three local variables, and both the menu and the
            // packet are built from those. It was written the other way first - the
            // packet from Room and the menu from the block entity's own createMenu,
            // which still read the config - and the two disagreed the moment a screen
            // was small enough to be cut down. The server then sent 228 slots to a menu
            // holding 148 and the client walked off the end of its own list. A number
            // that has to be the same in two places should be in one.
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

    /**
     * Fed: a level of the player's experience goes into the chest.
     *
     * <p><b>Sneaking with empty hands, which is the one gesture this block had spare.</b>
     * A plain right-click opens it and a right-click holding something is the game's own
     * way of saying "use what I am holding", so the remaining case is a deliberate,
     * empty-handed press — and it wants to be deliberate, because none of it comes back.
     *
     * <p><b>It always says what happened.</b> Nothing visible moves when a chest absorbs
     * experience: no slot changes and the screen is not open. A press that did nothing and
     * a press that took a level would look identical, so both of them are answered — one
     * of the few places a message is the honest interface rather than a lazy one.
     */
    private InteractionResult fed(CellaBlockEntity chest, Player player, Level level,
            BlockPos pos) {
        if (!kind.grows()) {
            return InteractionResult.PASS;
        }
        int taken = chest.absorb(player);
        // Grown up rather than merely fed. Asked after absorbing and before anything is
        // said about it, because ripening replaces the block entity - reading the old one
        // afterwards would report a percentage belonging to a chest that no longer exists.
        if (taken > 0 && kind.ripens() && level instanceof net.minecraft.server.level.ServerLevel
                server && chest.ripen(server)) {
            player.displayClientMessage(Component.translatable("message.cella.ripened",
                    CellaRegistry.block(kind.becomes().orElseThrow()).get().getName()), true);
            return InteractionResult.CONSUME;
        }
        if (taken > 0) {
            // Reaching the top is a state change and gets its own sound, because one press
            // can now be the whole of it: without a mark for it, the moment the chest
            // became capable of something else would pass in silence.
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
            // Which of the two it was, because the fixes are opposite: go and fight, or
            // stop feeding a chest that has finished growing.
            player.displayClientMessage(Component.translatable(
                    chest.grown() >= 1.0F ? "message.cella.grown" : "message.cella.nothing"), true);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * Broken: the contents either fall out or come with it.
     *
     * <p>Which one is {@link Kind#keeps}, and it is Laravel against everything else. The
     * larva spills, because 54 slots is a pile a player can pick up; the rest hand the
     * contents to {@link Kept} and drop an item that names them. Neither of them puts
     * the contents <em>on</em> the item — see that class for the three ceilings that stop
     * it and the family of bugs it avoids.
     *
     * <p>Done here rather than in the drops because here runs whatever removed the block.
     */
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

    /**
     * One block, and no loot table to disagree with this — unless one has been handed
     * over already.
     *
     * <p>A chest that kept its contents dropped itself in {@link #onRemove}, with the
     * name of what it kept on it. Dropping a second, empty one here would be a chest for
     * free. The block entity is the one that was standing here: the game holds on to it
     * across the removal so that loot can ask it things, and this is one of them.
     */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
                instanceof CellaBlockEntity chest && chest.given()) {
            return List.of();
        }
        return List.of(new ItemStack(this));
    }

    /**
     * Put down: if the item names a chest, that chest is what this becomes.
     *
     * <p>The name is spent in the taking, so a second item naming the same one puts down
     * an empty chest rather than a second copy of the contents. See {@link Kept#take}.
     */
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
            // One is that chest, put back as it was. Several are the contents of several,
            // which belong to no one arrangement - see CellaBlockEntity.
            if (filed.size() == 1 && !held.fused()) {
                chest.restore(level.registryAccess(), filed.getFirst());
                return;
            }
            for (ItemStack over : chest.pour(level.registryAccess(), filed)) {
                // Fusing will not let this happen. If a world has an item from before it
                // did, the leftovers are handed over rather than dropped: one item on the
                // floor is a thing to pick up, and a thousand is a world that stops
                // opening.
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), over);
            }
        });
    }

    /**
     * The client swings the lid; the server keeps the count honest.
     *
     * <p>The server side is a recheck rather than any work of its own: a player can stop
     * having a chest open without saying so - dying, stepping through a portal, losing
     * their connection - and without asking now and again the lid would stay up forever.
     */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, CellaRegistry.BLOCK_ENTITY.get(),
                        CellaBlockEntity::lidTick)
                : createTickerHelper(type, CellaRegistry.BLOCK_ENTITY.get(),
                        (l, p, s, chest) -> chest.serverTick());
    }

    /** A block event is how the server tells everyone watching that the lid moved. */
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
