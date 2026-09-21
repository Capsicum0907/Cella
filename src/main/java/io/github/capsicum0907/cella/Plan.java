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

    public static final int LONGEST = 20;

    public static final DyeColor FIRST = DyeColor.LIGHT_BLUE;

    public static String fit(String name) {
        String clean = name == null ? "" : name;
        return clean.length() <= LONGEST ? clean : clean.substring(0, LONGEST);
    }

    public record Partition(String name, DyeColor colour, int length) {
        public Partition {
            name = fit(name);
            length = Math.max(0, length);
        }

        private static final String NAME = "Name";
        private static final String COLOUR = "Colour";
        private static final String LENGTH = "Length";

        public int slots() {
            return length * LC;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString(NAME, name);
            tag.putInt(COLOUR, colour.getId());
            tag.putInt(LENGTH, length);
            return tag;
        }

        static Partition read(CompoundTag tag) {
            return new Partition(tag.getString(NAME), DyeColor.byId(tag.getInt(COLOUR)),
                    tag.getInt(LENGTH));
        }
    }

    private final List<Partition> carved = new ArrayList<>();

    private int assigned = NONE;

    public static int capacity(int slots) {
        return Math.max(1, slots / LC);
    }

    public void ensure(int slots) {
        if (carved.isEmpty()) {
            carved.add(new Partition("", FIRST, capacity(slots)));
            assigned = 0;
        }
    }

    public boolean split() {
        return carved.size() > 1;
    }

    public List<Partition> over(int slots) {
        return List.copyOf(carved);
    }

    public int count(int slots) {
        return carved.size();
    }

    public Partition at(int index, int slots) {
        return carved.get(index);
    }

    public int taken() {
        int all = 0;
        for (Partition one : carved) {
            all += one.length();
        }
        return all;
    }

    public int start(int index) {
        int at = 0;
        for (int before = 0; before < index && before < carved.size(); before++) {
            at += carved.get(before).length();
        }
        return at;
    }

    public int first(int index, int slots) {
        return Math.min(slots, start(index) * LC);
    }

    public int past(int index, int slots) {
        return Math.min(slots, first(index, slots) + carved.get(index).slots());
    }

    public int indexOf(int slot, int slots) {
        for (int at = 0; at < carved.size(); at++) {
            if (slot >= first(at, slots) && slot < past(at, slots)) {
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
        return assigned;
    }

    public void assign(int index) {
        assigned = index >= 0 && index < carved.size() ? index : NONE;
    }

    public boolean room(int length, int slots, int ignoring) {
        int held = ignoring >= 0 && ignoring < carved.size()
                ? carved.get(ignoring).length()
                : 0;
        return length >= 0 && taken() - held + length <= capacity(slots);
    }

    public boolean add(Partition wanted, int slots) {
        if (!room(wanted.length(), slots, NONE)) {
            return false;
        }
        carved.add(wanted);
        return true;
    }

    public boolean replace(int index, Partition wanted, int slots) {
        if (index < 0 || index >= carved.size() || !room(wanted.length(), slots, index)) {
            return false;
        }
        carved.set(index, wanted);
        return true;
    }

    public boolean drop(int index) {
        if (index < 0 || index >= carved.size() || carved.size() <= 1) {
            return false;
        }
        carved.remove(index);
        if (assigned == index) {
            assigned = NONE;
        } else if (assigned > index) {
            assigned--;
        }
        return true;
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
            if (room(one.length(), slots, NONE)) {
                carved.add(one);
            }
        }
        boolean had = !carved.isEmpty();
        ensure(slots);
        assign(had ? tag.getInt(ASSIGNED) : 0);
    }
}
