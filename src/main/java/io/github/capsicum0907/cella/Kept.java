package io.github.capsicum0907.cella;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public class Kept extends SavedData {
    private static final String NAME = Cella.MODID + "_kept";

    private static final String CHESTS = "Chests";
    private static final String ID = "Id";
    private static final String CONTENTS = "Contents";
    private static final String KIND = "Kind";
    private static final String WHEN = "When";
    private static final String EXPERIENCE = "Experience";
    private static final String NAMES = "Names";

    private static final String HANDED = "Handed";

    private static final String SIZE = "Size";
    private static final String ITEMS = "Items";

    private static final long UNDATED = -1L;

    private record Filed(CompoundTag contents, String kind, long when, int experience,
            int names) {
    }

    public record Chest(CompoundTag contents, int experience) {
    }

    public record Trace(UUID id, String named, Optional<Kind> kind, long when,
            int used, int slots, int experience, int names) {
        public boolean dated() {
            return when >= 0;
        }

        public boolean formed() {
            return !named.isEmpty();
        }

        public boolean grown() {
            return experience > 0;
        }

        public boolean claimed() {
            return names > 1;
        }
    }

    private final Map<UUID, Filed> chests;

    private Kept() {
        this.chests = new HashMap<>();
    }

    private Kept(Map<UUID, Filed> chests) {
        this.chests = chests;
    }

    public static Optional<Kept> of(Level level) {
        MinecraftServer server = level.getServer();
        return server == null
                ? Optional.empty()
                : Optional.of(server.overworld().getDataStorage().computeIfAbsent(
                        new SavedData.Factory<>(Kept::new, Kept::load), NAME));
    }

    public UUID put(CompoundTag contents, Kind kind, long when, int experience) {
        UUID id = UUID.randomUUID();

        chests.put(id, new Filed(contents, kind.id(), when, experience, 1));
        setDirty();
        return id;
    }

    public Optional<Chest> take(UUID id) {
        Filed filed = chests.remove(id);
        if (filed != null) {
            setDirty();
        }
        return Optional.ofNullable(filed)
                .map(one -> new Chest(one.contents(), one.experience()));
    }

    public int hand(UUID id) {
        Filed filed = chests.get(id);
        if (filed == null) {
            return 0;
        }
        chests.put(id, new Filed(filed.contents(), filed.kind(), filed.when(),
                filed.experience(), filed.names() + 1));
        setDirty();
        return filed.names();
    }

    public boolean lost(UUID id) {
        Filed filed = chests.get(id);
        if (filed == null) {
            return false;
        }
        if (filed.names() > 1) {
            chests.put(id, new Filed(filed.contents(), filed.kind(), filed.when(),
                    filed.experience(), filed.names() - 1));
            setDirty();
            return false;
        }
        chests.remove(id);
        setDirty();
        return true;
    }

    public static void destroyed(Level level, ItemStack stack) {
        Held held = stack.get(CellaRegistry.KEPT.get());
        if (held == null) {
            return;
        }
        of(level).ifPresent(kept -> held.chests().forEach(kept::lost));
    }

    public Optional<Trace> trace(UUID id) {
        return Optional.ofNullable(chests.get(id)).map(filed -> trace(id, filed));
    }

    public List<Trace> list() {
        List<Trace> traces = new ArrayList<>(chests.size());
        chests.forEach((id, filed) -> traces.add(trace(id, filed)));
        traces.sort(Comparator.comparingLong(Trace::when));
        return traces;
    }

    public int size() {
        return chests.size();
    }

    public boolean forget(UUID id) {
        if (chests.remove(id) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    private static Trace trace(UUID id, Filed filed) {
        return new Trace(id, filed.kind(), Kind.named(filed.kind()), filed.when(),
                usedIn(filed.contents()), slotsIn(filed.contents()), filed.experience(),
                filed.names());
    }

    private static int usedIn(CompoundTag contents) {
        return contents.getList(ITEMS, Tag.TAG_COMPOUND).size();
    }

    private static int slotsIn(CompoundTag contents) {
        return contents.getInt(SIZE);
    }

    static Kept load(CompoundTag tag, HolderLookup.Provider registries) {
        Map<UUID, Filed> chests = new HashMap<>();
        ListTag list = tag.getList(CHESTS, Tag.TAG_COMPOUND);
        for (int at = 0; at < list.size(); at++) {
            CompoundTag entry = list.getCompound(at);
            UUIDUtil.CODEC.parse(NbtOps.INSTANCE, entry.get(ID)).result().ifPresent(id ->
                    chests.put(id, new Filed(entry.getCompound(CONTENTS),
                            entry.getString(KIND),
                            entry.contains(WHEN, Tag.TAG_LONG)
                                    ? entry.getLong(WHEN)
                                    : UNDATED,
                            entry.getInt(EXPERIENCE),
                            entry.contains(NAMES, Tag.TAG_INT)
                                    ? entry.getInt(NAMES)
                                    : 1 + entry.getInt(HANDED))));
        }
        return new Kept(chests);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        chests.forEach((id, filed) -> {
            CompoundTag entry = new CompoundTag();
            UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE, id).result()
                    .ifPresent(written -> entry.put(ID, written));
            entry.put(CONTENTS, filed.contents());
            if (!filed.kind().isEmpty()) {
                entry.putString(KIND, filed.kind());
            }
            if (filed.when() >= 0) {
                entry.putLong(WHEN, filed.when());
            }
            if (filed.experience() > 0) {
                entry.putInt(EXPERIENCE, filed.experience());
            }

            if (filed.names() != 1) {
                entry.putInt(NAMES, filed.names());
            }
            list.add(entry);
        });
        tag.put(CHESTS, list);
        return tag;
    }
}
