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

public class CellaBlockEntity extends BlockEntity implements LidBlockEntity {
    private static final String CONTENTS = "Contents";
    private static final String HISTORY = "History";
    static final String PLAN = "Plan";
    private static final String EXPERIENCE = "Experience";

    private final Sorted contents;

    private int experience;

    private final ChestLidController lidController = new ChestLidController();

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
            level.blockEvent(pos, state.getBlock(), 1, now);
        }

        @Override
        protected boolean isOwnContainer(Player player) {
            return player.containerMenu instanceof CellaMenu menu && menu.isFor(contents);
        }
    };

    public CellaBlockEntity(BlockPos pos, BlockState state) {
        super(CellaRegistry.BLOCK_ENTITY.get(), pos, state);
        this.contents = new Sorted(kindOf(state).slots()) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                touched[(int) (revision % TOUCHED)] = slot;
                revision++;
                told();
            }

            @Override
            protected void moved(int from) {
                super.moved(from);
                settled = revision;
            }

            @Override
            protected boolean reporting() {
                return watching;
            }

            @Override
            protected void poured(int part, ItemStack kind, int before, int after) {
                if (watching) {
                    ledger.put(part, kind, before, after);
                    setChanged();
                }
            }
        };
        plan.ensure(contents.getSlots());
        contents.adopt(carving());
    }

    public Sorted contents() {
        return contents;
    }

    private final Plan plan = new Plan();

    public Plan plan() {
        return plan;
    }

    private Sorted.Carve carving() {
        return new Sorted.Carve() {
            @Override
            public int count() {
                return plan.count(contents.getSlots());
            }

            @Override
            public int first(int index) {
                return plan.first(index, contents.getSlots());
            }

            @Override
            public int past(int index) {
                return plan.past(index, contents.getSlots());
            }
        };
    }

    public void replan(Runnable change) {
        change.run();
        contents.adopt(carving());
        filed();
    }

    public void replan(Runnable change, java.util.function.IntUnaryOperator where) {
        Sorted.Carve was = contents.frozen();
        change.run();
        contents.carve(was, carving(), where);
        filed();
    }

    private void filed() {
        setChanged();
        if (level != null) {
            invalidateCapabilities();
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    public net.neoforged.neoforge.items.IItemHandlerModifiable outlet() {
        int at = plan.assigned();
        if (at == Plan.NONE) {
            return Outlet.NOTHING;
        }
        int slots = contents.getSlots();
        int from = plan.first(at, slots);
        return new Outlet(contents, from, plan.past(at, slots) - from);
    }

    public boolean resize(int index, Plan.Partition wanted) {
        int slots = contents.getSlots();
        if (index < 0 || index >= plan.count(slots)
                || wanted.slots() < contents.used(index)) {
            return false;
        }
        boolean[] done = new boolean[1];
        replan(() -> done[0] = plan.replace(index, wanted, slots), part -> part);
        return done[0];
    }

    public int firstGap() {
        return plan.taken();
    }

    public int spare() {
        return Plan.capacity(contents.getSlots()) - plan.taken();
    }

    public boolean divides() {
        return kind().trait().divides();
    }

    public boolean divide(Plan.Partition wanted) {
        if (!divides()) {
            return false;
        }
        int slots = contents.getSlots();
        boolean[] done = new boolean[1];
        replan(() -> done[0] = plan.add(wanted, slots));
        return done[0];
    }

    public boolean undivide(int index) {
        if (contents.used(index) > 0) {
            return false;
        }
        boolean[] done = new boolean[1];
        replan(() -> done[0] = plan.drop(index),
                part -> part < index ? part : (part > index ? part - 1 : -1));
        return done[0];
    }

    private final Ledger ledger = new Ledger();

    public Ledger ledger() {
        return ledger;
    }

    private boolean watching;

    public void byHand(Runnable work) {
        boolean was = watching;
        watching = true;
        try {
            work.run();
        } finally {
            watching = was;
        }
    }

    private boolean bulk;

    private static final int TOUCHED = 256;

    private final int[] touched = new int[TOUCHED];

    private long revision;

    private long settled;

    private static final int[] NOTHING = new int[0];

    public long revision() {
        return revision;
    }

    public int[] since(long mark) {
        if (mark < settled) {
            return null;
        }
        long behind = revision - mark;
        if (behind <= 0) {
            return NOTHING;
        }
        if (behind > TOUCHED) {
            return null;
        }
        int[] out = new int[(int) behind];
        for (int index = 0; index < out.length; index++) {
            out[index] = touched[(int) ((mark + index) % TOUCHED)];
        }
        return out;
    }

    public void inOneGo(Runnable work) {
        boolean was = bulk;
        bulk = true;
        try {
            contents.straight(work);
        } finally {
            bulk = was;
        }
        told();
    }

    private void told() {
        if (!bulk && level != null && !level.isClientSide) {
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    public static Kind kindOf(BlockState state) {
        return state.getBlock() instanceof CellaBlock chest ? chest.kind() : Kind.values()[0];
    }

    public Kind kind() {
        return kindOf(getBlockState());
    }

    public int experience() {
        return experience;
    }

    public float grown() {
        Kind kind = kind();
        return kind.grows() ? Math.min(1.0F, (float) experience / kind.growth()) : 0.0F;
    }

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
        if (ledger.any()) {
            tag.put(HISTORY, ledger.save(registries));
        }
        tag.put(PLAN, plan.save());
        tag.putInt(EXPERIENCE, experience);

        tag.putInt(FUSE, fuse);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        contents.deserializeNBT(registries, tag.getCompound(CONTENTS));

        plan.load(tag.getCompound(PLAN), contents.getSlots());
        contents.adopt(carving());
        ledger.load(registries, tag.getList(HISTORY, Ledger.TAG));

        experience = tag.getInt(EXPERIENCE);
        fuse = tag.contains(FUSE) ? tag.getInt(FUSE) : UNLIT;
    }

    private boolean given;

    public boolean given() {
        return given;
    }

    public int used() {
        return contents.used();
    }

    public boolean isEmpty() {
        return used() == 0;
    }

    public boolean worthKeeping() {
        return !isEmpty() || experience > 0;
    }

    public void handOver(Level level, BlockPos pos) {
        if (level.isClientSide || !worthKeeping()) {
            return;
        }
        int used = used();
        int slots = contents.getSlots();
        int grown = experience;
        Kept.of(level).ifPresent(kept -> {
            CompoundTag filed = contents.serializeNBT(level.registryAccess());
            filed.put(PLAN, plan.save());
            java.util.UUID id = kept.put(filed, kind(), level.getGameTime(), grown);

            contents.setSize(contents.getSlots());
            replan(() -> plan.load(new CompoundTag(), contents.getSlots()));
            experience = 0;
            ItemStack stack = new ItemStack(getBlockState().getBlock());
            stack.set(CellaRegistry.KEPT.get(),
                    new Held(java.util.List.of(id), used, slots, grown, kind().growth()));
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            given = true;
        });
    }

    public void restore(HolderLookup.Provider registries, Kept.Chest kept) {
        contents.deserializeNBT(registries, kept.contents());
        replan(() -> plan.load(kept.contents().getCompound(PLAN), contents.getSlots()));
        experience = capped(kept.experience());
        setChanged();
    }

    private int capped(int coming) {
        return Math.min(coming, kind().growth());
    }

    public java.util.List<ItemStack> fuse(HolderLookup.Provider registries,
            java.util.List<Kept.Chest> filed) {
        java.util.List<Plan.Partition> carved = new java.util.ArrayList<>();
        java.util.List<java.util.List<ItemStack>> held = new java.util.ArrayList<>();
        java.util.List<ItemStack> over = new java.util.ArrayList<>();

        for (Kept.Chest one : filed) {
            ItemStackHandler from = new ItemStackHandler();
            from.deserializeNBT(registries, one.contents());
            Plan was = new Plan();
            was.load(one.contents().getCompound(PLAN), from.getSlots());
            boolean[] claimed = new boolean[from.getSlots()];
            for (int index = 0; index < was.count(from.getSlots()); index++) {
                carved.add(was.at(index, from.getSlots()));
                java.util.List<ItemStack> inside = new java.util.ArrayList<>();
                for (int slot = was.first(index, from.getSlots());
                        slot < was.past(index, from.getSlots()); slot++) {
                    claimed[slot] = true;
                    if (!from.getStackInSlot(slot).isEmpty()) {
                        inside.add(from.getStackInSlot(slot));
                    }
                }
                held.add(inside);
            }
            for (int slot = 0; slot < claimed.length; slot++) {
                if (!claimed[slot] && !from.getStackInSlot(slot).isEmpty()) {
                    over.add(from.getStackInSlot(slot));
                }
            }
        }

        replan(() -> plan.load(Plan.of(carved), contents.getSlots()));
        inOneGo(() -> {
            int slots = contents.getSlots();
            for (int index = 0; index < held.size(); index++) {
                boolean there = index < plan.count(slots);
                int cursor = there ? plan.first(index, slots) : slots;
                int end = there ? plan.past(index, slots) : slots;
                for (ItemStack stack : held.get(index)) {
                    if (cursor < end) {
                        contents.setStackInSlot(cursor++, stack);
                    } else {
                        over.add(stack);
                    }
                }
            }
        });
        setChanged();
        return over;
    }

    public java.util.List<ItemStack> pour(HolderLookup.Provider registries,
            java.util.List<Kept.Chest> filed) {
        java.util.List<ItemStack> over = new java.util.ArrayList<>();

        inOneGo(() -> {
            int cursor = 0;

            for (Kept.Chest one : filed) {
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
        });
        setChanged();
        return over;
    }

    public void spill(Level level, BlockPos pos) {
        for (int slot = 0; slot < contents.getSlots(); slot++) {
            ItemStack stack = contents.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                contents.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }

        if (experience > 0 && level instanceof net.minecraft.server.level.ServerLevel server) {
            net.minecraft.world.entity.ExperienceOrb.award(server,
                    net.minecraft.world.phys.Vec3.atCenterOf(pos), experience);
            experience = 0;
        }
    }

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

    public int openers() {
        return openers.getOpenerCount();
    }

    public void recheck() {
        if (level != null && !remove) {
            openers.recheckOpeners(level, getBlockPos(), getBlockState());
        }
    }

    private int fuse = UNLIT;

    private static final String FUSE = "Fuse";

    private static final int UNLIT = -1;

    private static final int FUSE_TICKS = 100;

    public boolean lit() {
        return fuse > UNLIT;
    }

    public boolean light() {
        if (lit() || kind().becomes().isEmpty() || kind().ripens() || grown() < 1.0F) {
            return false;
        }
        fuse = FUSE_TICKS;
        setChanged();
        return true;
    }

    private void absorb() {
        Kind kind = kind();
        int room = kind.growth() - experience;
        if (kind.trait().reach() <= 0 || room <= 0 || level == null) {
            return;
        }
        for (net.minecraft.world.entity.ExperienceOrb orb : level.getEntitiesOfClass(
                net.minecraft.world.entity.ExperienceOrb.class,
                new net.minecraft.world.phys.AABB(worldPosition).inflate(kind.trait().reach()),
                alive -> alive.isAlive() && alive.getValue() <= kind.growth() - experience)) {
            experience += orb.getValue();
            orb.discard();
            setChanged();
            said(level, worldPosition, SoundEvents.EXPERIENCE_ORB_PICKUP);
        }
    }

    private static final int REACHES_EVERY = 10;

    public void serverTick() {
        recheck();
        if (level != null && level.getGameTime() % REACHES_EVERY == 0) {
            absorb();
        }
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

    private void end(net.minecraft.server.level.ServerLevel server) {
        BlockPos pos = getBlockPos();
        if (!become(server)) {
            return;
        }

        server.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8.0F,
                Level.ExplosionInteraction.NONE);
        Blast.start(server, pos, Blast.REACH);
    }

    public boolean ripen(net.minecraft.server.level.ServerLevel server) {
        if (!kind().ripens() || grown() < 1.0F || !become(server)) {
            return false;
        }
        said(server, getBlockPos(), SoundEvents.PLAYER_LEVELUP);
        return true;
    }

    private boolean become(net.minecraft.server.level.ServerLevel server) {
        Kind next = kind().becomes().orElse(null);
        if (next == null) {
            return false;
        }
        BlockPos pos = getBlockPos();
        CompoundTag was = contents.serializeNBT(server.registryAccess());

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

    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }
}
