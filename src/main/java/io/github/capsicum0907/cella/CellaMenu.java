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

public class CellaMenu extends AbstractContainerMenu {
    private static final int PLAYER_ROWS = 3;
    private static final int HOTBAR = CellaConfig.PLAYER_COLUMNS;
    public static final int SLOT = 18;

    private static final int FIRST_Y = 18;

    private static final int MARGIN = 7;

    public static final int CHROME = 114;

    public static int height(int rows) {
        return CHROME + rows * SLOT;
    }

    public static int width(int columns) {
        return Math.max(CellaConfig.PLAYER_COLUMNS, columns) * SLOT + 2 * MARGIN;
    }

    public static int rowsIn(int height) {
        return (height - CHROME) / SLOT;
    }

    public static int columnsIn(int width) {
        return (width - 2 * MARGIN) / SLOT;
    }

    public static final int SORT = -1;
    public static final int STOW = -2;
    public static final int MATCHING = -3;
    public static final int TAKE = -4;
    public static final int TAKING = -5;

    private final ContainerLevelAccess access;

    private final IItemHandlerModifiable contents;

    private final Window window;

    private final Kind kind;
    private final int pageSize;
    private final int columns;
    private final int width;

    private final boolean server;

    public static CellaMenu at(int id, Inventory inventory, BlockPos pos, int size,
            int rows, int columns) {
        Level level = inventory.player.level();
        int pageSize = rows * columns;
        ContainerLevelAccess access = ContainerLevelAccess.create(level, pos);

        Kind kind = CellaBlockEntity.kindOf(level.getBlockState(pos));
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof CellaBlockEntity chest) {
            return new CellaMenu(id, inventory, chest.contents(),
                    Window.onto(chest.contents(), pageSize), columns, access, kind);
        }

        ItemStackHandler shown = new ItemStackHandler(pageSize);
        return new CellaMenu(id, inventory, shown, Window.of(shown, pageSize, size),
                columns, access, kind);
    }

    private CellaMenu(int id, Inventory inventory, IItemHandlerModifiable contents,
            Window window, int columns, ContainerLevelAccess access, Kind kind) {
        super(CellaRegistry.MENU.get(), id);
        this.access = access;
        this.kind = kind;
        this.contents = contents;
        this.columns = columns;
        this.pageSize = window.getSlots();
        this.window = window;
        this.server = !inventory.player.level().isClientSide;
        this.width = width(columns);
        int chestLeft = (width - columns * SLOT) / 2;
        int playerLeft = (width - CellaConfig.PLAYER_COLUMNS * SLOT) / 2;

        for (int index = 0; index < pageSize; index++) {
            addSlot(new PagedSlot(window, index,
                    chestLeft + (index % columns) * SLOT,
                    FIRST_Y + (index / columns) * SLOT));
        }

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

        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return window.page();
            }

            @Override
            public void set(int value) {
                window.openAt(value);
            }
        });

        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return window.pages();
            }

            @Override
            public void set(int value) {
                told = value;
                window.told(told, onPage);
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return window.onThisPage();
            }

            @Override
            public void set(int value) {
                onPage = value;
                window.told(told, onPage);
            }
        });

        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return contents instanceof Sorted sorted ? sorted.order().ordinal() : 0;
            }

            @Override
            public void set(int value) {
                kept = value;
            }
        });

        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return viewing;
            }

            @Override
            public void set(int value) {
                viewing = value;
            }
        });

        if (server) {
            access.execute((level, pos) -> {
                if (!(level.getBlockEntity(pos) instanceof CellaBlockEntity chest)) {
                    return;
                }
                if (chest.plan().divided()) {
                    viewing = Peek.LIST;
                    window.limit(0, 0);
                } else {
                    viewing = 0;
                }
            });
        }

        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return access.evaluate((level, at) ->
                        level.getBlockEntity(at) instanceof CellaBlockEntity chest
                                ? Math.round(chest.grown() * 100.0F)
                                : 0).orElse(0);
            }

            @Override
            public void set(int value) {
                grown = value;
            }
        });
    }

    private int kept;

    public Order order() {
        if (contents instanceof Sorted sorted) {
            return sorted.order();
        }
        Order[] all = Order.values();
        return all[Math.floorMod(kept, all.length)];
    }

    private int grown;

    public int grown() {
        return grown;
    }

    private int told = 1;
    private int onPage;

    public boolean isFor(IItemHandlerModifiable other) {
        return contents == other;
    }

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

    public int rows() {
        return pageSize / columns;
    }

    public int columns() {
        return columns;
    }

    public int width() {
        return width;
    }

    public Kind kind() {
        return kind;
    }

    public int page() {
        return window.page();
    }

    public int pages() {
        return window.pages();
    }

    private int viewing = Peek.LIST;

    public int viewing() {
        return viewing;
    }

    public void view(int index) {
        if (!server) {
            return;
        }
        access.execute((level, pos) -> {
            if (!(level.getBlockEntity(pos) instanceof CellaBlockEntity chest)) {
                return;
            }
            int slots = chest.contents().getSlots();
            java.util.List<Plan.Partition> carved = chest.plan().over(slots);
            if (index < 0 || index >= carved.size()) {
                viewing = Peek.LIST;
                window.limit(0, 0);
            } else {
                viewing = index;
                Plan.Partition one = carved.get(index);
                window.limit(one.first(), Math.min(slots, one.past()) - one.first());
            }
            seen = revision();
            sendAllDataToRemote();
        });
    }

    public void edit(Edit edit) {
        if (!server) {
            return;
        }
        access.execute((level, pos) -> {
            if (!(level.getBlockEntity(pos) instanceof CellaBlockEntity chest)) {
                return;
            }
            if (edit.drop()) {
                chest.undivide(edit.index());
            } else if (edit.index() == Edit.ADDING) {
                chest.divide(edit.wanted());
            } else {
                chest.resize(edit.index(), edit.wanted());
            }
            view(Peek.LIST);
        });
    }

    public Shelf shelf(int shown) {
        return access.evaluate((level, pos) ->
                level.getBlockEntity(pos) instanceof CellaBlockEntity chest
                        ? Shelf.of(chest, shown)
                        : Shelf.NOTHING).orElse(Shelf.NOTHING);
    }

    public boolean nothingShown() {
        return !window.holds(0);
    }

    public void turnTo(int wanted) {
        if (!server || wanted < 0 || wanted >= pages()) {
            return;
        }
        window.openAt(wanted);
        sendAllDataToRemote();
    }

    public void look(String looking) {
        if (!server) {
            return;
        }
        window.search(looking);
        seen = revision();
        sendAllDataToRemote();
    }

    private long seen;

    private long revision() {
        return access.evaluate((level, pos) ->
                level.getBlockEntity(pos) instanceof CellaBlockEntity chest
                        ? chest.revision()
                        : 0L).orElse(0L);
    }

    @Override
    public void broadcastChanges() {
        if (server && window.searching()) {
            follow();
        }
        super.broadcastChanges();
    }

    private void follow() {
        access.execute((level, pos) -> {
            if (!(level.getBlockEntity(pos) instanceof CellaBlockEntity chest)) {
                return;
            }
            long now = chest.revision();
            if (now == seen) {
                return;
            }
            int[] since = chest.since(seen);
            seen = now;
            boolean moved = false;
            if (since == null) {
                moved = window.again();
            } else {
                for (int slot : since) {
                    moved |= window.changed(slot);
                }
            }
            if (moved) {
                sendAllDataToRemote();
            }
        });
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= 0) {
            turnTo(id);
            return true;
        }
        if (id < TAKING) {
            return false;
        }
        if (!player.level().isClientSide) {
            byHand(() -> inOneGo(() -> {
                switch (id) {
                    case SORT -> {
                        if (contents instanceof Sorted sorted) {
                            sorted.order(sorted.order().next());
                        } else {
                            Tidy.everything(contents);
                        }
                    }
                    case STOW -> stow(player, false);
                    case MATCHING -> stow(player, true);
                    case TAKE -> take(player, false);
                    default -> take(player, true);
                }
            }));
        }
        return true;
    }

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

    private void byHand(Runnable work) {
        access.execute((level, pos) -> {
            if (level.getBlockEntity(pos) instanceof CellaBlockEntity chest) {
                chest.byHand(work);
            } else {
                work.run();
            }
        });
    }

    @Override
    public void clicked(int id, int button, net.minecraft.world.inventory.ClickType type,
            Player player) {
        if (!server) {
            super.clicked(id, button, type, player);
            return;
        }
        byHand(() -> super.clicked(id, button, type, player));
    }

    private void inOneGo(Runnable work) {
        access.execute((level, pos) -> {
            if (level.getBlockEntity(pos) instanceof CellaBlockEntity chest) {
                chest.inOneGo(work);
            } else {
                work.run();
            }
        });
    }

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
        return access.evaluate((level, pos) ->
                level.getBlockState(pos).getBlock() instanceof CellaBlock
                        && player.canInteractWithBlock(pos, 4.0), true);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!server) {
            return ItemStack.EMPTY;
        }
        ItemStack[] answer = new ItemStack[] { ItemStack.EMPTY };
        byHand(() -> answer[0] = moved(player, index));
        return answer[0];
    }

    private ItemStack moved(Player player, int index) {
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

        if (left.getCount() == stack.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.set(left);
        return before;
    }
}
