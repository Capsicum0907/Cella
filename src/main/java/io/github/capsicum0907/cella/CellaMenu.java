package io.github.capsicum0907.cella;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * One page of a chest, and the player's own inventory below it.
 *
 * <p>The slots are ordinary {@link SlotItemHandler}s numbered nought upwards. What they
 * are laid over is a {@link Window}, and turning the page moves the window rather than
 * the slots — see that class for why it cannot be the other way round.
 *
 * <p>One of these per open screen, so the page is a field here and two players reading
 * the same chest do not fight over it.
 */
public class CellaMenu extends AbstractContainerMenu {
    private static final int PLAYER_ROWS = 3;
    private static final int HOTBAR = CellaConfig.COLUMNS;
    private static final int SLOT = 18;

    /** Where the chest's own slots start, in the vanilla chest layout. */
    private static final int FIRST_X = 8;
    private static final int FIRST_Y = 18;

    private final ContainerLevelAccess access;
    private final Window window;
    private final IItemHandlerModifiable contents;
    private final int pageSize;

    /**
     * Opened at a block, on both sides. The client builds its own from the position in
     * the packet and finds its own copy of the block entity, which is where its slots'
     * contents will be written as the server sends them.
     */
    public static CellaMenu at(int id, Inventory inventory, BlockPos pos) {
        ItemStackHandler contents = inventory.player.level().getBlockEntity(pos)
                        instanceof CellaBlockEntity chest
                ? chest.contents()
                : new ItemStackHandler(CellaConfig.pageSize());
        return new CellaMenu(id, inventory, contents,
                ContainerLevelAccess.create(inventory.player.level(), pos));
    }

    private CellaMenu(int id, Inventory inventory, IItemHandlerModifiable contents,
            ContainerLevelAccess access) {
        super(CellaRegistry.MENU.get(), id);
        this.access = access;
        this.contents = contents;
        this.pageSize = CellaConfig.pageSize();
        this.window = new Window(contents, pageSize);

        int rows = pageSize / CellaConfig.COLUMNS;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < CellaConfig.COLUMNS; column++) {
                addSlot(new SlotItemHandler(window, column + row * CellaConfig.COLUMNS,
                        FIRST_X + column * SLOT, FIRST_Y + row * SLOT));
            }
        }

        // The vanilla chest layout, which grows downwards as rows are added.
        int below = (rows - 4) * SLOT;
        for (int row = 0; row < PLAYER_ROWS; row++) {
            for (int column = 0; column < CellaConfig.COLUMNS; column++) {
                addSlot(new Slot(inventory, column + row * CellaConfig.COLUMNS + HOTBAR,
                        FIRST_X + column * SLOT, 103 + row * SLOT + below));
            }
        }
        for (int column = 0; column < HOTBAR; column++) {
            addSlot(new Slot(inventory, column, FIRST_X + column * SLOT, 161 + below));
        }
    }

    public Window window() {
        return window;
    }

    /**
     * The one id that is not a page.
     *
     * <p>Negative, so it cannot collide with a page however many pages there are, and
     * so {@code turnTo} would refuse it anyway if this test were ever removed.
     */
    public static final int SORT = -1;

    /** Everything the player is carrying, into the chest. */
    public static final int STOW = -2;

    /**
     * Turning the page, or the one thing that is not turning the page.
     *
     * <p>Rides on {@code clickMenuButton}, which the game already has a packet for, so
     * there is nothing of our own to send. The id <em>is</em> the page wanted rather
     * than "next" or "previous": a difference would depend on both sides agreeing about
     * where they already were, and they do not have to.
     *
     * <p>Sorting runs on the server only. The client's copy of the contents holds
     * whatever pages it has been shown and nothing else, so sorting it would shuffle a
     * chest that is mostly holes; what comes back from the server's own sort arrives as
     * the ordinary slot updates.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == SORT || id == STOW) {
            if (!player.level().isClientSide) {
                if (id == SORT) {
                    Tidy.everything(contents);
                } else {
                    stow(player);
                }
                resend();
            }
            return true;
        }
        window.turnTo(id);
        if (!player.level().isClientSide) {
            resend();
        }
        return true;
    }

    /**
     * The player's own storage, into the chest, wherever there is room for it.
     *
     * <p>Into the <em>contents</em> and not the page, which is the whole reason this
     * button exists: a sorting mod's version of it can only reach the slots the screen
     * has, so it fills the page on show and gives the rest back.
     *
     * <p><b>Except what is in their hand.</b> That is the one slot to keep something a
     * container would otherwise swallow, and without it a button meant to save time
     * takes the pickaxe you were holding.
     */
    private void stow(Player player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (slot == inventory.selected) {
                continue;
            }
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            inventory.setItem(slot, ItemHandlerHelper.insertItemStacked(contents, stack, false));
        }
        inventory.setChanged();
    }

    /**
     * Says every slot again, whether it looks changed or not.
     *
     * <p><b>This is the price of moving what is behind a slot.</b> The game decides what
     * to send by comparing each slot with what it last told the client that slot held —
     * {@code synchronizeSlotToRemote} against {@code remoteSlots} — which is exactly
     * right as long as a slot only changes when somebody puts something in it. Here the
     * slot can stay still while the storage underneath it moves, and then the comparison
     * is asking the wrong question.
     *
     * <p>What that looked like: page two full of iron blocks, page three also beginning
     * with iron blocks, and turning from one to the other sent nothing for those slots,
     * because to the server they had not changed. The client's own copy of the contents
     * had never been told anything about page three, so it drew them empty — a chest
     * with a hole in it that filled itself in as soon as anything was moved.
     *
     * <p>A whole page of slots is a chest's worth of packet, which is what opening any
     * chest costs, so there is nothing to save by being cleverer here.
     */
    private void resend() {
        sendAllDataToRemote();
    }

    @Override
    public boolean stillValid(Player player) {
        return AbstractContainerMenu.stillValid(access, player, CellaRegistry.BLOCK.get());
    }

    /**
     * Shift-click fills the whole chest, not the page being looked at.
     *
     * <p>The same answer the block gives a hopper, and for the same reason: which page
     * somebody happens to have open is not a fact about the chest. So this goes past the
     * window to the contents underneath, and an item shift-clicked in can land on a page
     * that is not on screen.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();

        if (index < pageSize) {
            if (!moveItemStackTo(stack, pageSize, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            ItemStack left = ItemHandlerHelper.insertItemStacked(contents, stack.copy(), false);
            if (left.getCount() == stack.getCount()) {
                return ItemStack.EMPTY;
            }
            stack.setCount(left.getCount());
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return before;
    }
}
