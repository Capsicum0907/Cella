package io.github.capsicum0907.cella;

import net.minecraft.world.item.ItemStack;

public record Claim(int part, ItemStack kind, int count) {
    public boolean covers(int at, ItemStack stack) {
        return part == at && Alike.same(kind, stack);
    }
}
