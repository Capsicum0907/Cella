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
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

@GameTestHolder(Cella.MODID)
@PrefixGameTestTemplate(false)
public final class CellaTests {
    private static final BlockPos WHERE = new BlockPos(2, 1, 2);

    private static final int PLAYER_SLOTS = 36;

    private static final Kind KIND = Kind.PERFECT;

    private CellaTests() {
    }

    private static final int LATER = 2;

    private static final String WAVE_CLEARS = "waveClears";

    private static final String WAVE_KILLS = "waveKills";

    private static final String WAVE_NAMES = "waveNames";

    private static final String WAVE_LADDER = "waveLadder";

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

    @GameTest(template = TestStructures.FLOOR)
    public static void turningThePageMovesWhatTheSlotsShow(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        int page = KIND.pageSize();

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

        menu.slots.get(2).set(new ItemStack(Items.COAL, 3));
        check(chest.contents().getStackInSlot(0).is(Items.COAL),
                "what is put in goes where its kind belongs, coal sorting before diamond");
        check(chest.contents().getStackInSlot(1).is(Items.DIAMOND),
                "and the diamonds move along to make room for it");
        helper.succeed();
    }

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

        window.setStackInSlot(5, new ItemStack(Items.STONE, 1));
        window.openAt(0);
        check(window.getStackInSlot(5).is(Items.STONE),
                "and the contents of a full page are not dropped for arriving first");
        helper.succeed();
    }

    @GameTest(template = TestStructures.FLOOR)
    public static void theMenuCountsItsPagesFromTheChest(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        int odd = KIND.pageSize() * 3;
        chest.contents().setSize(odd);

        CellaMenu menu = CellaMenu.at(1, player.getInventory(), helper.absolutePos(WHERE), odd,
                CellaConfig.rows(KIND), CellaConfig.columns(KIND));
        check(menu.slots.size() == KIND.pageSize() + PLAYER_SLOTS,
                "the menu is still a page tall: " + menu.slots.size());
        check(menu.pages() == 3, "and counted its pages from the chest, not from the config");
        helper.succeed();
    }

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

    @GameTest(template = TestStructures.FLOOR)
    public static void aHopperSeesTheWholeChest(GameTestHelper helper) {
        place(helper);
        IItemHandler offered = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                helper.absolutePos(WHERE), null);

        check(offered != null, "the chest should offer an item handler");
        check(offered.getSlots() == KIND.slots(),
                "and it should be every page, not one: " + offered.getSlots());

        int far = KIND.pageSize() * (KIND.pages() - 1);
        check(offered.insertItem(far, new ItemStack(Items.REDSTONE, 7), false).isEmpty(),
                "and an offer made past the first page is taken whole");

        check(offered.getStackInSlot(0).is(Items.REDSTONE)
                        && offered.getStackInSlot(0).getCount() == 7,
                "and is in the chest, where redstone goes");
        helper.succeed();
    }

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

    @GameTest(template = TestStructures.FLOOR)
    public static void theLarvaSpillsBecauseItCanBePickedUp(GameTestHelper helper) {
        check(!Kind.LARVAL.keeps(), "this test is about the one that does not keep");
        CellaBlockEntity chest = place(helper, Kind.LARVAL);
        chest.contents().setStackInSlot(3, new ItemStack(Items.GOLD_INGOT, 11));

        helper.destroyBlock(WHERE);

        helper.succeedWhen(() -> {
            java.util.List<ItemStack> dropped = dropped(helper);
            long gold = dropped.stream().filter(stack -> stack.is(Items.GOLD_INGOT))
                    .mapToLong(ItemStack::getCount).sum();
            check(gold == 11, "the eleven gold should be on the floor, not " + gold);
            check(dropped.stream().filter(stack -> stack.is(CellaRegistry.item(Kind.LARVAL).get()))
                            .noneMatch(stack -> stack.has(CellaRegistry.KEPT.get())),
                    "and the chest should not be carrying a name it never filed");
        });
    }

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

    @GameTest(template = TestStructures.FLOOR)
    public static void aScreenCutsThePageDownAndNeverUp(GameTestHelper helper) {
        Room small = new Room(6, 9);
        check(small.rowsFor(KIND) == 6, "a short screen should get six rows of a Perfect");
        check(small.columnsFor(KIND) == 9, "and nine columns");

        Room wall = new Room(64, 64);
        check(wall.rowsFor(KIND) == CellaConfig.rows(KIND),
                "a big screen should get the kind's own shape");
        check(wall.columnsFor(KIND) == CellaConfig.columns(KIND), "in both directions");
        check(wall.rowsFor(Kind.LARVAL) == CellaConfig.rows(Kind.LARVAL),
                "and a small chest should not be stretched to fill it");

        Player nobody = helper.makeMockPlayer(GameType.SURVIVAL);
        check(Room.of(nobody).equals(Room.ANY),
                "a player who has not said anything should have nothing cut down");
        helper.succeed();
    }

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

    @GameTest(template = TestStructures.FLOOR)
    public static void theItemSaysHowFullTheChestWas(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper, KIND);
        for (int slot = 0; slot < 300; slot++) {
            chest.contents().setStackInSlot(slot, new ItemStack(Items.STONE, 1));
        }

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

        check(held.chests().size() == 8, "eight names, one per Cella: " + held.chests());
        check(held.chests().getFirst().equals(filed.getFirst())
                        && held.chests().get(7).equals(filed.getLast()),
                "the two that were filed at the ends they were laid at: " + held.chests());
        check(held.used() == 2, "two slots between them: " + held.used());
        check(held.slots() == Kind.SEMI_PERFECT.slots(),
                "in the room a Semi-Perfect has: " + held.slots());

        check(held.experience() == 0,
                "a fusion should arrive unfed: " + held.experience());
        check(held.grows() && held.growth() == Kind.SEMI_PERFECT.growth(),
                "with its own threshold to fill, so the bar means something");

        check(kept.take(filed.getFirst()).isPresent(), "assembling must not have spent them");
        kept.take(filed.getLast());
        helper.succeed();
    }

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

        grid.set(0, new ItemStack(CellaRegistry.item(Kind.IMPERFECT).get()));
        check(!fusion.matches(CraftingInput.of(3, 3, grid), helper.getLevel()),
                "and one that has never been fed is not");
        grid.set(0, grown(Kind.IMPERFECT, 0));

        grid.get(0).set(CellaRegistry.KEPT.get(), new Held(
                java.util.List.of(java.util.UUID.randomUUID()),
                Kind.SEMI_PERFECT.slots() + 1, Kind.SEMI_PERFECT.slots() + 1,
                Kind.IMPERFECT.growth(), Kind.IMPERFECT.growth()));
        check(!fusion.matches(CraftingInput.of(3, 3, grid), helper.getLevel()),
                "and one that would not fit is not");
        helper.succeed();
    }

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

        check(chest.fuse(registries, filed).isEmpty(), "all of it should fit");
        check(chest.plan().untouched(chest.contents().getSlots()),
                "two undivided ones make one undivided one: "
                        + chest.plan().over(chest.contents().getSlots()));

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

    @GameTest(template = TestStructures.FLOOR)
    public static void fusingKeepsEveryPartition(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper, KIND);
        var registries = helper.getLevel().registryAccess();
        int lc = Plan.LC;
        int each = Kind.SEMI_PERFECT.slots() / lc;

        ItemStackHandler split = new ItemStackHandler(Kind.SEMI_PERFECT.slots());
        split.setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 5));
        split.setStackInSlot(2 * lc, new ItemStack(Items.DIAMOND, 7));
        CompoundTag first = split.serializeNBT(registries);
        first.put(CellaBlockEntity.PLAN, Plan.of(java.util.List.of(
                new Plan.Partition("gold", DyeColor.YELLOW, 2),
                new Plan.Partition("gems", DyeColor.CYAN, 3))));

        ItemStackHandler whole = new ItemStackHandler(Kind.SEMI_PERFECT.slots());
        whole.setStackInSlot(900, new ItemStack(Items.STONE, 9));
        CompoundTag second = whole.serializeNBT(registries);

        ItemStackHandler another = new ItemStackHandler(Kind.SEMI_PERFECT.slots());
        another.setStackInSlot(40, new ItemStack(Items.APPLE, 4));
        CompoundTag third = another.serializeNBT(registries);

        java.util.List<Kept.Chest> filed = java.util.List.of(new Kept.Chest(second, 0),
                new Kept.Chest(first, 0), new Kept.Chest(third, 0));
        check(chest.fuse(registries, filed).isEmpty(), "all of it should fit");

        java.util.List<Plan.Partition> carved =
                chest.plan().over(chest.contents().getSlots());
        check(carved.size() == 3, "both of the divided one and one for the rest: " + carved);
        check(carved.get(0).name().equals("gold") && carved.get(0).length() == 2
                        && carved.get(0).colour() == DyeColor.YELLOW,
                "the first keeps its name, colour and size: " + carved.get(0));
        check(carved.get(1).name().equals("gems") && carved.get(1).length() == 3,
                "and so does the second: " + carved.get(1));
        check(carved.get(2).length() == 2 * each && carved.get(2).name().isEmpty(),
                "the two undivided ones become one, after it: " + carved.get(2));

        check(chest.contents().getStackInSlot(0).is(Items.GOLD_INGOT),
                "the gold stays in its own: " + chest.contents().getStackInSlot(0));
        check(chest.contents().getStackInSlot(2 * lc).is(Items.DIAMOND),
                "the diamonds in theirs: " + chest.contents().getStackInSlot(2 * lc));
        check(chest.contents().getStackInSlot(5 * lc).is(Items.APPLE)
                        && chest.contents().getStackInSlot(5 * lc + 1).is(Items.STONE),
                "and the apples and stone together in the last: "
                        + chest.contents().getStackInSlot(5 * lc));
        check(chest.contents().used(2) == 2, "which holds just those: " + chest.contents().used(2));
        helper.succeed();
    }

    @GameTest(template = TestStructures.FLOOR)
    public static void spawningLeavesTheContentsWithTheParent(GameTestHelper helper) {
        var recipes = helper.getLevel().getServer().getRecipeManager();
        var junior = recipes.byKey(ResourceLocation.fromNamespaceAndPath(Cella.MODID, "junior"))
                .orElseThrow(() -> new GameTestAssertException("no junior recipe"));
        check(junior.value() instanceof Spawning, "a spawning, not a fusion");
        Spawning spawning = (Spawning) junior.value();

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

    @GameTest(template = TestStructures.FLOOR)
    public static void sortingReachesEveryPage(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        int page = KIND.pageSize();

        chest.contents().setStackInSlot(3, new ItemStack(Items.COBBLESTONE, 20));
        chest.contents().setStackInSlot(page + 7, new ItemStack(Items.COBBLESTONE, 30));
        chest.contents().setStackInSlot(page * 4 + 1, new ItemStack(Items.COBBLESTONE, 20));
        chest.contents().setStackInSlot(page * 2 + 5, new ItemStack(Items.DIAMOND, 9));

        Tidy.everything(chest.contents());

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

    @GameTest(template = TestStructures.FLOOR)
    public static void whatAPlayerMovesIsWrittenDown(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(1, new ItemStack(Items.COBBLESTONE, 40));

        chest.contents().insertItem(0, new ItemStack(Items.DIAMOND, 7), false);
        check(chest.ledger().size() == 0, "a hopper leaves no trace: " + chest.ledger().size());

        CellaMenu menu = CellaMenu.at(1, player.getInventory(), helper.absolutePos(WHERE),
                chest.contents().getSlots(),
                CellaConfig.rows(KIND), CellaConfig.columns(KIND));
        menu.clickMenuButton(player, CellaMenu.STOW);

        check(chest.ledger().size() == 1, "a button press does: " + chest.ledger().size());
        Ledger.Move move = chest.ledger().moves().getFirst();
        check(move.part() == 0, "in the only partition there is: " + move.part());
        check(move.kind().is(Items.COBBLESTONE), "the cobblestone that went in");
        check(move.before() == 0 && move.after() == 40,
                "from none to forty: " + move.before() + " -> " + move.after());
        check(move.moved() == 40, "which is forty arriving");
        helper.succeed();
    }

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

    @GameTest(template = TestStructures.FLOOR)
    public static void theShelfSaysWhatEachPartitionHolds(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        carve(chest, new Plan.Partition("front", DyeColor.RED, 1),
                new Plan.Partition("back", DyeColor.BLUE, 2));

        chest.contents().insertItem(0, new ItemStack(Items.STONE, 100), false);
        chest.contents().insertItem(0, new ItemStack(Items.APPLE, 3), false);
        chest.contents().insertItem(Plan.LC, new ItemStack(Items.DIAMOND, 1), false);

        Shelf all = Shelf.of(chest, Peek.LIST);
        check(all.slices().size() == 2, "two slices: " + all.slices().size());
        check(all.slices().get(0).name().equals("front"), "named");
        check(all.slices().get(0).dye() == DyeColor.RED, "and coloured");
        check(all.slices().get(0).slots() == Plan.LC, "a partition of one LC");
        check(all.slices().get(1).slots() == Plan.LC * 2, "and one of two");
        check(all.slices().get(0).used() == 3, "three slots used: "
                + all.slices().get(0).used());
        check(all.tallies().isEmpty(), "and nothing is counted unless it was asked for");

        Shelf one = Shelf.of(chest, 0);
        check(one.shown() == 0, "the first was asked for");
        check(one.tallies().size() == 2, "two kinds in it: " + one.tallies().size());
        check(one.tallies().get(0).kind().is(Items.APPLE), "apples first, being sorted");
        check(one.tallies().get(0).count() == 3, "three of them");
        check(one.tallies().get(1).kind().is(Items.STONE), "then the stone");
        check(one.tallies().get(1).count() == 100, "all hundred, across its two slots");

        Shelf other = Shelf.of(chest, 1);
        check(other.tallies().size() == 1, "the second holds one kind");
        check(other.tallies().get(0).kind().is(Items.DIAMOND), "which is the diamond");

        CellaBlockEntity plain = place(helper, Kind.SUPER_PERFECT);
        Shelf whole = Shelf.of(plain, Peek.LIST);
        check(whole.slices().size() == 1, "a fresh chest is one partition");
        check(whole.slices().get(0).slots() == plain.contents().getSlots(),
                "spanning the whole of it: " + whole.slices().get(0).slots());
        helper.succeed();
    }

    @GameTest(template = TestStructures.FLOOR)
    public static void theRecordSaysWhichPartition(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        carve(chest, new Plan.Partition("front", DyeColor.RED, 1),
                new Plan.Partition("back", DyeColor.BLUE, 1));

        chest.byHand(() -> {
            chest.contents().insertItem(0, new ItemStack(Items.STONE, 4), false);
            chest.contents().insertItem(Plan.LC, new ItemStack(Items.APPLE, 6), false);
        });

        check(chest.ledger().size() == 2, "two movements: " + chest.ledger().size());
        Ledger.Move first = chest.ledger().moves().get(0);
        Ledger.Move second = chest.ledger().moves().get(1);
        check(first.part() == 0 && first.kind().is(Items.STONE), "the stone in the first");
        check(second.part() == 1 && second.kind().is(Items.APPLE), "the apple in the second");
        check(first.moved() == 4 && second.moved() == 6,
                "with how many moved: " + first.moved() + " " + second.moved());

        check(Plan.fit("a".repeat(30)).length() == Plan.LONGEST,
                "and a name is cut to twenty");
        helper.succeed();
    }

    @GameTest(template = TestStructures.FLOOR)
    public static void onlyOnePartitionIsOpenToTheOutside(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        int lc = Plan.LC;

        check(chest.outlet().getSlots() == chest.contents().getSlots(),
                "a fresh chest offers the whole of itself");

        carve(chest, new Plan.Partition("front", DyeColor.RED, 1),
                new Plan.Partition("back", DyeColor.BLUE, 1));

        check(chest.outlet().getSlots() == 0, "divided, nothing is offered until it is assigned");
        check(chest.outlet().insertItem(0, new ItemStack(Items.STONE, 1), false).getCount() == 1,
                "and an offer comes back whole");

        chest.replan(() -> chest.plan().assign(1));
        check(chest.outlet().getSlots() == lc, "assigned, one partition is offered: "
                + chest.outlet().getSlots());
        check(chest.outlet().insertItem(0, new ItemStack(Items.STONE, 5), false).isEmpty(),
                "and it takes what it is given");
        check(chest.contents().used(1) == 1, "into the assigned one: " + chest.contents().used(1));
        check(chest.contents().used(0) == 0, "and not the other: " + chest.contents().used(0));

        check(chest.outlet().getStackInSlot(0).is(Items.STONE), "which is what it reads back");
        helper.succeed();
    }

    @GameTest(template = TestStructures.FLOOR)
    public static void onlyTheBigOnesDivide(GameTestHelper helper) {
        for (Kind kind : Kind.values()) {
            boolean big = kind != Kind.LARVAL && kind != Kind.IMPERFECT;
            check(kind.trait().divides() == big,
                    kind.id() + " divides: " + kind.trait().divides());
        }

        CellaBlockEntity small = place(helper, Kind.IMPERFECT);
        check(!small.divides(), "an Imperfect does not divide");
        check(!small.divide(new Plan.Partition("no", DyeColor.RED, 1)), "and refuses to");
        check(small.plan().over(small.contents().getSlots()).size() == 1,
                "but is still one partition inside");
        check(small.contents().parts() == 1, "which is what the store sees");
        helper.succeed();
    }

    @GameTest(template = TestStructures.FLOOR)
    public static void partitionsComeBackWithTheContents(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper, KIND);
        carve(chest, new Plan.Partition("kit", DyeColor.LIME, 2),
                new Plan.Partition("ore", DyeColor.PURPLE, 3));
        chest.contents().insertItem(0, new ItemStack(Items.STONE, 5), false);
        chest.replan(() -> chest.plan().assign(1));

        Kept kept = Kept.of(helper.getLevel()).orElseThrow(
                () -> new GameTestAssertException("there should be a store on a server"));
        chest.handOver(helper.getLevel(), helper.absolutePos(WHERE));
        check(chest.plan().over(chest.contents().getSlots()).size() == 1,
                "the broken one is a fresh chest again");

        Held held = dropped(helper).stream()
                .map(stack -> stack.get(CellaRegistry.KEPT.get()))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException("no chest with a name"));
        CellaBlockEntity back = place(helper, KIND);
        back.restore(helper.getLevel().registryAccess(),
                kept.take(held.chests().getFirst()).orElseThrow(
                        () -> new GameTestAssertException("the contents should be there")));

        java.util.List<Plan.Partition> carved =
                back.plan().over(back.contents().getSlots());
        check(carved.size() == 2, "both partitions came back: " + carved.size());
        check(carved.get(0).name().equals("kit"), "named");
        check(carved.get(1).colour() == DyeColor.PURPLE, "and coloured");
        check(carved.get(1).length() == 3, "with their sizes");
        check(back.plan().assigned() == 1, "and the outlet with them");
        check(back.contents().getStackInSlot(0).is(Items.STONE), "contents too");
        helper.succeed();
    }

    @GameTest(template = TestStructures.FLOOR)
    public static void fourEmptyThenTwoHalves(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper, Kind.JUNIOR);
        carve(chest, new Plan.Partition("p0", DyeColor.RED, 0),
                new Plan.Partition("p1", DyeColor.RED, 0),
                new Plan.Partition("p2", DyeColor.RED, 0),
                new Plan.Partition("p3", DyeColor.RED, 0));
        check(chest.plan().over(chest.contents().getSlots()).size() == 4,
                "four of them: " + chest.plan().over(chest.contents().getSlots()).size());

        check(chest.resize(0, new Plan.Partition("p0", DyeColor.RED, 32)),
                "the first grows to thirty-two");

        check(chest.resize(1, new Plan.Partition("p1", DyeColor.BLUE, 32)),
                "and the second takes the other half");
        check(chest.spare() == 0, "which leaves nothing free: " + chest.spare());
        helper.succeed();
    }

    @GameTest(template = TestStructures.FLOOR)
    public static void twoHalvesFitExactly(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper, Kind.JUNIOR);
        int all = chest.contents().getSlots() / Plan.LC;
        check(all == 64, "a Cella Jr. is sixty-four LC: " + all);

        carve(chest, new Plan.Partition("one", DyeColor.RED, 0));
        check(chest.resize(0, new Plan.Partition("one", DyeColor.RED, 32)),
                "grown to half the chest");

        check(chest.firstGap() == 32, "the gap starts at thirty-two: " + chest.firstGap());
        check(chest.divide(new Plan.Partition("two", DyeColor.BLUE, 0)), "an empty second");
        check(chest.resize(1, new Plan.Partition("two", DyeColor.BLUE, 32)),
                "and the other half fits exactly");

        check(chest.plan().over(chest.contents().getSlots()).get(1).length() == 32,
                "which is what it says: "
                        + chest.plan().over(chest.contents().getSlots()).get(1).length());
        helper.succeed();
    }

    @GameTest(template = TestStructures.FLOOR)
    public static void eachPartitionIsItsOwnStore(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Sorted contents = chest.contents();
        int lc = Plan.LC;

        carve(chest, new Plan.Partition("front", DyeColor.RED, 1),
                new Plan.Partition("back", DyeColor.BLUE, 2));
        check(contents.parts() == 2, "two partitions: " + contents.parts());
        check(chest.divide(new Plan.Partition("over", DyeColor.LIME, 3)),
                "a third one takes the room that is left");
        check(chest.plan().start(2) == 3,
                "which begins after the two already there: " + chest.plan().start(2));
        check(chest.undivide(2), "and it goes away again");
        check(contents.parts() == 2, "and taking it away leaves two: " + contents.parts());

        contents.insertItem(0, new ItemStack(Items.STONE, 10), false);
        contents.insertItem(lc, new ItemStack(Items.APPLE, 3), false);

        check(contents.getStackInSlot(0).is(Items.STONE), "the stone is in the first");
        check(contents.getStackInSlot(lc).is(Items.APPLE), "the apple in the second");
        check(contents.used(0) == 1 && contents.used(1) == 1,
                "one slot each: " + contents.used(0) + " " + contents.used(1));

        contents.insertItem(0, new ItemStack(Items.APPLE, 5), false);
        check(contents.getStackInSlot(0).is(Items.APPLE), "the first partition sorts itself");
        check(contents.getStackInSlot(1).is(Items.STONE), "with its own stone after it");
        check(contents.getStackInSlot(lc).is(Items.APPLE), "and the second is untouched");

        ItemStack over = contents.insertItem(0, new ItemStack(Items.DIRT, 64 * lc), false);
        check(!over.isEmpty(), "one partition of fifty-four slots cannot take all of it");
        check(contents.used(1) == 1, "and nothing landed next door: " + contents.used(1));
        helper.succeed();
    }

    @GameTest(template = TestStructures.FLOOR)
    public static void addingAPartitionKeepsWhatWasAlreadyThere(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Sorted contents = chest.contents();

        check(chest.plan().over(contents.getSlots()).size() == 1,
                "a fresh chest is one partition over the whole of it");

        contents.insertItem(0, new ItemStack(Items.STONE, 100), false);
        contents.insertItem(0, new ItemStack(Items.APPLE, 7), false);
        int had = items(contents);
        check(had == 107, "what went in: " + had);

        check(chest.divide(new Plan.Partition("new", DyeColor.RED, 0)),
                "an empty partition beside it");
        check(items(contents) == had, "leaves every item where it was: " + items(contents));
        check(contents.getStackInSlot(0).is(Items.APPLE), "and still in order");

        check(chest.undivide(1), "and it can be taken away again");
        check(items(contents) == had, "with nothing lost either way: " + items(contents));
        check(contents.used() == 3, "packed to the front: " + contents.used());
        helper.succeed();
    }

    @GameTest(template = TestStructures.FLOOR)
    public static void growingOneCarriesTheNextAlong(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Sorted contents = chest.contents();
        int lc = Plan.LC;

        carve(chest, new Plan.Partition("front", DyeColor.RED, 1),
                new Plan.Partition("back", DyeColor.BLUE, 1));

        contents.insertItem(0, new ItemStack(Items.STONE, 10), false);
        contents.insertItem(lc, new ItemStack(Items.APPLE, 7), false);
        check(contents.getStackInSlot(lc).is(Items.APPLE), "the apples sit in the second LC");

        check(chest.resize(0, new Plan.Partition("front", DyeColor.RED, 3)),
                "the first grows to three LC");
        check(chest.plan().start(1) == 3,
                "so the second begins at three: " + chest.plan().start(1));

        check(contents.getStackInSlot(0).is(Items.STONE)
                && contents.getStackInSlot(0).getCount() == 10,
                "the stone stayed where it was: " + contents.getStackInSlot(0));
        check(contents.getStackInSlot(3 * lc).is(Items.APPLE)
                && contents.getStackInSlot(3 * lc).getCount() == 7,
                "the apples came along: " + contents.getStackInSlot(3 * lc));
        check(contents.getStackInSlot(lc).isEmpty(), "leaving nothing behind");
        check(items(contents) == 17, "with nothing lost: " + items(contents));
        check(contents.used(0) == 1 && contents.used(1) == 1,
                "one slot each: " + contents.used(0) + " " + contents.used(1));

        check(chest.resize(0, new Plan.Partition("front", DyeColor.RED, 1)),
                "and it shrinks back again");
        check(contents.getStackInSlot(lc).is(Items.APPLE),
                "the apples come back with it: " + contents.getStackInSlot(lc));
        check(items(contents) == 17, "still nothing lost: " + items(contents));

        check(!chest.resize(0, new Plan.Partition("front", DyeColor.RED,
                        Plan.capacity(contents.getSlots()))),
                "it cannot take room the second one is holding");
        check(!chest.resize(0, new Plan.Partition("front", DyeColor.RED, 0)),
                "nor shrink onto what is in it");
        check(items(contents) == 17, "and a refusal moves nothing: " + items(contents));
        helper.succeed();
    }

    @GameTest(template = TestStructures.FLOOR)
    public static void takingOneAwayBringsTheRestForward(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Sorted contents = chest.contents();
        int lc = Plan.LC;

        carve(chest, new Plan.Partition("first", DyeColor.RED, 2),
                new Plan.Partition("second", DyeColor.BLUE, 1),
                new Plan.Partition("third", DyeColor.LIME, 1));

        contents.insertItem(2 * lc, new ItemStack(Items.APPLE, 5), false);
        contents.insertItem(3 * lc, new ItemStack(Items.DIAMOND, 9), false);
        check(contents.used(1) == 1 && contents.used(2) == 1,
                "a slot in each of the last two: " + contents.used(1) + " " + contents.used(2));

        check(chest.undivide(0), "the empty first one goes away");
        check(chest.plan().over(contents.getSlots()).size() == 2,
                "leaving two: " + chest.plan().over(contents.getSlots()).size());
        check(chest.plan().start(0) == 0 && chest.plan().start(1) == 1,
                "packed to the front: " + chest.plan().start(0) + " " + chest.plan().start(1));

        check(contents.getStackInSlot(0).is(Items.APPLE)
                && contents.getStackInSlot(0).getCount() == 5,
                "the apples slid forward: " + contents.getStackInSlot(0));
        check(contents.getStackInSlot(lc).is(Items.DIAMOND)
                && contents.getStackInSlot(lc).getCount() == 9,
                "and the diamonds after them: " + contents.getStackInSlot(lc));
        check(items(contents) == 14, "with nothing lost: " + items(contents));
        helper.succeed();
    }

    @GameTest(template = TestStructures.FLOOR)
    public static void aPlanTooSmallForWhatItFindsKeepsItAnyway(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStackHandler raw = new ItemStackHandler(KIND.slots());
        raw.setStackInSlot(0, new ItemStack(Items.STONE, 3));
        raw.setStackInSlot(60, new ItemStack(Items.APPLE, 4));
        raw.setStackInSlot(120, new ItemStack(Items.DIAMOND, 5));
        CompoundTag filed = raw.serializeNBT(registries);

        CompoundTag one = new CompoundTag();
        one.putString("Name", "small");
        one.putInt("Colour", DyeColor.RED.getId());
        one.putInt("Length", 1);
        ListTag carved = new ListTag();
        carved.add(one);
        CompoundTag plan = new CompoundTag();
        plan.put("Carved", carved);
        plan.putInt("Assigned", 0);
        filed.put("Plan", plan);

        CellaBlockEntity chest = place(helper);
        chest.restore(registries, new Kept.Chest(filed, 0));

        check(chest.plan().over(chest.contents().getSlots()).size() == 1,
                "one partition came back");
        check(items(chest.contents()) == 12,
                "and everything that was outside it came in: " + items(chest.contents()));
        check(chest.contents().used(0) == 3,
                "into three slots: " + chest.contents().used(0));
        helper.succeed();
    }

    @GameTest(template = TestStructures.FLOOR)
    public static void oneOfThemOpensStraightIntoIt(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        CellaMenu alone = CellaMenu.at(1, player.getInventory(), helper.absolutePos(WHERE),
                chest.contents().getSlots(),
                CellaConfig.rows(KIND), CellaConfig.columns(KIND));
        check(alone.viewing() == 0,
                "one partition opens into itself, not the list: " + alone.viewing());

        check(chest.resize(0, new Plan.Partition("only", DyeColor.RED, 2)),
                "and when that one is smaller than the chest");
        CellaMenu narrow = CellaMenu.at(2, player.getInventory(), helper.absolutePos(WHERE),
                chest.contents().getSlots(),
                CellaConfig.rows(KIND), CellaConfig.columns(KIND));
        check(narrow.viewing() == 0, "it still opens into itself: " + narrow.viewing());
        check(narrow.pages() == pagesOver(2 * Plan.LC),
                "over its own slots only: " + narrow.pages());

        carve(chest, new Plan.Partition("front", DyeColor.RED, 1),
                new Plan.Partition("back", DyeColor.BLUE, 1));
        CellaMenu both = CellaMenu.at(3, player.getInventory(), helper.absolutePos(WHERE),
                chest.contents().getSlots(),
                CellaConfig.rows(KIND), CellaConfig.columns(KIND));
        check(both.viewing() == Peek.LIST,
                "two of them open the list instead: " + both.viewing());
        helper.succeed();
    }

    private static int pagesOver(int slots) {
        int page = CellaConfig.rows(KIND) * CellaConfig.columns(KIND);
        return Math.max(1, (slots + page - 1) / page);
    }

    @GameTest(template = TestStructures.FLOOR)
    public static void aPartitionOfNothingHoldsNothing(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        check(chest.resize(0, new Plan.Partition("", Plan.FIRST, 0)),
                "an empty partition gives all its room back");
        check(chest.plan().over(chest.contents().getSlots()).get(0).length() == 0,
                "so it claims nothing");

        CellaMenu menu = CellaMenu.at(1, player.getInventory(), helper.absolutePos(WHERE),
                chest.contents().getSlots(),
                CellaConfig.rows(KIND), CellaConfig.columns(KIND));
        check(menu.viewing() == 0, "it still opens into itself: " + menu.viewing());
        check(!menu.slots.get(0).isActive(), "and shows no squares at all");
        check(!menu.slots.get(0).mayPlace(new ItemStack(Items.COAL, 1)),
                "and refuses anything put straight into it");

        int hand = menu.slots.size() - CellaConfig.PLAYER_COLUMNS;
        menu.slots.get(hand).set(new ItemStack(Items.COAL, 32));
        menu.quickMoveStack(player, hand);
        check(menu.slots.get(hand).getItem().getCount() == 32,
                "shift-clicking leaves the coal where it is: "
                        + menu.slots.get(hand).getItem());

        menu.slots.get(0).set(new ItemStack(Items.DIAMOND, 5));
        check(menu.slots.get(0).getItem().isEmpty(),
                "a square that is not there refuses what is put in it: "
                        + menu.slots.get(0).getItem());
        check(items(chest.contents()) == 0,
                "so nothing went into the chest either: " + items(chest.contents()));
        helper.succeed();
    }

    @GameTest(template = TestStructures.FLOOR)
    public static void everythingShowsEveryPartitionAtOnce(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Sorted contents = chest.contents();
        int lc = Plan.LC;

        carve(chest, new Plan.Partition("front", DyeColor.RED, 1),
                new Plan.Partition("back", DyeColor.BLUE, 1));

        contents.insertItem(0, new ItemStack(Items.STONE, 10), false);
        contents.insertItem(lc, new ItemStack(Items.APPLE, 7), false);
        contents.insertItem(lc, new ItemStack(Items.DIAMOND, 3), false);

        CellaMenu menu = CellaMenu.at(1, player.getInventory(), helper.absolutePos(WHERE),
                contents.getSlots(), CellaConfig.rows(KIND), CellaConfig.columns(KIND));
        menu.view(Peek.WHOLE);
        check(menu.viewing() == Peek.WHOLE, "the whole chest is on show: " + menu.viewing());

        check(menu.slots.get(0).getItem().is(Items.APPLE),
                "the second one's apples lead, since the order is the chest's: "
                        + menu.slots.get(0).getItem());
        check(menu.slots.get(1).getItem().is(Items.DIAMOND),
                "then its diamonds: " + menu.slots.get(1).getItem());
        check(menu.slots.get(2).getItem().is(Items.STONE),
                "then the first one's stone: " + menu.slots.get(2).getItem());
        check(!menu.slots.get(3).isActive(), "and nothing past what is there");

        int hand = menu.slots.size() - CellaConfig.PLAYER_COLUMNS;
        menu.slots.get(hand).set(new ItemStack(Items.COAL, 32));
        menu.quickMoveStack(player, hand);
        check(menu.slots.get(hand).getItem().getCount() == 32,
                "nothing is stored while no partition is picked to store it in: "
                        + menu.slots.get(hand).getItem());
        check(items(contents) == 20, "so the chest is unchanged: " + items(contents));

        menu.quickMoveStack(player, 0);
        check(items(contents) == 13, "but taking out works: " + items(contents));
        menu.broadcastChanges();
        check(menu.slots.get(0).getItem().is(Items.DIAMOND),
                "and the rest closes up: " + menu.slots.get(0).getItem());

        contents.insertItem(0, new ItemStack(Items.DIAMOND, 2), false);
        menu.broadcastChanges();
        check(menu.slots.get(0).getItem().is(Items.DIAMOND)
                        && menu.slots.get(1).getItem().is(Items.DIAMOND)
                        && menu.slots.get(2).getItem().is(Items.STONE),
                "diamonds put in the first land beside the second one's: "
                        + menu.slots.get(0).getItem() + ", " + menu.slots.get(1).getItem());
        check(menu.slots.get(0).getItem().getCount() == 2,
                "the first one's before the second one's: " + menu.slots.get(0).getItem());
        helper.succeed();
    }

    @GameTest(template = TestStructures.FLOOR)
    public static void loadingLeavesEachPartitionItsOwn(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        var registries = helper.getLevel().registryAccess();
        int lc = Plan.LC;

        carve(chest, new Plan.Partition("front", DyeColor.RED, 1),
                new Plan.Partition("back", DyeColor.BLUE, 1));
        chest.contents().insertItem(0, new ItemStack(Items.STONE, 10), false);
        chest.contents().insertItem(lc, new ItemStack(Items.APPLE, 7), false);

        CompoundTag saved = chest.saveWithoutMetadata(registries);
        CompoundTag written = saved.getCompound(CellaBlockEntity.CONTENTS);
        check(written.contains(Filing.PARTS),
                "it is saved a partition at a time: " + written.getAllKeys());
        whose(chest, saved, "reloaded");

        CompoundTag old = saved.copy();
        old.put(CellaBlockEntity.CONTENTS, chest.contents().serializeNBT(registries));
        old.put(CellaBlockEntity.PLAN, chest.plan().save());
        whose(chest, old, "reloaded from the old way of saving");

        restored(chest, Filing.write(chest.contents(), chest.plan(), registries),
                "put back down after breaking");
        CompoundTag filedOld = chest.contents().serializeNBT(registries);
        filedOld.put(CellaBlockEntity.PLAN, chest.plan().save());
        restored(chest, filedOld, "put back down from the old way of filing");
        helper.succeed();
    }

    @GameTest(template = TestStructures.FLOOR)
    public static void loadingWithoutContentsKeepsTheSize(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        var registries = helper.getLevel().registryAccess();
        CellaBlockEntity loaded = new CellaBlockEntity(chest.getBlockPos(), chest.getBlockState());
        loaded.loadWithComponents(new CompoundTag(), registries);
        check(loaded.contents().getSlots() == KIND.slots(),
                "nothing said about contents leaves it its size: " + loaded.contents().getSlots());
        check(loaded.plan().untouched(loaded.contents().getSlots()),
                "and one partition over all of it: "
                        + loaded.plan().over(loaded.contents().getSlots()));
        helper.succeed();
    }

    @GameTest(template = TestStructures.FLOOR)
    public static void everythingSaysWhoseEachSquareIs(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Sorted contents = chest.contents();
        int lc = Plan.LC;

        carve(chest, new Plan.Partition("front", DyeColor.RED, 1),
                new Plan.Partition("none", DyeColor.GRAY, 0),
                new Plan.Partition("back", DyeColor.BLUE, 1));
        contents.insertItem(0, new ItemStack(Items.STONE, 10), false);
        contents.insertItem(lc, new ItemStack(Items.APPLE, 7), false);
        contents.insertItem(lc, new ItemStack(Items.DIAMOND, 3), false);

        CellaMenu menu = CellaMenu.at(1, player.getInventory(), helper.absolutePos(WHERE),
                contents.getSlots(), CellaConfig.rows(KIND), CellaConfig.columns(KIND));
        menu.view(2);
        check(menu.owners(chest).isEmpty(), "one partition open needs nothing sent");
        check(menu.owner(0) == 2, "since every square is the open one's: " + menu.owner(0));

        menu.view(Peek.WHOLE);
        java.util.List<Integer> owners = menu.owners(chest);
        check(owners.get(0) == 2 && owners.get(1) == 2,
                "the apples and diamonds are the third's, past the empty one: " + owners);
        check(owners.get(2) == 0, "the stone is the first one's: " + owners);
        check(owners.get(3) == Plan.NONE, "and past them, no one's: " + owners);

        menu.look("diamond");
        owners = menu.owners(chest);
        check(owners.get(0) == 2, "a search keeps saying whose: " + owners);
        check(owners.get(1) == Plan.NONE, "and nothing after the one it found: " + owners);
        helper.succeed();
    }

    @GameTest(template = TestStructures.FLOOR)
    public static void storingGoesWhereItIsPointed(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Sorted contents = chest.contents();

        carve(chest, new Plan.Partition("front", DyeColor.RED, 1),
                new Plan.Partition("back", DyeColor.BLUE, 1));

        CellaMenu menu = CellaMenu.at(1, player.getInventory(), helper.absolutePos(WHERE),
                contents.getSlots(), CellaConfig.rows(KIND), CellaConfig.columns(KIND));
        menu.view(Peek.WHOLE);

        int hand = menu.slots.size() - CellaConfig.PLAYER_COLUMNS;
        menu.slots.get(hand).set(new ItemStack(Items.COAL, 32));
        menu.quickMoveStack(player, hand);
        check(menu.slots.get(hand).getItem().getCount() == 32,
                "nothing moves while nowhere is picked: " + menu.slots.get(hand).getItem());

        menu.into(1);
        menu.quickMoveStack(player, hand);
        check(menu.slots.get(hand).getItem().isEmpty(),
                "with the second picked it goes: " + menu.slots.get(hand).getItem());
        check(contents.used(0) == 0,
                "not into the first, which had room: " + contents.used(0));
        check(contents.used(1) == 1, "but into the one pointed at: " + contents.used(1));
        check(contents.getStackInSlot(Plan.LC).is(Items.COAL),
                "which is where it landed: " + contents.getStackInSlot(Plan.LC));

        menu.into(Into.NONE);
        menu.slots.get(hand).set(new ItemStack(Items.APPLE, 4));
        menu.quickMoveStack(player, hand);
        check(menu.slots.get(hand).getItem().getCount() == 4,
                "and letting it go stops storing again: " + menu.slots.get(hand).getItem());
        helper.succeed();
    }

    @GameTest(template = TestStructures.FLOOR)
    public static void anOpenPartitionIsWhereThingsGo(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Sorted contents = chest.contents();
        int lc = Plan.LC;

        carve(chest, new Plan.Partition("front", DyeColor.RED, 1),
                new Plan.Partition("back", DyeColor.BLUE, 1));
        contents.insertItem(0, new ItemStack(Items.STONE, 10), false);
        contents.insertItem(lc, new ItemStack(Items.DIAMOND, 3), false);

        CellaMenu menu = CellaMenu.at(1, player.getInventory(), helper.absolutePos(WHERE),
                contents.getSlots(), CellaConfig.rows(KIND), CellaConfig.columns(KIND));
        menu.view(1);

        int hand = menu.slots.size() - CellaConfig.PLAYER_COLUMNS;
        menu.slots.get(hand).set(new ItemStack(Items.COAL, 32));
        menu.quickMoveStack(player, hand);
        check(contents.used(0) == 1, "shift-clicking passes the first by: " + contents.used(0));
        check(contents.used(1) == 2, "and lands in the one that is open: " + contents.used(1));

        player.getInventory().setItem(1, new ItemStack(Items.APPLE, 5));
        menu.clickMenuButton(player, CellaMenu.STOW);
        check(contents.used(0) == 1, "storing passes the first by too: " + contents.used(0));
        check(contents.used(1) == 3, "and fills the open one: " + contents.used(1));

        menu.clickMenuButton(player, CellaMenu.TAKE);
        check(contents.used(1) == 0, "taking empties the open one: " + contents.used(1));
        check(contents.getStackInSlot(0).is(Items.STONE) && contents.used(0) == 1,
                "and leaves the other alone: " + contents.getStackInSlot(0));
        check(player.getInventory().countItem(Items.STONE) == 0,
                "so no stone reached the player");

        player.getInventory().setItem(1, new ItemStack(Items.STONE, 4));
        menu.clickMenuButton(player, CellaMenu.MATCHING);
        check(player.getInventory().countItem(Items.STONE) == 4,
                "stone is not a kind the open one holds, so it stays out");
        helper.succeed();
    }

    @GameTest(template = TestStructures.FLOOR)
    public static void theLastPartitionStays(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        check(!chest.undivide(0), "the only partition there is cannot be taken away");
        check(chest.plan().over(chest.contents().getSlots()).size() == 1, "so it stays");
        helper.succeed();
    }

    @GameTest(template = TestStructures.FLOOR)
    public static void givingBackTheRoomItIsNotUsing(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Sorted contents = chest.contents();

        contents.insertItem(0, new ItemStack(Items.STONE, 64 * Plan.LC + 1), false);
        check(contents.used(0) == Plan.LC + 1, "it spans into a second LC: " + contents.used(0));

        int least = (contents.used(0) + Plan.LC - 1) / Plan.LC;
        check(least == 2, "which rounds up to two LC: " + least);
        check(chest.resize(0, new Plan.Partition("", Plan.FIRST, least)),
                "shrinking to what it uses is allowed");
        check(chest.plan().over(contents.getSlots()).get(0).length() == least,
                "so it claims two LC now");
        check(chest.firstGap() == least, "and the rest is free from there: " + chest.firstGap());
        check(items(contents) == 64 * Plan.LC + 1, "with nothing lost: " + items(contents));
        helper.succeed();
    }

    private static int items(Sorted contents) {
        int all = 0;
        for (int slot = 0; slot < contents.getSlots(); slot++) {
            all += contents.getStackInSlot(slot).getCount();
        }
        return all;
    }

    @GameTest(template = TestStructures.FLOOR)
    public static void aPartitionWillNotShrinkOntoItsContents(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Sorted contents = chest.contents();

        carve(chest, new Plan.Partition("one", DyeColor.RED, 2));
        contents.insertItem(0, new ItemStack(Items.STONE, 64 * Plan.LC + 1), false);
        check(contents.used(0) == Plan.LC + 1, "it spans into the second: " + contents.used(0));

        check(!chest.resize(0, new Plan.Partition("one", DyeColor.RED, 1)),
                "shrinking below the contents is refused");
        check(contents.used(0) == Plan.LC + 1, "and nothing moved: " + contents.used(0));

        ItemStack out = contents.extractItem(0, 64, false);
        check(out.getCount() == 1, "the remainder is what comes out: " + out.getCount());
        check(contents.used(0) == Plan.LC, "leaving it exactly full: " + contents.used(0));

        check(chest.resize(0, new Plan.Partition("one", DyeColor.RED, 1)),
                "once it fits, it shrinks");
        int items = 0;
        for (int slot = 0; slot < contents.getSlots(); slot++) {
            items += contents.getStackInSlot(slot).getCount();
        }
        check(items == 64 * Plan.LC, "with every item still there: " + items);
        check(contents.used(0) == Plan.LC, "packed into the slots it has: " + contents.used(0));
        helper.succeed();
    }

    @GameTest(template = TestStructures.FLOOR)
    public static void theChestKeepsItselfInOrder(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Sorted contents = chest.contents();

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

    @GameTest(template = TestStructures.FLOOR)
    public static void theChestIsKeptInWhicheverOrderItWasTold(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Sorted contents = chest.contents();

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

        Order at = contents.order();
        for (int step = 0; step < Order.values().length; step++) {
            at = at.next();
        }
        check(at == contents.order(), "the cycle closes");
        helper.succeed();
    }

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

        contents.extractItem(0, 7, false);
        check(contents.used() == 2, "the empty remainder goes: " + contents.used());
        check(contents.getStackInSlot(2).isEmpty(), "with nothing left behind it");
        helper.succeed();
    }

    @GameTest(template = TestStructures.FLOOR)
    public static void afullChestRefusesWhatWillNotFit(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper, Kind.LARVAL);
        Sorted contents = chest.contents();
        int slots = Kind.LARVAL.slots();

        contents.insertItem(0, new ItemStack(Items.STONE, 64 * slots), false);
        check(contents.used() == slots, "every slot of a Larval is spoken for: " + contents.used());

        ItemStack asked = new ItemStack(Items.DIAMOND, 4);
        check(contents.insertItem(0, asked, true).getCount() == 4,
                "a simulated offer of a new kind comes back whole");
        check(contents.insertItem(0, asked, false).getCount() == 4,
                "and so does the real one");
        check(contents.used() == slots, "with nothing having moved: " + contents.used());

        contents.extractItem(0, 10, false);
        check(contents.insertItem(0, new ItemStack(Items.STONE, 10), false).isEmpty(),
                "the room that is left is room for more of what is in it");
        helper.succeed();
    }

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

        check(chest.contents().getStackInSlot(0).is(Items.APPLE)
                        && chest.contents().getStackInSlot(1).is(Items.BONE),
                "and went in, ahead of the stone that filled the page on screen");
        helper.succeed();
    }

    @GameTest(template = TestStructures.FLOOR)
    public static void takingReachesPastThePage(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        int page = KIND.pageSize();

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

    @GameTest(template = TestStructures.FLOOR)
    public static void oneFireTakesEveryChestAFusionAte(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        Kept kept = Kept.of(helper.getLevel()).orElseThrow(
                () -> new GameTestAssertException("a game test has a server, so it has a store"));

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

    @GameTest(template = TestStructures.FLOOR)
    public static void anOrphanWithNoFormGetsOneThatWouldHoldIt(GameTestHelper helper) {
        check(Kind.fitting(1) == Kind.LARVAL, "one slot fits in the smallest there is");
        check(Kind.fitting(Kind.LARVAL.slots()) == Kind.LARVAL, "and so does exactly a Larval");
        check(Kind.fitting(Kind.LARVAL.slots() + 1) == Kind.IMPERFECT, "one more does not");
        check(Kind.fitting(Kind.SEMI_PERFECT.slots() + 1) == Kind.JUNIOR,
                "and above Semi-Perfect it is Junior, which is not the next one written down");
        check(Kind.biggest() == Kind.MAX, "the biggest form is Max");
        check(Kind.fitting(Kind.MAX.slots() + 1) == Kind.MAX,
                "and something too big for any of them still gets the biggest there is");
        helper.succeed();
    }

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

        int before = chest.experience();
        check(chest.absorb(player) == 0, "an empty player should give nothing");
        check(chest.experience() == before, "and the chest should not have grown");
        helper.succeed();
    }

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

        CellaBlockEntity flat = place(helper, Kind.MAX);
        flat.restore(registries, new Kept.Chest(
                new ItemStackHandler(16).serializeNBT(registries), 9999));
        check(flat.experience() == 0, "a form that does not grow keeps none of it");
        helper.succeed();
    }

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

        for (Kind kind : Kind.values()) {
            check(kind.becomes().isEmpty() || kind.grows(),
                    kind.id() + " can end itself but can never be ready to");
        }
        helper.succeed();
    }

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

        CellaBlockEntity larva = place(helper, Kind.LARVAL);
        check(!larva.light(), "a form with nowhere to go should refuse");
        helper.succeed();
    }

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

    @GameTest(template = TestStructures.FLOOR)
    public static void aFedLarvaGrowsUp(GameTestHelper helper) {
        check(Kind.LARVAL.ripens(), "a larva grows up on its own");
        check(Kind.LARVAL.becomes().orElse(null) == Kind.IMPERFECT, "into an Imperfect");
        check(!Kind.PERFECT.ripens(), "and a Perfect does not - it has to be ended");

        CellaBlockEntity larva = place(helper, Kind.LARVAL);
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
        check(grown.plan().untouched(grown.contents().getSlots()),
                "and one partition across the whole of it: "
                        + grown.plan().over(grown.contents().getSlots()));
        helper.succeed();
    }

    @GameTest(template = TestStructures.FLOOR)
    public static void growingKeepsWhatWasCarved(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper, Kind.PERFECT);
        carve(chest, new Plan.Partition("gold", DyeColor.YELLOW, 2),
                new Plan.Partition("gems", DyeColor.CYAN, 3));
        chest.contents().insertItem(0, new ItemStack(Items.GOLD_INGOT, 5), false);
        chest.contents().insertItem(2 * Plan.LC, new ItemStack(Items.DIAMOND, 7), false);

        check(chest.become(helper.getLevel()), "it grows");
        CellaBlockEntity grown = (CellaBlockEntity) helper.getBlockEntity(WHERE);
        int slots = grown.contents().getSlots();
        check(slots == Kind.SUPER_PERFECT.slots(), "into a Super Perfect: " + slots);

        java.util.List<Plan.Partition> carved = grown.plan().over(slots);
        check(carved.size() == 2 && carved.get(0).name().equals("gold")
                        && carved.get(0).length() == 2 && carved.get(1).name().equals("gems")
                        && carved.get(1).length() == 3,
                "with the same partitions at the same sizes: " + carved);
        check(grown.spare() == Plan.capacity(slots) - 5,
                "and what it grew by left free: " + grown.spare());
        check(grown.contents().getStackInSlot(0).is(Items.GOLD_INGOT)
                        && grown.contents().getStackInSlot(2 * Plan.LC).is(Items.DIAMOND),
                "each thing still in its own");

        CellaBlockEntity plain = place(helper, Kind.PERFECT);
        plain.contents().insertItem(0, new ItemStack(Items.STONE, 9), false);
        check(plain.become(helper.getLevel()), "an undivided one grows too");
        CellaBlockEntity whole = (CellaBlockEntity) helper.getBlockEntity(WHERE);
        check(whole.plan().untouched(whole.contents().getSlots()),
                "and stays one partition across all of it: "
                        + whole.plan().over(whole.contents().getSlots()));
        check(whole.contents().getStackInSlot(0).is(Items.STONE), "with the stone in it");
        helper.succeed();
    }

    @GameTest(template = TestStructures.FLOOR)
    public static void aBrokenLarvaGivesBackWhatItAte(GameTestHelper helper) {
        check(!Kind.LARVAL.keeps(), "the larva is the one that spills");
        CellaBlockEntity larva = place(helper, Kind.LARVAL);
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

        pig.setNoAi(true);
        level.addFreshEntity(pig);
        return pig;
    }

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

        window.setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 1));
        check(chest.getStackInSlot(60).is(Items.GOLD_INGOT),
                "writing through a result reaches the slot it came from");
        check(chest.getStackInSlot(1).isEmpty(), "and not the one it sits at on screen");

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

    @GameTest(template = TestStructures.FLOOR)
    public static void searchingDoesNotNeedALanguage(GameTestHelper helper) {
        int size = 4;
        ItemStackHandler chest = new ItemStackHandler(200);
        chest.setStackInSlot(0, new ItemStack(Items.DIAMOND_SWORD, 1));
        chest.setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 1));
        chest.setStackInSlot(2, new ItemStack(Items.STONE, 1));

        Window window = Window.onto(chest, size);

        window.search("gold_ingot");
        check(window.onThisPage() == 1, "the registry path finds it: " + window.onThisPage());
        check(window.getStackInSlot(0).is(Items.GOLD_INGOT), "and it is the gold");

        window.search("diamond sword");
        check(window.onThisPage() == 1, "an underscore reads as a space: " + window.onThisPage());
        check(window.getStackInSlot(0).is(Items.DIAMOND_SWORD), "and it is the sword");

        window.search("netherite");
        check(window.onThisPage() == 0, "and a word for nothing finds nothing");
        helper.succeed();
    }

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

        chest.setStackInSlot(60, ItemStack.EMPTY);
        check(window.changed(60), "emptying a result moves the results");
        check(window.onThisPage() == 2, "the square goes rather than going blank: "
                + window.onThisPage());
        check(window.getStackInSlot(1).is(Items.DIAMOND_BLOCK), "and the rest close up");

        chest.setStackInSlot(20, new ItemStack(Items.DIAMOND_AXE, 1));
        check(window.changed(20), "a new match moves them too");
        check(window.getStackInSlot(0).is(Items.DIAMOND), "the chest's order is kept");
        check(window.getStackInSlot(1).is(Items.DIAMOND_AXE), "the new one at its own place");
        check(window.getStackInSlot(2).is(Items.DIAMOND_BLOCK), "and not at the end");

        chest.setStackInSlot(70, new ItemStack(Items.EMERALD, 1));
        check(!window.changed(70), "what does not match does not move the results");
        check(window.onThisPage() == 3, "still three: " + window.onThisPage());

        Tidy.everything(chest);
        check(window.again(), "a sort is answered by asking the whole chest again");
        check(window.onThisPage() == 3, "the same three survive it: " + window.onThisPage());
        for (int square = 0; square < 3; square++) {
            check(!window.getStackInSlot(square).isEmpty(),
                    "and no square of a result page is empty: " + square);
        }
        helper.succeed();
    }

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

            if (above.reach() < below.reach()) {
                check(above == Trait.SUPER_PERFECT && above.reach() == 0,
                        step + " lost reach, and Super Perfect is the only rung allowed to");
            }
        }

        check(!Kind.IMPERFECT.trait().finds(), "four pages is not a reason to search");
        check(Kind.SEMI_PERFECT.trait().finds(), "eighteen is");
        check(Kind.IMPERFECT.pages() < Kind.SEMI_PERFECT.pages(),
                "and the second is the bigger of the two, which is why");

        check(Kind.LARVAL.trait() == Trait.LARVA, "the larva is the larva");
        check(Kind.JUNIOR.trait() == Kind.PERFECT.trait(), "Junior is a Perfect");

        check(Kind.MAX.trait() == Kind.SUPER_PERFECT.trait(),
                "Max must not out-do Super Perfect, so it shares its trait");
        for (Kind kind : Kind.values()) {
            check(kind.trait().ordinal() <= Trait.SUPER_PERFECT.ordinal(),
                    kind.id() + " is above the ceiling, and there is not supposed to be one");
        }
        helper.succeed();
    }

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

        int was = lasting.getAge();
        lasting.tick();
        check(lasting.getAge() != was, "and still moving: the clock has to keep running");
        helper.succeed();
    }

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

    private static ItemStack grown(Kind kind, int used) {
        ItemStack stack = new ItemStack(CellaRegistry.item(kind).get());
        stack.set(CellaRegistry.KEPT.get(),
                new Held(java.util.List.of(java.util.UUID.randomUUID()), used, kind.slots(),
                        kind.growth(), kind.growth()));
        return stack;
    }

    private static CompoundTag store(CompoundTag... entries) {
        ListTag chests = new ListTag();
        for (CompoundTag entry : entries) {
            chests.add(entry);
        }
        CompoundTag tag = new CompoundTag();
        tag.put("Chests", chests);
        return tag;
    }

    private static net.minecraft.world.entity.item.ItemEntity dropping(GameTestHelper helper,
            ItemStack stack) {
        return new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(), 0, 0, 0, stack);
    }

    private static java.util.UUID file(Kept kept, HolderLookup.Provider registries) {
        ItemStackHandler was = new ItemStackHandler(Kind.IMPERFECT.slots());
        was.setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 1));
        return kept.put(was.serializeNBT(registries), Kind.IMPERFECT, 1L, 0);
    }

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

    private static CompoundTag named(java.util.UUID id, CompoundTag contents, String kind) {
        CompoundTag entry = new CompoundTag();
        entry.put("Id", UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE, id).result().orElseThrow());
        entry.put("Contents", contents);
        if (kind != null) {
            entry.putString("Kind", kind);
        }
        return entry;
    }

    private static void whose(CellaBlockEntity chest, CompoundTag saved, String how) {
        var registries = chest.getLevel().registryAccess();
        CellaBlockEntity loaded = new CellaBlockEntity(chest.getBlockPos(), chest.getBlockState());
        loaded.loadWithComponents(saved, registries);
        stillWhose(loaded, how);
    }

    private static void restored(CellaBlockEntity chest, CompoundTag filed, String how) {
        var registries = chest.getLevel().registryAccess();
        CellaBlockEntity placed = new CellaBlockEntity(chest.getBlockPos(), chest.getBlockState());
        placed.restore(registries, new Kept.Chest(filed, 0));
        stillWhose(placed, how);
    }

    private static void stillWhose(CellaBlockEntity chest, String how) {
        int lc = Plan.LC;
        check(chest.plan().over(chest.contents().getSlots()).size() == 2,
                how + ", it keeps both partitions: " + chest.plan().over(chest.contents().getSlots()));
        check(chest.contents().getStackInSlot(0).is(Items.STONE),
                how + ", the stone is still the front one's: " + chest.contents().getStackInSlot(0));
        check(chest.contents().getStackInSlot(lc).is(Items.APPLE)
                        && chest.contents().getStackInSlot(lc).getCount() == 7,
                how + ", the apples still the back one's: " + chest.contents().getStackInSlot(lc));
        check(chest.contents().used(0) == 1 && chest.contents().used(1) == 1,
                how + ", with nothing else anywhere");
    }

    private static void freed(CellaBlockEntity chest) {
        check(chest.resize(0, new Plan.Partition("", Plan.FIRST, 0)),
                "the chest-wide partition gives its room back");
    }

    private static void carve(CellaBlockEntity chest, Plan.Partition... wanted) {
        freed(chest);
        for (Plan.Partition one : wanted) {
            check(chest.divide(one), "carve " + one.name());
        }
        check(chest.undivide(0), "and the chest-wide one steps aside");
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
