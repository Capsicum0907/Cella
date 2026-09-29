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
    public static final int SELECT = -6;

    private Player who;

    private int into = Into.NONE;

    public void into(int index) {
        into = index;
    }

    public int into() {
        return into;
    }

    private int shown = Peek.LIST;

    public void shows(int index) {
        shown = index;
    }

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
        this.who = inventory.player;
        this.server = !inventory.player.level().isClientSide;
        this.width = width(columns);
        int chestLeft = (width - columns * SLOT) / 2;
        int playerLeft = (width - CellaConfig.PLAYER_COLUMNS * SLOT) / 2;

        for (int index = 0; index < pageSize; index++) {
            addSlot(new PagedSlot(window, index,
                    chestLeft + (index % columns) * SLOT,
                    FIRST_Y + (index / columns) * SLOT, this::replaceable, () -> selecting));
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
                looks(value);
            }
        });

        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return selecting ? 1 : 0;
            }

            @Override
            public void set(int value) {
                selecting = value != 0;
                if (!selecting) {
                    selection.clear();
                }
            }
        });

        if (server) {
            access.execute((level, pos) -> {
                if (!(level.getBlockEntity(pos) instanceof CellaBlockEntity chest)) {
                    return;
                }
                if (chest.plan().split()) {
                    viewing = Peek.LIST;
                    window.limit(0, 0);
                } else {
                    viewing = 0;
                    int slots = chest.contents().getSlots();
                    int from = chest.plan().first(0, slots);
                    window.limit(from, chest.plan().past(0, slots) - from);
                }
            });
        } else {
            window.told(told, onPage);
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
                chest.release(this);
                chest.closed(player);
            }
        });
    }

    private CellaBlockEntity chest() {
        return access.evaluate((level, pos) ->
                level.getBlockEntity(pos) instanceof CellaBlockEntity chest ? chest : null)
                .orElse(null);
    }

    private int partOfShown(int real) {
        if (viewing >= 0) {
            return viewing;
        }
        return contents instanceof Sorted sorted ? sorted.partOf(real) : Plan.NONE;
    }

    private boolean replaceable(int shown, ItemStack incoming) {
        if (!server) {
            return true;
        }
        int real = window.real(shown);
        CellaBlockEntity chest = chest();
        if (real < 0 || chest == null) {
            return true;
        }
        ItemStack there = contents.getStackInSlot(real);
        if (there.isEmpty()) {
            return true;
        }
        int part = chest.contents().partOf(real);
        return chest.reserved(part, there) == 0 || chest.spare(part, there) >= there.getCount();
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
        quit();
        access.execute((level, pos) -> {
            if (!(level.getBlockEntity(pos) instanceof CellaBlockEntity chest)) {
                return;
            }
            int slots = chest.contents().getSlots();
            if (index == Peek.WHOLE) {
                viewing = Peek.WHOLE;
                window.everything(Math.min(slots, chest.plan().taken() * Plan.LC));
            } else if (index < 0 || index >= chest.plan().count(slots)) {
                viewing = Peek.LIST;
                window.limit(0, 0);
            } else {
                viewing = index;
                int from = chest.plan().first(index, slots);
                window.limit(from, chest.plan().past(index, slots) - from);
            }
            looks(viewing);
            seen = revision();
            sendAllDataToRemote();
        });
    }

    private boolean selecting;

    private final java.util.BitSet selection = new java.util.BitSet();

    private long plansAt;

    public boolean selecting() {
        return selecting;
    }

    public boolean selected(int shown) {
        return selecting && selection.get(shown);
    }

    public int selectedCount() {
        return selection.cardinality();
    }

    public void selectionFrom(java.util.List<Integer> indices) {
        selection.clear();
        indices.forEach(selection::set);
    }

    private void select() {
        CellaBlockEntity chest = chest();
        if (selecting || chest == null || !chest.divides()
                || (viewing != Peek.WHOLE && viewing < 0)) {
            return;
        }
        window.freeze();
        selection.clear();
        plansAt = chest.plans();
        selecting = true;
        tellSelection();
        sendAllDataToRemote();
    }

    public void quit() {
        if (!server || !selecting) {
            return;
        }
        selecting = false;
        selection.clear();
        CellaBlockEntity chest = chest();
        if (chest != null) {
            chest.release(this);
        }
        window.thaw();
        tellSelection();
        sendAllDataToRemote();
    }

    public void pick(int index, boolean toggle) {
        if (!server || !selecting) {
            return;
        }
        if (!window.frozenStack(index).isEmpty()) {
            if (selection.get(index)) {
                if (toggle) {
                    selection.clear(index);
                    claimSelection();
                }
            } else {
                selection.set(index);
                if (!claimSelection()) {
                    selection.clear(index);
                }
            }
        }
        tellSelection();
    }

    public void pickKind(int index) {
        if (!server || !selecting) {
            return;
        }
        ItemStack kind = window.frozenStack(index);
        if (kind.isEmpty()) {
            tellSelection();
            return;
        }
        java.util.List<Integer> alike = new java.util.ArrayList<>();
        boolean all = true;
        for (int at = 0; at < window.frozenSize(); at++) {
            if (ItemStack.isSameItemSameComponents(window.frozenStack(at), kind)) {
                alike.add(at);
                all &= selection.get(at);
            }
        }
        if (all) {
            alike.forEach(selection::clear);
            claimSelection();
        } else {
            for (int at : alike) {
                if (!selection.get(at)) {
                    selection.set(at);
                    if (!claimSelection()) {
                        selection.clear(at);
                    }
                }
            }
        }
        tellSelection();
    }

    public boolean move(int into) {
        CellaBlockEntity chest = chest();
        if (!server || !selecting || chest == null) {
            return false;
        }
        int slots = chest.contents().getSlots();
        if (into < 0 || into >= chest.plan().count(slots)) {
            return false;
        }
        java.util.List<Claim> moving = new java.util.ArrayList<>();
        for (Claim claim : selectionClaims()) {
            if (claim.part() != into) {
                moving.add(claim);
            }
        }
        for (Claim claim : moving) {
            if (chest.contents().count(claim.part(), claim.kind()) < claim.count()) {
                return false;
            }
        }
        java.util.List<ItemStack> kinds = new java.util.ArrayList<>();
        java.util.List<Integer> counts = new java.util.ArrayList<>();
        for (Claim claim : moving) {
            int at = -1;
            for (int one = 0; one < kinds.size(); one++) {
                if (ItemStack.isSameItemSameComponents(kinds.get(one), claim.kind())) {
                    at = one;
                }
            }
            if (at < 0) {
                kinds.add(claim.kind());
                counts.add(claim.count());
            } else {
                counts.set(at, counts.get(at) + claim.count());
            }
        }
        Space target = new Space(partition(into));
        if (!target.fits(kinds, counts)) {
            if (who != null) {
                who.sendSystemMessage(net.minecraft.network.chat.Component
                        .translatable("gui.cella.move.full"));
            }
            return false;
        }
        chest.release(this);
        chest.byHand(() -> chest.inOneGo(() -> {
            for (Claim claim : moving) {
                Space source = new Space(partition(claim.part()));
                int taken = source.takeOut(claim.kind(), claim.count());
                int put = target.put(claim.kind(), taken);
                if (put < taken) {
                    source.put(claim.kind(), taken - put);
                }
            }
        }));
        quit();
        return true;
    }

    public java.util.List<Claim> selectionClaims() {
        java.util.List<Claim> wanted = new java.util.ArrayList<>();
        if (!(contents instanceof Sorted sorted)) {
            return wanted;
        }
        for (int at = selection.nextSetBit(0); at >= 0; at = selection.nextSetBit(at + 1)) {
            ItemStack kind = window.frozenStack(at);
            int part = sorted.partOf(window.frozenAt(at));
            boolean merged = false;
            for (int one = 0; one < wanted.size(); one++) {
                Claim claim = wanted.get(one);
                if (claim.covers(part, kind)) {
                    wanted.set(one, new Claim(part, claim.kind(), claim.count() + kind.getCount()));
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                wanted.add(new Claim(part, kind.copyWithCount(1), kind.getCount()));
            }
        }
        return wanted;
    }

    private boolean claimSelection() {
        CellaBlockEntity chest = chest();
        return chest != null && chest.tryClaim(this, selectionClaims());
    }

    private void tellSelection() {
        if (who instanceof net.minecraft.server.level.ServerPlayer player) {
            java.util.List<Integer> indices = new java.util.ArrayList<>();
            selection.stream().forEach(indices::add);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new Picked(indices));
        }
    }

    private void looks(int value) {
        viewing = value;
        if (value != Peek.WHOLE) {
            into = Into.NONE;
        }
    }

    public void assign(int index) {
        if (!server) {
            return;
        }
        access.execute((level, pos) -> {
            if (level.getBlockEntity(pos) instanceof CellaBlockEntity chest) {
                chest.assign(index);
            }
        });
    }

    public void edit(Edit edit, net.minecraft.world.entity.player.Player who) {
        if (!server) {
            return;
        }
        access.execute((level, pos) -> {
            if (!(level.getBlockEntity(pos) instanceof CellaBlockEntity chest)) {
                return;
            }
            String trouble = null;
            if (edit.drop()) {
                if (!chest.undivide(edit.index())) {
                    trouble = "gui.cella.edit.full";
                }
            } else if (edit.index() == Edit.ADDING) {
                if (!chest.divide(edit.wanted())) {
                    trouble = "gui.cella.edit.nofit";
                }
            } else if (!chest.resize(edit.index(), edit.wanted())) {
                trouble = chest.contents().used(edit.index()) > edit.wanted().slots()
                        ? "gui.cella.edit.shrink"
                        : "gui.cella.edit.nofit";
            }
            if (trouble != null && who != null) {
                who.sendSystemMessage(net.minecraft.network.chat.Component
                        .translatable(trouble, edit.wanted().length()));
            }
            view(Peek.LIST);
        });
    }

    private boolean picked() {
        return access.evaluate((level, pos) ->
                level.getBlockEntity(pos) instanceof CellaBlockEntity chest
                        && into >= 0 && into < chest.plan().count(chest.contents().getSlots()))
                .orElse(false);
    }

    private IItemHandlerModifiable partition(int index) {
        return access.evaluate((level, pos) -> {
            if (!(level.getBlockEntity(pos) instanceof CellaBlockEntity chest)) {
                return null;
            }
            int slots = chest.contents().getSlots();
            if (index < 0 || index >= chest.plan().count(slots)) {
                return null;
            }
            int from = chest.plan().first(index, slots);
            return (IItemHandlerModifiable)
                    new Outlet(chest.contents(), from, chest.plan().past(index, slots) - from);
        }).orElse(null);
    }

    private IItemHandlerModifiable storing() {
        return viewing == Peek.WHOLE ? partition(into) : partition(viewing);
    }

    private IItemHandlerModifiable taking() {
        return viewing == Peek.WHOLE ? contents : partition(viewing);
    }

    private ItemStack poured(ItemStack stack) {
        IItemHandlerModifiable where = storing();
        if (where == null || stack.isEmpty()) {
            return stack;
        }
        int many = Math.min(stack.getCount(), new Space(where).room(stack));
        if (many <= 0) {
            return stack;
        }
        ItemStack over = ItemHandlerHelper.insertItemStacked(where, stack.copyWithCount(many), false);
        int left = stack.getCount() - (many - over.getCount());
        return left > 0 ? stack.copyWithCount(left) : ItemStack.EMPTY;
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
        if (!server || selecting) {
            return;
        }
        if (viewing == Peek.LIST && !looking.isBlank()) {
            view(Peek.WHOLE);
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
        if (server) {
            CellaBlockEntity chest = chest();
            if (selecting && chest != null && chest.plans() != plansAt) {
                quit();
            }
            follow();
        }
        super.broadcastChanges();
        if (server) {
            mark();
        }
    }

    private java.util.List<Integer> marked = java.util.List.of();

    private void mark() {
        java.util.List<Integer> now = access.evaluate((level, pos) ->
                level.getBlockEntity(pos) instanceof CellaBlockEntity chest
                        ? owners(chest)
                        : java.util.List.<Integer>of()).orElse(java.util.List.of());
        if (now.equals(marked)) {
            return;
        }
        marked = now;
        if (who instanceof net.minecraft.server.level.ServerPlayer player) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new Owners(now));
        }
    }

    public java.util.List<Integer> owners(CellaBlockEntity chest) {
        if (viewing != Peek.WHOLE) {
            return java.util.List.of();
        }
        int slots = chest.contents().getSlots();
        java.util.List<Plan.Partition> carved = chest.plan().over(slots);
        int[] ends = new int[carved.size()];
        int end = 0;
        for (int part = 0; part < ends.length; part++) {
            end = Math.min(slots, end + carved.get(part).slots());
            ends[part] = end;
        }
        java.util.List<Integer> out = new java.util.ArrayList<>(pageSize);
        for (int shown = 0; shown < pageSize; shown++) {
            int real = window.real(shown);
            int part = real < 0 ? ends.length : after(ends, real);
            out.add(part < ends.length ? part : Plan.NONE);
        }
        return out;
    }

    private static int after(int[] ends, int slot) {
        int low = 0;
        int high = ends.length;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (ends[middle] <= slot) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private java.util.List<Integer> owning = java.util.List.of();

    public void owned(java.util.List<Integer> parts) {
        owning = parts;
    }

    public int owner(int shown) {
        if (viewing >= 0) {
            return viewing;
        }
        return viewing == Peek.WHOLE && shown >= 0 && shown < owning.size()
                ? owning.get(shown)
                : Plan.NONE;
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
            if (window.searching()) {
                boolean moved = false;
                if (since == null || window.whole()) {
                    moved = window.again();
                } else {
                    for (int slot : since) {
                        moved |= window.changed(slot);
                    }
                }
                if (moved) {
                    sendAllDataToRemote();
                }
            }
            tell(chest);
        });
    }

    private void tell(CellaBlockEntity chest) {
        if (who instanceof net.minecraft.server.level.ServerPlayer player) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    Shelf.of(chest, shown));
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= 0) {
            turnTo(id);
            return true;
        }
        if (id == SELECT) {
            if (server) {
                if (selecting) {
                    quit();
                } else {
                    select();
                }
            }
            return true;
        }
        if (selecting) {
            return true;
        }
        if (id < TAKING) {
            return false;
        }
        if (viewing == Peek.LIST && id != SORT) {
            return true;
        }
        if ((id == STOW || id == MATCHING)
                && (viewing == Peek.LIST || (viewing == Peek.WHOLE && !picked()))) {
            return true;
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
        IItemHandlerModifiable from = taking();
        if (from == null) {
            return;
        }
        Inventory inventory = player.getInventory();
        Set<ItemStack> carried = matchingOnly ? carried(inventory) : Set.of();
        Space pockets = new Space(
                new net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper(inventory));
        CellaBlockEntity chest = chest();
        java.util.List<Claim> held = chest == null ? java.util.List.of() : chest.claimed();
        int[] spare = new int[held.size()];
        for (int at = 0; at < held.size(); at++) {
            spare[at] = chest.spare(held.get(at).part(), held.get(at).kind());
        }

        for (int slot = 0; slot < from.getSlots() && !pockets.full(); slot++) {
            ItemStack stack = from.getStackInSlot(slot);
            if (stack.isEmpty() || (matchingOnly && !carried.contains(stack))) {
                continue;
            }
            int many = Math.min(stack.getCount(), pockets.room(stack));
            int capped = -1;
            int part = partOfShown(slot);
            for (int at = 0; at < held.size(); at++) {
                if (held.get(at).covers(part, stack)) {
                    capped = at;
                    many = Math.min(many, spare[at]);
                    break;
                }
            }
            if (many <= 0) {
                continue;
            }
            int moved = pockets.put(stack, many);
            if (capped >= 0) {
                spare[capped] -= moved;
            }
            int left = stack.getCount() - moved;
            from.setStackInSlot(slot, left > 0 ? stack.copyWithCount(left) : ItemStack.EMPTY);
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
        if (selecting && ((id >= 0 && id < pageSize)
                || type == net.minecraft.world.inventory.ClickType.QUICK_MOVE
                || type == net.minecraft.world.inventory.ClickType.PICKUP_ALL)) {
            return;
        }
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
            inventory.setItem(slot, poured(stack));
        }
        inventory.setChanged();
    }

    private Set<ItemStack> kinds() {
        Set<ItemStack> kept = ItemStackLinkedSet.createTypeAndComponentsSet();
        IItemHandlerModifiable held = taking();
        for (int slot = 0; held != null && slot < held.getSlots(); slot++) {
            ItemStack stack = held.getStackInSlot(slot);
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
        if (selecting) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem() || viewing == Peek.LIST) {
            return ItemStack.EMPTY;
        }
        if (index >= pageSize && viewing == Peek.WHOLE && !picked()) {
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

        ItemStack left = poured(stack);

        if (left.getCount() == stack.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.set(left);
        return before;
    }
}
