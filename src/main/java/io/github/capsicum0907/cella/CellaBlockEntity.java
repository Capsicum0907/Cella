package io.github.capsicum0907.cella;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
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
public class CellaBlockEntity extends BlockEntity implements LidBlockEntity {
    private static final String CONTENTS = "Contents";
    private static final String EXPERIENCE = "Experience";

    private final ItemStackHandler contents;

    /**
     * What it has been fed, in points.
     *
     * <p>Only ever goes up while the chest stands, and only by a player deciding to hand
     * some over; see {@link #absorb}. How much means anything is {@link Kind#growth}, and
     * a form that does not grow never has any.
     */
    private int experience;

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

    /**
     * How much it has been fed, and how much that is out of what it can use.
     *
     * <p>Nought to one, and nought for a form that does not grow — asked by the screen's
     * title and by the bar on the item, which are two views of the same figure rather than
     * two figures.
     */
    public int experience() {
        return experience;
    }

    public float grown() {
        Kind kind = kind();
        return kind.grows() ? Math.min(1.0F, (float) experience / kind.growth()) : 0.0F;
    }

    /**
     * Takes everything it can use off a player and keeps it.
     *
     * <p><b>In one go, not a level at a time.</b> This is not something being fed; it is
     * something absorbing, and a creature that takes what it needs in mouthfuls is a
     * different creature. The press is already deliberate — sneaking, empty-handed, at the
     * block — so making the amount small bought a safety that the gesture had already
     * bought, at the price of the one thing this is meant to feel like.
     *
     * <p>Bounded on both sides and by whichever runs out first: a player is emptied rather
     * than short-changed, and <b>a chest that has finished growing takes nothing</b> —
     * never a point past its threshold, since anything over it is experience that can no
     * longer mean anything, taken from somebody who cannot get it back.
     *
     * @return how many points moved, which is nought when the chest is full or the player
     *         is empty — the caller decides what to say about each
     */
    public int absorb(Player player) {
        Kind kind = kind();
        int room = kind.growth() - experience;
        if (!kind.grows() || room <= 0) {
            return 0;
        }
        int taken = Experience.take(player, room);
        if (taken > 0) {
            experience += taken;
            setChanged();
        }
        return taken;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(CONTENTS, contents.serializeNBT(registries));
        tag.putInt(EXPERIENCE, experience);
        // A lit chest that is saved is still lit when the world comes back. Forgetting it
        // would be a chest that quietly stopped being dangerous.
        tag.putInt(FUSE, fuse);
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
        // Absent in every chest written before experience existed, which reads as nought
        // and is the truth: none of them had been fed anything.
        experience = tag.getInt(EXPERIENCE);
        fuse = tag.contains(FUSE) ? tag.getInt(FUSE) : UNLIT;
    }

    /** Set once {@link #handOver} has dropped the item itself. See {@code CellaBlock}. */
    private boolean given;

    /** Whether the item for this chest has already been dropped, contents and all. */
    public boolean given() {
        return given;
    }

    /**
     * How many slots have something in them.
     *
     * <p>Slots rather than stacks, because slots are the scarce thing in a Cella: one
     * item in each of two hundred thousand of them is full in the only sense that matters
     * when you go to put something away. Written onto the item at {@link #handOver} and
     * shown there; see {@link Held}.
     */
    public int used() {
        int used = 0;
        for (int slot = 0; slot < contents.getSlots(); slot++) {
            if (!contents.getStackInSlot(slot).isEmpty()) {
                used++;
            }
        }
        return used;
    }

    /** Nothing in any slot. Says what its name says, and nothing about experience. */
    public boolean isEmpty() {
        return used() == 0;
    }

    /**
     * Whether breaking this would lose something.
     *
     * <p><b>Not the same question as {@link #isEmpty}</b>, and the difference is the whole
     * point of having two. A chest with no items and fifty levels in it is empty and is
     * very much worth keeping — and since experience is one way, dropping it would not be
     * an inconvenience but the loss of everything that was fought for, with no way back
     * but to fight for it again.
     */
    public boolean worthKeeping() {
        return !isEmpty() || experience > 0;
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
     * <p>One with nothing to keep is not filed at all — no items and no experience means
     * no name to give, and it drops the ordinary way. {@link #worthKeeping} is the test,
     * not {@link #isEmpty}: experience does not sit in a slot.
     */
    public void handOver(Level level, BlockPos pos) {
        if (level.isClientSide || !worthKeeping()) {
            return;
        }
        int used = used();
        int slots = contents.getSlots();
        int grown = experience;
        Kept.of(level).ifPresent(kept -> {
            // Which form and what time, because once the block is gone this is the last
            // place either was known - and an orphan nobody can describe is an orphan
            // nobody can decide about. See Kept.Trace.
            java.util.UUID id = kept.put(contents.serializeNBT(level.registryAccess()),
                    kind(), level.getGameTime(), grown);
            // The block entity is on its way out, but an emptied one cannot be read by
            // anything that still has hold of it.
            contents.setSize(contents.getSlots());
            experience = 0;
            ItemStack stack = new ItemStack(getBlockState().getBlock());
            stack.set(CellaRegistry.KEPT.get(),
                    new Held(java.util.List.of(id), used, slots, grown, kind().growth()));
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            given = true;
        });
    }

    /**
     * Puts a filed chest back into this one, at the size and in the arrangement it was
     * filed at.
     *
     * <p>Breaking a chest and putting it down again gives you back <em>that chest</em>,
     * which is why this replaces rather than pours: the size comes back too, so a chest
     * built when the numbers were different stays the size it was.
     */
    public void restore(HolderLookup.Provider registries, Kept.Chest kept) {
        contents.deserializeNBT(registries, kept.contents());
        experience = capped(kept.experience());
        setChanged();
    }

    /**
     * Experience coming back in, never past what this form can use.
     *
     * <p>A chest put down as a form that grows less than the one it was filed from would
     * otherwise sit over its own threshold, and a bar reading more than full is a bar
     * saying something that is not true.
     */
    private int capped(int coming) {
        return Math.min(coming, kind().growth());
    }

    /**
     * Pours several filed chests into this one, in order, closing up the gaps.
     *
     * <p>What a fusion gives you is the <em>contents</em> of what it ate rather than any
     * one of the chests, so this appends instead of replacing and does not touch the size.
     * Eight chests a tenth full become one chest a tenth full with everything at the
     * front, which is the only arrangement that means anything after eight were merged.
     *
     * @return what would not fit, which {@link Fusing} makes impossible and this counts
     *         anyway - the caller decides what to do about a world that has one
     */
    public java.util.List<ItemStack> pour(HolderLookup.Provider registries,
            java.util.List<Kept.Chest> filed) {
        java.util.List<ItemStack> over = new java.util.ArrayList<>();
        int cursor = 0;
        int grown = experience;
        for (Kept.Chest one : filed) {
            // What several were fed adds up, the way what several held does. Capped at the
            // end rather than per chest, so the order they were eaten in cannot change it.
            grown += one.experience();
            ItemStackHandler from = new ItemStackHandler();
            from.deserializeNBT(registries, one.contents());
            for (int slot = 0; slot < from.getSlots(); slot++) {
                ItemStack stack = from.getStackInSlot(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                while (cursor < contents.getSlots()
                        && !contents.getStackInSlot(cursor).isEmpty()) {
                    cursor++;
                }
                if (cursor >= contents.getSlots()) {
                    over.add(stack);
                } else {
                    contents.setStackInSlot(cursor++, stack);
                }
            }
        }
        experience = capped(grown);
        setChanged();
        return over;
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
        // ⚠ And what it has eaten, back on the floor as orbs.
        //
        // The larva keeps nothing, and that has to include this. The alternative was to
        // let it vanish, which is the silent loss this mod keeps closing - and worse here
        // than for items, because experience is one way and there is no picking it back
        // up off the ground unless something puts it there.
        //
        // It does sit oddly beside "one way": a Laravel can be broken to get its feeding
        // back. But a chest that has to be destroyed to open it is not a bank, and nothing
        // comes out that did not go in, so what it costs is the chest and not the rule.
        if (experience > 0 && level instanceof net.minecraft.server.level.ServerLevel server) {
            net.minecraft.world.entity.ExperienceOrb.award(server,
                    net.minecraft.world.phys.Vec3.atCenterOf(pos), experience);
            experience = 0;
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

    /** Ticks left before it ends itself, or {@link #UNLIT}. */
    private int fuse = UNLIT;

    private static final String FUSE = "Fuse";

    /** Not counting. Negative so that nought can be the tick it goes off on. */
    private static final int UNLIT = -1;

    /**
     * How long between the star and the blast.
     *
     * <p>⚠ <b>There has to be one.</b> The wave removes the ground rather than damaging
     * anybody, so in the End what it does to whoever is standing there is drop them into
     * nothing — and an irreversible thing that happens the same instant it is asked for is
     * a thing players lose worlds to. Five seconds is enough to get away from the middle
     * and nowhere near enough to get a hundred blocks out, which is the intended bargain.
     *
     * <p>It counts down out loud for the same reason.
     */
    private static final int FUSE_TICKS = 100;

    public boolean lit() {
        return fuse > UNLIT;
    }

    /**
     * Starts the countdown, if this is a form that has somewhere to go and has taken in
     * everything it can use.
     *
     * @return whether it took
     */
    public boolean light() {
        if (lit() || kind().becomes().isEmpty() || kind().ripens() || grown() < 1.0F) {
            return false;
        }
        fuse = FUSE_TICKS;
        setChanged();
        return true;
    }

    /** The server's tick: the openers recheck, and the fuse if one is burning. */
    public void serverTick() {
        recheck();
        if (!lit() || !(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        fuse--;
        if (fuse % 20 == 0 && fuse > 0) {
            said(level, worldPosition, SoundEvents.NOTE_BLOCK_BASEDRUM.value());
        }
        if (fuse <= 0) {
            fuse = UNLIT;
            end(server);
        }
    }

    /**
     * It destroys itself and comes back as what it was becoming.
     *
     * <p><b>The contents move from block to block and never become an item.</b> An item at
     * the centre of this would be thrown by the explosion, in a dimension made largely of
     * somewhere to fall — so the one thing that must survive would be the one thing put
     * where it could not. Nothing is filed and nothing is dropped; the chest is simply
     * standing there afterwards, which is also what happened in the story.
     *
     * <p><b>Poured rather than restored</b>, because what it comes back as is bigger. See
     * {@link #restore}: putting a filed chest back brings its size with it, which is right
     * when it is the same chest and wrong when the whole point is that it is not.
     *
     * <p>The experience is spent. It bought this.
     */
    private void end(net.minecraft.server.level.ServerLevel server) {
        BlockPos pos = getBlockPos();
        if (!become(server)) {
            return;
        }
        // Vanilla's explosion for what vanilla's explosion is good at - the noise, the
        // light and throwing whatever is standing about. It is not what removes the
        // ground; see Blast for why it cannot be.
        server.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8.0F,
                Level.ExplosionInteraction.NONE);
        Blast.start(server, pos, Blast.REACH);
    }

    /**
     * It has eaten enough and grows up, there and then.
     *
     * <p>The other way a form changes, and the quiet one. Nothing is spent and nothing is
     * destroyed — see {@link Kind#ripens} for why the two are different events rather than
     * one mechanism with a flag on it.
     *
     * @return whether it changed
     */
    public boolean ripen(net.minecraft.server.level.ServerLevel server) {
        if (!kind().ripens() || grown() < 1.0F || !become(server)) {
            return false;
        }
        said(server, getBlockPos(), SoundEvents.PLAYER_LEVELUP);
        return true;
    }

    /**
     * Turns into whatever it becomes, in place, keeping what is inside it.
     *
     * <p><b>The contents move from block to block and never become an item.</b> For the
     * ending that matters most — an item at the centre of that would be thrown by the
     * explosion, in a dimension largely made of somewhere to fall, so the one thing that
     * has to survive would be the one thing put where it could not. For growing up it is
     * simply the truth: nothing was dropped, it is the same chest and it got bigger.
     *
     * <p><b>Poured rather than restored</b>, because what it comes back as is bigger. See
     * {@link #restore}: putting a filed chest back brings its size with it, which is right
     * when it is the same chest at the same size and wrong when the point is that it is
     * not.
     *
     * <p>The experience is spent either way. It bought this.
     *
     * @return whether there was anywhere to go
     */
    private boolean become(net.minecraft.server.level.ServerLevel server) {
        Kind next = kind().becomes().orElse(null);
        if (next == null) {
            return false;
        }
        BlockPos pos = getBlockPos();
        CompoundTag was = contents.serializeNBT(server.registryAccess());

        // Emptied before the block is replaced, so that onRemove finds nothing worth
        // keeping and neither files it, drops an item naming it, nor spills it.
        contents.setSize(contents.getSlots());
        experience = 0;

        BlockState born = CellaRegistry.block(next).get().defaultBlockState()
                .setValue(CellaBlock.FACING, getBlockState().getValue(CellaBlock.FACING));
        server.setBlockAndUpdate(pos, born);
        if (server.getBlockEntity(pos) instanceof CellaBlockEntity reborn) {
            reborn.pour(server.registryAccess(), java.util.List.of(new Kept.Chest(was, 0)));
        }
        return true;
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

    /**
     * What the screen is titled.
     *
     * <p>This is not a {@code MenuProvider} any more. It was, and that put the making of
     * the menu here — where the only shape available is the config's, while the packet
     * that goes with it is written by the block from the player's own screen. Those are
     * two answers to one question. The block makes both now; see {@code CellaBlock}.
     */
    public Component getDisplayName() {
        Component name = Component.translatable(getBlockState().getBlock().getDescriptionId());
        if (!kind().grows()) {
            return name;
        }
        // Worked out as the screen is opened, which is when it is true: the only thing
        // that moves this figure is a player feeding the block, and that cannot be done
        // while its screen is in the way.
        return Component.translatable("container.cella.grown", name,
                Math.round(grown() * 100.0F));
    }

}
