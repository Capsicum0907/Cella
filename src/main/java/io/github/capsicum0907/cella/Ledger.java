package io.github.capsicum0907.cella;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

public final class Ledger {
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

        public int moved() {
            return after - before;
        }
    }

    private final Deque<Move> moves = new ArrayDeque<>();

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

    public boolean any() {
        return !moves.isEmpty();
    }

    static final int TAG = Tag.TAG_COMPOUND;
}
