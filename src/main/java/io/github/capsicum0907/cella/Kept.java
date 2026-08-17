package io.github.capsicum0907.cella;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
 * <p>⚠ <b>Nothing collects orphans yet.</b> An item that goes into lava leaves its
 * contents here for good. It is a few kilobytes and it is not wrong, only untidy, and the
 * alternative — deleting on the item's death — is a way to throw away the wrong chest.
 */
public class Kept extends SavedData {
    /** The file this ends up in, under the overworld's data folder. */
    private static final String NAME = Cella.MODID + "_kept";

    private static final String CHESTS = "Chests";
    private static final String ID = "Id";
    private static final String CONTENTS = "Contents";

    /**
     * Serialised rather than live handlers.
     *
     * <p>Saved data is all loaded at once and stays loaded. A Cella Max is 221,184 slots,
     * and holding that as {@code ItemStack}s for every chest anybody has ever picked up
     * would be a lot of nothing in memory. A tag is what came off the disk and what goes
     * back to it; it is only turned into a chest when one is put down.
     */
    private final Map<UUID, CompoundTag> chests;

    private Kept() {
        this.chests = new HashMap<>();
    }

    private Kept(Map<UUID, CompoundTag> chests) {
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

    /** Files a chest's contents away and answers with the name to put on the item. */
    public UUID put(CompoundTag contents) {
        UUID id = UUID.randomUUID();
        chests.put(id, contents);
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
    public Optional<CompoundTag> take(UUID id) {
        CompoundTag contents = chests.remove(id);
        if (contents != null) {
            setDirty();
        }
        return Optional.ofNullable(contents);
    }

    private static Kept load(CompoundTag tag, HolderLookup.Provider registries) {
        Map<UUID, CompoundTag> chests = new HashMap<>();
        ListTag list = tag.getList(CHESTS, Tag.TAG_COMPOUND);
        for (int at = 0; at < list.size(); at++) {
            CompoundTag entry = list.getCompound(at);
            UUIDUtil.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, entry.get(ID)).result()
                    .ifPresent(id -> chests.put(id, entry.getCompound(CONTENTS)));
        }
        return new Kept(chests);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        chests.forEach((id, contents) -> {
            CompoundTag entry = new CompoundTag();
            UUIDUtil.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, id).result()
                    .ifPresent(written -> entry.put(ID, written));
            entry.put(CONTENTS, contents);
            list.add(entry);
        });
        tag.put(CHESTS, list);
        return tag;
    }
}
