package io.github.capsicum0907.cella;

import io.github.capsicum0907.cella.data.TestStructures;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
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

    /** The player's own three rows and hotbar, which every container menu ends with. */
    private static final int PLAYER_SLOTS = 36;

    private CellaTests() {
    }

    /** A page other than the first, so nothing passes by looking at page nought. */
    private static final int LATER = 2;

    /**
     * The menu holds every slot of the chest, and slot <em>i</em> is contents <em>i</em>.
     *
     * <p>This is the whole design in one assertion, and it used to be false on purpose:
     * the menu had one page and a window moved underneath it. Both things that went wrong
     * with that came of the same place — a sorting mod could only ever see a page, and
     * the game's own "has this slot changed" test was asking about a slot whose meaning
     * had moved out from under it.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void theMenuHoldsEveryPage(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        int all = chest.contents().getSlots();
        int far = LATER * CellaConfig.pageSize() + 4;
        chest.contents().setStackInSlot(far, new ItemStack(Items.DIAMOND, 5));

        CellaMenu menu = CellaMenu.at(1, player.getInventory(), helper.absolutePos(WHERE), all);

        check(menu.slots.size() == all + PLAYER_SLOTS,
                "the menu should hold the whole chest and the player: " + menu.slots.size());
        check(menu.slots.get(far).getItem().getCount() == 5,
                "and slot i should be contents i, on any page");
        helper.succeed();
    }

    /**
     * Only the page on show is active, and active is the only thing hiding the rest.
     *
     * <p>Every page's slots sit at the same coordinates, one page deep, so this is what
     * stops eight of them being drawn on top of one another. The screen asks
     * {@code isActive} before it draws a slot, before it calls one hovered and before it
     * decides which one the mouse is in.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void onlyTheOpenPageIsActive(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        int page = CellaConfig.pageSize();
        CellaMenu menu = CellaMenu.at(1, player.getInventory(), helper.absolutePos(WHERE),
                chest.contents().getSlots());

        menu.turnTo(LATER);
        for (int slot = 0; slot < chest.contents().getSlots(); slot++) {
            boolean shown = slot / page == LATER;
            check(menu.slots.get(slot).isActive() == shown,
                    "slot " + slot + " should " + (shown ? "" : "not ") + "be shown on page " + LATER);
        }
        check(menu.slots.get(menu.slots.size() - 1).isActive(),
                "and the player's own slots are never a page");
        helper.succeed();
    }

    /**
     * A page that is not there is refused rather than clamped.
     *
     * <p>Nothing sends this any more — the page never leaves the client — but being moved
     * somewhere you did not ask for is still worse than not moving.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void thereIsNoPageAfterTheLast(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CellaMenu menu = CellaMenu.at(1, player.getInventory(), helper.absolutePos(WHERE),
                chest.contents().getSlots());

        check(menu.pages() == CellaConfig.PAGES.get(),
                "a new chest should have as many pages as the config says");
        menu.turnTo(menu.pages());
        check(menu.page() == 0, "a page past the end should be ignored");
        menu.turnTo(-1);
        check(menu.page() == 0, "and so should one before the start");
        helper.succeed();
    }

    /**
     * The menu is as big as the chest it was opened on, not as big as the config.
     *
     * <p>A chest keeps the size it was built with, so the two disagree in any world whose
     * config has been turned down since. The client cannot work the real size out — its
     * copy of the block entity was made at the config's size — which is why the number
     * travels in the packet that opens the screen. Getting it wrong is not cosmetic now
     * that every slot is in the menu: filling a shorter list from a longer one walks off
     * the end of it.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void theMenuIsAsBigAsTheChest(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        // A chest from a world whose config said something else.
        int odd = CellaConfig.pageSize() * 3;
        chest.contents().setSize(odd);

        CellaMenu menu = CellaMenu.at(1, player.getInventory(), helper.absolutePos(WHERE), odd);
        check(menu.slots.size() == odd + PLAYER_SLOTS,
                "the menu should have followed the chest: " + menu.slots.size());
        check(menu.pages() == 3, "and counted its pages from it, not from the config");
        helper.succeed();
    }

    /**
     * No two slots share a position, on any page.
     *
     * <p>The reason is a bug that {@code isActive} did not prevent: shift-clicking an
     * empty slot fetched an item from the same square on another page. Vanilla's own
     * "which slot is the mouse over" asks {@code isActive} first and was never wrong;
     * a mod's need not, and with eight slots in one square it can pick any of them.
     *
     * <p>So this asserts the property rather than the fix — anything that puts two slots
     * in one place fails here, however it does it.
     */
    @GameTest(template = TestStructures.FLOOR)
    public static void noTwoSlotsShareAPlace(GameTestHelper helper) {
        CellaBlockEntity chest = place(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CellaMenu menu = CellaMenu.at(1, player.getInventory(), helper.absolutePos(WHERE),
                chest.contents().getSlots());

        for (int on = 0; on < menu.pages(); on++) {
            menu.turnTo(on);
            java.util.Set<Long> taken = new java.util.HashSet<>();
            for (Slot slot : menu.slots) {
                long place = ((long) slot.x << 32) ^ (slot.y & 0xFFFFFFFFL);
                check(taken.add(place),
                        "two slots at " + slot.x + "," + slot.y + " with page " + on + " open");
            }
            // And the page being shown is the one at the coordinates the screen draws.
            check(menu.slots.get(on * CellaConfig.pageSize()).y == 18,
                    "the open page should be where the screen draws it");
        }
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

        CellaMenu menu = CellaMenu.at(1, player.getInventory(), helper.absolutePos(WHERE),
                chest.contents().getSlots());
        int hand = menu.slots.size() - CellaConfig.COLUMNS;
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
        int page = CellaConfig.pageSize();

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
        int page = CellaConfig.pageSize();
        for (int slot = 0; slot < page; slot++) {
            chest.contents().setStackInSlot(slot, new ItemStack(Items.STONE, 64));
        }

        player.getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
        player.getInventory().selected = 0;
        player.getInventory().setItem(1, new ItemStack(Items.APPLE, 12));
        player.getInventory().setItem(20, new ItemStack(Items.BONE, 5));

        CellaMenu menu = CellaMenu.at(1, player.getInventory(), helper.absolutePos(WHERE),
                chest.contents().getSlots());
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
