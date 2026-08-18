package io.github.capsicum0907.cella;

import java.util.Collection;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * The top of the ladder gets up again with whoever was carrying it.
 *
 * <h2>Why there is a rule for this at all</h2>
 *
 * <p>⚠ <b>This mod kills the player on purpose, in the one dimension where what they
 * drop cannot be gone back for.</b> The fuse is five seconds because that is enough to
 * leave the middle and nowhere near enough to leave the reach — dying is the bargain, not
 * an accident — and what the wave leaves behind is a hundred blocks of nothing with the
 * player's belongings falling through it.
 *
 * <p>{@code CellaBlockEntity#end} already reasoned this out once, for the contents: an item
 * at the centre of a blast, <em>in a dimension made largely of somewhere to fall</em>, is
 * the one thing that must survive put where it cannot. That is why the contents move from
 * block to block and never become an item. The same sentence is true of a Super Perfect
 * being carried, and nothing was doing anything about it.
 *
 * <p><b>A new column rather than a reading of an old one.</b> Neither
 * {@link Trait#unbreakableAsAnItem} nor {@link Trait#survivesAnnihilation} implies it: this
 * is not about the item being destroyed, it is about the item being put somewhere nobody
 * can reach. See {@link Trait#keptOnDeath}, which by the ladder's own rule belongs to Super
 * Perfect or to nobody.
 *
 * <h2>⚠ Where it waits, and why not in memory</h2>
 *
 * <p>Between dying and getting up there is a screen with two buttons on it, and a player
 * can close the game while it is showing. A map keyed on who died would lose everything in
 * it to that, <b>silently and with no way back</b> — so this writes into the one corner of
 * a player's data that both crosses a death ({@code ServerPlayer#restoreFrom} copies it)
 * and goes to disk with them.
 *
 * <h2>What it does not reach</h2>
 *
 * <ul>
 *   <li><b>Slots the inventory itself holds</b>, and not a Cella inside somebody's shulker
 *       box. A line has to be somewhere, and "what the player was carrying" is the one that
 *       can be stated.
 *   <li>Nothing at all when {@code keepInventory} is on — the inventory is never dropped,
 *       so nothing arrives here and the items stay exactly where they already are.
 * </ul>
 */
final class Carried {
    /** Inside the player's persisted data; see the note on {@link #persisted}. */
    private static final String CARRIED = Cella.MODID + "_carried";

    private Carried() {
    }

    /** Server-side only, because the drops are: this is fired out of the death loot. */
    static void died(LivingDropsEvent event) {
        if (event.getEntity() instanceof Player player) {
            keep(player, event.getDrops());
        }
    }

    static void respawned(PlayerEvent.PlayerRespawnEvent event) {
        give(event.getEntity());
    }

    /**
     * Takes the forms that come back out of the drops and writes them down.
     *
     * <p>Out of the collection rather than picked up afterwards: a drop removed here is
     * never added to the world at all, so there is no moment where the item exists in a
     * crater and no race with whatever else is happening there.
     */
    static void keep(Player player, Collection<ItemEntity> drops) {
        HolderLookup.Provider registries = player.registryAccess();
        ListTag keeping = new ListTag();
        drops.removeIf(drop -> {
            ItemStack stack = drop.getItem();
            if (!comesBack(stack)) {
                return false;
            }
            keeping.add(stack.save(registries));
            return true;
        });
        if (keeping.isEmpty()) {
            return;
        }
        CompoundTag persisted = persisted(player);
        // Added to what is there rather than replacing it. Nothing can die twice without
        // getting up in between, but a write that assumes so is a write that would throw
        // the first lot away if anything ever could.
        ListTag waiting = persisted.getList(CARRIED, Tag.TAG_COMPOUND);
        waiting.addAll(keeping);
        persisted.put(CARRIED, waiting);
    }

    /**
     * Hands them back, once.
     *
     * <p>⚠ <b>Cleared before anything is handed over</b>, so that a failure half way
     * through loses the item rather than minting a second one on every respawn. That is the
     * right way round for a Cella in particular: a lost item is a name lost, and the
     * contents are still filed for {@code /cella kept} to hand back, whereas two items
     * naming one chest is a thing this mod goes out of its way never to make.
     */
    static void give(Player player) {
        CompoundTag persisted = persisted(player);
        ListTag waiting = persisted.getList(CARRIED, Tag.TAG_COMPOUND);
        if (waiting.isEmpty()) {
            return;
        }
        persisted.remove(CARRIED);
        HolderLookup.Provider registries = player.registryAccess();
        for (int at = 0; at < waiting.size(); at++) {
            // Whatever will not fit is dropped at their feet, which is where they are
            // standing rather than where they died.
            ItemStack.parse(registries, waiting.get(at))
                    .ifPresent(player.getInventory()::placeItemBackInInventory);
        }
    }

    /** Whether this form gets up again. */
    private static boolean comesBack(ItemStack stack) {
        return stack.getItem() instanceof CellaItem cella && cella.kind().trait().keptOnDeath();
    }

    /**
     * The corner of a player's data that crosses a death and is written to disk.
     *
     * <p>⚠ {@code getCompound} hands back a fresh tag when there is nothing under the key,
     * so it has to be put back or the first write of a life goes nowhere. Putting back one
     * that was already there costs nothing and is the same object.
     */
    private static CompoundTag persisted(Player player) {
        CompoundTag data = player.getPersistentData();
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
        return persisted;
    }
}
