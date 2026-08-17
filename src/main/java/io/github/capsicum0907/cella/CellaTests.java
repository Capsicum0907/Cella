package io.github.capsicum0907.cella;

import io.github.capsicum0907.cella.data.TestStructures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * What the paging is supposed to be, checked where it could go wrong: at the edge
 * between the window and the contents underneath it.
 *
 * <p>Run with {@code gradlew runGameTestServer}.
 */
@GameTestHolder(Cella.MODID)
@PrefixGameTestTemplate(false)
public final class CellaTests {
    private static final BlockPos WHERE = new BlockPos(2, 1, 2);

    /** The player's own three rows and hotbar, which every container menu ends with. */
    private static final int PLAYER_SLOTS = 36;

    /**
     * The kind these tests are written about.
     *
     * <p>Named rather than taken as the first of them: several of these need a chest with
     * pages to spare, and the first is the smallest form there is. Naming it also means
     * adding a kind cannot quietly change what is being tested.
     */
    private static final Kind KIND = Kind.PERFECT;

    private CellaTests() {
    }

    /** A page other than the first, so nothing passes by looking at page nought. */
    private static final int LATER = 2;

    /**
     * The menu is one page tall, whatever the chest is.
     *
     * <p>This is what an open screen costs, and it is the reason for the whole rewrite: a
     * menu that held every slot sent every slot on opening and compared every slot each
     * tick, which is fine at four hundred and is not at thirteen thousand.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void theMenuIsOnePageOfTheChest(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        CellaMenu menu = CellaMenu.at(1, player.getInventory(), helper.absolutePos(WHERE),
                chest.contents().getSlots(),
                CellaConfig.rows(KIND), CellaConfig.columns(KIND));

        check(menu.slots.size() == KIND.pageSize() + PLAYER_SLOTS,
                "the menu should be a page and the player: " + menu.slots.size());
        check(menu.pages() == KIND.pages(),
                "and know how many pages there are behind it");
        helper.succeed();
    }

    /**
     * Turning a page changes what the slots hold, and nothing is renumbered.
     *
     * <p>The window moves and the slots do not know it happened — which is the point, and
     * is also the one thing the game is entitled to be surprised by. It works out what to
     * send by comparing each slot against what it last told the client that slot held, and
     * that reasoning is only sound while nothing moves underneath a slot. So a page turn
     * is followed by {@code sendAllDataToRemote}; see {@link CellaMenu#turnTo}.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void turningThePageMovesWhatTheSlotsShow(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        int page = KIND.pageSize();
        chest.contents().setStackInSlot(4, new ItemStack(Items.DIAMOND, 5));
        chest.contents().setStackInSlot(LATER * page + 4, new ItemStack(Items.EMERALD, 7));

        CellaMenu menu = CellaMenu.at(1, player.getInventory(), helper.absolutePos(WHERE),
                chest.contents().getSlots(),
                CellaConfig.rows(KIND), CellaConfig.columns(KIND));

        check(menu.slots.get(4).getItem().is(Items.DIAMOND), "page one holds the diamonds");
        menu.turnTo(LATER);
        check(menu.slots.get(4).getItem().getCount() == 7,
                "and the same slot holds the emeralds once the page has turned");

        // Writing through the slot has to land on the page being shown, not the first.
        menu.slots.get(5).set(new ItemStack(Items.COAL, 3));
        check(chest.contents().getStackInSlot(LATER * page + 5).is(Items.COAL),
                "and what is put in should land on the page that is open");
        check(chest.contents().getStackInSlot(5).isEmpty(), "not on the one that is not");
        helper.succeed();
    }

    /**
     * A page that is not there is refused rather than clamped.
     *
     * <p>The page is asked for over the wire now, so what arrives is whatever was sent -
     * a number the server has no reason to trust. Being moved somewhere you did not ask
     * for is worse than not moving, and being moved somewhere that does not exist is a
     * window off the end of the chest.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void thereIsNoPageAfterTheLast(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CellaMenu menu = CellaMenu.at(1, player.getInventory(), helper.absolutePos(WHERE),
                chest.contents().getSlots(),
                CellaConfig.rows(KIND), CellaConfig.columns(KIND));

        check(menu.pages() == KIND.pages(),
                "a new chest should have as many pages as the config says");
        menu.clickMenuButton(player, menu.pages());
        check(menu.page() == 0, "a page past the end should be ignored");
        menu.clickMenuButton(player, Integer.MAX_VALUE);
        check(menu.page() == 0, "and so should a number that is not a page at all");
        menu.turnTo(-1);
        check(menu.page() == 0, "and so should one before the start");
        helper.succeed();
    }

    /**
     * The server turns the page, because the server is what a click is read against.
     *
     * <p>A page arrives as a button, as itself: the five real buttons are negative so the
     * two can never collide however many pages a chest grows to. The client asks and
     * waits, so this is the only side that turns anything.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void theServerTurnsThePage(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CellaMenu menu = CellaMenu.at(1, player.getInventory(), helper.absolutePos(WHERE),
                chest.contents().getSlots(),
                CellaConfig.rows(KIND), CellaConfig.columns(KIND));

        check(menu.clickMenuButton(player, LATER), "a page should be a button it takes");
        check(menu.page() == LATER, "and it should have turned to it");
        helper.succeed();
    }

    /**
     * The client's window has the page and not the chest, and still knows where it stops.
     *
     * <p>There is no client in a game test, so this asks the window directly — which is
     * where the difference lives and the only place it does. The client is sent one page
     * at a time and is told how many slots the chest has; from those two it can say how
     * many pages there are and which squares of the last one are real, without holding
     * two hundred thousand slots of nothing.
     *
     * <p><b>Writing goes through and showing does not.</b> A page turn arrives as the
     * contents first and the page number second, so for that moment the window is
     * answering about the page before — and a full page arriving while it still thinks it
     * is on a short one must not be dropped on the floor.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void theClientsWindowHoldsOnlyThePage(GameTestHelper helper) {
        int size = KIND.pageSize();
        int total = size * 2 + 5;
        Window window = Window.of(new ItemStackHandler(size), size, total);

        check(window.getSlots() == size, "the client holds a page: " + window.getSlots());
        check(window.pages() == 3, "and knows there are three of them: " + window.pages());

        window.openAt(2);
        check(window.holds(4), "the five that are there are the chest's");
        check(!window.holds(5), "and the rest of the grid is not");
        check(window.insertItem(5, new ItemStack(Items.STONE, 1), false).getCount() == 1,
                "nothing off the end takes an item");

        // What the server sends still lands, whichever page this thinks it is on.
        window.setStackInSlot(5, new ItemStack(Items.STONE, 1));
        window.openAt(0);
        check(window.getStackInSlot(5).is(Items.STONE),
                "and the contents of a full page are not dropped for arriving first");
        helper.succeed();
    }

    /**
     * The menu counts its pages from the chest, not from the config.
     *
     * <p>A chest keeps the size it was built with, so the two disagree in any world whose
     * config has been turned down since. The client cannot work the real size out — its
     * copy of the block entity was made at the config's size — which is why the number
     * travels in the packet that opens the screen: a client that thinks there are fewer
     * pages than there are cannot ask for the last one, and one that thinks there are
     * more can ask for a page the server refuses and then sit waiting for the answer.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void theMenuCountsItsPagesFromTheChest(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        // A chest from a world whose config said something else.
        int odd = KIND.pageSize() * 3;
        chest.contents().setSize(odd);

        CellaMenu menu = CellaMenu.at(1, player.getInventory(), helper.absolutePos(WHERE), odd,
                CellaConfig.rows(KIND), CellaConfig.columns(KIND));
        check(menu.slots.size() == KIND.pageSize() + PLAYER_SLOTS,
                "the menu is still a page tall: " + menu.slots.size());
        check(menu.pages() == 3, "and counted its pages from the chest, not from the config");
        helper.succeed();
    }

    /**
     * The last page may be short, and what is off the end takes nothing.
     *
     * <p>A chest is the size it was built at and that size need not divide by a page. The
     * leftover squares are drawn as a grid because a grid with a bite out of it is worse,
     * but they are not slots of anything — and being merely hidden is not enough, because
     * hidden is a fact about this screen and every other mod is entitled to ignore it. So
     * the refusal is underneath, in the window.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void theShortLastPageTakesNothing(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        int page = KIND.pageSize();

        int odd = page * 2 + 5;
        chest.contents().setSize(odd);

        CellaMenu menu = CellaMenu.at(1, player.getInventory(), helper.absolutePos(WHERE), odd,
                CellaConfig.rows(KIND), CellaConfig.columns(KIND));
        check(menu.pages() == 3, "five slots over is still a page of its own");
        menu.turnTo(2);

        check(menu.slots.get(4).isActive(), "the five that are there should be shown");
        check(!menu.slots.get(5).isActive(), "and the rest of the grid should not");
        check(menu.slots.get(5).getItem().isEmpty(), "there is nothing off the end");
        check(!menu.slots.get(5).mayPlace(new ItemStack(Items.STONE)),
                "and nothing off the end will take an item");
        check(menu.slots.get(5).getMaxStackSize() == 0, "because there is no room there");
        helper.succeed();
    }

    /**
     * A hopper sees every page, not the one somebody has open.
     *
     * <p>The capability is the contents themselves, so this asks how many slots the
     * block offers and expects all of them.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void aHopperSeesTheWholeChest(GameTestHelper helper) {
        place(helper);
        IItemHandler offered = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                helper.absolutePos(WHERE), null);

        check(offered != null, "the chest should offer an item handler");
        check(offered.getSlots() == KIND.slots(),
                "and it should be every page, not one: " + offered.getSlots());

        // Reaching past the first page has to work, not merely be counted.
        int far = KIND.pageSize() * (KIND.pages() - 1);
        offered.insertItem(far, new ItemStack(Items.REDSTONE, 7), false);
        check(offered.getStackInSlot(far).getCount() == 7, "and the last page should take items");
        helper.succeed();
    }

    /**
     * Breaking one that keeps: nothing on the floor but the chest, and it has a name.
     *
     * <p>Everything from Imperfect up survives being broken. The contents go to
     * {@link Kept} and the item carries the name of what was filed - not the contents
     * themselves, for the three reasons on that class.
     *
     * <p>The gold is on a later page, because a design that only kept the first one would
     * pass anything simpler than this.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void breakingOneThatKeepsGivesItAName(GameTestHelper helper) {
        check(KIND.keeps(), "this test is about a kind that keeps");
        CellaBlockEntity chest = place(helper, KIND);
        chest.contents().setStackInSlot(LATER * KIND.pageSize(),
                new ItemStack(Items.GOLD_INGOT, 11));

        helper.destroyBlock(WHERE);

        helper.succeedWhen(() -> {
            java.util.List<ItemStack> dropped = dropped(helper);
            check(dropped.stream().noneMatch(stack -> stack.is(Items.GOLD_INGOT)),
                    "the gold should not be on the floor");
            java.util.List<ItemStack> chests = dropped.stream()
                    .filter(stack -> stack.is(CellaRegistry.item(KIND).get()))
                    .toList();
            check(chests.size() == 1, "exactly one chest should have dropped: " + chests.size());
            check(chests.get(0).has(CellaRegistry.KEPT.get()),
                    "and it should carry the name of what it kept");
            check(chests.get(0).getMaxStackSize() == 1, "and a full one should not stack");
        });
    }

    /**
     * Laravel spills, because Laravel is the one whose contents can be picked back up.
     *
     * <p>Fifty-four slots is a stack and a half of trips for a player with thirty-six of
     * them, against a five-minute despawn. Semi-Perfect's 1,728 would be forty-eight,
     * which is not a mess to clear up - it is the contents being destroyed by the act of
     * moving the chest. That is the whole reason the line is drawn where it is.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void theLarvaSpillsBecauseItCanBePickedUp(GameTestHelper helper) {
        check(!Kind.LARAVEL.keeps(), "this test is about the one that does not keep");
        CellaBlockEntity chest = place(helper, Kind.LARAVEL);
        chest.contents().setStackInSlot(3, new ItemStack(Items.GOLD_INGOT, 11));

        helper.destroyBlock(WHERE);

        helper.succeedWhen(() -> {
            java.util.List<ItemStack> dropped = dropped(helper);
            long gold = dropped.stream().filter(stack -> stack.is(Items.GOLD_INGOT))
                    .mapToLong(ItemStack::getCount).sum();
            check(gold == 11, "the eleven gold should be on the floor, not " + gold);
            check(dropped.stream().filter(stack -> stack.is(CellaRegistry.item(Kind.LARAVEL).get()))
                            .noneMatch(stack -> stack.has(CellaRegistry.KEPT.get())),
                    "and the chest should not be carrying a name it never filed");
        });
    }

    /**
     * Put down again, a named chest is the chest that was filed - and the name is spent.
     *
     * <p>Spent in the taking, so a second item naming the same chest puts down an empty
     * one. Two items naming one chest are two views of one chest, and only the first of
     * them can be looking at anything.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void aNamedChestComesBackAndTheNameIsSpent(GameTestHelper helper) {
        Kept kept = Kept.of(helper.getLevel()).orElseThrow(
                () -> new GameTestAssertException("there should be a store on a server"));

        ItemStackHandler filed = new ItemStackHandler(KIND.slots());
        filed.setStackInSlot(LATER * KIND.pageSize(), new ItemStack(Items.GOLD_INGOT, 11));
        java.util.UUID name = kept.put(
                filed.serializeNBT(helper.getLevel().registryAccess()),
                KIND, helper.getLevel().getGameTime(), 0);

        ItemStack stack = new ItemStack(CellaRegistry.item(KIND).get());
        stack.set(CellaRegistry.KEPT.get(),
                new Held(java.util.List.of(name), 1, KIND.slots(), 0, KIND.growth()));
        check(stack.getMaxStackSize() == 1, "a named chest should not stack");

        CellaBlockEntity chest = place(helper, KIND);
        CellaRegistry.block(KIND).get().setPlacedBy(helper.getLevel(),
                helper.absolutePos(WHERE), chest.getBlockState(), null, stack);

        check(chest.contents().getStackInSlot(LATER * KIND.pageSize()).getCount() == 11,
                "the gold should be back, on the page it was on");
        check(kept.take(name).isEmpty(), "and the name should have been spent");
        helper.succeed();
    }

    /**
     * What a screen can show only ever cuts a page down, never stretches it.
     *
     * <p>The page shape is the kind's - Laravel is six by nine because a Laravel should
     * look like a chest - and {@link Room} is a ceiling on it. A player with a wall for a
     * monitor gets what the chest was designed to show; a player with the window this
     * game opens in gets fewer rows of it and more pages.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void aScreenCutsThePageDownAndNeverUp(GameTestHelper helper) {
        Room small = new Room(6, 9);
        check(small.rowsFor(KIND) == 6, "a short screen should get six rows of a Perfect");
        check(small.columnsFor(KIND) == 9, "and nine columns");

        Room wall = new Room(64, 64);
        check(wall.rowsFor(KIND) == CellaConfig.rows(KIND),
                "a big screen should get the kind's own shape");
        check(wall.columnsFor(KIND) == CellaConfig.columns(KIND), "in both directions");
        check(wall.rowsFor(Kind.LARAVEL) == CellaConfig.rows(Kind.LARAVEL),
                "and a small chest should not be stretched to fill it");

        Player nobody = helper.makeMockPlayer(GameType.SURVIVAL);
        check(Room.of(nobody).equals(Room.ANY),
                "a player who has not said anything should have nothing cut down");
        helper.succeed();
    }

    /**
     * The panel's height and the rows that fit in it are the same arithmetic.
     *
     * <p>Asked both ways round by two different places - the screen sizes a panel from
     * its rows, the client counts rows into a window - so this is the property that stops
     * them being two rectangles.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void thePanelMeasuresTheSameBothWays(GameTestHelper helper) {
        for (int rows = 1; rows <= 20; rows++) {
            check(CellaMenu.rowsIn(CellaMenu.height(rows)) == rows,
                    rows + " rows should measure back to " + rows);
        }
        for (int columns = CellaConfig.PLAYER_COLUMNS; columns <= 20; columns++) {
            check(CellaMenu.columnsIn(CellaMenu.width(columns)) == columns,
                    columns + " columns should measure back to " + columns);
        }
        helper.succeed();
    }

    /**
     * The item says how full the chest it is carrying was, and that cannot go stale.
     *
     * <p>The client is never told what is inside, so anything shown about a chest in a
     * hand has to travel on the item. It is exact rather than an estimate: filed contents
     * are reachable by one operation, which hands the whole chest back, so nothing can
     * put an item into a chest that is in somebody's pocket.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void theItemSaysHowFullTheChestWas(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper, KIND);
        for (int slot = 0; slot < 300; slot++) {
            chest.contents().setStackInSlot(slot, new ItemStack(Items.STONE, 1));
        }
        check(chest.used() == 300, "three hundred slots should be spoken for");

        helper.destroyBlock(WHERE);

        helper.succeedWhen(() -> {
            Held held = dropped(helper).stream()
                    .filter(stack -> stack.is(CellaRegistry.item(KIND).get()))
                    .map(stack -> stack.get(CellaRegistry.KEPT.get()))
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElseThrow(() -> new GameTestAssertException("no chest with a name"));
            check(held.used() == 300, "the item should say three hundred: " + held.used());
            check(held.slots() == KIND.slots(),
                    "and how many there were altogether: " + held.slots());
            check(held.counted(), "which is enough to draw a bar from");
        });
    }

    /**
     * A fusion carries the contents of everything it ate into what it made.
     *
     * <p>Names and counts only — nothing is filed or opened while a bench is being looked
     * at, because {@code assemble} runs every time the ingredients sit there. The pouring
     * is at placement.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void aFusionCarriesWhatItAte(GameTestHelper helper) {
        Kept kept = Kept.of(helper.getLevel()).orElseThrow(
                () -> new GameTestAssertException("there should be a store on a server"));
        var recipes = helper.getLevel().getServer().getRecipeManager();
        var semiPerfect = recipes
                .byKey(ResourceLocation.fromNamespaceAndPath(Cella.MODID, "semi_perfect"))
                .orElseThrow(() -> new GameTestAssertException("no semi_perfect recipe"));
        check(semiPerfect.value() instanceof Fusing, "a fusion, since it eats Cellas");
        Fusing fusion = (Fusing) semiPerfect.value();

        // Eight Imperfects round obsidian, two of them with something in them.
        java.util.List<ItemStack> grid = new java.util.ArrayList<>();
        for (int at = 0; at < 9; at++) {
            grid.add(at == 4
                    ? new ItemStack(Items.OBSIDIAN)
                    : new ItemStack(CellaRegistry.item(Kind.IMPERFECT).get()));
        }
        java.util.List<java.util.UUID> filed = new java.util.ArrayList<>();
        for (int at : new int[] { 0, 8 }) {
            ItemStackHandler one = new ItemStackHandler(Kind.IMPERFECT.slots());
            one.setStackInSlot(at, new ItemStack(Items.GOLD_INGOT, 7));
            java.util.UUID name = kept.put(
                    one.serializeNBT(helper.getLevel().registryAccess()),
                    Kind.IMPERFECT, helper.getLevel().getGameTime(), 0);
            filed.add(name);
            grid.get(at).set(CellaRegistry.KEPT.get(),
                    new Held(java.util.List.of(name), 1, Kind.IMPERFECT.slots(), 0, 0));
        }

        CraftingInput bench = CraftingInput.of(3, 3, grid);
        check(fusion.matches(bench, helper.getLevel()), "it should be a recipe");

        ItemStack made = fusion.assemble(bench, helper.getLevel().registryAccess());
        Held held = made.get(CellaRegistry.KEPT.get());
        check(held != null, "the result should carry what it ate");
        check(held.chests().equals(filed),
                "both names, in the order they were laid out: " + held.chests());
        check(held.used() == 2, "two slots between them: " + held.used());
        check(held.slots() == Kind.SEMI_PERFECT.slots(),
                "in the room a Semi-Perfect has: " + held.slots());

        // And nothing was taken out of the store by looking at the bench.
        check(kept.take(filed.getFirst()).isPresent(), "assembling must not have spent them");
        kept.take(filed.getLast());
        helper.succeed();
    }

    /**
     * A fusion that would not fit is not a recipe at all.
     *
     * <p>Prevention, not handling. The alternative is to make the chest and put the
     * remainder somewhere, and the only somewhere is the floor — thousands of item
     * entities on one block, landing on whoever filled their chests. With capacity fixed
     * this cannot arise; a world holding chests built to older numbers still can.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void aFusionThatWouldNotFitIsNotARecipe(GameTestHelper helper) {
        var recipes = helper.getLevel().getServer().getRecipeManager();
        var semiPerfect = recipes
                .byKey(ResourceLocation.fromNamespaceAndPath(Cella.MODID, "semi_perfect"))
                .orElseThrow(() -> new GameTestAssertException("no semi_perfect recipe"));
        Fusing fusion = (Fusing) semiPerfect.value();

        java.util.List<ItemStack> grid = new java.util.ArrayList<>();
        for (int at = 0; at < 9; at++) {
            grid.add(at == 4
                    ? new ItemStack(Items.OBSIDIAN)
                    : new ItemStack(CellaRegistry.item(Kind.IMPERFECT).get()));
        }
        check(fusion.matches(CraftingInput.of(3, 3, grid), helper.getLevel()),
                "eight empty ones are a recipe");

        // One of them is fuller than the whole Semi-Perfect it would go into.
        grid.get(0).set(CellaRegistry.KEPT.get(), new Held(
                java.util.List.of(java.util.UUID.randomUUID()),
                Kind.SEMI_PERFECT.slots() + 1, Kind.SEMI_PERFECT.slots() + 1, 0, 0));
        check(!fusion.matches(CraftingInput.of(3, 3, grid), helper.getLevel()),
                "and one that would not fit is not");
        helper.succeed();
    }

    /**
     * Pouring closes up the gaps, and stops at the end rather than overwriting.
     *
     * <p>Eight chests a tenth full become one chest a tenth full with everything at the
     * front. Any other arrangement is a fiction after eight were merged.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void pouringClosesUpTheGaps(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper, KIND);
        var registries = helper.getLevel().registryAccess();

        java.util.List<Kept.Chest> filed = new java.util.ArrayList<>();
        for (int which = 0; which < 2; which++) {
            ItemStackHandler one = new ItemStackHandler(Kind.SEMI_PERFECT.slots());
            one.setStackInSlot(100, new ItemStack(Items.GOLD_INGOT, which + 1));
            one.setStackInSlot(900, new ItemStack(Items.DIAMOND, which + 1));
            filed.add(new Kept.Chest(one.serializeNBT(registries), 0));
        }

        check(chest.pour(registries, filed).isEmpty(), "all of it should fit");
        check(chest.contents().getStackInSlot(0).is(Items.GOLD_INGOT), "first in, first slot");
        check(chest.contents().getStackInSlot(1).is(Items.DIAMOND), "then the rest of it");
        check(chest.contents().getStackInSlot(2).getCount() == 2, "then the second chest's");
        check(chest.contents().getStackInSlot(4).isEmpty(), "and nothing after them");
        check(chest.used() == 4, "four slots spoken for: " + chest.used());
        helper.succeed();
    }

    /**
     * Spawning leaves the parent's contents with the parent, and gives the children none.
     *
     * <p>The half that matters since fusions exist. Cella Jr. is the one recipe that eats
     * a Cella and does <em>not</em> carry anything, because nothing was eaten — the
     * Perfect is still standing there. So its name has to come back on it, and it must
     * not come back on the seven as well: seven items naming one chest would be seven
     * players racing to place the first, and six empty chests.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void spawningLeavesTheContentsWithTheParent(GameTestHelper helper) {
        var recipes = helper.getLevel().getServer().getRecipeManager();
        var junior = recipes.byKey(ResourceLocation.fromNamespaceAndPath(Cella.MODID, "junior"))
                .orElseThrow(() -> new GameTestAssertException("no junior recipe"));
        check(junior.value() instanceof Spawning, "a spawning, not a fusion");
        Spawning spawning = (Spawning) junior.value();

        Held named = new Held(java.util.List.of(java.util.UUID.randomUUID()), 5,
                Kind.PERFECT.slots(), 0, Kind.PERFECT.growth());
        ItemStack perfect = new ItemStack(CellaRegistry.item(Kind.PERFECT).get());
        perfect.set(CellaRegistry.KEPT.get(), named);

        ItemStack diamond = new ItemStack(Items.DIAMOND_BLOCK);
        CraftingInput bench = CraftingInput.of(3, 3, java.util.List.of(
                diamond, diamond, diamond,
                diamond, perfect, diamond,
                diamond, diamond, diamond));

        check(spawning.matches(bench, helper.getLevel()), "it should be a recipe");

        ItemStack back = spawning.getRemainingItems(bench).get(4);
        check(back.is(CellaRegistry.item(Kind.PERFECT).get()), "the Perfect should come back");
        check(named.equals(back.get(CellaRegistry.KEPT.get())),
                "still naming what it was carrying");

        ItemStack made = spawning.assemble(bench, helper.getLevel().registryAccess());
        check(made.getCount() == 7, "seven at a time: " + made.getCount());
        check(!made.has(CellaRegistry.KEPT.get()),
                "and none of them naming the chest that spawned them");
        check(made.getMaxStackSize() > 1, "so they stack, being empty");
        helper.succeed();
    }

    private static java.util.List<ItemStack> dropped(GameTestHelper helper) {
        return helper.getEntities(net.minecraft.world.entity.EntityType.ITEM).stream()
                .map(entity -> ((net.minecraft.world.entity.item.ItemEntity) entity).getItem())
                .toList();
    }

    /**
     * Shift-clicking fills the chest, not the page on screen.
     *
     * <p>Page one is filled first so that the only room left is somewhere the player
     * cannot see. An item that comes back to the player's hand instead of going in is
     * the failure this is looking for.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void shiftClickReachesPastThePage(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        int page = KIND.pageSize();
        for (int slot = 0; slot < page; slot++) {
            chest.contents().setStackInSlot(slot, new ItemStack(Items.STONE, 64));
        }

        CellaMenu menu = CellaMenu.at(1, player.getInventory(), helper.absolutePos(WHERE),
                chest.contents().getSlots(),
                CellaConfig.rows(KIND), CellaConfig.columns(KIND));
        int hand = menu.slots.size() - CellaConfig.PLAYER_COLUMNS;
        menu.slots.get(hand).set(new ItemStack(Items.COAL, 32));
        menu.quickMoveStack(player, hand);

        check(menu.slots.get(hand).getItem().isEmpty(), "the coal should have left the player");
        check(chest.contents().getStackInSlot(page).getCount() == 32,
                "and landed on the first slot with room, which is on page two");
        helper.succeed();
    }

    /**
     * Sorting reaches every page, which is the thing a sorting mod cannot do.
     *
     * <p>The setup is the case that made it worth having: partial stacks of one item
     * scattered across pages that are never open at the same time. Nothing outside can
     * put them together, because nothing outside can see past the window.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void sortingReachesEveryPage(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        int page = KIND.pageSize();

        // Seventy in three pieces on three pages, so merging leaves a remainder to
        // place as well as a full stack - the two cases that can differ.
        chest.contents().setStackInSlot(3, new ItemStack(Items.COBBLESTONE, 20));
        chest.contents().setStackInSlot(page + 7, new ItemStack(Items.COBBLESTONE, 30));
        chest.contents().setStackInSlot(page * 4 + 1, new ItemStack(Items.COBBLESTONE, 20));
        chest.contents().setStackInSlot(page * 2 + 5, new ItemStack(Items.DIAMOND, 9));

        Tidy.everything(chest.contents());

        // 64 of cobblestone, then the remainder, then the diamonds - and nothing left
        // anywhere else.
        check(chest.contents().getStackInSlot(0).is(Items.COBBLESTONE)
                        && chest.contents().getStackInSlot(0).getCount() == 64,
                "the first slot should be a full stack of cobblestone");
        check(chest.contents().getStackInSlot(1).is(Items.COBBLESTONE)
                        && chest.contents().getStackInSlot(1).getCount() == 6,
                "the second should be the six left over");
        check(chest.contents().getStackInSlot(2).is(Items.DIAMOND)
                        && chest.contents().getStackInSlot(2).getCount() == 9,
                "and the diamonds should follow, cobblestone sorting before diamond");
        for (int slot = 3; slot < chest.contents().getSlots(); slot++) {
            check(chest.contents().getStackInSlot(slot).isEmpty(),
                    "slot " + slot + " should have been emptied into the front");
        }
        helper.succeed();
    }

    /**
     * Stowing empties the player into the chest, past the page on screen.
     *
     * <p>Page one is filled first so the only room is out of sight — the case a sorting
     * mod's version of this button cannot do, which is why there is one here.
     *
     * <p>What is in the player's hand stays there. A button meant to save time that
     * takes the tool you are holding costs more than it saves.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void stowingReachesPastThePage(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        int page = KIND.pageSize();
        for (int slot = 0; slot < page; slot++) {
            chest.contents().setStackInSlot(slot, new ItemStack(Items.STONE, 64));
        }

        player.getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
        player.getInventory().selected = 0;
        player.getInventory().setItem(1, new ItemStack(Items.APPLE, 12));
        player.getInventory().setItem(20, new ItemStack(Items.BONE, 5));

        CellaMenu menu = CellaMenu.at(1, player.getInventory(), helper.absolutePos(WHERE),
                chest.contents().getSlots(),
                CellaConfig.rows(KIND), CellaConfig.columns(KIND));
        menu.clickMenuButton(player, CellaMenu.STOW);

        check(player.getInventory().getItem(0).is(Items.IRON_PICKAXE),
                "the pickaxe in hand should have been left alone");
        check(player.getInventory().getItem(1).isEmpty() && player.getInventory().getItem(20).isEmpty(),
                "everything else should have gone in");
        check(chest.contents().getStackInSlot(page).is(Items.APPLE)
                        && chest.contents().getStackInSlot(page + 1).is(Items.BONE),
                "and landed on page two, which is where the room was");
        helper.succeed();
    }

    /**
     * Taking reaches past the page too, and stops when the player is full.
     *
     * <p>The mirror of stowing, and the same reason for existing: a sorting mod can only
     * move what its screen shows it.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void takingReachesPastThePage(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        int page = KIND.pageSize();

        // One stack on the first page, one three pages in where nothing can see it.
        chest.contents().setStackInSlot(0, new ItemStack(Items.STONE, 64));
        chest.contents().setStackInSlot(page * 3 + 6, new ItemStack(Items.GOLD_INGOT, 12));

        CellaMenu menu = CellaMenu.at(1, player.getInventory(), helper.absolutePos(WHERE),
                chest.contents().getSlots(),
                CellaConfig.rows(KIND), CellaConfig.columns(KIND));
        menu.clickMenuButton(player, CellaMenu.TAKE);

        check(chest.contents().getStackInSlot(0).isEmpty()
                        && chest.contents().getStackInSlot(page * 3 + 6).isEmpty(),
                "both should have left the chest, including the one three pages in");
        check(player.getInventory().countItem(Items.GOLD_INGOT) == 12,
                "and the gold should be on the player");
        helper.succeed();
    }

    /**
     * Taking what matches brings back more of what the player already carries.
     *
     * <p>The hand counts here, unlike stowing. Your hand says what you want; it is only
     * not a place to put things.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void takingWhatMatchesLeavesTheRest(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        int page = KIND.pageSize();

        chest.contents().setStackInSlot(page * 2, new ItemStack(Items.COBBLESTONE, 40));
        chest.contents().setStackInSlot(page * 2 + 1, new ItemStack(Items.DIAMOND, 3));
        player.getInventory().setItem(player.getInventory().selected,
                new ItemStack(Items.COBBLESTONE, 1));

        CellaMenu menu = CellaMenu.at(1, player.getInventory(), helper.absolutePos(WHERE),
                chest.contents().getSlots(),
                CellaConfig.rows(KIND), CellaConfig.columns(KIND));
        menu.clickMenuButton(player, CellaMenu.TAKING);

        check(chest.contents().getStackInSlot(page * 2).isEmpty(),
                "the cobblestone matches what is in hand and should have come out");
        check(chest.contents().getStackInSlot(page * 2 + 1).getCount() == 3,
                "the diamonds match nothing the player has and should have stayed");
        helper.succeed();
    }

    /**
     * What is being worn stays on.
     *
     * <p>Asked because of Curios, whose slots hold things a player would very much rather
     * not post into a chest by pressing one button. It cannot happen, and not by care:
     * {@code Inventory.INVENTORY_SIZE} is the pack and the hotbar, armour and the off
     * hand are other compartments, and anything Curios keeps is not in {@code Inventory}
     * at all. Armour and the off hand stand in for that here — they fail the same way if
     * the loop ever grows past thirty-six.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void whatIsWornStaysOn(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,
                new ItemStack(Items.DIAMOND_CHESTPLATE));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,
                new ItemStack(Items.SHIELD));
        player.getInventory().setItem(5, new ItemStack(Items.APPLE, 3));

        CellaMenu menu = CellaMenu.at(1, player.getInventory(), helper.absolutePos(WHERE),
                chest.contents().getSlots(),
                CellaConfig.rows(KIND), CellaConfig.columns(KIND));
        menu.clickMenuButton(player, CellaMenu.STOW);

        check(player.getInventory().getItem(5).isEmpty(), "the apples should have gone in");
        check(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)
                        .is(Items.DIAMOND_CHESTPLATE),
                "the chestplate should still be worn");
        check(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND)
                        .is(Items.SHIELD), "and the shield should still be held");
        helper.succeed();
    }

    /**
     * The lid stays up while anybody still has it open.
     *
     * <p>The whole reason the block entity counts openers instead of holding a flag. Two
     * players open the chest, one walks away: a flag shuts the lid in the other one's
     * face, and the sound plays twice on the way in and twice on the way out.
     *
     * <p>The lid itself cannot be checked here — it swings on the client and a game test
     * has none — so this asks the count, which is what the lid is drawn from.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void theLidWaitsForTheLastOneOut(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Player first = helper.makeMockPlayer(GameType.SURVIVAL);
        Player second = helper.makeMockPlayer(GameType.SURVIVAL);

        check(chest.openers() == 0, "a chest nobody has opened is shut");
        chest.opened(first);
        chest.opened(second);
        check(chest.openers() == 2, "two of them should be counted, not one");

        chest.closed(first);
        check(chest.openers() == 1, "and one leaving should leave it open for the other");
        chest.closed(second);
        check(chest.openers() == 0, "until the last one goes");
        helper.succeed();
    }

    /**
     * The Perfect that makes Junior stays; the ones that make Super Perfect do not.
     *
     * <p>Both halves matter. A parent that always came back would be the bucket rule -
     * a property of the block - and would make Super Perfect free, since that one eats
     * four of them. This is a property of the one recipe, and the second assertion is
     * what says so.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void theParentIsSpentExceptWhenItSpawns(GameTestHelper helper) {
        var recipes = helper.getLevel().getServer().getRecipeManager();

        ItemStack perfect = new ItemStack(CellaRegistry.block(Kind.PERFECT).get());
        ItemStack diamond = new ItemStack(Items.DIAMOND_BLOCK);
        var junior = recipes.byKey(ResourceLocation.fromNamespaceAndPath(Cella.MODID, "junior"))
                .orElseThrow(() -> new GameTestAssertException("no junior recipe"));
        check(junior.value() instanceof Spawning, "junior should be a spawning recipe");
        CraftingInput around = CraftingInput.of(3, 3, java.util.List.of(
                diamond, diamond, diamond,
                diamond, perfect, diamond,
                diamond, diamond, diamond));
        check(((Spawning) junior.value()).getRemainingItems(around).get(4)
                        .is(CellaRegistry.item(Kind.PERFECT).get()),
                "the Perfect in the middle should still be there afterwards");

        ItemStack star = new ItemStack(Items.NETHER_STAR);
        var superPerfect = recipes
                .byKey(ResourceLocation.fromNamespaceAndPath(Cella.MODID, "super_perfect"))
                .orElseThrow(() -> new GameTestAssertException("no super_perfect recipe"));
        check(!(superPerfect.value() instanceof Spawning),
                "super perfect should not give its Perfects back");
        CraftingInput eaten = CraftingInput.of(3, 3, java.util.List.of(
                perfect, star, perfect,
                star, new ItemStack(Items.DRAGON_EGG), star,
                perfect, star, perfect));
        for (ItemStack left : ((net.minecraft.world.item.crafting.CraftingRecipe)
                superPerfect.value()).getRemainingItems(eaten)) {
            check(left.isEmpty(), "nothing should come back from it");
        }
        helper.succeed();
    }

    /**
     * A chest filed before the store recorded anything about it still loads, and still
     * hands its contents back.
     *
     * <p><b>This is the direction that matters.</b> The store grew fields — which form,
     * and when — and worlds have entries written before either existed. An entry that was
     * skipped for want of them would be exactly the orphan this is all for, thrown away by
     * the thing built to rescue it, and nothing would say so.
     *
     * <p>It loads knowing nothing about itself and saying so, and knowing everything about
     * its contents, because how full it is was never a field: it is read off the contents
     * tag, which every entry has always had.
     *
     * <p>The keys are written out here rather than taken from {@link Kept}, on purpose.
     * They are the shape of what is on disk in somebody's world, so a rename that breaks
     * those worlds should break this.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void anEntryFiledBeforeAnyOfThisStillLoads(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStackHandler was = new ItemStackHandler(Kind.SEMI_PERFECT.slots());
        was.setStackInSlot(3, new ItemStack(Items.GOLD_INGOT, 5));
        was.setStackInSlot(900, new ItemStack(Items.DIAMOND, 2));
        java.util.UUID name = java.util.UUID.randomUUID();

        Kept kept = Kept.load(store(named(name, was.serializeNBT(registries), null)), registries);
        check(kept.size() == 1, "the old entry should have loaded: " + kept.size());

        Kept.Trace trace = kept.trace(name).orElseThrow(
                () -> new GameTestAssertException("and still answer to its name"));
        check(!trace.formed(), "with no form, rather than one guessed from its size");
        check(trace.kind().isEmpty(), "which is nothing when asked for a Kind");
        check(!trace.dated(), "and no date");
        check(trace.used() == 2, "but how full it is is read off the contents: " + trace.used());
        check(trace.slots() == Kind.SEMI_PERFECT.slots(), "and how big: " + trace.slots());

        check(kept.take(name).isPresent(), "and the contents are there to be handed back");
        helper.succeed();
    }

    /**
     * What is recorded now survives being written and read back.
     *
     * <p>The other half of the one above: the two facts that cannot be worked out again
     * have to make it to the disk, or an entry becomes undescribable the first time the
     * world is closed.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void whatWasFiledSurvivesTheDisk(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStackHandler was = new ItemStackHandler(KIND.slots());
        was.setStackInSlot(7, new ItemStack(Items.GOLD_INGOT, 5));

        Kept kept = Kept.load(new CompoundTag(), registries);
        long when = 50_000L;
        int fed = 1234;
        java.util.UUID name = kept.put(was.serializeNBT(registries), KIND, when, fed);

        Kept read = Kept.load(kept.save(new CompoundTag(), registries), registries);
        Kept.Trace trace = read.trace(name).orElseThrow(
                () -> new GameTestAssertException("it should come back"));
        check(trace.kind().orElse(null) == KIND, "as the form it was: " + trace.named());
        check(trace.dated() && trace.when() == when, "at the time it was: " + trace.when());
        check(trace.used() == 1 && trace.slots() == KIND.slots(), "and as full as it was");
        check(trace.experience() == fed, "and as fed as it was: " + trace.experience());
        helper.succeed();
    }

    /**
     * An entry naming a form this version does not have is written back, not flattened.
     *
     * <p>Resolving the id on the way in and saving the resolution would turn "names a form
     * I do not know" into "names nothing" the first time such a world was opened — and
     * nothing would ever put it back. So the id is kept as it was written, and the listing
     * has a third answer for it that is not the same as knowing nothing.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void aFormThisVersionLacksIsWrittenBackRatherThanLost(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        String foreign = "ultra_perfect";
        check(Kind.named(foreign).isEmpty(), "there should be no such form here");

        ItemStackHandler was = new ItemStackHandler(64);
        java.util.UUID name = java.util.UUID.randomUUID();
        Kept kept = Kept.load(
                store(named(name, was.serializeNBT(registries), foreign)), registries);

        Kept.Trace trace = kept.trace(name).orElseThrow(
                () -> new GameTestAssertException("it should load"));
        check(trace.formed(), "as something that names a form");
        check(trace.kind().isEmpty(), "which this version cannot resolve");
        check(trace.named().equals(foreign), "and says which: " + trace.named());

        Kept read = Kept.load(kept.save(new CompoundTag(), registries), registries);
        check(read.trace(name).orElseThrow().named().equals(foreign),
                "and it is still there after a round trip to the disk");
        helper.succeed();
    }

    /**
     * Looking does not spend a name; forgetting destroys it.
     *
     * <p>Contents leave the store exactly once, by {@link Kept#take}, and everything the
     * cleanup tool does has to hold that. Handing somebody an item that names a chest is
     * looking — the chest comes back when something is placed, once, whichever item got
     * there first — so a listing or a hand-out that quietly took would be two chests out
     * of one. The failure is silent, so it is a test rather than a careful reading.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void lookingIsNotTakingAndForgettingIsNeither(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStackHandler was = new ItemStackHandler(Kind.IMPERFECT.slots());
        was.setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 1));

        Kept kept = Kept.load(new CompoundTag(), registries);
        java.util.UUID name = kept.put(was.serializeNBT(registries), Kind.IMPERFECT, 1L, 0);

        check(kept.trace(name).isPresent(), "it should be there to look at");
        check(kept.trace(name).isPresent(), "and still there, because looking is not taking");
        check(kept.list().size() == 1, "and listing it does not spend it either");
        check(kept.size() == 1, "so the store still holds one");

        check(kept.forget(name), "forgetting should say it found something");
        check(kept.trace(name).isEmpty(), "and then there is nothing to look at");
        check(kept.take(name).isEmpty(), "nor anything to take, which is what it is for");
        check(!kept.forget(name), "and forgetting again should say so rather than pretend");
        helper.succeed();
    }

    /**
     * An orphan whose form was never recorded is handed back in one that would hold it.
     *
     * <p>By size, and <b>not by position in the list</b>: Cella Jr. comes off Perfect and
     * so sits between two forms far bigger than it, which is what an implementation that
     * walked the list in order would get wrong. What this answers is "what would hold
     * this", which is derivable, rather than "what was this", which is not.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void anOrphanWithNoFormGetsOneThatWouldHoldIt(GameTestHelper helper) {
        check(Kind.fitting(1) == Kind.LARAVEL, "one slot fits in the smallest there is");
        check(Kind.fitting(Kind.LARAVEL.slots()) == Kind.LARAVEL, "and so does exactly a Laravel");
        check(Kind.fitting(Kind.LARAVEL.slots() + 1) == Kind.IMPERFECT, "one more does not");
        check(Kind.fitting(Kind.SEMI_PERFECT.slots() + 1) == Kind.JUNIOR,
                "and above Semi-Perfect it is Junior, which is not the next one written down");
        check(Kind.biggest() == Kind.MAX, "the biggest form is Max");
        check(Kind.fitting(Kind.MAX.slots() + 1) == Kind.MAX,
                "and something too big for any of them still gets the biggest there is");
        helper.succeed();
    }

    /**
     * The points-per-level curve agrees with the game's own, at every level and both joins.
     *
     * <p>{@link Experience#total} restates vanilla's piecewise arithmetic because there is
     * no accessor for it, and a curve copied wrong is a curve that is only wrong somewhere
     * in the middle — around level 17 or 32, where the pieces meet, and nowhere a casual
     * try would look. So it is walked against the one accessor the game does expose: the
     * cost of the next level, which is the difference between two of these.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void theExperienceCurveIsTheGamesOwn(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        check(Experience.total(0) == 0, "nothing is nothing");

        for (int level = 0; level < 60; level++) {
            player.experienceLevel = level;
            int step = Experience.total(level + 1) - Experience.total(level);
            check(step == player.getXpNeededForNextLevel(),
                    "level " + level + " should cost " + player.getXpNeededForNextLevel()
                            + " to leave, not " + step);
        }

        // And the figure Perfect is written with is the level it is meant to be.
        check(Experience.total(50) == Kind.PERFECT.growth(),
                "Perfect should want level fifty: " + Experience.total(50));
        helper.succeed();
    }

    /**
     * Absorbing takes everything the player has, when the chest can still use it.
     *
     * <p>Not a mouthful at a time. The press is already deliberate — sneaking, empty
     * handed, at the block — so a small amount bought no safety the gesture had not
     * bought already, and cost the one thing this is meant to feel like.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void absorbingTakesEverythingItCanUse(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper, KIND);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.giveExperienceLevels(30);
        int had = Experience.points(player);
        check(had < KIND.growth(), "thirty levels should be less than a Perfect wants");

        int taken = chest.absorb(player);
        check(taken == had, "all of it should have moved: " + taken + " of " + had);
        check(chest.experience() == had, "and landed in the chest: " + chest.experience());
        check(Experience.points(player) == 0,
                "and the player should be empty: " + Experience.points(player));
        check(player.experienceLevel == 0, "no levels left: " + player.experienceLevel);

        // An empty player gives nothing, rather than the chest filling itself from one.
        int before = chest.experience();
        check(chest.absorb(player) == 0, "an empty player should give nothing");
        check(chest.experience() == before, "and the chest should not have grown");
        helper.succeed();
    }

    /**
     * Feeding stops exactly at the threshold rather than going past it.
     *
     * <p>Anything over it would be experience that can never mean anything, taken from
     * somebody who cannot get it back.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void absorbingStopsAtTheTop(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper, KIND);
        Player rich = helper.makeMockPlayer(GameType.SURVIVAL);
        rich.giveExperienceLevels(200);
        int had = Experience.points(rich);
        check(had > KIND.growth(), "two hundred levels should be more than a Perfect wants");

        int taken = chest.absorb(rich);
        check(chest.experience() == KIND.growth(),
                "it should stop exactly full: " + chest.experience());
        check(chest.grown() == 1.0F, "which is all the way grown");
        check(taken == KIND.growth(), "having taken only what it could use: " + taken);
        check(Experience.points(rich) == had - taken,
                "and left the change with the player: " + Experience.points(rich));
        check(chest.absorb(rich) == 0, "and a full one takes nothing more");
        helper.succeed();
    }

    /**
     * A form that does not grow takes nothing, and shows nothing.
     *
     * <p>Nought in the column means "has no use for experience", not "needs none". A bar
     * that could never move is worse than no bar: it says there is something to fill.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void aFormThatDoesNotGrowTakesNothing(GameTestHelper helper) {
        check(!Kind.LARAVEL.grows(), "Laravel does not grow on experience yet");
        CellaBlockEntity chest = place(helper, Kind.LARAVEL);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.giveExperienceLevels(30);
        int had = Experience.points(player);

        check(chest.absorb(player) == 0, "so it should take nothing");
        check(Experience.points(player) == had, "and the player should keep it all");
        check(chest.grown() == 0.0F, "and there is nothing to draw");
        helper.succeed();
    }

    /**
     * A chest with experience and no items is still worth keeping.
     *
     * <p><b>This is the hole that made experience dangerous.</b> Breaking a chest files it
     * away only if there is something to keep, and that test used to be "are any slots
     * spoken for" — so a chest holding nothing but levels would have dropped as a plain
     * item and the experience would have gone with the block. One way means there is no
     * getting it back except by fighting for it again.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void experienceAloneIsWorthKeeping(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper, KIND);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.giveExperienceLevels(30);
        int fed = chest.absorb(player);
        check(fed > 0, "it should have been fed something to lose");

        check(chest.isEmpty(), "no slot has anything in it");
        check(chest.worthKeeping(), "and it is still very much worth keeping");

        helper.destroyBlock(WHERE);

        helper.succeedWhen(() -> {
            Held held = dropped(helper).stream()
                    .filter(stack -> stack.is(CellaRegistry.item(KIND).get()))
                    .map(stack -> stack.get(CellaRegistry.KEPT.get()))
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElseThrow(() -> new GameTestAssertException(
                            "an empty chest with experience should still name what it kept"));
            check(held.experience() == fed,
                    "and carry what it was fed: " + held.experience());
            check(held.grows() && held.growth() == KIND.growth(),
                    "against the form's own threshold, so the bar means something");
        });
    }

    /**
     * Experience comes back with the chest, and never over the top of the bar.
     *
     * <p>Put back into the form it came from it is the same figure. Put back into a form
     * that can use less of it, it is that form's threshold — because a bar reading more
     * than full is a bar saying something untrue, and refusing the chest instead would
     * lose the contents as well.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void experienceComesBackAndIsNeverOverFull(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        CompoundTag was = new ItemStackHandler(KIND.slots()).serializeNBT(registries);

        CellaBlockEntity chest = place(helper, KIND);
        chest.restore(registries, new Kept.Chest(was, KIND.growth() / 2));
        check(chest.experience() == KIND.growth() / 2,
                "half fed comes back half fed: " + chest.experience());

        chest.restore(registries, new Kept.Chest(was, KIND.growth() * 3));
        check(chest.experience() == KIND.growth(),
                "and more than it can use stops at the top: " + chest.experience());

        // A form with no use for experience takes none of it back either.
        CellaBlockEntity larva = place(helper, Kind.LARAVEL);
        larva.restore(registries, new Kept.Chest(
                new ItemStackHandler(Kind.LARAVEL.slots()).serializeNBT(registries), 9999));
        check(larva.experience() == 0, "a form that does not grow keeps none of it");
        helper.succeed();
    }

    /** The store as it is on disk, holding one entry. See the note on the migration test. */
    private static CompoundTag store(CompoundTag entry) {
        ListTag chests = new ListTag();
        chests.add(entry);
        CompoundTag tag = new CompoundTag();
        tag.put("Chests", chests);
        return tag;
    }

    /** One entry, with a form id or without one. */
    private static CompoundTag named(java.util.UUID id, CompoundTag contents, String kind) {
        CompoundTag entry = new CompoundTag();
        entry.put("Id", UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE, id).result().orElseThrow());
        entry.put("Contents", contents);
        if (kind != null) {
            entry.putString("Kind", kind);
        }
        return entry;
    }

    private static CellaBlockEntity place(GameTestHelper helper) {
        return place(helper, KIND);
    }

    private static CellaBlockEntity place(GameTestHelper helper, Kind kind) {
        helper.setBlock(WHERE, CellaRegistry.block(kind).get());
        return (CellaBlockEntity) helper.getBlockEntity(WHERE);
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new GameTestAssertException(what);
        }
    }
}
