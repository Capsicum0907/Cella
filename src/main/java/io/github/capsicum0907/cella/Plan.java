package io.github.capsicum0907.cella;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.DyeColor;

public final class Plan {
    public static final int LC = 54;

    public static final int NONE = -1;

    public record Partition(String name, DyeColor colour, int start, int length) {
        private static final String NAME = "Name";
        private static final String COLOUR = "Colour";
        private static final String START = "Start";
        private static final String LENGTH = "Length";

        public int first() {
            return start * LC;
        }

        public int slots() {
            return length * LC;
        }

        public int past() {
            return first() + slots();
        }

        public boolean holds(int slot) {
            return slot >= first() && slot < past();
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString(NAME, name);
            tag.putInt(COLOUR, colour.getId());
            tag.putInt(START, start);
            tag.putInt(LENGTH, length);
            return tag;
        }

        static Partition read(CompoundTag tag) {
            return new Partition(tag.getString(NAME), DyeColor.byId(tag.getInt(COLOUR)),
                    tag.getInt(START), tag.getInt(LENGTH));
        }
    }

    private final List<Partition> carved = new ArrayList<>();

    private int assigned = NONE;

    public boolean divided() {
        return !carved.isEmpty();
    }

    public List<Partition> over(int slots) {
        if (carved.isEmpty()) {
            return List.of(whole(slots));
        }
        return List.copyOf(carved);
    }

    private static Partition whole(int slots) {
        return new Partition("", DyeColor.WHITE, 0, Math.max(1, slots / LC));
    }

    public int count(int slots) {
        return carved.isEmpty() ? 1 : carved.size();
    }

    public Partition at(int index, int slots) {
        List<Partition> all = over(slots);
        return all.get(Math.floorMod(index, all.size()));
    }

    public int indexOf(int slot, int slots) {
        List<Partition> all = over(slots);
        for (int at = 0; at < all.size(); at++) {
            if (all.get(at).holds(slot)) {
                return at;
            }
        }
        return NONE;
    }

    public Optional<Partition> covering(int slot, int slots) {
        int at = indexOf(slot, slots);
        return at == NONE ? Optional.empty() : Optional.of(at(at, slots));
    }

    public int assigned() {
        return carved.isEmpty() ? 0 : assigned;
    }

    public void assign(int index) {
        assigned = index >= 0 && index < carved.size() ? index : NONE;
    }

    public Optional<Partition> outlet(int slots) {
        int at = assigned();
        return at == NONE ? Optional.empty() : Optional.of(at(at, slots));
    }

    public boolean room(Partition wanted, int slots, int ignoring) {
        if (wanted.length() < 1 || wanted.start() < 0
                || wanted.past() > slots) {
            return false;
        }
        for (int at = 0; at < carved.size(); at++) {
            if (at == ignoring) {
                continue;
            }
            Partition other = carved.get(at);
            if (wanted.first() < other.past() && other.first() < wanted.past()) {
                return false;
            }
        }
        return true;
    }

    public boolean add(Partition wanted, int slots) {
        if (!room(wanted, slots, NONE)) {
            return false;
        }
        carved.add(wanted);
        carved.sort(java.util.Comparator.comparingInt(Partition::start));
        return true;
    }

    public boolean replace(int index, Partition wanted, int slots) {
        if (index < 0 || index >= carved.size() || !room(wanted, slots, index)) {
            return false;
        }
        carved.set(index, wanted);
        carved.sort(java.util.Comparator.comparingInt(Partition::start));
        return true;
    }

    public void drop(int index) {
        if (index < 0 || index >= carved.size()) {
            return;
        }
        carved.remove(index);
        if (assigned == index) {
            assigned = NONE;
        } else if (assigned > index) {
            assigned--;
        }
    }

    private static final String CARVED = "Carved";
    private static final String ASSIGNED = "Assigned";

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Partition one : carved) {
            list.add(one.save());
        }
        tag.put(CARVED, list);
        tag.putInt(ASSIGNED, assigned);
        return tag;
    }

    public void load(CompoundTag tag, int slots) {
        carved.clear();
        ListTag list = tag.getList(CARVED, Tag.TAG_COMPOUND);
        for (int at = 0; at < list.size(); at++) {
            Partition one = Partition.read(list.getCompound(at));
            if (room(one, slots, NONE)) {
                carved.add(one);
            }
        }
        carved.sort(java.util.Comparator.comparingInt(Partition::start));
        assign(tag.getInt(ASSIGNED));
    }

    public boolean any() {
        return !carved.isEmpty() || assigned != NONE;
    }
}
