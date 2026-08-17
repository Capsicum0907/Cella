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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The contents of chests that have been picked up, kept in the world rather than on the
 * item.
 *
 * <p><b>The item holds a name, not a chest.</b> Everything from Imperfect up survives
 * being broken — see {@link Kind#keeps} — and the obvious way to do that is the shulker
 * box's: put the contents in a component on the item. It does not reach. Three separate
 * ceilings say so:
 *
 * <ul>
 *   <li>Vanilla's own {@code ItemContainerContents} stops at <b>256 slots</b>. Perfect is
 *       13,824.
 *   <li>A full Cella Max is somewhere between one and four megabytes of stacks, against a
 *       packet frame limit of <b>2,097,151 bytes</b> — a three-byte length prefix. Over
 *       that is not a slow chest, it is a dropped connection.
 *   <li>Whatever that came to would be re-sent every time the inventory holding it
 *       changed.
 * </ul>
 *
 * <p><b>And it removes the duplication family rather than managing it.</b> A component
 * belongs to the {@code ItemStack}, so two of them in a stack are one set of contents
 * with a count of two — which is the shape of both bugs Acervus had. There is nothing
 * here to copy: copying the stack copies a name, and two items naming one chest are two
 * views of one chest rather than two chests. The item still refuses to stack while it
 * holds a name, because two views of one chest is not something to hand a player by
 * accident; see {@code CellaItem}.
 *
 * <p><b>One store for every dimension</b>, on the overworld. A chest broken in the Nether
 * and put down at home is the same chest, so it cannot be filed under where it was dug up.
 *
 * <h2>Orphans</h2>
 *
 * <p>An item that goes into lava leaves its contents here with nothing left to ask for
 * them. That is the cost of the name, and it is paid in kilobytes rather than in anything
 * a player can see — but it is untidy, and after fusions it happens eight at a time.
 *
 * <p><b>Nothing sweeps them automatically, and that is the decision rather than the
 * omission.</b> Knowing a name has gone would mean counting every item in the world that
 * could be holding one, and a Cella item is anywhere: a hand, a chest, an ender chest, an
 * item on the floor, some other mod's warehouse, <em>another Cella</em>. Any sweep that
 * misses one — an unloaded chunk is enough — deletes contents that were still spoken for.
 * The failure points the wrong way, so it is not done.
 *
 * <p>What is done instead is {@link Trace}: enough about each filed chest that a person
 * can look at the list and decide. See {@code KeptCommand}. And because deciding is the
 * expensive part, the tool's first job is <b>handing contents back</b> rather than
 * deleting them — a name is all it takes to put an orphan on a new item.
 */
public class Kept extends SavedData {
    /** The file this ends up in, under the overworld's data folder. */
    private static final String NAME = Cella.MODID + "_kept";

    private static final String CHESTS = "Chests";
    private static final String ID = "Id";
    private static final String CONTENTS = "Contents";
    private static final String KIND = "Kind";
    private static final String WHEN = "When";
    private static final String EXPERIENCE = "Experience";

    /**
     * {@code ItemStackHandler}'s own two keys, read here and never written.
     *
     * <p>Its {@code Items} list holds the slots that have something in them and no others,
     * and {@code Size} is how many slots there were altogether — so how full a filed chest
     * is can be had from the tag as it lies, without turning a Cella Max back into two
     * hundred thousand {@code ItemStack}s to count them.
     */
    private static final String SIZE = "Size";
    private static final String ITEMS = "Items";

    /** Game time counts up from nought, so nothing that was recorded is negative. */
    private static final long UNDATED = -1L;

    /**
     * What is known about one filed chest, beyond the contents themselves.
     *
     * <p><b>Only what cannot be worked out again.</b> How full it is and how big it is are
     * in the contents tag already and are read from there; which form it was and when it
     * was filed are not written anywhere else, so they are written here. A field that
     * duplicates a fact is a field that can disagree with it.
     *
     * <p><b>The form is kept as the id that was written</b>, not as a resolved
     * {@link Kind}, so that a version of this mod which does not have that form still
     * writes back what it read. Resolving on the way in and saving the resolution would
     * turn "names a form I do not know" into "names nothing" the first time such a world
     * was opened, and that is not recoverable.
     *
     * @param kind the form's id, or empty for an entry filed before this was recorded
     * @param when game time, or {@link #UNDATED}
     * @param experience points it had been fed, which is nought for most entries and for
     *              every entry written before a Cella could be fed at all
     */
    private record Filed(CompoundTag contents, String kind, long when, int experience) {
    }

    /**
     * A chest coming back out: what was in it, and what it had been fed.
     *
     * <p>Two things rather than one because they are stored in two places for a reason -
     * the contents are the handler's own tag and the experience is a number beside it -
     * and putting the number inside that tag would mean writing a foreign key into a
     * format vanilla owns. A pair at the door is cheaper than a lie about the format.
     */
    public record Chest(CompoundTag contents, int experience) {
    }

    /**
     * One filed chest as something a person can judge.
     *
     * <p>A list of UUIDs is not material for a decision. Which form it was, how long ago
     * it was put here and how much is in it are — and with those, an entry that turns out
     * to matter can be handed back rather than thrown away.
     *
     * <p><b>Not knowing is one of the answers.</b> Chests filed before any of this was
     * recorded load with no form and no date, and they say so. Guessing the form from the
     * size would be wrong often enough to matter: a chest built when the ladder had
     * different numbers keeps the size it was built at, which is the whole reason
     * {@link CellaBlockEntity#restore} puts the size back too.
     *
     * <p><b>And there is a third answer, which is not the same as the first.</b> An entry
     * can name a form this version of the mod does not have. That is worth telling apart
     * from an entry that names nothing — one says the world has been opened by a different
     * version and the other says the entry is simply old — so {@link #named} is what was
     * written and {@link #kind} is what that turned out to be, if anything.
     *
     * @param when game time when it was filed, or negative if it was filed before that was
     *             recorded — ask {@link #dated()} rather than comparing
     */
    public record Trace(UUID id, String named, Optional<Kind> kind, long when,
            int used, int slots, int experience) {
        /** Whether it knows when it was filed at all. */
        public boolean dated() {
            return when >= 0;
        }

        /** Whether it names a form at all, whether or not this version has that form. */
        public boolean formed() {
            return !named.isEmpty();
        }

        /** Whether it had been fed anything. One way, so this only ever went up. */
        public boolean grown() {
            return experience > 0;
        }
    }

    /**
     * Serialised rather than live handlers.
     *
     * <p>Saved data is all loaded at once and stays loaded. A Cella Max is 221,184 slots,
     * and holding that as {@code ItemStack}s for every chest anybody has ever picked up
     * would be a lot of nothing in memory. A tag is what came off the disk and what goes
     * back to it; it is only turned into a chest when one is put down.
     */
    private final Map<UUID, Filed> chests;

    private Kept() {
        this.chests = new HashMap<>();
    }

    private Kept(Map<UUID, Filed> chests) {
        this.chests = chests;
    }

    /**
     * The one store, or empty if there is no server — which is every client-side caller.
     */
    public static Optional<Kept> of(Level level) {
        MinecraftServer server = level.getServer();
        return server == null
                ? Optional.empty()
                : Optional.of(server.overworld().getDataStorage().computeIfAbsent(
                        new SavedData.Factory<>(Kept::new, Kept::load), NAME));
    }

    /**
     * Files a chest's contents away and answers with the name to put on the item.
     *
     * @param kind which form it was, which nothing else records once the block is gone
     * @param when game time now; see {@link Trace}
     * @param experience what it had been fed, which is lost with the block if it is not
     *              filed here - and being one way, lost for good
     */
    public UUID put(CompoundTag contents, Kind kind, long when, int experience) {
        UUID id = UUID.randomUUID();
        chests.put(id, new Filed(contents, kind.id(), when, experience));
        setDirty();
        return id;
    }

    /**
     * Hands back what was filed under that name, and forgets it.
     *
     * <p><b>Taking, not reading.</b> The contents exist in one place at a time — in a
     * chest in the world, or here — and a name that has been spent is a name that answers
     * with nothing. An item duplicated by some other mod's doing then puts down one full
     * chest and one empty one, rather than two full ones.
     */
    public Optional<Chest> take(UUID id) {
        Filed filed = chests.remove(id);
        if (filed != null) {
            setDirty();
        }
        return Optional.ofNullable(filed)
                .map(one -> new Chest(one.contents(), one.experience()));
    }

    /**
     * What is known about one filed chest, without spending it.
     *
     * <p><b>Deliberately not {@link #take}.</b> Handing somebody an item that names a
     * chest is not the same as putting the chest back, and the contents stay filed until
     * something is placed. That keeps the one rule that makes all of this safe: contents
     * come out of here exactly once, whoever asks and however often.
     */
    public Optional<Trace> trace(UUID id) {
        return Optional.ofNullable(chests.get(id)).map(filed -> trace(id, filed));
    }

    /**
     * Everything filed, oldest first, with the ones that predate any record of their age
     * ahead of those — which is the right way round, since they are the oldest there are.
     */
    public List<Trace> list() {
        List<Trace> traces = new ArrayList<>(chests.size());
        chests.forEach((id, filed) -> traces.add(trace(id, filed)));
        traces.sort(Comparator.comparingLong(Trace::when));
        return traces;
    }

    /** How many chests are filed. Asked before printing any of them. */
    public int size() {
        return chests.size();
    }

    /**
     * Destroys one, by name.
     *
     * <p>Separate from {@link #take} because it is a different act: taking is a chest
     * being put back into the world, and this is a person having looked at an orphan and
     * decided. There is no undoing it, which is why nothing calls it but a command with a
     * name typed into it.
     *
     * @return whether there was anything under that name
     */
    public boolean forget(UUID id) {
        if (chests.remove(id) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    private static Trace trace(UUID id, Filed filed) {
        return new Trace(id, filed.kind(), Kind.named(filed.kind()), filed.when(),
                usedIn(filed.contents()), slotsIn(filed.contents()), filed.experience());
    }

    /** How many slots have something in them; see the note on {@link #ITEMS}. */
    private static int usedIn(CompoundTag contents) {
        return contents.getList(ITEMS, Tag.TAG_COMPOUND).size();
    }

    /** How big the chest was when it was filed, which is not its kind's size today. */
    private static int slotsIn(CompoundTag contents) {
        return contents.getInt(SIZE);
    }

    /**
     * <b>Entries written before there was anything to say about them still load.</b> The
     * store held nothing but contents at first, and worlds have those. Dropping one would
     * not be a cosmetic loss — it is exactly the orphan this class is meant to hand back,
     * thrown away by the thing that was built to rescue it. It loads with no form and no
     * date, and the listing says so rather than filling either in.
     *
     * <p>An entry with no name, on the other hand, is not an entry: there is no way to ask
     * for it and no way to name it in a command, so it is skipped along with anything else
     * that fails to parse.
     */
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
                            entry.getInt(EXPERIENCE))));
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
            list.add(entry);
        });
        tag.put(CHESTS, list);
        return tag;
    }
}
