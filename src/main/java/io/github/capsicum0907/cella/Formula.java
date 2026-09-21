package io.github.capsicum0907.cella;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.world.level.ItemLike;

public record Formula(List<String> pattern, Map<Character, List<Supplier<ItemLike>>> of,
        int count, boolean spawns, boolean fuses) {
    public static class Builder {
        private final List<String> pattern;
        private final Map<Character, List<Supplier<ItemLike>>> of = new LinkedHashMap<>();
        private int count = 1;
        private boolean spawns;
        private boolean eatsAChest;

        private Builder(String top, String middle, String bottom) {
            this.pattern = List.of(top, middle, bottom);
        }

        public Builder key(char letter, Supplier<ItemLike> what) {
            of.put(letter, List.of(what));
            return this;
        }

        public Builder key(char letter, Kind kind, String... orNamed) {
            eatsAChest = true;
            List<Supplier<ItemLike>> any = new ArrayList<>();
            any.add(() -> CellaRegistry.block(kind).get());
            for (String id : orNamed) {
                any.add(() -> CellaRegistry.block(Kind.named(id).orElseThrow(
                        () -> new IllegalStateException("no kind is called " + id))).get());
            }
            of.put(letter, List.copyOf(any));
            return this;
        }

        public Builder count(int made) {
            this.count = made;
            return this;
        }

        public Builder spawning() {
            this.spawns = true;
            return this;
        }

        public Formula done() {
            return new Formula(pattern,
                    java.util.Collections.unmodifiableMap(new LinkedHashMap<>(of)),
                    count, spawns, eatsAChest && !spawns);
        }
    }

    public static Builder shaped(String top, String middle, String bottom) {
        return new Builder(top, middle, bottom);
    }

    public static boolean grown(CraftingInput input) {
        for (int at = 0; at < input.size(); at++) {
            ItemStack laid = input.getItem(at);
            if (!(laid.getItem() instanceof BlockItem block)
                    || !(block.getBlock() instanceof CellaBlock chest)
                    || !chest.kind().grows()) {
                continue;
            }
            Held held = laid.get(CellaRegistry.KEPT.get());
            if (held == null || !held.grows() || held.grown() < 1.0F) {
                return false;
            }
        }
        return true;
    }
}
