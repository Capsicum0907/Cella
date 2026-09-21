package io.github.capsicum0907.cella;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public final class Blast {
    public static final int REACH = 100;

    private static final int SPEED = 2;

    private static final float BITE = 1000.0F;

    private static final List<Blast> RUNNING = new ArrayList<>();

    private final ServerLevel level;
    private final BlockPos centre;
    private final int reach;

    private int at = 1;

    private boolean over;

    private final java.util.Set<Integer> bitten = new java.util.HashSet<>();

    private Blast(ServerLevel level, BlockPos centre, int reach) {
        this.level = level;
        this.centre = centre;
        this.reach = reach;
    }

    public static Blast start(ServerLevel level, BlockPos centre, int reach) {
        Blast blast = new Blast(level, centre, reach);
        RUNNING.add(blast);
        return blast;
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
                blast.over = true;
                each.remove();
            }
        }
    }

    public static void forget() {
        RUNNING.clear();
    }

    public boolean over() {
        return over;
    }

    private boolean step() {
        for (int shell = 0; shell < SPEED && at <= reach; shell++, at++) {
            surface(at);
        }
        sweep();
        return at > reach;
    }

    private void sweep() {
        Vec3 middle = Vec3.atCenterOf(centre);
        AABB box = AABB.ofSize(middle, at * 2.0, at * 2.0, at * 2.0);
        DamageSource source = Annihilation.by(level);
        double within = (double) at * at;
        for (Entity caught : level.getEntities((Entity) null, box, Entity::isAlive)) {
            if (caught.distanceToSqr(middle) > within || !bitten.add(caught.getId())) {
                continue;
            }

            if (caught instanceof net.minecraft.world.entity.item.ItemEntity lying
                    && lying.getItem().getItem() instanceof CellaItem cella
                    && cella.kind().trait().survivesAnnihilation()) {
                continue;
            }

            if (caught instanceof Player) {
                caught.hurt(source, BITE);
            } else {
                if (caught instanceof net.minecraft.world.entity.item.ItemEntity item) {
                    Kept.destroyed(level, item.getItem());
                }
                caught.discard();
            }
        }
    }

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

    @FunctionalInterface
    interface At {
        void at(int x, int y, int z);
    }

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

    static int surfaceOf(int out) {
        int[] count = { 0 };
        walk(out, (x, y, z) -> count[0]++);
        return count[0];
    }

    private void maybe(BlockPos.MutableBlockPos pos, int x, int y, int z, long within) {
        if ((long) x * x + (long) y * y + (long) z * z > within) {
            return;
        }
        pos.set(centre.getX() + x, centre.getY() + y, centre.getZ() + z);

        if (!level.hasChunkAt(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }

        if (state.getDestroySpeed(level, pos) < 0.0F) {
            return;
        }

        if (state.getBlock() instanceof CellaBlock cella
                && cella.kind().trait().survivesAnnihilation()) {
            return;
        }

        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }
}
