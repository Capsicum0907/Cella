package io.github.capsicum0907.cella;

import io.github.capsicum0907.cella.data.TestStructures;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

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

    private CellaTests() {
    }

    /** A page other than the first, so nothing passes by looking at page nought. */
    private static final int LATER = 2;

    /**
     * The window shows the page it was turned to, and writing through it lands where
     * that page really is.
     *
     * <p>This is the one thing the whole design rests on: slot <em>i</em> of page
     * <em>p</em> is item <em>p × size + i</em>. If that arithmetic is wrong nothing else
     * can be right, and every symptom would look like items moving on their own.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void aPageIsAWindowOntoTheContents(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Window window = new Window(chest.contents(), CellaConfig.pageSize());

        window.turnTo(LATER);
        window.setStackInSlot(0, new ItemStack(Items.DIAMOND, 5));

        int expected = LATER * CellaConfig.pageSize();
        check(chest.contents().getStackInSlot(expected).getCount() == 5,
                "the item should be at slot " + expected + " of the contents");
        check(chest.contents().getStackInSlot(0).isEmpty(),
                "and page one should not have it");

        window.turnTo(0);
        check(window.getStackInSlot(0).isEmpty(), "nor should the window, turned back");
        helper.succeed();
    }

    /**
     * Two windows onto one chest hold their own page.
     *
     * <p>Two players reading the same chest is the case, and nothing arranges it — the
     * window belongs to the screen, so there are simply two of them. The test is here
     * because that would stop being true the moment somebody moved the page onto the
     * block entity to save passing it around.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void twoReadersDoNotShareAPage(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Window mine = new Window(chest.contents(), CellaConfig.pageSize());
        Window yours = new Window(chest.contents(), CellaConfig.pageSize());

        mine.turnTo(LATER);
        mine.setStackInSlot(0, new ItemStack(Items.EMERALD, 3));

        check(yours.page() == 0, "the other reader should still be on the first page");
        check(yours.getStackInSlot(0).isEmpty(), "and should not see what went onto page three");
        yours.turnTo(LATER);
        check(yours.getStackInSlot(0).getCount() == 3, "until it turns there too");
        helper.succeed();
    }

    /**
     * A page that is not there is refused rather than clamped.
     *
     * <p>The number arrives from a packet, so it is whatever the other side said. Being
     * moved somewhere you did not ask for is worse than not moving.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void thereIsNoPageAfterTheLast(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Window window = new Window(chest.contents(), CellaConfig.pageSize());

        check(window.pages() == CellaConfig.PAGES.get(),
                "a new chest should have as many pages as the config says");
        window.turnTo(window.pages());
        check(window.page() == 0, "a page past the end should be ignored");
        window.turnTo(-1);
        check(window.page() == 0, "and so should one before the start");
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
        check(offered.getSlots() == CellaConfig.pageSize() * CellaConfig.PAGES.get(),
                "and it should be every page, not one: " + offered.getSlots());

        // Reaching past the first page has to work, not merely be counted.
        int far = CellaConfig.pageSize() * (CellaConfig.PAGES.get() - 1);
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
        chest.contents().setStackInSlot(LATER * CellaConfig.pageSize(),
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
        int page = CellaConfig.pageSize();
        for (int slot = 0; slot < page; slot++) {
            chest.contents().setStackInSlot(slot, new ItemStack(Items.STONE, 64));
        }

        CellaMenu menu = CellaMenu.at(1, player.getInventory(), helper.absolutePos(WHERE));
        int hand = menu.slots.size() - CellaConfig.COLUMNS;
        menu.slots.get(hand).set(new ItemStack(Items.COAL, 32));
        menu.quickMoveStack(player, hand);

        check(menu.slots.get(hand).getItem().isEmpty(), "the coal should have left the player");
        check(chest.contents().getStackInSlot(page).getCount() == 32,
                "and landed on the first slot with room, which is on page two");
        helper.succeed();
    }

    private static CellaBlockEntity place(GameTestHelper helper) {
        helper.setBlock(WHERE, CellaRegistry.BLOCK.get());
        return (CellaBlockEntity) helper.getBlockEntity(WHERE);
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new GameTestAssertException(what);
        }
    }
}
