package io.github.capsicum0907.cella;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * What has gone in and out, kept but not yet shown.
 *
 * <p>A chest of two hundred thousand slots absorbs a mistake without a ripple: put the
 * wrong stack in, or take the wrong one out, and nothing on the screen is different
 * afterwards. Somewhere that says <i>forty cobblestone left here a minute ago</i> is the
 * only way to find out.
 *
 * <p>⚠ <b>Kinds, not slots.</b> The first sketch of this recorded a slot number, which was
 * written before the chest kept itself in order — see {@link Sorted}. It cannot now: slot
 * five is whatever sorted into fifth place at the time, and a number that means something
 * different at every moment records nothing. What is stable is the kind and how much of it
 * there was, which is also the question being asked.
 *
 * <p>⚠ <b>By hand only.</b> A hopper feeding a chest for an afternoon would fill this with
 * its own footsteps and push out everything a person did. So only what goes through the
 * screen is written down; automation is not a thing anybody goes looking for afterwards.
 *
 * <p><b>Components and all.</b> The kind is the item with its components, the same question
 * {@code Tidy.merge} asks, so an enchanted book is not filed as a book. Recording bare
 * item ids would report the loss of something irreplaceable as the loss of a paper one.
 */
public final class Ledger {
    /** One movement: what it was, how much there was, how much there is. */
    public record Move(ItemStack kind, int before, int after) {
        private static final String KIND = "Kind";
        private static final String BEFORE = "Before";
        private static final String AFTER = "After";

        CompoundTag save(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.put(KIND, kind.save(registries));
            tag.putInt(BEFORE, before);
            tag.putInt(AFTER, after);
            return tag;
        }

        static Move read(HolderLookup.Provider registries, CompoundTag tag) {
            ItemStack kind = ItemStack.parse(registries, tag.getCompound(KIND))
                    .orElse(ItemStack.EMPTY);
            return new Move(kind, tag.getInt(BEFORE), tag.getInt(AFTER));
        }

        /** How much arrived, or left if it is negative. */
        public int moved() {
            return after - before;
        }
    }

    private final Deque<Move> moves = new ArrayDeque<>();

    /**
     * Writes one down, and drops the oldest if there are now too many.
     *
     * <p>⚠ The ceiling is read every time rather than held, so turning it down in the
     * config takes effect on the next thing that happens rather than on the next restart.
     * A ceiling of nought turns the whole thing off and empties what is there.
     */
    public void put(ItemStack kind, int before, int after) {
        if (before == after) {
            return;
        }
        ItemStack one = kind.copy();
        one.setCount(1);
        moves.addLast(new Move(one, before, after));
        trim();
    }

    private void trim() {
        int ceiling = CellaConfig.history();
        while (moves.size() > ceiling) {
            moves.removeFirst();
        }
    }

    /** Oldest first. */
    public List<Move> moves() {
        trim();
        return List.copyOf(moves);
    }

    public int size() {
        return moves.size();
    }

    public ListTag save(HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Move move : moves) {
            list.add(move.save(registries));
        }
        return list;
    }

    public void load(HolderLookup.Provider registries, ListTag list) {
        moves.clear();
        for (int at = 0; at < list.size(); at++) {
            Move move = Move.read(registries, list.getCompound(at));
            if (!move.kind().isEmpty()) {
                moves.addLast(move);
            }
        }
        trim();
    }

    /** Whether there is anything worth saving, so an untouched chest writes no tag. */
    public boolean any() {
        return !moves.isEmpty();
    }

    static final int TAG = Tag.TAG_COMPOUND;
}
