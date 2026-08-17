package io.github.capsicum0907.cella;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The whole chest, of which one page is shown.
 *
 * <p>Every slot is here. What a page is lives in {@link PagedSlot}, and it is a fact
 * about drawing rather than about storage — so the page is a field on this side of the
 * wire only, and turning it sends nothing.
 *
 * <p>One of these per open screen, so two players reading the same chest can be on
 * different pages without either of them being told about the other.
 */
public class CellaMenu extends AbstractContainerMenu {
    private static final int PLAYER_ROWS = 3;
    private static final int HOTBAR = CellaConfig.PLAYER_COLUMNS;
    private static final int SLOT = 18;

    /** The lid is seventeen deep, and the first row of slots begins under it. */
    private static final int FIRST_Y = 18;

    /** The margin either side of the widest thing on the panel. */
    private static final int MARGIN = 7;

    /** The button ids that are not a page: there is no page for them to collide with. */
    public static final int SORT = -1;
    public static final int STOW = -2;
    public static final int MATCHING = -3;
    public static final int TAKE = -4;
    public static final int TAKING = -5;

    private final ContainerLevelAccess access;
    private final IItemHandlerModifiable contents;
    private final int pageSize;
    private final int columns;
    private final int width;
    private int page;

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
     * save — and a client whose menu has fewer slots than the server sends walks off the
     * end of its own list while it is being filled. Under the old design that mismatch
     * was invisible; here it is a crash, so the number is sent.
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
        // As wide as the widest of the two inventories, and never narrower than the
        // player's, which is nine whatever the chest is.
        this.width = Math.max(CellaConfig.PLAYER_COLUMNS, columns) * SLOT + 2 * MARGIN;
        int chestLeft = (width - columns * SLOT) / 2;
        int playerLeft = (width - CellaConfig.PLAYER_COLUMNS * SLOT) / 2;

        // Every page's slots, all at the same coordinates, stacked one page deep. Only
        // the page on show answers isActive, so only it is drawn or clicked.
        for (int index = 0; index < contents.getSlots(); index++) {
            int within = index % pageSize;
            addSlot(new PagedSlot(this, contents, index,
                    chestLeft + (within % columns) * SLOT,
                    FIRST_Y + (within / columns) * SLOT));
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
        layOut();
    }

    /**
     * Puts every page where it belongs relative to the one being shown.
     *
     * <p>Called whenever that changes, which is what stops two slots ever sharing a
     * position. See {@link PagedSlot} for what went wrong when they did.
     */
    private void layOut() {
        for (Slot slot : slots) {
            if (slot instanceof PagedSlot paged) {
                paged.place(page);
            }
        }
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
        return Math.max(1, (contents.getSlots() + pageSize - 1) / pageSize);
    }

    /**
     * Turns to a page, ignoring one that is not there.
     *
     * <p>No packet and no server. Which page is on show is not a fact about the chest,
     * and {@code isActive} is asked by the screen and by nothing else — it appears
     * nowhere in {@code AbstractContainerMenu}, and the server's click path does not
     * consult it.
     */
    public void turnTo(int wanted) {
        if (wanted >= 0 && wanted < pages()) {
            page = wanted;
            layOut();
        }
    }

    /**
     * The two things that are not a slot click.
     *
     * <p>Both run on the server, because both change what is in the chest. What comes
     * back arrives as the ordinary slot updates, and those are trustworthy now that slot
     * <em>i</em> is contents <em>i</em>: the game works out what to send by comparing
     * each slot against what it last told the client that slot held, which is sound
     * exactly when nothing moves underneath a slot. Nothing does any more.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id > SORT || id < TAKING) {
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
     * Shift-click fills the whole chest, which now costs nothing to say.
     *
     * <p>The chest's slots are the first {@code contents.getSlots()} of the menu and the
     * player's are the rest, so the ordinary vanilla move does it — including onto pages
     * that are not on screen. Under the old design this had to reach around the menu to
     * the contents underneath, because the menu only had the page.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        int chest = contents.getSlots();

        if (index < chest) {
            if (!moveItemStackTo(stack, chest, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, chest, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return before;
    }
}
