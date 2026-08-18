package io.github.capsicum0907.cella;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.neoforge.event.entity.item.ItemExpireEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Items that have been told their time is up, asked again once the tick is over.
 *
 * <p>⚠ <b>The usual way a Cella is lost is not fire or a creeper, it is the five minutes
 * every dropped item has</b> — see {@code CellaItem#onEntityItemUpdate}, which is why the
 * top of the ladder never arrives here at all. So the names that go this way outnumber the
 * ones anybody ever watches burn, and a cleanup that only knew about fire would be watching
 * the small half.
 *
 * <h2>⚠ Why the event is not the answer, only the question</h2>
 *
 * <p>{@link ItemExpireEvent} is an <b>offer</b> rather than an announcement: any mod may
 * hand the item more life, and the game removes it only if none did. Reading the offer
 * means reading it from somewhere in a queue — even at the lowest priority, a listener
 * registered after this one still runs after it — and forgetting a chest whose item is
 * then granted another five minutes is the one mistake here that cannot be walked back.
 *
 * <p><b>So nothing is decided when the question is asked.</b> The entity is written down,
 * and when the level has finished ticking the <b>outcome</b> is read instead: it is gone,
 * and its age has passed the lifespan it turned out to have. Both halves are needed and
 * neither is the removal reason, which says {@code DISCARDED} for a player picking one up
 * just as it does for one running out.
 *
 * <p>The second half is what makes the first safe. An item that was given more time has a
 * lifespan that moved out from under its age, so it fails that test however it leaves the
 * world afterwards; and an item that was not given more time is removed by the game in the
 * same statement, with nothing able to get at it in between.
 */
final class Expiring {
    /**
     * Nothing here is saved and nothing is held over: everything written down is answered
     * for before the end of the tick it was written down in.
     */
    private static final List<ItemEntity> DUE = new ArrayList<>();

    private Expiring() {
    }

    /** Written down, not decided. */
    static void reached(ItemExpireEvent event) {
        watch(event.getEntity());
    }

    /**
     * The same, reachable by a test that wants to ask what becomes of one that does not go
     * after all — which is the half no ordinary play can arrange on demand.
     */
    static void watch(ItemEntity entity) {
        DUE.add(entity);
    }

    /** The outcome, once everything with something to say about it has said it. */
    static void tick(LevelTickEvent.Post event) {
        if (DUE.isEmpty() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        for (Iterator<ItemEntity> each = DUE.iterator(); each.hasNext();) {
            ItemEntity entity = each.next();
            if (entity.level() != level) {
                continue;
            }
            each.remove();
            // Gone, and gone for the reason it was written down here. A lifespan that has
            // moved is somebody having bought it more time, and an item still lying there
            // is an item still holding the name.
            if (entity.isRemoved() && entity.getAge() >= entity.lifespan) {
                Kept.destroyed(level, entity.getItem());
            }
        }
    }

    /** A server going away owes nobody an answer. */
    static void forget() {
        DUE.clear();
    }
}
