package io.github.capsicum0907.cella;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class Pockets {
    private final Inventory inventory;

    public Pockets(Inventory inventory) {
        this.inventory = inventory;
    }

    public int room(ItemStack kind) {
        int limit = inventory.getMaxStackSize(kind);
        int room = 0;
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack held = inventory.getItem(slot);
            if (held.isEmpty()) {
                room += limit;
            } else if (ItemStack.isSameItemSameComponents(held, kind)) {
                room += Math.max(0, limit - held.getCount());
            }
        }
        return room;
    }

    public boolean full() {
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack held = inventory.getItem(slot);
            if (held.isEmpty() || held.getCount() < inventory.getMaxStackSize(held)) {
                return false;
            }
        }
        return true;
    }

    public int put(ItemStack kind, int many) {
        int limit = inventory.getMaxStackSize(kind);
        int left = many;
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE && left > 0; slot++) {
            ItemStack held = inventory.getItem(slot);
            if (!held.isEmpty() && ItemStack.isSameItemSameComponents(held, kind)) {
                int moved = Math.min(left, Math.max(0, limit - held.getCount()));
                held.grow(moved);
                left -= moved;
            }
        }
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE && left > 0; slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                int moved = Math.min(left, limit);
                inventory.setItem(slot, kind.copyWithCount(moved));
                left -= moved;
            }
        }
        return many - left;
    }
}
