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
     * A batch of its own for each test that sets a wave going.
     *
     * <p>⚠ <b>Tests in one batch run at the same time, and batches run one after another.</b>
     * The waves in flight are one list for the whole server — which is right, since a server
     * has one set of them — so two of these running together would have one asking whether
     * "the wave" has finished while another's was still going, and {@code Blast.forget}
     * clearing a wave the test beside it was waiting on. That is a real collision and not a
     * quirk of the harness: the same list is what a second player lighting a second Cella
     * would land in.
     */
    private static final String WAVE_CLEARS = "waveClears";

    private static final String WAVE_KILLS = "waveKills";

    private static final String WAVE_NAMES = "waveNames";

    private static final String WAVE_LADDER = "waveLadder";

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
        // One kind, three slots further than a page goes, so there is a second page with a
        // known end to it. The chest packs to the front, so a gap cannot be arranged.
        for (int slot = 0; slot < page + 3; slot++) {
            chest.contents().setStackInSlot(slot, new ItemStack(Items.DIAMOND, 64));
        }

        CellaMenu menu = CellaMenu.at(1, player.getInventory(), helper.absolutePos(WHERE),
                chest.contents().getSlots(),
                CellaConfig.rows(KIND), CellaConfig.columns(KIND));

        check(menu.slots.get(4).getItem().is(Items.DIAMOND), "page one holds the diamonds");
        menu.turnTo(1);
        check(menu.slots.get(2).getItem().getCount() == 64,
                "and the page after it holds the last of them once the page has turned");
        check(menu.slots.get(3).getItem().isEmpty(), "with the rest of that page empty");

        // ⚠ Writing through a slot no longer leaves anything at that slot. The chest keeps
        // itself in order, so a square takes what is put on it and the kind decides where
        // it goes; the screen is a list to take from, not a grid to arrange. See Sorted.
        menu.slots.get(2).set(new ItemStack(Items.COAL, 3));
        check(chest.contents().getStackInSlot(0).is(Items.COAL),
                "what is put in goes where its kind belongs, coal sorting before diamond");
        check(chest.contents().getStackInSlot(1).is(Items.DIAMOND),
                "and the diamonds move along to make room for it");
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
        check(offered.insertItem(far, new ItemStack(Items.REDSTONE, 7), false).isEmpty(),
                "and an offer made past the first page is taken whole");
        // ⚠ Not at the slot it was offered to. The chest keeps itself in order, so what it
        // takes goes where its kind belongs; see Sorted.
        check(offered.getStackInSlot(0).is(Items.REDSTONE)
                        && offered.getStackInSlot(0).getCount() == 7,
                "and is in the chest, where redstone goes");
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

        check(chest.contents().getStackInSlot(0).getCount() == 11
                        && chest.contents().getStackInSlot(0).is(Items.GOLD_INGOT),
                "the gold should be back, wherever gold sorts to");
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
        // ⚠ Five, not three hundred: a chest that keeps itself in order keeps itself
        // merged, so three hundred loose stone are four full stacks and a remainder.
        check(chest.used() == 5, "three hundred stone pour into five slots: " + chest.used());

        helper.destroyBlock(WHERE);

        helper.succeedWhen(() -> {
            Held held = dropped(helper).stream()
                    .filter(stack -> stack.is(CellaRegistry.item(KIND).get()))
                    .map(stack -> stack.get(CellaRegistry.KEPT.get()))
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElseThrow(() -> new GameTestAssertException("no chest with a name"));
            check(held.used() == 5, "the item should say five: " + held.used());
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
        // All eight full, because a recipe will not take one that is not - and two of
        // them naming contents, which is what this is actually about.
        java.util.List<ItemStack> grid = new java.util.ArrayList<>();
        for (int at = 0; at < 9; at++) {
            grid.add(at == 4 ? new ItemStack(Items.OBSIDIAN) : grown(Kind.IMPERFECT, 0));
        }
        java.util.List<java.util.UUID> filed = new java.util.ArrayList<>();
        for (int at : new int[] { 0, 8 }) {
            ItemStackHandler one = new ItemStackHandler(Kind.IMPERFECT.slots());
            one.setStackInSlot(at, new ItemStack(Items.GOLD_INGOT, 7));
            java.util.UUID name = kept.put(
                    one.serializeNBT(helper.getLevel().registryAccess()),
                    Kind.IMPERFECT, helper.getLevel().getGameTime(), 0);
            filed.add(name);
            grid.get(at).set(CellaRegistry.KEPT.get(), new Held(java.util.List.of(name), 1,
                    Kind.IMPERFECT.slots(), Kind.IMPERFECT.growth(), Kind.IMPERFECT.growth()));
        }

        CraftingInput bench = CraftingInput.of(3, 3, grid);
        check(fusion.matches(bench, helper.getLevel()), "it should be a recipe");

        ItemStack made = fusion.assemble(bench, helper.getLevel().registryAccess());
        Held held = made.get(CellaRegistry.KEPT.get());
        check(held != null, "the result should carry what it ate");
        // All eight, in the order they were laid out - not just the two with something in
        // them. ⚠ A chest fed to the top is worth keeping even with nothing inside it, so
        // breaking one gives an item with a name on it either way, and a fusion of eight
        // full Imperfects therefore carries eight names.
        check(held.chests().size() == 8, "eight names, one per Cella: " + held.chests());
        check(held.chests().getFirst().equals(filed.getFirst())
                        && held.chests().get(7).equals(filed.getLast()),
                "the two that were filed at the ends they were laid at: " + held.chests());
        check(held.used() == 2, "two slots between them: " + held.used());
        check(held.slots() == Kind.SEMI_PERFECT.slots(),
                "in the room a Semi-Perfect has: " + held.slots());

        // ⚠ And none of what they were fed. A Cella that changes form starts again at
        // nothing, and eight going into one is still a change of form. Left to add up, the
        // eight full Imperfects would have overflowed a Semi-Perfect's own threshold and it
        // would have arrived finished - a rung that could never be climbed.
        check(held.experience() == 0,
                "a fusion should arrive unfed: " + held.experience());
        check(held.grows() && held.growth() == Kind.SEMI_PERFECT.growth(),
                "with its own threshold to fill, so the bar means something");

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
            grid.add(at == 4 ? new ItemStack(Items.OBSIDIAN) : grown(Kind.IMPERFECT, 0));
        }
        check(fusion.matches(CraftingInput.of(3, 3, grid), helper.getLevel()),
                "eight grown empty ones are a recipe");

        // One that has not finished growing is not an ingredient at all.
        grid.set(0, new ItemStack(CellaRegistry.item(Kind.IMPERFECT).get()));
        check(!fusion.matches(CraftingInput.of(3, 3, grid), helper.getLevel()),
                "and one that has never been fed is not");
        grid.set(0, grown(Kind.IMPERFECT, 0));

        // One of them is fuller than the whole Semi-Perfect it would go into.
        grid.get(0).set(CellaRegistry.KEPT.get(), new Held(
                java.util.List.of(java.util.UUID.randomUUID()),
                Kind.SEMI_PERFECT.slots() + 1, Kind.SEMI_PERFECT.slots() + 1,
                Kind.IMPERFECT.growth(), Kind.IMPERFECT.growth()));
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
        // Both chests' gold is one stack of gold and both chests' diamonds one of diamonds,
        // in the chest's order rather than in the order they arrived.
        check(chest.contents().getStackInSlot(0).is(Items.DIAMOND)
                        && chest.contents().getStackInSlot(0).getCount() == 3,
                "the diamonds of both, poured together");
        check(chest.contents().getStackInSlot(1).is(Items.GOLD_INGOT)
                        && chest.contents().getStackInSlot(1).getCount() == 3,
                "and then the gold, diamond sorting before gold");
        check(chest.contents().getStackInSlot(2).isEmpty(), "and nothing after them");
        check(chest.used() == 2, "two slots spoken for: " + chest.used());
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

        // Full, because a recipe will not take a Cella that has not finished growing.
        ItemStack perfect = grown(Kind.PERFECT, 5);
        Held named = perfect.get(CellaRegistry.KEPT.get());

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
        check(chest.contents().getStackInSlot(0).is(Items.COAL)
                        && chest.contents().getStackInSlot(0).getCount() == 32,
                "and went into the chest, ahead of the stone that filled the page");
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
     * What a player moves is written down; what a hopper moves is not.
     *
     * <p>A chest this size absorbs a mistake without a ripple — put the wrong stack in and
     * nothing on the screen is different afterwards. ⚠ But a hopper running all afternoon
     * would fill the record with its own footsteps and push out everything a person did,
     * so only what comes through the screen counts.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void whatAPlayerMovesIsWrittenDown(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(1, new ItemStack(Items.COBBLESTONE, 40));

        // Automation, which is not remembered.
        chest.contents().insertItem(0, new ItemStack(Items.DIAMOND, 7), false);
        check(chest.ledger().size() == 0, "a hopper leaves no trace: " + chest.ledger().size());

        CellaMenu menu = CellaMenu.at(1, player.getInventory(), helper.absolutePos(WHERE),
                chest.contents().getSlots(),
                CellaConfig.rows(KIND), CellaConfig.columns(KIND));
        menu.clickMenuButton(player, CellaMenu.STOW);

        check(chest.ledger().size() == 1, "a button press does: " + chest.ledger().size());
        Ledger.Move move = chest.ledger().moves().getFirst();
        check(move.kind().is(Items.COBBLESTONE), "the cobblestone that went in");
        check(move.before() == 0 && move.after() == 40,
                "from none to forty: " + move.before() + " -> " + move.after());
        check(move.moved() == 40, "which is forty arriving");
        helper.succeed();
    }

    /**
     * The record keeps components, so an enchanted book is not filed as a book.
     *
     * <p>⚠ Recording bare item ids would report the loss of something irreplaceable as the
     * loss of a paper one. {@code Tidy.merge} already tells them apart; so does this.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void therecordkeepsWhatMakesAThingItself(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);

        ItemStack plain = new ItemStack(Items.DIAMOND_PICKAXE);
        ItemStack named = new ItemStack(Items.DIAMOND_PICKAXE);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("Bertha"));

        chest.byHand(() -> {
            chest.contents().insertItem(0, plain.copy(), false);
            chest.contents().insertItem(0, named.copy(), false);
        });

        check(chest.ledger().size() == 2, "two movements, not one: " + chest.ledger().size());
        for (Ledger.Move move : chest.ledger().moves()) {
            check(move.before() == 0 && move.after() == 1,
                    "each went from none to one: " + move.before() + " -> " + move.after());
        }
        check(!ItemStack.isSameItemSameComponents(
                        chest.ledger().moves().get(0).kind(),
                        chest.ledger().moves().get(1).kind()),
                "and the two are remembered as different things");
        helper.succeed();
    }

    /**
     * The chest is in order at all times, with nobody having pressed anything.
     *
     * <p>Arrivals go where their kind belongs and pour into the one open stack of it; a
     * kind that was not there yet takes a slot of its own and everything after it moves
     * along. ⚠ <b>No button is pressed anywhere in this test</b> — that is the whole claim.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void theChestKeepsItselfInOrder(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Sorted contents = chest.contents();

        // Out of order, and in pieces that have to pour together.
        contents.insertItem(0, new ItemStack(Items.STONE, 40), false);
        contents.insertItem(0, new ItemStack(Items.DIAMOND, 5), false);
        contents.insertItem(0, new ItemStack(Items.STONE, 40), false);
        contents.insertItem(0, new ItemStack(Items.COAL, 2), false);
        contents.insertItem(0, new ItemStack(Items.DIAMOND, 3), false);

        check(contents.getStackInSlot(0).is(Items.COAL) && contents.getStackInSlot(0).getCount() == 2,
                "coal first: " + contents.getStackInSlot(0));
        check(contents.getStackInSlot(1).is(Items.DIAMOND) && contents.getStackInSlot(1).getCount() == 8,
                "then the diamonds, poured together: " + contents.getStackInSlot(1));
        check(contents.getStackInSlot(2).is(Items.STONE) && contents.getStackInSlot(2).getCount() == 64,
                "then a full stack of stone: " + contents.getStackInSlot(2));
        check(contents.getStackInSlot(3).is(Items.STONE) && contents.getStackInSlot(3).getCount() == 16,
                "and the remainder of it last: " + contents.getStackInSlot(3));
        check(contents.used() == 4, "four slots spoken for: " + contents.used());
        check(contents.getStackInSlot(4).isEmpty(), "and nothing behind them");
        helper.succeed();
    }

    /**
     * There is more than one order, and the chest is kept in whichever it was told.
     *
     * <p>⚠ <b>Registry name is not readable.</b> {@code andesite_wall} is followed by
     * {@code baked_potato}, and what the player sees is Andesite Wall followed by whatever
     * their pack calls a baked potato — two things with nothing between them. Ordering by
     * the name on screen is the answer to that, and it is why more than one exists.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void theChestIsKeptInWhicheverOrderItWasTold(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Sorted contents = chest.contents();

        // ⚠ A pair the two orders genuinely disagree about. A block of coal is
        // minecraft:coal_block, which sorts after minecraft:bone - and is called "Block of
        // Coal", which sorts before "Bone". Neither of these depends on the server having
        // a language: without one the names come back as translation keys, and
        // block.minecraft.coal_block still sorts before item.minecraft.bone.
        contents.insertItem(0, new ItemStack(Items.BONE, 1), false);
        contents.insertItem(0, new ItemStack(Items.COAL_BLOCK, 1), false);
        contents.insertItem(0, new ItemStack(Items.APPLE, 1), false);

        check(contents.order() == Order.REGISTRY, "a chest starts in registry name order");
        check(contents.getStackInSlot(0).is(Items.APPLE), "apple first");
        check(contents.getStackInSlot(1).is(Items.BONE), "then bone");
        check(contents.getStackInSlot(2).is(Items.COAL_BLOCK), "then coal_block");

        contents.order(Order.DISPLAY);
        check(contents.getStackInSlot(0).is(Items.APPLE), "Apple first either way");
        check(contents.getStackInSlot(1).is(Items.COAL_BLOCK),
                "but a Block of Coal comes before a Bone on screen");
        check(contents.getStackInSlot(2).is(Items.BONE), "and the bone goes last");

        // Cycling reaches every one of them and comes back round.
        Order at = contents.order();
        for (int step = 0; step < Order.values().length; step++) {
            at = at.next();
        }
        check(at == contents.order(), "the cycle closes");
        helper.succeed();
    }

    /**
     * Taking comes out of the remainder, so a kind stays a run with one partial behind it.
     *
     * <p>Out of the slot it was asked of, the first full stack of a kind would become a
     * second partial and the order would be wrong within its own run. What comes back is
     * the same item either way, so the only thing decided here is which of them is left
     * looking untidy — and the answer is neither.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void takingComesOutOfTheRemainder(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Sorted contents = chest.contents();
        contents.insertItem(0, new ItemStack(Items.STONE, 140), false);

        check(contents.used() == 3, "sixty-four, sixty-four and twelve: " + contents.used());

        ItemStack out = contents.extractItem(0, 5, false);
        check(out.is(Items.STONE) && out.getCount() == 5, "asked at the front, five come back");
        check(contents.getStackInSlot(0).getCount() == 64, "the full stacks are untouched");
        check(contents.getStackInSlot(1).getCount() == 64, "both of them");
        check(contents.getStackInSlot(2).getCount() == 7, "and it came out of the remainder");

        // Emptying the remainder closes the run up rather than leaving a hole in it.
        contents.extractItem(0, 7, false);
        check(contents.used() == 2, "the empty remainder goes: " + contents.used());
        check(contents.getStackInSlot(2).isEmpty(), "with nothing left behind it");
        helper.succeed();
    }

    /**
     * A full chest says so, and says it before taking anything it cannot hold.
     *
     * <p>⚠ Asked to simulate, it has to answer with what it would refuse. A store that
     * says yes and then drops the difference is how contents go missing.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void afullChestRefusesWhatWillNotFit(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper, Kind.LARAVEL);
        Sorted contents = chest.contents();
        int slots = Kind.LARAVEL.slots();

        contents.insertItem(0, new ItemStack(Items.STONE, 64 * slots), false);
        check(contents.used() == slots, "every slot of a Laravel is spoken for: " + contents.used());

        ItemStack asked = new ItemStack(Items.DIAMOND, 4);
        check(contents.insertItem(0, asked, true).getCount() == 4,
                "a simulated offer of a new kind comes back whole");
        check(contents.insertItem(0, asked, false).getCount() == 4,
                "and so does the real one");
        check(contents.used() == slots, "with nothing having moved: " + contents.used());

        // There is still room in the kind that is already there, and that is taken.
        contents.extractItem(0, 10, false);
        check(contents.insertItem(0, new ItemStack(Items.STONE, 10), false).isEmpty(),
                "the room that is left is room for more of what is in it");
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
        // The room was off the page, and where they landed is where their kinds belong.
        check(chest.contents().getStackInSlot(0).is(Items.APPLE)
                        && chest.contents().getStackInSlot(1).is(Items.BONE),
                "and went in, ahead of the stone that filled the page on screen");
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

        check(chest.used() == 1,
                "the cobblestone matches what is in hand and should have come out");
        check(chest.contents().getStackInSlot(0).is(Items.DIAMOND)
                        && chest.contents().getStackInSlot(0).getCount() == 3,
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

        ItemStack perfect = grown(Kind.PERFECT, 0);
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

        // The other half, on a fusion: what it eats does not come back. This used to ask
        // super_perfect, which is no longer a recipe at all - a Perfect that ends itself
        // comes back as one. Semi-Perfect is the same shape and still crafted.
        ItemStack imperfect = grown(Kind.IMPERFECT, 0);
        var semiPerfect = recipes
                .byKey(ResourceLocation.fromNamespaceAndPath(Cella.MODID, "semi_perfect"))
                .orElseThrow(() -> new GameTestAssertException("no semi_perfect recipe"));
        check(!(semiPerfect.value() instanceof Spawning),
                "a fusion should not give its ingredients back");
        CraftingInput eaten = CraftingInput.of(3, 3, java.util.List.of(
                imperfect, imperfect, imperfect,
                imperfect, new ItemStack(Items.OBSIDIAN), imperfect,
                imperfect, imperfect, imperfect));
        for (ItemStack left : ((net.minecraft.world.item.crafting.CraftingRecipe)
                semiPerfect.value()).getRemainingItems(eaten)) {
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

        // Handing a name out does not spend it either, and that it went out is remembered
        // - which is the only thing anybody can do about a claim that cannot be recalled.
        check(kept.hand(name) == 1, "a filed chest already has the name it was filed with");
        check(kept.trace(name).orElseThrow().claimed(), "and the hand-out makes that two");
        check(kept.hand(name) == 2, "the next one should say there were two before it");
        check(kept.trace(name).orElseThrow().names() == 3, "and count all three");
        check(kept.trace(name).isPresent(), "and handing out still does not spend it");

        check(kept.forget(name), "forgetting should say it found something");
        check(kept.trace(name).isEmpty(), "and then there is nothing to look at");
        check(kept.take(name).isEmpty(), "nor anything to take, which is what it is for");
        check(!kept.forget(name), "and forgetting again should say so rather than pretend");
        helper.succeed();
    }

    /**
     * A chest goes when the last name for it goes, and not one destruction before.
     *
     * <p><b>This is the one exception to "nothing sweeps them", so it is the one to test
     * hardest.</b> Filing drops an item; {@code give} mints another; either can be the one
     * that burns. ⚠ Forgetting on the first destruction would delete contents the other
     * item still names — which is precisely the failure the no-sweep rule exists to avoid,
     * arrived at by a different road.
     *
     * <p>Which of them was destroyed is deliberately never asked. The store holds contents
     * and not items, and the only question it has is whether anything is left to come and
     * ask for them.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void theLastNameIsTheOneThatTakesTheContents(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        Kept kept = Kept.load(new CompoundTag(), registries);
        java.util.UUID name = file(kept, registries);

        check(kept.trace(name).orElseThrow().names() == 1, "filing a chest makes one name");
        check(!kept.trace(name).orElseThrow().claimed(), "which is not a claim against itself");
        kept.hand(name);
        check(kept.trace(name).orElseThrow().names() == 2, "and handing one out makes two");

        check(!kept.lost(name), "one of two is not the last of them");
        check(kept.trace(name).orElseThrow().names() == 1, "and leaves the other standing");
        check(!kept.trace(name).orElseThrow().claimed(), "with nothing left to warn about");
        check(kept.trace(name).orElseThrow().used() == 1, "and the contents untouched");

        check(kept.lost(name), "the other one is the last of them");
        check(kept.trace(name).isEmpty(), "and the contents go with it");
        check(!kept.lost(name), "and losing a name twice over finds nothing to say it about");
        helper.succeed();
    }

    /**
     * An entry written before the store counted names loads with the one it was filed with.
     *
     * <p>What the old key held was hand-outs, which is the same count without the item the
     * chest was filed with — so an entry that had been given out once names two things and
     * not one. ⚠ <b>Reading both keys would double the count on every load</b>, and the
     * shape of that bug is a chest that quietly becomes harder to forget every time the
     * world is opened.
     *
     * <p>The keys are written out here rather than taken from {@link Kept}, for the reason
     * given on the entry-with-nothing-recorded test: they are what is on somebody's disk.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void anEntryFromBeforeNamesWereCountedKeepsItsOwn(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStackHandler was = new ItemStackHandler(64);
        java.util.UUID plain = java.util.UUID.randomUUID();
        java.util.UUID given = java.util.UUID.randomUUID();

        CompoundTag never = named(plain, was.serializeNBT(registries), null);
        CompoundTag once = named(given, was.serializeNBT(registries), null);
        once.putInt("Handed", 1);

        Kept kept = Kept.load(store(never, once), registries);
        check(kept.trace(plain).orElseThrow().names() == 1,
                "one never handed out has the name it was filed with");
        check(kept.trace(given).orElseThrow().names() == 2,
                "and one handed out once has that and the hand-out: "
                        + kept.trace(given).orElseThrow().names());

        Kept read = Kept.load(kept.save(new CompoundTag(), registries), registries);
        check(read.trace(given).orElseThrow().names() == 2,
                "and it is still two after a round trip, not four: "
                        + read.trace(given).orElseThrow().names());
        check(read.trace(plain).orElseThrow().names() == 1, "nor is the plain one three");
        helper.succeed();
    }

    /**
     * An item destroyed where it lay takes its chest with it; one picked up does not.
     *
     * <p>⚠ <b>Both halves, because the removal is shared.</b> Vanilla takes an item entity
     * out of the world the same way whether a player took it or a fire did — {@code discard}
     * either way — so a cleanup keyed on that would delete the contents of every chest
     * anybody ever picked up off the floor. What this leans on is the one call only the
     * destroying road makes, and the picking-up half is what says the road is not shared.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void aDestroyedItemTakesItsChestAndAPickedUpOneDoesNot(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        Kept kept = Kept.of(helper.getLevel()).orElseThrow(
                () -> new GameTestAssertException("a game test has a server, so it has a store"));
        java.util.UUID burnt = file(kept, registries);
        java.util.UUID lifted = file(kept, registries);

        net.minecraft.world.entity.item.ItemEntity dying =
                carrying(helper, Kind.IMPERFECT, java.util.List.of(burnt));
        check(dying.hurt(helper.getLevel().damageSources().onFire(), 20.0F),
                "fire takes an Imperfect, which is not the top of the ladder");
        check(dying.isRemoved(), "and destroys the item");
        check(kept.trace(burnt).isEmpty(), "so the chest it named goes with it");

        net.minecraft.world.entity.item.ItemEntity taken =
                carrying(helper, Kind.IMPERFECT, java.util.List.of(lifted));
        taken.playerTouch(helper.makeMockPlayer(GameType.SURVIVAL));
        check(taken.isRemoved(), "picking one up takes the entity out just the same");
        check(kept.trace(lifted).isPresent(),
                "but that is somebody holding the name, not the name being destroyed");

        kept.forget(lifted);
        helper.succeed();
    }

    /**
     * What the top of the ladder was being carried in gets up again; nothing else does.
     *
     * <p>Driven at the two halves rather than through a death, because a game test has no
     * player with a connection to respawn — what it can do is exactly what the game does
     * between them: carry {@code PERSISTED_NBT_TAG} across to whoever gets up. ⚠ <b>That
     * hand-off is the whole reason the data lives there</b> rather than in a map, since a
     * player can close the game while the death screen is up.
     *
     * <p>And handed back once. A respawn that minted a fresh Super Perfect every time would
     * be worse than losing it: two items naming one chest is the state this mod goes out of
     * its way never to make.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void theTopOfTheLadderGetsUpWithYou(GameTestHelper helper) {
        check(Kind.SUPER_PERFECT.trait().keptOnDeath(), "the top of the ladder gets up");
        check(Kind.MAX.trait().keptOnDeath(), "and Max is a Super Perfect, so it does too");
        check(!Kind.PERFECT.trait().keptOnDeath(), "and the step below it does not");

        Player died = helper.makeMockPlayer(GameType.SURVIVAL);
        java.util.List<net.minecraft.world.entity.item.ItemEntity> drops =
                new java.util.ArrayList<>();
        drops.add(dropping(helper, new ItemStack(CellaRegistry.item(Kind.SUPER_PERFECT).get())));
        drops.add(dropping(helper, new ItemStack(CellaRegistry.item(Kind.PERFECT).get())));
        drops.add(dropping(helper, new ItemStack(Items.GOLD_INGOT, 5)));

        Carried.keep(died, drops);
        check(drops.size() == 2, "only the one that gets up is taken out of the drops: "
                + drops.size());
        check(drops.stream().noneMatch(drop -> drop.getItem().getItem()
                        == CellaRegistry.item(Kind.SUPER_PERFECT).get()),
                "and it is the Super Perfect that went");

        Player alive = helper.makeMockPlayer(GameType.SURVIVAL);
        // What ServerPlayer#restoreFrom does, and the only reason this survives at all.
        alive.getPersistentData().put(Player.PERSISTED_NBT_TAG,
                died.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG));

        Carried.give(alive);
        check(alive.getInventory().countItem(CellaRegistry.item(Kind.SUPER_PERFECT).get()) == 1,
                "it should be in the hands of whoever got up");
        check(alive.getInventory().countItem(CellaRegistry.item(Kind.PERFECT).get()) == 0,
                "and nothing that was left in the crater should be");

        Carried.give(alive);
        check(alive.getInventory().countItem(CellaRegistry.item(Kind.SUPER_PERFECT).get()) == 1,
                "and once however often they get up, because two of one chest is worse");
        helper.succeed();
    }

    /**
     * The wave goes round the top of the ladder and through everything under it.
     *
     * <p>⚠ <b>Both, because the rule used to be "any Cella".</b> Sparing a Laravel for
     * being a Cella is a chest surviving annihilation for being a chest, which is not a
     * reason; what earns it is being as hard as the world's floor, and only the top two
     * forms are.
     *
     * <p>And the contents go with the block rather than being filed and left. A Cella
     * removed by anything hands its contents over and drops the name — which is right, and
     * would leave one orphan per chest in every crater if the front did not sweep the item
     * up behind itself.
     */
    @GameTest(template = TestStructures.FLOOR, batch = WAVE_LADDER)
    public static void theWaveGoesRoundTheTopOfTheLadderAndThroughTheRest(GameTestHelper helper) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        Kept kept = Kept.of(level).orElseThrow(
                () -> new GameTestAssertException("a game test has a server, so it has a store"));
        BlockPos centre = helper.absolutePos(new BlockPos(2, 40, 2));
        BlockPos standing = centre.offset(2, 0, 0);
        BlockPos falling = centre.offset(3, 0, 0);
        int filed = kept.size();

        check(Kind.SUPER_PERFECT.trait().survivesAnnihilation(), "the top of the ladder survives");
        check(!Kind.PERFECT.trait().survivesAnnihilation(), "and the step below it does not");

        level.setBlock(standing,
                CellaRegistry.block(Kind.SUPER_PERFECT).get().defaultBlockState(), 2);
        level.setBlock(falling, CellaRegistry.block(Kind.PERFECT).get().defaultBlockState(), 2);
        // Something in it, so that being removed is something it would file if it could.
        ((CellaBlockEntity) level.getBlockEntity(falling)).contents()
                .setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 1));

        Blast.start(level, centre, 6);
        helper.succeedWhen(() -> {
            check(level.getBlockState(falling).isAir(),
                    "a Perfect in the reach is a box in the way");
            check(level.getBlockState(standing).getBlock() instanceof CellaBlock,
                    "and a Super Perfect is as hard as the world's floor");
            check(kept.size() == filed,
                    "and what was in the one that went is annihilated rather than filed: "
                            + kept.size() + " against " + filed);
        });
    }

    /**
     * A Cella swept up by the wave takes its chest with it.
     *
     * <p>⚠ <b>The wave removes rather than destroys</b> — {@code discard}, so that nothing
     * is left lying in a crater nobody can reach — and removal is the road that does
     * <em>not</em> go through the item. So this is the one call site in the mod that has to
     * remember on its own, and the only one a player could never check by hand: it wants
     * the End, a lit Perfect and a Cella lying in the reach.
     *
     * <p>Nothing here asks {@link Blast} how many waves are in flight or clears the ones
     * that are. Those are the same static list for the whole server, and a test that
     * reached into it would be answering for whatever else was running beside it.
     */
    @GameTest(template = TestStructures.FLOOR, batch = WAVE_NAMES)
    public static void theWaveTakesTheNamesItSweepsUp(GameTestHelper helper) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        HolderLookup.Provider registries = level.registryAccess();
        Kept kept = Kept.of(level).orElseThrow(
                () -> new GameTestAssertException("a game test has a server, so it has a store"));
        java.util.UUID swept = file(kept, registries);

        BlockPos centre = helper.absolutePos(new BlockPos(2, 40, 2));
        net.minecraft.world.entity.item.ItemEntity lying =
                carrying(helper, Kind.IMPERFECT, java.util.List.of(swept));
        lying.moveTo(centre.getX() + 2.5, centre.getY() + 0.5, centre.getZ() + 0.5);

        Blast.start(level, centre, 3);
        helper.succeedWhen(() -> {
            check(lying.isRemoved(), "the wave should have taken the item");
            check(kept.trace(swept).isEmpty(), "and the chest it named with it");
        });
    }

    /**
     * A name that simply runs out is lost; one that was given more time is not.
     *
     * <p>⚠ <b>Running out is the commonest of the two by a long way</b>, so a cleanup that
     * only watched for fire would be watching the small half. Everything below the top of
     * the ladder has five minutes on the floor; the top winds its own clock back and never
     * gets here.
     *
     * <p>The second half is the one worth the machinery. The expiry event is an offer any
     * mod can answer, so being told an item is due is not being told it went — and the
     * store is read from that difference. Nothing in ordinary play arranges it on demand,
     * which is why {@link Expiring#watch} is reachable from here: an item that was written
     * down and then did not go is exactly the state another mod's extra five minutes makes.
     *
     * <p>Nothing is ticked by hand. The decision is taken when the level has finished
     * ticking, so a test that drove the entity itself would be asking before the answer.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void aNameThatRunsOutIsLostAndOneGivenMoreTimeIsNot(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        Kept kept = Kept.of(helper.getLevel()).orElseThrow(
                () -> new GameTestAssertException("a game test has a server, so it has a store"));
        java.util.UUID waited = file(kept, registries);
        java.util.UUID spared = file(kept, registries);

        net.minecraft.world.entity.item.ItemEntity fading =
                carrying(helper, Kind.IMPERFECT, java.util.List.of(waited));
        fading.lifespan = 3;

        net.minecraft.world.entity.item.ItemEntity lasting =
                carrying(helper, Kind.IMPERFECT, java.util.List.of(spared));
        Expiring.watch(lasting);

        helper.runAfterDelay(10, () -> {
            check(fading.isRemoved(), "the one that ran out should be gone");
            check(kept.trace(waited).isEmpty(), "and the chest it named with it");
            check(lasting.isAlive(), "the one that was given more time should still be lying there");
            check(kept.trace(spared).isPresent(),
                    "and its chest still filed, because it is still holding the name");
            kept.forget(spared);
            helper.succeed();
        });
    }

    /**
     * One fire takes every chest a fusion ate.
     *
     * <p>A crafted Semi-Perfect names eight chests until it is placed, so the item that
     * burns is one item and eight names. ⚠ A loop written to stop at the first would leave
     * seven orphans behind the one it tidied, and the listing would look right.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void oneFireTakesEveryChestAFusionAte(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        Kept kept = Kept.of(helper.getLevel()).orElseThrow(
                () -> new GameTestAssertException("a game test has a server, so it has a store"));

        // How many go into one, off the sizes rather than written down again: a fusion is
        // exactly as big as what it ate, which is the rule Fusing is built on.
        int eats = Kind.SEMI_PERFECT.slots() / Kind.IMPERFECT.slots();
        java.util.List<java.util.UUID> eaten = new java.util.ArrayList<>();
        for (int each = 0; each < eats; each++) {
            eaten.add(file(kept, registries));
        }

        net.minecraft.world.entity.item.ItemEntity fused =
                carrying(helper, Kind.SEMI_PERFECT, java.util.List.copyOf(eaten));
        check(fused.hurt(helper.getLevel().damageSources().onFire(), 20.0F), "fire takes it");
        for (java.util.UUID one : eaten) {
            check(kept.trace(one).isEmpty(), "and every chest it was carrying goes with it");
        }
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

        // And every threshold is a whole level, so a bar reaches its end exactly when one
        // lands rather than somewhere in the middle of one. Asked of all of them rather
        // than of a named figure, which is a thing that goes stale the first time somebody
        // retunes the ladder.
        for (Kind kind : Kind.values()) {
            if (!kind.grows()) {
                continue;
            }
            int level = 0;
            while (Experience.total(level) < kind.growth()) {
                level++;
            }
            check(Experience.total(level) == kind.growth(),
                    kind.id() + " wants " + kind.growth() + ", which is between levels "
                            + (level - 1) + " and " + level);
        }
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
     *
     * <p>Max is named rather than taken as whichever form happens not to grow, for the
     * same reason {@link #KIND} is: a change to the ladder should fail this out loud
     * instead of quietly testing a different thing. It was Laravel until Laravel grew and
     * Semi-Perfect until every ingredient had to be full — Max is the last one, having
     * nothing above it to grow into.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void aFormThatDoesNotGrowTakesNothing(GameTestHelper helper) {
        check(!Kind.MAX.grows(), "Max does not grow on experience, having nowhere to go");
        CellaBlockEntity chest = place(helper, Kind.MAX);
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
        CellaBlockEntity flat = place(helper, Kind.MAX);
        flat.restore(registries, new Kept.Chest(
                new ItemStackHandler(16).serializeNBT(registries), 9999));
        check(flat.experience() == 0, "a form that does not grow keeps none of it");
        helper.succeed();
    }

    /**
     * Every form that says what it becomes names one that exists.
     *
     * <p>The column holds an id rather than the constant, because Java will not let an
     * enum constant refer to one declared after it. That trade gives up the compiler's
     * check, so the check is here instead — a name with no form behind it would otherwise
     * be found by somebody standing in the End with a nether star.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void everyFormBecomesOneThatExists(GameTestHelper helper) {
        int found = 0;
        for (Kind kind : Kind.values()) {
            if (!kind.rawBecomes().isEmpty()) {
                check(kind.becomes().isPresent(),
                        kind.id() + " says it becomes " + kind.rawBecomes() + ", which is nothing");
                found++;
            }
        }
        check(found > 0, "at least one form should have somewhere to go");
        check(Kind.PERFECT.becomes().orElse(null) == Kind.SUPER_PERFECT,
                "and Perfect's somewhere is Super Perfect");

        // A form that can end itself has to be one that can be fed, or it could never
        // reach the state that allows it.
        for (Kind kind : Kind.values()) {
            check(kind.becomes().isEmpty() || kind.grows(),
                    kind.id() + " can end itself but can never be ready to");
        }
        helper.succeed();
    }

    /**
     * A chest that has not taken in enough will not light, and one that has will.
     *
     * <p>The condition is the whole cost of the step. Everything else about it — the star,
     * the dimension — gates where and how, and this is the part that had to be earned.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void onlyAFullOneWillLight(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper, KIND);
        check(!chest.lit(), "it should not start out going");
        check(!chest.light(), "and an empty one should refuse");
        check(!chest.lit(), "and still not be going");

        Player rich = helper.makeMockPlayer(GameType.SURVIVAL);
        rich.giveExperienceLevels(200);
        chest.absorb(rich);
        check(chest.grown() == 1.0F, "now it has taken in all it can use");

        check(chest.light(), "so it should light");
        check(chest.lit(), "and be going");
        check(!chest.light(), "and lighting it twice should do nothing");

        // A form with nowhere to go never lights, however full it is.
        CellaBlockEntity larva = place(helper, Kind.LARAVEL);
        check(!larva.light(), "a form with nowhere to go should refuse");
        helper.succeed();
    }

    /**
     * The wave walks the surface of a cube and never the inside of it.
     *
     * <p>Which is the difference between a step costing what it reaches and a step costing
     * everything it encloses: the last shell of a hundred-block wave is a quarter of a
     * million positions on the surface and eight million inside it. This asks the shape
     * rather than the timing, because a timing test on a build machine says nothing.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void theWaveWalksSurfacesAndNotVolumes(GameTestHelper helper) {
        for (int out = 1; out <= 12; out++) {
            int surface = 0;
            for (int x = -out; x <= out; x++) {
                for (int y = -out; y <= out; y++) {
                    for (int z = -out; z <= out; z++) {
                        if (Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z))) == out) {
                            surface++;
                        }
                    }
                }
            }
            check(surface == Blast.surfaceOf(out),
                    out + " out should be " + surface + " positions, not " + Blast.surfaceOf(out));
        }
        helper.succeed();
    }

    /**
     * The wave clears everything from one block out to its reach, and stops there.
     *
     * <p><b>Written because a screenshot could be read two ways.</b> After the first one in
     * a real game there was cobblestone still standing directly under the chest, which is
     * either a platform somebody built afterwards or the first shells never running — and
     * those want opposite responses. The block below the centre is the first position the
     * wave ever touches, so asking about it settles which.
     *
     * <p>The centre itself is left, because that is what came back.
     */
    @GameTest(template = TestStructures.FLOOR, batch = WAVE_CLEARS)
    public static void theWaveClearsFromOneBlockOutToItsReach(GameTestHelper helper) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        BlockPos centre = helper.absolutePos(new BlockPos(2, 40, 2));
        int reach = 6;
        int side = reach + 3;

        for (int x = -side; x <= side; x++) {
            for (int y = -side; y <= side; y++) {
                for (int z = -side; z <= side; z++) {
                    level.setBlock(centre.offset(x, y, z),
                            net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
                }
            }
        }
        check(!level.getBlockState(centre.below()).isAir(), "the ground starts out solid");

        Blast wave = Blast.start(level, centre, reach);

        helper.succeedWhen(() -> {
            check(wave.over(), "the wave should have finished");
            check(!level.getBlockState(centre).isAir(),
                    "the centre is what survived, and is left alone");
            check(level.getBlockState(centre.below()).isAir(),
                    "the block under it is the first thing the wave touches");
            check(level.getBlockState(centre.above()).isAir(), "and the one over it");
            for (int out = 1; out <= reach; out++) {
                check(level.getBlockState(centre.offset(out, 0, 0)).isAir(),
                        out + " blocks out should be gone");
            }
            check(!level.getBlockState(centre.offset(reach + 2, 0, 0)).isAir(),
                    "and past the reach nothing is touched");
        });
    }

    /**
     * Every form can be reached from somewhere.
     *
     * <p><b>This is the test that should have existed already.</b> Imperfect has no recipe
     * on purpose — a Laravel that has eaten enough becomes one — but nothing implemented
     * that, so for a long while there was no way to obtain an Imperfect at all, and since
     * every form above it is fused out of Imperfects, the whole ladder above the first rung
     * was unreachable outside creative. Nothing failed and nothing said so: the recipes
     * that existed were all correct, and the one that was missing was missing by design.
     *
     * <p>So the property is not "every form has a recipe" — the point of Imperfect is that
     * it does not — but <b>every form is either made or grown into</b>.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void everyFormCanBeReached(GameTestHelper helper) {
        for (Kind kind : Kind.values()) {
            if (kind.formula().isPresent()) {
                continue;
            }
            boolean grownInto = false;
            for (Kind from : Kind.values()) {
                grownInto |= from.becomes().orElse(null) == kind;
            }
            check(grownInto, kind.id() + " has no recipe and nothing grows into it, "
                    + "so nothing in a survival world can ever have one");
        }
        helper.succeed();
    }

    /**
     * A larva that has eaten enough grows up, there and then, keeping what is inside it.
     *
     * <p>The other way a form changes. Nothing is spent, nothing is destroyed and nothing
     * has to be done to it — which is what growing up is, and why it is one column rather
     * than a second mechanism. See {@link Kind#ripens}.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void aFedLarvaGrowsUp(GameTestHelper helper) {
        check(Kind.LARAVEL.ripens(), "a larva grows up on its own");
        check(Kind.LARAVEL.becomes().orElse(null) == Kind.IMPERFECT, "into an Imperfect");
        check(!Kind.PERFECT.ripens(), "and a Perfect does not - it has to be ended");

        CellaBlockEntity larva = place(helper, Kind.LARAVEL);
        larva.contents().setStackInSlot(3, new ItemStack(Items.GOLD_INGOT, 9));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.giveExperienceLevels(30);

        larva.absorb(player);
        check(larva.grown() == 1.0F, "it should be full");
        check(!larva.light(), "and a form that ripens can never be lit");

        check(larva.ripen(helper.getLevel()), "so it grows up instead");
        check(helper.getBlockState(WHERE).is(CellaRegistry.block(Kind.IMPERFECT).get()),
                "the block should be an Imperfect now");

        CellaBlockEntity grown = (CellaBlockEntity) helper.getBlockEntity(WHERE);
        check(grown.contents().getSlots() == Kind.IMPERFECT.slots(),
                "at the new size, not the old one: " + grown.contents().getSlots());
        check(grown.contents().getStackInSlot(0).getCount() == 9,
                "with what was inside it, closed up to the front");
        check(grown.experience() == 0, "and the feeding spent");
        helper.succeed();
    }

    /**
     * Breaking a larva gives back what it has eaten, as orbs.
     *
     * <p>The larva keeps nothing — that is why it is the one form that spills — and this
     * has to be part of that. ⚠ It sits oddly beside experience being one way, and the
     * reading that settles it is that a chest which must be destroyed to open it is not a
     * bank: nothing comes out that did not go in, and what it costs is the chest. Letting
     * it vanish instead would be the silent loss this mod keeps closing, and worse than for
     * items, because there is no picking experience up off the ground unless something puts
     * it there.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void aBrokenLarvaGivesBackWhatItAte(GameTestHelper helper) {
        check(!Kind.LARAVEL.keeps(), "the larva is the one that spills");
        CellaBlockEntity larva = place(helper, Kind.LARAVEL);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.giveExperienceLevels(10);
        int fed = larva.absorb(player);
        check(fed > 0, "it should have eaten something to give back");

        larva.spill(helper.getLevel(), helper.absolutePos(WHERE));
        check(larva.experience() == 0, "it should be holding none afterwards");

        int onTheFloor = helper.getLevel()
                .getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class,
                        new net.minecraft.world.phys.AABB(helper.absolutePos(WHERE)).inflate(8))
                .stream().mapToInt(net.minecraft.world.entity.ExperienceOrb::getValue).sum();
        check(onTheFloor == fed,
                "and all of it should be on the floor: " + onTheFloor + " of " + fed);
        helper.succeed();
    }

    /**
     * The wave kills what is inside it and leaves what is outside.
     *
     * <p>The blocks are the spectacle and this is the cost. It has an edge for the same
     * reason the crater does — something has to be far enough away — so both halves are
     * asked, since a sweep that killed everything loaded would be a very different mod.
     */
    @GameTest(template = TestStructures.FLOOR, batch = WAVE_KILLS)
    public static void theWaveKillsWhatIsInsideIt(GameTestHelper helper) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        BlockPos centre = helper.absolutePos(new BlockPos(2, 40, 2));
        int reach = 6;

        net.minecraft.world.entity.animal.Pig inside = spawn(level, centre.offset(3, 0, 0));
        net.minecraft.world.entity.animal.Pig outside = spawn(level, centre.offset(20, 0, 0));
        check(inside.isAlive() && outside.isAlive(), "both should start out alive");

        Blast wave = Blast.start(level, centre, reach);

        helper.succeedWhen(() -> {
            check(wave.over(), "the wave should have finished");
            check(inside.isRemoved(), "the one inside should be gone");
            check(outside.isAlive(), "and the one well outside should not be");

            // Gone rather than killed: a pig killed leaves porkchop, and a crater full of
            // what used to be standing in it is neither the picture nor reachable.
            check(helper.getLevel()
                            .getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                                    new net.minecraft.world.phys.AABB(centre).inflate(reach + 4))
                            .isEmpty(),
                    "and it should not have dropped anything");
        });
    }

    private static net.minecraft.world.entity.animal.Pig spawn(
            net.minecraft.server.level.ServerLevel level, BlockPos where) {
        net.minecraft.world.entity.animal.Pig pig =
                net.minecraft.world.entity.EntityType.PIG.create(level);
        pig.moveTo(where.getX() + 0.5, where.getY(), where.getZ() + 0.5, 0.0F, 0.0F);
        // Still, so that walking does not decide the test.
        pig.setNoAi(true);
        level.addFreshEntity(pig);
        return pig;
    }

    /**
     * Searching pages over what matched, and the slots underneath never find out.
     *
     * <p>Which is the whole of it: the page still hands out its own number of squares and
     * a slot is still an ordinary slot, and the only thing that changed is where each
     * square lands. Eighteen pages of mostly nothing becomes the handful that answered.
     *
     * <p>⚠ <b>Writing through a result has to land on the slot it came from</b>, not on the
     * position it happens to occupy in the results. That is the same fault the paging was
     * built to avoid — what is on the screen and what a click acts on being different
     * things — and it is easier to get wrong here, because the two numbers are no longer
     * even close.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void searchingPagesOverWhatMatched(GameTestHelper helper) {
        int size = 4;
        ItemStackHandler chest = new ItemStackHandler(200);
        chest.setStackInSlot(3, new ItemStack(Items.DIAMOND, 1));
        chest.setStackInSlot(60, new ItemStack(Items.DIAMOND_SWORD, 1));
        chest.setStackInSlot(61, new ItemStack(Items.EMERALD, 1));
        chest.setStackInSlot(150, new ItemStack(Items.DIAMOND_BLOCK, 1));

        Window window = Window.onto(chest, size);
        check(!window.searching(), "it starts out showing the chest");
        check(window.pages() == 50, "all fifty pages of it: " + window.pages());

        window.search("diamond");
        check(window.searching(), "and then it is showing results");
        check(window.pages() == 1, "three hits fit on one page: " + window.pages());
        check(window.onThisPage() == 3, "and three squares of it are real: " + window.onThisPage());
        check(window.getStackInSlot(0).is(Items.DIAMOND), "in the chest's own order");
        check(window.getStackInSlot(1).is(Items.DIAMOND_SWORD), "a sword is a diamond one");
        check(window.getStackInSlot(2).is(Items.DIAMOND_BLOCK), "and so is a block");
        check(window.getStackInSlot(3).isEmpty(), "the fourth square is nothing");
        check(!window.holds(3), "and refuses to be one");

        // The emerald was never a result, and the write lands where the item lives.
        window.setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 1));
        check(chest.getStackInSlot(60).is(Items.GOLD_INGOT),
                "writing through a result reaches the slot it came from");
        check(chest.getStackInSlot(1).isEmpty(), "and not the one it sits at on screen");

        // More matches than a page, so results page too. The sword became gold above, so
        // what is left is the one diamond, the nine added here and the block: eleven.
        for (int slot = 100; slot < 109; slot++) {
            chest.setStackInSlot(slot, new ItemStack(Items.DIAMOND, 1));
        }
        window.search("diamond");
        check(window.pages() == 3, "eleven hits over three pages: " + window.pages());
        window.openAt(2);
        check(window.onThisPage() == 3, "the last of which is short: " + window.onThisPage());
        check(window.getStackInSlot(2).is(Items.DIAMOND_BLOCK), "ending where the chest does");

        window.search("");
        check(!window.searching(), "an empty search puts the chest back");
        check(window.pages() == 50, "all of it: " + window.pages());
        helper.succeed();
    }

    /**
     * The last page of an answer is filled out with somewhere to put things.
     *
     * <p>Six results on a page of a hundred and ninety-two used to leave the rest bare, and
     * bare is both the wrong answer to <i>is that all of them</i> — it is what an empty
     * chest looks like too — and nowhere to drop anything. The squares after the last
     * result are spare slots of the chest, so they take what is put on them.
     *
     * <p>⚠ A search that found nothing gets none of them: a grid of empty boxes reads as
     * items that cannot be seen, and there are words for that case instead.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void theLastPageOfAnAnswerHasSomewhereToPutThings(GameTestHelper helper) {
        int size = 4;
        Sorted chest = new Sorted(200);
        chest.insertItem(0, new ItemStack(Items.DIAMOND, 1), false);
        chest.insertItem(0, new ItemStack(Items.DIAMOND_SWORD, 1), false);
        chest.insertItem(0, new ItemStack(Items.EMERALD, 1), false);

        Window window = Window.onto(chest, size);
        window.search("diamond");
        check(window.onThisPage() == size,
                "two results and the page filled out: " + window.onThisPage());
        check(window.getStackInSlot(2).isEmpty(), "the spare squares hold nothing");
        check(window.holds(2), "but they are slots, not a picture of slots");

        window.setStackInSlot(2, new ItemStack(Items.COAL, 5));
        check(chest.getStackInSlot(0).is(Items.COAL) && chest.getStackInSlot(0).getCount() == 5,
                "and what is put on one goes where its kind belongs");

        window.search("netherite");
        check(window.onThisPage() == 0, "a search that answered nothing gets no squares");
        helper.succeed();
    }

    /**
     * A search does not need the server to have a language.
     *
     * <p>⚠ <b>A dedicated server never loaded a resource pack.</b> Asked for the name of a
     * modded item it hands back the translation key, so a search matched only against the
     * name on screen works in single player and comes back empty on the server — which is
     * where a chest of this size is most likely to be. The registry name is always there,
     * and is what the chest is already ordered by.
     *
     * <p>The name on screen is still matched, for where there is one. Neither is dropped.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void searchingDoesNotNeedALanguage(GameTestHelper helper) {
        int size = 4;
        ItemStackHandler chest = new ItemStackHandler(200);
        chest.setStackInSlot(0, new ItemStack(Items.DIAMOND_SWORD, 1));
        chest.setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 1));
        chest.setStackInSlot(2, new ItemStack(Items.STONE, 1));

        Window window = Window.onto(chest, size);

        // The registry path, exactly as it is spelled.
        window.search("gold_ingot");
        check(window.onThisPage() == 1, "the registry path finds it: " + window.onThisPage());
        check(window.getStackInSlot(0).is(Items.GOLD_INGOT), "and it is the gold");

        // And as it is typed, with the underscore read as the space it stands for.
        window.search("diamond sword");
        check(window.onThisPage() == 1, "an underscore reads as a space: " + window.onThisPage());
        check(window.getStackInSlot(0).is(Items.DIAMOND_SWORD), "and it is the sword");

        // A word that is in neither name finds nothing rather than everything.
        window.search("netherite");
        check(window.onThisPage() == 0, "and a word for nothing finds nothing");
        helper.succeed();
    }

    /**
     * Results are maintained against the chest rather than written once and trusted.
     *
     * <p><b>A result list is a claim, and the chest can falsify it.</b> Emptying a slot the
     * search turned up used to leave its number in the list pointing at nothing — a square
     * of results showing nothing at all — and sorting rewrote every slot underneath every
     * number at once, so the same squares came back holding items that had never matched.
     *
     * <p><b>One slot at a time where one slot moved, the whole question where more did.</b>
     * Following a hopper by rebuilding is a fifth of a million name comparisons a tick on a
     * Cella Max; following a sort slot by slot is the same work done badly. Both are here.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void resultsFollowTheChest(GameTestHelper helper) {
        int size = 4;
        ItemStackHandler chest = new ItemStackHandler(200);
        chest.setStackInSlot(3, new ItemStack(Items.DIAMOND, 1));
        chest.setStackInSlot(60, new ItemStack(Items.DIAMOND_SWORD, 1));
        chest.setStackInSlot(150, new ItemStack(Items.DIAMOND_BLOCK, 1));

        Window window = Window.onto(chest, size);
        window.search("diamond");
        check(window.onThisPage() == 3, "three hits to begin with: " + window.onThisPage());

        // The middle one is taken out from underneath.
        chest.setStackInSlot(60, ItemStack.EMPTY);
        check(window.changed(60), "emptying a result moves the results");
        check(window.onThisPage() == 2, "the square goes rather than going blank: "
                + window.onThisPage());
        check(window.getStackInSlot(1).is(Items.DIAMOND_BLOCK), "and the rest close up");

        // One arrives between two that are already there.
        chest.setStackInSlot(20, new ItemStack(Items.DIAMOND_AXE, 1));
        check(window.changed(20), "a new match moves them too");
        check(window.getStackInSlot(0).is(Items.DIAMOND), "the chest's order is kept");
        check(window.getStackInSlot(1).is(Items.DIAMOND_AXE), "the new one at its own place");
        check(window.getStackInSlot(2).is(Items.DIAMOND_BLOCK), "and not at the end");

        // Something that was never an answer is not one now.
        chest.setStackInSlot(70, new ItemStack(Items.EMERALD, 1));
        check(!window.changed(70), "what does not match does not move the results");
        check(window.onThisPage() == 3, "still three: " + window.onThisPage());

        // A sort rewrites every slot at once, which is asked again rather than followed.
        Tidy.everything(chest);
        check(window.again(), "a sort is answered by asking the whole chest again");
        check(window.onThisPage() == 3, "the same three survive it: " + window.onThisPage());
        for (int square = 0; square < 3; square++) {
            check(!window.getStackInSlot(square).isEmpty(),
                    "and no square of a result page is empty: " + square);
        }
        helper.succeed();
    }

    /**
     * A reader whose page stops existing is moved onto one that does.
     *
     * <p>Results shrink under whoever is reading them — a hopper empties what they were
     * looking at and page three of three stops being there. The grid would draw as nothing
     * at all, and a blank page reads as items that cannot be seen rather than as a page
     * that is gone. So the reader lands on the last page there is.
     *
     * <p>⚠ <b>This is not the reset a new search does.</b> Asking a different question puts
     * you at the start of the answer; the answer changing under you moves you no further
     * than it has to.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void aPageThatStopsExistingLetsGoOfItsReader(GameTestHelper helper) {
        int size = 4;
        ItemStackHandler chest = new ItemStackHandler(200);
        for (int hit = 0; hit < 9; hit++) {
            chest.setStackInSlot(hit * 10, new ItemStack(Items.DIAMOND, 1));
        }

        Window window = Window.onto(chest, size);
        window.search("diamond");
        check(window.pages() == 3, "nine hits over three pages: " + window.pages());
        window.openAt(2);
        check(window.page() == 2, "reading the last of them");

        for (int hit = 0; hit < 5; hit++) {
            chest.setStackInSlot(hit * 10, ItemStack.EMPTY);
            window.changed(hit * 10);
        }
        check(window.pages() == 1, "four left on one page: " + window.pages());
        check(window.page() == 0, "and the reader came with them: " + window.page());
        check(window.onThisPage() == 4, "onto a page that is full: " + window.onThisPage());
        check(!window.getStackInSlot(0).isEmpty(), "and holds something");
        helper.succeed();
    }

    /**
     * The ladder only ever adds, except in the one place it was decided that it takes away.
     *
     * <p><b>This is what makes the table safe to write the way it is written.</b> Each
     * {@link Trait} is the whole answer for the forms that share it, rather than a list of
     * gains to be added up — which is only equivalent to the cumulative reading while every
     * column climbs. A step that quietly went backwards would leave a form less than the
     * one below it, and nothing would say so.
     *
     * <p>⚠ The exception is named rather than tolerated: <b>reach goes to nought at Super
     * Perfect</b>, because a form that has finished growing has no business reaching for
     * experience it cannot use. That is the one subtraction, and it is asked about here so
     * that a second one cannot arrive unnoticed.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void theLadderOnlyAdds(GameTestHelper helper) {
        Trait[] rungs = Trait.values();
        for (int at = 1; at < rungs.length; at++) {
            Trait below = rungs[at - 1];
            Trait above = rungs[at];
            String step = below + " -> " + above;

            check(above.resistance() >= below.resistance(), step + " got softer");
            check(!above.burns() || below.burns(), step + " started burning again");
            check(above.witherproof() || !below.witherproof(), step + " lost its wither tag");
            check(above.unbreakableAsAnItem() || !below.unbreakableAsAnItem(),
                    step + " stopped surviving as an item");
            check(!below.particular() || above.particular(), step + " stopped needing a tool");
            check(above.finds() || !below.finds(), step + " lost its search box");
            check(above.keptOnDeath() || !below.keptOnDeath(),
                    step + " stopped getting up again");

            // The one place it takes something away, and the only one.
            if (above.reach() < below.reach()) {
                check(above == Trait.SUPER_PERFECT && above.reach() == 0,
                        step + " lost reach, and Super Perfect is the only rung allowed to");
            }
        }

        // Searching arrives where turning pages stops working, which is a claim about
        // sizes and so worth stating as one.
        check(!Kind.IMPERFECT.trait().finds(), "four pages is not a reason to search");
        check(Kind.SEMI_PERFECT.trait().finds(), "eighteen is");
        check(Kind.IMPERFECT.pages() < Kind.SEMI_PERFECT.pages(),
                "and the second is the bigger of the two, which is why");

        // And the forms are wired to rungs in order, so the chain above describes them.
        check(Kind.LARAVEL.trait() == Trait.LARVA, "the larva is the larva");
        check(Kind.JUNIOR.trait() == Kind.PERFECT.trait(), "Junior is a Perfect");
        // ⚠ A rule and not a gap: nothing may have a property Super Perfect lacks, because
        // a Perfect Cell is stronger than a Cell Max and a ladder whose last rung outdid it
        // would be saying otherwise. Max buys room, which is what it is - bigger, and less.
        check(Kind.MAX.trait() == Kind.SUPER_PERFECT.trait(),
                "Max must not out-do Super Perfect, so it shares its trait");
        for (Kind kind : Kind.values()) {
            check(kind.trait().ordinal() <= Trait.SUPER_PERFECT.ordinal(),
                    kind.id() + " is above the ceiling, and there is not supposed to be one");
        }
        helper.succeed();
    }

    /**
     * The top of the ladder does not time out; everything below it does.
     *
     * <p>⚠ <b>Waiting is the commonest way one is lost</b> — commoner than fire or a
     * creeper, and the biggest source of the orphans the cleanup command exists for. So
     * this is asked the way the game asks it: give the item a lifespan it has already
     * outlived and tick it, and see which one is still there.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void theTopOfTheLadderDoesNotTimeOut(GameTestHelper helper) {
        check(Kind.SUPER_PERFECT.trait().unbreakableAsAnItem(), "a Super Perfect does not go");
        check(!Kind.PERFECT.trait().unbreakableAsAnItem(), "a Perfect does");

        net.minecraft.world.entity.item.ItemEntity lasting = dropped(helper, Kind.SUPER_PERFECT);
        net.minecraft.world.entity.item.ItemEntity fading = dropped(helper, Kind.PERFECT);
        for (int tick = 0; tick < 12; tick++) {
            lasting.tick();
            fading.tick();
        }

        check(lasting.isAlive(), "the Super Perfect should still be lying there");
        check(fading.isRemoved(), "and the Perfect should be gone: " + fading.isAlive());

        // ⚠ And still keeping time, because the bob and the spin are read off the same
        // clock the lifetime is. Stopping it is the obvious way to make an item last and it
        // leaves one hanging motionless in the air; this winds it back instead.
        int was = lasting.getAge();
        lasting.tick();
        check(lasting.getAge() != was, "and still moving: the clock has to keep running");
        helper.succeed();
    }

    /** One on the floor, with a lifespan short enough that a few ticks settle it. */
    private static net.minecraft.world.entity.item.ItemEntity dropped(GameTestHelper helper,
            Kind kind) {
        net.minecraft.world.entity.item.ItemEntity entity =
                new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(), 0, 0, 0,
                        new ItemStack(CellaRegistry.item(kind).get()));
        entity.moveTo(helper.absolutePos(WHERE).getX() + 0.5,
                helper.absolutePos(WHERE).getY() + 1.0,
                helper.absolutePos(WHERE).getZ() + 0.5);
        entity.lifespan = 3;
        entity.setPickUpDelay(0);
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }

    /**
     * A Cella item stamped as having finished growing, which every recipe now requires.
     *
     * <p>Real ones get there by being placed, fed and broken. A test that did that eight
     * times over would be testing the feeding, which has its own tests; what these want is
     * an ingredient that qualifies.
     */
    private static ItemStack grown(Kind kind, int used) {
        ItemStack stack = new ItemStack(CellaRegistry.item(kind).get());
        stack.set(CellaRegistry.KEPT.get(),
                new Held(java.util.List.of(java.util.UUID.randomUUID()), used, kind.slots(),
                        kind.growth(), kind.growth()));
        return stack;
    }

    /** The store as it is on disk. See the note on the migration test. */
    private static CompoundTag store(CompoundTag... entries) {
        ListTag chests = new ListTag();
        for (CompoundTag entry : entries) {
            chests.add(entry);
        }
        CompoundTag tag = new CompoundTag();
        tag.put("Chests", chests);
        return tag;
    }

    /**
     * A death drop: an item entity that has been made and not yet added to anything.
     *
     * <p>Which is what the drops of a death are when they are offered round — removing one
     * from that collection is the difference between an item existing and never having.
     */
    private static net.minecraft.world.entity.item.ItemEntity dropping(GameTestHelper helper,
            ItemStack stack) {
        return new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(), 0, 0, 0, stack);
    }

    /** One chest in a store, with something in it so that it was worth filing. */
    private static java.util.UUID file(Kept kept, HolderLookup.Provider registries) {
        ItemStackHandler was = new ItemStackHandler(Kind.IMPERFECT.slots());
        was.setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 1));
        return kept.put(was.serializeNBT(registries), Kind.IMPERFECT, 1L, 0);
    }

    /**
     * A Cella lying on the floor naming those chests, with its ordinary lifespan.
     *
     * <p>Not {@link #dropped}, which cuts the life short so that a few ticks settle it.
     * These are about being destroyed rather than about running out.
     */
    private static net.minecraft.world.entity.item.ItemEntity carrying(GameTestHelper helper,
            Kind kind, java.util.List<java.util.UUID> names) {
        ItemStack stack = new ItemStack(CellaRegistry.item(kind).get());
        stack.set(CellaRegistry.KEPT.get(),
                new Held(names, names.size(), kind.slots(), 0, 0));
        net.minecraft.world.entity.item.ItemEntity entity =
                new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(), 0, 0, 0, stack);
        entity.moveTo(helper.absolutePos(WHERE).getX() + 0.5,
                helper.absolutePos(WHERE).getY() + 1.0,
                helper.absolutePos(WHERE).getZ() + 0.5);
        entity.setPickUpDelay(0);
        helper.getLevel().addFreshEntity(entity);
        return entity;
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
