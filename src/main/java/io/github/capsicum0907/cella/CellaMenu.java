package io.github.capsicum0907.cella;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * One page of a chest, with the whole chest underneath it.
 *
 * <p>The menu is a page tall — {@link Window} is the page, and the slots are ordinary
 * slots into it. The five buttons reach past it into {@link #contents}, which is the
 * whole thing, because that is what they are for: what a screen can do is limited to what
 * it shows, and these are the ones that are not.
 *
 * <p>One of these per open screen. Two players reading the same chest have a page each.
 */
public class CellaMenu extends AbstractContainerMenu {
    private static final int PLAYER_ROWS = 3;
    private static final int HOTBAR = CellaConfig.PLAYER_COLUMNS;
    private static final int SLOT = 18;

    /** The lid is seventeen deep, and the first row of slots begins under it. */
    private static final int FIRST_Y = 18;

    /** The margin either side of the widest thing on the panel. */
    private static final int MARGIN = 7;

    /**
     * The button ids that are not a page.
     *
     * <p>Negative on purpose: a page is sent as itself, so the two cannot collide however
     * many pages a chest grows to.
     */
    public static final int SORT = -1;
    public static final int STOW = -2;
    public static final int MATCHING = -3;
    public static final int TAKE = -4;
    public static final int TAKING = -5;

    private final ContainerLevelAccess access;
    private final IItemHandlerModifiable contents;
    private final Window window;
    private final int pageSize;
    private final int columns;
    private final int width;

    /** Whether this is the server's copy of the menu. See {@link #turnTo}. */
    private final boolean server;

    /** The page being shown. */
    private int page;

    /**
     * The page the server has, which is what a click will be read against.
     *
     * <p>Sent as a data slot, so it arrives with the contents and by the same means. On
     * the server it is always {@link #page}; on the client it lags it by one round trip,
     * and the gap is what {@link #pending} is.
     */
    private int agreed;

    /**
     * Opened at a block, on both sides.
     *
     * <p><b>The size and the page height come over the wire, not out of the config.</b>
     * How tall a page is could be read off the block at that position instead, and the
     * one branch below where there is no block would then have nothing to ask. One more
     * number in a packet that is already being sent is cheaper than a clever answer with
     * a hole in it. A chest keeps the
     * size it was built with, so a world whose config has since been turned down holds
     * chests bigger than the config says. The client builds its menu from its own copy of
     * the block entity, which was made at the config's size and knows nothing about the
     * save — and a client whose page count disagrees with the server's is one that can
     * ask for a page the server will refuse, and sit waiting for it.
     */
    public static CellaMenu at(int id, Inventory inventory, BlockPos pos, int size,
            int rows, int columns) {
        Level level = inventory.player.level();
        IItemHandlerModifiable contents;
        if (level.getBlockEntity(pos) instanceof CellaBlockEntity chest) {
            contents = chest.contents();
            // Only ever the client catching up. Resizing the server's own contents is how
            // a chest gets emptied by somebody opening it.
            if (level.isClientSide && contents.getSlots() != size) {
                chest.contents().setSize(size);
            }
        } else {
            contents = new ItemStackHandler(size);
        }
        return new CellaMenu(id, inventory, contents, rows, columns,
                ContainerLevelAccess.create(level, pos));
    }

    private CellaMenu(int id, Inventory inventory, IItemHandlerModifiable contents,
            int rows, int columns, ContainerLevelAccess access) {
        super(CellaRegistry.MENU.get(), id);
        this.access = access;
        this.contents = contents;
        this.columns = columns;
        this.pageSize = rows * columns;
        this.window = new Window(contents, pageSize);
        this.server = !inventory.player.level().isClientSide;
        // As wide as the widest of the two inventories, and never narrower than the
        // player's, which is nine whatever the chest is.
        this.width = Math.max(CellaConfig.PLAYER_COLUMNS, columns) * SLOT + 2 * MARGIN;
        int chestLeft = (width - columns * SLOT) / 2;
        int playerLeft = (width - CellaConfig.PLAYER_COLUMNS * SLOT) / 2;

        // A page's worth, and the page underneath them moves. There is one slot per
        // square on the screen, which is the reason the mouse cannot be lied to.
        for (int index = 0; index < pageSize; index++) {
            addSlot(new PagedSlot(window, index,
                    chestLeft + (index % columns) * SLOT,
                    FIRST_Y + (index / columns) * SLOT));
        }

        // The vanilla chest layout, which grows downwards as rows are added.
        int below = (rows() - 4) * SLOT;
        for (int row = 0; row < PLAYER_ROWS; row++) {
            for (int column = 0; column < CellaConfig.PLAYER_COLUMNS; column++) {
                addSlot(new Slot(inventory, column + row * CellaConfig.PLAYER_COLUMNS + HOTBAR,
                        playerLeft + column * SLOT, 103 + row * SLOT + below));
            }
        }
        for (int column = 0; column < HOTBAR; column++) {
            addSlot(new Slot(inventory, column, playerLeft + column * SLOT, 161 + below));
        }

        // The page, travelling the way every other number a menu has travels. Reading it
        // is the server answering; being set is the client being told, and the client
        // takes the answer as final - see turnTo.
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return agreed;
            }

            @Override
            public void set(int value) {
                agreed = value;
                show(value);
            }
        });
    }

    /**
     * Whether this menu is the one open on that chest.
     *
     * <p>Asked by the opener count, which has to tell one chest from another: a player
     * standing in this one with a different one open must not be counted here. The
     * contents are the identity - one handler per block entity, and the menu was built
     * from it.
     */
    public boolean isFor(IItemHandlerModifiable other) {
        return contents == other;
    }

    /** The screen has gone away; the lid can come down if nobody else has it up. */
    @Override
    public void removed(Player player) {
        super.removed(player);
        access.execute((level, pos) -> {
            if (level.getBlockEntity(pos) instanceof CellaBlockEntity chest) {
                chest.closed(player);
            }
        });
    }

    public int pageSize() {
        return pageSize;
    }

    /** How tall the chest half of the screen is, and how wide, and how far across. */
    public int rows() {
        return pageSize / columns;
    }

    public int columns() {
        return columns;
    }

    public int width() {
        return width;
    }

    public int page() {
        return page;
    }

    public int pages() {
        return window.pages();
    }

    /**
     * Whether the page on screen is one the server has not agreed to yet.
     *
     * <p>True for the length of one round trip after turning, and it matters for exactly
     * one thing: <b>a click is read against the server's page, not the drawn one.</b>
     * While these disagree the screen is showing the page that was asked for and the
     * server would answer about the page before it, so a click in that gap takes an item
     * other than the one under the pointer. The screen refuses chest clicks until this
     * goes false — see {@code CellaScreen}. It is never true on the server.
     */
    public boolean pending() {
        return page != agreed;
    }

    /** Shows a page, which is all that turning one is: the window moves, nothing is told. */
    private void show(int wanted) {
        page = wanted;
        window.openAt(wanted);
    }

    /**
     * Turns to a page, ignoring one that is not there.
     *
     * <p><b>The server owns which page a click means.</b> So the client turns at once and
     * asks, and the server turns and answers with the page's contents and the page number
     * together — one {@code sendAllDataToRemote}, which is the one call that says "forget
     * what you were told about these slots". That call is the whole reason a window is
     * workable: the game decides what to send by comparing each slot against what it last
     * told the client that slot held, which is sound only while nothing moves underneath
     * a slot. Under a window something does, every time a page turns, and this is how
     * vanilla itself says so.
     *
     * <p>The client does not wait to draw, because a chest that answers the arrow key
     * fifty milliseconds later feels broken. It waits to <em>act</em>, which is the part
     * that can be wrong.
     */
    public void turnTo(int wanted) {
        if (wanted < 0 || wanted >= pages()) {
            return;
        }
        show(wanted);
        if (server) {
            agreed = wanted;
            sendAllDataToRemote();
        }
    }

    /**
     * A page, or one of the five things that are not a slot click.
     *
     * <p>The buttons run on the server because they change what is in the chest. What
     * comes back arrives as the ordinary slot updates, which are trustworthy for the page
     * on show and irrelevant for the rest: a slot the client cannot see is a slot it does
     * not need to be told about, and it is told all of them again the moment it turns.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= 0) {
            // Whatever was sent, which is not necessarily a page. turnTo checks.
            turnTo(id);
            return true;
        }
        if (id < TAKING) {
            return false;
        }
        if (!player.level().isClientSide) {
            // Every one of these writes most of the chest, so the neighbours are told
            // once at the end rather than once per slot. See CellaBlockEntity#inOneGo.
            inOneGo(() -> {
                switch (id) {
                    case SORT -> Tidy.everything(contents);
                    case STOW -> stow(player, false);
                    case MATCHING -> stow(player, true);
                    case TAKE -> take(player, false);
                    default -> take(player, true);
                }
            });
        }
        return true;
    }

    /**
     * The chest, back into the player, until one of them runs out.
     *
     * <p>The mirror of {@link #stow}, and it exists for the same reason: what a sorting
     * mod can move is what its screen can see, and this screen shows a page.
     *
     * <p><b>Matching</b> means the kinds the player is already carrying - coming back for
     * more of what you have. It reads their slots including the one in hand, which is the
     * difference from stowing: your hand says what you want, it is only not a place to
     * put things.
     *
     * <p>Stops at the first stack that will not fit rather than stepping over it, so what
     * comes out is the front of the chest and not a scattering from the middle of it.
     */
    private void take(Player player, boolean matchingOnly) {
        Inventory inventory = player.getInventory();
        Set<ItemStack> carried = matchingOnly ? carried(inventory) : Set.of();

        for (int slot = 0; slot < contents.getSlots(); slot++) {
            ItemStack stack = contents.getStackInSlot(slot);
            if (stack.isEmpty() || (matchingOnly && !carried.contains(stack))) {
                continue;
            }
            ItemStack moving = stack.copy();
            inventory.add(moving);
            contents.setStackInSlot(slot, moving);
            if (!moving.isEmpty()) {
                break;
            }
        }
        inventory.setChanged();
    }

    /** Every kind the player has, the hand included. */
    private static Set<ItemStack> carried(Inventory inventory) {
        Set<ItemStack> kinds = ItemStackLinkedSet.createTypeAndComponentsSet();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                kinds.add(stack);
            }
        }
        return kinds;
    }

    /** Through the block entity when there is one; a menu without one has nobody to tell. */
    private void inOneGo(Runnable work) {
        access.execute((level, pos) -> {
            if (level.getBlockEntity(pos) instanceof CellaBlockEntity chest) {
                chest.inOneGo(work);
            } else {
                work.run();
            }
        });
    }

    /**
     * The player's own storage, into the chest, wherever there is room for it.
     *
     * <p>Two buttons, one method, and the difference is one question asked per stack.
     * <b>Matching</b> only sends what the chest already keeps — the thing you want when
     * coming home with a full inventory and a place for half of it. <b>Everything</b>
     * asks nothing and sends the lot.
     *
     * <p><b>Except what is in their hand</b>, either way. That is the one slot to keep
     * something a container would otherwise swallow, and without it a button meant to
     * save time takes the pickaxe you were holding.
     *
     * @param matchingOnly whether a stack has to be one the chest already holds
     */
    private void stow(Player player, boolean matchingOnly) {
        Inventory inventory = player.getInventory();
        Set<ItemStack> kept = matchingOnly ? kinds() : Set.of();

        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (slot == inventory.selected) {
                continue;
            }
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || (matchingOnly && !kept.contains(stack))) {
                continue;
            }
            inventory.setItem(slot, ItemHandlerHelper.insertItemStacked(contents, stack, false));
        }
        inventory.setChanged();
    }

    /**
     * Every kind the chest holds, as a set that can be asked.
     *
     * <p>A set rather than a list walked per stack: the chest can be eighteen hundred
     * slots and the player has thirty-six, and a list makes that a multiplication.
     * {@code ItemStackLinkedSet} hashes on item <em>and</em> components, which is the
     * question being asked.
     */
    private Set<ItemStack> kinds() {
        Set<ItemStack> kept = ItemStackLinkedSet.createTypeAndComponentsSet();
        for (int slot = 0; slot < contents.getSlots(); slot++) {
            ItemStack stack = contents.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                kept.add(stack);
            }
        }
        return kept;
    }

    @Override
    public boolean stillValid(Player player) {
        // Any of them, because a menu does not care which kind it was opened on - only
        // that the player is still standing at one.
        return access.evaluate((level, pos) ->
                level.getBlockState(pos).getBlock() instanceof CellaBlock
                        && player.canInteractWithBlock(pos, 4.0), true);
    }

    /**
     * Shift-click fills the whole chest, not the page it is looking at.
     *
     * <p>Out of the chest is vanilla's own move: the destination is the player, and every
     * one of their slots is in this menu.
     *
     * <p>Into the chest is not, and cannot be. The menu holds a page, and the answer to
     * "the page is full" has to be the next page rather than the player's hand. So that
     * direction goes to the contents underneath — {@code insertItemStacked}, which tops
     * up partial stacks before it opens a new slot, on every page there is.
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
            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            return before;
        }

        ItemStack left = ItemHandlerHelper.insertItemStacked(contents, stack, false);
        // Nothing moved. Saying so is what stops the caller asking again forever.
        if (left.getCount() == stack.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.set(left);
        return before;
    }
}
