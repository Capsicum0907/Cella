package io.github.capsicum0907.cella;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.neoforge.event.entity.item.ItemExpireEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

final class Expiring {
    private static final List<ItemEntity> DUE = new ArrayList<>();

    private Expiring() {
    }

    static void reached(ItemExpireEvent event) {
        watch(event.getEntity());
    }

    static void watch(ItemEntity entity) {
        DUE.add(entity);
    }

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

            if (entity.isRemoved() && entity.getAge() >= entity.lifespan) {
                Kept.destroyed(level, entity.getItem());
            }
        }
    }

    static void forget() {
        DUE.clear();
    }
}
