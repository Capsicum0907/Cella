package io.github.capsicum0907.cella;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * A wave of destruction travelling outwards from a Cella that has ended itself.
 *
 * <h2>Why this is not an explosion</h2>
 *
 * <p><b>Vanilla explosions cannot do this at any radius, and the limit is the algorithm
 * rather than the machine.</b> An explosion casts rays from its centre and takes each
 * block's blast resistance off the ray as it passes, so in solid ground the reach is a
 * fraction of the radius asked for and no more. Measured in end stone: a radius of eight
 * removes eight blocks and opens a hole one across; sixteen removes forty-eight and opens
 * two; <b>thirty-two removes two hundred and eighty-three and opens four.</b> Asking for a
 * hundred would not give a hundred, it would give a dozen. What makes a stick of dynamite
 * look impressive above ground is that air costs the ray almost nothing.
 *
 * <p>So the blocks are removed here, deliberately, and {@code Level#explode} is left to do
 * what it is good at — the sound, the particles, and throwing whatever is standing nearby.
 *
 * <h2>Why it takes a few seconds</h2>
 *
 * <p><b>A sphere of radius a hundred is four million blocks</b>, and blocks are written at
 * roughly two hundred a millisecond, so doing it in one go is twenty seconds of a server
 * not answering. Spread over ticks with a fixed number of shells each, the cost per tick
 * is bounded no matter how big the radius is — and a wave that arrives is a better thing
 * to watch than a hole that is suddenly there. The constraint and the drama want the same
 * shape, which does not happen often.
 *
 * <p>Shells are walked as the surface of a cube rather than by testing every position in
 * it, or each step would cost the volume it encloses instead of its surface, and the last
 * shell of a big one would cost eight million tests to find a quarter of a million blocks.
 *
 * <h2>What it does to whatever is standing there</h2>
 *
 * <p><b>Kills it.</b> The blocks are the spectacle and this is the cost: everything alive
 * inside the front dies as the front passes it, the one who lit it included. That is what
 * the fuse is for — five seconds is enough to leave the middle and nowhere near enough to
 * leave the reach, which is the bargain being offered.
 *
 * <h2>⚠ What it will not touch</h2>
 *
 * <ul>
 *   <li><b>Anything the game says cannot be broken.</b> The End's exit portal is bedrock,
 *       and a mod that strands players in the End is a mod nobody keeps.
 *   <li><b>Other Cellas.</b> Which is the source material's answer as well as this mod's:
 *       nothing here is a threat to its own kind. It is also the practical one — a Cella
 *       removed by this would drop an item into a blast, in a dimension made mostly of
 *       nothing to fall into, and an item lost there is contents that only
 *       {@code /cella kept} can find again.
 *   <li>Blocks are set to air rather than broken, so nothing drops. Forty thousand item
 *       entities is not a spectacle, it is a stall. This is annihilation and not mining.
 * </ul>
 *
 * <p>⚠ <b>Waves are not saved.</b> One lasts a few seconds, so a server stopping in the
 * middle of one leaves a crater that stops half way — untidy, and nothing a world cannot
 * hold. Writing it to disk would mean a save file that owes the world an explosion.
 */
public final class Blast {
    /** How far it reaches, in blocks. */
    public static final int REACH = 100;

    /**
     * Shells per tick, which is how fast the edge travels: two blocks a tick is forty a
     * second, so the full reach arrives in two and a half seconds.
     *
     * <p>Constant rather than budgeted by how much work each shell turns out to be. A wave
     * that slowed down as it went would read as the game struggling, which is the one thing
     * it must not look like.
     */
    private static final int SPEED = 2;

    private static final List<Blast> RUNNING = new ArrayList<>();

    private final ServerLevel level;
    private final BlockPos centre;
    private final int reach;

    /** How far out it has already been. Starts at one: the centre is what survived. */
    private int at = 1;

    private Blast(ServerLevel level, BlockPos centre, int reach) {
        this.level = level;
        this.centre = centre;
        this.reach = reach;
    }

    /** Sets one going. It runs itself from there. */
    public static void start(ServerLevel level, BlockPos centre, int reach) {
        RUNNING.add(new Blast(level, centre, reach));
    }

    /** Whether anything is going on, which is the only thing a test can ask from outside. */
    public static int running() {
        return RUNNING.size();
    }

    public static void tick(LevelTickEvent.Post event) {
        if (RUNNING.isEmpty() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        for (Iterator<Blast> each = RUNNING.iterator(); each.hasNext();) {
            Blast blast = each.next();
            if (blast.level != level) {
                continue;
            }
            if (blast.step()) {
                each.remove();
            }
        }
    }

    /** Forgets everything in flight. A world that is going away owes nobody a crater. */
    public static void forget() {
        RUNNING.clear();
    }

    /** @return whether it has finished */
    private boolean step() {
        for (int shell = 0; shell < SPEED && at <= reach; shell++, at++) {
            surface(at);
        }
        sweep();
        return at > reach;
    }

    /**
     * Everything alive inside the front, killed.
     *
     * <p><b>The whole sphere each tick and not the band it just passed.</b> The band is
     * what the blocks want, because a block that has been removed stays removed — but a
     * living thing can walk into somewhere the wave has already been, or be spawned there,
     * and the band would let it stand in the crater untouched. Asking about the whole of
     * the inside costs the same query and has no hole in it: whatever is already dead is
     * filtered out before anything is done to it.
     *
     * <p><b>Damage rather than {@code kill()}</b>, so that the game's own answers still
     * apply — a totem is a thing players are entitled to be saved by, and a creative-mode
     * player is a thing that has to survive it or this could not be tested. The amount is
     * past anything armour reduces to survivable.
     *
     * <p>⚠ <b>Which includes the player who lit it</b>, and is meant to: five seconds is
     * enough to leave the middle and nowhere near enough to leave the reach.
     */
    private void sweep() {
        Vec3 middle = Vec3.atCenterOf(centre);
        AABB box = AABB.ofSize(middle, at * 2.0, at * 2.0, at * 2.0);
        DamageSource source = level.damageSources().explosion(null, null);
        double within = (double) at * at;
        for (Entity caught : level.getEntities((Entity) null, box, Entity::isAlive)) {
            if (caught.distanceToSqr(middle) <= within) {
                caught.hurt(source, Float.MAX_VALUE);
            }
        }
    }

    /**
     * Everything at exactly that many blocks out, measured as a cube, kept if it is inside
     * the sphere.
     *
     * <p>The cube's surface: the two faces where x is at the edge are solid squares, and
     * every slice between them is a ring. Walking it any other way costs the volume.
     */
    private void surface(int out) {
        int floor = level.getMinBuildHeight();
        int ceiling = level.getMaxBuildHeight() - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        long within = (long) reach * reach;
        walk(out, (x, y, z) -> {
            int height = centre.getY() + y;
            if (height >= floor && height <= ceiling) {
                maybe(pos, x, y, z, within);
            }
        });
    }

    /** Somewhere on the shell. */
    @FunctionalInterface
    interface At {
        void at(int x, int y, int z);
    }

    /**
     * Every offset whose largest component is exactly {@code out}, and nothing else.
     *
     * <p>The two faces where x is at the edge are solid squares; every slice between them
     * is a ring, so only its two z edges are visited. Testing every position in the cube
     * and skipping the inside would cost the volume to find the surface.
     */
    static void walk(int out, At at) {
        for (int x = -out; x <= out; x++) {
            boolean edgeX = Math.abs(x) == out;
            for (int y = -out; y <= out; y++) {
                boolean edgeY = Math.abs(y) == out;
                if (edgeX || edgeY) {
                    for (int z = -out; z <= out; z++) {
                        at.at(x, y, z);
                    }
                } else {
                    at.at(x, y, -out);
                    at.at(x, y, out);
                }
            }
        }
    }

    /** How many positions {@link #walk} visits. Used by the test that it visits the right ones. */
    static int surfaceOf(int out) {
        int[] count = { 0 };
        walk(out, (x, y, z) -> count[0]++);
        return count[0];
    }

    /**
     * One position, if it is inside the sphere and is something this may remove.
     *
     * <p>Nothing checks whether it was reached already: a cube's surface at a given size
     * is walked exactly once, and no two sizes share a position.
     */
    private void maybe(BlockPos.MutableBlockPos pos, int x, int y, int z, long within) {
        // The corners of the cube stick out of the sphere it is standing in for.
        if ((long) x * x + (long) y * y + (long) z * z > within) {
            return;
        }
        pos.set(centre.getX() + x, centre.getY() + y, centre.getZ() + z);
        // Asked before the block is, so that a wave big enough to leave the loaded world
        // does not drag chunks in to destroy them.
        if (!level.hasChunkAt(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        // Bedrock and its family answer negative, and the End's way home is made of it.
        if (state.getDestroySpeed(level, pos) < 0.0F) {
            return;
        }
        if (state.getBlock() instanceof CellaBlock) {
            return;
        }
        // Set, not destroyed: nothing drops, and nothing tells its neighbours. A wave that
        // announced every block it removed would spend its time on redstone.
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }
}
