package io.github.capsicum0907.cella;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The chest itself: one run of slots, however many pages that comes to.
 *
 * <p><b>It knows nothing about pages.</b> Paging is a property of looking, and lives in
 * {@link Window}. What is here is a flat handler and the two things that have to be
 * true of it — that it is saved, and that everyone who is not a screen sees all of it.
 */
public class CellaBlockEntity extends BlockEntity implements MenuProvider {
    private static final String CONTENTS = "Contents";

    private final ItemStackHandler contents;

    public CellaBlockEntity(BlockPos pos, BlockState state) {
        super(CellaRegistry.BLOCK_ENTITY.get(), pos, state);
        this.contents = new ItemStackHandler(CellaConfig.pageSize() * CellaConfig.PAGES.get()) {
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

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return CellaMenu.at(id, inventory, getBlockPos());
    }
}
