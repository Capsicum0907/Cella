package io.github.capsicum0907.cella;

import java.util.Collection;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

final class Carried {
    private static final String CARRIED = Cella.MODID + "_carried";

    private Carried() {
    }

    static void died(LivingDropsEvent event) {
        if (event.getEntity() instanceof Player player) {
            keep(player, event.getDrops());
        }
    }

    static void respawned(PlayerEvent.PlayerRespawnEvent event) {
        give(event.getEntity());
    }

    static void keep(Player player, Collection<ItemEntity> drops) {
        HolderLookup.Provider registries = player.registryAccess();
        ListTag keeping = new ListTag();
        drops.removeIf(drop -> {
            ItemStack stack = drop.getItem();
            if (!comesBack(stack)) {
                return false;
            }
            keeping.add(stack.save(registries));
            return true;
        });
        if (keeping.isEmpty()) {
            return;
        }
        CompoundTag persisted = persisted(player);

        ListTag waiting = persisted.getList(CARRIED, Tag.TAG_COMPOUND);
        waiting.addAll(keeping);
        persisted.put(CARRIED, waiting);
    }

    static void give(Player player) {
        CompoundTag persisted = persisted(player);
        ListTag waiting = persisted.getList(CARRIED, Tag.TAG_COMPOUND);
        if (waiting.isEmpty()) {
            return;
        }
        persisted.remove(CARRIED);
        HolderLookup.Provider registries = player.registryAccess();
        for (int at = 0; at < waiting.size(); at++) {
            ItemStack.parse(registries, waiting.get(at))
                    .ifPresent(player.getInventory()::placeItemBackInInventory);
        }
    }

    private static boolean comesBack(ItemStack stack) {
        return stack.getItem() instanceof CellaItem cella && cella.kind().trait().keptOnDeath();
    }

    private static CompoundTag persisted(Player player) {
        CompoundTag data = player.getPersistentData();
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
        return persisted;
    }
}
