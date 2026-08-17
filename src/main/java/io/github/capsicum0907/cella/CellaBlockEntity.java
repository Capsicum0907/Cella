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
 * {@link Window} — a chest is a flat run of slots and a screen is what has a page. So a
 * hopper, a comparator and the sort button all see the whole thing without asking, and
 * the two things that have to be true here are that it is saved and that it is one run.
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
                told();
            }
        };
    }

    public ItemStackHandler contents() {
        return contents;
    }

    /** Set while a whole-chest operation is running; see {@link #inOneGo}. */
    private boolean bulk;

    /**
     * Runs something that writes many slots, and tells the neighbours once at the end.
     *
     * <p>A comparator reads how full this is, so every write has to be announced - and
     * announcing each one separately is fine for a hopper moving an item and absurd for a
     * sort. Sorting eighteen hundred slots clears them all and writes them all back, so
     * the naive version is three and a half thousand neighbour updates inside one tick,
     * every one of them saying the same thing to the same blocks.
     *
     * <p>Only the telling is held back. {@code setChanged} still runs per write, which is
     * a flag rather than work.
     */
    public void inOneGo(Runnable work) {
        bulk = true;
        try {
            work.run();
        } finally {
            bulk = false;
        }
        told();
    }

    private void told() {
        if (!bulk && level != null && !level.isClientSide) {
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
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

    /** Set once {@link #handOver} has dropped the item itself. See {@code CellaBlock}. */
    private boolean given;

    /** Whether the item for this chest has already been dropped, contents and all. */
    public boolean given() {
        return given;
    }

    /** Nothing in any slot. Asked before deciding there is anything worth keeping. */
    public boolean isEmpty() {
        for (int slot = 0; slot < contents.getSlots(); slot++) {
            if (!contents.getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Files the contents away and drops the chest as an item that names them.
     *
     * <p>The forms that {@link Kind#keeps} do this instead of spilling. <b>The item is
     * dropped from here rather than from the loot table</b>, because here is the one
     * place that runs however the block came to be removed — broken in survival, broken
     * in creative, replaced by a command. The loot table runs only when something is
     * harvesting, and a chest that keeps its contents except when it does not would be
     * worse than one that never did.
     *
     * <p>An empty one is not filed. Nothing to keep, no name to give, and it drops the
     * ordinary way.
     */
    public void handOver(Level level, BlockPos pos) {
        if (level.isClientSide || isEmpty()) {
            return;
        }
        Kept.of(level).ifPresent(kept -> {
            java.util.UUID id = kept.put(contents.serializeNBT(level.registryAccess()));
            // The block entity is on its way out, but an emptied one cannot be read by
            // anything that still has hold of it.
            contents.setSize(contents.getSlots());
            ItemStack stack = new ItemStack(getBlockState().getBlock());
            stack.set(CellaRegistry.KEPT.get(), id);
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            given = true;
        });
    }

    /** Puts a filed chest back into this one, at the size it was filed at. */
    public void restore(HolderLookup.Provider registries, CompoundTag kept) {
        contents.deserializeNBT(registries, kept);
        setChanged();
    }

    /**
     * Everything inside, onto the floor.
     *
     * <p>What Laravel does, and only Laravel — see {@link Kind#keeps}. It is the one whose
     * contents a player can actually pick back up.
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
