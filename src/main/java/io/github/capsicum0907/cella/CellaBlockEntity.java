package io.github.capsicum0907.cella;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestLidController;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The chest itself: one run of slots, however many pages that comes to.
 *
 * <p><b>It knows nothing about pages.</b> Paging is a property of looking, and lives in
 * {@link Window}. What is here is a flat handler and the two things that have to be
 * true of it — that it is saved, and that everyone who is not a screen sees all of it.
 */
public class CellaBlockEntity extends BlockEntity implements MenuProvider, LidBlockEntity {
    private static final String CONTENTS = "Contents";

    private final ItemStackHandler contents;

    /** How far the lid has swung, on the client. */
    private final ChestLidController lidController = new ChestLidController();

    /**
     * <b>How many people have it open, not whether anybody does.</b>
     *
     * <p>A flag is the obvious thing and it is wrong: two players open the chest, one
     * walks away, and the lid shuts in the other one's face. Counting is also what makes
     * the sound play once when the first arrives and once when the last leaves, rather
     * than on every screen.
     *
     * <p>{@code isOwnContainer} has to recognise <em>this</em> chest rather than any of
     * them, or a player standing in one with another open somewhere would be counted
     * twice. The contents are the identity: there is one handler per block entity.
     */
    private final ContainerOpenersCounter openers = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            said(level, pos, SoundEvents.CHEST_OPEN);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            said(level, pos, SoundEvents.CHEST_CLOSE);
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state,
                int was, int now) {
            // A block event is how the server tells everyone who can see the block; the
            // lid is drawn from it and nothing else.
            level.blockEvent(pos, state.getBlock(), 1, now);
        }

        @Override
        protected boolean isOwnContainer(Player player) {
            return player.containerMenu instanceof CellaMenu menu && menu.isFor(contents);
        }
    };

    public CellaBlockEntity(BlockPos pos, BlockState state) {
        super(CellaRegistry.BLOCK_ENTITY.get(), pos, state);
        this.contents = new ItemStackHandler(kindOf(state).slots()) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                // A comparator reads how full this is, so it has to hear about every
                // change - including one made on a page nobody has open.
                if (level != null && !level.isClientSide) {
                    level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
                }
            }
        };
    }

    public ItemStackHandler contents() {
        return contents;
    }

    /**
     * Which kind of chest this sits in.
     *
     * <p>Asked of the block rather than saved, because it cannot disagree that way: a
     * block entity is only ever in the block it was made for. The fallback is the first
     * kind, and is only reachable if one of these is somehow put somewhere else.
     */
    public static Kind kindOf(BlockState state) {
        return state.getBlock() instanceof CellaBlock chest ? chest.kind() : Kind.values()[0];
    }

    public Kind kind() {
        return kindOf(getBlockState());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(CONTENTS, contents.serializeNBT(registries));
    }

    /**
     * <p><b>The saved size wins over the configured one.</b>
     * {@code ItemStackHandler#deserializeNBT} calls {@code setSize} with the {@code Size}
     * it finds, so a chest built when the config said twelve pages comes back with
     * twelve pages even in a world whose config now says four. That is deliberate and it
     * is the only behaviour that is safe: the alternative is that editing a number in a
     * text file silently deletes what was in the pages past the new end. The number in
     * the config describes a chest being <em>made</em>.
     */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        contents.deserializeNBT(registries, tag.getCompound(CONTENTS));
    }

    /**
     * Everything inside, onto the floor.
     *
     * <p>Contents drop rather than riding on the item, which is the opposite of what a
     * heap does and is right for the opposite reason: a chest holds an amount a floor
     * can take. It also puts this mod entirely outside the family of duplication bugs
     * that come of keeping contents in a component — there is nothing on the item to
     * copy.
     */
    public void spill(Level level, BlockPos pos) {
        for (int slot = 0; slot < contents.getSlots(); slot++) {
            ItemStack stack = contents.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                contents.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    /**
     * Opening and closing, counted. Called by the block when a screen is asked for and
     * by the menu when one goes away.
     */
    public void opened(Player player) {
        if (level != null && !player.isSpectator()) {
            openers.incrementOpeners(player, level, getBlockPos(), getBlockState());
        }
    }

    public void closed(Player player) {
        if (level != null && !player.isSpectator()) {
            openers.decrementOpeners(player, level, getBlockPos(), getBlockState());
        }
    }

    /** How many have it open. Read by the test that this is a count and not a flag. */
    public int openers() {
        return openers.getOpenerCount();
    }

    /**
     * Asked every so often because a player can stop having it open without saying so —
     * dying, going through a portal, losing their connection. Without this the lid stays
     * up and the count never comes back down.
     */
    public void recheck() {
        if (level != null && !remove) {
            openers.recheckOpeners(level, getBlockPos(), getBlockState());
        }
    }

    /** The client's half: the lid swings towards where the block event said it should be. */
    public static void lidTick(Level level, BlockPos pos, BlockState state, CellaBlockEntity chest) {
        chest.lidController.tickLid();
    }

    @Override
    public boolean triggerEvent(int id, int value) {
        if (id == 1) {
            lidController.shouldBeOpen(value > 0);
            return true;
        }
        return super.triggerEvent(id, value);
    }

    @Override
    public float getOpenNess(float partial) {
        return lidController.getOpenness(partial);
    }

    private static void said(Level level, BlockPos pos, SoundEvent sound) {
        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound,
                SoundSource.BLOCKS, 0.5F, level.getRandom().nextFloat() * 0.1F + 0.9F);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return CellaMenu.at(id, inventory, getBlockPos(), contents.getSlots(),
                CellaConfig.rows(kind()), CellaConfig.columns(kind()));
    }
}
