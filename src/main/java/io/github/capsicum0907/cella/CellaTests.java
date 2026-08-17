package io.github.capsicum0907.cella;

import io.github.capsicum0907.cella.data.TestStructures;

import net.minecraft.core.BlockPos;
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
     * What is on a later page survives being broken and put back down.
     *
     * <p>Contents drop rather than riding on the item, so "survives" means they are on
     * the floor — all of them, including the pages nobody looked at.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void breakingItSpillsEveryPage(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        chest.contents().setStackInSlot(LATER * KIND.pageSize(),
                new ItemStack(Items.GOLD_INGOT, 11));

        helper.destroyBlock(WHERE);

        helper.succeedWhen(() -> {
            long gold = helper.getEntities(net.minecraft.world.entity.EntityType.ITEM).stream()
                    .map(entity -> ((net.minecraft.world.entity.item.ItemEntity) entity).getItem())
                    .filter(stack -> stack.is(Items.GOLD_INGOT))
                    .mapToLong(ItemStack::getCount)
                    .sum();
            check(gold == 11, "the eleven gold on page three should be on the floor, not " + gold);
        });
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

    private static CellaBlockEntity place(GameTestHelper helper) {
        helper.setBlock(WHERE, CellaRegistry.block(KIND).get());
        return (CellaBlockEntity) helper.getBlockEntity(WHERE);
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new GameTestAssertException(what);
        }
    }
}
